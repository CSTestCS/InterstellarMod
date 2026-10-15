package com.cstestcs.interstellar.core.race;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses and writes the simple {@code Key: value} format of {@code RACENAME_details.txt}. */
public final class RaceDetailsParser {
    private RaceDetailsParser() {}

    public static RaceDetails parse(String raceId, String text) {
        Map<RaceDetails.Key, String> values = new EnumMap<>(RaceDetails.Key.class);
        List<String> warnings = new ArrayList<>();
        String[] lines = text.replace("\r", "").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
            int colon = line.indexOf(':');
            if (colon <= 0) {
                warnings.add("line " + (i + 1) + ": expected 'Key: value'");
                continue;
            }
            String rawKey = line.substring(0, colon).strip().replace("\"", "");
            String value = stripComment(line.substring(colon + 1)).strip();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1).strip();
            RaceDetails.Key key = RaceDetails.Key.byName(rawKey);
            if (key == null) {
                warnings.add("line " + (i + 1) + ": unknown key '" + rawKey + "'");
                continue;
            }
            if (value.isEmpty() || value.equalsIgnoreCase("default") || value.equalsIgnoreCase("inherit")) continue;
            values.put(key, value);
        }
        return new RaceDetails(normalizeId(raceId), values, warnings);
    }

    private static String stripComment(String value) {
        // ' #' starts a trailing comment, but '#minecraft:tag' must survive.
        int idx = value.indexOf(" # ");
        return idx >= 0 ? value.substring(0, idx) : value;
    }

    public static String normalizeId(String raw) {
        return raw.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /** Writes a fully commented details file. Values missing from {@code values} are written as empty settings. */
    public static String write(String header, Map<RaceDetails.Key, String> values) {
        StringBuilder sb = new StringBuilder();
        for (String h : header.split("\n")) sb.append("# ").append(h).append('\n');
        sb.append("# Empty settings inherit from the Subrace parent, or use the default.\n\n");
        for (RaceDetails.Key key : RaceDetails.Key.values()) {
            sb.append("# ").append(key.help).append('\n');
            sb.append(key.fileKey).append(": ").append(values.getOrDefault(key, "")).append("\n\n");
        }
        return sb.toString();
    }
}
