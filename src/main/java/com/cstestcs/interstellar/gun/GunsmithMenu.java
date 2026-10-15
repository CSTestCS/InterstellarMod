package com.cstestcs.interstellar.gun;

import com.cstestcs.interstellar.registry.ModBlocks;
import com.cstestcs.interstellar.registry.ModItems;
import com.cstestcs.interstellar.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Gunsmith table: a gun slot, a material slot (the material installed parts are made from, or a trim material) and
 * a trim template slot. Buttons in the screen send {@link com.cstestcs.interstellar.network.ModPayloads.GunsmithAction}s.
 */
public class GunsmithMenu extends AbstractContainerMenu {
    public static final int GUN = 0, MATERIAL = 1, TEMPLATE = 2;
    private final SimpleContainer container = new SimpleContainer(3);
    private final ContainerLevelAccess access;

    public GunsmithMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, ContainerLevelAccess.NULL);
    }

    public GunsmithMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(ModMenus.GUNSMITH.get(), id);
        this.access = access;
        addSlot(new Slot(container, GUN, 26, 98) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.GUN.get());
            }
        });
        addSlot(new Slot(container, MATERIAL, 62, 98));
        addSlot(new Slot(container, TEMPLATE, 98, 98));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 124 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 182));
    }

    public ItemStack gunSlot() {
        return container.getItem(GUN);
    }

    public void setGunSlot(ItemStack stack) {
        container.setItem(GUN, stack);
    }

    public ItemStack materialSlot() {
        return container.getItem(MATERIAL);
    }

    public ItemStack templateSlot() {
        return container.getItem(TEMPLATE);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.GUNSMITH_TABLE.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, container));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 3) {
            if (!moveItemStackTo(stack, 3, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.is(ModItems.GUN.get())) {
            if (!moveItemStackTo(stack, GUN, GUN + 1, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, MATERIAL, MATERIAL + 1, false) && !moveItemStackTo(stack, TEMPLATE, TEMPLATE + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
