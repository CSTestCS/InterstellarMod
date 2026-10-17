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
 * F3+T. Delete a file to get the default back on the next start; unedited files follow new defaults.
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
        // Files still matching the default we exported last time are updated when the defaults change; edited
        // files are kept. Packs from before this record existed get their differing files backed up, then updated.
        Path record = dir.resolve(".defaults");
        java.util.Properties known = new java.util.Properties();
        boolean hadRecord = Files.exists(record);
        if (hadRecord) {
            try (InputStream in = Files.newInputStream(record)) {
                known.load(in);
            }
        }
        try (InputStream index = GunModelPack.class.getResourceAsStream(INDEX)) {
            if (index == null) return;
            for (String line : new String(index.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String rel = line.strip();
                if (rel.isEmpty() || rel.contains("..")) continue;
                byte[] fresh;
                try (InputStream in = GunModelPack.class.getResourceAsStream("/" + rel)) {
                    if (in == null) continue;
                    fresh = in.readAllBytes();
                }
                Path target = dir.resolve(rel);
                String freshHash = hash(fresh);
                if (Files.exists(target)) {
                    String current = hash(Files.readAllBytes(target));
                    if (current.equals(freshHash)) {
                        known.setProperty(rel, freshHash);
                        continue;
                    }
                    boolean untouched = current.equals(known.getProperty(rel));
                    if (hadRecord && !untouched) continue; // edited by the player
                    if (!untouched) {
                        Path backup = dir.resolveSibling("gunmodels_backup").resolve(rel);
                        Files.createDirectories(backup.getParent());
                        Files.copy(target, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                Files.createDirectories(target.getParent());
                Files.write(target, fresh);
                known.setProperty(rel, freshHash);
            }
        }
        try (java.io.OutputStream out = Files.newOutputStream(record)) {
            known.store(out, "Hashes of the default models last exported (do not edit)");
        }
    }

    private static String hash(byte[] data) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }
}
