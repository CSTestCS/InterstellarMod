package com.cstestcs.interstellar.core.race;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.random.RandomGenerator;

/**
 * Resolves subrace inheritance and performs race selection for spawning mobs.
 * Built once per (re)load, after which it is immutable and lock-free to read.
 */
public final class RaceRegistry {
    public static final RaceRegistry EMPTY = new RaceRegistry(Map.of(), Map.of(), List.of());

    private final Map<String, RaceDefinition> races;
    private final Map<String, List<RaceDefinition>> children;
    private final List<RaceDefinition> roots;
    private final List<String> problems;

    private RaceRegistry(Map<String, RaceDefinition> races, Map<String, List<RaceDefinition>> children, List<String> problems) {
        this.races = races;
        this.children = children;
        List<RaceDefinition> r = new ArrayList<>();
        for (RaceDefinition d : races.values()) if (!d.isSubrace()) r.add(d);
        r.sort(Comparator.comparing(RaceDefinition::id));
        this.roots = List.copyOf(r);
        this.problems = List.copyOf(problems);
    }

    public static RaceRegistry build(Collection<RaceDetails> details) {
        Map<String, RaceDetails> byId = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        for (RaceDetails d : details) {
            if (byId.put(d.id(), d) != null) problems.add("duplicate race id '" + d.id() + "'");
            for (String w : d.warnings()) problems.add(d.id() + ": " + w);
        }
        Map<String, Map<RaceDetails.Key, String>> resolvedRaw = new HashMap<>();
        Map<String, RaceDefinition> out = new TreeMap<>();
        for (String id : byId.keySet()) resolveRaw(id, byId, resolvedRaw, new LinkedHashSet<>(), problems);
        for (String id : byId.keySet()) out.put(id, toDefinition(id, resolvedRaw.get(id), problems));

        Map<String, List<RaceDefinition>> kids = new HashMap<>();
        for (RaceDefinition d : out.values()) {
            if (d.isSubrace()) kids.computeIfAbsent(d.parentId(), k -> new ArrayList<>()).add(d);
        }
        kids.values().forEach(l -> l.sort(Comparator.comparing(RaceDefinition::id)));
        return new RaceRegistry(Collections.unmodifiableMap(out), kids, problems);
    }

