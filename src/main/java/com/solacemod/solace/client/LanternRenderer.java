package com.solacemod.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solacemod.solace.Solace;
import com.solacemod.solace.client.mesh.Figure;
import com.solacemod.solace.client.mesh.LanternShape;
import com.solacemod.solace.client.mesh.Mat4;
import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Solace's true form (see client/mesh/LanternShape): a glowing lava-lamp body with spiky black ferrofluid moving
 * inside, drifting wax, a bright core with a tiny curled-up figure in it, smooth tendrils, and cyan light flowing over it.
 * The Hollow (the boss) is the same shape, dark, with red light.
 */
final class LanternRenderer {
    private static final ResourceLocation AURA = Solace.id("textures/entity/lantern_aura.png");
    private static final ResourceLocation AURA_HOLLOW = Solace.id("textures/entity/lantern_aura_hollow.png");
    /** How high the body floats above the ground, in blocks. */
    private static final float FLOAT = 0.55F;

    private final LanternShape shape = new LanternShape();
    private final Figure tiny = new Figure(0);

    LanternRenderer() {
        tiny.curl();
    }

    void render(SolaceEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Real time, not game time: it keeps moving even while the world is frozen around it.
        float t = (net.minecraft.Util.getMillis() % 3_600_000L) / 50F;
        boolean hollow = entity.isHollow();
        shape.update(t, hollow || entity.isAgitated() ? 1.8F : 1.0F, hollow);
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);
        int full = LightTexture.FULL_BRIGHT;
        Mat4 m = new Mat4();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot)));
        float size = entity.getForm() == SolaceEntity.FORM_VAST ? 5.0F : 1.0F;
        poseStack.scale(size, size, size);
        poseStack.translate(0, FLOAT + Mth.sin(t * 0.04F) * 0.05F, 0);

        // Inside first: the ferrofluid, and the tiny figure curled up in the core.
        shape.ferro.emit(m, new MeshDraw(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(FigureRenderer.CLOTH)), full, overlay), 0xFFFFFFFF, null);
        poseStack.pushPose();
        poseStack.translate(0, LanternShape.CORE_Y - 0.12F, 0);
        poseStack.scale(0.15F, 0.15F, 0.15F);
        poseStack.mulPose(Axis.YP.rotationDegrees(t * 0.4F));
        FigureRenderer.draw(tiny, poseStack, buffer, full, overlay, 0, 0, 1.0F, false, t);
        poseStack.popPose();

        // The see-through body and tendrils: glowing cyan, or the Hollow's near-black.
        RenderType glass = hollow ? RenderType.entityTranslucent(FigureRenderer.CLOTH) : RenderType.entityTranslucentEmissive(FigureRenderer.CLOTH);
        int light = hollow ? packedLight : full;
        shape.tendrils.emit(m, new MeshDraw(poseStack, buffer.getBuffer(glass), light, overlay), 0xFFFFFFFF, null);
        shape.glass.emit(m, new MeshDraw(poseStack, buffer.getBuffer(glass), light, overlay), 0xFFFFFFFF, null);

        // Glowing things, added on top so they shine through: the wax, the core and its glow, the light flowing over the surface.
        MeshDraw glow = new MeshDraw(poseStack, buffer.getBuffer(RenderType.eyes(FigureRenderer.CLOTH)), full, OverlayTexture.NO_OVERLAY);
        shape.wax.emit(m, glow, FigureRenderer.grey(0.7F), null);
        shape.core.emit(m, glow, 0xFFFFFFFF, null);
        shape.coreGlow.emit(m, glow, FigureRenderer.grey(0.25F), null);
        shape.aura.emit(m, new MeshDraw(poseStack, buffer.getBuffer(RenderType.energySwirl(hollow ? AURA_HOLLOW : AURA, (t * 0.006F) % 1F, (t * -0.02F) % 1F)),
                full, OverlayTexture.NO_OVERLAY), FigureRenderer.grey(0.9F), null);
        poseStack.popPose();
    }
}
