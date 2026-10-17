package com.cstestcs.interstellar.core.gun;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Every effect id a weapon part or material can carry, and when it acts. The game side implements each one in
 * {@code gun.GunEffects}; a data effect missing from this list does nothing (and fails the data test).
 */
public final class EffectCatalog {
    public enum Kind {
        /** On the target when a shot or swing connects. */
        HIT,
        /** Changes the damage of a hit (before it is dealt). */
        DAMAGE,
        /** Blades only: how swings work. */
        MELEE,
        /** When the weapon fires or is held. */
        FIRE,
        /** How shots travel (makes ballistic guns fire visible rounds). */
        PATH,
        /** Where a shot lands (entity or block). */
        IMPACT
    }

    private static final Map<String, Kind> ALL = new LinkedHashMap<>();

    static {
        for (String id : new String[] { "ignite", "freeze", "poison", "wither", "levitate", "blind", "knockback", "shatter", "chain",
                "leech", "pull", "launch", "stagger", "weaken", "nausea", "mark", "warp", "lightning", "echo", "disarm", "famish",
                "darkness", "shockwave", "plague", "reaper", "slow", "bleed", "adrenaline" }) ALL.put(id, Kind.HIT);
        for (String id : new String[] { "execute", "armor_shred", "volatile" }) ALL.put(id, Kind.DAMAGE);
        for (String id : new String[] { "sweep", "lunge", "slam", "parry" }) ALL.put(id, Kind.MELEE);
        for (String id : new String[] { "silent", "ammo_saver", "steadfast", "spray", "grenade", "bayonet" }) ALL.put(id, Kind.FIRE);
        for (String id : new String[] { "pierce", "ricochet", "homing", "split", "accelerate", "arc", "phase" }) ALL.put(id, Kind.PATH);
        for (String id : new String[] { "cluster", "gravity_well", "concussive" }) ALL.put(id, Kind.IMPACT);
    }

    private EffectCatalog() {}

    public static Map<String, Kind> all() {
        return Collections.unmodifiableMap(ALL);
    }

    public static boolean known(String id) {
        return ALL.containsKey(id);
    }

    public static Kind kind(String id) {
        return ALL.get(id);
    }

    /** Path effects that need a visible, travelling round (hitscan can pierce on its own). */
    public static final Set<String> NEEDS_PROJECTILE = Set.of("ricochet", "homing", "split", "accelerate", "arc", "phase");
}
