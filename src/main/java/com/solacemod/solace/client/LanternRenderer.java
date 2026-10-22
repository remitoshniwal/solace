package com.solacemod.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.solacemod.solace.Solace;
import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * Solace's true form: a 3D ball of plasma made of small voxels (see VoxelOrb), with a dark outline, a solid
 * white-hot middle shining through glowing layers of blue, and arcs of lightning, wrapped in a soft, realistic plasma
 * glow that crackles round it. It churns (a loop of slightly different
 * shapes), slowly turns, and has a soft glow round it.
 * The Hollow (the boss) is the same thing in red, churning faster.
 */
final class LanternRenderer {
    /** Plain white: the voxels carry their own colours. */
    private static final ResourceLocation WHITE = Solace.id("textures/entity/outline.png");
    /** Soft plasma with lightning veins (painted by tools/shade_textures.py), glowing round the voxel body. */
    private static final ResourceLocation[] PLASMA = {Solace.id("textures/entity/orb_plasma_0.png"), Solace.id("textures/entity/orb_plasma_1.png")};
    /** How high the centre of the ball floats, in blocks (its hitbox, SolaceEntity, is sized to fit it). */
    static final float FLOAT = 0.82F;

    private static volatile VoxelOrb blue, red;

    LanternRenderer() {
        // Building the voxel shapes takes a moment, so it's done in the background when the game starts.
        if (blue == null) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                red = new VoxelOrb(new int[]{0xFFE6DC, 0xFF9C84, 0xE0402E, 0x9A1414, 0x500608}, 0xFF0E0202, 27);
                blue = new VoxelOrb(new int[]{0xFFFFFF, 0xBFEFFF, 0x6CB8FF, 0x2F6FE8, 0x173A9C}, 0xFF060A22, 21);
            });
        }
    }

    void render(SolaceEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (blue == null) return; // still being built (only for a moment after the game starts)
        if (Shaders.packOn() && Shaders.shadowPass()) return; // a ball of light casts no shadow (only shader packs draw entity shadows like this)
        // Real time, not game time: it keeps moving even while the world is frozen around it.
        float t = (net.minecraft.Util.getMillis() % 3_600_000L) / 50F;
        boolean hollow = entity.isHollow();
        float ticksPerFrame = hollow || entity.isAgitated() ? 1.5F : 3F;
        float scale = entity.getForm() == SolaceEntity.FORM_VAST ? 5.0F : 1.0F;
        int full = LightTexture.FULL_BRIGHT;
        VoxelOrb.Frame frame = (hollow ? red : blue).frames[(int) (t / ticksPerFrame) % VoxelOrb.FRAMES];

        poseStack.pushPose();
        poseStack.translate(0, (FLOAT + Mth.sin(t * 0.06F) * 0.05F) * scale, 0);
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(t * 0.8F));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.013F) * 12));

        // Each buffer is fetched right before it's used (fetching another one can close it).
        emit(poseStack, buffer.getBuffer(RenderType.entityTranslucentEmissive(WHITE)), frame.core(), full);
        emit(poseStack, buffer.getBuffer(RenderType.entityCutout(WHITE)), frame.outline(), full);
        // The blue layers glow: added on top of what's behind them, so the white middle shines through.
        float flicker = 0.9F + 0.1F * Mth.sin(t * 0.7F);
        emitGlow(poseStack, buffer.getBuffer(RenderType.eyes(WHITE)), frame.shell(), flicker);
        poseStack.popPose();

        // Round it, a softer, realistic glow: two layers of plasma with lightning veins, always facing you, turning
        // different ways and breathing, so its edge is never a neat circle.
        poseStack.pushPose();
        poseStack.translate(0, (FLOAT + Mth.sin(t * 0.06F) * 0.05F) * scale, 0);
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        float speed = hollow || entity.isAgitated() ? 1.8F : 1.0F;
        int tint = hollow ? 0xFFFF4030 : 0xFFFFFFFF;
        for (int i = 0; i < 2; i++) {
            float size = (i == 0 ? 2.5F : 2.0F) * (1 + 0.05F * Mth.sin(t * 0.21F * speed + i * 2));
            float w = size * (1 + 0.08F * Mth.sin(t * 0.13F + i)), h = size * (1 + 0.08F * Mth.cos(t * 0.11F + i * 1.7F));
            float bright = (i == 0 ? 0.7F : 0.85F) * (0.85F + 0.15F * Mth.sin(t * 0.9F + i * 3));
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees((i == 0 ? 1 : -1.6F) * t * 0.9F * speed + i * 120));
            billboard(poseStack, buffer.getBuffer(RenderType.eyes(PLASMA[i])), w, h, dim(tint, bright));
            poseStack.popPose();
        }
        poseStack.popPose();

        if (!Minecraft.getInstance().isPaused()) particles(entity, scale, hollow);
    }

    private static void emit(PoseStack poseStack, VertexConsumer out, List<VoxelOrb.Face> faces, int light) {
        PoseStack.Pose pose = poseStack.last();
        for (VoxelOrb.Face f : faces) {
            for (int k = 0; k < 4; k++) {
                out.addVertex(pose, f.pos()[k * 3], f.pos()[k * 3 + 1], f.pos()[k * 3 + 2]).setColor(f.argb()).setUv(0.5F, 0.5F)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, f.nx(), f.ny(), f.nz());
            }
        }
    }

    /** The glow layer ignores see-through and adds its colour, so dim the colours instead (by the face's alpha too). */
    private static void emitGlow(PoseStack poseStack, VertexConsumer out, List<VoxelOrb.Face> faces, float level) {
        PoseStack.Pose pose = poseStack.last();
        for (VoxelOrb.Face f : faces) {
            int c = f.argb();
            float strength = level * ((c >>> 24) / 255F);
            int r = (int) (((c >> 16) & 255) * strength), g = (int) (((c >> 8) & 255) * strength), b = (int) ((c & 255) * strength);
            int argb = 0xFF000000 | r << 16 | g << 8 | b;
            for (int k = 0; k < 4; k++) {
                out.addVertex(pose, f.pos()[k * 3], f.pos()[k * 3 + 1], f.pos()[k * 3 + 2]).setColor(argb).setUv(0.5F, 0.5F)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, f.nx(), f.ny(), f.nz());
            }
        }
    }

    /** A flat square facing the camera (w by h blocks). */
    private static void billboard(PoseStack poseStack, VertexConsumer out, float w, float h, int argb) {
        PoseStack.Pose pose = poseStack.last();
        float[][] corners = {{-w / 2, -h / 2, 0, 1}, {w / 2, -h / 2, 1, 1}, {w / 2, h / 2, 1, 0}, {-w / 2, h / 2, 0, 0}};
        for (float[] c : corners) {
            out.addVertex(pose, c[0], c[1], 0).setColor(argb).setUv(c[2], c[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
        }
    }

    private static int dim(int argb, float level) {
        int r = (int) (((argb >> 16) & 255) * level), g = (int) (((argb >> 8) & 255) * level), b = (int) ((argb & 255) * level);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Now and then a real spark jumps off it. */
    private static void particles(SolaceEntity e, float scale, boolean hollow) {
        RandomSource r = e.getRandom();
        if (r.nextInt(hollow ? 6 : 10) != 0) return;
        double y = e.getY() + FLOAT * scale;
        e.level().addParticle(hollow ? ParticleTypes.SMOKE : ParticleTypes.ELECTRIC_SPARK, e.getX(), y, e.getZ(),
                r.nextGaussian() * 0.12, r.nextGaussian() * 0.12, r.nextGaussian() * 0.12);
    }
}
