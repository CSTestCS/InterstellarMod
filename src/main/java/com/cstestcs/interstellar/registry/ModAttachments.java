package com.cstestcs.interstellar.registry;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.race.RaceData;
import java.util.function.Supplier;
import net.minecraft.network.codec.ByteBufCodecs;
import com.mojang.serialization.Codec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Interstellar.MODID);

    /** Race, texture variants, affection and owner of a biped. Synced so every client renders the same thing. */
    public static final Supplier<AttachmentType<RaceData>> RACE = ATTACHMENTS.register("race", () -> AttachmentType.builder(() -> RaceData.NONE)
            .serialize(RaceData.CODEC.fieldOf("value")).sync(RaceData.STREAM_CODEC).copyOnDeath().build());

    /**
     * Custom body chosen by the server from the union of bodies clients reported, "" for none. Synced so that every
     * client that has the body installed renders the same one on the same mob.
     */
    public static final Supplier<AttachmentType<String>> BODY = ATTACHMENTS.register("body", () -> AttachmentType.builder(() -> "")
            .serialize(Codec.STRING.fieldOf("value")).sync(ByteBufCodecs.STRING_UTF8).copyOnDeath().build());

    /**
     * Body a player picked with {@code /race body}: a body name, "none" for the standard player model, or "" when
     * the player never chose (then the client's {@code ownBody} setting decides). Saved with the player.
     */
    public static final Supplier<AttachmentType<String>> BODY_CHOICE = ATTACHMENTS.register("body_choice", () -> AttachmentType.builder(() -> "")
            .serialize(Codec.STRING.fieldOf("value")).copyOnDeath().build());
}
