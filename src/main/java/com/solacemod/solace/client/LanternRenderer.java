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
 * Solace's true form: a 3D ball of plasma made of chunky voxels (see VoxelOrb), with a dark outline, a solid
 * white-hot middle shining through glowing layers of blue, and arcs of lightning. It churns (a loop of slightly different
 * shapes), slowly turns, and has a soft glow round it.
 * The Hollow (the boss) is the same thing in red, churning faster.
 */
final class LanternRenderer {
    /** Plain white: the voxels carry their own colours. */
    private static final ResourceLocation WHITE = Solace.id("textures/entity/outline.png");
    /** How high the centre of the ball floats, in blocks (its hitbox, SolaceEntity, is sized to fit it). */
    static final float FLOAT = 0.82F;

    private static VoxelOrb blue, red;

    void render(SolaceEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (blue == null) {
            blue = new VoxelOrb(new int[]{0xFFFFFF, 0xBFEFFF, 0x6CB8FF, 0x2F6FE8, 0x173A9C}, 0xFF060A22, 21);
            red = new VoxelOrb(new int[]{0xFFE6DC, 0xFF9C84, 0xE0402E, 0x9A1414, 0x500608}, 0xFF0E0202, 27);
        }
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
        // And a faint halo just outside.
        poseStack.scale(1.16F, 1.16F, 1.16F);
        emitGlow(poseStack, buffer.getBuffer(RenderType.eyes(WHITE)), frame.shell(), 0.22F + 0.06F * Mth.sin(t * 0.15F));
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

    /** Now and then a real spark jumps off it. */
    private static void particles(SolaceEntity e, float scale, boolean hollow) {
        RandomSource r = e.getRandom();
        if (r.nextInt(hollow ? 6 : 10) != 0) return;
        double y = e.getY() + FLOAT * scale;
        e.level().addParticle(hollow ? ParticleTypes.SMOKE : ParticleTypes.ELECTRIC_SPARK, e.getX(), y, e.getZ(),
                r.nextGaussian() * 0.12, r.nextGaussian() * 0.12, r.nextGaussian() * 0.12);
    }
}
