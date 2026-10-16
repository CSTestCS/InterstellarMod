package com.cstestcs.interstellar.gun;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.gun.GunData;
import com.cstestcs.interstellar.core.gun.GunRegistry;
import com.cstestcs.interstellar.registry.ModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Laser and energy swords (melee archetypes). Their attack damage and speed come from their stats while the blade
 * has charge (an empty blade is only a hilt); every hit uses one charge and applies the on-hit effects of its parts
 * and materials, like a gun's shots.
 */
@EventBusSubscriber(modid = Interstellar.MODID)
public final class BladeEvents {
    private static final net.minecraft.resources.Identifier DAMAGE = Interstellar.id("blade_damage"), SPEED = Interstellar.id("blade_speed");
    /** Effects such as chain lightning hurt other mobs with the same attack; they must not trigger more effects. */
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);

    private BladeEvents() {}

    static GunRegistry.Stats blade(ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem)) return null;
        GunRegistry.Stats stats = GunItem.stats(stack);
        return stats != null && stats.fireMode() == GunData.FireMode.MELEE && stats.complete() ? stats : null;
    }

    @SubscribeEvent
    static void onAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        GunRegistry.Stats stats = blade(stack);
        if (stats == null) return;
        boolean lit = GunItem.state(stack).ammo() > 0;
        double damage = lit ? stats.get(GunData.Stat.DAMAGE) - 1 : 0; // players punch for 1
        double speed = stats.get(GunData.Stat.FIRE_RATE) / 60.0 - 4; // swings per second, base 4
        event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(DAMAGE, damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        event.addModifier(Attributes.ATTACK_SPEED, new AttributeModifier(SPEED, speed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
    }

    @SubscribeEvent
    static void onHit(LivingDamageEvent.Post event) {
        if (APPLYING.get()) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) return;
        if (!(attacker.level() instanceof ServerLevel level)) return;
        ItemStack stack = attacker.getMainHandItem();
        GunRegistry.Stats stats = blade(stack);
        if (stats == null) return;
        GunCodecs.GunState state = GunItem.state(stack);
        if (state.ammo() <= 0) {
            if (attacker instanceof Player p) p.sendOverlayMessage(Component.translatable("message.interstellar.gun.blade_off"));
            return;
        }
        LivingEntity target = event.getEntity();
        APPLYING.set(true);
        try {
            GunFiring.applyEffects(level, attacker, target, target.position().add(0, target.getBbHeight() * 0.6, 0), stats, event.getInflictedDamage(), source);
        } finally {
            APPLYING.set(false);
        }
        boolean infinite = !(attacker instanceof Player p) || p.getAbilities().instabuild;
        if (!infinite) stack.set(ModComponents.GUN_STATE.get(), new GunCodecs.GunState(state.ammo() - 1, state.reloadUntil()));
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 1.8f);
    }
}
