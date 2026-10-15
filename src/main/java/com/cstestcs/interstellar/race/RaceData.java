package com.cstestcs.interstellar.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Per-entity race state.
 *
 * @param race        race id, "" when the entity has no race (yet)
 * @param skinVariant index into the race's body skins
 * @param headVariant index into the race's head skins (picked independently)
 * @param affection   0..100, how swayed this mob is by favourite items
 * @param owner       recruiting player, if recruited
 * @param rolled      whether race selection already ran (so reloads and chunk loads never re-roll)
 */
public record RaceData(String race, int skinVariant, int headVariant, float affection, Optional<UUID> owner, boolean rolled) {
    public static final RaceData NONE = new RaceData("", 0, 0, 0, Optional.empty(), false);

    public static final Codec<RaceData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("race").forGetter(RaceData::race),
            Codec.INT.optionalFieldOf("skin", 0).forGetter(RaceData::skinVariant),
            Codec.INT.optionalFieldOf("head", 0).forGetter(RaceData::headVariant),
            Codec.FLOAT.optionalFieldOf("affection", 0f).forGetter(RaceData::affection),
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(RaceData::owner),
            Codec.BOOL.optionalFieldOf("rolled", false).forGetter(RaceData::rolled)
    ).apply(i, RaceData::new));

    public static final StreamCodec<ByteBuf, RaceData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RaceData::race,
            ByteBufCodecs.VAR_INT, RaceData::skinVariant,
            ByteBufCodecs.VAR_INT, RaceData::headVariant,
            ByteBufCodecs.FLOAT, RaceData::affection,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), RaceData::owner,
            ByteBufCodecs.BOOL, RaceData::rolled,
            RaceData::new);

    public boolean hasRace() {
        return !race.isEmpty();
    }

    public boolean isRecruited() {
        return owner.isPresent();
    }

    public RaceData withAffection(float a) {
        return new RaceData(race, skinVariant, headVariant, Math.max(0, Math.min(100, a)), owner, rolled);
    }

    public RaceData withOwner(UUID o) {
        return new RaceData(race, skinVariant, headVariant, affection, Optional.ofNullable(o), rolled);
    }
}
