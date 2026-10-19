package com.solacemod.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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

/**
 * Solace's true form, drawn as Minecraft-style pixel art: a ragged ball of blue plasma with a dark outline, a
 * white-hot middle and crackling arcs of lightning. It plays like a flipbook (the way lava and portals animate), with
 * a blocky glow behind it, and always faces whoever looks at it.
 * The Hollow (the boss) is the same thing in red, crackling faster.
 */
final class LanternRenderer {
    private static final ResourceLocation FRAMES = Solace.id("textures/entity/orb_frames.png");
    private static final ResourceLocation FRAMES_HOLLOW = Solace.id("textures/entity/orb_frames_hollow.png");
    private static final ResourceLocation GLOW = Solace.id("textures/entity/orb_glow.png");
    private static final ResourceLocation GLOW_HOLLOW = Solace.id("textures/entity/orb_glow_hollow.png");
    /** How many pictures are in the flipbook strip. */
    private static final int FRAME_COUNT = 12;
    /** How high the centre of the light floats, in blocks. */
    private static final float FLOAT = 1.1F;
    /** Width of the ball, in blocks: its 32 pixels come out at Minecraft's usual 16 pixels per block. */
    private static final float SIZE = 2.0F;

    void render(SolaceEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Real time, not game time: it keeps moving even while the world is frozen around it.
        float t = (net.minecraft.Util.getMillis() % 3_600_000L) / 50F;
        boolean hollow = entity.isHollow();
        float ticksPerFrame = hollow || entity.isAgitated() ? 1.5F : 3F;
        float scale = entity.getForm() == SolaceEntity.FORM_VAST ? 5.0F : 1.0F;
        int full = LightTexture.FULL_BRIGHT;
        int frame = (int) (t / ticksPerFrame) % FRAME_COUNT;

        poseStack.pushPose();
        // Bobs up and down in whole pixels, like an item on the ground.
        float bob = Math.round(Mth.sin(t * 0.06F) * 1.5F) / 16F;
        poseStack.translate(0, (FLOAT + bob) * scale, 0);
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

        // A blocky halo behind it, added to what's behind so it lights up the dark; it breathes slowly.
        float halo = SIZE * (1.35F + 0.08F * Mth.sin(t * 0.1F));
        quad(poseStack, buffer.getBuffer(RenderType.eyes(hollow ? GLOW_HOLLOW : GLOW)), halo, 0, 1, 0xFFFFFFFF, full, -0.01F);
        // The ball itself: solid pixels with hard edges, one frame of the strip at a time.
        float u0 = frame / (float) FRAME_COUNT, u1 = (frame + 1) / (float) FRAME_COUNT;
        quad(poseStack, buffer.getBuffer(RenderType.entityTranslucentEmissive(hollow ? FRAMES_HOLLOW : FRAMES)), SIZE, u0, u1, 0xFFFFFFFF, full, 0);
        poseStack.popPose();

        if (!Minecraft.getInstance().isPaused()) particles(entity, scale, hollow);
    }

    /** A flat square facing the camera, showing the texture from u0 to u1 across. */
    private static void quad(PoseStack poseStack, VertexConsumer out, float size, float u0, float u1, int argb, int light, float z) {
        PoseStack.Pose pose = poseStack.last();
        float h = size / 2;
        float[][] corners = {{-h, -h, u0, 1}, {h, -h, u1, 1}, {h, h, u1, 0}, {-h, h, u0, 0}};
        for (float[] c : corners) {
            out.addVertex(pose, c[0], c[1], z).setColor(argb).setUv(c[2], c[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
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
