package com.solacemod.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solacemod.solace.Solace;
import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
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
 * Inside the light is a tiny pale figure curled up: the real Solace. The Hollow (the boss) is the same shape, dark, with a red core.
 */
final class LanternRenderer {
    private static final ResourceLocation MASS = Solace.id("textures/entity/lantern.png");
    private static final ResourceLocation MASS_HOLLOW = Solace.id("textures/entity/lantern_hollow.png");
    private static final ResourceLocation CORE = Solace.id("textures/entity/lantern_core.png");
    private static final ResourceLocation CORE_HOLLOW = Solace.id("textures/entity/lantern_core_hollow.png");
    private static final ResourceLocation FIGURE = Solace.id("textures/entity/solace.png");

    private final ModelPart mass;
    private final ModelPart core;
    private final ModelPart[][] tendrils;
    private final PlayerModel<SolaceEntity> figure;

    LanternRenderer(EntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(SolaceModels.LANTERN);
        mass = root.getChild("mass");
        core = root.getChild("core");
        tendrils = new ModelPart[SolaceModels.TENDRILS.length][SolaceModels.SEGMENTS];
        for (int i = 0; i < tendrils.length; i++) {
            ModelPart seg = root.getChild("t" + i);
            tendrils[i][0] = seg;
            for (int j = 1; j < SolaceModels.SEGMENTS; j++) {
                seg = seg.getChild("s");
                tendrils[i][j] = seg;
            }
        }
        figure = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        curlUp(figure);
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
        if (entity.getForm() == SolaceEntity.FORM_VAST) poseStack.scale(3.0F, 3.0F, 3.0F);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        // Model space: y points down and the ground is at 24 pixels. Float above the ground, bobbing slowly.
        poseStack.translate(0.0F, -1.501F - 0.65F - Mth.sin(t * 0.04F) * 0.08F, 0.0F);

        // The tiny figure, curled up inside the light.
        poseStack.pushPose();
        poseStack.translate(0.0F, (-14.0F - 2.5F) / 16F, 0.0F);
        poseStack.scale(0.2F, 0.2F, 0.2F);
        poseStack.mulPose(Axis.YP.rotationDegrees(t * 0.4F));
        figure.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(FIGURE)), LightTexture.FULL_BRIGHT, overlay);
        poseStack.popPose();

        // The core glows at full brightness and breathes.
        float beat = 1.0F + Mth.sin(t * (hollow ? 0.3F : 0.12F)) * 0.08F;
        core.xScale = core.yScale = core.zScale = beat;
        core.render(poseStack, buffer.getBuffer(RenderType.entityTranslucentEmissive(hollow ? CORE_HOLLOW : CORE)), LightTexture.FULL_BRIGHT, overlay);

        // The mass and tendrils: glowing teal, or the Hollow's near-black.
        var consumer = buffer.getBuffer(hollow ? RenderType.entityTranslucent(MASS_HOLLOW) : RenderType.entityTranslucentEmissive(MASS));
        int light = hollow ? packedLight : LightTexture.FULL_BRIGHT;
        mass.yScale = 1.0F + Mth.sin(t * 0.05F) * 0.03F;
        mass.render(poseStack, consumer, light, overlay);
        for (ModelPart[] tendril : tendrils) tendril[0].render(poseStack, consumer, light, overlay);
        poseStack.popPose();
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

    /** Knees up, head down, arms around its legs. */
    private static void curlUp(PlayerModel<SolaceEntity> m) {
        m.hat.visible = m.jacket.visible = m.leftSleeve.visible = m.rightSleeve.visible = m.leftPants.visible = m.rightPants.visible = false;
        m.head.xRot = 0.9F;
        m.body.xRot = 0.6F;
        m.rightArm.xRot = -1.3F;
        m.leftArm.xRot = -1.3F;
        m.rightArm.zRot = 0.3F;
        m.leftArm.zRot = -0.3F;
        m.rightLeg.xRot = -1.9F;
        m.leftLeg.xRot = -1.9F;
        m.rightLeg.y = 9.0F;
        m.leftLeg.y = 9.0F;
        m.rightLeg.z = -3.0F;
        m.leftLeg.z = -3.0F;
    }
}
