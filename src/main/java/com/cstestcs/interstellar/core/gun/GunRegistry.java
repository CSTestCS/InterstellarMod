package com.cstestcs.interstellar.core.gun;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Holds all gun archetypes, parts and materials, computes final stats for a {@link GunBuild} and rolls random
 * builds for aliens. Built from JSON so packs can add content without code.
 *
 * <pre>
 * archetype: {"alien":false,"tier":1,"fire_mode":"auto","ammo":"ballistic","magazine":"rifle",
 *             "stats":{"damage":5,"fire_rate":600},"slots":["receiver","barrel",...],"required":["receiver","barrel"],
 *             "themes":["human"]}
 * part:      {"slot":"barrel","tier":2,"alien":false,"archetypes":["assault_rifle"],
 *             "modifiers":{"damage":{"add":1,"mul":1.1}},"fire_mode":"burst","effects":["ignite"],"model":"interstellar:gun/barrel/long"}
 * material:  {"item":"minecraft:diamond","color":"#4ee1d6","tier":3,"modifiers":{...},"effects":[...]}
 * </pre>
 */
public final class GunRegistry {
    public static final GunRegistry EMPTY = new GunRegistry(Map.of(), Map.of(), Map.of(), List.of());

    private final Map<String, GunData.Archetype> archetypes;
    private final Map<String, GunData.Part> parts;
    private final Map<String, GunData.Material> materials;
    private final Map<String, GunData.Material> materialByItem = new HashMap<>();
    private final List<String> problems;

    public GunRegistry(Map<String, GunData.Archetype> archetypes, Map<String, GunData.Part> parts, Map<String, GunData.Material> materials, List<String> problems) {
        this.archetypes = Collections.unmodifiableMap(new LinkedHashMap<>(archetypes));
        this.parts = Collections.unmodifiableMap(new LinkedHashMap<>(parts));
        this.materials = Collections.unmodifiableMap(new LinkedHashMap<>(materials));
        for (GunData.Material m : materials.values()) if (m.item() != null) materialByItem.put(m.item(), m);
        this.problems = List.copyOf(problems);
    }

    public GunData.Archetype archetype(String id) { return archetypes.get(id); }
    public GunData.Part part(String id) { return parts.get(id); }
    public GunData.Material material(String id) { return materials.get(id); }
    public GunData.Material materialForItem(String itemId) { return materialByItem.get(itemId); }
    public Collection<GunData.Archetype> archetypes() { return archetypes.values(); }
    public Collection<GunData.Part> parts() { return parts.values(); }
    public Collection<GunData.Material> materials() { return materials.values(); }
    public List<String> problems() { return problems; }

