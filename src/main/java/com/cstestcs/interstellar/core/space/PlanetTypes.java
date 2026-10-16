package com.cstestcs.interstellar.core.space;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The pool of dimension types planets are made with. Dimension types have to be known to clients before they join, so
 * (unlike the snapshot, which built one per dimension) a fixed pool of randomly styled types ships in the mod's data
 * and every planet picks one: sky, fog and cloud colours, skylight, ambient light and star brightness, in the spirit of
 * the snapshot's random fog colours, light maps and sky settings.
 */
public final class PlanetTypes {
    public static final int COUNT = 24;

    public record Type(int index, int skyColor, int fogColor, int cloudColor, boolean skylight, float ambientLight, float starBrightness) {
        public String id() {
            return "interstellar:planet_type_" + index;
        }
    }

    private static final List<Type> TYPES;

    static {
        List<Type> list = new ArrayList<>();
        Random r = new Random(0x20141L);
        for (int i = 0; i < COUNT; i++) {
            int sky = color(r), fog = r.nextBoolean() ? color(r) : sky, cloud = r.nextInt(3) == 0 ? color(r) : 0xFFFFFF;
            boolean skylight = r.nextInt(8) != 0;
            float ambient = skylight ? (r.nextInt(4) == 0 ? r.nextFloat() * 0.2f : 0f) : 0.1f + r.nextFloat() * 0.2f;
            float stars = r.nextFloat();
            list.add(new Type(i, sky, fog, cloud, skylight, ambient, stars));
        }
        TYPES = List.copyOf(list);
    }

    private PlanetTypes() {}

    public static List<Type> all() {
        return TYPES;
    }

    public static Type get(int index) {
        return TYPES.get(Math.floorMod(index, COUNT));
    }

    private static int color(Random r) {
        return (r.nextInt(256) << 16) | (r.nextInt(256) << 8) | r.nextInt(256);
    }
}
