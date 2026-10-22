package com.solacemod.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solacemod.solace.Solace;
import com.solacemod.solace.client.model.BbModel;
import com.solacemod.solace.client.model.Mat4;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.io.InputStream;

/**
 * Solace's body: the Blockbench model in assets/solace/models/entity/solace.bbmodel (made with tools/models).
 * A headless samurai with no eyes: a tall laced neck guard stands round an empty hollow. The normal form is black
 * armour with red cords, a tattered cloak and claws, outlined in white; the negative is the same armour in white with
 * black cords, shoulder flames and sword blades for hands, outlined in black. Both are the same
 * model: parts named n_... show only in the normal form, x_... only in the negative, and the texture sheet swaps.
 * Shared by Solace itself and its copy in the distance ("two places").
 */
final class SolaceBody {
    static final ResourceLocation NORMAL = Solace.id("textures/entity/solace_shade.png");
    private static final ResourceLocation NEGATIVE = Solace.id("textures/entity/solace_shade_negative.png");
    private static final ResourceLocation NEGATIVE_GLOW = Solace.id("textures/entity/solace_shade_negative_glow.png");
    /** The uneasy form (later chapters): the normal sheet with white cracks, and the cracks alone, glowing. */
    private static final ResourceLocation UNEASY = Solace.id("textures/entity/solace_shade_uneasy.png");
    private static final ResourceLocation UNEASY_GLOW = Solace.id("textures/entity/solace_shade_uneasy_glow.png");
    /** Plain white, coloured per vertex: a white outline round the normal form, a black one round the negative. */
    private static final ResourceLocation OUTLINE = Solace.id("textures/entity/outline.png");
    /** Outline thickness, in model pixels (16 to a block). */
    private static final float OUTLINE_WIDTH = 0.3F;
    private static final String MODEL = "/assets/solace/models/entity/solace.bbmodel";
    private static final String[] FLAMES = {"x_flame_r", "x_flame_l", "x_flame_r2", "x_flame_l2"};

    private static BbModel model;

    private SolaceBody() {
    }

    /** Loaded once, the first time it's drawn. If the file is broken, Solace is simply invisible (and the log says why). */
    static BbModel model() {
        if (model == null) {
            try (InputStream in = SolaceBody.class.getResourceAsStream(MODEL)) {
                model = BbModel.load(in);
            } catch (Exception e) {
                Solace.LOGGER.error("Could not load Solace's model {}", MODEL, e);
                model = BbModel.load(new java.io.ByteArrayInputStream("{\"elements\":[],\"outliner\":[]}".getBytes()));
            }
        }
        return model;
    }

    /**
     * Moves the bones for this frame.
     *
     * @param swing  walk cycle position
     * @param amount how fast it's walking (0..1)
     * @param t      age in ticks
     * @param twist  how far its upper body is turned, in degrees (it has no head to turn)
     * @param attack 0..1 while striking
     * @param seed   a number per Solace, so two of them never move in step
     * @param stage  how far the story has gone (0..4): its shoulder spikes grow
     */
    static void pose(float swing, float amount, float t, float twist, float attack, int seed, int stage) {
        BbModel m = model();
        m.reset();
        float w = swing * 0.6662F;
        float idle = Mth.sin(t * 0.05F + seed) * 3;
        // Hunched further forward while walking; a slow breath otherwise. All angles in degrees.
        m.bone("body").rot(-amount * 7 + Mth.sin(t * 0.06F) * 1.5F, twist, Mth.sin(t * 0.031F + seed) * 1.2F);
        m.bone("leg_r").rot(Mth.cos(w) * 38 * amount, 0, 0);
        m.bone("leg_l").rot(-Mth.cos(w) * 38 * amount, 0, 0);
        m.bone("shin_r").rot(-Math.max(0, Mth.sin(w)) * 40 * amount, 0, 0);
        m.bone("shin_l").rot(-Math.max(0, -Mth.sin(w)) * 40 * amount, 0, 0);
        float strike = Mth.sin(attack * Mth.PI) * 105;
        m.bone("arm_r").rot(-Mth.cos(w) * 26 * amount + idle + strike, 0, Mth.sin(t * 0.043F + seed) * 2.5F);
        m.bone("arm_l").rot(Mth.cos(w) * 26 * amount - idle, 0, -Mth.sin(t * 0.047F + seed) * 2.5F);
        // Forearms and hands never quite settle: a slow, uneven twitch.
        m.bone("forearm_r").rot(Mth.sin(t * 0.071F + seed) * 5 + strike * 0.2F, 0, 0);
        m.bone("forearm_l").rot(Mth.sin(t * 0.067F + seed + 2) * 5, 0, 0);
        m.bone("hand_r").rot(Mth.sin(t * 0.13F + seed) * 6, 0, 0);
        m.bone("hand_l").rot(Mth.sin(t * 0.12F + seed + 1) * 6, 0, 0);
        float spikes = 0.75F + 0.09F * Math.max(0, Math.min(4, stage));
        m.bone("spikes_r").scale(spikes);
        m.bone("spikes_l").scale(spikes);
        for (int i = 0; i < FLAMES.length; i++) {
            BbModel.Bone f = m.bone(FLAMES[i]);
            float lick = Mth.sin(t * 0.45F + i * 2.1F) + 0.5F * Mth.sin(t * 1.13F + i);
            f.sy = 1 + 0.14F * lick;
            f.sx = f.sz = 1 - 0.06F * lick;
            f.rz = Mth.sin(t * 0.3F + i) * 6;
        }
    }

