package com.cstestcs.interstellar.client.render;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.client.body.BodyInstances;
import com.cstestcs.interstellar.client.race.ClientRaces;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import org.jspecify.annotations.Nullable;

/**
 * Per-frame race/body information attached to an entity's render state. Filled when the state is extracted (race,
 * variants, body pose) and completed in {@code RenderLivingEvent.Pre} (main model and texture, which only the
 * renderer knows).
 */
public final class RenderInfo {
    public static final ContextKey<RenderInfo> KEY = new ContextKey<>(Interstellar.id("render_info"));

    public ClientRaces.@Nullable Race race;
    public int skinVariant, headVariant;
    public BodyInstances.@Nullable Instance body;
    /** The renderer's own model instance; every other HumanoidModel drawn with this state is a layer (armour...). */
    public @Nullable Object mainModel;
    public @Nullable Identifier texture;
    /** Biped skin layout: vanilla texture can be wrapped on custom bodies (players, zombies, piglins...). */
    public boolean bipedTexture;
    /** Villager-like model whose clothes must be moved onto the race's biped. */
    public boolean villagerLike;
    /** Villager type, profession and level textures, drawn on the race biped. */
    public java.util.List<Identifier> outfit = java.util.List.of();
    public float ageInTicks, walkSpeed;

    /** The race replaces the whole model with a biped wearing RACENAME_skin. */
    public boolean replacesBody() {
        return race != null && race.def.replaceBody() && race.skin(skinVariant) != null;
    }

    public boolean hasRaceHead() {
        return race != null && race.head != null;
    }

    /** Whether a custom body is drawn for this entity this frame. */
    public boolean drawsBody() {
        return body != null && (replacesBody() || bipedTexture);
    }
}
