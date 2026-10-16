package com.cstestcs.interstellar.ship;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.registry.ModBlocks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Starship designs: a few built-in ones plus every design saved with {@code /interstellar ship save} (stored in
 * {@code config/interstellar/starships/<name>.nbt}, shareable between worlds). Aliens fly these through space.
 */
public final class StarshipLibrary {
    private static final Map<String, ShipStructure> BUILT_IN = new LinkedHashMap<>();

    private StarshipLibrary() {}

    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(Interstellar.MODID).resolve("starships");
    }

    public static String cleanName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    public static void save(MinecraftServer server, String name, ShipStructure structure) throws IOException {
        Files.createDirectories(folder());
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var tag = ShipStructure.CODEC.encodeStart(ops, structure).getOrThrow();
        CompoundTag root = new CompoundTag();
        root.put("ship", tag);
        NbtIo.writeCompressed(root, folder().resolve(cleanName(name) + ".nbt"));
    }

    public static Optional<ShipStructure> load(MinecraftServer server, String name) {
        ShipStructure builtIn = builtIn().get(name);
        if (builtIn != null) return Optional.of(builtIn);
        Path file = folder().resolve(cleanName(name) + ".nbt");
        if (!Files.exists(file)) return Optional.empty();
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            return ShipStructure.CODEC.parse(ops, root.get("ship")).result();
        } catch (IOException | RuntimeException e) {
            Interstellar.LOGGER.warn("Could not read starship design {}: {}", file, e.toString());
            return Optional.empty();
        }
    }

    /** Names of all designs: saved ones first, then the built-in ones. */
    public static List<String> names() {
        List<String> out = new ArrayList<>();
        if (Files.isDirectory(folder())) {
            try (Stream<Path> files = Files.list(folder())) {
                files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".nbt")).map(n -> n.substring(0, n.length() - 4)).sorted().forEach(out::add);
            } catch (IOException ignored) {}
        }
        out.addAll(builtIn().keySet());
        return out;
    }

    // ------------------------------------------------------------------ built-in designs

    private static Map<String, ShipStructure> builtIn() {
        if (BUILT_IN.isEmpty()) {
            BUILT_IN.put("scout", design(new String[][] {
                    { "..G..", ".....", "G...G", ".....", "..G.." },
                    { ".CHC.", "HHHHH", "HHHHH", "HHHHH", ".THT." },
                    { "..H..", ".HPH.", ".E.L.", ".HSH.", "....." },
                    { ".....", "..H..", ".H.H.", "..H..", "....." } }));
            BUILT_IN.put("raider", design(new String[][] {
                    { ".......", "..G.G..", ".......", "G.....G", ".......", "..G.G..", "......." },
                    { "C.HHH.C", "HHHHHHH", "HHHHHHH", "HHHHHHH", "HHHHHHH", "HHHHHHH", "T.HHH.T" },
                    { "C.....C", ".HHPHH.", ".H...H.", ".HS.SH.", ".HE.LH.", ".HS.SH.", "T.HHH.T" },
                    { ".......", ".HHHHH.", ".HHHHH.", ".HHHHH.", ".HHHHH.", ".HHHHH.", "......." } }));
        }
        return BUILT_IN;
    }

    /**
     * Builds a structure from layers (bottom first) of rows (north first): G gear, H hull, P control panel (pilot
     * behind it, to the south), E generator, L life support, T thruster, C cannon, S seat. The ship flies north.
     */
    private static ShipStructure design(String[][] layers) {
        BlockPos panel = null;
        for (int y = 0; y < layers.length; y++) {
            for (int z = 0; z < layers[y].length; z++) {
                int x = layers[y][z].indexOf('P');
                if (x >= 0) panel = new BlockPos(x, y, z);
            }
        }
        if (panel == null) throw new IllegalStateException("design without control panel");
        List<ShipStructure.Entry> entries = new ArrayList<>();
        for (int y = 0; y < layers.length; y++) {
            for (int z = 0; z < layers[y].length; z++) {
                String row = layers[y][z];
                for (int x = 0; x < row.length(); x++) {
                    BlockState state = stateFor(row.charAt(x));
                    if (state == null) continue;
                    entries.add(new ShipStructure.Entry(new BlockPos(x, y, z).subtract(panel), state, Optional.empty()));
                }
            }
        }
        return new ShipStructure(entries, Direction.NORTH.toYRot());
    }

    private static BlockState stateFor(char c) {
        return switch (c) {
            case 'G' -> ModBlocks.LANDING_GEAR.get().defaultBlockState().setValue(LandingGearBlock.EXTENDED, false);
            case 'H' -> ModBlocks.HULL_PLATING.get().defaultBlockState();
            case 'P' -> ModBlocks.CONTROL_PANEL.get().defaultBlockState().setValue(ShipPartBlock.FACING, Direction.SOUTH);
            case 'E' -> ModBlocks.SHIP_GENERATOR.get().defaultBlockState();
            case 'L' -> ModBlocks.LIFE_SUPPORT.get().defaultBlockState();
            case 'T' -> ModBlocks.SHIP_THRUSTER.get().defaultBlockState().setValue(ShipPartBlock.FACING, Direction.SOUTH);
            case 'C' -> ModBlocks.SHIP_CANNON.get().defaultBlockState().setValue(CannonBlock.FACING, Direction.NORTH).setValue(CannonBlock.SIZE, 3);
            case 'S' -> ModBlocks.PASSENGER_SEAT.get().defaultBlockState().setValue(ShipPartBlock.FACING, Direction.NORTH);
            default -> null;
        };
    }

    /** A copy of a design with every hull plating block replaced (alien ships use their world's metal or stone). */
    public static ShipStructure withHull(ShipStructure design, BlockState hull) {
        List<ShipStructure.Entry> out = new ArrayList<>(design.entries.size());
        Block plating = ModBlocks.HULL_PLATING.get();
        for (ShipStructure.Entry e : design.entries) {
            out.add(e.state().is(plating) ? new ShipStructure.Entry(e.pos(), hull, Optional.empty()) : e);
        }
        return new ShipStructure(out, design.baseYaw);
    }
}
