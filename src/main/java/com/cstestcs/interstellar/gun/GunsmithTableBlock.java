package com.cstestcs.interstellar.gun;

import com.cstestcs.interstellar.ship.ShipPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Opens the {@link GunsmithMenu}. Directional only for looks. */
public class GunsmithTableBlock extends ShipPartBlock {
    public GunsmithTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            GunDataManager.sendTo(sp);
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new GunsmithMenu(id, inv, ContainerLevelAccess.create(level, pos)),
                    Component.translatable("container.interstellar.gunsmith")));
        }
        return InteractionResult.SUCCESS;
    }
}
