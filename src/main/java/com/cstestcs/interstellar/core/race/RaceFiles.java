package com.cstestcs.interstellar.core.race;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Groups the files of the races folder by race id.
 * <pre>
 *   RACENAME_details.txt       race definition
 *   RACENAME_head.java         Blockbench "Modded Entity" export replacing the head
 *   RACENAME_skin.png          64x64 biped skin (variants: RACENAME_skin_01.png, RACENAME_skin_2.png ...)
 *   RACENAME_headskin.png      texture of the head model (variants likewise)
 * </pre>
 * Body and head variants are picked independently.
 */
public final class RaceFiles {
    private static final Pattern TEXTURE = Pattern.compile("^(.+)_(skin|headskin)(?:_(\\d+))?\\.png$");
    private static final Pattern HEAD = Pattern.compile("^(.+)_head\\.java$");
    private static final Pattern DETAILS = Pattern.compile("^(.+)_details\\.txt$");

    /** Files belonging to one race. Texture lists are ordered: base file first, then variants by number. */
    public static final class Entry {
        public final String raceId;
        public String detailsFile;
        public String headModelFile;
        public final List<String> skins = new ArrayList<>();
        public final List<String> headSkins = new ArrayList<>();

        Entry(String raceId) {
            this.raceId = raceId;
        }

        public boolean hasSkin() {
            return !skins.isEmpty();
        }

        public boolean hasHeadModel() {
            return headModelFile != null;
        }
    }

    private RaceFiles() {}

    public static Map<String, Entry> group(Collection<String> fileNames) {
        Map<String, Entry> out = new TreeMap<>();
        Map<String, List<String[]>> textures = new TreeMap<>();
        for (String name : fileNames) {
            String lower = name.toLowerCase(Locale.ROOT);
            Matcher m;
            if ((m = DETAILS.matcher(lower)).matches()) {
                entry(out, m.group(1)).detailsFile = name;
            } else if ((m = HEAD.matcher(lower)).matches()) {
                entry(out, m.group(1)).headModelFile = name;
            } else if ((m = TEXTURE.matcher(lower)).matches()) {
                textures.computeIfAbsent(m.group(1), k -> new ArrayList<>())
                        .add(new String[] { m.group(2), m.group(3) == null ? "-1" : m.group(3), name });
            }
        }
        for (Map.Entry<String, List<String[]>> e : textures.entrySet()) {
            Entry entry = entry(out, e.getKey());
            e.getValue().sort(Comparator.comparingInt(a -> Integer.parseInt(a[1])));
            for (String[] t : e.getValue()) (t[0].equals("skin") ? entry.skins : entry.headSkins).add(t[2]);
        }
        return out;
    }

    private static Entry entry(Map<String, Entry> map, String id) {
        return map.computeIfAbsent(RaceDetailsParser.normalizeId(id), Entry::new);
    }

    /**
     * Deterministically picks a variant index from a seed (normally the entity UUID). {@code salt} separates the head
     * pick from the body pick so they are independent.
     */
    public static int pickVariant(int count, long seed, long salt) {
        if (count <= 1) return 0;
        long h = mix(seed ^ (salt * 0x9E3779B97F4A7C15L));
        return (int) Long.remainderUnsigned(h, count);
    }

    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
