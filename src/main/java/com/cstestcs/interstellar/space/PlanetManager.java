package com.cstestcs.interstellar.space;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.InterstellarConfig;
import com.cstestcs.interstellar.core.space.PlanetLayout;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Persistent record of which planet represents which dimension and where it sits in space. Planets keep their
 * position forever; dimensions added later (new mods) get the next free spiral slot, so nothing ever moves.
 */
public final class PlanetManager extends SavedData {
    private record Stored(String dimension, int size, int x, int y, int z, boolean moon, boolean built) {
        static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(Stored::dimension),
                Codec.INT.fieldOf("size").forGetter(Stored::size),
                Codec.INT.fieldOf("x").forGetter(Stored::x),
                Codec.INT.fieldOf("y").forGetter(Stored::y),
                Codec.INT.fieldOf("z").forGetter(Stored::z),
                Codec.BOOL.fieldOf("moon").forGetter(Stored::moon),
                Codec.BOOL.fieldOf("built").forGetter(Stored::built)
        ).apply(i, Stored::new));

        PlanetLayout.Planet planet() {
            return new PlanetLayout.Planet(dimension, size, x, y, z, moon);
        }
    }

    private static final Codec<PlanetManager> CODEC = RecordCodecBuilder.create(i -> i.group(
            Stored.CODEC.listOf().fieldOf("planets").forGetter(m -> new ArrayList<>(m.planets.values())),
            Codec.INT.fieldOf("next_slot").forGetter(m -> m.nextSlot)
    ).apply(i, PlanetManager::new));

    public static final SavedDataType<PlanetManager> TYPE = new SavedDataType<>(Interstellar.id("planets"),
            level -> new PlanetManager(List.of(), 0), level -> CODEC);

    private final Map<String, Stored> planets = new LinkedHashMap<>();
    private int nextSlot;

    private PlanetManager(List<Stored> list, int nextSlot) {
        for (Stored s : list) planets.put(s.dimension, s);
        this.nextSlot = nextSlot;
    }

    public static PlanetManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable PlanetLayout.Planet planetFor(ResourceKey<Level> dim) {
        Stored s = planets.get(dim.identifier().toString());
        return s == null ? null : s.planet();
    }

    public List<PlanetLayout.Planet> all() {
        List<PlanetLayout.Planet> out = new ArrayList<>();
        for (Stored s : planets.values()) out.add(s.planet());
        return out;
    }

    /** The planet whose atmosphere contains the point, if any. */
    public @Nullable PlanetLayout.Planet planetAt(double x, double y, double z) {
        for (Stored s : planets.values()) {
            PlanetLayout.Planet p = s.planet();
            if (p.insideAtmosphere(x, y, z)) return p;
        }
        return null;
    }

    /** Assigns slots to any dimension without a planet and builds every planet not yet built. */
    public void ensurePlanets(MinecraftServer server) {
        ServerLevel space = server.getLevel(SpaceTravel.SPACE);
        if (space == null) return;
        Map<String, Integer> overrides = new HashMap<>();
        for (String o : InterstellarConfig.SERVER.planetSizeOverrides.get()) {
            String[] kv = o.split("=");
            try {
                overrides.put(kv[0].strip(), Integer.parseInt(kv[1].strip()));
            } catch (RuntimeException ignored) {}
        }
        List<? extends String> excluded = InterstellarConfig.SERVER.excludedPlanetDimensions.get();
        List<ResourceKey<Level>> keys = new ArrayList<>(server.levelKeys());
        keys.sort((a, b) -> {
            boolean va = a.identifier().getNamespace().equals("minecraft"), vb = b.identifier().getNamespace().equals("minecraft");
            return va != vb ? (va ? -1 : 1) : a.identifier().compareTo(b.identifier());
        });
        // Main planets first so moons can find their hosts.
        for (int pass = 0; pass < 2; pass++) {
            for (ResourceKey<Level> key : keys) {
                String id = key.identifier().toString();
                if (key.equals(SpaceTravel.SPACE) || planets.containsKey(id) || excluded.contains(id)) continue;
                boolean moon = PlanetLayout.isMoonName(id);
                if ((pass == 0) == moon) continue;
                ServerLevel level = server.getLevel(key);
                double scale = level == null ? 1 : level.dimensionType().coordinateScale();
                int size = PlanetLayout.sizeFor(id, scale, overrides);
                int[] pos = null;
                if (moon) {
                    Stored host = hostFor(key.identifier());
                    if (host != null) {
                        int n = 1 + (int) planets.values().stream().filter(s -> s.moon && s.dimension.startsWith(key.identifier().getNamespace() + ":")).count();
                        pos = PlanetLayout.moonPosition(host.planet(), n);
                    }
                }
                if (pos == null) {
                    pos = PlanetLayout.spiralPosition(nextSlot++);
                    moon = false;
                }
                planets.put(id, new Stored(id, size, pos[0], pos[1], pos[2], moon, false));
                setDirty();
            }
        }
        for (Map.Entry<String, Stored> e : planets.entrySet()) {
            Stored s = e.getValue();
            if (s.built) continue;
            PlanetBuilder.build(space, s.planet(), server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(s.dimension))));
            e.setValue(new Stored(s.dimension, s.size, s.x, s.y, s.z, s.moon, true));
            setDirty();
        }
    }

    private @Nullable Stored hostFor(Identifier moon) {
        for (Stored s : planets.values()) if (!s.moon && s.dimension.startsWith(moon.getNamespace() + ":")) return s;
        return null;
    }

    /** Forces a rebuild of every planet (used by /interstellar planets rebuild). */
    public void markAllUnbuilt() {
        planets.replaceAll((k, s) -> new Stored(s.dimension, s.size, s.x, s.y, s.z, s.moon, false));
        setDirty();
    }
}
