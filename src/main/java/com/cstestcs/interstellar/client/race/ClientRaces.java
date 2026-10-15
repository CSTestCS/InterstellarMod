package com.cstestcs.interstellar.client.race;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.model.JavaModelInterpreter;
import com.cstestcs.interstellar.core.model.ModelDef;
import com.cstestcs.interstellar.core.race.RaceDefinition;
import com.cstestcs.interstellar.core.race.RaceDetails;
import com.cstestcs.interstellar.core.race.RaceDetailsParser;
import com.cstestcs.interstellar.core.race.RaceFiles;
import com.cstestcs.interstellar.core.race.RaceRegistry;
import com.cstestcs.interstellar.network.ModPayloads;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Client copy of the host's races: receives the zipped races folder, registers every skin as a dynamic texture and
 * bakes every {@code RACENAME_head.java} into a {@link ModelPart}. Subraces without their own files use their
 * parent's.
 */
public final class ClientRaces {
    /** Everything needed to draw one race. */
    public static final class Race {
        public final RaceDefinition def;
        public final List<Identifier> skins = new ArrayList<>();
        public final List<Identifier> headSkins = new ArrayList<>();
        public @Nullable ModelPart head;
        public @Nullable ModelPart tail;

        Race(RaceDefinition def) {
            this.def = def;
        }

        public @Nullable Identifier skin(int variant) {
            return skins.isEmpty() ? null : skins.get(Math.floorMod(variant, skins.size()));
        }

        public @Nullable Identifier headSkin(int variant) {
            if (headSkins.isEmpty()) return skin(variant);
            return headSkins.get(Math.floorMod(variant, headSkins.size()));
        }
    }

    private static final Map<String, Race> RACES = new HashMap<>();
    private static final List<Identifier> REGISTERED = new ArrayList<>();
    private static byte[][] chunks;
    private static int chunkHash;

    private ClientRaces() {}

    public static @Nullable Race get(String id) {
        return id == null || id.isEmpty() ? null : RACES.get(id);
    }

    public static void receive(ModPayloads.RaceAssets payload) {
        if (chunks == null || chunks.length != payload.total() || chunkHash != payload.hash()) {
            chunks = new byte[payload.total()][];
            chunkHash = payload.hash();
        }
        chunks[payload.index()] = payload.data();
        for (byte[] c : chunks) if (c == null) return;
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (byte[] c : chunks) all.writeBytes(c);
        chunks = null;
        load(all.toByteArray());
    }

    private static void load(byte[] zip) {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) files.put(e.getName(), in.readAllBytes());
        } catch (IOException ex) {
            Interstellar.LOGGER.error("Corrupt race assets from server", ex);
            return;
        }
        clear();
        Map<String, RaceFiles.Entry> grouped = RaceFiles.group(files.keySet());
        List<RaceDetails> details = new ArrayList<>();
        for (RaceFiles.Entry entry : grouped.values()) {
            if (entry.detailsFile != null) details.add(RaceDetailsParser.parse(entry.raceId, new String(files.get(entry.detailsFile), StandardCharsets.UTF_8)));
        }
        RaceRegistry registry = RaceRegistry.build(details);
        for (RaceDefinition def : registry.all()) {
            Race race = new Race(def);
            RaceFiles.Entry own = grouped.get(def.id());
            RaceFiles.Entry source = own;
            // Walk up to the nearest ancestor that has textures.
            String parent = def.parentId();
            while ((source == null || !source.hasSkin() && source.headSkins.isEmpty() && !source.hasHeadModel()) && parent != null) {
                source = grouped.get(parent);
                RaceDefinition p = registry.get(parent);
                parent = p == null ? null : p.parentId();
            }
            if (source != null) {
                for (String s : source.skins) race.skins.add(texture(source.raceId, s, files.get(s)));
                for (String s : source.headSkins) race.headSkins.add(texture(source.raceId, s, files.get(s)));
                if (source.headModelFile != null) race.head = bakeHead(source.headModelFile, files.get(source.headModelFile));
            }
            race.skins.removeIf(java.util.Objects::isNull);
            race.headSkins.removeIf(java.util.Objects::isNull);
            if (def.hasTail()) race.tail = TailModels.bake(def.tailType());
            RACES.put(def.id(), race);
        }
        Interstellar.LOGGER.info("Received {} races from the server", RACES.size());
    }

    private static @Nullable Identifier texture(String race, String file, byte[] data) {
        if (data == null) return null;
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
            Identifier id = Interstellar.id("dynamic/race/" + file.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_"));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
            REGISTERED.add(id);
            return id;
        } catch (IOException e) {
            Interstellar.LOGGER.warn("Race {}: unreadable texture {}", race, file, e);
            return null;
        }
    }

    private static @Nullable ModelPart bakeHead(String file, byte[] data) {
        try {
            ModelDef def = JavaModelInterpreter.interpret(new String(data, StandardCharsets.UTF_8));
            return toLayer(def, true).bakeRoot();
        } catch (JavaModelInterpreter.ModelParseException | RuntimeException e) {
            Interstellar.LOGGER.warn("Head model {} could not be read: {}", file, e.getMessage());
            return null;
        }
    }

    /**
     * Converts an interpreted model into a vanilla layer definition. With {@code alignHead}, a part named "head" has
     * its pivot moved to the origin so it lines up with the mob's head pivot.
     */
    public static LayerDefinition toLayer(ModelDef def, boolean alignHead) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (ModelDef.PartDef p : def.root.children.values()) add(root, p, alignHead && p.name.equals("head"));
        return LayerDefinition.create(mesh, def.textureWidth, def.textureHeight);
    }

    private static void add(PartDefinition parent, ModelDef.PartDef p, boolean zeroOffset) {
        CubeListBuilder cubes = CubeListBuilder.create();
        for (ModelDef.CubeDef c : p.cubes) {
            cubes.texOffs(c.u(), c.v()).mirror(c.mirror())
                    .addBox(c.x(), c.y(), c.z(), c.w(), c.h(), c.d(), new CubeDeformation(c.growX(), c.growY(), c.growZ()));
        }
        PartPose pose = PartPose.offsetAndRotation(zeroOffset ? 0 : p.x, zeroOffset ? 0 : p.y, zeroOffset ? 0 : p.z, p.xRot, p.yRot, p.zRot);
        if (p.scale != 1f) pose = pose.scaled(p.scale);
        PartDefinition child = parent.addOrReplaceChild(p.name, cubes, pose);
        for (ModelDef.PartDef c : p.children.values()) add(child, c, false);
    }

    public static void clear() {
        for (Identifier id : REGISTERED) Minecraft.getInstance().getTextureManager().release(id);
        REGISTERED.clear();
        RACES.clear();
    }
}
