package com.cstestcs.interstellar.core.gun;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Immutable definitions that make up the data-driven gun system (loaded from datapack JSON). */
public final class GunData {
    private GunData() {}

    /** Every stat a gun has. Parts and materials modify these additively and/or multiplicatively. */
    public enum Stat {
        DAMAGE(6, 0.5, 200),
        FIRE_RATE(300, 20, 2000),          // rounds per minute
        MAGAZINE(12, 1, 500),
        RELOAD_TIME(2.0, 0.2, 10),         // seconds
        SPREAD(2.0, 0, 30),                // degrees
        RECOIL(1.0, 0, 20),                // degrees of pitch kick
        RANGE(48, 4, 512),                 // blocks
        VELOCITY(4.0, 0.5, 20),            // blocks per tick
        PELLETS(1, 1, 24),
        BURST(1, 1, 10),
        CHARGE_TIME(0, 0, 5),              // seconds, energy weapons
        HEADSHOT(1.5, 1, 5),
        ARMOR_PIERCE(0, 0, 1),             // fraction of armour ignored
        ZOOM(1, 1, 12),
        EFFECT_POWER(0, 0, 10);

        public final double base, min, max;

        Stat(double base, double min, double max) {
            this.base = base;
            this.min = min;
            this.max = max;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Stat byKey(String key) {
            for (Stat s : values()) if (s.key().equals(key)) return s;
            return null;
        }
    }

    public enum Slot {
        RECEIVER, BARREL, STOCK, GRIP, MAGAZINE, SIGHT, MUZZLE, UNDERBARREL, CORE;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Slot byKey(String key) {
            for (Slot s : values()) if (s.key().equals(key)) return s;
            return null;
        }
    }

    public enum FireMode { SEMI, AUTO, BURST, CHARGE }

    public enum AmmoType { BALLISTIC, ENERGY, PLASMA, EXPLOSIVE, NEEDLE }

    /** Stat modifier: value = (base + add) * mul. */
    public record Modifier(double add, double mul) {
        public static final Modifier NONE = new Modifier(0, 1);

        public Modifier combine(Modifier o) {
            return new Modifier(add + o.add, mul * o.mul);
        }
    }

    /**
     * A weapon family (pistol, assault rifle, plasma rifle, ...). Base stats, which slots exist, which are required,
     * and whether it is human (overworld-craftable) or alien tech.
     */
    public record Archetype(String id, boolean alien, int tier, FireMode fireMode, AmmoType ammo,
            Map<Stat, Double> stats, Set<Slot> slots, Set<Slot> required, String magazineKind, List<String> themes) {}

    /** A swappable part. {@code archetypes} empty = fits every archetype that has the slot. */
    public record Part(String id, Slot slot, boolean alien, int tier, Set<String> archetypes, Map<Stat, Modifier> modifiers,
            FireMode fireModeOverride, AmmoType ammoOverride, List<String> effects, String model, List<String> themes) {}

    /** Material a part is made from: colour tint, stat changes and on-hit effects. Looked up by item id. */
    public record Material(String id, String item, int color, int tier, Map<Stat, Modifier> modifiers, List<String> effects,
            boolean alien, List<String> themes) {}

    /** Trim support: trim materials recolour the gun's trim layer and give a small bonus. */
    public record Trim(String pattern, String material, int color) {}

    public static Map<Stat, Modifier> modifiers() {
        return new EnumMap<>(Stat.class);
    }
}
