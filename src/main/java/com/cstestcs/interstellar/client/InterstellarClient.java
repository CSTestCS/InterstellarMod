package com.cstestcs.interstellar.client;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.InterstellarConfig;
import com.cstestcs.interstellar.client.body.BodyInstances;
import com.cstestcs.interstellar.client.body.BodyLibrary;
import com.cstestcs.interstellar.client.gun.GunHud;
import com.cstestcs.interstellar.client.gun.GunItemModels;
import com.cstestcs.interstellar.client.gun.GunsmithScreen;
import com.cstestcs.interstellar.client.race.ClientRaces;
import com.cstestcs.interstellar.client.race.RaceLayer;
import com.cstestcs.interstellar.client.render.AlienRenderer;
import com.cstestcs.interstellar.client.render.ProjectileRenderer;
import com.cstestcs.interstellar.client.render.RenderInfoExtractor;
import com.cstestcs.interstellar.client.ship.ShipClient;
import com.cstestcs.interstellar.client.ship.SpacecraftRenderer;
import com.cstestcs.interstellar.gun.GunDataManager;
import com.cstestcs.interstellar.gun.GunItem;
import com.cstestcs.interstellar.network.ModPayloads;
import com.cstestcs.interstellar.registry.ModEntities;
import com.cstestcs.interstellar.registry.ModMenus;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.neoforged.neoforge.common.NeoForge;

/** Client entry point: renderers, layers, input, payload handlers. Never loaded on a dedicated server. */
@Mod(value = Interstellar.MODID, dist = Dist.CLIENT)
public final class InterstellarClient {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Interstellar.id("keys"));
    public static final KeyMapping RELOAD = new KeyMapping("key.interstellar.reload", InputConstants.KEY_R, CATEGORY);

    public InterstellarClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(InterstellarClient::registerRenderers);
        modBus.addListener(InterstellarClient::addLayers);
        modBus.addListener(InterstellarClient::registerModifiers);
        modBus.addListener(InterstellarClient::registerKeys);
        modBus.addListener(InterstellarClient::registerScreens);
        modBus.addListener(InterstellarClient::registerPayloads);
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAboveAll(Interstellar.id("gun_hud"), GunHud::render));
        modBus.addListener(GunItemModels::registerSelectProperties);
        modBus.addListener(GunItemModels::registerTints);
        modBus.addListener(GunItemModels::registerItemModels);
        modBus.addListener(com.cstestcs.interstellar.client.space.SpaceClient::registerSky);
        modBus.addListener(OreTint::register);
        modBus.addListener(com.cstestcs.interstellar.client.gun.GunModelPack::onAddPackFinders);
        NeoForge.EVENT_BUS.addListener(com.cstestcs.interstellar.client.space.SpaceClient::submitGlow);

        NeoForge.EVENT_BUS.addListener(InterstellarClient::onRenderLivingPre);
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post e) -> BodyInstances.endFrame());
        NeoForge.EVENT_BUS.addListener(InterstellarClient::onClientTick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> BodyLibrary.reportToServer());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ClientRaces.clear();
            com.cstestcs.interstellar.client.body.BipedSkins.clear();
            com.cstestcs.interstellar.client.space.SpaceClient.clear();
        });
        ShipClient.register();
        // Minecraft.getInstance() is still null while mods are constructed; load bodies once the client exists.
        modBus.addListener((FMLClientSetupEvent e) -> BodyLibrary.reload());
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SPACECRAFT.get(), SpacecraftRenderer::new);
        event.registerEntityRenderer(ModEntities.SEAT.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.ALIEN.get(), AlienRenderer::new);
        event.registerEntityRenderer(ModEntities.GUN_PROJECTILE.get(), ProjectileRenderer::new);
    }

    /** The race layer goes on every living entity renderer; it is a no-op for entities without a race or body. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : event.getEntityTypes()) {
            EntityRenderer<?, ?> r = event.getRenderer(type);
            if (r instanceof LivingEntityRenderer lr) lr.addLayer(new RaceLayer(lr));
        }
        for (var skin : event.getSkins()) {
            LivingEntityRenderer player = event.getPlayerRenderer(skin);
            if (player != null) player.addLayer(new RaceLayer(player));
            LivingEntityRenderer mannequin = event.getMannequinRenderer(skin);
            if (mannequin != null) mannequin.addLayer(new RaceLayer(mannequin));
        }
    }

    /** Every living renderer (and its subclasses) plus players/mannequins, which use the avatar renderer path. */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void registerModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier((Class) LivingEntityRenderer.class,
                (java.util.function.BiConsumer<LivingEntity, LivingEntityRenderState>) (e, st) -> {
                    RenderInfoExtractor.extract(e, st);
                    ShipClient.extract(e, st);
                });
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                RenderInfoExtractor.extract(avatar, state);
                ShipClient.extract(avatar, state);
            }
        });
    }

    /** The renderer knows its model and texture; record them so the render hook can tell the entity's model from layers. */
    private static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        RenderInfoExtractor.complete(event);
        ShipClient.tiltRider(event);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(RELOAD);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.GUNSMITH.get(), GunsmithScreen::new);
    }

    private static void registerPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(ModPayloads.RaceAssets.TYPE, (p, ctx) -> ClientRaces.receive(p));
        event.register(ModPayloads.GunData.TYPE, (p, ctx) -> GunDataManager.receive(p));
        event.register(ModPayloads.Planets.TYPE, (p, ctx) -> com.cstestcs.interstellar.client.space.SpaceClient.receive(p));
        event.register(ModPayloads.SaveOwnBody.TYPE, (p, ctx) -> {
            InterstellarConfig.CLIENT.ownBody.set(p.body());
            InterstellarConfig.CLIENT_SPEC.save();
        });
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GunItem.showDetails = mc.options.keyShift.isDown();
        while (RELOAD.consumeClick()) {
            if (mc.player.getMainHandItem().getItem() instanceof GunItem) ClientPacketDistributor.sendToServer(new ModPayloads.ReloadGun());
        }
        ShipClient.tick(mc);
    }
}
