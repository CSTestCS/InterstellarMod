package com.cstestcs.interstellar.core.race;

import static org.junit.jupiter.api.Assertions.*;

import com.cstestcs.interstellar.core.model.JavaModelInterpreter;
import com.cstestcs.interstellar.core.model.ModelDef;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class RaceRegistryTest {
    private static RaceDetails d(String id, String text) {
        return RaceDetailsParser.parse(id, text);
    }

    @Test
    void parsesKeysCaseInsensitivelyAndSkipsComments() {
        RaceDetails r = d("Elf", """
                # comment
                Race Name: Wood Elf
                gender: Female
                HomeBiome: #minecraft:is_forest, !minecraft:dark_forest   # trailing comment
                PopulationPercent: 25%
                Bogus: 1
                Weapons:
                """);
        assertEquals("elf", r.id());
        assertEquals("Wood Elf", r.get(RaceDetails.Key.RACE_NAME));
        assertEquals("#minecraft:is_forest, !minecraft:dark_forest", r.get(RaceDetails.Key.HOME_BIOME));
        assertNull(r.get(RaceDetails.Key.WEAPONS));
        assertEquals(1, r.warnings().size());
    }

    @Test
    void subraceInheritsEmptySettings() {
        RaceRegistry reg = RaceRegistry.build(List.of(
                d("elf", "RaceName: Elf\nGender: female\nFavoriteItem: minecraft:apple\nFavoriteItemPotency: 30\nRaceSize: 0.9\nFallback: true"),
                d("high_elf", "Subrace: Elf\nRaceName: High Elf\nRaceSize: 1.1")));
        RaceDefinition high = reg.get("high_elf");
        assertEquals("elf", high.parentId());
        assertEquals(RaceDefinition.Gender.FEMALE, high.gender());
        assertEquals("minecraft:apple", high.favoriteItem());
        assertEquals(30, high.favoriteItemPotency());
        assertEquals(1.1f, high.size(), 1e-6);
        assertFalse(high.fallback(), "Fallback must not be inherited");
        assertTrue(reg.get("elf").fallback());
    }

    @Test
    void cyclesAndMissingParentsAreReportedNotFatal() {
        RaceRegistry reg = RaceRegistry.build(List.of(d("a", "Subrace: b"), d("b", "Subrace: a"), d("c", "Subrace: nope")));
        assertEquals(3, reg.size());
        assertFalse(reg.problems().isEmpty());
        assertFalse(reg.get("c").isSubrace());
    }

    @Test
    void matchersSupportTagsNamespacesAndNegation() {
        IdMatcher m = IdMatcher.parse("#minecraft:is_forest, mymod:*, !minecraft:dark_forest, plains");
        assertTrue(m.matches("minecraft:birch_forest", Set.of("minecraft:is_forest")));
        assertFalse(m.matches("minecraft:dark_forest", Set.of("minecraft:is_forest")));
        assertTrue(m.matches("mymod:anything", Set.of()));
        assertTrue(m.matches("minecraft:plains", Set.of()));
        assertFalse(m.matches("minecraft:desert", Set.of()));
        assertTrue(IdMatcher.parse("").matches("x:y", Set.of()));
    }

    @Test
    void selectionUsesPopulationThenFallbackThenSubraces() {
        RaceRegistry reg = RaceRegistry.build(List.of(
                d("zombie", "MobConstraint: minecraft:zombie\nFallback: true\nPopulationPercent: 100"),
                d("ghoul", "MobConstraint: minecraft:zombie\nPopulationPercent: 30"),
                d("night_ghoul", "Subrace: ghoul\nPopulationPercent: 50\nHomeDimension: minecraft:the_nether")));
        SpawnContext overworld = SpawnContext.of("minecraft:zombie", "minecraft:overworld", "minecraft:plains");
        SpawnContext nether = SpawnContext.of("minecraft:zombie", "minecraft:the_nether", "minecraft:nether_wastes");
        SplittableRandom rng = new SplittableRandom(1);
        int ghoul = 0, zombie = 0, night = 0, n = 20000;
        for (int i = 0; i < n; i++) {
            String id = reg.select(overworld, rng).id();
            if (id.equals("ghoul")) ghoul++;
            else if (id.equals("zombie")) zombie++;
            else fail("night ghoul outside the nether: " + id);
            if (reg.select(nether, rng).id().equals("night_ghoul")) night++;
        }
        assertEquals(0.30, ghoul / (double) n, 0.02);
        assertEquals(0.70, zombie / (double) n, 0.02);
        assertEquals(0.15, night / (double) n, 0.02);
        assertNull(reg.select(SpawnContext.of("minecraft:skeleton", "minecraft:overworld", "minecraft:plains"), rng));
    }

    @Test
    void fileGroupingFindsVariants() {
        Map<String, RaceFiles.Entry> g = RaceFiles.group(List.of("Zombie_Villager_details.txt", "zombie_villager_skin.png",
                "zombie_villager_skin_02.png", "zombie_villager_skin_1.png", "zombie_villager_headskin.png", "zombie_villager_head.java", "notes.md"));
        RaceFiles.Entry e = g.get("zombie_villager");
        assertNotNull(e);
        assertEquals(List.of("zombie_villager_skin.png", "zombie_villager_skin_1.png", "zombie_villager_skin_02.png"), e.skins);
        assertEquals(List.of("zombie_villager_headskin.png"), e.headSkins);
        assertTrue(e.hasHeadModel());
        assertEquals(1, g.size());
    }

    @Test
    void defaultFilesParseAndResolve() throws Exception {
        Map<String, byte[]> files = DefaultRaces.files();
        List<RaceDetails> details = new ArrayList<>();
        for (var e : files.entrySet()) {
            if (e.getKey().endsWith("_details.txt")) {
                details.add(RaceDetailsParser.parse(e.getKey().replace("_details.txt", ""), new String(e.getValue(), StandardCharsets.UTF_8)));
            }
            if (e.getKey().endsWith("_head.java")) {
                ModelDef def = JavaModelInterpreter.interpret(new String(e.getValue(), StandardCharsets.UTF_8));
                assertNotNull(def.root.find("head"));
            }
            if (e.getKey().endsWith(".png")) assertEquals((byte) 0x89, e.getValue()[0]);
        }
        RaceRegistry reg = RaceRegistry.build(details);
        assertTrue(reg.problems().isEmpty(), reg.problems().toString());
        assertTrue(reg.size() >= DefaultRaces.VANILLA_BIPEDS.size());
        SpawnContext ctx = SpawnContext.of("minecraft:villager", "minecraft:overworld", "minecraft:plains");
        assertEquals("villager", reg.select(ctx, new SplittableRandom(3)).id());
    }
}
