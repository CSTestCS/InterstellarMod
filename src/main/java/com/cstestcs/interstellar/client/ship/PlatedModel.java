package com.cstestcs.interstellar.client.ship;

import com.cstestcs.interstellar.Interstellar;
import com.cstestcs.interstellar.ship.plating.HullPlatingBlock;
import com.cstestcs.interstellar.ship.plating.HullSlab;
import com.cstestcs.interstellar.ship.plating.Platable;
import com.cstestcs.interstellar.ship.plating.Plating;
import com.cstestcs.interstellar.ship.plating.Tilt;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.quad.BakedNormals;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Model of every platable block. It turns sideways shapes (see {@link Tilt}) and covers the block with its plating:
 * a plain hull block (or a double slab) shows the plating's own model; shapes and ship parts keep their shape, and
 * every face drawn with a hull texture takes the quads the plating shows on the same side (sprite, render layer,
 * tint, light and colours), so the plating looks the same on them as it does as a block. Screens, glowing cores,
 * seat cushions and other details keep their own textures.
 */
public final class PlatedModel implements BlockStateModel {
    /** Textures that are hull: these faces take the plating. */
    private static final Set<String> CASING = Set.of("block/ship_side", "block/ship_top", "block/landing_gear_top");

    private final BlockStateModel base;
    private final boolean whole;
    private final Map<Key, BlockStateModelPart> parts = new ConcurrentHashMap<>();

    private record Key(BlockStateModelPart part, @Nullable BlockState plating, Direction tilt) {}

    private record GeometryKey(@Nullable Object base, @Nullable BlockState plating, Direction tilt) {}

    private PlatedModel(BlockStateModel base, boolean whole) {
        this.base = base;
        this.whole = whole;
    }

