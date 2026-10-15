package com.cstestcs.interstellar.core;

import static org.junit.jupiter.api.Assertions.*;

import com.cstestcs.interstellar.core.gun.GunBuild;
import com.cstestcs.interstellar.core.gun.GunData;
import com.cstestcs.interstellar.core.gun.GunRegistry;
import com.cstestcs.interstellar.core.ship.ShipScanner;
import com.cstestcs.interstellar.core.ship.ShipScanner.Kind;
import com.cstestcs.interstellar.core.space.ForeignWorlds;
import com.cstestcs.interstellar.core.space.PlanetLayout;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ShipPlanetGunTest {
    /** 3x3 gear base at y=1 on ground y=0, 3x3 deck at y=2 holding the required parts. */
    private static Map<Long, Kind> ship() {
        Map<Long, Kind> w = new HashMap<>();
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) w.put(ShipScanner.pack(x, 0, z), Kind.SOLID); // ground
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            w.put(ShipScanner.pack(x, 1, z), Kind.LANDING_GEAR);
            w.put(ShipScanner.pack(x, 2, z), Kind.SOLID);
        }
        w.put(ShipScanner.pack(0, 3, 0), Kind.CONTROL_PANEL);
        w.put(ShipScanner.pack(1, 3, 0), Kind.GENERATOR);
        w.put(ShipScanner.pack(2, 3, 0), Kind.LIFE_SUPPORT);
        return w;
    }

    @Test
    void validShipExcludesGround() {
        Map<Long, Kind> w = ship();
        ShipScanner.Result r = ShipScanner.scan(0, 3, 0, 100, (x, y, z) -> w.getOrDefault(ShipScanner.pack(x, y, z), Kind.AIR));
        assertTrue(r.ok(), r.problems().toString());
        assertEquals(21, r.blocks().size());
        assertEquals(1, r.minY());
    }

    @Test
    void reportsMissingPartsAndLimit() {
        Map<Long, Kind> w = ship();
        w.remove(ShipScanner.pack(1, 3, 0));
        ShipScanner.Result r = ShipScanner.scan(0, 3, 0, 10, (x, y, z) -> w.getOrDefault(ShipScanner.pack(x, y, z), Kind.AIR));
        assertTrue(r.problems().contains(ShipScanner.Problem.TOO_MANY_BLOCKS));
        r = ShipScanner.scan(0, 3, 0, 100, (x, y, z) -> w.getOrDefault(ShipScanner.pack(x, y, z), Kind.AIR));
        assertEquals(List.of(ShipScanner.Problem.NO_GENERATOR), r.problems());
    }

    @Test
    void planetsDoNotOverlapAndAreStable() {
        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (String d : List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end", "othermod:moon", "othermod:mars", "othermod:moon_b"))
            sizes.put(d, PlanetLayout.sizeFor(d, d.contains("nether") ? 8 : 1, Map.of()));
        for (ForeignWorlds.World fw : ForeignWorlds.all()) sizes.put(fw.dimensionId(), PlanetLayout.sizeFor(fw.dimensionId(), 1, Map.of()));
        assertEquals(16, sizes.get("minecraft:overworld"));
        assertEquals(1, sizes.get("minecraft:the_nether"));
        List<PlanetLayout.Planet> a = PlanetLayout.layout(sizes), b = PlanetLayout.layout(sizes);
        assertEquals(a, b);
        for (int i = 0; i < a.size(); i++) for (int j = i + 1; j < a.size(); j++) {
            int[] p = a.get(i).atmosphere(), q = a.get(j).atmosphere();
            boolean overlap = p[0] < q[3] && q[0] < p[3] && p[1] < q[4] && q[1] < p[4] && p[2] < q[5] && q[2] < p[5];
            assertFalse(overlap, a.get(i) + " overlaps " + a.get(j));
        }
        assertTrue(a.stream().filter(PlanetLayout.Planet::moon).count() >= 1);
    }

    private static GunRegistry guns() {
        GunRegistry.Builder b = new GunRegistry.Builder();
        b.archetype("rifle", JsonParser.parseString("""
                {"tier":1,"fire_mode":"auto","stats":{"damage":5,"fire_rate":600,"magazine":30},
                 "slots":["receiver","barrel","stock","sight"],"required":["receiver","barrel"],"themes":["human"]}""").getAsJsonObject());
        b.archetype("plasma", JsonParser.parseString("""
                {"alien":true,"tier":2,"ammo":"plasma","slots":["receiver","core"],"required":["receiver","core"],"themes":["plasma"]}""").getAsJsonObject());
        b.part("long_barrel", JsonParser.parseString("""
                {"slot":"barrel","modifiers":{"damage":{"add":1,"mul":1.5},"range":{"mul":2}}}""").getAsJsonObject());
        b.part("basic_receiver", JsonParser.parseString("{\"slot\":\"receiver\",\"fire_mode\":\"burst\"}").getAsJsonObject());
        b.part("plasma_core", JsonParser.parseString("{\"slot\":\"core\",\"alien\":true,\"effects\":[\"ignite\"],\"themes\":[\"plasma\"]}").getAsJsonObject());
        b.material("diamond", JsonParser.parseString("{\"item\":\"minecraft:diamond\",\"color\":\"#4ee1d6\",\"modifiers\":{\"damage\":{\"mul\":1.2}}}").getAsJsonObject());
        return b.build();
    }

    @Test
    void gunStatsStack() {
        GunRegistry reg = guns();
        assertTrue(reg.problems().isEmpty(), reg.problems().toString());
        GunBuild build = new GunBuild("rifle", Map.of(GunData.Slot.BARREL, new GunBuild.Choice("long_barrel", "diamond"),
                GunData.Slot.RECEIVER, new GunBuild.Choice("basic_receiver", null)), null, Map.of());
        GunRegistry.Stats s = reg.compute(build);
        assertEquals((5 + 1) * 1.5 * 1.2, s.get(GunData.Stat.DAMAGE), 1e-9);
        assertEquals(GunData.FireMode.BURST, s.fireMode());
        assertEquals(3, s.get(GunData.Stat.BURST));
        assertEquals(30, s.magazine());
        assertEquals(2, s.cooldownTicks());
        assertTrue(s.complete());
        assertEquals(0x4ee1d6, s.colors().get(GunData.Slot.BARREL));
        assertFalse(reg.compute(new GunBuild("rifle", Map.of(), null, Map.of())).complete());
    }

    @Test
    void alienRollsAreCompleteAndThemed() {
        GunRegistry reg = guns();
        SplittableRandom rng = new SplittableRandom(5);
        for (int i = 0; i < 50; i++) {
            GunBuild b = reg.roll("plasma", 2, true, rng);
            assertEquals("plasma", b.archetype());
            assertTrue(reg.compute(b).complete());
            assertEquals(GunData.AmmoType.PLASMA, reg.compute(b).ammo());
        }
    }
}
