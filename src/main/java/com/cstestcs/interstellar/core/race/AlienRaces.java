package com.cstestcs.interstellar.core.race;

import java.util.SplittableRandom;


/**
 * Helpers shared by the alien species. (Earlier versions generated one procedural race per foreign world here; the
 * hand-designed species in {@link SpeciesRaces} replaced them, and unedited old files are removed on start.)
 */
public final class AlienRaces {
    private AlienRaces() {}

    /**
     * The weapons a species carries: always guns, plus two or three others among energy swords, bows, crossbows,
     * tridents and melee weapons (each alien picks one of the list when it spawns; guns are listed twice to stay the
     * most common).
     */
    static String weapons(SplittableRandom r) {
        String[] others = { "blade", "blade", "bow", "crossbow", "trident", "sword", "axe", "mace" };
        java.util.List<String> out = new java.util.ArrayList<>(java.util.List.of("gun", "gun"));
        int n = 2 + r.nextInt(2);
        while (out.size() < 2 + n) {
            String w = others[r.nextInt(others.length)];
            if (!out.contains(w)) out.add(w);
        }
        return String.join(", ", out);
    }
}