    /** Wraps the models of every platable block once the block models are baked. */
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
        int n = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof Platable)) continue;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                BlockStateModel model = models.get(state);
                if (model == null || model instanceof PlatedModel) continue;
                boolean whole = block instanceof HullPlatingBlock || block instanceof HullSlab && state.getValue(HullSlab.TYPE) == SlabType.DOUBLE;
                try {
                    models.put(state, new PlatedModel(model, whole));
                    n++;
                } catch (UnsupportedOperationException e) {
                    Interstellar.LOGGER.error("Cannot wrap block models: plated blocks will not show their plating");
                    return;
                }
            }
        }
        Interstellar.LOGGER.debug("Wrapped {} platable block models", n);
    }

    /** Plated blocks take the tints of their plating (grass, leaves...). */
    public static void registerTints(RegisterColorHandlersEvent.BlockTintSources event) {
        List<BlockTintSource> layers = new ArrayList<>();
        for (int i = 0; i < 4; i++) layers.add(new PlatingTint(i));
        Block[] blocks = BuiltInRegistries.BLOCK.stream().filter(b -> b instanceof Platable).toArray(Block[]::new);
        event.register(List.copyOf(layers), blocks);
    }

    private record PlatingTint(int layer) implements BlockTintSource {
        @Override
        public int color(BlockState state) {
            return -1;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            BlockState plating = level.getModelData(pos).get(Plating.PROPERTY);
            if (plating == null) return -1;
            BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(plating, layer);
            return source == null ? -1 : source.colorInWorld(plating, level, pos);
        }
    }

    private static BlockStateModel model(BlockState state) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
    }

    // ---- without a level (items, fallbacks): the plain block

    @Override
    @SuppressWarnings("deprecation")
    public void collectParts(RandomSource random, List<BlockStateModelPart> out) {
        base.collectParts(random, out);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Material.Baked particleMaterial() {
        return base.particleMaterial();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int materialFlags() {
        return base.materialFlags();
    }

    // ---- in a level

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> out) {
        BlockState plating = level.getModelData(pos).get(Plating.PROPERTY);
        Direction tilt = Tilt.of(state);
        if (plating != null && whole) {
            model(plating).collectParts(level, pos, plating, random, out);
            return;
        }
        if (plating == null && tilt == Direction.UP) {
            base.collectParts(level, pos, state, random, out);
            return;
        }
        List<BlockStateModelPart> own = new ArrayList<>();
        base.collectParts(level, pos, state, random, own);
        for (BlockStateModelPart part : own) out.add(parts.computeIfAbsent(new Key(part, plating, tilt), PlatedModel::build));
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState plating = level.getModelData(pos).get(Plating.PROPERTY);
        return plating != null ? model(plating).particleMaterial(level, pos, plating) : base.particleMaterial(level, pos, state);
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockState plating = level.getModelData(pos).get(Plating.PROPERTY);
        int own = base.materialFlags(level, pos, state);
        return plating != null ? own | model(plating).materialFlags(level, pos, plating) : own;
    }

    @Override
    public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        BlockState plating = level.getModelData(pos).get(Plating.PROPERTY);
        Direction tilt = Tilt.of(state);
        if (plating == null && tilt == Direction.UP) return base.createGeometryKey(level, pos, state, random);
        Object inner = plating != null && whole ? model(plating).createGeometryKey(level, pos, plating, random) : base.createGeometryKey(level, pos, state, random);
        return new GeometryKey(inner, plating, tilt);
    }

    // ---- building the turned, plated parts

    private static BlockStateModelPart build(Key key) {
        List<BlockStateModelPart> skin = null;
        if (key.plating != null) {
            skin = new ArrayList<>();
            model(key.plating).collectParts(RandomSource.create(42L), skin);
        }
        int[][] m = Tilt.matrix(key.tilt);
        Map<Direction, List<BakedQuad>> culled = new EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) culled.put(d, new ArrayList<>());
        List<BakedQuad> loose = new ArrayList<>();
        for (Direction d : Direction.values()) {
            for (BakedQuad q : key.part.getQuads(d)) cover(turn(q, m, key.tilt), skin, culled.get(Tilt.toWorld(key.tilt, d)));
        }
        for (BakedQuad q : key.part.getQuads(null)) cover(turn(q, m, key.tilt), skin, loose);
        int flags = key.part.materialFlags();
        for (List<BakedQuad> list : culled.values()) for (BakedQuad q : list) flags |= q.materialInfo().flags();
        for (BakedQuad q : loose) flags |= q.materialInfo().flags();
        Material.Baked particle = skin != null && !skin.isEmpty() ? skin.get(0).particleMaterial() : key.part.particleMaterial();
        return new Part(culled, loose, key.part.useAmbientOcclusion(), particle, flags);
    }

    private static BakedQuad turn(BakedQuad q, int[][] m, Direction tilt) {
        if (tilt == Direction.UP) return q;
        return new BakedQuad(turn(q.position0(), m), turn(q.position1(), m), turn(q.position2(), m), turn(q.position3(), m),
                q.packedUV0(), q.packedUV1(), q.packedUV2(), q.packedUV3(), Tilt.toWorld(tilt, q.direction()), q.materialInfo(),
                BakedNormals.UNSPECIFIED, q.bakedColors());
    }

    private static Vector3fc turn(Vector3fc p, int[][] m) {
        float x = p.x() - 0.5f, y = p.y() - 0.5f, z = p.z() - 0.5f;
        return new Vector3f(m[0][0] * x + m[0][1] * y + m[0][2] * z + 0.5f, m[1][0] * x + m[1][1] * y + m[1][2] * z + 0.5f,
                m[2][0] * x + m[2][1] * y + m[2][2] * z + 0.5f);
    }

    /** A hull face takes the plating's quads for that side; any other face is kept. */
    private static void cover(BakedQuad face, @Nullable List<BlockStateModelPart> skin, List<BakedQuad> out) {
        if (skin == null || !casing(face.materialInfo().sprite())) {
            out.add(face);
            return;
        }
        List<BakedQuad> found = new ArrayList<>();
        for (BlockStateModelPart part : skin) {
            found.addAll(part.getQuads(face.direction()));
            for (BakedQuad q : part.getQuads(null)) if (q.direction() == face.direction()) found.add(q);
        }
        if (found.isEmpty()) {
            out.add(face);
            return;
        }
        for (BakedQuad s : found) out.add(retexture(face, s));
    }

    private static boolean casing(TextureAtlasSprite sprite) {
        Identifier id = sprite.contents().name();
        return id.getNamespace().equals(Interstellar.MODID) && (id.getPath().startsWith("block/hull_plating") || CASING.contains(id.getPath()));
    }

    /** The face's shape with the skin quad's material: UVs moved from the face's sprite into the area the skin uses. */
    private static BakedQuad retexture(BakedQuad face, BakedQuad skin) {
        TextureAtlasSprite from = face.materialInfo().sprite();
        float su0 = Float.MAX_VALUE, sv0 = Float.MAX_VALUE, su1 = -Float.MAX_VALUE, sv1 = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            long uv = skin.packedUV(i);
            su0 = Math.min(su0, UVPair.unpackU(uv));
            su1 = Math.max(su1, UVPair.unpackU(uv));
            sv0 = Math.min(sv0, UVPair.unpackV(uv));
            sv1 = Math.max(sv1, UVPair.unpackV(uv));
        }
        float fu = from.getU1() - from.getU0(), fv = from.getV1() - from.getV0();
        long[] uvs = new long[4];
        for (int i = 0; i < 4; i++) {
            long uv = face.packedUV(i);
            float lu = fu == 0 ? 0 : (UVPair.unpackU(uv) - from.getU0()) / fu;
            float lv = fv == 0 ? 0 : (UVPair.unpackV(uv) - from.getV0()) / fv;
            uvs[i] = UVPair.pack(su0 + lu * (su1 - su0), sv0 + lv * (sv1 - sv0));
        }
        return new BakedQuad(face.position0(), face.position1(), face.position2(), face.position3(), uvs[0], uvs[1], uvs[2], uvs[3], face.direction(),
                skin.materialInfo(), face.bakedNormals(), skin.bakedColors());
    }

    private record Part(Map<Direction, List<BakedQuad>> culled, List<BakedQuad> loose, boolean ao, Material.Baked particle, int flags)
            implements BlockStateModelPart {
        @Override
        public List<BakedQuad> getQuads(@Nullable Direction side) {
            return side == null ? loose : culled.get(side);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return ao;
        }

        @Override
        public Material.Baked particleMaterial() {
            return particle;
        }

        @Override
        public int materialFlags() {
            return flags;
        }
    }

    @Override
    public String toString() {
        return "PlatedModel[" + base + (whole ? ", whole" : "") + "]";
    }
}
