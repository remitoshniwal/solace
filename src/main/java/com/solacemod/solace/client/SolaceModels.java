package com.solacemod.solace.client;

import com.solacemod.solace.Solace;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** The shapes of Solace's two custom forms, built in code (no model files needed). */
public final class SolaceModels {
    public static final ModelLayerLocation BODY = new ModelLayerLocation(Solace.id("body"), "main");
    /** A slightly bigger copy of the body, for the flowing energy around him in his negative form. */
    public static final ModelLayerLocation BODY_AURA = new ModelLayerLocation(Solace.id("body"), "aura");
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
    static final int SEGMENTS = 5;
    static final int FERRO_BLOBS = 10;
    static final int WAX_BLOBS = 5;
    /** Which way each ferrofluid spike points (xRot, zRot). With no rotation a spike points straight up. */
    static final float[][] SPIKES = {{0, 0}, {3.14F, 0}, {1.57F, 0}, {-1.57F, 0}, {0, 1.57F}, {0, -1.57F},
            {0.8F, 0.8F}, {-0.8F, -0.8F}, {0.8F, -0.8F}, {-0.8F, 0.8F}, {2.3F, 0.8F}, {-2.3F, -0.8F}};
    static final float SEGMENT_LENGTH = 3.6F;

    private SolaceModels() {
    }

    /**
     * Solace's own body (not a player shape): a tall, thin figure in a long tailored coat, with broad stiff shoulders,
     * long jointed arms with long fingers, and a high collar with nothing inside it. No head: just two glowing eyes
     * floating where a face should be. In his negative form a blank, glowing white head appears.
     * Measurements are in pixels (16 = one block); the ground is at y = 24 and y points down.
     * The texture layout (textures/entity/solace.png) is painted by tools/make_textures.py to match the texOffs below.
     */
    public static LayerDefinition createBody() {
        return body(0.0F);
    }

    public static LayerDefinition createAura() {
        return body(0.45F);
    }

    /** @param grow how much bigger than normal each box is (0 = the body itself, more = the glowing shell around it) */
    private static LayerDefinition body(float grow) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation g = new CubeDeformation(grow);
        CubeDeformation thin = new CubeDeformation(grow - 0.2F);

