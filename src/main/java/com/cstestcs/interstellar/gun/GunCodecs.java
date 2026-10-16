package com.cstestcs.interstellar.gun;

import com.cstestcs.interstellar.core.gun.GunBuild;
import com.cstestcs.interstellar.core.gun.GunData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Codecs for the gun data components. */
public final class GunCodecs {
    private GunCodecs() {}

    public static final Codec<GunData.Slot> SLOT = Codec.STRING.comapFlatMap(
            s -> Optional.ofNullable(GunData.Slot.byKey(s)).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "unknown gun slot " + s)),
            GunData.Slot::key);
    public static final Codec<GunData.Stat> STAT = Codec.STRING.comapFlatMap(
            s -> Optional.ofNullable(GunData.Stat.byKey(s)).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "unknown gun stat " + s)),
            GunData.Stat::key);

    public static final Codec<GunBuild.Choice> CHOICE = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("part").forGetter(GunBuild.Choice::part),
            Codec.STRING.optionalFieldOf("material").forGetter(c -> Optional.ofNullable(c.material()))
    ).apply(i, (p, m) -> new GunBuild.Choice(p, m.orElse(null))));

    public static final Codec<GunData.Trim> TRIM = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("pattern").forGetter(GunData.Trim::pattern),
            Codec.STRING.fieldOf("material").forGetter(GunData.Trim::material),
            Codec.INT.fieldOf("color").forGetter(GunData.Trim::color)
    ).apply(i, GunData.Trim::new));

    public static final Codec<GunBuild> BUILD = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("archetype").forGetter(GunBuild::archetype),
            Codec.unboundedMap(SLOT, CHOICE).optionalFieldOf("parts", Map.of()).forGetter(GunBuild::parts),
            TRIM.optionalFieldOf("trim").forGetter(b -> Optional.ofNullable(b.trim())),
            Codec.unboundedMap(STAT, Codec.DOUBLE).optionalFieldOf("rolls", Map.of()).forGetter(GunBuild::rolls),
            Codec.STRING.optionalFieldOf("frame_material").forGetter(b -> Optional.ofNullable(b.frame()))
    ).apply(i, (a, p, t, r, f) -> new GunBuild(a, enumMap(GunData.Slot.class, p), t.orElse(null), enumMap(GunData.Stat.class, r), f.orElse(null))));

    public static final StreamCodec<ByteBuf, GunBuild> BUILD_STREAM = ByteBufCodecs.fromCodec(BUILD);

    /** Rounds currently loaded and the tick a reload finishes (0 = not reloading). */
    public record GunState(int ammo, long reloadUntil) {
        public static final GunState EMPTY = new GunState(0, 0);
        public static final Codec<GunState> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("ammo").forGetter(GunState::ammo),
                Codec.LONG.optionalFieldOf("reload_until", 0L).forGetter(GunState::reloadUntil)
        ).apply(i, GunState::new));
        public static final StreamCodec<ByteBuf, GunState> STREAM = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GunState::ammo, ByteBufCodecs.VAR_LONG, GunState::reloadUntil, GunState::new);
    }

    /** A magazine: which magazine kind it fits and how many rounds it holds. */
    public record Magazine(String kind, int ammo, int capacity) {
        public static final Codec<Magazine> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("kind").forGetter(Magazine::kind),
                Codec.INT.fieldOf("ammo").forGetter(Magazine::ammo),
                Codec.INT.fieldOf("capacity").forGetter(Magazine::capacity)
        ).apply(i, Magazine::new));
        public static final StreamCodec<ByteBuf, Magazine> STREAM = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Magazine::kind, ByteBufCodecs.VAR_INT, Magazine::ammo, ByteBufCodecs.VAR_INT, Magazine::capacity, Magazine::new);
    }

    private static <K extends Enum<K>, V> Map<K, V> enumMap(Class<K> cls, Map<K, V> in) {
        Map<K, V> m = new EnumMap<>(cls);
        m.putAll(in);
        return m;
    }
}
