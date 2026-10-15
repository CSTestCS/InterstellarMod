package com.cstestcs.interstellar.core.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JavaModelInterpreterTest {
    /** Trimmed but otherwise verbatim Blockbench 4.x "Modded Entity" export. */
    private static final String BLOCKBENCH = """
            // Made with Blockbench 4.10.4
            // Exported for Minecraft version 1.17 or later with Mojang mappings
            // Paste this class into your mod and generate all required imports
            public class fox_head<T extends Entity> extends EntityModel<T> {
            	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "fox_head"), "main");
            	private final ModelPart head;

            	public fox_head(ModelPart root) {
            		this.head = root.getChild("head");
            	}

            	public static LayerDefinition createBodyLayer() {
            		MeshDefinition meshdefinition = new MeshDefinition();
            		PartDefinition partdefinition = meshdefinition.getRoot();

            		PartDefinition head = partdefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
            		.texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.5F)), PartPose.offset(0.0F, 0.0F, 0.0F));

            		PartDefinition ear_r1 = head.addOrReplaceChild("ear_r1", CubeListBuilder.create().texOffs(24, 16).mirror().addBox(-1.0F, -3.0F, -0.5F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(3.0F, -8.0F, 0.0F, 0.0F, 0.0F, (float) Math.PI / 8F));

            		PartDefinition snout = head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(0, 16).addBox(-2.0F, -2.0F, -3.0F, 4, 2, 3, CubeDeformation.NONE), PartPose.rotation(-0.1745F, 0.0F, 0.0F));

            		return LayerDefinition.create(meshdefinition, 64, 64);
            	}

            	@Override
            	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            	}
            }
            """;

    @Test
    void interpretsBlockbenchExport() throws Exception {
        ModelDef def = JavaModelInterpreter.interpret(BLOCKBENCH);
        assertEquals(64, def.textureWidth);
        ModelDef.PartDef head = def.root.children.get("head");
        assertEquals(2, head.cubes.size());
        assertEquals(0.5f, head.cubes.get(1).growX(), 1e-6);
        assertEquals(32, head.cubes.get(1).u());
        ModelDef.PartDef ear = head.children.get("ear_r1");
        assertTrue(ear.cubes.get(0).mirror());
        assertEquals(Math.PI / 8, ear.zRot, 1e-5);
        assertEquals(3f, ear.x, 1e-6);
        ModelDef.PartDef snout = head.children.get("snout");
        assertEquals(-0.1745f, snout.xRot, 1e-6);
        assertEquals(4, def.root.cubeCount());
    }

    @Test
    void writerRoundTrips() throws Exception {
        ModelDef def = JavaModelInterpreter.interpret(BLOCKBENCH);
        ModelDef again = JavaModelInterpreter.interpret(JavaModelWriter.write("x", def));
        assertEquals(def.root.cubeCount(), again.root.cubeCount());
        assertEquals(def.root.find("ear_r1").zRot, again.root.find("ear_r1").zRot, 1e-4);
    }

    @Test
    void reportsGarbage() {
        assertThrows(JavaModelInterpreter.ModelParseException.class, () -> JavaModelInterpreter.interpret("class X {}"));
    }
}
