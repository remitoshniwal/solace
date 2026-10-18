package com.solacemod.solace.client;

import com.solacemod.solace.Solace;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** The shapes of Solace's two custom forms, built in code (no model files needed). */
public final class SolaceModels {
    public static final ModelLayerLocation LUMP = new ModelLayerLocation(Solace.id("lump"), "main");
    public static final ModelLayerLocation LANTERN = new ModelLayerLocation(Solace.id("lantern"), "main");

    /**
     * Where each Lantern tendril grows from and which way it points (x, y, z in pixels; xRot, zRot in radians).
     * A tendril with no rotation hangs straight down. Change these to reshape the Lantern.
     */
    static final float[][] TENDRILS = {
            // hanging from the bottom of the mass, like a ghost's trailing edge
            {-6, 2, -3, -0.15F, 0.25F}, {-2, 2, -4, -0.2F, 0.05F}, {2, 2, -4, -0.2F, -0.05F}, {6, 2, -3, -0.15F, -0.25F},
            {-6, 2, 3, 0.15F, 0.25F}, {-2, 2, 4, 0.2F, 0.05F}, {2, 2, 4, 0.2F, -0.05F}, {6, 2, 3, 0.15F, -0.25F},
            // reaching out from the shoulders, like arms
            {9, -20, 0, 0, -2.3F}, {-9, -20, 0, 0, 2.3F}, {9, -11, 1, 0.2F, -1.7F}, {-9, -11, 1, 0.2F, 1.7F},
            // drifting up off its back
            {4, -22, 6, 2.4F, -0.2F}, {-4, -22, 6, 2.4F, 0.2F}, {3, -8, 6, 2.0F, -0.1F}, {-3, -8, 6, 2.0F, 0.1F},
            // rising off the top of its head, like hair underwater
            {0, -36, 0, 3.0F, 0}, {4, -34, 2, 2.8F, -0.3F}, {-4, -34, -2, 3.3F, 0.3F},
    };
    static final int SEGMENTS = 7;
    static final float SEGMENT_LENGTH = 4.5F;

    private SolaceModels() {
    }

    /** One small cube. The lump layer draws it many times, stretched and squashed. */
    public static LayerDefinition createLump() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("lump", CubeListBuilder.create().texOffs(0, 0).addBox(-1, -1, -1, 2, 2, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
    }

    /** The Lantern: a tall see-through mass, a core, and tendrils made of jointed segments. About 4 blocks tall. */
    public static LayerDefinition createLantern() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("mass", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-9, -24, -6, 18, 26, 12)     // body
                .texOffs(0, 0).addBox(-6, -36, -6, 12, 12, 12)     // hood
                .texOffs(0, 0).addBox(-7, -30, -5, 14, 8, 10),     // between the two, so it isn't boxy
                PartPose.ZERO);
        root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -4, -4, 8, 8, 8), PartPose.offset(0, -14, 0));
        for (int i = 0; i < TENDRILS.length; i++) {
            float[] t = TENDRILS[i];
            PartDefinition seg = root.addOrReplaceChild("t" + i, segment(0), PartPose.offsetAndRotation(t[0], t[1], t[2], t[3], 0, t[4]));
            for (int j = 1; j < SEGMENTS; j++) {
                seg = seg.addOrReplaceChild("s", segment(j), PartPose.offset(0, SEGMENT_LENGTH, 0));
            }
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** One piece of a tendril, thinner toward the tip. */
    private static CubeListBuilder segment(int index) {
        float w = Math.max(1.0F, 4.0F - index * 0.5F);
        return CubeListBuilder.create().texOffs(0, 0).addBox(-w / 2, 0, -w / 2, w, SEGMENT_LENGTH + 0.5F, w);
    }
}
