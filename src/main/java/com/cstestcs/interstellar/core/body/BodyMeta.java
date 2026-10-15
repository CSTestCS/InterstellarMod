package com.cstestcs.interstellar.core.body;

import com.cstestcs.interstellar.core.race.RaceDefinition;
import java.util.Locale;

/**
 * Metadata of a custom body, read from an optional {@code NAME.txt} next to {@code NAME.fbx}:
 * <pre>
 *   DisplayName: Buff
 *   Gender: male | female | any | null
 *   Weight: 10            (relative chance when the server assigns bodies)
 * </pre>
 */
public record BodyMeta(String name, String displayName, BodyGender gender, int weight) {
    public enum BodyGender {
        MALE, FEMALE, ANY, NULL;

        public static BodyGender parse(String s) {
            if (s == null) return ANY;
            return switch (s.strip().toLowerCase(Locale.ROOT)) {
                case "male", "m" -> MALE;
                case "female", "f" -> FEMALE;
                case "null", "none", "neutral" -> NULL;
                default -> ANY;
            };
        }

        /**
         * Gender matching between a body and a race: genderless (null) races may use any body, gendered races use
         * bodies of their gender or "any", and "null" bodies are reserved for genderless races.
         */
        public boolean suits(RaceDefinition.Gender race) {
            return switch (this) {
                case ANY -> true;
                case NULL -> race == RaceDefinition.Gender.NULL;
                case MALE -> race != RaceDefinition.Gender.FEMALE;
                case FEMALE -> race != RaceDefinition.Gender.MALE;
            };
        }
    }

    public static BodyMeta parse(String name, String text) {
        String display = name, gender = null;
        int weight = 10;
        if (text != null) {
            for (String line : text.replace("\r", "").split("\n")) {
                int c = line.indexOf(':');
                if (c <= 0 || line.strip().startsWith("#")) continue;
                String k = line.substring(0, c).strip().toLowerCase(Locale.ROOT).replace(" ", "");
                String v = line.substring(c + 1).strip();
                switch (k) {
                    case "displayname", "name" -> display = v;
                    case "gender" -> gender = v;
                    case "weight" -> {
                        try {
                            weight = Math.max(0, Integer.parseInt(v));
                        } catch (NumberFormatException ignored) {}
                    }
                    default -> {}
                }
            }
        }
        return new BodyMeta(name, display, BodyGender.parse(gender), weight);
    }

    public String toText() {
        return "# Interstellar custom body settings\nDisplayName: " + displayName + "\n# male, female, any or null\nGender: "
                + gender.name().toLowerCase(Locale.ROOT) + "\n# Relative chance of being assigned to mobs\nWeight: " + weight + "\n";
    }
}
