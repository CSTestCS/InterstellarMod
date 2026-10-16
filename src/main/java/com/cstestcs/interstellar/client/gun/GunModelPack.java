package com.cstestcs.interstellar.client.gun;

import com.cstestcs.interstellar.Interstellar;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * {@code config/interstellar/gunmodels} - an always-on resource pack holding the default gun part, frame and trim
 * models, copied there on first start so players can reshape them (Blockbench, any JSON item model). Every material
 * version of a part inherits its shape from these files, so one edit reshapes the part in all materials. Reload with
 * F3+T. Delete a file to get the default back on the next start.
 */
public final class GunModelPack {
    private static final String INDEX = "/assets/interstellar/gunmodels_index.txt";

    private GunModelPack() {}

    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(Interstellar.MODID).resolve("gunmodels");
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        Path dir = folder();
        try {
            export(dir);
        } catch (IOException e) {
            Interstellar.LOGGER.warn("Could not export the default gun models to {}: {}", dir, e.toString());
            return;
        }
        PackLocationInfo info = new PackLocationInfo("interstellar_gunmodels", Component.literal("Interstellar gun models (config/interstellar/gunmodels)"),
                PackSource.BUILT_IN, Optional.empty());
        Pack pack = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(dir), PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
        else Interstellar.LOGGER.warn("The gun model pack in {} has an invalid pack.mcmeta", dir);
    }

    private static void export(Path dir) throws IOException {
        Files.createDirectories(dir);
        Path meta = dir.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            Files.writeString(meta, """
                    {"pack": {"description": "Interstellar gun models - edit freely", "pack_format": 97, "min_format": [97, 0], "max_format": [999, 0]}}
                    """, StandardCharsets.UTF_8);
        }
        Path readme = dir.resolve("README.txt");
        if (!Files.exists(readme)) {
            Files.writeString(readme, """
                    Gun part models used by Interstellar, as an always-active resource pack.

                    assets/interstellar/models/item/gun/part/<part id>.json   one model per gun part (barrels, scopes, magazines...)
                    assets/interstellar/models/item/gun/frame/<archetype>.json  the body of each gun family
                    assets/interstellar/models/item/gun/base.json, trim.json

                    Item space: 16 units, the gun points along +X. Faces using the texture "#metal" are drawn with the part's
                    material (the gold block texture for gold parts, and so on); other textures you add stay as they are.
                    Edit, then press F3+T in game. Delete a file to restore the default on the next start.
                    """, StandardCharsets.UTF_8);
        }
        try (InputStream index = GunModelPack.class.getResourceAsStream(INDEX)) {
            if (index == null) return;
            for (String line : new String(index.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String rel = line.strip();
                if (rel.isEmpty() || rel.contains("..")) continue;
                Path target = dir.resolve(rel);
                if (Files.exists(target)) continue;
                try (InputStream in = GunModelPack.class.getResourceAsStream("/" + rel)) {
                    if (in == null) continue;
                    Files.createDirectories(target.getParent());
                    Files.write(target, in.readAllBytes());
                }
            }
        }
    }
}
