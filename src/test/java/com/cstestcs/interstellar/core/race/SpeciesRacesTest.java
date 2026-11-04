package com.cstestcs.interstellar.core.race;

import static org.junit.jupiter.api.Assertions.*;

import com.cstestcs.interstellar.core.model.JavaModelInterpreter;
import com.cstestcs.interstellar.core.model.ModelDef;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The hand-designed species (Halo Covenant, mob-folk) produce valid race files with readable heads and skins. */
class SpeciesRacesTest {
    @Test
    void everySpeciesHasValidFilesAndAHead() throws Exception {
        Map<String, byte[]> files = DefaultRaces.files();
        List<RaceDetails> details = new ArrayList<>();
        for (var e : files.entrySet()) {
            if (e.getKey().endsWith("_details.txt")) {
                details.add(RaceDetailsParser.parse(e.getKey().replace("_details.txt", ""), new String(e.getValue(), StandardCharsets.UTF_8)));
            }
        }
        RaceRegistry reg = RaceRegistry.build(details);
        assertTrue(reg.problems().isEmpty(), reg.problems().toString());
        for (SpeciesRaces.Spec s : SpeciesRaces.specs()) {
            assertNotNull(reg.get(s.id()), s.id());
            assertNotNull(files.get(s.id() + "_skin.png"), s.id());
            assertNotNull(files.get(s.id() + "_headskin.png"), s.id());
            ModelDef head = JavaModelInterpreter.interpret(new String(files.get(s.id() + "_head.java"), StandardCharsets.UTF_8));
            assertTrue(head.root.find("head").cubeCount() >= 1, s.id() + " has an empty head");
            // The head must reach the neck pivot (y = 0) so it never floats above the body.
            float bottom = -99;
            for (ModelDef.CubeDef c : head.root.find("head").cubes) bottom = Math.max(bottom, c.y() + c.h());
            assertTrue(bottom >= -0.01f, s.id() + " head floats " + (-bottom) + " px above the body");
        }
        for (SpeciesRaces.Sub s : SpeciesRaces.subs()) assertEquals(s.parent(), reg.get(s.id()).parentId(), s.id());
        for (String id : SpeciesRaces.COVENANT) {
            assertEquals("covenant", reg.get(id).faction(), id);
            assertTrue(reg.get(id).homeDimension().matches("othermod:dimension", java.util.Set.of()), id);
            assertFalse(reg.get(id).homeDimension().matches("minecraft:overworld", java.util.Set.of()), id);
        }
        // The old procedural alien races are gone; every mob-folk species belongs to a multi-species faction.
        for (SpeciesRaces.Spec s : SpeciesRaces.specs()) {
            if (!SpeciesRaces.COVENANT.contains(s.id())) assertTrue(SpeciesRaces.FACTIONS.containsKey(s.faction()), s.id() + " has no faction");
        }
        assertTrue(SpeciesRaces.specs().size() >= 30);
    }
}
