package com.cstestcs.interstellar.core.race;

import com.cstestcs.interstellar.core.image.Image;
import com.cstestcs.interstellar.core.image.SkinLayout;
import com.cstestcs.interstellar.core.model.JavaModelWriter;
import com.cstestcs.interstellar.core.model.ModelDef;
import com.cstestcs.interstellar.core.space.ForeignWorlds;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.BiConsumer;

import static com.cstestcs.interstellar.core.race.HeadKit.Pattern.*;
import static com.cstestcs.interstellar.core.race.RaceDetails.Key.*;
import static com.cstestcs.interstellar.core.image.SkinLayout.Face.*;

/**
 * Hand-designed alien species, written as ordinary race files next to the generated ones:
 * <ul>
 *     <li>the Covenant of Halo (Sangheili Elites, Jiralhanae Brutes, Unggoy Grunts, Kig-Yar Jackals), found among the
 *     aliens of every world and crewing some alien ships, armed with their own weapons;</li>
 *     <li>bipedal aliens descended from Minecraft's animals and mobs, each living on the worlds that suit it.</li>
 * </ul>
 * Each has a custom head model and texture, a painted body skin and, for the Halo species, ranks as subraces.
 */
public final class SpeciesRaces {
    private SpeciesRaces() {}

    /** Ids of the Covenant species (used to crew Covenant ships). */
    public static final List<String> COVENANT = List.of("sangheili", "jiralhanae", "unggoy", "kig_yar");

    /**
     * Alien factions besides the Covenant: each groups several species (members never fight each other) and has its
     * own enemies. Factions are ordinary race settings (Faction, Enemies), so race files can add or change them.
     */
    static final Map<String, List<String>> FACTIONS = Map.of(
            "pastoral_union", List.of("bovar", "porcine", "ovine", "gallid", "equine", "camelid", "caprid", "sniffar", "armadan"),
            "wildkin", List.of("lupine", "feline", "vulpine", "leporid", "ursan"),
            "tide_court", List.of("axolite", "testudo", "cephalid", "delphin", "guardite", "ranid"),
            "hive", List.of("apid", "psittan", "chiropter"),
            "nether_conclave", List.of("striden", "hoglan", "blazar", "ghastling"),
            "blight", List.of("creepid", "arachnid", "gelan", "ravagon"),
            "shulker_syndicate", List.of("shulkari"));
    private static final Map<String, String> FACTION_ENEMIES = Map.of(
            "pastoral_union", "covenant, blight, nether_conclave",
            "wildkin", "covenant, blight",
            "tide_court", "covenant, blight, nether_conclave",
            "hive", "covenant, blight",
            "nether_conclave", "humans, villagers, illagers, covenant, tide_court, pastoral_union",
            "blight", "humans, villagers, illagers, covenant, pastoral_union, wildkin, tide_court, hive",
            "shulker_syndicate", "humans, villagers, illagers, covenant");

    static String factionOf(String race) {
        for (Map.Entry<String, List<String>> e : FACTIONS.entrySet()) if (e.getValue().contains(race)) return e.getKey();
        return race;
    }

    // ---------------------------------------------------------------- body skins

    /** How a species' body is painted. */
    record Body(int skin, int belly, int tips, HeadKit.Pattern pattern, int accent, int armor, int trim, boolean tank) {
        static Body fur(int skin, int belly, int tips, HeadKit.Pattern pattern, int accent) {
            return new Body(skin, belly, tips, pattern, accent, 0, 0, false);
        }

        Body armored(int armor, int trim) {
            return new Body(skin, belly, tips, pattern, accent, armor, trim, tank);
        }

        Body withTank() {
            return new Body(skin, belly, tips, pattern, accent, armor, trim, true);
        }
    }

