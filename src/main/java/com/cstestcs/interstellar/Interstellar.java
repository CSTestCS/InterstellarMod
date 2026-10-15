package com.cstestcs.interstellar;

import com.cstestcs.interstellar.command.InterstellarCommands;
import com.cstestcs.interstellar.gun.GunDataManager;
import com.cstestcs.interstellar.network.ModPayloads;
import com.cstestcs.interstellar.race.RaceManager;
import com.cstestcs.interstellar.registry.ModAttachments;
import com.cstestcs.interstellar.registry.ModBlocks;
import com.cstestcs.interstellar.registry.ModComponents;
import com.cstestcs.interstellar.registry.ModEntities;
import com.cstestcs.interstellar.registry.ModItems;
import com.cstestcs.interstellar.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(Interstellar.MODID)
public final class Interstellar {
    public static final String MODID = "interstellar";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Interstellar(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModMenus.MENUS.register(modBus);
        modBus.addListener(ModEntities::registerAttributes);
        modBus.addListener(ModEntities::registerSpawnPlacements);
        modBus.addListener(ModPayloads::register);

        container.registerConfig(ModConfig.Type.SYNCED, InterstellarConfig.SERVER_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, InterstellarConfig.CLIENT_SPEC);

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> InterstellarCommands.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent e) -> e.addListener(id("guns"), GunDataManager.INSTANCE));
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> RaceManager.load(e.getServer()));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
