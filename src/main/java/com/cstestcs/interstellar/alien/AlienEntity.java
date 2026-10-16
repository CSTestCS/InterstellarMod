package com.cstestcs.interstellar.alien;

import com.cstestcs.interstellar.InterstellarConfig;
import com.cstestcs.interstellar.core.space.ForeignWorlds;
import com.cstestcs.interstellar.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

/**
 * The humanoid that alien races are applied to. It has no look of its own: its race (one per foreign world, plus
 * subraces) supplies skin, head model, size, stats, disposition, faction and weapon theme. Aliens carry rolled guns
 * from their world's theme and drop them with a configurable chance (30% by default).
 */
public class AlienEntity extends Monster {
    public AlienEntity(EntityType<? extends AlienEntity> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 4)
                .add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.ARMOR, 4);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new GunAttackGoal(this, 1.0, 18f));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false) {
            @Override
            public boolean canUse() {
                return !GunAttackGoal.hasGun(mob) && super.canUse();
            }
        });
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // Default temperament; the alien's race replaces these with its own disposition and faction rules.
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
            @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
        String dim = level.getLevel().dimension().identifier().toString();
        if (com.cstestcs.interstellar.core.space.PlanetGenome.isPlanet(dim)) homePlanet = dim;
        setDropChance(EquipmentSlot.MAINHAND, InterstellarConfig.SERVER.alienWeaponDropChance.get().floatValue());
        AlienArmor.equip(this, homeDimension(), getRandom(), 0.25f + difficulty.getSpecialMultiplier() * 0.3f);
        return out;
    }

    /** The planet this alien was found on ("" if it spawned anywhere else). */
    private String homePlanet = "";

    public String homePlanet() {
        return homePlanet;
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (!homePlanet.isEmpty()) out.putString("home_planet", homePlanet);
    }

    @Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
        super.readAdditionalSaveData(in);
        homePlanet = in.getStringOr("home_planet", "");
    }

    /** The dimension this alien's race comes from (its race's home), else where it was spawned. */
    public String homeDimension() {
        var race = com.cstestcs.interstellar.race.RaceEvents.raceOf(this);
        if (race != null && !race.homeDimension().includes().isEmpty()) return race.homeDimension().includes().get(0);
        return level().dimension().identifier().toString();
    }

    public static boolean checkSpawnRules(EntityType<AlienEntity> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isValidSpawn(level, below, type) && level.getBlockState(pos).isAir();
    }

    /** Aliens drop alloy plus the material of their world's theme. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        RandomSource r = getRandom();
        if (r.nextFloat() < 0.6f) spawnAtLocation(level, ModItems.ALIEN_MATERIALS.get(0).get(), 1 + r.nextInt(2));
        ForeignWorlds.World world = ForeignWorlds.byDimension(level.dimension().identifier().toString());
        String theme = world == null ? "plasma" : world.theme().weaponTheme;
        Item material = themeMaterial(theme);
        if (material != null && r.nextFloat() < 0.35f) spawnAtLocation(level, material, 1);
    }

    private void spawnAtLocation(ServerLevel level, Item item, int count) {
        spawnAtLocation(level, new net.minecraft.world.item.ItemStack(item, count));
    }

    static @Nullable Item themeMaterial(String theme) {
        String name = switch (theme) {
            case "plasma" -> "plasma_core";
            case "nether" -> "nether_cinder";
            case "void" -> "void_shard";
            case "crystal" -> "crystal_lattice";
            case "bio" -> "bio_gel";
            case "cryo" -> "cryo_crystal";
            case "solar" -> "solar_cell";
            case "hydro" -> "hydro_pearl";
            case "tech" -> "tech_chip";
            case "sonic" -> "sonic_resonator";
            default -> null;
        };
        int i = name == null ? -1 : ModItems.ALIEN_MATERIAL_NAMES.indexOf(name);
        return i < 0 ? null : ModItems.ALIEN_MATERIALS.get(i).get();
    }
}
