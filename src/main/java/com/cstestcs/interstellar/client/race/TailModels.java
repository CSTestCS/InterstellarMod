package com.cstestcs.interstellar.client.race;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Built-in tails. Every cube maps its whole box UV into the top-left 8x8 pixels of the skin (a 2x6x2 box UV is
 * exactly 8x8), so a tail takes its colour and pattern from that otherwise unused corner of RACENAME_skin.png.
 * The root sits at the base of the spine; segments are chained so they can sway.
 */
public final class TailModels {
    private TailModels() {}

    /** segments, segment length (<=6), thickness (<=2), droop (radians per segment), tip style */
    private record Shape(int segments, float length, float thick, float droop, String tip) {}

    private static Shape shape(String type) {
        return switch (type) {
            case "cat" -> new Shape(4, 4, 1, 0.35f, "none");
            case "fox", "wolf" -> new Shape(3, 5, 2, 0.45f, "fluff");
            case "lizard" -> new Shape(5, 5, 2, 0.15f, "none");
            case "dragon" -> new Shape(6, 5, 2, 0.12f, "spade");
            case "demon" -> new Shape(5, 4, 1, 0.25f, "spade");
            case "monkey" -> new Shape(5, 4, 1, -0.3f, "none");
            case "rabbit", "bunny" -> new Shape(1, 2, 2, 0.6f, "fluff");
            case "fish", "mermaid" -> new Shape(3, 5, 2, 0.1f, "fin");
            default -> new Shape(3, 4, 1.5f, 0.3f, "none");
        };
    }

    public static ModelPart bake(String type) {
        Shape s = shape(type);
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parent = mesh.getRoot();
        float w = s.thick;
        for (int i = 0; i < s.segments; i++) {
            float t = s.segments == 1 ? 1 : 1 - i * 0.12f;
            float ww = Math.max(0.5f, w * t);
            CubeListBuilder cube = CubeListBuilder.create().texOffs(0, 0).addBox(-ww / 2, 0, -ww / 2, ww, s.length, ww);
            // First segment points backwards and down from the lower back; the rest bend progressively.
            PartPose pose = i == 0 ? PartPose.offsetAndRotation(0, 11, 2, 0.9f + s.droop, 0, 0)
                    : PartPose.offsetAndRotation(0, s.length - 0.3f, 0, s.droop, 0, 0);
            parent = parent.addOrReplaceChild("segment" + i, cube, pose);
        }
        switch (s.tip) {
            case "fluff" -> parent.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 0).addBox(-1, 0, -1, 2, 2, 2).texOffs(0, 0).addBox(-1.25f, 0.5f, -1.25f, 2, 2, 2),
                    PartPose.offset(0, s.length - 1, 0));
            case "spade" -> parent.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, 0, -0.5f, 3, 3, 1),
                    PartPose.offset(0, s.length - 0.5f, 0));
            case "fin" -> parent.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, 0, -0.5f, 3, 3, 1),
                    PartPose.offset(0, s.length - 0.5f, 0));
            default -> {}
        }
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    /** Sways the chain: idle swish plus extra motion while walking. */
    public static void animate(ModelPart root, float ageInTicks, float walkSpeed) {
        ModelPart p = root;
        for (int i = 0; ; i++) {
            if (!p.hasChild("segment" + i)) break;
            ModelPart seg = p.getChild("segment" + i);
            seg.resetPose();
            seg.yRot += (float) Math.sin(ageInTicks * 0.12f - i * 0.6f) * (0.15f + walkSpeed * 0.4f);
            seg.zRot += (float) Math.sin(ageInTicks * 0.09f - i * 0.5f) * 0.05f;
            p = seg;
        }
    }
}
