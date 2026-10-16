package com.cstestcs.interstellar.race;

import com.cstestcs.interstellar.core.race.RaceDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Bio-scanner: use it on any creature (or in the air to scan yourself) to read its race's name, favourite item and
 * home dimension.
 */
public class ScannerItem extends Item {
    public ScannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!player.level().isClientSide()) report(player, target);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) report(player, player);
        return InteractionResult.SUCCESS;
    }

    private static void report(Player player, LivingEntity target) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.6f, 1.8f);
        RaceDefinition race = RaceEvents.raceOf(target);
        MutableComponent msg = Component.translatable("message.interstellar.scanner.header", target.getDisplayName()).withStyle(ChatFormatting.AQUA);
        if (race == null) {
            player.sendSystemMessage(msg.append("\n").append(Component.translatable("message.interstellar.scanner.none").withStyle(ChatFormatting.GRAY)));
            return;
        }
        msg.append("\n").append(line("race", Component.literal(race.displayName())));
        msg.append("\n").append(line("favorite", favorite(race.favoriteItem())));
        msg.append("\n").append(line("home", home(race)));
        player.sendSystemMessage(msg);
    }

    private static Component line(String key, Component value) {
        return Component.translatable("message.interstellar.scanner." + key).withStyle(ChatFormatting.GRAY).append(value.copy().withStyle(ChatFormatting.WHITE));
    }

    private static Component favorite(String id) {
        if (id == null || id.isBlank()) return Component.translatable("message.interstellar.scanner.nothing");
        if (id.startsWith("#")) return Component.literal(id);
        Identifier rl = Identifier.tryParse(id);
        if (rl == null) return Component.literal(id);
        return BuiltInRegistries.ITEM.getOptional(rl).map(i -> new ItemStack(i).getHoverName()).orElse(Component.literal(id));
    }

    private static Component home(RaceDefinition race) {
        if (race.homeDimension().isAny() || race.homeDimension().includes().isEmpty()) return Component.translatable("message.interstellar.scanner.anywhere");
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String dim : race.homeDimension().includes()) {
            var world = com.cstestcs.interstellar.core.space.ForeignWorlds.byDimension(dim);
            names.add(world != null ? world.displayName() + " (" + dim + ")" : dim);
        }
        return Component.literal(String.join(", ", names));
    }
}
