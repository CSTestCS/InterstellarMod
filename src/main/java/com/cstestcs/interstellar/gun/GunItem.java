package com.cstestcs.interstellar.gun;

import com.cstestcs.interstellar.core.gun.GunBuild;
import com.cstestcs.interstellar.core.gun.GunData;
import com.cstestcs.interstellar.core.gun.GunRegistry;
import com.cstestcs.interstellar.registry.ModComponents;
import com.cstestcs.interstellar.registry.ModItems;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The single item behind every gun. Everything that makes a gun what it is lives in the {@code gun_build}
 * component; loaded rounds and reload state live in {@code gun_state}. Guns have no durability.
 * <ul>
 *     <li>Semi-automatic: one shot per use. Burst: one burst per use. Automatic: fires while use is held.</li>
 *     <li>Charge: hold use for the charge time, release to fire.</li>
 *     <li>Reload with the reload key (R by default) using a magazine of the gun's magazine kind.</li>
 * </ul>
 */
public class GunItem extends Item {
    /** Set by the client while shift is held so tooltips list every part. */
    public static volatile boolean showDetails;

    public GunItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(GunBuild build, boolean loaded) {
        ItemStack stack = new ItemStack(ModItems.GUN.get());
        stack.set(ModComponents.GUN_BUILD.get(), build);
        GunRegistry.Stats stats = GunDataManager.registry().compute(build);
        stack.set(ModComponents.GUN_STATE.get(), new GunCodecs.GunState(loaded && stats != null ? stats.magazine() : 0, 0));
        return stack;
    }

    public static GunRegistry.@Nullable Stats stats(ItemStack stack) {
        GunBuild build = stack.get(ModComponents.GUN_BUILD.get());
        return build == null ? null : GunDataManager.registry().compute(build);
    }

    public static GunCodecs.GunState state(ItemStack stack) {
        return stack.getOrDefault(ModComponents.GUN_STATE.get(), GunCodecs.GunState.EMPTY);
    }