    /**
     * Draws the posed model.
     *
     * @param negativeMask which areas show the negative (BbModel.BODY, RIGHT_ARM, LEFT_ARM); 0 = none
     * @param glow         brightness of the white outline round the normal form (it stutters like a bad bulb)
     * @param push         how hard its cloak is pushed back (walking speed)
     * @param uneasy       the later chapters: white cracks through it, and a dirty grey outline instead of a white one
     */
    static void draw(PoseStack poseStack, MultiBufferSource buffer, int light, int overlay, int negativeMask, float glow, float t, float push,
                     boolean uneasy) {
        BbModel m = model();
        Mat4 base = BbModel.base();
        int full = LightTexture.FULL_BRIGHT;
        // The outline is drawn with back faces hidden (entityCutout), which the inside-out trick needs.
        // (Each buffer is fetched right before it's used: fetching another one can close it.)
        if (negativeMask != BbModel.ALL) {
            m.draw(base, new MeshDraw(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(uneasy ? UNEASY : NORMAL)), light, overlay), negativeMask, false, t, push, 0xFFFFFFFF);
            if (uneasy) {
                // The cracks glow faintly, breathing slowly, so they show even in the dark.
                m.draw(base, new MeshDraw(poseStack, buffer.getBuffer(RenderType.eyes(UNEASY_GLOW)), full, OverlayTexture.NO_OVERLAY),
                        negativeMask, false, t, push, grey(0.5F + 0.25F * Mth.sin(t * 0.09F)));
            }
            // With a shader pack on, "full brightness" gets coloured like torchlight (orange or peach), so the white outline is drawn
            // as a glow instead, which shader packs leave white. Both ways hide back faces, as the inside-out trick needs.
            RenderType outline = Shaders.packOn() ? RenderType.eyes(OUTLINE) : RenderType.entityCutout(OUTLINE);
            m.drawOutline(base, new MeshDraw(poseStack, buffer.getBuffer(outline), full, OverlayTexture.NO_OVERLAY), negativeMask, false, OUTLINE_WIDTH,
                    uneasy ? dirty(Math.min(0.75F, 0.3F + glow * 0.5F)) : grey(Math.min(1, 0.55F + glow * 0.6F)));
        }
        if (negativeMask != 0) {
            m.draw(base, new MeshDraw(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(NEGATIVE)), full, overlay), negativeMask, true, t, push, 0xFFFFFFFF);
            m.draw(base, new MeshDraw(poseStack, buffer.getBuffer(RenderType.eyes(NEGATIVE_GLOW)), full, OverlayTexture.NO_OVERLAY),
                    negativeMask, true, t, push, grey(0.8F + 0.2F * Mth.sin(t * 0.37F)));
            m.drawOutline(base, new MeshDraw(poseStack, buffer.getBuffer(RenderType.entityCutout(OUTLINE)), full, OverlayTexture.NO_OVERLAY), negativeMask, true,
                    OUTLINE_WIDTH, 0xFF000000);
        }
    }

    /** A grimy, yellowed grey, like the glass of an old bulb. */
    static int dirty(float level) {
        int r = Math.max(0, Math.min(255, (int) (level * 255)));
        return 0xFF000000 | r << 16 | (int) (r * 0.93F) << 8 | (int) (r * 0.8F);
    }

    static int grey(float level) {
        int v = Math.max(0, Math.min(255, (int) (level * 255)));
        return 0xFF000000 | v << 16 | v << 8 | v;
    }
}
