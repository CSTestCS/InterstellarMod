package com.cstestcs.interstellar.client.race;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Unit;

/** Models used by the race layer. */
public final class RaceModels {
    private RaceModels() {}

    /** Biped wearing a race skin. A subclass so the render hook can recognise and leave it alone. */
    public static final class RaceBiped extends HumanoidModel<HumanoidRenderState> {
        public RaceBiped(ModelPart root) {
            super(root);
        }
    }

    /**
     * Villager clothing laid out on a biped: the villager texture's head/hat, robe, sleeves and legs mapped onto
     * biped-shaped parts, so a raced villager still shows its biome and profession outfit.
     */
    public static final class VillagerOutfit extends HumanoidModel<HumanoidRenderState> {
        public VillagerOutfit(ModelPart root) {
            super(root);
        }

        public static LayerDefinition create() {
            MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
            PartDefinition root = mesh.getRoot();
            PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -10, -4, 8, 10, 8, new CubeDeformation(0.3f)),
                    PartPose.ZERO);
            PartDefinition hat = head.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(32, 0).addBox(-4, -10, -4, 8, 10, 8, new CubeDeformation(0.55f)),
                    PartPose.ZERO);
            hat.addOrReplaceChild("hat_rim", CubeListBuilder.create().texOffs(30, 47).addBox(-8, -8, -6, 16, 16, 1), PartPose.rotation(-(float) Math.PI / 2, 0, 0));
            root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 20).addBox(-4, 0, -3, 8, 12, 6, new CubeDeformation(0.05f))
                    .texOffs(0, 38).addBox(-4, 0, -3, 8, 20, 6, new CubeDeformation(0.55f)), PartPose.ZERO);
            root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(44, 22).addBox(-3, -2, -2, 4, 8, 4, new CubeDeformation(0.3f)),
                    PartPose.offset(-5, 2, 0));
            root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(44, 22).mirror().addBox(-1, -2, -2, 4, 8, 4, new CubeDeformation(0.3f)),
                    PartPose.offset(5, 2, 0));
            root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 22).addBox(-2, 0, -2, 4, 12, 4, new CubeDeformation(0.2f)),
                    PartPose.offset(-1.9f, 12, 0));
            root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 22).mirror().addBox(-2, 0, -2, 4, 12, 4, new CubeDeformation(0.2f)),
                    PartPose.offset(1.9f, 12, 0));
            return LayerDefinition.create(mesh, 64, 64);
        }
    }

    /** Wraps a free-standing part (head model, tail) as a model; the state type is unused. */
    public static final class Part extends Model<Unit> {
        public Part(ModelPart root) {
            super(root, RenderTypes::entityCutout);
        }
    }

    /** Tail with sway animation driven by its state. */
    public static final class Tail extends Model<Tail.State> {
        public record State(float age, float walk) {}

        public Tail(ModelPart root) {
            super(root, RenderTypes::entityCutout);
        }

        @Override
        public void setupAnim(State state) {
            super.setupAnim(state);
            TailModels.animate(root(), state.age(), state.walk());
        }
    }

    private static ModelPart nose;

    /** Villager nose for converted skins (texture region {@link com.cstestcs.interstellar.core.image.SkinConverter#NOSE}). */
    public static ModelPart nose() {
        if (nose == null) {
            MeshDefinition mesh = new MeshDefinition();
            int[] n = com.cstestcs.interstellar.core.image.SkinConverter.NOSE;
            mesh.getRoot().addOrReplaceChild("nose", CubeListBuilder.create().texOffs(n[0], n[1]).addBox(-1, -3, -6, 2, 4, 2), PartPose.ZERO);
            nose = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        }
        return nose;
    }

    public static RaceBiped biped(boolean slim) {
        return new RaceBiped(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot());
    }
}