    // ------------------------------------------------------------------ use

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        GunRegistry.Stats stats = stats(stack);
        if (stats == null || !stats.complete()) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.interstellar.gun.incomplete"));
            return InteractionResult.FAIL;
        }
        if (stats.fireMode() == GunData.FireMode.MELEE) return InteractionResult.PASS; // blades strike with attack
        GunCodecs.GunState state = state(stack);
        if (state.reloadUntil() > level.getGameTime()) return InteractionResult.FAIL;
        if (state.ammo() <= 0) {
            if (level instanceof ServerLevel) GunActions.reload((net.minecraft.server.level.ServerPlayer) player);
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        if (stats.fireMode() == GunData.FireMode.SEMI || stats.fireMode() == GunData.FireMode.BURST) {
            if (!player.getCooldowns().isOnCooldown(stack)) fire(level, player, stack, stats);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        GunRegistry.Stats stats = stats(stack);
        if (stats == null) return;
        int used = getUseDuration(stack, entity) - remaining;
        switch (stats.fireMode()) {
            case AUTO -> {
                if (used % stats.cooldownTicks() == 0) {
                    if (!fire(level, entity, stack, stats)) entity.releaseUsingItem();
                }
            }
            case BURST -> {
                int burst = (int) Math.round(stats.get(GunData.Stat.BURST));
                int gap = Math.max(1, Math.min(3, stats.cooldownTicks()));
                if (used > 0 && used % gap == 0 && used / gap < burst) fire(level, entity, stack, stats);
                if (used / gap >= burst) entity.releaseUsingItem();
            }
            default -> {}
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        GunRegistry.Stats stats = stats(stack);
        if (stats == null) return false;
        if (stats.fireMode() == GunData.FireMode.CHARGE) {
            int charged = getUseDuration(stack, entity) - remaining;
            if (charged >= stats.get(GunData.Stat.CHARGE_TIME) * 20) {
                fire(level, entity, stack, stats);
                return true;
            }
        }
        return false;
    }

    /** Fires one shot (all pellets). @return false if the gun is empty or reloading. */
    public static boolean fire(Level level, LivingEntity shooter, ItemStack stack, GunRegistry.Stats stats) {
        if (stats.fireMode() == GunData.FireMode.MELEE) return false;
        GunCodecs.GunState state = state(stack);
        boolean infinite = !(shooter instanceof Player) || ((Player) shooter).getAbilities().instabuild;
        if (state.reloadUntil() > level.getGameTime()) return false;
        if (state.ammo() <= 0 && !infinite) return false;
        if (level instanceof ServerLevel server) {
            GunFiring.shoot(server, shooter, stats, GunFiring.color(stats));
            if (!infinite) stack.set(ModComponents.GUN_STATE.get(), new GunCodecs.GunState(state.ammo() - 1, 0));
            if (shooter instanceof Player p && stats.fireMode() != GunData.FireMode.AUTO) {
                int burst = stats.fireMode() == GunData.FireMode.BURST ? (int) Math.round(stats.get(GunData.Stat.BURST)) * 3 : 0;
                p.getCooldowns().addCooldown(stack, Math.max(stats.cooldownTicks(), burst));
            }
        } else if (shooter instanceof Player p) {
            // Recoil kick on the client; the server is authoritative for everything else.
            float recoil = (float) stats.get(GunData.Stat.RECOIL) * (p.isCrouching() ? 0.5f : 1f);
            p.setXRot(p.getXRot() - recoil * 0.6f);
            p.setYRot(p.getYRot() + (p.getRandom().nextFloat() - 0.5f) * recoil * 0.3f);
        }
        return true;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        GunRegistry.Stats stats = stats(stack);
        return stats != null && stats.get(GunData.Stat.ZOOM) > 1.5 ? ItemUseAnimation.SPYGLASS : ItemUseAnimation.CROSSBOW;
    }

    // ------------------------------------------------------------------ presentation

    @Override
    public Component getName(ItemStack stack) {
        GunBuild build = stack.get(ModComponents.GUN_BUILD.get());
        if (build == null) return super.getName(stack);
        String id = build.archetype();
        return Component.translatableWithFallback("gun.interstellar." + id.replace(':', '.'), pretty(id));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        GunBuild build = stack.get(ModComponents.GUN_BUILD.get());
        GunRegistry.Stats stats = stats(stack);
        if (build == null || stats == null) {
            out.accept(Component.translatable("tooltip.interstellar.gun.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        GunCodecs.GunState state = state(stack);
        out.accept(Component.translatable("tooltip.interstellar.gun.ammo", state.ammo(), stats.magazine(), pretty(stats.magazineKind()))
                .withStyle(ChatFormatting.YELLOW));
        out.accept(Component.literal(pretty(stats.fireMode().name()) + " · " + pretty(stats.ammo().name()) + " · Tier " + stats.tier())
                .withStyle(stats.alien() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA));
        if (!stats.complete()) out.accept(Component.translatable("message.interstellar.gun.incomplete").withStyle(ChatFormatting.RED));
        line(out, "damage", stats.get(GunData.Stat.DAMAGE) * stats.get(GunData.Stat.PELLETS), "%.1f");
        line(out, "fire_rate", stats.get(GunData.Stat.FIRE_RATE), "%.0f rpm");
        line(out, "range", stats.get(GunData.Stat.RANGE), "%.0f");
        line(out, "reload_time", stats.get(GunData.Stat.RELOAD_TIME), "%.1fs");
        line(out, "spread", stats.get(GunData.Stat.SPREAD), "%.1f°");
        if (!stats.effects().isEmpty()) out.accept(Component.literal("✦ " + String.join(", ", stats.effects())).withStyle(ChatFormatting.GOLD));
        if (flag.isAdvanced() || com.cstestcs.interstellar.gun.GunItem.showDetails) {
            for (Map.Entry<GunData.Slot, GunBuild.Choice> e : build.parts().entrySet()) {
                String mat = e.getValue().material() == null ? "" : " (" + pretty(e.getValue().material()) + ")";
                out.accept(Component.literal(" " + pretty(e.getKey().key()) + ": " + pretty(e.getValue().part()) + mat).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (build.trim() != null) out.accept(Component.literal(" Trim: " + pretty(build.trim().pattern())).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void line(Consumer<Component> out, String stat, double v, String fmt) {
        out.accept(Component.translatable("stat.interstellar.gun." + stat).append(": " + String.format(Locale.ROOT, fmt, v)).withStyle(ChatFormatting.GRAY));
    }

    public static String pretty(String id) {
        String s = id.substring(id.indexOf(':') + 1).replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder();
        for (String w : s.split(" ")) {
            if (w.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    /** Creative tab: one example of every archetype, rolled deterministically. */
    public static void addCreativeExamples(CreativeModeTab.Output out) {
        SplittableRandom rng = new SplittableRandom(1414);
        for (GunData.Archetype a : GunDataManager.registry().archetypes()) {
            GunBuild b = GunDataManager.registry().example(a.id(), 5, rng);
            if (b != null) out.accept(create(new GunBuild(b.archetype(), b.parts(), null, Map.of(), b.frame()), true));
        }
    }
}
