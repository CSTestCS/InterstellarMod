package com.cstestcs.interstellar.core.gun;

import static org.junit.jupiter.api.Assertions.*;

import com.cstestcs.interstellar.core.space.ForeignWorlds;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Loads the shipped datapack gun JSON exactly like the game does and checks every alien theme can roll a gun. */
class DefaultGunDataTest {
    static GunRegistry load() throws Exception {
        Path root = Path.of("src/main/resources/data/interstellar/gun");
        GunRegistry.Builder b = new GunRegistry.Builder();
        for (String kind : new String[] { "archetype", "part", "material" }) {
            try (Stream<Path> files = Files.list(root.resolve(kind))) {
                for (Path f : files.sorted().toList()) {
                    String id = f.getFileName().toString().replace(".json", "");
                    var json = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                    switch (kind) {
                        case "archetype" -> b.archetype(id, json);
                        case "part" -> b.part(id, json);
                        default -> b.material(id, json);
                    }
                }
            }
        }
        return b.build();
    }

    @Test
    void shippedDataIsValidAndEveryThemeRolls() throws Exception {
        GunRegistry reg = load();
        assertTrue(reg.problems().isEmpty(), reg.problems().toString());
        SplittableRandom rng = new SplittableRandom(9);
        for (ForeignWorlds.World w : ForeignWorlds.all()) {
            for (int tier = 0; tier <= 5; tier++) {
                GunBuild build = reg.roll(w.theme().weaponTheme, tier, true, rng);
                GunRegistry.Stats s = reg.compute(build);
                assertTrue(s.alien(), build.archetype());
                assertTrue(s.complete(), "incomplete roll " + build);
                assertTrue(s.magazine() >= 1);
            }
        }
        for (GunData.Archetype a : reg.archetypes()) {
            for (GunData.Slot slot : a.required()) assertFalse(reg.partsFor(a.id(), slot).isEmpty(), a.id() + " has no " + slot + " part");
        }
    }
}
