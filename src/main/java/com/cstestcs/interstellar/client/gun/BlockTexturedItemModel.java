package com.cstestcs.interstellar.client.gun;

import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Item model {@code interstellar:block_textured}: the shape of {@code model} (a gun part) covered with the material
 * block's own baked model. Every face takes the quads the block shows on the same side, copying each quad's sprite,
 * render type, tint, light emission, colours and other material data, so anything a resource pack or mod gives the
 * block's model (retextures, emissive or metallic layers, overlays, custom render types, PBR sprites) shows on the
 * gun as well. The block model is read when the item is drawn, so it always matches the loaded resources.
 */
public final class BlockTexturedItemModel implements ItemModel {
    private final List<ItemTintSource> tints;
    private final List<BakedQuad> shape;
    private final ItemQuads fallback;
    private final Supplier<Vector3fc[]> extents;
    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;
    private final Identifier blockId;
    private @Nullable BlockStateModel source;
    private ItemQuads quads;
    private boolean animated;

    private BlockTexturedItemModel(List<ItemTintSource> tints, QuadCollection shape, ModelRenderProperties properties, Matrix4fc transformation, Identifier blockId) {
        this.tints = tints;
        this.shape = shape.getAll();
        this.fallback = ItemQuads.split(this.shape);
        this.quads = fallback;
        this.properties = properties;
        this.transformation = transformation;
        this.blockId = blockId;
        List<BakedQuad> all = this.shape;
        Vector3fc[] ext = CuboidItemModelWrapper.computeExtents(all);
        this.extents = () -> ext;
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext displayContext, @Nullable ClientLevel level,
            @Nullable ItemOwner owner, int seed) {
        refresh();
        output.appendModelIdentityElement(this);
        ItemStackRenderState.LayerRenderState layer = output.newLayer();
        if (item.hasFoil()) {
            layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
            output.setAnimated();
            output.appendModelIdentityElement(ItemStackRenderState.FoilType.STANDARD);
        }
        if (!tints.isEmpty()) {
            var tintLayers = layer.tintLayers();
            for (ItemTintSource tint : tints) {
                int c = tint.calculate(item, level, owner == null ? null : owner.asLivingEntity());
                tintLayers.add(c);
                output.appendModelIdentityElement(c);
            }
        }
        layer.setExtents(extents);
        layer.setLocalTransform(transformation);
        properties.applyToLayer(layer, displayContext);
        layer.setQuads(quads);
        if (animated) output.setAnimated();
    }

    /** Rebuilds the quads when the block's model changed (first use, resource reload). */
    private void refresh() {
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
        if (block == null) return;
        BlockState state = block.defaultBlockState();
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        if (model == source) return;
        source = model;
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        List<BakedQuad> out = new ArrayList<>();
        for (BakedQuad q : shape) {
            List<BakedQuad> skins = blockQuads(parts, q.direction());
            if (skins.isEmpty()) {
                out.add(q);
                continue;
            }
            for (BakedQuad skin : skins) out.add(retexture(q, skin));
        }
        quads = out.isEmpty() ? fallback : ItemQuads.split(out);
        animated = false;
        for (BakedQuad q : out) if ((q.materialInfo().flags() & BakedQuad.FLAG_ANIMATED) != 0) animated = true;
    }

    /** The block's quads facing {@code side} (culled and unculled), else those of any side. */
    private static List<BakedQuad> blockQuads(List<BlockStateModelPart> parts, Direction side) {
        List<BakedQuad> found = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            found.addAll(part.getQuads(side));
            for (BakedQuad q : part.getQuads(null)) if (q.direction() == side) found.add(q);
        }
        if (found.isEmpty()) {
            for (BlockStateModelPart part : parts) {
                for (Direction d : Direction.values()) {
                    if (found.isEmpty()) found.addAll(part.getQuads(d));
                }
            }
        }
        return found;
    }

    /**
     * The part's face with the block quad's material: positions and normals from the part, UVs moved from the part's
     * placeholder sprite into the area the block quad uses, everything else from the block quad.
     */
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

    public record Unbaked(Identifier model, Identifier block, Optional<Transformation> transformation, List<ItemTintSource> tints) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model),
                Identifier.CODEC.fieldOf("block").forGetter(Unbaked::block),
                Transformation.EXTENDED_CODEC.optionalFieldOf("transformation").forGetter(Unbaked::transformation),
                ItemTintSources.CODEC.listOf().optionalFieldOf("tints", List.of()).forGetter(Unbaked::tints)
        ).apply(i, Unbaked::new));

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(model);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transform) {
            ModelBaker baker = context.blockModelBaker();
            ResolvedModel resolved = baker.getModel(model);
            TextureSlots slots = resolved.getTopTextureSlots();
            QuadCollection quads = resolved.bakeTopGeometry(slots, baker, BlockModelRotation.IDENTITY);
            ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, resolved, slots);
            return new BlockTexturedItemModel(tints, quads, properties, Transformation.compose(transform, transformation), block);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