    static Image paintBody(Body b, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        Image img = new Image(64, 64);
        HeadKit.Mat skin = new HeadKit.Mat(b.skin, 0.12f, b.pattern, b.accent);
        for (SkinLayout.Part p : new SkinLayout.Part[] { SkinLayout.Part.HEAD, SkinLayout.Part.BODY, SkinLayout.Part.RIGHT_ARM, SkinLayout.Part.LEFT_ARM,
                SkinLayout.Part.RIGHT_LEG, SkinLayout.Part.LEFT_LEG }) {
            for (SkinLayout.Face f : SkinLayout.Face.values()) {
                int[] rect = SkinLayout.faceRect(p, f);
                for (int y = rect[1]; y < rect[3]; y++) {
                    for (int x = rect[0]; x < rect[2]; x++) img.set(x, y, texel(skin, x, y, r));
                }
            }
        }
        // Lighter belly and chest, darker hands and feet (hooves, paws, claws).
        int[] chest = SkinLayout.faceRect(SkinLayout.Part.BODY, FRONT);
        for (int y = chest[1] + 2; y < chest[3]; y++) for (int x = chest[0] + 1; x < chest[2] - 1; x++) img.set(x, y, noisy(b.belly, r));
        for (SkinLayout.Part limb : new SkinLayout.Part[] { SkinLayout.Part.RIGHT_ARM, SkinLayout.Part.LEFT_ARM, SkinLayout.Part.RIGHT_LEG, SkinLayout.Part.LEFT_LEG }) {
            for (SkinLayout.Face f : new SkinLayout.Face[] { FRONT, BACK, LEFT, RIGHT, BOTTOM }) {
                int[] rect = SkinLayout.faceRect(limb, f);
                int from = f == BOTTOM ? rect[1] : rect[3] - 2;
                for (int y = from; y < rect[3]; y++) for (int x = rect[0]; x < rect[2]; x++) img.set(x, y, noisy(b.tips, r));
            }
        }
        if (b.armor != 0) {
            // Armour: chest and back plates, shoulder pads, belt, greaves (overlay layers).
            for (SkinLayout.Face f : SkinLayout.Face.values()) {
                int[] rect = SkinLayout.faceRect(SkinLayout.Part.JACKET, f);
                for (int y = rect[1]; y < rect[3]; y++) {
                    for (int x = rect[0]; x < rect[2]; x++) {
                        int ly = y - rect[1];
                        boolean plate = f == TOP || f == BOTTOM || ly < 7 || ly >= 9 && ly < 10;
                        if (plate) img.set(x, y, noisy(ly == 6 || ly == 9 || x == rect[0] || x == rect[2] - 1 ? b.trim : b.armor, r));
                    }
                }
            }
            for (SkinLayout.Part sleeve : new SkinLayout.Part[] { SkinLayout.Part.RIGHT_SLEEVE, SkinLayout.Part.LEFT_SLEEVE }) {
                for (SkinLayout.Face f : SkinLayout.Face.values()) {
                    int[] rect = SkinLayout.faceRect(sleeve, f);
                    int bottom = f == TOP || f == BOTTOM ? rect[3] : rect[1] + 4;
                    for (int y = rect[1]; y < bottom; y++) for (int x = rect[0]; x < rect[2]; x++) img.set(x, y, noisy(y == bottom - 1 ? b.trim : b.armor, r));
                    if (f != TOP && f != BOTTOM) {
                        for (int y = rect[1] + 7; y < rect[1] + 10; y++) for (int x = rect[0]; x < rect[2]; x++) img.set(x, y, noisy(b.armor, r)); // gauntlets
                    }
                }
            }
            for (SkinLayout.Part pants : new SkinLayout.Part[] { SkinLayout.Part.RIGHT_PANTS, SkinLayout.Part.LEFT_PANTS }) {
                for (SkinLayout.Face f : new SkinLayout.Face[] { FRONT, BACK, LEFT, RIGHT }) {
                    int[] rect = SkinLayout.faceRect(pants, f);
                    for (int y = rect[1]; y < rect[1] + 3; y++) for (int x = rect[0]; x < rect[2]; x++) img.set(x, y, noisy(b.armor, r)); // thigh plates
                    for (int y = rect[1] + 5; y < rect[3] - 1; y++) for (int x = rect[0]; x < rect[2]; x++) img.set(x, y, noisy(y == rect[1] + 5 ? b.trim : b.armor, r));
                }
            }
        } else {
            // A harness: straps across the chest and a belt in the accent colour.
            int[] front = SkinLayout.faceRect(SkinLayout.Part.BODY, FRONT);
            for (int i = 0; i < 8; i++) {
                img.set(front[0] + i, front[1] + i, noisy(Image.shade(b.accent, 0.6f), r));
            }
            img.fill(front[0], front[3] - 3, front[2] - front[0], 1, Image.shade(b.accent, 0.55f));
        }
        if (b.tank) {
            // Methane tank on the back (Unggoy).
            int[] back = SkinLayout.faceRect(SkinLayout.Part.JACKET, BACK);
            for (int y = back[1]; y < back[1] + 10; y++) for (int x = back[0] + 1; x < back[2] - 1; x++) img.set(x, y, noisy(b.trim, r));
            img.fill(back[0] + 3, back[1] + 2, 2, 5, HeadKit.rgb(0x7FFFE0));
        }
        // Tail colour source (top-left 8x8).
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) img.set(x, y, noisy(b.skin, r));
        return img;
    }

    private static int texel(HeadKit.Mat m, int x, int y, SplittableRandom r) {
        int h = (x * 73856093) ^ (y * 19349663);
        int c = switch (m.pattern()) {
            case SPOTS -> Math.floorMod(h, 9) < 2 ? m.accent() : m.color();
            case STRIPES -> y % 3 == 0 ? m.accent() : m.color();
            case SCALES -> (x + y % 2) % 2 == 0 ? m.color() : Image.shade(m.color(), 0.86f);
            case FUR -> r.nextInt(4) == 0 ? m.accent() : m.color();
            case PIXELS -> Math.floorMod(h, 4) == 0 ? Image.shade(m.color(), 0.75f) : Math.floorMod(h, 4) == 1 ? m.accent() : m.color();
            case PLATES -> y % 4 == 0 ? Image.shade(m.color(), 0.7f) : m.color();
            default -> m.color();
        };
        return noisy(c, r);
    }

    private static int noisy(int c, SplittableRandom r) {
        return Image.shade(c, 0.92f + r.nextFloat() * 0.16f);
    }

    // ---------------------------------------------------------------- species definitions

    /** One species: its details, body, head and the worlds it lives on. */
    record Spec(String id, String name, String disposition, String faction, String enemies, List<ForeignWorlds.Theme> homes, int population,
            double size, double health, double speed, double damage, String tail, boolean slim, String traits, String weapons, String weaponTheme,
            int tier, String favorite, String names, Body body, BiConsumer<HeadKit, Integer> head, double headScale, String description) {}

    /** A subrace: different looks (body, head colour) and a few stats. */
    record Sub(String id, String parent, String name, int population, double size, double health, double damage, int tier, String weapons, Body body,
            Integer headColor) {}

    private static int c(int hex) {
        return HeadKit.rgb(hex);
    }

    private static Map<Character, Integer> pal(Object... kv) {
        Map<Character, Integer> m = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((Character) kv[i], c((Integer) kv[i + 1]));
        return m;
    }

    static void write(Map<String, byte[]> out) {
        for (Spec s : specs()) writeSpec(out, s);
        for (Sub s : subs()) writeSub(out, s);
    }

    private static void writeSpec(Map<String, byte[]> out, Spec s) {
        Map<RaceDetails.Key, String> v = new EnumMap<>(RaceDetails.Key.class);
        v.put(RACE_NAME, s.name);
        v.put(GENDER, "null");
        v.put(TAIL_TYPE, s.tail);
        v.put(HOME_DIMENSION, s.homes.isEmpty() ? COVENANT_HOME : homes(s.homes));
        v.put(MOB_CONSTRAINT, "interstellar:alien");
        v.put(POPULATION_PERCENT, Integer.toString(s.population));
        v.put(DEFAULT_DISPOSITION, s.disposition);
        if (s.favorite != null) {
            v.put(FAVORITE_ITEM, s.favorite);
            v.put(FAVORITE_ITEM_POTENCY, "10");
        }
        v.put(RACE_SIZE, fmt(s.size));
        v.put(WEAPONS, s.weapons);
        v.put(REPLACE_BODY, "true");
        v.put(BODY_TYPE, "any");
        v.put(SLIM_ARMS, Boolean.toString(s.slim));
        double headScale = Math.max(s.headScale, headWidthScale(s));
        if (headScale != 1) v.put(HEAD_SCALE, fmt(headScale));
        v.put(FACTION, s.faction);
        v.put(ENEMIES, s.enemies);
        v.put(HEALTH_MULTIPLIER, fmt(s.health));
        v.put(SPEED_MULTIPLIER, fmt(s.speed));
        v.put(DAMAGE_MULTIPLIER, fmt(s.damage));
        v.put(TRAITS, s.traits);
        v.put(NAMES, s.names);
        v.put(PLAYER_SELECTABLE, "false");
        v.put(WEAPON_TIER, Integer.toString(s.tier));
        v.put(WEAPON_THEME, s.weaponTheme);
        out.put(s.id + "_details.txt", DefaultRaces.text(RaceDetailsParser.write(s.description + " Generated by Interstellar.", v)));
        long seed = s.id.hashCode() * 0x9E3779B97F4A7C15L;
        out.put(s.id + "_skin.png", paintBody(s.body, seed).toPng());
        out.put(s.id + "_skin_01.png", paintBody(s.body, seed + 1).toPng());
        writeHead(out, s.id, s.head, null, seed);
    }

    private static void writeSub(Map<String, byte[]> out, Sub s) {
        Map<RaceDetails.Key, String> v = new EnumMap<>(RaceDetails.Key.class);
        v.put(SUBRACE, s.parent);
        v.put(RACE_NAME, s.name);
        v.put(POPULATION_PERCENT, Integer.toString(s.population));
        if (s.size > 0) v.put(RACE_SIZE, fmt(s.size));
        if (s.health > 0) v.put(HEALTH_MULTIPLIER, fmt(s.health));
        if (s.damage > 0) v.put(DAMAGE_MULTIPLIER, fmt(s.damage));
        if (s.tier >= 0) v.put(WEAPON_TIER, Integer.toString(s.tier));
        if (s.weapons != null) v.put(WEAPONS, s.weapons);
        out.put(s.id + "_details.txt", DefaultRaces.text(RaceDetailsParser.write(s.name + ", a subrace of " + s.parent + ". Generated by Interstellar.", v)));
        long seed = s.id.hashCode() * 0x9E3779B97F4A7C15L;
        if (s.body != null) {
            out.put(s.id + "_skin.png", paintBody(s.body, seed).toPng());
            out.put(s.id + "_skin_01.png", paintBody(s.body, seed + 1).toPng());
        }
        if (s.headColor != null) {
            Spec parent = specs().stream().filter(p -> p.id.equals(s.parent)).findFirst().orElseThrow();
            writeHead(out, s.id, parent.head, s.headColor, seed);
        }
    }

    /** Heads much narrower than the body look lost on it: scale them up to about 7 pixels across (at most 1.5x). */
    private static double headWidthScale(Spec s) {
        HeadKit k = new HeadKit(128, 0);
        s.head.accept(k, null);
        float w = k.width();
        return w >= 6 ? 1 : Math.min(1.5, 7.0 / w);
    }

    private static void writeHead(Map<String, byte[]> out, String id, BiConsumer<HeadKit, Integer> design, Integer variant, long seed) {
        HeadKit k;
        try {
            k = new HeadKit(64, seed);
            design.accept(k, variant);
            k.connect();
        } catch (IllegalStateException full) {
            k = new HeadKit(128, seed);
            design.accept(k, variant);
            k.connect();
        }
        out.put(id + "_headskin.png", k.tex.toPng());
        out.put(id + "_head.java", DefaultRaces.text(JavaModelWriter.write(id + "_head", k.model())));
    }

    /** Dimension ids of every world with one of these themes. */
    private static String homes(List<ForeignWorlds.Theme> themes) {
        List<String> ids = new ArrayList<>();
        for (ForeignWorlds.World w : ForeignWorlds.all()) if (themes.contains(w.theme())) ids.add(w.dimensionId());
        return String.join(", ", ids);
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    static List<Spec> specs() {
        List<Spec> s = new ArrayList<>();
        halo(s);
        mobs(s);
        return s;
    }

    // ================================================================ Halo: the Covenant

    /** The Covenant wars on humanity and on every other alien faction. */
    private static final String COV_ENEMIES = "humans, villagers, illagers, pastoral_union, wildkin, tide_court, hive, nether_conclave, blight, shulker_syndicate";
    /** The Covenant can appear on any world that is not one of vanilla Minecraft's (Interstellar's planets, other mods' dimensions). */
    static final String COVENANT_HOME = "*, !minecraft:*";

    private static void halo(List<Spec> s) {
        s.add(new Spec("sangheili", "Sangheili (Elite)", "hostile", "covenant", COV_ENEMIES, List.of(), 8, 1.25, 2.2, 1.15, 1.4, "none", false,
                "night_vision",
                "weapon:plasma_rifle, weapon:plasma_rifle, weapon:energy_sword, weapon:covenant_carbine, weapon:needle_rifle, weapon:concussion_rifle, weapon:beam_rifle",
                "plasma", 3, null, "Thel 'Vadam, Rtas 'Vadum, Jul 'Mdama, Usze 'Taham, Ripa 'Moramee, Xytan 'Jar, Orna 'Fulsamee, Torka 'Mdama",
                new Body(c(0x4A4444), c(0x5C5552), c(0x3A3434), PLAIN, c(0x4A4444), 0, 0, false).armored(c(0x3B5FB0), c(0x9FB8E8)),
                SpeciesRaces::elite, 1.0, "Sangheili warriors of the Covenant: tall, proud, split-jawed."));
        s.add(new Spec("jiralhanae", "Jiralhanae (Brute)", "hostile", "covenant", COV_ENEMIES, List.of(), 8, 1.4, 2.6, 0.95, 1.6, "none", false,
                "regeneration",
                "weapon:spiker, weapon:spiker, weapon:mauler, weapon:brute_shot, weapon:gravity_hammer, weapon:pulse_carbine, weapon:skewer, weapon:stalker_rifle",
                "nether", 3, null, "Tartarus, Atriox, Escharum, Maccabeus, Gravemind's Bane, Vorgus, Lydus, Barathrum",
                Body.fur(c(0x5E5A55), c(0x6E6A66), c(0x3A3632), FUR, c(0x4A4642)).armored(c(0x6E5A3A), c(0xB08A4A)),
                SpeciesRaces::brute, 1.0, "Jiralhanae: huge, furred and savage, fond of heavy blades and spike weapons."));
        s.add(new Spec("unggoy", "Unggoy (Grunt)", "hostile", "covenant", COV_ENEMIES, List.of(), 14, 0.75, 1.0, 1.05, 0.9, "none", false,
                "",
                "weapon:plasma_pistol, weapon:plasma_pistol, weapon:needler, weapon:needler, weapon:fuel_rod_cannon, weapon:plasma_launcher",
                "plasma", 1, "minecraft:cookie", "Yayap, Dadab, Kwassass, Flipyap, Lalabeb, Gagaw, Bapap, Mumumu",
                Body.fur(c(0x6C5A6E), c(0x857085), c(0x4A3A4C), SCALES, c(0x5C4A5E)).armored(c(0xC0602A), c(0xE09050)).withTank(),
                SpeciesRaces::grunt, 1.0, "Unggoy: small, numerous, breathing methane through a mask."));
        s.add(new Spec("kig_yar", "Kig-Yar (Jackal)", "hostile", "covenant", COV_ENEMIES, List.of(), 10, 1.0, 1.2, 1.25, 1.0, "lizard", true,
                "night_vision",
                "weapon:plasma_pistol, weapon:covenant_carbine, weapon:covenant_carbine, weapon:beam_rifle, weapon:needle_rifle",
                "plasma", 2, "minecraft:gold_ingot", "Chur'R-Yar, Reth, Zhar, Kel'Tah, Yar-Raq, Skirr, Ckik, Tzar'K",
                Body.fur(c(0x8A8A4A), c(0xA2A060), c(0x5A5A30), SCALES, c(0x6E6E38)).armored(c(0x5A4A48), c(0x8A7470)),
                SpeciesRaces::jackal, 1.0, "Kig-Yar: lean, beaked sharpshooters and pirates."));
    }

    static List<Sub> subs() {
        return List.of(
                new Sub("sangheili_major", "sangheili", "Sangheili Major", 30, 1.3, 2.8, 1.6, 4, null,
                        new Body(c(0x4A4444), c(0x5C5552), c(0x3A3434), PLAIN, c(0x4A4444), 0, 0, false).armored(c(0xA8282A), c(0xE8807A)), c(0xA8282A)),
                new Sub("sangheili_ultra", "sangheili", "Sangheili Ultra", 12, 1.32, 3.2, 1.8, 5, null,
                        new Body(c(0x4A4444), c(0x5C5552), c(0x3A3434), PLAIN, c(0x4A4444), 0, 0, false).armored(c(0xE0E0E4), c(0x9AA0B0)), c(0xE0E0E4)),
                new Sub("sangheili_zealot", "sangheili", "Sangheili Zealot", 6, 1.35, 3.6, 2.0, 5, "weapon:energy_sword, weapon:energy_sword, weapon:fuel_rod_cannon",
                        new Body(c(0x4A4444), c(0x5C5552), c(0x3A3434), PLAIN, c(0x4A4444), 0, 0, false).armored(c(0xD8A830), c(0xFFE080)), c(0xD8A830)),
                new Sub("jiralhanae_chieftain", "jiralhanae", "Jiralhanae Chieftain", 10, 1.55, 4.0, 2.0, 5, "weapon:gravity_hammer, weapon:gravity_hammer, weapon:fuel_rod_cannon",
                        Body.fur(c(0x6A665F), c(0x7A766E), c(0x3A3632), FUR, c(0x504C46)).armored(c(0xC8A040), c(0xF0D070)), c(0xC8A040)),
                new Sub("unggoy_major", "unggoy", "Unggoy Major", 25, 0.78, 1.3, 1.0, 2, null,
                        Body.fur(c(0x6C5A6E), c(0x857085), c(0x4A3A4C), SCALES, c(0x5C4A5E)).armored(c(0xB0282A), c(0xE06060)).withTank(), c(0xB0282A)),
                new Sub("unggoy_heavy", "unggoy", "Unggoy Heavy", 12, 0.8, 1.6, 1.1, 3, "weapon:fuel_rod_cannon, weapon:plasma_launcher",
                        Body.fur(c(0x6C5A6E), c(0x857085), c(0x4A3A4C), SCALES, c(0x5C4A5E)).armored(c(0x4A7A2A), c(0x90C060)).withTank(), c(0x4A7A2A)),
                new Sub("kig_yar_sniper", "kig_yar", "Kig-Yar Sniper", 25, 1.0, 1.0, 1.2, 3, "weapon:beam_rifle, weapon:needle_rifle",
                        Body.fur(c(0x7A7A44), c(0x92905A), c(0x4A4A28), SCALES, c(0x606030)).armored(c(0x3A3A4A), c(0x6A6A80)), null),
                new Sub("bovar_mooshroom", "bovar", "Mooshroom Bovar", 25, 1.0, -1, -1, -1, null,
                        Body.fur(c(0xA8251E), c(0xE8E0D8), c(0x3A2A22), SPOTS, c(0xE8E0D8)), 1),
                new Sub("ursan_polar", "ursan", "Polar Ursan", 35, 1.15, 1.8, 1.3, -1, null,
                        Body.fur(c(0xF2F0EA), c(0xFFFFFF), c(0x2A2A2A), FUR, c(0xDCDAD2)), 1),
                new Sub("cephalid_glow", "cephalid", "Glow Cephalid", 30, -1, -1, -1, -1, null,
                        Body.fur(c(0x1E6A6E), c(0x3AC0B0), c(0x0E3A3E), GLOW, c(0x6AF0D8)), 1),
                new Sub("gelan_magma", "gelan", "Magma Gelan", 40, -1, -1, 1.3, -1, null,
                        Body.fur(c(0x5A1E10), c(0xC84A14), c(0x2A0A06), PIXELS, c(0xFFA020)), 1));
    }

    // ---------------------------------------------------------------- Halo heads

    private static void elite(HeadKit k, Integer armor) {
        HeadKit.Mat skin = new HeadKit.Mat(c(0x4E4846)).with(SCALES, c(0x423C3A));
        HeadKit.Mat dark = new HeadKit.Mat(c(0x2E2826));
        HeadKit.Mat tooth = new HeadKit.Mat(c(0xE8E0C8));
        // Long sloping skull reaching forward over the jaw.
        HeadKit.Cube skull = k.box(k.head, -3, -8, -3.5f, 6, 6, 8, skin);
        k.box(k.head, -2.5f, -5, -6.5f, 5, 3, 3, skin); // upper jaw
        k.box(k.head, -2, -2, -2, 4, 2, 4, skin);       // throat
        k.decal(skull, LEFT, 4, 2, pal('o', 0xFF9A1A, 'k', 0x1A1010), "ko");
        k.decal(skull, RIGHT, 2, 2, pal('o', 0xFF9A1A, 'k', 0x1A1010), "ok");
        // Four mandibles, splayed, with teeth on the inside.
        for (int side = -1; side <= 1; side += 2) {
            for (int lower = 0; lower <= 1; lower++) {
                var m = k.part(k.head, "mandible_" + (side < 0 ? "r" : "l") + lower, side * 1.6f, lower == 0 ? -3.5f : -2.2f, -5,
                        lower == 0 ? 8 : 24, side * 18, side * (lower == 0 ? 6 : 14));
                HeadKit.Cube cb = k.box(m, -0.75f, 0, -4, 2, 1, 5, skin);
                k.fill(cb, side < 0 ? LEFT : RIGHT, 0, 0, 5, 1, c(0x2A2020));
                k.box(m, side < 0 ? 0.5f : -0.5f, 0.8f, -3.5f, 1, 1, 1, tooth);
                k.box(m, side < 0 ? 0.5f : -0.5f, 0.8f, -1.5f, 1, 1, 1, tooth);
            }
        }
        k.box(k.head, -1.5f, -3.5f, -5.5f, 3, 1, 1, dark); // maw
        if (armor != null) {
            // Ranked Sangheili wear a crested helmet in their armour colour.
            HeadKit.Mat helm = new HeadKit.Mat(armor, 0.05f, PLATES, armor);
            k.box(k.head, -3.5f, -8.7f, -4.5f, 7, 2, 9, helm);
            k.box(k.head, -0.5f, -10.5f, -3.5f, 1, 2, 7, helm);
            for (int side = -1; side <= 1; side += 2) k.box(k.head, side < 0 ? -4 : 3, -8, -1, 1, 3, 4, helm);
        }
    }

    private static void brute(HeadKit k, Integer armor) {
        HeadKit.Mat fur = new HeadKit.Mat(c(0x5A5650), 0.14f, FUR, c(0x46423C));
        HeadKit.Mat hide = new HeadKit.Mat(c(0x6A625A));
        HeadKit.Mat tusk = new HeadKit.Mat(c(0xE8E0C0));
        HeadKit.Cube skull = k.box(k.head, -4, -8, -4, 8, 8, 8, fur);
        HeadKit.Cube brow = k.box(k.head, -4.5f, -6.5f, -5, 9, 2, 2, hide);
        HeadKit.Cube muzzle = k.box(k.head, -3, -4.5f, -6, 6, 4, 2, hide);
        k.decal(muzzle, FRONT, 1, 0, pal('n', 0x2A2420, 'm', 0x3A1A1A, 't', 0xE8E0C0), "..nn..", "......", "tmmmmt", "t....t");
        k.decal(skull, FRONT, 1, 2, pal('e', 0xFFC21A, 'k', 0x201A10), ".ke..ek.");
        k.box(k.head, -3.5f, -2.5f, -6.5f, 1, 3, 1, tusk);
        k.box(k.head, 2.5f, -2.5f, -6.5f, 1, 3, 1, tusk);
        k.box(k.head, -4.5f, -8.5f, -1, 9, 7, 6, fur.noise(0.2f)); // mane
        k.box(k.head, -3, -9.5f, -3, 6, 2, 6, fur.noise(0.2f));
        if (armor != null) {
            // Chieftain's headdress.
            HeadKit.Mat gold = new HeadKit.Mat(armor, 0.05f, PLATES, armor);
            k.box(k.head, -4.5f, -9.5f, -4.5f, 9, 2, 4, gold);
            k.box(k.head, -1, -12.5f, -4, 2, 3, 2, gold);
            k.box(k.head, -5.5f, -11, -2, 1, 4, 2, gold);
            k.box(k.head, 4.5f, -11, -2, 1, 4, 2, gold);
        }
        k.fill(brow, FRONT, 0, 1, 9, 1, c(0x4A443E));
    }

    private static void grunt(HeadKit k, Integer armor) {
        HeadKit.Mat skin = new HeadKit.Mat(c(0x6C5A6E)).with(SCALES, c(0x5C4A5E));
        HeadKit.Mat mask = new HeadKit.Mat(armor == null ? c(0x6A6E74) : Image.shade(armor, 0.8f), 0.05f, PLATES, c(0x4A4E54));
        HeadKit.Mat tube = new HeadKit.Mat(c(0x3A3E44));
        HeadKit.Cube skull = k.box(k.head, -3.5f, -7, -3, 7, 6, 7, skin);
        k.box(k.head, -3, -8.5f, -1, 6, 2, 5, skin); // domed crown
        k.decal(skull, FRONT, 0, 1, pal('w', 0xF0E8D0, 'k', 0x101010, 'o', 0xE07A20), ".wk...kw", ".ko...ok");
        HeadKit.Cube m = k.box(k.head, -3, -4, -4.5f, 6, 3, 2, mask);
        k.decal(m, FRONT, 1, 1, pal('g', 0x6AFFF0, 'd', 0x2A6A70), "gddg");
        k.box(k.head, -4.5f, -3.5f, -3.5f, 1, 1, 5, tube);
        k.box(k.head, 3.5f, -3.5f, -3.5f, 1, 1, 5, tube);
        k.box(k.head, -1, -2, -5, 2, 1, 1, tube);
        if (armor != null) k.box(k.head, -3.5f, -7.5f, -3.5f, 7, 1, 3, new HeadKit.Mat(armor, 0.05f, PLATES, armor));
    }

    private static void jackal(HeadKit k, Integer armor) {
        HeadKit.Mat skin = new HeadKit.Mat(c(0x8A8A48)).with(SCALES, c(0x74743C));
        HeadKit.Mat beak = new HeadKit.Mat(c(0x5A4A3A));
        HeadKit.Mat quill = new HeadKit.Mat(c(0xC8B850), 0.15f, STRIPES, c(0x8A6A2A));
        HeadKit.Cube skull = k.box(k.head, -2.5f, -7, -2, 5, 5, 6, skin);
        HeadKit.Cube snout = k.box(k.head, -1.5f, -6, -8, 3, 3, 6, beak);
        k.box(k.head, -1.25f, -3, -7.5f, 3, 1, 6, beak.noise(0.12f)); // lower jaw
        k.decal(skull, LEFT, 0, 1, pal('r', 0xE0201A, 'k', 0x200808), "rk");
        k.decal(skull, RIGHT, 4, 1, pal('r', 0xE0201A, 'k', 0x200808), "kr");
        k.fill(snout, FRONT, 0, 2, 3, 1, c(0x2A201A));
        for (int i = 0; i < 5; i++) {
            var q = k.part(k.head, "quill_" + i, -2 + i, -7, -1 + (i % 2), -35 - 8 * (i % 3), 0, (i - 2) * 9);
            k.box(q, -0.5f, -5 - (i % 2) * 2, 0, 1, 5 + (i % 2) * 2, 1, quill);
        }
        k.box(k.head, -2.5f, -4, 1, 5, 2, 3, skin); // back of the jaw
    }

    // ================================================================ mobs as bipedal aliens

    private static final String MOB_ENEMIES = "humans, villagers, illagers";

    private static Spec mob(String id, String name, String disposition, List<ForeignWorlds.Theme> homes, double size, String tail, boolean slim,
            String traits, String favorite, String names, Body body, BiConsumer<HeadKit, Integer> head, double headScale, String description) {
        SplittableRandom r = new SplittableRandom(id.hashCode());
        String theme = homes.isEmpty() ? "plasma" : homes.get(0).weaponTheme;
        String faction = factionOf(id);
        return new Spec(id, name, disposition, faction, FACTION_ENEMIES.getOrDefault(faction, MOB_ENEMIES), homes, 100, size, 1.0 + r.nextDouble() * 0.8,
                0.9 + r.nextDouble() * 0.35, 1.0, tail, slim, traits, AlienRaces.weapons(r), theme, 1 + r.nextInt(3), favorite, names, body, head,
                headScale, description);
    }

    private static void mobs(List<Spec> s) {
        s.add(mob("bovar", "Bovar", "neutral", List.of(ForeignWorlds.Theme.RUST, ForeignWorlds.Theme.FUNGAL), 1.15, "none", false, "", "minecraft:wheat",
                "Moob, Bessa, Oxar, Hoofdan, Brindle, Tauros",
                Body.fur(c(0x4A3226), c(0xE8E0D8), c(0x2A2420), SPOTS, c(0xE8E0D8)), SpeciesRaces::cow, 1.0, "Bovine aliens, broad and patient."));
        s.add(mob("porcine", "Porcine", "neutral", List.of(ForeignWorlds.Theme.FUNGAL, ForeignWorlds.Theme.DUNE), 1.05, "none", false, "", "minecraft:carrot",
                "Oink, Truffa, Snoutrik, Porka, Hamlin, Bacca",
                Body.fur(c(0xF0A4A0), c(0xF6BCB8), c(0x8A5A58), PLAIN, c(0xD88A86)), SpeciesRaces::pig, 1.0, "Pig-folk with flat snouts and big appetites."));
        s.add(mob("ovine", "Ovine", "passive", List.of(ForeignWorlds.Theme.RUST, ForeignWorlds.Theme.BONE), 1.0, "none", false, "", "minecraft:wheat",
                "Baa'rel, Lanolin, Fleecia, Woolm, Ramsey, Merina",
                Body.fur(c(0xEAEAE4), c(0xD9C7A8), c(0xC8B094), FUR, c(0xD8D8D0)), SpeciesRaces::sheep, 1.0, "Woolly, gentle sheep-folk."));
        s.add(mob("gallid", "Gallid", "passive", List.of(ForeignWorlds.Theme.RUST, ForeignWorlds.Theme.HONEY), 0.8, "none", true, "slow_fall, no_fall_damage",
                "minecraft:wheat_seeds", "Cluckra, Peckett, Combsby, Bawk, Plume, Henrietta",
                Body.fur(c(0xF4F4F0), c(0xFFFFFF), c(0xE8B830), FUR, c(0xDCDCD6)), SpeciesRaces::chicken, 1.2, "Feathered, light-boned chicken-folk."));
        s.add(mob("lupine", "Lupine", "neutral", List.of(ForeignWorlds.Theme.BONE, ForeignWorlds.Theme.FROZEN), 1.1, "wolf", false, "night_vision",
                "minecraft:bone", "Fenra, Howlan, Grayfang, Lobo, Vargr, Akela",
                Body.fur(c(0xBCB6AE), c(0xDCD8D0), c(0x5A544E), FUR, c(0x9A948C)), SpeciesRaces::wolf, 1.0, "Wolf-folk who hunt in packs."));
        s.add(mob("feline", "Feline", "neutral", List.of(ForeignWorlds.Theme.DUNE, ForeignWorlds.Theme.HONEY), 0.95, "cat", true, "no_fall_damage",
                "minecraft:cod", "Mira, Tabbith, Purrl, Ozzel, Whiskra, Ocella",
                Body.fur(c(0xE0A050), c(0xF4E8D0), c(0x8A5A2A), STRIPES, c(0xB06A28)), SpeciesRaces::cat, 1.0, "Agile, striped cat-folk."));
        s.add(mob("vulpine", "Vulpine", "neutral", List.of(ForeignWorlds.Theme.FROZEN, ForeignWorlds.Theme.HONEY), 0.95, "fox", true, "night_vision",
                "minecraft:sweet_berries", "Reynar, Kitsa, Vixa, Rusk, Tod, Ember",
                Body.fur(c(0xE07A2A), c(0xF4F0E8), c(0x2A2220), FUR, c(0xC8641E)), SpeciesRaces::fox, 1.0, "Cunning fox-folk."));
        s.add(mob("leporid", "Leporid", "passive", List.of(ForeignWorlds.Theme.FROZEN, ForeignWorlds.Theme.DUNE), 0.85, "rabbit", true, "no_fall_damage",
                "minecraft:carrot", "Hopkin, Thistle, Bun, Lapin, Cottontail, Harew",
                Body.fur(c(0x9A7A5A), c(0xE8DCC8), c(0x6A5038), FUR, c(0x86684A)), SpeciesRaces::rabbit, 1.0, "Quick, long-eared rabbit-folk."));
        s.add(mob("equine", "Equine", "neutral", List.of(ForeignWorlds.Theme.DUNE, ForeignWorlds.Theme.BONE), 1.2, "none", false, "", "minecraft:golden_apple",
                "Maren, Stallix, Galloway, Fillia, Destrin, Equa",
                Body.fur(c(0x7A4A2A), c(0x8A5A36), c(0x2A1E16), PLAIN, c(0x5A3620)), SpeciesRaces::horse, 1.0, "Tall horse-folk, swift runners."));
        s.add(mob("camelid", "Camelid", "neutral", List.of(ForeignWorlds.Theme.DUNE), 1.15, "none", false, "", "minecraft:hay_block",
                "Lhama, Alpacka, Vicun, Guana, Spitz, Cria",
                Body.fur(c(0xE8DCC0), c(0xF4ECDA), c(0x6A5A44), FUR, c(0xC8B898)), SpeciesRaces::llama, 1.0, "Llama-folk caravaners who spit when angry."));
        s.add(mob("ursan", "Ursan", "neutral", List.of(ForeignWorlds.Theme.HONEY, ForeignWorlds.Theme.FROZEN), 1.25, "none", false, "", "minecraft:bamboo",
                "Bambao, Ursula, Kodi, Pandor, Bruin, Mei",
                Body.fur(c(0xF0F0EC), c(0xFFFFFF), c(0x1E1E1E), PLAIN, c(0x1E1E1E)), SpeciesRaces::bear, 1.0, "Big bear-folk, gentle until provoked."));
        s.add(mob("ranid", "Ranid", "neutral", List.of(ForeignWorlds.Theme.TOXIC, ForeignWorlds.Theme.FUNGAL), 0.9, "none", false, "water_breathing, no_fall_damage",
                "minecraft:slime_ball", "Croak, Ribbix, Bogsby, Lilypa, Toadrin, Hopla",
                Body.fur(c(0x6A8A3A), c(0xD8C890), c(0x4A6228), SPOTS, c(0x4A6228)), SpeciesRaces::frog, 1.0, "Frog-folk of the swamps."));
        s.add(mob("axolite", "Axolite", "passive", List.of(ForeignWorlds.Theme.REEF, ForeignWorlds.Theme.CRYSTAL), 0.9, "fish", true, "water_breathing, regeneration",
                "minecraft:tropical_fish", "Lotl, Ambyx, Pinka, Gillow, Mexa, Regen",
                Body.fur(c(0xF0A8C8), c(0xF8C8DC), c(0xD87AA0), PLAIN, c(0xC8608A)), SpeciesRaces::axolotl, 1.0, "Axolotl-folk who heal from any wound."));
        s.add(mob("testudo", "Testudo", "passive", List.of(ForeignWorlds.Theme.REEF), 1.0, "none", false, "water_breathing", "minecraft:seagrass",
                "Shellby, Torto, Caretta, Slowan, Kemp, Leatha",
                Body.fur(c(0x5A9A4A), c(0xD8D8A0), c(0x3A6A30), SCALES, c(0x4A3A22)).armored(c(0x5A4A2A), c(0x3A2E1A)), SpeciesRaces::turtle, 1.0,
                "Turtle-folk carrying their shells as armour."));
        s.add(mob("apid", "Apid", "neutral", List.of(ForeignWorlds.Theme.HONEY), 0.85, "none", true, "slow_fall, no_fall_damage", "minecraft:honey_bottle",
                "Buzzra, Apis, Combin, Nectra, Drone, Melli",
                Body.fur(c(0xE8B830), c(0xF0C850), c(0x2A1E10), STRIPES, c(0x3A2A14)), SpeciesRaces::bee, 1.0, "Bee-folk of the hive worlds."));
        s.add(mob("arachnid", "Arachnid", "hostile", List.of(ForeignWorlds.Theme.ECHO, ForeignWorlds.Theme.TOXIC), 1.1, "none", true, "night_vision",
                "minecraft:spider_eye", "Skitter, Weba, Arach, Fangor, Silka, Tarantix",
                Body.fur(c(0x3A3030), c(0x4A3C3A), c(0x1E1818), FUR, c(0x2A2222)), SpeciesRaces::spider, 1.0, "Eight-eyed spider-folk."));
        s.add(mob("creepid", "Creepid", "hostile", List.of(ForeignWorlds.Theme.TOXIC), 1.0, "none", false, "", "minecraft:gunpowder",
                "Sssam, Hisska, Boomra, Kreep, Fuzzle, Tnt'ka",
                Body.fur(c(0x5AB04A), c(0x6AC85A), c(0x2A5A22), PIXELS, c(0xA8E890)), SpeciesRaces::creeper, 1.0, "Creeper-folk. Keep your distance."));
        s.add(mob("cephalid", "Cephalid", "neutral", List.of(ForeignWorlds.Theme.REEF, ForeignWorlds.Theme.ECHO), 1.0, "none", false, "water_breathing",
                "minecraft:ink_sac", "Inka, Tentar, Kraka, Squill, Octavo, Mollus",
                Body.fur(c(0x3A5A7A), c(0x6A8AA8), c(0x22384E), PLAIN, c(0x2A4A66)), SpeciesRaces::squid, 1.0, "Squid-folk with tentacled faces."));
        s.add(mob("delphin", "Delphin", "passive", List.of(ForeignWorlds.Theme.REEF), 1.05, "fish", false, "water_breathing", "minecraft:cod",
                "Echo, Flippa, Pod, Bottlenose, Clickri, Marin",
                Body.fur(c(0x8A9AA8), c(0xD8E0E8), c(0x5A6A78), PLAIN, c(0x6A7A88)), SpeciesRaces::dolphin, 1.0, "Playful dolphin-folk."));
        s.add(mob("caprid", "Caprid", "neutral", List.of(ForeignWorlds.Theme.FROZEN), 1.0, "none", false, "no_fall_damage", "minecraft:wheat",
                "Billy, Nanna, Ibex, Ramzar, Kidd, Chamo",
                Body.fur(c(0xE8E4DC), c(0xF4F0EA), c(0x6A645C), FUR, c(0xD0CCC4)), SpeciesRaces::goat, 1.0, "Goat-folk of the frozen peaks; they ram."));
        s.add(mob("psittan", "Psittan", "passive", List.of(ForeignWorlds.Theme.CRYSTAL), 0.8, "none", true, "slow_fall, no_fall_damage", "minecraft:wheat_seeds",
                "Polly, Ara, Kea, Lorik, Cocka, Rio",
                Body.fur(c(0xD8201E), c(0xF0C030), c(0x3A3A3A), PLAIN, c(0x2A6AD8)), SpeciesRaces::parrot, 1.1, "Brightly feathered parrot-folk."));
        s.add(mob("striden", "Striden", "neutral", List.of(ForeignWorlds.Theme.MAGMA), 1.2, "none", false, "fire_immune", "minecraft:warped_fungus",
                "Lavan, Wobble, Strida, Magmo, Basalto, Crimsa",
                Body.fur(c(0xA03A3A), c(0xB84A4A), c(0x5A1E1E), SCALES, c(0x7A2A2A)), SpeciesRaces::strider, 1.0, "Strider-folk who wade through lava."));
        s.add(mob("hoglan", "Hoglan", "hostile", List.of(ForeignWorlds.Theme.MAGMA), 1.3, "none", false, "fire_immune", "minecraft:crimson_fungus",
                "Tuskor, Grunth, Boarak, Hoggar, Sowra, Bristle",
                Body.fur(c(0x8A5A4A), c(0xA06A56), c(0x3A241E), FUR, c(0xC89A70)), SpeciesRaces::hoglin, 1.0, "Hoglin-folk, tusked and aggressive."));
        s.add(mob("blazar", "Blazar", "hostile", List.of(ForeignWorlds.Theme.MAGMA, ForeignWorlds.Theme.BONE), 1.0, "none", false, "fire_immune, slow_fall",
                "minecraft:blaze_rod", "Pyrrh, Cinda, Scorch, Flarix, Emberon, Ignis",
                Body.fur(c(0xE8A020), c(0xFFD040), c(0x8A4A10), PIXELS, c(0xFFF080)), SpeciesRaces::blaze, 1.0, "Blaze-folk wreathed in heat."));
        s.add(mob("guardite", "Guardite", "hostile", List.of(ForeignWorlds.Theme.REEF), 1.1, "fish", false, "water_breathing", "minecraft:prismarine_crystals",
                "Elda, Prisma, Spinar, Oculon, Tidewarden, Monu",
                Body.fur(c(0x5A9A8A), c(0xC8A070), c(0x2A5A50), SCALES, c(0x3A7A6A)), SpeciesRaces::guardian, 1.0, "One-eyed guardian-folk of the deep."));
        s.add(mob("gelan", "Gelan", "hostile", List.of(ForeignWorlds.Theme.TOXIC), 1.0, "none", false, "no_fall_damage", "minecraft:slime_ball",
                "Gloop, Squelch, Ooza, Jello, Blobbin, Viscus",
                Body.fur(c(0x6AC85A), c(0x8AE070), c(0x3A8A2A), PLAIN, c(0x4AA03A)), SpeciesRaces::slime, 1.0, "Slime-folk with gel bodies."));
        s.add(mob("shulkari", "Shulkari", "hostile", List.of(ForeignWorlds.Theme.VOID), 1.0, "none", false, "slow_fall, no_fall_damage", "minecraft:chorus_fruit",
                "Purpa, Shellix, Endra, Levi, Kalis, Chorun",
                Body.fur(c(0x9A6A9A), c(0xE8E070), c(0x5A3A5A), PLATES, c(0x7A4A7A)).armored(c(0x8A5A8A), c(0x5A3A5A)), SpeciesRaces::shulker, 1.0,
                "Shulker-folk in purpur shells."));
        s.add(mob("ghastling", "Ghastling", "hostile", List.of(ForeignWorlds.Theme.BONE, ForeignWorlds.Theme.MAGMA), 1.1, "none", true, "fire_immune, slow_fall",
                "minecraft:ghast_tear", "Wail, Sobra, Lamenta, Teary, Hollow, Weep",
                Body.fur(c(0xF0F0F0), c(0xFFFFFF), c(0xC8C8C8), PLAIN, c(0xD0D0D0)), SpeciesRaces::ghast, 1.0, "Pale ghast-folk who weep fire."));
        s.add(mob("sniffar", "Sniffar", "passive", List.of(ForeignWorlds.Theme.FUNGAL), 1.3, "none", false, "", "minecraft:torchflower_seeds",
                "Snuff, Mossa, Rootle, Pitcher, Torcha, Ancien",
                Body.fur(c(0x9A3A2A), c(0x6A8A3A), c(0x3A2A1A), FUR, c(0x5A7A2A)), SpeciesRaces::sniffer, 1.0, "Ancient sniffer-folk, keen of nose."));
        s.add(mob("armadan", "Armadan", "passive", List.of(ForeignWorlds.Theme.DUNE), 0.9, "none", false, "", "minecraft:spider_eye",
                "Scute, Rolla, Bandit, Dillo, Shelby, Pango",
                Body.fur(c(0xC88A7A), c(0xE0A898), c(0x6A4A40), PLATES, c(0x9A6A5A)).armored(c(0xA86A5A), c(0x7A4A3E)), SpeciesRaces::armadillo, 1.0,
                "Armadillo-folk who curl up behind their plates."));
        s.add(mob("chiropter", "Chiropter", "neutral", List.of(ForeignWorlds.Theme.ECHO, ForeignWorlds.Theme.VOID), 0.85, "none", true, "night_vision, slow_fall",
                "minecraft:glow_berries", "Echola, Nyx, Flitter, Vesper, Duskwing, Sonar",
                Body.fur(c(0x4A3A2E), c(0x6A5444), c(0x2A1E16), FUR, c(0x3A2C22)), SpeciesRaces::bat, 1.0, "Bat-folk who see with sound."));
        s.add(mob("ravagon", "Ravagon", "hostile", List.of(ForeignWorlds.Theme.RUST, ForeignWorlds.Theme.ECHO), 1.45, "none", false, "", "minecraft:saddle",
                "Gorehorn, Trampler, Ravok, Crusha, Bellow, Rampa",
                Body.fur(c(0x5A5654), c(0x6A6664), c(0x2A2826), PLATES, c(0x444240)), SpeciesRaces::ravager, 1.0, "Ravager-folk: horned, huge, relentless."));
    }

    // ---------------------------------------------------------------- mob heads

    private static final Map<Character, Integer> EYES = pal('w', 0xFFFFFF, 'k', 0x101010, 'b', 0x2A2A2A, 'p', 0xE07AA0, 'r', 0xE0201A);

    private static void cow(HeadKit k, Integer v) {
        boolean moo = v != null;
        int hide = moo ? c(0xA8251E) : c(0x4A3226);
        HeadKit.Cube skull = k.box(k.head, -4, -8, -3, 8, 7, 6, new HeadKit.Mat(hide, 0.08f, SPOTS, c(0xE8E0D8)));
        HeadKit.Cube muzzle = k.box(k.head, -3, -4, -5, 6, 3, 2, new HeadKit.Mat(c(0xD9B8A0)));
        k.decal(muzzle, FRONT, 1, 1, EYES, "k..k");
        k.decal(skull, FRONT, 0, 2, EYES, "wk....kw");
        for (int side = -1; side <= 1; side += 2) {
            var horn = k.part(k.head, "horn_" + side, side * 4, -7, -1, 0, 0, side * -20);
            k.box(horn, side < 0 ? -2 : 0, -1, -0.5f, 2, 1, 1, new HeadKit.Mat(c(0xD8D0C0)));
            k.box(horn, side < 0 ? -2 : 1, -3, -0.5f, 1, 2, 1, new HeadKit.Mat(c(0xB8B0A0)));
            k.box(k.head, side < 0 ? -5 : 4, -6, -1, 1, 1, 2, new HeadKit.Mat(hide));
        }
        if (moo) {
            // Mushrooms growing from the head.
            k.box(k.head, -2.5f, -10, -1, 3, 2, 3, new HeadKit.Mat(c(0xC8201E)).with(SPOTS, c(0xF0F0F0)));
            k.box(k.head, -1.5f, -8.5f, 0, 1, 1, 1, new HeadKit.Mat(c(0xE8E0D0)));
            k.box(k.head, 1, -9.5f, 0.5f, 2, 1, 2, new HeadKit.Mat(c(0xC8201E)).with(SPOTS, c(0xF0F0F0)));
        }
    }

    private static void pig(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -4, -8, -4, 8, 8, 8, new HeadKit.Mat(c(0xF0A4A0)));
        HeadKit.Cube snout = k.box(k.head, -2, -4, -5, 4, 3, 1, new HeadKit.Mat(c(0xE8888A)));
        k.decal(snout, FRONT, 0, 1, pal('n', 0x8A3A44), "n..n");
        k.decal(skull, FRONT, 0, 3, EYES, "wk....kw");
        k.box(k.head, -4.5f, -8.5f, -2, 2, 2, 1, new HeadKit.Mat(c(0xE8909A)));
        k.box(k.head, 2.5f, -8.5f, -2, 2, 2, 1, new HeadKit.Mat(c(0xE8909A)));
    }

    private static void sheep(HeadKit k, Integer v) {
        HeadKit.Cube face = k.box(k.head, -3, -7, -4.5f, 6, 6, 7, new HeadKit.Mat(c(0xD9C7A8)));
        k.decal(face, FRONT, 0, 2, EYES, "wk..kw", "......", "..pp..");
        k.box(k.head, -4, -8.5f, -2.5f, 8, 4, 7, new HeadKit.Mat(c(0xEDEDE8), 0.15f, FUR, c(0xD8D8D0)));
        k.box(k.head, -4.5f, -5.5f, -1, 1, 1, 2, new HeadKit.Mat(c(0xC8B494)));
        k.box(k.head, 3.5f, -5.5f, -1, 1, 1, 2, new HeadKit.Mat(c(0xC8B494)));
    }

    private static void chicken(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -2, -8, -2, 4, 6, 3, new HeadKit.Mat(c(0xF4F4F0)));
        k.decal(skull, FRONT, 0, 1, EYES, "k..k");
        k.box(k.head, -2, -6, -4, 4, 2, 2, new HeadKit.Mat(c(0xF0B020)));
        k.box(k.head, -1, -4, -3, 2, 2, 2, new HeadKit.Mat(c(0xD82020)));
        k.box(k.head, -0.5f, -9, -1.5f, 1, 1, 3, new HeadKit.Mat(c(0xD82020)));
    }

    private static void wolf(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -3, -7, -3, 6, 6, 5, new HeadKit.Mat(c(0xBCB6AE), 0.12f, FUR, c(0xA49E96)));
        HeadKit.Cube snout = k.box(k.head, -1.5f, -4, -6, 3, 3, 3, new HeadKit.Mat(c(0xDCD8D0)));
        k.decal(snout, FRONT, 1, 0, EYES, "k");
        k.decal(skull, FRONT, 0, 2, pal('y', 0xE0C040, 'k', 0x101010), "yk..ky");
        k.box(k.head, -3, -9, -1, 2, 2, 1, new HeadKit.Mat(c(0x9A948C)));
        k.box(k.head, 1, -9, -1, 2, 2, 1, new HeadKit.Mat(c(0x9A948C)));
    }

    private static void cat(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -2.5f, -6, -3, 5, 4, 5, new HeadKit.Mat(c(0xE0A050), 0.1f, STRIPES, c(0xB06A28)));
        HeadKit.Cube muzzle = k.box(k.head, -1.5f, -3, -4, 3, 2, 1, new HeadKit.Mat(c(0xF4E8D0)));
        k.decal(muzzle, FRONT, 1, 0, pal('p', 0xE07AA0), "p");
        k.decal(skull, FRONT, 0, 1, pal('g', 0x6AD040, 'k', 0x101010), "gk.kg");
        k.box(k.head, -2, -7, 0, 1, 1, 2, new HeadKit.Mat(c(0xB06A28)));
        k.box(k.head, 1, -7, 0, 1, 1, 2, new HeadKit.Mat(c(0xB06A28)));
    }

    private static void fox(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -4, -7, -3, 8, 6, 6, new HeadKit.Mat(c(0xE07A2A), 0.1f, FUR, c(0xC8641E)));
        HeadKit.Cube snout = k.box(k.head, -2, -3, -6, 4, 2, 3, new HeadKit.Mat(c(0xF4F0E8)));
        k.decal(snout, FRONT, 1, 0, EYES, "kk");
        k.decal(skull, FRONT, 1, 2, pal('k', 0x101010, 'w', 0xF4F0E8), "wk..kw", "w....w");
        for (int side = -1; side <= 1; side += 2) {
            HeadKit.Cube ear = k.box(k.head, side < 0 ? -4 : 2, -9, -1, 2, 2, 1, new HeadKit.Mat(c(0xE07A2A)));
            k.fill(ear, TOP, 0, 0, 2, 1, c(0x2A2220));
        }
    }

    private static void rabbit(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -2.5f, -6, -3, 5, 5, 5, new HeadKit.Mat(c(0x9A7A5A), 0.12f, FUR, c(0x86684A)));
        k.decal(skull, FRONT, 0, 1, pal('k', 0x101010, 'w', 0xE8DCC8, 'p', 0xE07AA0), "k...k", ".....", "..p..", ".www.");
        for (int side = -1; side <= 1; side += 2) {
            var ear = k.part(k.head, "ear_" + side, side * 1.5f, -6, 0, -10, 0, side * 8);
            HeadKit.Cube e = k.box(ear, -0.5f, -7, -0.5f, 1, 7, 1, new HeadKit.Mat(c(0x9A7A5A)));
            k.fill(e, FRONT, 0, 1, 1, 5, c(0xE8A8B0));
        }
    }

    private static void horse(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -3, -9, -2, 6, 5, 6, new HeadKit.Mat(c(0x7A4A2A)));
        HeadKit.Cube muzzle = k.box(k.head, -2, -7, -7, 4, 4, 5, new HeadKit.Mat(c(0x6A3E22)));
        k.decal(muzzle, FRONT, 0, 2, pal('n', 0x2A1A10), "n..n");
        k.fill(skull, FRONT, 2, 0, 2, 5, c(0xF0E8E0)); // blaze
        k.decal(skull, LEFT, 1, 2, EYES, "k");
        k.decal(skull, RIGHT, 4, 2, EYES, "k");
        k.box(k.head, -1, -11, 0, 2, 7, 4, new HeadKit.Mat(c(0x2A1E16), 0.15f, FUR, c(0x1A120E)));
        k.box(k.head, -3, -10.5f, 0, 1, 2, 1, new HeadKit.Mat(c(0x7A4A2A)));
        k.box(k.head, 2, -10.5f, 0, 1, 2, 1, new HeadKit.Mat(c(0x7A4A2A)));
    }

    private static void llama(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -2, -9, -3, 4, 4, 6, new HeadKit.Mat(c(0xE8DCC0), 0.12f, FUR, c(0xD4C8A8)));
        HeadKit.Cube muzzle = k.box(k.head, -1.5f, -7, -6, 3, 3, 3, new HeadKit.Mat(c(0xD8CCB0)));
        k.decal(muzzle, FRONT, 0, 1, pal('n', 0x4A3A2A), "n.n");
        k.decal(skull, LEFT, 1, 1, EYES, "k");
        k.decal(skull, RIGHT, 4, 1, EYES, "k");
        k.box(k.head, -2, -12, 0, 1, 3, 1, new HeadKit.Mat(c(0xE8DCC0)));
        k.box(k.head, 1, -12, 0, 1, 3, 1, new HeadKit.Mat(c(0xE8DCC0)));
        k.box(k.head, -2, -5, -2, 4, 5, 4, new HeadKit.Mat(c(0xE8DCC0), 0.12f, FUR, c(0xD4C8A8))); // long neck
    }

    private static void bear(HeadKit k, Integer v) {
        boolean polar = v != null;
        int fur = polar ? c(0xF2F0EA) : c(0xF0F0EC);
        HeadKit.Cube skull = k.box(k.head, -4, -7, -3.5f, 8, 7, 7, new HeadKit.Mat(fur, 0.1f, FUR, c(0xDCDAD2)));
        HeadKit.Cube snout = k.box(k.head, -2, -4, -5, 4, 3, 2, new HeadKit.Mat(polar ? c(0xE8E6DE) : c(0xF8F8F4)));
        k.decal(snout, FRONT, 1, 0, EYES, "kk");
        if (polar) k.decal(skull, FRONT, 1, 2, EYES, "k....k");
        else k.decal(skull, FRONT, 0, 2, pal('b', 0x1E1E1E, 'k', 0x050505, 'w', 0xFFFFFF), ".bb..bb.", ".bk..kb.", ".bb..bb.");
        int ear = polar ? fur : c(0x1E1E1E);
        k.box(k.head, -4.5f, -8, -1, 2, 2, 1, new HeadKit.Mat(ear));
        k.box(k.head, 2.5f, -8, -1, 2, 2, 1, new HeadKit.Mat(ear));
    }

    private static void frog(HeadKit k, Integer v) {
        HeadKit.Mat skin = new HeadKit.Mat(c(0x6A8A3A), 0.1f, SPOTS, c(0x4A6228));
        HeadKit.Cube skull = k.box(k.head, -4.5f, -5, -4, 9, 4, 7, skin);
        k.fill(skull, FRONT, 0, 3, 9, 1, c(0x2A3A18)); // wide mouth
        for (int side = -1; side <= 1; side += 2) {
            HeadKit.Cube eye = k.box(k.head, side < 0 ? -4 : 1, -7, -3, 3, 2, 3, new HeadKit.Mat(c(0x7AA048)));
            k.decal(eye, FRONT, 0, 0, pal('y', 0xE8D040, 'k', 0x101010), "yky");
        }
        k.box(k.head, -4, -1, -3, 8, 1, 6, new HeadKit.Mat(c(0xD8C890))); // throat
    }

    private static void axolotl(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -4, -6, -4, 8, 5, 6, new HeadKit.Mat(c(0xF0A8C8)));
        k.decal(skull, FRONT, 0, 1, EYES, "k......k", "........", "..pppp..");
        HeadKit.Mat gill = new HeadKit.Mat(c(0xD8608A));
        for (int i = 0; i < 3; i++) {
            k.box(k.head, -6, -7 + i * 2, -1, 2, 1, 1, gill);
            k.box(k.head, 4, -7 + i * 2, -1, 2, 1, 1, gill);
        }
        k.box(k.head, -2, -8, -1, 4, 2, 1, gill);
    }

    private static void turtle(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -3, -6, -4, 6, 5, 6, new HeadKit.Mat(c(0x5A9A4A)).with(SCALES, c(0x4A8A3A)));
        k.decal(skull, FRONT, 0, 1, EYES, "k....k", "......", ".kkkk.");
        k.box(k.head, -2.5f, -3, -5, 5, 2, 1, new HeadKit.Mat(c(0x4A7A3A)));
        k.box(k.head, -2, -1, -2, 4, 2, 4, new HeadKit.Mat(c(0x5A9A4A))); // neck
    }

    private static void bee(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -3.5f, -7, -3.5f, 7, 7, 7, new HeadKit.Mat(c(0xE8B830), 0.08f, STRIPES, c(0x3A2A14)));
        k.decal(skull, FRONT, 0, 1, pal('k', 0x101010, 'w', 0x6A6A8A), "kk...kk", "kw...wk", "kk...kk");
        for (int side = -1; side <= 1; side += 2) {
            var ant = k.part(k.head, "antenna_" + side, side * 1.5f, -7, -3, -25, 0, side * 15);
            k.box(ant, -0.5f, -4, -0.5f, 1, 4, 1, new HeadKit.Mat(c(0x2A1E10)));
            k.box(ant, -0.5f, -5, -1.5f, 1, 1, 2, new HeadKit.Mat(c(0x2A1E10)));
        }
    }

    private static void spider(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -4, -8, -4, 8, 8, 8, new HeadKit.Mat(c(0x3A3030), 0.12f, FUR, c(0x2A2222)));
        k.decal(skull, FRONT, 0, 1, EYES, ".r.rr.r.", "........", "r.r..r.r", "........", "..rkkr..");
        k.box(k.head, -2.5f, -1, -5, 1, 2, 1, new HeadKit.Mat(c(0x1A1010)));
        k.box(k.head, 1.5f, -1, -5, 1, 2, 1, new HeadKit.Mat(c(0x1A1010)));
    }

    private static void creeper(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -4, -8, -4, 8, 8, 8, new HeadKit.Mat(c(0x5AB04A), 0.15f, PIXELS, c(0xA8E890)));
        k.decal(skull, FRONT, 0, 0, pal('k', 0x101010),
                "........", ".kk..kk.", ".kk..kk.", "...kk...", "..kkkk..", "..kkkk..", "..k..k..", "........");
    }

    private static void squid(HeadKit k, Integer v) {
        boolean glow = v != null;
        int skin = glow ? c(0x1E6A6E) : c(0x3A5A7A);
        HeadKit.Cube skull = k.box(k.head, -4, -10, -4, 8, 8, 8, new HeadKit.Mat(skin, 0.1f, glow ? GLOW : PLAIN, glow ? c(0x6AF0D8) : skin));
        k.decal(skull, FRONT, 1, 4, pal('w', 0xFFFFFF, 'k', 0x101010, 'g', 0x9AFFF0), glow ? "gk..kg" : "wk..kw");
        HeadKit.Mat tent = new HeadKit.Mat(Image.shade(skin, 0.85f));
        for (int i = 0; i < 4; i++) {
            var t = k.part(k.head, "tentacle_" + i, -3 + i * 2, -2, -3.5f, 10, 0, (i - 1.5f) * 6);
            k.box(t, -0.5f, 0, -0.5f, 1, 4 + (i % 2), 1, tent);
        }
    }

    private static void dolphin(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -3.5f, -7, -3, 7, 6, 7, new HeadKit.Mat(c(0x8A9AA8)));
        k.decal(skull, FRONT, 0, 2, EYES, "k.....k");
        k.box(k.head, -1.5f, -3.5f, -6, 3, 2, 3, new HeadKit.Mat(c(0xA8B6C2)));
        k.box(k.head, -0.5f, -10, -1, 1, 3, 4, new HeadKit.Mat(c(0x6A7A88)));
        k.fill(skull, BOTTOM, 0, 0, 7, 7, c(0xD8E0E8));
    }

    private static void goat(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -2.5f, -7, -4, 5, 5, 7, new HeadKit.Mat(c(0xE8E4DC), 0.12f, FUR, c(0xD0CCC4)));
        k.decal(skull, LEFT, 1, 1, pal('y', 0xD8A030, 'k', 0x101010), "ky");
        k.decal(skull, RIGHT, 5, 1, pal('y', 0xD8A030, 'k', 0x101010), "yk");
        k.box(k.head, -1, -2, -4, 2, 3, 1, new HeadKit.Mat(c(0xD8D4CC))); // beard
        for (int side = -1; side <= 1; side += 2) {
            var horn = k.part(k.head, "horn_" + side, side * 1.5f, -7, -2, -40, 0, side * 10);
            k.box(horn, -0.5f, -5, -0.5f, 1, 5, 1, new HeadKit.Mat(c(0x9A8E7A), 0.1f, STRIPES, c(0x7A6E5A)));
            k.box(k.head, side < 0 ? -4 : 2.5f, -6, -1, 2, 1, 1, new HeadKit.Mat(c(0xE8E4DC)));
        }
    }

    private static void parrot(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -2.5f, -7, -2.5f, 5, 5, 5, new HeadKit.Mat(c(0xD8201E)));
        k.decal(skull, FRONT, 0, 1, pal('w', 0xF0F0F0, 'k', 0x101010), "wk.kw");
        k.box(k.head, -1, -5, -4, 2, 2, 2, new HeadKit.Mat(c(0x3A3A3A)));
        k.box(k.head, -1, -3, -4, 2, 1, 1, new HeadKit.Mat(c(0x3A3A3A)));
        k.box(k.head, -0.5f, -10, -1, 1, 3, 3, new HeadKit.Mat(c(0xF0C030), 0.1f, STRIPES, c(0x2A6AD8)));
    }

    private static void strider(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -4, -8, -4, 8, 8, 8, new HeadKit.Mat(c(0xA03A3A)).with(SCALES, c(0x8A2E2E)));
        k.decal(skull, FRONT, 0, 2, pal('k', 0x101010, 'm', 0x5A1E1E), ".k....k.", "........", "..mmmm..");
        HeadKit.Mat hair = new HeadKit.Mat(c(0x5A3A5A));
        for (int i = 0; i < 4; i++) {
            var h = k.part(k.head, "strand_" + i, -3 + i * 2, -8, -1 + (i % 2) * 2, -15 + i * 10, 0, (i - 1.5f) * 15);
            k.box(h, -0.5f, -6, -0.5f, 1, 6, 1, hair);
        }
    }

    private static void hoglin(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -3.5f, -7, -6, 7, 7, 8, new HeadKit.Mat(c(0x8A5A4A), 0.12f, FUR, c(0x744A3C)));
        k.decal(skull, FRONT, 0, 1, pal('k', 0x101010, 'n', 0xC89A70), "k.....k", "", "", ".nnnnn.", ".n...n.");
        k.box(k.head, -4.5f, -3, -6, 1, 3, 1, new HeadKit.Mat(c(0xE8E0C0)));
        k.box(k.head, 3.5f, -3, -6, 1, 3, 1, new HeadKit.Mat(c(0xE8E0C0)));
        k.box(k.head, -5.5f, -7, -1, 2, 1, 3, new HeadKit.Mat(c(0x7A4A3A)));
        k.box(k.head, 3.5f, -7, -1, 2, 1, 3, new HeadKit.Mat(c(0x7A4A3A)));
        k.box(k.head, -1, -9, -4, 2, 2, 6, new HeadKit.Mat(c(0xC89A70), 0.2f, FUR, c(0xA87A50))); // bristles
    }

    private static void blaze(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -4, -8, -4, 8, 8, 8, new HeadKit.Mat(c(0xE8A020), 0.15f, PIXELS, c(0xFFF080)));
        k.decal(skull, FRONT, 0, 2, pal('k', 0x3A1A06, 'o', 0xFFD040), ".kk..kk.", ".......", "..kkkk..");
        HeadKit.Mat rod = new HeadKit.Mat(c(0xF0C020), 0.1f, STRIPES, c(0xC88010));
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i + Math.PI / 4;
            k.box(k.head, (float) (Math.cos(a) * 6) - 0.5f, -10, (float) (Math.sin(a) * 6) - 0.5f, 1, 5, 1, rod);
        }
    }

    private static void guardian(HeadKit k, Integer v) {
        k.box(k.head, -4, -8, -4, 8, 8, 8, new HeadKit.Mat(c(0x5A9A8A)).with(SCALES, c(0x4A8A7A)));
        HeadKit.Cube eye = k.box(k.head, -2, -6, -5, 4, 4, 1, new HeadKit.Mat(c(0xF0F0E8)));
        k.decal(eye, FRONT, 1, 1, pal('o', 0xE07A20, 'k', 0x101010), "ok", "ko");
        HeadKit.Mat spike = new HeadKit.Mat(c(0xC8A070));
        k.box(k.head, -0.5f, -10, -0.5f, 1, 2, 1, spike);
        k.box(k.head, -5.5f, -4.5f, -0.5f, 2, 1, 1, spike);
        k.box(k.head, 3.5f, -4.5f, -0.5f, 2, 1, 1, spike);
        k.box(k.head, -0.5f, -4.5f, 3.5f, 1, 1, 2, spike);
        k.box(k.head, -4.5f, -9, -4.5f, 1, 1, 1, spike);
        k.box(k.head, 3.5f, -9, -4.5f, 1, 1, 1, spike);
    }

    private static void slime(HeadKit k, Integer v) {
        boolean magma = v != null;
        HeadKit.Mat gel = magma ? new HeadKit.Mat(c(0x5A1E10), 0.1f, PIXELS, c(0xFFA020)) : new HeadKit.Mat(c(0x6AC85A), 0.08f, PLAIN, c(0x4AA03A));
        HeadKit.Cube skull = k.box(k.head, -4, -8, -4, 8, 8, 8, gel);
        k.decal(skull, FRONT, 0, 2, pal('k', magma ? 0xFFD040 : 0x2A5A22, 'm', magma ? 0xFFB020 : 0x3A7A2A), ".kk..kk.", ".kk..kk.", "........", "....mm..");
        k.box(k.head, -2, -6, -2, 4, 4, 4, new HeadKit.Mat(magma ? c(0xFFC030) : c(0x4AA03A))); // core, seen through the gel at the edges
    }

    private static void shulker(HeadKit k, Integer v) {
        HeadKit.Mat shell = new HeadKit.Mat(c(0x9A6A9A), 0.06f, PLATES, c(0x7A4A7A));
        k.box(k.head, -4, -9, -4, 8, 5, 8, shell);
        k.box(k.head, -4, -3, -4, 8, 3, 8, shell);
        HeadKit.Cube face = k.box(k.head, -3, -5, -3, 6, 3, 6, new HeadKit.Mat(c(0xE8E070)));
        k.decal(face, FRONT, 0, 0, pal('k', 0x101010), ".k..k.");
    }

    private static void ghast(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -4, -8, -4, 8, 8, 8, new HeadKit.Mat(c(0xF0F0F0), 0.04f, PLAIN, c(0xE0E0E0)));
        k.decal(skull, FRONT, 0, 2, pal('k', 0x303030, 'r', 0xB8B8B8), ".kk..kk.", ".r....r.", "...kk...", "...kk...");
        HeadKit.Mat tent = new HeadKit.Mat(c(0xE8E8E8));
        for (int i = 0; i < 3; i++) k.box(k.head, -3 + i * 2.5f, 0, -3.5f, 1, 3 + i % 2, 1, tent);
    }

    private static void sniffer(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -3.5f, -8, -4, 7, 6, 7, new HeadKit.Mat(c(0x9A3A2A), 0.12f, FUR, c(0x7A2A1E)));
        k.box(k.head, -3.5f, -9, -3, 7, 1, 6, new HeadKit.Mat(c(0x6A8A3A), 0.2f, FUR, c(0x4A6A2A))); // moss
        HeadKit.Cube nose = k.box(k.head, -2.5f, -4, -6.5f, 5, 3, 3, new HeadKit.Mat(c(0x4A7A3A)));
        k.decal(nose, FRONT, 1, 1, pal('k', 0x1A2A10), "k.k");
        k.decal(skull, FRONT, 0, 2, pal('k', 0x101010, 'g', 0x9AD050), ".gk.kg.");
    }

    private static void armadillo(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -2.5f, -6, -4, 5, 4, 5, new HeadKit.Mat(c(0xC88A7A)).with(PLATES, c(0x9A6A5A)));
        k.decal(skull, FRONT, 0, 1, EYES, "k...k");
        k.box(k.head, -1.5f, -4, -6, 3, 2, 2, new HeadKit.Mat(c(0xE0A898)));
        k.box(k.head, -2.5f, -9, -1, 1, 3, 1, new HeadKit.Mat(c(0xD8988A)));
        k.box(k.head, 1.5f, -9, -1, 1, 3, 1, new HeadKit.Mat(c(0xD8988A)));
    }

    private static void bat(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -3, -6, -3, 6, 5, 5, new HeadKit.Mat(c(0x4A3A2E), 0.12f, FUR, c(0x3A2C22)));
        k.decal(skull, FRONT, 0, 1, pal('k', 0x101010, 'w', 0xF0F0F0), "......", ".k..k.", "......", ".w..w.");
        k.box(k.head, -3, -9, -1, 2, 3, 1, new HeadKit.Mat(c(0x5A4636)));
        k.box(k.head, 1, -9, -1, 2, 3, 1, new HeadKit.Mat(c(0x5A4636)));
        k.box(k.head, -1, -3, -4, 2, 1, 1, new HeadKit.Mat(c(0x2A1E16)));
    }

    private static void ravager(HeadKit k, Integer v) {
        HeadKit.Cube skull = k.box(k.head, -4, -8, -6, 8, 8, 8, new HeadKit.Mat(c(0x5A5654), 0.1f, PLATES, c(0x444240)));
        k.decal(skull, FRONT, 0, 1, pal('k', 0x101010, 'g', 0x2A2826), "k......k", "", "", ".gggggg.");
        k.box(k.head, -3.5f, -1, -6.5f, 7, 2, 4, new HeadKit.Mat(c(0x4A4644))); // jaw
        HeadKit.Mat horn = new HeadKit.Mat(c(0xC8BCA8), 0.1f, STRIPES, c(0x9A8E7A));
        for (int side = -1; side <= 1; side += 2) {
            var h = k.part(k.head, "horn_" + side, side * 4, -7, -2, -20, 0, side * -30);
            k.box(h, side < 0 ? -3 : 0, -1, -0.5f, 3, 1, 1, horn);
            k.box(h, side < 0 ? -4 : 2, -4, -0.5f, 1, 3, 1, horn);
        }
    }
}
