package com.solacemod.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solacemod.solace.Solace;
import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Solace's true form: a huge see-through teal mass of drifting tendrils with a bright core.
 * Inside the light is a tiny figure in a suit, curled up: the real Solace. The Hollow (the boss) is the same shape, dark, with a red core.
 */
final class LanternRenderer {
    private static final ResourceLocation MASS = Solace.id("textures/entity/lantern.png");
    private static final ResourceLocation MASS_HOLLOW = Solace.id("textures/entity/lantern_hollow.png");
    private static final ResourceLocation CORE = Solace.id("textures/entity/lantern_core.png");
    private static final ResourceLocation CORE_HOLLOW = Solace.id("textures/entity/lantern_core_hollow.png");
    private static final ResourceLocation FIGURE = Solace.id("textures/entity/solace.png");

    private final ModelPart mass;
    private final ModelPart core;
    private final ModelPart coreGlow;
    private final ModelPart massAura;
    private final ModelPart[] ferro = new ModelPart[SolaceModels.FERRO_BLOBS];
    private final ModelPart[][] spikes = new ModelPart[SolaceModels.FERRO_BLOBS][SolaceModels.SPIKES.length];
    private final ModelPart[] wax = new ModelPart[SolaceModels.WAX_BLOBS];
    private static final ResourceLocation FERRO = Solace.id("textures/entity/lantern_ferro.png");
    private static final ResourceLocation AURA = Solace.id("textures/entity/lantern_aura.png");
    private static final ResourceLocation AURA_HOLLOW = Solace.id("textures/entity/lantern_aura_hollow.png");
    /** The Lantern's size compared with the model as built (which is about 4 blocks tall). */
    private static final float SIZE = 0.55F;
    private final ModelPart[][] tendrils;
    private final SolaceModel<SolaceEntity> figure;

    LanternRenderer(EntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(SolaceModels.LANTERN);
        mass = root.getChild("mass");
        core = root.getChild("core");
        coreGlow = root.getChild("core_glow");
        massAura = root.getChild("mass_aura");
        for (int i = 0; i < ferro.length; i++) {
            ferro[i] = root.getChild("ferro" + i);
            for (int k = 0; k < SolaceModels.SPIKES.length; k++) spikes[i][k] = ferro[i].getChild("spike" + k);
        }
        for (int i = 0; i < wax.length; i++) wax[i] = root.getChild("wax" + i);
        tendrils = new ModelPart[SolaceModels.TENDRILS.length][SolaceModels.SEGMENTS];
        for (int i = 0; i < tendrils.length; i++) {
            ModelPart seg = root.getChild("t" + i);
            tendrils[i][0] = seg;
            for (int j = 1; j < SolaceModels.SEGMENTS; j++) {
                seg = seg.getChild("s");
                tendrils[i][j] = seg;
            }
        }
        figure = new SolaceModel<>(context.bakeLayer(SolaceModels.BODY));
        figure.curlUp();
    }

