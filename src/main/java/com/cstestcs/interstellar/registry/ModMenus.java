package com.cstestcs.interstellar.registry;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.gun.GunsmithMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    private ModMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Interstellar.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<GunsmithMenu>> GUNSMITH = MENUS.register("gunsmith",
            () -> IMenuTypeExtension.create(GunsmithMenu::new));
}
