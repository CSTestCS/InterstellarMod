package com.cstestcs.interstellar.core.race;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Decides when a generated default file (race files, body models) may be replaced by a newer default. A manifest
 * remembers the hash of what was last written; a file whose content still matches it was never edited and is
 * refreshed. Files written before the manifest existed are compared by meaning instead (details files only).
 */
public final class DefaultFileUpdater {
    private DefaultFileUpdater() {}

    public static String hash(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @param recordedHash hash written to the manifest when the file was generated, or null for files from before
     *                     the manifest
     * @return whether the file on disk can be overwritten with the new default
     */
    public static boolean replaceable(String name, byte[] current, byte[] newDefault, String recordedHash) {
        String cur = hash(current);
        if (cur.equals(hash(newDefault))) return false; // already up to date
        if (recordedHash != null) return cur.equals(recordedHash);
        return name.endsWith("_details.txt") && sameSettings(new String(current, StandardCharsets.UTF_8), new String(newDefault, StandardCharsets.UTF_8));
    }

    /**
     * Legacy check for details files: the same values apart from settings an update intentionally changed (traits)
     * or added (keys missing from the old file).
     */
    static boolean sameSettings(String oldText, String newText) {
        Map<RaceDetails.Key, String> a = new EnumMap<>(RaceDetailsParser.parse("x", oldText).values());
        Map<RaceDetails.Key, String> b = new EnumMap<>(RaceDetailsParser.parse("x", newText).values());
        a.remove(RaceDetails.Key.TRAITS);
        b.remove(RaceDetails.Key.TRAITS);
        b.keySet().retainAll(oldKeys(oldText));
        return a.equals(b);
    }

    private static java.util.Set<RaceDetails.Key> oldKeys(String text) {
        java.util.Set<RaceDetails.Key> keys = java.util.EnumSet.noneOf(RaceDetails.Key.class);
        for (String line : text.replace("\r", "").split("\n")) {
            int colon = line.indexOf(':');
            if (line.startsWith("#") || colon <= 0) continue;
            RaceDetails.Key k = RaceDetails.Key.byName(line.substring(0, colon).strip());
            if (k != null) keys.add(k);
        }
        return keys;
    }
}
