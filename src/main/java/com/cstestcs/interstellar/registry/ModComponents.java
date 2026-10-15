package com.cstestcs.interstellar.registry;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.gun.GunBuild;
import com.cstestcs.interstellar.gun.GunCodecs;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    private ModComponents() {}

    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Interstellar.MODID);

    /** The full configuration of a gun (archetype, parts, materials, trim, rolls). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GunBuild>> GUN_BUILD = COMPONENTS.registerComponentType("gun_build",
            b -> b.persistent(GunCodecs.BUILD).networkSynchronized(GunCodecs.BUILD_STREAM).cacheEncoding());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GunCodecs.GunState>> GUN_STATE = COMPONENTS.registerComponentType("gun_state",
            b -> b.persistent(GunCodecs.GunState.CODEC).networkSynchronized(GunCodecs.GunState.STREAM));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GunCodecs.Magazine>> MAGAZINE = COMPONENTS.registerComponentType("magazine",
            b -> b.persistent(GunCodecs.Magazine.CODEC).networkSynchronized(GunCodecs.Magazine.STREAM));
}
