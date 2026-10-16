package com.cstestcs.interstellar.race;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.InterstellarConfig;
import com.cstestcs.interstellar.body.BodyServer;
import com.cstestcs.interstellar.core.gun.GunBuild;
import com.cstestcs.interstellar.core.race.RaceDefinition;
import com.cstestcs.interstellar.core.race.RaceFiles;
import com.cstestcs.interstellar.core.race.SpawnContext;
import com.cstestcs.interstellar.gun.GunDataManager;
import com.cstestcs.interstellar.gun.GunItem;
import com.cstestcs.interstellar.registry.ModAttachments;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Applies races to bipeds when they first enter a world and implements race behaviour: size and stat multipliers,
 * traits, disposition and faction AI, preferred weapons, and recruitment with favourite items.
 */
@EventBusSubscriber(modid = Interstellar.MODID)
public final class RaceEvents {
    /** Entity types that can carry races. Vanilla bipeds are listed in data/interstellar/tags/entity_type/bipeds.json. */
    public static final TagKey<EntityType<?>> BIPEDS = TagKey.create(Registries.ENTITY_TYPE, Interstellar.id("bipeds"));
    private static final Identifier SIZE_ID = Interstellar.id("race_size");
    private static final Identifier HEALTH_ID = Interstellar.id("race_health");
    private static final Identifier SPEED_ID = Interstellar.id("race_speed");
    private static final Identifier DAMAGE_ID = Interstellar.id("race_damage");