        // Torso: a narrow waist under a broad chest, square padded shoulders, and a high empty collar.
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(16, 0).addBox(-3.0F, 1.0F, -1.5F, 6, 7, 3, g)          // waist
                .texOffs(34, 0).addBox(-4.0F, -6.0F, -2.0F, 8, 7, 4, g)         // chest
                .texOffs(16, 10).addBox(-6.5F, -6.5F, -2.5F, 3, 2, 5, g)        // right shoulder pad
                .texOffs(16, 10).mirror().addBox(3.5F, -6.5F, -2.5F, 3, 2, 5, g).mirror(false)
                .texOffs(16, 17).addBox(-2.5F, -9.0F, -3.0F, 5, 3, 1, g)        // collar, front
                .texOffs(16, 21).addBox(-2.5F, -9.0F, 2.0F, 5, 3, 1, g)         // collar, back
                .texOffs(0, 52).addBox(-3.5F, -9.0F, -3.0F, 1, 3, 6, g)        // collar, sides
                .texOffs(0, 52).mirror().addBox(2.5F, -9.0F, -3.0F, 1, 3, 6, g).mirror(false)
                .texOffs(40, 11).addBox(-2.5F, -6.2F, -2.0F, 5, 1, 4, g),       // the dark inside of the collar
                PartPose.ZERO);
        // The long coat hangs from the waist in panels, so it can swing as he walks.
        body.addOrReplaceChild("coat_back", CubeListBuilder.create().texOffs(0, 24).addBox(-3.5F, 0.0F, 0.0F, 7, 9, 1, g),
                PartPose.offset(0.0F, 7.5F, 1.6F));
        body.addOrReplaceChild("coat_front_right", CubeListBuilder.create().texOffs(16, 25).addBox(-3.6F, 0.0F, -1.0F, 4, 9, 1, g),
                PartPose.offset(0.0F, 7.5F, -1.6F));
        body.addOrReplaceChild("coat_front_left", CubeListBuilder.create().texOffs(16, 25).mirror().addBox(-0.4F, 0.0F, -1.0F, 4, 9, 1, g),
                PartPose.offset(0.0F, 7.5F, -1.6F));
        body.addOrReplaceChild("coat_side_right", CubeListBuilder.create().texOffs(26, 25).addBox(-1.0F, 0.0F, -1.5F, 1, 9, 3, g),
                PartPose.offset(-3.4F, 7.5F, 0.0F));
        body.addOrReplaceChild("coat_side_left", CubeListBuilder.create().texOffs(26, 25).mirror().addBox(0.0F, 0.0F, -1.5F, 1, 9, 3, g),
                PartPose.offset(3.4F, 7.5F, 0.0F));

        // Where the face should be: two eyes, floating in the empty collar. And the head that only exists in the negative.
        root.addOrReplaceChild("eyes", CubeListBuilder.create()
                .texOffs(28, 37).addBox(-2.3F, -3.0F, -1.5F, 1, 1, 1, new CubeDeformation(grow * 1.6F - 0.05F))
                .texOffs(28, 37).addBox(1.3F, -3.0F, -1.5F, 1, 1, 1, new CubeDeformation(grow * 1.6F - 0.05F)),
                PartPose.offset(0.0F, -7.5F, 0.0F));
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 37).addBox(-3.5F, -8.0F, -3.5F, 7, 8, 7, g),
                PartPose.offset(0.0F, -7.5F, 0.0F));

        // Long arms: upper arm, forearm (so the elbows bend), a narrow hand and three long fingers.
        for (int side : new int[]{-1, 1}) {
            String name = side < 0 ? "right" : "left";
            boolean m = side > 0;
            PartDefinition arm = root.addOrReplaceChild(name + "_arm", CubeListBuilder.create().texOffs(34, 25).mirror(m)
                    .addBox(-1.0F, -0.5F, -1.0F, 2, 8, 2, new CubeDeformation(grow + 0.25F)), PartPose.offset(5.2F * side, -5.0F, 0.0F));
            PartDefinition fore = arm.addOrReplaceChild(name + "_forearm", CubeListBuilder.create().texOffs(42, 25).mirror(m)
                    .addBox(-1.0F, 0.0F, -1.0F, 2, 7, 2, new CubeDeformation(grow + 0.1F)), PartPose.offset(0.0F, 7.5F, 0.0F));
            PartDefinition hand = fore.addOrReplaceChild(name + "_hand", CubeListBuilder.create().texOffs(50, 25).mirror(m)
                    .addBox(-1.0F, 0.0F, -1.0F, 2, 2, 2, thin), PartPose.offset(0.0F, 7.0F, 0.0F));
            for (int f = 0; f < 3; f++) {
                hand.addOrReplaceChild(name + "_finger" + f, CubeListBuilder.create().texOffs(58, 25)
                        .addBox(-0.5F, 0.0F, -0.5F, 1, 3, 1, new CubeDeformation(grow - 0.2F)), PartPose.offset(-0.6F + f * 0.6F, 1.8F, -0.4F + (f == 1 ? -0.2F : 0)));
            }
        }

        // Long thin legs and long pointed shoes.
        for (int side : new int[]{-1, 1}) {
            String name = side < 0 ? "right" : "left";
            PartDefinition leg = root.addOrReplaceChild(name + "_leg", CubeListBuilder.create().texOffs(0, 0).mirror(side > 0)
                    .addBox(-1.5F, 0.0F, -1.5F, 3, 15, 3, g), PartPose.offset(1.9F * side, 8.0F, 0.0F));
            leg.addOrReplaceChild(name + "_shoe", CubeListBuilder.create().texOffs(0, 18).mirror(side > 0)
                    .addBox(-1.5F, 15.0F, -3.5F, 3, 1, 5, g), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** The Lantern: a tall see-through mass, a core, and tendrils made of jointed segments. About 4 blocks tall. */
    public static LayerDefinition createLantern() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Layers of different sizes, widest in the middle, so it reads as a rounded mass rather than a box.
        root.addOrReplaceChild("mass", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6, -2, -4.5F, 12, 5, 9)      // the trailing bottom
                .texOffs(0, 0).addBox(-8, -16, -6, 16, 14, 12)      // belly, widest
                .texOffs(0, 0).addBox(-9.5F, -12, -4, 19, 6, 8)     // bulge at the sides
                .texOffs(0, 0).addBox(-7, -26, -5, 14, 10, 10)      // chest
                .texOffs(0, 0).addBox(-5, -34, -4.5F, 10, 8, 9)     // hood
                .texOffs(0, 0).addBox(-3, -38, -3, 6, 4, 6),        // crown
                PartPose.ZERO);
        // The same mass, a little bigger, for the moving cyan energy around it.
        root.addOrReplaceChild("mass_aura", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6, -2, -4.5F, 12, 5, 9, new CubeDeformation(2.0F))
                .texOffs(0, 0).addBox(-8, -16, -6, 16, 14, 12, new CubeDeformation(2.0F))
                .texOffs(0, 0).addBox(-7, -26, -5, 14, 10, 10, new CubeDeformation(2.0F))
                .texOffs(0, 0).addBox(-5, -34, -4.5F, 10, 8, 9, new CubeDeformation(2.0F)),
                PartPose.ZERO);
        // Ferrofluid: glossy black blobs covered in spikes, rising and sinking through the body like a lava lamp.
        for (int i = 0; i < FERRO_BLOBS; i++) {
            PartDefinition blob = root.addOrReplaceChild("ferro" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-2, -2, -2, 4, 4, 4), PartPose.ZERO);
            for (int k = 0; k < SPIKES.length; k++) {
                blob.addOrReplaceChild("spike" + k, CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-0.5F, -5.0F, -0.5F, 1, 5, 1, new CubeDeformation(-0.05F)), PartPose.rotation(SPIKES[k][0], 0, SPIKES[k][1]));
            }
        }
        // Lava-lamp wax: soft glowing cyan blobs drifting slowly up and down.
        for (int i = 0; i < WAX_BLOBS; i++) {
            root.addOrReplaceChild("wax" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-2.5F, -2.5F, -2.5F, 5, 5, 5), PartPose.ZERO);
        }
        root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -4, -4, 8, 8, 8), PartPose.offset(0, -14, 0));
        root.addOrReplaceChild("core_glow", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -4, -4, 8, 8, 8, new CubeDeformation(3.0F)),
                PartPose.offset(0, -14, 0));
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
        float w = Math.max(0.8F, 2.4F - index * 0.4F);
        return CubeListBuilder.create().texOffs(0, 0).addBox(-w / 2, 0, -w / 2, w, SEGMENT_LENGTH + 0.5F, w);
    }
}
