package com.cstestcs.interstellar.client.render;

import com.cstestcs.interstellar.InterstellarConfig;
import com.cstestcs.interstellar.client.body.BodyInstances;
import com.cstestcs.interstellar.client.body.BodyLibrary;
import com.cstestcs.interstellar.client.race.ClientRaces;
import com.cstestcs.interstellar.core.body.BodyModel;
import com.cstestcs.interstellar.race.RaceData;
import com.cstestcs.interstellar.registry.ModAttachments;
import com.cstestcs.interstellar.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.VillagerDataHolder;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/** Fills {@link RenderInfo} for each living entity's render state. */
public final class RenderInfoExtractor {
    private RenderInfoExtractor() {}

    /** Mobs whose own texture uses the 64x64 biped skin layout, so it can be wrapped onto a custom body. */
    private static Set<EntityType<?>> bipedTextured;

    private static Set<EntityType<?>> bipedTextured() {
        if (bipedTextured == null) {
            bipedTextured = Set.of(EntityTypes.PLAYER, EntityTypes.MANNEQUIN, EntityTypes.ZOMBIE, EntityTypes.HUSK, EntityTypes.DROWNED,
                    EntityTypes.GIANT, EntityTypes.PIGLIN, EntityTypes.PIGLIN_BRUTE, EntityTypes.ZOMBIFIED_PIGLIN, ModEntities.ALIEN.get());
        }
        return bipedTextured;
    }

    public static void extract(LivingEntity entity, LivingEntityRenderState state) {
        RaceData data = entity.getData(ModAttachments.RACE);
        ClientRaces.Race race = ClientRaces.get(data.race());
        String bodyName = entity.getData(ModAttachments.BODY);
        BodyModel body = BodyLibrary.body(bodyName);
        if (body != null) {
            int max = InterstellarConfig.CLIENT.bodyRenderDistance.get();
            var viewer = Minecraft.getInstance().player;
            if (viewer != null && entity.distanceToSqr(viewer) > (double) max * max) body = null;
        }
        if (race == null && body == null) {
            state.setRenderData(RenderInfo.KEY, null);
            return;
        }
        RenderInfo info = new RenderInfo();
        info.race = race;
        info.skinVariant = data.skinVariant();
        info.headVariant = data.headVariant();
        info.ageInTicks = state.ageInTicks;
        info.walkSpeed = state.walkAnimationSpeed;
        info.bipedTexture = bipedTextured().contains(entity.getType());
        if (entity instanceof VillagerDataHolder holder) {
            info.villagerLike = true;
            info.outfit = outfit(entity, holder);
        }
        if (body != null && (info.replacesBody() || info.bipedTexture)) info.body = BodyInstances.update(entity, state, body);
        state.setRenderData(RenderInfo.KEY, info);
    }

    /** Biome type, profession and level textures, matching the vanilla villager layers. */
    private static List<Identifier> outfit(LivingEntity entity, VillagerDataHolder holder) {
        List<Identifier> out = new ArrayList<>();
        String base = entity.getType() == EntityTypes.ZOMBIE_VILLAGER ? "zombie_villager" : "villager";
        var vd = holder.getVillagerData();
        vd.type().unwrapKey().ifPresent(k -> out.add(Identifier.withDefaultNamespace("textures/entity/" + base + "/type/" + k.identifier().getPath() + ".png")));
        vd.profession().unwrapKey().ifPresent(k -> {
            if (!k.identifier().getPath().equals("none")) {
                out.add(Identifier.withDefaultNamespace("textures/entity/" + base + "/profession/" + k.identifier().getPath() + ".png"));
                String level = switch (vd.level()) {
                    case 1 -> "stone";
                    case 2 -> "iron";
                    case 3 -> "gold";
                    case 4 -> "emerald";
                    default -> "diamond";
                };
                if (base.equals("villager")) out.add(Identifier.withDefaultNamespace("textures/entity/villager/profession_level/" + level + ".png"));
            }
        });
        return out;
    }

    /** Called from {@code RenderLivingEvent.Pre}: only the renderer knows its model and texture. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void complete(RenderLivingEvent.Pre<?, ?, ?> event) {
        LivingEntityRenderState state = event.getRenderState();
        RenderInfo info = state.getRenderData(RenderInfo.KEY);
        if (info == null) return;
        info.mainModel = event.getRenderer().getModel();
        info.texture = ((net.minecraft.client.renderer.entity.LivingEntityRenderer) event.getRenderer()).getTextureLocation(state);
    }
}