    private RaceEvents() {}

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof LivingEntity living)) return;
        if (!(living instanceof Player) && !living.getType().builtInRegistryHolder().is(BIPEDS)) return;
        RaceData data = living.getData(ModAttachments.RACE);
        if (!data.rolled() && InterstellarConfig.SERVER.racesEnabled.get()) {
            data = roll(level, living);
            living.setData(ModAttachments.RACE, data);
            RaceDefinition race = RaceManager.get(data.race());
            if (race != null) firstApply(level, living, race);
        }
        RaceDefinition race = RaceManager.get(data.race());
        if (race != null) applyEveryLoad(living, race, data);
        BodyServer.assignIfMissing(living, race);
    }

    /** Re-roll an entity's race (used by commands); keeps nothing from the old race. */
    public static void setRace(LivingEntity living, RaceDefinition race) {
        long seed = living.getUUID().getMostSignificantBits() ^ living.getUUID().getLeastSignificantBits();
        RaceFiles.Entry files = RaceManager.files(race.id());
        int skins = files == null ? 1 : files.skins.size(), heads = files == null ? 1 : files.headSkins.size();
        living.setData(ModAttachments.RACE, new RaceData(race.id(), RaceFiles.pickVariant(skins, seed, 1), RaceFiles.pickVariant(heads, seed, 2),
                0, java.util.Optional.empty(), true));
        if (living.level() instanceof ServerLevel level) firstApply(level, living, race);
        applyEveryLoad(living, race, living.getData(ModAttachments.RACE));
        BodyServer.reassign(living, race);
    }

    private static RaceData roll(ServerLevel level, LivingEntity living) {
        if (living instanceof Player && !InterstellarConfig.SERVER.assignRacesToPlayers.get()) {
            RaceDefinition human = RaceManager.get("human");
            return new RaceData(human == null ? "" : "human", 0, 0, 0, java.util.Optional.empty(), true);
        }
        SpawnContext ctx = context(level, living);
        long seed = living.getUUID().getMostSignificantBits() ^ living.getUUID().getLeastSignificantBits();
        RaceDefinition race = RaceManager.registry().select(ctx, new SplittableRandom(seed));
        if (race == null) return new RaceData("", 0, 0, 0, java.util.Optional.empty(), true);
        // Variants are picked independently for body and head skins.
        RaceFiles.Entry files = RaceManager.files(race.id());
        int skins = files == null ? 1 : files.skins.size(), heads = files == null ? 1 : files.headSkins.size();
        return new RaceData(race.id(), RaceFiles.pickVariant(skins, seed, 1), RaceFiles.pickVariant(heads, seed, 2), 0, java.util.Optional.empty(), true);
    }

    static SpawnContext context(ServerLevel level, LivingEntity living) {
        Set<String> typeTags = new HashSet<>();
        living.getType().builtInRegistryHolder().tags().forEach(t -> typeTags.add(t.location().toString()));
        BlockPos pos = living.blockPosition();
        Holder<Biome> biome = level.getBiome(pos);
        Set<String> biomeTags = new HashSet<>();
        biome.tags().forEach(t -> biomeTags.add(t.location().toString()));
        String biomeId = biome.unwrapKey().map(k -> k.identifier().toString()).orElse("minecraft:plains");
        return new SpawnContext(EntityType.getKey(living.getType()).toString(), typeTags, level.dimension().identifier().toString(), Set.of(),
                biomeId, biomeTags);
    }

    /** One-time effects of receiving a race: name, preferred weapon, full health at the new maximum. */
    private static void firstApply(ServerLevel level, LivingEntity living, RaceDefinition race) {
        SplittableRandom rng = new SplittableRandom(living.getUUID().getLeastSignificantBits());
        applyAttributes(living, race);
        living.setHealth(living.getMaxHealth());
        if (living instanceof Mob mob) {
            if (!race.names().isEmpty() && !mob.hasCustomName()) {
                mob.setCustomName(Component.literal(race.names().get(rng.nextInt(race.names().size()))));
            }
            equipPreferredWeapon(level, mob, race, rng);
        }
    }

    /** Effects that are not saved with the entity (AI goals, trait effects) and must be re-added on every load. */
    private static void applyEveryLoad(LivingEntity living, RaceDefinition race, RaceData data) {
        applyAttributes(living, race);
        for (java.util.Map.Entry<String, Holder<MobEffect>> t : traitEffects().entrySet()) {
            Holder<MobEffect> effect = t.getValue();
            if (race.traits().contains(t.getKey())) {
                if (!living.hasEffect(effect)) living.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, true, false));
            } else {
                // A trait the race no longer has (edited race file): drop the permanent effect it left behind.
                MobEffectInstance inst = living.getEffect(effect);
                if (inst != null && inst.isInfiniteDuration() && inst.isAmbient()) living.removeEffect(effect);
            }
        }
        if (living instanceof PathfinderMob mob && !race.fallback()) applyDisposition(mob, race);
        if (living instanceof PathfinderMob mob) {
            // Any raced mob that ends up holding a gun knows how to use it.
            if (!(mob instanceof com.cstestcs.interstellar.alien.AlienEntity)) {
                mob.goalSelector.addGoal(2, new com.cstestcs.interstellar.alien.GunAttackGoal(mob, 1.0, 16f));
            }
            mob.goalSelector.addGoal(3, new RaceGoals.FollowRecruiter(mob));
            mob.targetSelector.addGoal(1, new RaceGoals.DefendRecruiter(mob));
        }
    }

    private static java.util.Map<String, Holder<MobEffect>> traitEffects() {
        return java.util.Map.of("fire_immune", MobEffects.FIRE_RESISTANCE, "water_breathing", MobEffects.WATER_BREATHING,
                "night_vision", MobEffects.NIGHT_VISION, "slow_fall", MobEffects.SLOW_FALLING, "glowing", MobEffects.GLOWING,
                "regeneration", MobEffects.REGENERATION);
    }

    private static void applyAttributes(LivingEntity living, RaceDefinition race) {
        modifier(living, Attributes.SCALE, SIZE_ID, race.size() - 1);
        modifier(living, Attributes.MAX_HEALTH, HEALTH_ID, race.healthMultiplier() - 1);
        modifier(living, Attributes.MOVEMENT_SPEED, SPEED_ID, race.speedMultiplier() - 1);
        modifier(living, Attributes.ATTACK_DAMAGE, DAMAGE_ID, race.damageMultiplier() - 1);
    }

    private static void modifier(LivingEntity living, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, Identifier id, double amount) {
        AttributeInstance inst = living.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (Math.abs(amount) > 1e-6) inst.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /**
     * Custom (non-fallback) races override the mob's vanilla temperament. Hostile races hunt players and their
     * enemy factions, neutral ones only retaliate, passive ones never attack.
     */
    private static void applyDisposition(PathfinderMob mob, RaceDefinition race) {
        boolean canFight = mob.getAttribute(Attributes.ATTACK_DAMAGE) != null;
        switch (race.disposition()) {
            case PASSIVE -> {
                mob.targetSelector.removeAllGoals(g -> !(g instanceof RaceGoals.DefendRecruiter));
                mob.setTarget(null);
            }
            case NEUTRAL -> {
                mob.targetSelector.removeAllGoals(g -> !(g instanceof RaceGoals.DefendRecruiter));
                if (canFight) mob.targetSelector.addGoal(2, new HurtByTargetGoal(mob));
            }
            case HOSTILE -> {
                if (canFight) {
                    mob.goalSelector.addGoal(2, new MeleeAttackGoal(mob, 1.0, false));
                    mob.targetSelector.addGoal(2, new HurtByTargetGoal(mob));
                    mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, Player.class, true));
                }
            }
        }
        if (canFight && !race.enemies().isEmpty()) {
            mob.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false,
                    (target, level) -> race.isEnemyOf(raceOf(target))));
        }
    }

    public static RaceDefinition raceOf(LivingEntity e) {
        return RaceManager.get(e.getData(ModAttachments.RACE).race());
    }

    private static void equipPreferredWeapon(ServerLevel level, Mob mob, RaceDefinition race, SplittableRandom rng) {
        if (race.weapons().isEmpty()) return;
        boolean alien = mob.getType() == com.cstestcs.interstellar.registry.ModEntities.ALIEN.get();
        if (!alien && mob.getMainHandItem().isEmpty()) return; // only change the weapon of mobs that spawned armed
        String kind = race.weapons().get(rng.nextInt(race.weapons().size()));
        ItemStack weapon = weaponFor(kind, race, alien, rng);
        if (!weapon.isEmpty()) mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
    }

    private static ItemStack weaponFor(String kind, RaceDefinition race, boolean alien, SplittableRandom rng) {
        if (kind.equals("gun") || kind.startsWith("gun:")) {
            GunBuild build = GunDataManager.registry().roll(kind.startsWith("gun:") ? kind.substring(4) : race.weaponTheme(), race.weaponTier(),
                    alien || !race.weaponTheme().equals("human"), rng);
            return build == null ? ItemStack.EMPTY : GunItem.create(build, true);
        }
        Item item = switch (kind) {
            case "sword" -> Items.IRON_SWORD;
            case "axe" -> Items.IRON_AXE;
            case "shovel" -> Items.IRON_SHOVEL;
            case "bow" -> Items.BOW;
            case "crossbow" -> Items.CROSSBOW;
            case "trident" -> Items.TRIDENT;
            case "mace" -> Items.MACE;
            default -> BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(kind)).orElse(Items.AIR);
        };
        return new ItemStack(item);
    }

    // ------------------------------------------------------------------ recruitment

    /** Giving a mob its race's favourite item raises affection; at 100 the mob is recruited by that player. */
    @SubscribeEvent
    static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Mob mob) || !(event.getEntity() instanceof ServerPlayer player)) return;
        RaceData data = mob.getData(ModAttachments.RACE);
        RaceDefinition race = RaceManager.get(data.race());
        if (race == null || !race.canBeSwayed()) return;
        ItemStack stack = event.getItemStack();
        if (!matches(stack, race.favoriteItem())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        ServerLevel level = (ServerLevel) mob.level();
        if (data.owner().map(o -> o.equals(player.getUUID())).orElse(false)) {
            player.sendOverlayMessage(Component.translatable("message.interstellar.race.already_recruited", mob.getDisplayName()));
            return;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        float affection = data.affection() + (float) race.favoriteItemPotency();
        if (affection >= 100) {
            mob.setData(ModAttachments.RACE, data.withAffection(100).withOwner(player.getUUID()));
            mob.setTarget(null);
            mob.setPersistenceRequired();
            level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getEyeY(), mob.getZ(), 7, 0.4, 0.4, 0.4, 0.05);
            player.sendOverlayMessage(Component.translatable("message.interstellar.race.recruited", mob.getDisplayName()));
        } else {
            mob.setData(ModAttachments.RACE, data.withAffection(affection));
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getEyeY(), mob.getZ(), 4, 0.3, 0.3, 0.3, 0.02);
            player.sendOverlayMessage(Component.translatable("message.interstellar.race.affection", mob.getDisplayName(), Math.round(affection)));
        }
    }

    private static boolean matches(ItemStack stack, String favorite) {
        if (stack.isEmpty() || favorite == null) return false;
        if (favorite.startsWith("#")) {
            Identifier tag = Identifier.tryParse(favorite.substring(1));
            return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
        }
        Identifier id = Identifier.tryParse(favorite);
        return id != null && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }

    // ------------------------------------------------------------------ factions, traits

    /** Mobs never target allies (same faction or allied faction) or the player who recruited them. */
    @SubscribeEvent
    static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (target == null) return;
        LivingEntity self = event.getEntity();
        RaceData data = self.getData(ModAttachments.RACE);
        if (data.owner().isPresent() && data.owner().get().equals(target.getUUID())) {
            event.setCanceled(true);
            return;
        }
        RaceDefinition mine = RaceManager.get(data.race()), theirs = raceOf(target);
        if (mine != null && theirs != null && mine.faction() != null && mine.isAlliedWith(theirs) && self.getLastHurtByMob() != target) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        RaceDefinition race = raceOf(event.getEntity());
        if (race != null && race.hasTrait("no_fall_damage")) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) RaceManager.sendAssets(player);
    }
}
