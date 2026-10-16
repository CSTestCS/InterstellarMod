package com.cstestcs.interstellar.core;

import static org.junit.jupiter.api.Assertions.*;

import com.cstestcs.interstellar.core.space.ForeignWorlds;
import com.cstestcs.interstellar.core.space.PlanetGenome;
import com.cstestcs.interstellar.core.space.PlanetTypes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlanetGenomeTest {
    @Test
    void planetsAreDeterministicAndRoundTripThroughTheirDimensionId() {
        int found = 0;
        for (int cx = -40; cx <= 40; cx += 7) {
            for (int cz = -40; cz <= 40; cz += 5) {
                var g = PlanetGenome.at(cx, cz);
                if (g.isEmpty()) continue;
                found++;
                PlanetGenome p = g.get();
                assertEquals(p, PlanetGenome.at(cx, cz).orElseThrow());
                assertEquals(p, PlanetGenome.parse(p.dimensionId()).orElseThrow());
                assertTrue(p.dimensionId().matches("interstellar:[a-z0-9_]+"), p.dimensionId());
                // the planet and its atmosphere stay inside its cell
                int[] a = p.atmosphere();
                assertTrue(a[0] >= cx * PlanetGenome.CELL && a[3] <= (cx + 1) * PlanetGenome.CELL, p.toString());
                assertTrue(a[2] >= cz * PlanetGenome.CELL && a[5] <= (cz + 1) * PlanetGenome.CELL, p.toString());
                assertTrue(p.y() >= PlanetGenome.MIN_Y && p.y() < PlanetGenome.MAX_Y);
                assertFalse(p.biomes().isEmpty());
                assertTrue(p.species() >= 0 && p.species() < ForeignWorlds.COUNT);
                assertSame(ForeignWorlds.all().get(p.species()), ForeignWorlds.byDimension(p.dimensionId()));
            }
        }
        assertTrue(found > 50, "most cells hold a planet: " + found);
    }

    @Test
    void theOriginIsLeftToRealDimensionsAndSpaceIsFull() {
        assertTrue(PlanetGenome.near(0, 0, 300).isEmpty());
        List<PlanetGenome> far = PlanetGenome.near(5000, -7000, 1000);
        assertTrue(far.size() > 60, "planets everywhere further out: " + far.size());
        Set<String> names = new HashSet<>();
        Set<PlanetGenome.Terrain> terrains = new HashSet<>();
        for (PlanetGenome g : far) {
            names.add(g.name());
            terrains.add(g.terrain());
            int[] a = g.atmosphere();
            if (!g.blob()) assertEquals(g, PlanetGenome.atmosphereAt(a[0] + 0.5, a[1] + 0.5, a[2] + 0.5).orElseThrow());
            assertEquals(g, PlanetGenome.atmosphereAt(g.centerX(), g.centerY(), g.centerZ()).orElseThrow());
        }
        assertTrue(names.size() > far.size() * 0.9);
        assertTrue(terrains.size() >= 5, terrains.toString());
        assertTrue(PlanetGenome.parse("minecraft:overworld").isEmpty());
        assertTrue(PlanetGenome.parse("interstellar:planet_m3_17").isPresent() || PlanetGenome.at(-3, 17).isEmpty());
        assertEquals(PlanetTypes.COUNT, PlanetTypes.all().size());
    }

    @Test
    void endPlanetsAreIrregularRocksWithAHuggingAtmosphere() {
        PlanetGenome blob = PlanetGenome.near(9000, 9000, 3000).stream().filter(PlanetGenome::blob).findFirst().orElseThrow();
        int[] a = blob.atmosphere();
        int solid = 0, shell = 0, cube = blob.size() * blob.size() * blob.size();
        for (int x = a[0]; x < a[3]; x++) for (int y = a[1]; y < a[4]; y++) for (int z = a[2]; z < a[5]; z++) {
            if (blob.solidAt(x, y, z)) {
                solid++;
                assertFalse(blob.atmosphereShellAt(x, y, z));
            }
            if (blob.atmosphereShellAt(x, y, z)) {
                shell++;
                assertTrue(blob.touchesAtmosphere(x + 0.5, y + 0.5, z + 0.5, x + 0.5, y + 0.5, z + 0.5));
            }
        }
        // about as much rock as the cube it replaces, and no solid block outside the bounds
        assertTrue(solid > cube * 0.3 && solid < cube * 1.6, solid + " vs " + cube);
        assertTrue(shell > 0);
        // corners of the bounding box are outside the atmosphere (it hugs the rock, not the box)
        assertFalse(blob.touchesAtmosphere(a[0], a[1], a[2], a[0] + 0.1, a[1] + 0.1, a[2] + 0.1));
    }
}
