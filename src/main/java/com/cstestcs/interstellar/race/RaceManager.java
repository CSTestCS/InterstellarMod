package com.cstestcs.interstellar.race;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.race.DefaultRaces;
import com.cstestcs.interstellar.core.race.RaceDefinition;
import com.cstestcs.interstellar.core.race.RaceDetails;
import com.cstestcs.interstellar.core.race.RaceDetailsParser;
import com.cstestcs.interstellar.core.race.RaceFiles;
import com.cstestcs.interstellar.core.race.RaceRegistry;
import com.cstestcs.interstellar.network.ModPayloads;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Owns the host's races folder ({@code config/interstellar/races}). Writes the default race files, parses every
 * {@code *_details.txt} into a {@link RaceRegistry}, and zips the folder so clients can render the races' textures and
 * head models without having the files themselves.
 */
public final class RaceManager {
    private static final int CHUNK = 512 * 1024;
    private static volatile RaceRegistry registry = RaceRegistry.EMPTY;
    private static volatile byte[] zipped = new byte[0];
    private static volatile int hash;
    private static volatile Map<String, RaceFiles.Entry> files = Map.of();

    private RaceManager() {}

    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(Interstellar.MODID).resolve("races");
    }

    public static RaceRegistry registry() {
        return registry;
    }

    /** Texture/model files of a race, walking up the subrace chain until a race that has skins is found. */
    public static RaceFiles.Entry files(String id) {
        for (int depth = 0; id != null && depth < 32; depth++) {
            RaceFiles.Entry e = files.get(id);
            if (e != null && (e.hasSkin() || e.hasHeadModel() || !e.headSkins.isEmpty())) return e;
            RaceDefinition def = registry.get(id);
            id = def == null ? null : def.parentId();
        }
        return null;
    }

    public static RaceDefinition get(String id) {
        return registry.get(id);
    }

    public static void load(MinecraftServer server) {
        Path dir = folder();
        try {
            Files.createDirectories(dir);
            writeDefaults(dir, DefaultRaces.files());
            List<String> names;
            try (Stream<Path> s = Files.list(dir)) {
                names = s.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).sorted().toList();
            }
            Map<String, RaceFiles.Entry> grouped = RaceFiles.group(names);
            List<RaceDetails> details = new ArrayList<>();
            for (RaceFiles.Entry entry : grouped.values()) {
                if (entry.detailsFile == null) continue;
                String text = Files.readString(dir.resolve(entry.detailsFile), StandardCharsets.UTF_8);
                details.add(RaceDetailsParser.parse(entry.raceId, text));
            }
            RaceRegistry reg = RaceRegistry.build(details);
            for (String problem : reg.problems()) Interstellar.LOGGER.warn("[races] {}", problem);
            registry = reg;
            files = grouped;
            zipped = zip(dir, names);
            hash = Arrays.hashCode(zipped);
            Interstellar.LOGGER.info("Loaded {} races ({} bytes of race assets)", reg.size(), zipped.length);
        } catch (IOException e) {
            Interstellar.LOGGER.error("Could not load the races folder {}", dir, e);
        }
        if (server != null) for (ServerPlayer p : server.getPlayerList().getPlayers()) sendAssets(p);
    }

    /**
     * Writes missing default files and refreshes generated ones the user never edited (tracked by hash in
     * {@code .defaults}), so fixes to the defaults reach existing installs without touching customised files.
     */
    public static void writeDefaults(Path dir, Map<String, byte[]> defaults) throws IOException {
        Path manifestFile = dir.resolve(".defaults");
        java.util.Properties manifest = new java.util.Properties();
        if (Files.exists(manifestFile)) {
            try (var in = Files.newBufferedReader(manifestFile, StandardCharsets.UTF_8)) {
                manifest.load(in);
            }
        }
        boolean changed = false;
        for (Map.Entry<String, byte[]> e : defaults.entrySet()) {
            Path f = dir.resolve(e.getKey());
            String newHash = com.cstestcs.interstellar.core.race.DefaultFileUpdater.hash(e.getValue());
            if (!Files.exists(f) || com.cstestcs.interstellar.core.race.DefaultFileUpdater.replaceable(e.getKey(), Files.readAllBytes(f), e.getValue(),
                    manifest.getProperty(e.getKey()))) {
                Files.write(f, e.getValue());
            } else if (!newHash.equals(com.cstestcs.interstellar.core.race.DefaultFileUpdater.hash(Files.readAllBytes(f)))) {
                continue; // edited by the user: keep it and keep remembering the original hash
            }
            if (!newHash.equals(manifest.getProperty(e.getKey()))) {
                manifest.setProperty(e.getKey(), newHash);
                changed = true;
            }
        }
        if (changed) {
            try (var out = Files.newBufferedWriter(manifestFile, StandardCharsets.UTF_8)) {
                manifest.store(out, "Hashes of generated default files; files still matching them are updated automatically");
            }
        }
    }

    private static byte[] zip(Path dir, List<String> names) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String n : names) {
                String lower = n.toLowerCase(java.util.Locale.ROOT);
                if (!(lower.endsWith(".png") || lower.endsWith(".java") || lower.endsWith(".txt"))) continue;
                zip.putNextEntry(new ZipEntry(n));
                zip.write(Files.readAllBytes(dir.resolve(n)));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** Sends the zipped races folder in chunks (custom payloads are limited to about 1 MiB). */
    public static void sendAssets(ServerPlayer player) {
        byte[] data = zipped;
        int total = Math.max(1, (data.length + CHUNK - 1) / CHUNK);
        for (int i = 0; i < total; i++) {
            int from = i * CHUNK, to = Math.min(data.length, from + CHUNK);
            PacketDistributor.sendToPlayer(player, new ModPayloads.RaceAssets(i, total, hash, Arrays.copyOfRange(data, from, to)));
        }
    }
}
