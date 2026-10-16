package com.cstestcs.interstellar.registry;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.core.ship.ShipScanner;
import com.cstestcs.interstellar.gun.GunsmithTableBlock;
import com.cstestcs.interstellar.ship.ControlPanelBlock;
import com.cstestcs.interstellar.ship.LandingGearBlock;
import com.cstestcs.interstellar.ship.PassengerSeatBlock;
import com.cstestcs.interstellar.ship.ShipPartBlock;
import com.cstestcs.interstellar.space.AtmosphereBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All blocks. Ship parts share classes; which part a block counts as comes from block tags, so other mods' blocks can count too. */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Interstellar.MODID);

    private static BlockBehaviour.Properties metal(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<LandingGearBlock> LANDING_GEAR = BLOCKS.registerBlock("landing_gear", LandingGearBlock::new, p -> metal(p).noOcclusion());
    public static final DeferredBlock<ControlPanelBlock> CONTROL_PANEL = BLOCKS.registerBlock("control_panel", ControlPanelBlock::new, p -> metal(p).noOcclusion().lightLevel(s -> 7));
    public static final DeferredBlock<ShipPartBlock> SHIP_GENERATOR = BLOCKS.registerBlock("ship_generator", ShipPartBlock::new, p -> metal(p).lightLevel(s -> 10));
    public static final DeferredBlock<ShipPartBlock> LIFE_SUPPORT = BLOCKS.registerBlock("life_support", ShipPartBlock::new, p -> metal(p).lightLevel(s -> 5));
    public static final DeferredBlock<ShipPartBlock> SHIP_THRUSTER = BLOCKS.registerBlock("ship_thruster", ShipPartBlock::new, p -> metal(p).lightLevel(s -> 9));
    public static final DeferredBlock<com.cstestcs.interstellar.ship.CannonBlock> SHIP_CANNON = BLOCKS.registerBlock("ship_cannon",
            com.cstestcs.interstellar.ship.CannonBlock::new, p -> metal(p).noOcclusion());
    public static final DeferredBlock<PassengerSeatBlock> PASSENGER_SEAT = BLOCKS.registerBlock("passenger_seat", PassengerSeatBlock::new, p -> metal(p).noOcclusion().strength(1.5f));
    public static final DeferredBlock<com.cstestcs.interstellar.ship.WheelBlock> WHEEL = BLOCKS.registerBlock("wheel",
            com.cstestcs.interstellar.ship.WheelBlock::new, p -> metal(p).noOcclusion().sound(SoundType.WOOL).strength(2.0f, 6.0f));
    /** Ground land vehicles are assembled and parked on; never part of the vehicle itself. */
    public static final DeferredBlock<Block> VEHICLE_PAD = BLOCKS.registerSimpleBlock("vehicle_pad", ModBlocks::metal);
    public static final DeferredBlock<Block> HULL_PLATING = BLOCKS.registerSimpleBlock("hull_plating", ModBlocks::metal);
    public static final DeferredBlock<GunsmithTableBlock> GUNSMITH_TABLE = BLOCKS.registerBlock("gunsmith_table", GunsmithTableBlock::new, p -> metal(p).noOcclusion());
    /** Invisible glowing shell around every planet in space. */
    public static final DeferredBlock<AtmosphereBlock> ATMOSPHERE = BLOCKS.registerBlock("atmosphere", AtmosphereBlock::new,
            p -> p.noCollision().noOcclusion().noLootTable().strength(-1.0f, 3600000.0f).lightLevel(s -> 12)
                    .replaceable().mapColor(MapColor.NONE));

    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Interstellar.MODID);
    public static final java.util.function.Supplier<net.minecraft.world.level.block.entity.BlockEntityType<com.cstestcs.interstellar.ship.CannonBlockEntity>> CANNON_ENTITY =
            BLOCK_ENTITIES.register("ship_cannon", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    com.cstestcs.interstellar.ship.CannonBlockEntity::new, SHIP_CANNON.get()));

    /** Alien resources: ores that generate in each foreign world's base stone (tinted to match it), and storage blocks. */
    public static final java.util.List<String> ORE_MATERIALS = java.util.List.of("plasma_core", "nether_cinder", "void_shard", "crystal_lattice",
            "bio_gel", "cryo_crystal", "solar_cell", "hydro_pearl", "tech_chip", "sonic_resonator");
    public static final java.util.List<String> STORAGE_MATERIALS = java.util.List.of("alien_alloy", "plasma_core", "nether_cinder", "void_shard",
            "crystal_lattice", "bio_gel", "cryo_crystal", "solar_cell", "hydro_pearl", "tech_chip", "sonic_resonator");
    public static final java.util.Map<String, DeferredBlock<Block>> ALIEN_ORES = new java.util.LinkedHashMap<>();
    public static final java.util.Map<String, DeferredBlock<Block>> MATERIAL_BLOCKS = new java.util.LinkedHashMap<>();

    static {
        for (String m : ORE_MATERIALS) {
            ALIEN_ORES.put(m, BLOCKS.registerBlock(m + "_ore", p -> new net.minecraft.world.level.block.DropExperienceBlock(
                    net.minecraft.util.valueproviders.UniformInt.of(2, 5), p), p -> p.mapColor(MapColor.STONE).strength(3.0f, 3.0f)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops()));
        }
        for (String m : STORAGE_MATERIALS) MATERIAL_BLOCKS.put(m, BLOCKS.registerSimpleBlock(m + "_block", ModBlocks::metal));
    }

    /** Block tags deciding which ship part a block counts as (packs may add other mods' blocks). */
    public static final class Tags {
        public static final TagKey<Block> LANDING_GEAR = tag("ship/landing_gear");
        public static final TagKey<Block> GENERATORS = tag("ship/generators");
        public static final TagKey<Block> CONTROL_PANELS = tag("ship/control_panels");
        public static final TagKey<Block> LIFE_SUPPORT = tag("ship/life_support");
        public static final TagKey<Block> SEATS = tag("ship/seats");
        public static final TagKey<Block> WEAPONS = tag("ship/weapons");
        public static final TagKey<Block> THRUSTERS = tag("ship/thrusters");
        public static final TagKey<Block> WHEELS = tag("ship/wheels");
        public static final TagKey<Block> VEHICLE_PADS = tag("ship/vehicle_pads");
        /** Blocks a spacecraft can never contain (bedrock, portals, ...). */
        public static final TagKey<Block> UNMOVABLE = tag("ship/unmovable");

        private static TagKey<Block> tag(String path) {
            return TagKey.create(Registries.BLOCK, Interstellar.id(path));
        }
    }

    public static ShipScanner.Kind kindOf(net.minecraft.world.level.block.state.BlockState state) {
        if (state.isAir()) return ShipScanner.Kind.AIR;
        if (state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) return ShipScanner.Kind.AIR; // never pick up loose fluids
        if (state.is(Tags.UNMOVABLE) || state.getDestroySpeed(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO) < 0)
            return ShipScanner.Kind.UNMOVABLE;
        if (state.is(Tags.LANDING_GEAR)) return ShipScanner.Kind.LANDING_GEAR;
        if (state.is(Tags.WHEELS)) return ShipScanner.Kind.WHEEL;
        if (state.is(Tags.VEHICLE_PADS)) return ShipScanner.Kind.VEHICLE_PAD;
        if (state.is(Tags.CONTROL_PANELS)) return ShipScanner.Kind.CONTROL_PANEL;
        if (state.is(Tags.GENERATORS)) return ShipScanner.Kind.GENERATOR;
        if (state.is(Tags.LIFE_SUPPORT)) return ShipScanner.Kind.LIFE_SUPPORT;
        if (state.is(Tags.SEATS)) return ShipScanner.Kind.SEAT;
        if (state.is(Tags.WEAPONS)) return ShipScanner.Kind.WEAPON;
        if (state.is(Tags.THRUSTERS)) return ShipScanner.Kind.THRUSTER;
        return ShipScanner.Kind.SOLID;
    }
}