    void render(SolaceEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Real time, not game time: it keeps drifting even while the world is frozen around it.
        float t = (net.minecraft.Util.getMillis() % 3_600_000L) / 50F;
        boolean hollow = entity.isHollow();
        float speed = hollow || entity.isAgitated() ? 2.2F : 1.0F;
        animate(t * speed);
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)));
        float size = entity.getForm() == SolaceEntity.FORM_VAST ? 2.8F : SIZE;
        poseStack.scale(size, size, size);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        // Model space: y points down and the ground is at 24 pixels. Float above the ground, bobbing slowly.
        poseStack.translate(0.0F, -1.501F - 0.65F - Mth.sin(t * 0.04F) * 0.08F, 0.0F);

        // The tiny figure, curled up inside the light.
        poseStack.pushPose();
        poseStack.translate(0.0F, (-14.0F - 2.5F) / 16F, 0.0F);
        poseStack.scale(0.32F, 0.32F, 0.32F);
        poseStack.mulPose(Axis.YP.rotationDegrees(t * 0.4F));
        figure.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(FIGURE)), LightTexture.FULL_BRIGHT, overlay);
        figure.renderEyes(poseStack, buffer.getBuffer(RenderType.eyes(FIGURE)), LightTexture.FULL_BRIGHT, overlay, -1);
        poseStack.popPose();

        // Ferrofluid blobs inside, seen through the see-through mass.
        float agitation = hollow || entity.isAgitated() ? 1.8F : 1.0F;
        animateBlobs(t, agitation);
        var ferroBuffer = buffer.getBuffer(RenderType.entityCutoutNoCull(FERRO));
        for (ModelPart blob : ferro) blob.render(poseStack, ferroBuffer, LightTexture.FULL_BRIGHT, overlay);

        // The mass and tendrils: see-through glowing teal, or the Hollow's near-black.
        var consumer = buffer.getBuffer(hollow ? RenderType.entityTranslucent(MASS_HOLLOW) : RenderType.entityTranslucentEmissive(MASS));
        int light = hollow ? packedLight : LightTexture.FULL_BRIGHT;
        mass.yScale = 1.0F + Mth.sin(t * 0.05F) * 0.03F;
        mass.render(poseStack, consumer, light, overlay);
        for (ModelPart[] tendril : tendrils) tendril[0].render(poseStack, consumer, light, overlay);

        // Lava-lamp wax: soft glowing blobs, added on top so they shine through.
        var waxBuffer = buffer.getBuffer(RenderType.eyes(hollow ? CORE_HOLLOW : CORE));
        for (ModelPart w : wax) w.render(poseStack, waxBuffer, LightTexture.FULL_BRIGHT, overlay, SolaceRenderer.grey(0.7F));

        // The moving energy over the whole shape: cyan streaks flowing up (dim red for the Hollow).
        massAura.yScale = mass.yScale;
        massAura.render(poseStack, buffer.getBuffer(RenderType.energySwirl(hollow ? AURA_HOLLOW : AURA, (t * 0.006F) % 1.0F, (t * -0.02F) % 1.0F)),
                LightTexture.FULL_BRIGHT, overlay, SolaceRenderer.grey(1.0F));

        // The core, drawn last and added on top, so it shines through the mass. It breathes, with a soft glow around it.
        float beat = 1.0F + Mth.sin(t * (hollow ? 0.3F : 0.12F)) * 0.08F;
        core.xScale = core.yScale = core.zScale = beat;
        ResourceLocation coreTex = hollow ? CORE_HOLLOW : CORE;
        core.render(poseStack, buffer.getBuffer(RenderType.eyes(coreTex)), LightTexture.FULL_BRIGHT, overlay);
        coreGlow.xScale = coreGlow.yScale = coreGlow.zScale = beat;
        coreGlow.render(poseStack, buffer.getBuffer(RenderType.eyes(coreTex)), LightTexture.FULL_BRIGHT, overlay, SolaceRenderer.grey(0.22F));
        poseStack.popPose();
    }

    /**
     * Lava lamp and ferrofluid: each blob drifts up and down on its own slow cycle, squashing as it goes,
     * and the black blobs' spikes stretch and shrink like ferrofluid near a magnet.
     */
    private void animateBlobs(float t, float agitation) {
        for (int i = 0; i < ferro.length; i++) {
            float sp = (0.018F + i * 0.004F) * agitation;
            float phase = i * 1.9F;
            float rise = 0.5F + 0.5F * Mth.sin(t * sp + phase);
            ModelPart b = ferro[i];
            // Some drift out past the surface and crawl back in, like ferrofluid over glass.
            b.x = Mth.sin(t * sp * 0.7F + phase * 2) * (i % 3 == 0 ? 10.0F : 6.0F);
            b.z = Mth.cos(t * sp * 0.6F + phase) * (i % 3 == 0 ? 7.0F : 4.0F);
            b.y = -3.0F - rise * 30.0F;
            float size = 1.0F + (i % 3) * 0.45F;
            float squash = Mth.sin(t * sp * 2 + phase) * 0.18F;
            b.xScale = b.zScale = size * (1 + squash);
            b.yScale = size * (1 - squash);
            b.yRot = t * 0.01F * (i % 2 == 0 ? 1 : -1);
            for (int k = 0; k < spikes[i].length; k++) {
                spikes[i][k].yScale = 0.2F + 0.8F * Math.abs(Mth.sin(t * 0.09F * agitation + i * 1.3F + k * 0.7F));
                spikes[i][k].xScale = spikes[i][k].zScale = 1.3F - 0.5F * spikes[i][k].yScale; // thin when long, fat when short
            }
        }
        for (int i = 0; i < wax.length; i++) {
            float sp = (0.010F + i * 0.003F) * agitation;
            float phase = i * 2.6F + 1.0F;
            ModelPart w = wax[i];
            w.x = Mth.cos(t * sp * 0.8F + phase) * 5.0F;
            w.z = Mth.sin(t * sp * 0.5F + phase) * 3.0F;
            w.y = -6.0F - (0.5F + 0.5F * Mth.sin(t * sp + phase)) * 24.0F;
            float swell = 1.1F + 0.4F * Mth.sin(t * sp * 3 + phase);
            w.xScale = w.zScale = swell * 1.2F;
            w.yScale = swell * (0.8F + 0.4F * Mth.cos(t * sp * 2 + phase));
        }
    }

    /** Every segment sways a little more than the one before it, so the tendrils drift like they're underwater. */
    private void animate(float t) {
        for (int i = 0; i < tendrils.length; i++) {
            float[] base = SolaceModels.TENDRILS[i];
            for (int j = 0; j < tendrils[i].length; j++) {
                float amp = 0.10F + j * 0.035F;
                float phase = t * 0.05F + i * 1.7F + j * 0.55F;
                ModelPart seg = tendrils[i][j];
                seg.xRot = (j == 0 ? base[3] : 0) + Mth.sin(phase) * amp;
                seg.zRot = (j == 0 ? base[4] : 0) + Mth.cos(phase * 0.8F) * amp;
            }
        }
    }
}
