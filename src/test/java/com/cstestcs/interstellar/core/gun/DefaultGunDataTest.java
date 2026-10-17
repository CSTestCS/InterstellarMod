package com.cstestcs.interstellar.core.gun;

import static org.junit.jupiter.api.Assertions.*;

import com.cstestcs.interstellar.core.space.ForeignWorlds;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    /** Swapping any single option must change how the weapon behaves: unique effect sets per slot, known effect ids. */
    @Test
    void everyOptionHasItsOwnKnownEffects() throws Exception {
        GunRegistry reg = load();
        for (GunData.Part p : reg.parts()) {
            assertFalse(p.effects().isEmpty(), p.id() + " has no effect");
            for (String fx : p.effects()) assertTrue(EffectCatalog.known(fx), p.id() + ": unknown effect " + fx);
        }
        Set<Set<String>> seen = new HashSet<>();
        for (GunData.Material m : reg.materials()) {
            assertFalse(m.effects().isEmpty(), m.id() + " has no effect");
            for (String fx : m.effects()) assertTrue(EffectCatalog.known(fx), m.id() + ": unknown effect " + fx);
            assertTrue(seen.add(Set.copyOf(m.effects())), m.id() + " repeats another material's effects");
        }
        for (GunData.Archetype a : reg.archetypes()) {
            for (GunData.Slot slot : a.slots()) {
                Set<Set<String>> sets = new HashSet<>();
                for (GunData.Part p : reg.partsFor(a.id(), slot)) {
                    assertTrue(sets.add(Set.copyOf(p.effects())), a.id() + "/" + slot + ": " + p.id() + " behaves like another part");
                }
            }
        }
    }

    @Test
    void powerSourcesColourShots() throws Exception {
        GunRegistry reg = load();
        GunData.Archetype rifle = reg.archetype("plasma_rifle");
        List<GunData.Part> cores = reg.partsFor(rifle.id(), GunData.Slot.CORE);
        Set<Integer> colours = new HashSet<>();
        for (GunData.Part core : cores) {
            assertNotNull(core.color(), core.id());
            java.util.Map<GunData.Slot, GunBuild.Choice> parts = new java.util.EnumMap<>(GunData.Slot.class);
            for (GunData.Slot slot : rifle.required()) parts.put(slot, new GunBuild.Choice(reg.partsFor(rifle.id(), slot).get(0).id(), "iron"));
            parts.put(GunData.Slot.CORE, new GunBuild.Choice(core.id(), "iron"));
            colours.add(reg.compute(new GunBuild(rifle.id(), parts, null, java.util.Map.of())).powerColor());
        }
        assertTrue(colours.size() >= 11, "cores of different themes must colour shots differently: " + colours.size());
        for (GunData.Archetype a : reg.archetypes()) {
            if (a.fireMode() == GunData.FireMode.MELEE) assertNotNull(a.bladeStyle(), a.id());
        }
    }
}
