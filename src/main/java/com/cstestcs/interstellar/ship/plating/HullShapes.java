package com.cstestcs.interstellar.ship.plating;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** Shared settings of the hull plating shapes. */
public final class HullShapes {
    private HullShapes() {}

    /** Metal doors, trapdoors and buttons that still open by hand. */
    public static final BlockSetType HULL = BlockSetType.register(new BlockSetType("interstellar_hull", true, true, false,
            BlockSetType.PressurePlateSensitivity.MOBS, SoundType.METAL, SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN,
            SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN, SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON));
}
