package com.cstestcs.interstellar.client.body;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.InterstellarConfig;
import com.cstestcs.interstellar.core.body.AnimState;
import com.cstestcs.interstellar.core.body.AnimationClip;
import com.cstestcs.interstellar.core.body.BodyMeta;
import com.cstestcs.interstellar.core.body.BodyModel;
import com.cstestcs.interstellar.core.body.DefaultBodies;
import com.cstestcs.interstellar.core.fbx.FbxIO;
import com.cstestcs.interstellar.core.fbx.FbxScene;
import com.cstestcs.interstellar.network.ModPayloads;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Client-side custom bodies ({@code config/interstellar/custommodels/*.fbx}) and animations
 * ({@code config/interstellar/customanimations/*.fbx}, optionally in a sub folder named after a body to make clips
 * specific to that body). Loading and FBX parsing happen off-thread; the result is swapped in atomically.
 */
public final class BodyLibrary {
    public record Loaded(Map<String, BodyModel> bodies, Map<AnimState, List<AnimationClip>> clips,
            Map<String, Map<AnimState, List<AnimationClip>>> bodyClips) {
        static final Loaded EMPTY = new Loaded(Map.of(), Map.of(), Map.of());
    }

    private static volatile Loaded loaded = Loaded.EMPTY;

    private BodyLibrary() {}

    public static Path modelsDir() {
        return FMLPaths.CONFIGDIR.get().resolve(Interstellar.MODID).resolve("custommodels");
    }

    public static Path animationsDir() {
        return FMLPaths.CONFIGDIR.get().resolve(Interstellar.MODID).resolve("customanimations");
    }

    /** @return the body to render for a name, or null if it is not installed or disabled on this client. */
    public static @Nullable BodyModel body(String name) {
        if (name == null || name.isEmpty() || !InterstellarConfig.CLIENT.customBodies.get()) return null;
        if (InterstellarConfig.CLIENT.disabledBodies.get().contains(name)) return null;
        return loaded.bodies.get(name);
    }

    /** Clips for a state: body-specific ones first, else the shared ones; falls back through {@link AnimState#fallback()}. */
    public static List<AnimationClip> clips(String body, AnimState state) {
        if (!InterstellarConfig.CLIENT.customAnimations.get()) return List.of();
        Loaded l = loaded;
        for (AnimState s = state; s != null; s = s.fallback()) {
            List<AnimationClip> own = l.bodyClips.getOrDefault(body, Map.of()).get(s);
            if (own != null && !own.isEmpty()) return own;
            List<AnimationClip> shared = l.clips.get(s);
            if (shared != null && !shared.isEmpty()) return shared;
        }
        return List.of();
    }

    public static CompletableFuture<Void> reload() {
        return CompletableFuture.supplyAsync(BodyLibrary::loadAll).thenAcceptAsync(l -> {
            loaded = l;
            BodyInstances.clear();
            reportToServer();
        }, Minecraft.getInstance());
    }

    /** Tells the server which bodies this client can render (and which body the player wants). */
    public static void reportToServer() {
        if (Minecraft.getInstance().getConnection() == null) return;
        List<String> names = new ArrayList<>(), genders = new ArrayList<>();
        for (BodyModel b : loaded.bodies.values()) {
            if (InterstellarConfig.CLIENT.disabledBodies.get().contains(b.meta.name()) || !InterstellarConfig.CLIENT.customBodies.get()) continue;
            names.add(b.meta.name());
            genders.add(b.meta.gender().name().toLowerCase(Locale.ROOT));
        }
        ClientPacketDistributor.sendToServer(new ModPayloads.InstalledBodies(names, genders, InterstellarConfig.CLIENT.ownBody.get()));
    }

    private static Loaded loadAll() {
        Path models = modelsDir(), anims = animationsDir();
        Map<String, BodyModel> bodies = new HashMap<>();
        Map<AnimState, List<AnimationClip>> shared = new EnumMap<>(AnimState.class);
        Map<String, Map<AnimState, List<AnimationClip>>> perBody = new HashMap<>();
        try {
            Files.createDirectories(models);
            Files.createDirectories(anims);
            Path readme = anims.resolve("README.txt");
            if (!Files.exists(readme)) Files.writeString(readme, ANIM_README);
            for (Map.Entry<String, byte[]> e : DefaultBodies.files().entrySet()) {
                Path f = models.resolve(e.getKey());
                if (!Files.exists(f)) Files.write(f, e.getValue());
            }
            try (Stream<Path> files = Files.list(models)) {
                for (Path f : files.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".fbx")).toList()) {
                    String name = f.getFileName().toString().replaceAll("(?i)\\.fbx$", "");
                    try {
                        Path metaFile = models.resolve(name + ".txt");
                        String metaText = Files.exists(metaFile) ? Files.readString(metaFile, StandardCharsets.UTF_8) : null;
                        BodyModel body = BodyModel.fromScene(BodyMeta.parse(name, metaText), FbxScene.parse(FbxIO.read(Files.readAllBytes(f))));
                        if (!body.rig.isHumanoid()) Interstellar.LOGGER.warn("Body {} has no standard humanoid (Mixamo) rig; armour will not bend with it", name);
                        bodies.put(name, body);
                    } catch (Exception ex) {
                        Interstellar.LOGGER.warn("Could not load custom body {}: {}", f.getFileName(), ex.getMessage());
                    }
                }
            }
            try (Stream<Path> files = Files.walk(anims, 2)) {
                for (Path f : files.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".fbx")).toList()) {
                    String file = f.getFileName().toString();
                    AnimState state = AnimState.fromFileName(file);
                    if (state == null) {
                        Interstellar.LOGGER.warn("Animation {} does not name a state (idle, walk, run, crouch, jump, fall, swim, sit, fly, attack, shoot, death...)", file);
                        continue;
                    }
                    try {
                        AnimationClip clip = AnimationClip.fromScene(file, state, FbxScene.parse(FbxIO.read(Files.readAllBytes(f))));
                        if (clip == null) continue;
                        Path parent = anims.relativize(f).getParent();
                        if (parent == null) shared.computeIfAbsent(state, k -> new ArrayList<>()).add(clip);
                        else perBody.computeIfAbsent(parent.toString(), k -> new EnumMap<>(AnimState.class)).computeIfAbsent(state, k -> new ArrayList<>()).add(clip);
                    } catch (Exception ex) {
                        Interstellar.LOGGER.warn("Could not load animation {}: {}", file, ex.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            Interstellar.LOGGER.error("Could not read the custom body folders", e);
        }
        Interstellar.LOGGER.info("Loaded {} custom bodies and {} animation clips", bodies.size(),
                shared.values().stream().mapToInt(List::size).sum() + perBody.values().stream().flatMap(m -> m.values().stream()).mapToInt(List::size).sum());
        return new Loaded(Map.copyOf(bodies), shared, perBody);
    }

    public static List<String> names() {
        return new ArrayList<>(loaded.bodies.keySet());
    }

    private static final String ANIM_README = """
            Drop raw Mixamo animations (FBX Binary, "Without Skin" is fine) in this folder.
            The file name decides when a clip plays, by keyword:
              idle, walk, run/sprint/jog, crouch idle, crouch/sneak (walking), jump, fall,
              swim, sit/seated/driving, fly/glide, climb, attack/punch/slash, shoot/aim/rifle, death/dying
            e.g. "Walking.fbx", "Standard Run.fbx", "Crouched Walking.fbx", "Falling Idle.fbx".
            Several clips for one state are picked per mob at random.
            Put clips in a sub folder named after a body (e.g. customanimations/buff/Walking.fbx) to use them only for that body.
            Without any clips, bodies follow the vanilla limb movement.
            """;
}
