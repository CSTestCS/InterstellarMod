package com.cstestcs.interstellar.core.race;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Matches registry ids against a comma separated list of patterns:
 * <ul>
 *     <li>{@code minecraft:overworld} exact id (namespace defaults to minecraft)</li>
 *     <li>{@code #minecraft:is_forest} tag membership</li>
 *     <li>{@code mymod:*} any id in a namespace, {@code *} or {@code any} everything</li>
 *     <li>{@code !pattern} excludes; exclusions always win</li>
 * </ul>
 * An empty matcher matches everything.
 */
public final class IdMatcher {
    public static final IdMatcher ANY = new IdMatcher(List.of(), List.of(), "");

    private final List<String> includes;
    private final List<String> excludes;
    private final String source;

    private IdMatcher(List<String> includes, List<String> excludes, String source) {
        this.includes = includes;
        this.excludes = excludes;
        this.source = source;
    }

    public static IdMatcher parse(String raw) {
        if (raw == null || raw.isBlank()) return ANY;
        List<String> inc = new ArrayList<>();
        List<String> exc = new ArrayList<>();
        for (String part : raw.split("[,;]")) {
            String p = part.strip().toLowerCase(Locale.ROOT);
            if (p.isEmpty()) continue;
            boolean neg = p.startsWith("!");
            if (neg) p = p.substring(1).strip();
            if (p.equals("any") || p.equals("all")) p = "*";
            p = qualify(p);
            (neg ? exc : inc).add(p);
        }
        return new IdMatcher(List.copyOf(inc), List.copyOf(exc), raw.strip());
    }

    static String qualify(String p) {
        if (p.equals("*")) return p;
        boolean tag = p.startsWith("#");
        String body = tag ? p.substring(1) : p;
        if (!body.contains(":")) body = "minecraft:" + body;
        return tag ? "#" + body : body;
    }

    public boolean isAny() {
        return includes.isEmpty() && excludes.isEmpty() || includes.contains("*") && excludes.isEmpty();
    }

    public boolean matches(String id, Set<String> tags) {
        if (id == null) return includes.isEmpty();
        id = qualify(id.toLowerCase(Locale.ROOT));
        for (String e : excludes) if (single(e, id, tags)) return false;
        if (includes.isEmpty()) return true;
        for (String i : includes) if (single(i, id, tags)) return true;
        return false;
    }

    private static boolean single(String pattern, String id, Set<String> tags) {
        if (pattern.equals("*")) return true;
        if (pattern.startsWith("#")) return tags != null && tags.contains(pattern.substring(1));
        if (pattern.endsWith(":*")) return id.startsWith(pattern.substring(0, pattern.length() - 1));
        return pattern.equals(id);
    }

    public List<String> includes() {
        return includes;
    }

    @Override
    public String toString() {
        return source.isEmpty() ? "any" : source;
    }
}
