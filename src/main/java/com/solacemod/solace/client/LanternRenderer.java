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
 * Solace's true form: a ball of blue plasma. A white-hot middle, lightning veins branching out through churning blue
 * cloud, and a ragged, uneven edge, never a neat circle. Layers of it turn at different speeds and stretch and squash,
 * so it keeps boiling. Always turned to face whoever looks at it.
 * The Hollow (the boss) is the same thing gone wrong: dim, red, and smothered in smoke.
 */
final class LanternRenderer {
    private static final ResourceLocation CORE = Solace.id("textures/entity/orb_core.png");
    private static final ResourceLocation HAZE = Solace.id("textures/entity/orb_haze.png");
    private static final ResourceLocation SMOKE = Solace.id("textures/entity/orb_smoke.png");
    private static final ResourceLocation[] PLASMA = {
            Solace.id("textures/entity/orb_plasma_0.png"), Solace.id("textures/entity/orb_plasma_1.png"), Solace.id("textures/entity/orb_plasma_2.png")};
    /** How high the centre of the light floats, in blocks. */
    private static final float FLOAT = 1.1F;
    /** Size (blocks) and turning speed of each plasma layer, outermost first. */
    private static final float[][] LAYERS = {{2.2F, 0.12F}, {1.75F, -0.21F}, {1.35F, 0.33F}};

    void render(SolaceEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Real time, not game time: it keeps moving even while the world is frozen around it.
        float t = (net.minecraft.Util.getMillis() % 3_600_000L) / 50F;
        boolean hollow = entity.isHollow();
        float speed = hollow || entity.isAgitated() ? 1.8F : 1.0F;
        float size = entity.getForm() == SolaceEntity.FORM_VAST ? 5.0F : 1.0F;
        int full = LightTexture.FULL_BRIGHT;
        float pulse = 1 + 0.05F * Mth.sin(t * 0.21F * speed) + 0.03F * Mth.sin(t * 0.77F);

        poseStack.pushPose();
        poseStack.translate(0, (FLOAT + Mth.sin(t * 0.04F) * 0.06F) * size, 0);
        poseStack.scale(size, size, size);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

        if (hollow) {
            // Smoke behind and round it (drawn normally, so it darkens what's behind).
            VertexConsumer smoke = buffer.getBuffer(RenderType.entityTranslucent(SMOKE));
            spin(poseStack, smoke, 2.4F, 2.4F, t * 0.25F, 0xE0100406, packedLight, -0.02F);
            spin(poseStack, smoke, 2.0F, 2.0F, -t * 0.4F + 70, 0xE0100406, packedLight, -0.01F);
        }
        // A soft glow round it all, then the plasma: everything glowing is added on top of what's behind it.
        spin(poseStack, buffer.getBuffer(RenderType.eyes(HAZE)), 2.6F * pulse, 2.6F * pulse, 0, hollow ? 0xFF701010 : 0xFFFFFFFF, full, 0);
        for (int i = 0; i < LAYERS.length; i++) {
            float w = LAYERS[i][0] * pulse * (1 + 0.1F * Mth.sin(t * 0.13F * speed + i * 2.1F));
            float h = LAYERS[i][0] * pulse * (1 + 0.1F * Mth.cos(t * 0.11F * speed + i * 1.3F));
            // A crackle now and then: one layer flares for a moment.
            int flash = (int) (t / 3) * 31 + i * 7;
            float bright = Math.floorMod(flash * 2654435, 23) == 0 ? 1.0F : 0.78F + 0.1F * Mth.sin(t * 0.9F + i);
            int color = hollow ? tint(bright, 1.0F, 0.25F, 0.2F) : tint(bright, 1, 1, 1);
            float angle = t * LAYERS[i][1] * speed * 6 + i * 120;
            if (i == 1) {
                // Drawn see-through as well (not only added), so it still has a body in daylight and its smoky edge darkens.
                spin(poseStack, buffer.getBuffer(RenderType.entityTranslucentEmissive(PLASMA[2 - i])), w, h, angle, color & 0x00FFFFFF | 0xA0000000, full, 0.003F * i);
            }
            spin(poseStack, buffer.getBuffer(RenderType.eyes(PLASMA[2 - i])), w, h, angle, color, full, 0.004F * i + 0.001F);
        }
        // The white-hot middle: solid enough to read by day, then a glow on top.
        float core = hollow ? 0.32F : 0.48F;
        spin(poseStack, buffer.getBuffer(RenderType.entityTranslucentEmissive(CORE)), core, core, 0, hollow ? 0xFF8A2020 : 0xFFFFFFFF, full, 0.02F);
        spin(poseStack, buffer.getBuffer(RenderType.eyes(CORE)), core * 1.5F * pulse, core * 1.5F * pulse, 0, hollow ? 0xFFC03030 : 0xFFFFFFFF, full, 0.025F);
        poseStack.popPose();

        if (!Minecraft.getInstance().isPaused()) particles(entity, size, hollow);
    }

    private static int tint(float bright, float r, float g, float b) {
        return 0xFF000000 | (int) (255 * bright * r) << 16 | (int) (255 * bright * g) << 8 | (int) (255 * bright * b);
    }

    private static void spin(PoseStack poseStack, VertexConsumer out, float w, float h, float angle, int argb, int light, float z) {
        poseStack.pushPose();
        poseStack.translate(0, 0, z);
        poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
        quad(poseStack, out, w / 2, h / 2, argb, light);
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
}