    public List<GunData.Part> partsFor(String archetype, GunData.Slot slot) {
        List<GunData.Part> out = new ArrayList<>();
        for (GunData.Part p : parts.values()) {
            if (p.slot() == slot && (p.archetypes().isEmpty() || p.archetypes().contains(archetype))) out.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------------ stats

    public record Stats(Map<GunData.Stat, Double> values, GunData.FireMode fireMode, GunData.AmmoType ammo, List<String> effects,
            Map<GunData.Slot, Integer> colors, String magazineKind, boolean alien, int tier, boolean complete) {
        public double get(GunData.Stat s) {
            return values.getOrDefault(s, s.base);
        }

        public int magazine() {
            return (int) Math.round(get(GunData.Stat.MAGAZINE));
        }

        /** Ticks between shots (min 1). */
        public int cooldownTicks() {
            return Math.max(1, (int) Math.round(1200.0 / get(GunData.Stat.FIRE_RATE)));
        }

        public int reloadTicks() {
            return Math.max(1, (int) Math.round(get(GunData.Stat.RELOAD_TIME) * 20));
        }
    }

    public Stats compute(GunBuild build) {
        GunData.Archetype arch = archetypes.get(build.archetype());
        if (arch == null) return null;
        Map<GunData.Stat, GunData.Modifier> mods = new EnumMap<>(GunData.Stat.class);
        GunData.FireMode mode = arch.fireMode();
        GunData.AmmoType ammo = arch.ammo();
        List<String> effects = new ArrayList<>();
        Map<GunData.Slot, Integer> colors = new EnumMap<>(GunData.Slot.class);
        int tier = arch.tier();
        boolean complete = true;
        for (GunData.Slot req : arch.required()) if (!build.parts().containsKey(req)) complete = false;
        for (Map.Entry<GunData.Slot, GunBuild.Choice> e : build.parts().entrySet()) {
            if (!arch.slots().contains(e.getKey())) continue;
            GunData.Part part = parts.get(e.getValue().part());
            if (part == null || part.slot() != e.getKey()) continue;
            part.modifiers().forEach((s, m) -> mods.merge(s, m, GunData.Modifier::combine));
            if (part.fireModeOverride() != null) mode = part.fireModeOverride();
            if (part.ammoOverride() != null) ammo = part.ammoOverride();
            effects.addAll(part.effects());
            tier = Math.max(tier, part.tier());
            GunData.Material mat = e.getValue().material() == null ? null : materials.get(e.getValue().material());
            if (mat != null) {
                mat.modifiers().forEach((s, m) -> mods.merge(s, m, GunData.Modifier::combine));
                for (String fx : mat.effects()) if (!effects.contains(fx)) effects.add(fx);
                colors.put(e.getKey(), mat.color());
                tier = Math.max(tier, mat.tier());
            }
        }
        if (build.trim() != null) {
            // Trims are mostly cosmetic: a small handling bonus.
            mods.merge(GunData.Stat.RECOIL, new GunData.Modifier(0, 0.95), GunData.Modifier::combine);
        }
        Map<GunData.Stat, Double> values = new EnumMap<>(GunData.Stat.class);
        for (GunData.Stat s : GunData.Stat.values()) {
            double base = arch.stats().getOrDefault(s, s.base);
            GunData.Modifier m = mods.getOrDefault(s, GunData.Modifier.NONE);
            double v = (base + m.add()) * m.mul() * build.rolls().getOrDefault(s, 1.0);
            values.put(s, Math.max(s.min, Math.min(s.max, v)));
        }
        if (mode == GunData.FireMode.BURST && values.get(GunData.Stat.BURST) < 2) values.put(GunData.Stat.BURST, 3.0);
        return new Stats(values, mode, ammo, List.copyOf(effects), colors, arch.magazineKind(), arch.alien(), tier, complete);
    }

    // ------------------------------------------------------------------ random builds (aliens)

    /**
     * Rolls a complete gun matching a theme (e.g. "plasma", "crystal") and tier. Archetypes and parts tagged with the
     * theme are preferred; stats get a random ±(5 + 5*tier)% roll each.
     */
    public GunBuild roll(String theme, int tier, boolean alien, RandomGenerator rng) {
        List<GunData.Archetype> pool = new ArrayList<>();
        for (GunData.Archetype a : archetypes.values()) {
            if (a.alien() == alien && a.tier() <= Math.max(1, tier + 1) && (a.themes().isEmpty() || a.themes().contains(theme))) pool.add(a);
        }
        if (pool.isEmpty()) for (GunData.Archetype a : archetypes.values()) if (a.alien() == alien) pool.add(a);
        if (pool.isEmpty()) pool.addAll(archetypes.values());
        if (pool.isEmpty()) return null;
        GunData.Archetype arch = pool.get(rng.nextInt(pool.size()));
        Map<GunData.Slot, GunBuild.Choice> chosen = new EnumMap<>(GunData.Slot.class);
        List<GunData.Material> mats = new ArrayList<>();
        for (GunData.Material m : materials.values()) if (m.tier() <= tier + 1 && (m.themes().isEmpty() || m.themes().contains(theme) || !alien)) mats.add(m);
        for (GunData.Slot slot : arch.slots()) {
            boolean req = arch.required().contains(slot);
            if (!req && rng.nextDouble() > 0.35 + 0.12 * tier) continue;
            List<GunData.Part> options = new ArrayList<>();
            for (GunData.Part p : partsFor(arch.id(), slot)) {
                if (p.tier() <= tier + 1 && (p.themes().isEmpty() || p.themes().contains(theme))) options.add(p);
            }
            if (options.isEmpty()) options = partsFor(arch.id(), slot);
            if (options.isEmpty()) continue;
            GunData.Part part = options.get(rng.nextInt(options.size()));
            String mat = mats.isEmpty() ? null : mats.get(rng.nextInt(mats.size())).id();
            chosen.put(slot, new GunBuild.Choice(part.id(), mat));
        }
        Map<GunData.Stat, Double> rolls = new EnumMap<>(GunData.Stat.class);
        double spread = 0.05 + 0.05 * tier;
        for (GunData.Stat s : new GunData.Stat[] { GunData.Stat.DAMAGE, GunData.Stat.FIRE_RATE, GunData.Stat.RELOAD_TIME,
                GunData.Stat.SPREAD, GunData.Stat.RECOIL, GunData.Stat.RANGE, GunData.Stat.MAGAZINE }) {
            rolls.put(s, 1 + (rng.nextDouble() * 2 - 1) * spread);
        }
        return new GunBuild(arch.id(), chosen, null, rolls);
    }

    // ------------------------------------------------------------------ JSON

    public static final class Builder {
        private final Map<String, GunData.Archetype> archetypes = new LinkedHashMap<>();
        private final Map<String, GunData.Part> parts = new LinkedHashMap<>();
        private final Map<String, GunData.Material> materials = new LinkedHashMap<>();
        private final List<String> problems = new ArrayList<>();

        public Builder archetype(String id, JsonObject o) {
            try {
                Map<GunData.Stat, Double> stats = new EnumMap<>(GunData.Stat.class);
                if (o.has("stats")) {
                    for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("stats").entrySet()) {
                        GunData.Stat s = GunData.Stat.byKey(e.getKey());
                        if (s == null) problems.add(id + ": unknown stat " + e.getKey());
                        else stats.put(s, e.getValue().getAsDouble());
                    }
                }
                archetypes.put(id, new GunData.Archetype(id, bool(o, "alien"), integer(o, "tier", 1),
                        enumOf(GunData.FireMode.class, str(o, "fire_mode", "semi")), enumOf(GunData.AmmoType.class, str(o, "ammo", "ballistic")),
                        stats, slots(o.get("slots")), slots(o.get("required")), str(o, "magazine", id), strings(o.get("themes"))));
            } catch (RuntimeException e) {
                problems.add("archetype " + id + ": " + e.getMessage());
            }
            return this;
        }

        public Builder part(String id, JsonObject o) {
            try {
                GunData.Slot slot = GunData.Slot.byKey(str(o, "slot", ""));
                if (slot == null) throw new IllegalArgumentException("missing/unknown slot");
                String fm = str(o, "fire_mode", null), am = str(o, "ammo", null);
                parts.put(id, new GunData.Part(id, slot, bool(o, "alien"), integer(o, "tier", 1), Set.copyOf(strings(o.get("archetypes"))),
                        mods(id, o.get("modifiers")), fm == null ? null : enumOf(GunData.FireMode.class, fm),
                        am == null ? null : enumOf(GunData.AmmoType.class, am), strings(o.get("effects")), str(o, "model", null), strings(o.get("themes"))));
            } catch (RuntimeException e) {
                problems.add("part " + id + ": " + e.getMessage());
            }
            return this;
        }

        public Builder material(String id, JsonObject o) {
            try {
                materials.put(id, new GunData.Material(id, str(o, "item", null), color(str(o, "color", "#ffffff")), integer(o, "tier", 1),
                        mods(id, o.get("modifiers")), strings(o.get("effects")), bool(o, "alien"), strings(o.get("themes"))));
            } catch (RuntimeException e) {
                problems.add("material " + id + ": " + e.getMessage());
            }
            return this;
        }

        public GunRegistry build() {
            for (GunData.Part p : parts.values()) {
                for (String a : p.archetypes()) if (!archetypes.containsKey(a)) problems.add("part " + p.id() + " references unknown archetype " + a);
            }
            return new GunRegistry(archetypes, parts, materials, problems);
        }

        private Map<GunData.Stat, GunData.Modifier> mods(String id, JsonElement el) {
            Map<GunData.Stat, GunData.Modifier> out = new EnumMap<>(GunData.Stat.class);
            if (el == null || !el.isJsonObject()) return out;
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                GunData.Stat s = GunData.Stat.byKey(e.getKey());
                if (s == null) {
                    problems.add(id + ": unknown stat " + e.getKey());
                    continue;
                }
                if (e.getValue().isJsonPrimitive()) out.put(s, new GunData.Modifier(e.getValue().getAsDouble(), 1));
                else {
                    JsonObject m = e.getValue().getAsJsonObject();
                    out.put(s, new GunData.Modifier(m.has("add") ? m.get("add").getAsDouble() : 0, m.has("mul") ? m.get("mul").getAsDouble() : 1));
                }
            }
            return out;
        }
    }

