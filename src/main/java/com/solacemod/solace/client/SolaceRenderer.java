package com.solacemod.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solacemod.solace.Solace;
import com.solacemod.solace.entity.Presence;
import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Draws Solace as a player-shaped figure with glowing eyes. Its look changes as the story darkens. */
public class SolaceRenderer extends HumanoidMobRenderer<SolaceEntity, PlayerModel<SolaceEntity>> {
    private float frozenBob;
    private static final ResourceLocation NORMAL = Solace.id("textures/entity/solace.png");
    private static final ResourceLocation UNEASY = Solace.id("textures/entity/solace_uneasy.png");
    private static final ResourceLocation HOLLOW = Solace.id("textures/entity/solace_hollow.png");

    public SolaceRenderer(EntityRendererProvider.Context context) {
        super(context, new SolaceModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        addLayer(new Eyes(this));
    }

    @Override
    public ResourceLocation getTextureLocation(SolaceEntity entity) {
        return switch (entity.getForm()) {
            case SolaceEntity.FORM_HOLLOW -> HOLLOW;
            case SolaceEntity.FORM_UNEASY -> UNEASY;
            default -> NORMAL;
        };
    }

    /** Solace hovers a little above the ground and slowly bobs, like it isn't quite part of the world. */
    @Override
    public void render(SolaceEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        float t = entity.tickCount + partialTick;
        int presence = entity.getPresence();
        if (presence == Presence.FLICKER) {
            // Gone for a few frames, back, gone again. Sometimes it's drawn slightly to one side.
            int frame = (int) (t / 2);
            int noise = (frame * 73856093) ^ (entity.getId() * 19349663);
            if (Math.floorMod(noise, 3) == 0) return;
            if (Math.floorMod(noise, 7) == 0) {
                poseStack.pushPose();
                poseStack.translate(Math.floorMod(noise, 2) == 0 ? 0.4 : -0.4, 0, 0);
                renderBody(entity, entityYaw, partialTick, poseStack, buffer, packedLight, t);
                poseStack.popPose();
                return;
            }
        }
        if (presence == Presence.HEAD_TURN) {
            // Point the head at the camera, however far round that is.
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double dx = cam.x - Mth.lerp(partialTick, entity.xo, entity.getX());
            double dz = cam.z - Mth.lerp(partialTick, entity.zo, entity.getZ());
            float toCamera = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
            ((SolaceModel) getModel()).forcedHeadYaw = Mth.wrapDegrees(toCamera - Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot));
        } else {
            ((SolaceModel) getModel()).forcedHeadYaw = Float.NaN;
        }
        renderBody(entity, entityYaw, partialTick, poseStack, buffer, packedLight, t);
    }

    private void renderBody(SolaceEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                            net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight, float t) {
        poseStack.pushPose();
        float bob = entity.getPresence() == Presence.FREEZE ? frozenBob : (float) (Math.sin(t * 0.06) * 0.06);
        frozenBob = bob;
        poseStack.translate(0.0, entity.isHollow() ? 0.3 : 0.12 + bob, 0.0);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    @Override
    protected void scale(SolaceEntity entity, PoseStack poseStack, float partialTick) {
        float s = entity.isHollow() ? 1.35F : 0.9375F; // same size as a player model
        poseStack.scale(s, s, s);
    }

    /** Eyes that glow in the dark, like an Enderman's. */
    private static final class Eyes extends EyesLayer<SolaceEntity, PlayerModel<SolaceEntity>> {
        private static final RenderType NORMAL_EYES = RenderType.eyes(Solace.id("textures/entity/solace_eyes.png"));
        private static final RenderType HOLLOW_EYES = RenderType.eyes(Solace.id("textures/entity/solace_hollow_eyes.png"));
        Eyes(SolaceRenderer parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return NORMAL_EYES;
        }

        @Override
        public void render(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight, SolaceEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            RenderType type = entity.getForm() == SolaceEntity.FORM_NORMAL ? NORMAL_EYES : HOLLOW_EYES;
            var consumer = buffer.getBuffer(type);
            this.getParentModel().renderToBuffer(poseStack, consumer, 0xF00000, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        }
    }
}
