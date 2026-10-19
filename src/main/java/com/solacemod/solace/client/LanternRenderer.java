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

/**
 * Solace's true form: a burst of light. A white-hot core, cyan light breaking outward from it in uneven rays, a soft
 * haze, sparks flying off and fading, and dark smoke curling round it all. Always turned to face whoever looks at it.
 * The Hollow (the boss) is the same burst gone wrong: dim, red, and smothered in smoke.
 */
final class LanternRenderer {
    private static final ResourceLocation CORE = Solace.id("textures/entity/orb_core.png");
    private static final ResourceLocation CORONA = Solace.id("textures/entity/orb_corona.png");
    private static final ResourceLocation HAZE = Solace.id("textures/entity/orb_haze.png");
    private static final ResourceLocation STREAK = Solace.id("textures/entity/orb_streak.png");
    private static final ResourceLocation SMOKE = Solace.id("textures/entity/orb_smoke.png");
    /** How high the centre of the light floats, in blocks. */
    private static final float FLOAT = 1.1F;
    private static final int SPARKS = 16;

    void render(SolaceEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Real time, not game time: it keeps moving even while the world is frozen around it.
        float t = (net.minecraft.Util.getMillis() % 3_600_000L) / 50F;
        boolean hollow = entity.isHollow();
        float speed = hollow || entity.isAgitated() ? 1.8F : 1.0F;
        float size = entity.getForm() == SolaceEntity.FORM_VAST ? 5.0F : 1.0F;
        int full = LightTexture.FULL_BRIGHT;
        float pulse = 1 + 0.06F * Mth.sin(t * 0.21F * speed) + 0.03F * Mth.sin(t * 0.77F);

        poseStack.pushPose();
        poseStack.translate(0, (FLOAT + Mth.sin(t * 0.04F) * 0.06F) * size, 0);
        poseStack.scale(size, size, size);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

        // Smoke behind and round it (drawn normally, so it darkens what's behind).
        VertexConsumer smoke = buffer.getBuffer(RenderType.entityTranslucent(SMOKE));
        int smokeColor = hollow ? 0xE0100406 : 0x90000000;
        spin(poseStack, smoke, hollow ? 2.2F : 1.6F, t * 0.25F, smokeColor, packedLight, -0.02F);
        spin(poseStack, smoke, hollow ? 1.9F : 1.3F, -t * 0.4F + 70, smokeColor, packedLight, -0.01F);

        // The light: everything below adds to what's behind it, like real glow.
        VertexConsumer haze = buffer.getBuffer(RenderType.eyes(HAZE));
        spin(poseStack, haze, 1.9F * pulse, 0, hollow ? 0xFF701010 : 0xFFFFFFFF, full, 0);
        VertexConsumer corona = buffer.getBuffer(RenderType.eyes(CORONA));
        int c1 = hollow ? 0xFFB02020 : 0xFFFFFFFF, c2 = hollow ? 0xFF701818 : 0xFFA0C8D0;
        spin(poseStack, corona, 1.25F * pulse, t * 0.6F * speed, c1, full, 0.005F);
        spin(poseStack, corona, 1.0F * pulse, -t * 0.9F * speed + 40, c2, full, 0.01F);
        sparks(poseStack, buffer.getBuffer(RenderType.eyes(STREAK)), t * speed, hollow, full);
        // The core: solid enough to read by day, then a hot glow on top.
        float core = hollow ? 0.3F : 0.42F;
        spin(poseStack, buffer.getBuffer(RenderType.entityTranslucentEmissive(CORE)), core, 0, hollow ? 0xFF8A2020 : 0xFFFFFFFF, full, 0.02F);
        spin(poseStack, buffer.getBuffer(RenderType.eyes(CORE)), core * 1.4F * pulse, 0, hollow ? 0xFFC03030 : 0xFFFFFFFF, full, 0.025F);
        poseStack.popPose();

        if (!Minecraft.getInstance().isPaused()) particles(entity, size, hollow);
    }

    /** Sparks flying out from the core and fading, each on its own line, coming round again and again. */
    private static void sparks(PoseStack poseStack, VertexConsumer out, float t, boolean hollow, int light) {
        for (int i = 0; i < SPARKS; i++) {
            float phase = frac(t * 0.011F + hash(i * 7 + 1));
            float angle = hash(i * 13 + 5) * 360 + Mth.sin(t * 0.01F + i) * 8;
            float dist = 0.16F + phase * 0.85F;
            float len = 0.05F + 0.16F * (1 - phase);
            float fade = (1 - phase) * (1 - phase);
            int v = (int) (255 * fade);
            int color = hollow ? 0xFF000000 | v << 16 | (v / 5) << 8 | (v / 5) : 0xFF000000 | (int) (v * 0.85F) << 16 | v << 8 | v;
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
            poseStack.translate(0, dist, 0.015F);
            quad(poseStack, out, 0.035F, len, color, light);
            poseStack.popPose();
        }
    }

    private static void spin(PoseStack poseStack, VertexConsumer out, float size, float angle, int argb, int light, float z) {
        poseStack.pushPose();
        poseStack.translate(0, 0, z);
        poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
        quad(poseStack, out, size / 2, size / 2, argb, light);
        poseStack.popPose();
    }

    /** A flat square facing the camera (half-width w, half-height h). */
    private static void quad(PoseStack poseStack, VertexConsumer out, float w, float h, int argb, int light) {
        PoseStack.Pose pose = poseStack.last();
        float[][] corners = {{-w, -h, 0, 1}, {w, -h, 1, 1}, {w, h, 1, 0}, {-w, h, 0, 0}};
        for (float[] c : corners) {
            out.addVertex(pose, c[0], c[1], 0).setColor(argb).setUv(c[2], c[3]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
        }
    }

    /** Now and then a real spark jumps off it. */
    private static void particles(SolaceEntity e, float size, boolean hollow) {
        RandomSource r = e.getRandom();
        if (r.nextInt(hollow ? 6 : 10) != 0) return;
        double y = e.getY() + FLOAT * size;
        e.level().addParticle(hollow ? ParticleTypes.SMOKE : ParticleTypes.ELECTRIC_SPARK, e.getX(), y, e.getZ(),
                r.nextGaussian() * 0.12, r.nextGaussian() * 0.12, r.nextGaussian() * 0.12);
    }

    private static float hash(int n) {
        int h = n * 0x9E3779B1;
        h ^= h >>> 15;
        h *= 0x85EBCA77;
        h ^= h >>> 13;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float frac(float x) {
        return x - Mth.floor(x);
    }
}