    private static Set<GunData.Slot> slots(JsonElement el) {
        Set<GunData.Slot> out = EnumSet.noneOf(GunData.Slot.class);
        for (String s : strings(el)) {
            GunData.Slot slot = GunData.Slot.byKey(s);
            if (slot == null) throw new IllegalArgumentException("unknown slot " + s);
            out.add(slot);
        }
        return out;
    }

    static List<String> strings(JsonElement el) {
        List<String> out = new ArrayList<>();
        if (el == null) return out;
        if (el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            for (JsonElement e : a) out.add(e.getAsString());
        } else if (el.isJsonPrimitive()) out.add(el.getAsString());
        return out;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) ? o.get(k).getAsString() : def;
    }

    private static boolean bool(JsonObject o, String k) {
        return o.has(k) && o.get(k).getAsBoolean();
    }

    private static int integer(JsonObject o, String k, int def) {
        return o.has(k) ? o.get(k).getAsInt() : def;
    }

    private static <E extends Enum<E>> E enumOf(Class<E> cls, String s) {
        return Enum.valueOf(cls, s.toUpperCase(Locale.ROOT));
    }

    static int color(String s) {
        String h = s.startsWith("#") ? s.substring(1) : s;
        return (int) Long.parseLong(h, 16) & 0xFFFFFF;
    }
}