    private static Map<RaceDetails.Key, String> resolveRaw(String id, Map<String, RaceDetails> byId,
            Map<String, Map<RaceDetails.Key, String>> done, LinkedHashSet<String> visiting, List<String> problems) {
        Map<RaceDetails.Key, String> cached = done.get(id);
        if (cached != null) return cached;
        RaceDetails details = byId.get(id);
        Map<RaceDetails.Key, String> own = new EnumMap<>(RaceDetails.Key.class);
        own.putAll(details.values());
        String parent = own.get(RaceDetails.Key.SUBRACE);
        if (parent != null) {
            parent = RaceDetailsParser.normalizeId(parent);
            own.put(RaceDetails.Key.SUBRACE, parent);
            if (!byId.containsKey(parent)) {
                problems.add(id + ": subrace parent '" + parent + "' does not exist; treating as a root race");
                own.remove(RaceDetails.Key.SUBRACE);
            } else if (!visiting.add(id) || visiting.contains(parent)) {
                problems.add(id + ": subrace cycle " + visiting + " -> " + parent + "; treating as a root race");
                own.remove(RaceDetails.Key.SUBRACE);
            } else {
                Map<RaceDetails.Key, String> parentValues = resolveRaw(parent, byId, done, visiting, problems);
                for (Map.Entry<RaceDetails.Key, String> e : parentValues.entrySet()) {
                    if (e.getKey() == RaceDetails.Key.SUBRACE || e.getKey() == RaceDetails.Key.FALLBACK) continue;
                    own.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }
        visiting.remove(id);
        done.put(id, own);
        return own;
    }

    private static RaceDefinition toDefinition(String id, Map<RaceDetails.Key, String> v, List<String> problems) {
        return new RaceDefinition(
                id,
                v.get(RaceDetails.Key.SUBRACE),
                v.getOrDefault(RaceDetails.Key.RACE_NAME, prettify(id)),
                RaceDefinition.Gender.parse(v.get(RaceDetails.Key.GENDER)),
                lower(v.getOrDefault(RaceDetails.Key.TAIL_TYPE, "none")),
                IdMatcher.parse(v.get(RaceDetails.Key.HOME_DIMENSION)),
                IdMatcher.parse(v.get(RaceDetails.Key.MOB_CONSTRAINT)),
                IdMatcher.parse(v.get(RaceDetails.Key.HOME_BIOME)),
                clamp(num(id, v, RaceDetails.Key.POPULATION_PERCENT, 10, problems), 0, 100),
                RaceDefinition.Disposition.parse(v.get(RaceDetails.Key.DEFAULT_DISPOSITION), RaceDefinition.Disposition.NEUTRAL),
                emptyToNull(v.get(RaceDetails.Key.FAVORITE_ITEM)),
                clamp(num(id, v, RaceDetails.Key.FAVORITE_ITEM_POTENCY, 0, problems), 0, 100),
                (float) clamp(num(id, v, RaceDetails.Key.RACE_SIZE, 1, problems), 0.05, 16),
                list(v.get(RaceDetails.Key.WEAPONS)),
                bool(v.get(RaceDetails.Key.FALLBACK), false),
                bool(v.get(RaceDetails.Key.REPLACE_BODY), false),
                list(v.getOrDefault(RaceDetails.Key.BODY_TYPE, "any")),
                bool(v.get(RaceDetails.Key.SLIM_ARMS), false),
                (float) clamp(num(id, v, RaceDetails.Key.HEAD_SCALE, 1, problems), 0.05, 8),
                emptyToNull(lower(v.get(RaceDetails.Key.FACTION))),
                Set.copyOf(list(v.get(RaceDetails.Key.ALLIES))),
                Set.copyOf(list(v.get(RaceDetails.Key.ENEMIES))),
                clamp(num(id, v, RaceDetails.Key.HEALTH_MULTIPLIER, 1, problems), 0.01, 100),
                clamp(num(id, v, RaceDetails.Key.SPEED_MULTIPLIER, 1, problems), 0, 10),
                clamp(num(id, v, RaceDetails.Key.DAMAGE_MULTIPLIER, 1, problems), 0, 100),
                Set.copyOf(list(v.get(RaceDetails.Key.TRAITS))),
                namesList(v.get(RaceDetails.Key.NAMES)),
                bool(v.get(RaceDetails.Key.PLAYER_SELECTABLE), true),
                (int) clamp(num(id, v, RaceDetails.Key.WEAPON_TIER, 1, problems), 0, 5),
                lower(v.getOrDefault(RaceDetails.Key.WEAPON_THEME, "human")));
    }

    // ------------------------------------------------------------------ selection

    /**
     * Picks a race for a freshly spawned biped. Root races roll against their PopulationPercent (normalised if the
     * total exceeds 100); if nothing rolls, a matching {@code Fallback} race is used. The winner then gets a chance to
     * be replaced by each of its matching subraces, recursively.
     *
     * @return the chosen race, or {@code null} if no race applies.
     */
    public RaceDefinition select(SpawnContext ctx, RandomGenerator rng) {
        List<RaceDefinition> candidates = new ArrayList<>();
        for (RaceDefinition r : roots) if (!r.fallback() && r.accepts(ctx)) candidates.add(r);
        RaceDefinition chosen = roll(candidates, rng);
        if (chosen == null) chosen = bestFallback(ctx);
        if (chosen == null) return null;
        return descend(chosen, ctx, rng, 0);
    }

    private RaceDefinition descend(RaceDefinition race, SpawnContext ctx, RandomGenerator rng, int depth) {
        if (depth > 32) return race;
        List<RaceDefinition> kids = children.getOrDefault(race.id(), List.of());
        if (kids.isEmpty()) return race;
        List<RaceDefinition> valid = new ArrayList<>();
        for (RaceDefinition k : kids) if (k.accepts(ctx)) valid.add(k);
        RaceDefinition sub = roll(valid, rng);
        return sub == null ? race : descend(sub, ctx, rng, depth + 1);
    }

    private static RaceDefinition roll(List<RaceDefinition> candidates, RandomGenerator rng) {
        if (candidates.isEmpty()) return null;
        double total = 0;
        for (RaceDefinition r : candidates) total += r.populationPercent();
        if (total <= 0) return null;
        double scale = total > 100 ? 100 / total : 1;
        double roll = rng.nextDouble() * 100;
        double cum = 0;
        for (RaceDefinition r : candidates) {
            cum += r.populationPercent() * scale;
            if (roll < cum) return r;
        }
        return null;
    }

    private RaceDefinition bestFallback(SpawnContext ctx) {
        RaceDefinition best = null;
        int bestScore = -1;
        for (RaceDefinition r : roots) {
            if (!r.fallback() || !r.accepts(ctx)) continue;
            // Prefer the most specific constraint, e.g. "minecraft:zombie" beats "any".
            int score = (r.mobConstraint().isAny() ? 0 : 4) + (r.homeDimension().isAny() ? 0 : 2) + (r.homeBiome().isAny() ? 0 : 1);
            if (score > bestScore) {
                best = r;
                bestScore = score;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ accessors

    public RaceDefinition get(String id) {
        return id == null ? null : races.get(id);
    }

    public Collection<RaceDefinition> all() {
        return races.values();
    }

    public List<RaceDefinition> subracesOf(String id) {
        return children.getOrDefault(id, List.of());
    }

    public List<String> problems() {
        return problems;
    }

    public int size() {
        return races.size();
    }

    // ------------------------------------------------------------------ helpers

    private static double num(String id, Map<RaceDetails.Key, String> v, RaceDetails.Key key, double def, List<String> problems) {
        String s = v.get(key);
        if (s == null) return def;
        try {
            return Double.parseDouble(s.replace("%", "").strip());
        } catch (NumberFormatException e) {
            problems.add(id + ": " + key.fileKey + " '" + s + "' is not a number, using " + def);
            return def;
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static boolean bool(String s, boolean def) {
        if (s == null) return def;
        return switch (s.strip().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y", "1", "on" -> true;
            case "false", "no", "n", "0", "off" -> false;
            default -> def;
        };
    }

    private static List<String> list(String s) {
        if (s == null || s.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String p : s.split("[,;]")) {
            String t = p.strip().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) out.add(t);
        }
        return List.copyOf(out);
    }

    private static List<String> namesList(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split("[,;]")).map(String::strip).filter(x -> !x.isEmpty()).toList();
    }

    private static String lower(String s) {
        return s == null ? null : s.strip().toLowerCase(Locale.ROOT);
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() || s.equalsIgnoreCase("none") || s.equalsIgnoreCase("null") ? null : s.strip();
    }

    static String prettify(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.split("_")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
