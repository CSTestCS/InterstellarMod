package com.cstestcs.interstellar.gun;

import com.cstestcs.interstellar.registry.ModComponents;
import com.cstestcs.interstellar.registry.ModItems;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * One item for every magazine kind (pistol, rifle, shell, energy cell, crystal cartridge ...). Kind, rounds and
 * capacity are data components; empty magazines are refilled at the gunsmith table.
 */
public class MagazineItem extends Item {
    public static final int DEFAULT_CAPACITY = 60;

    public MagazineItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(String kind, int ammo, int capacity) {
        ItemStack stack = new ItemStack(ModItems.MAGAZINE.get());
        stack.set(ModComponents.MAGAZINE.get(), new GunCodecs.Magazine(kind, ammo, capacity));
        return stack;
    }

    public static GunCodecs.Magazine data(ItemStack stack) {
        return stack.get(ModComponents.MAGAZINE.get());
    }

    @Override
    public Component getName(ItemStack stack) {
        GunCodecs.Magazine m = data(stack);
        if (m == null) return super.getName(stack);
        return Component.translatable("item.interstellar.magazine.kind", GunItem.pretty(m.kind()));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return data(stack) != null;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        GunCodecs.Magazine m = data(stack);
        return m == null || m.capacity() <= 0 ? 0 : Math.round(13f * m.ammo() / m.capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        GunCodecs.Magazine m = data(stack);
        float f = m == null || m.capacity() <= 0 ? 0 : (float) m.ammo() / m.capacity();
        return Mth.hsvToRgb(f / 3f, 1f, 1f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        GunCodecs.Magazine m = data(stack);
        if (m != null) out.accept(Component.translatable("tooltip.interstellar.magazine.rounds", m.ammo(), m.capacity()).withStyle(ChatFormatting.GRAY));
    }
}
