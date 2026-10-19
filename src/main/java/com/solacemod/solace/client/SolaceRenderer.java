package com.solacemod.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solacemod.solace.Solace;
import com.solacemod.solace.entity.Presence;
import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws Solace: a calm man in a tailored suit. As the story goes on, dark veins creep up from his collar and cuffs,
 * and now and then he flickers into his negative (inverted) side, all of him or just his face or a hand.
 * His true form, the Lantern, is drawn by LanternRenderer.
 */
public class SolaceRenderer extends HumanoidMobRenderer<SolaceEntity, SolaceModel> {
    private float frozenBob;
    private final LanternRenderer lantern;
    private static final ResourceLocation NORMAL = Solace.id("textures/entity/solace.png");
    private static final ResourceLocation UNEASY = Solace.id("textures/entity/solace_uneasy.png");
    static final ResourceLocation NEGATIVE = Solace.id("textures/entity/solace_negative.png");
    private static final ResourceLocation[] VEINS = {
            Solace.id("textures/entity/solace_veins_1.png"), Solace.id("textures/entity/solace_veins_2.png"),
            Solace.id("textures/entity/solace_veins_3.png"), Solace.id("textures/entity/solace_veins_4.png")};

    /** How far the veins have spread (0 = none, 4 = to his eyes), by corruption level (= story chapter). */
    private static final int[] VEIN_STAGE = {0, 0, 0, 1, 2, 3, 3, 4, 4};
    /** Chance of a negative flash in each half second, by corruption level. Chapter 2 about once a minute, chapter 8 every few seconds. */
    private static final double[] FLASH_CHANCE = {0, 0, 0.008, 0.015, 0.025, 0.04, 0.055, 0.08, 0.1};

    public SolaceRenderer(EntityRendererProvider.Context context) {
        super(context, new SolaceModel(context.bakeLayer(SolaceModels.BODY)), 0.5F);
        // Order matters: veins and eyes are drawn on the normal parts, then the negative parts are drawn last.
        addLayer(new Veins(this));
        addLayer(new Eyes(this));
        addLayer(new Negative(this));
        lantern = new LanternRenderer(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SolaceEntity entity) {
        return entity.getForm() == SolaceEntity.FORM_NORMAL ? NORMAL : UNEASY;
    }

    /**
     * Which parts are showing their negative side right now. A flash lasts half a second, and inside it he
     * flickers between the two, like a bad signal. Worked out from the time, so it costs nothing to sync.
     */
    static int negativeMask(SolaceEntity entity, float t) {
        int level = Math.max(0, Math.min(8, entity.getCorruption()));
        double chance = FLASH_CHANCE[level] * (entity.isAgitated() ? 3 : 1);
        if (entity.getPresence() == Presence.FLICKER) chance = Math.max(chance, 0.6);
        if (chance <= 0) return 0;
        int window = (int) (t / 10);
        int h = window * 0x9E3779B1 ^ entity.getId() * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        if ((h & 0xFFFF) / 65536.0 >= chance) return 0;
        if (((int) (t / 2) & 1) == 1) return 0; // flicker back to normal
        return switch (Math.floorMod(h >>> 16, 6)) {
            case 0, 1 -> SolaceModel.ALL;
            case 2 -> SolaceModel.HEAD;
            case 3 -> SolaceModel.RIGHT_ARM;
            case 4 -> SolaceModel.LEFT_ARM;
            default -> SolaceModel.HEAD | SolaceModel.RIGHT_ARM;
        };
    }

    /** Solace hovers a little above the ground and slowly bobs, like it isn't quite part of the world. */
    @Override
    public void render(SolaceEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        float t = entity.tickCount + partialTick;
        if (entity.isLanternForm()) {
            lantern.render(entity, partialTick, poseStack, buffer, packedLight);
            return;
        }
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
        poseStack.translate(0.0, 0.12 + bob, 0.0);
        getModel().negativeMask = negativeMask(entity, t);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    @Override
    protected void scale(SolaceEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F); // same size as a player model
    }

    /** Dark veins creeping up from his collar and cuffs (only on skin, so they show on his neck, face and hands). */
    private static final class Veins extends RenderLayer<SolaceEntity, SolaceModel> {
        Veins(RenderLayerParent<SolaceEntity, SolaceModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, SolaceEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            int stage = VEIN_STAGE[Math.max(0, Math.min(8, entity.getCorruption()))];
            if (stage == 0 || entity.isInvisible()) return;
            getParentModel().renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityTranslucent(VEINS[stage - 1])), packedLight,
                    LivingEntityRenderer.getOverlayCoords(entity, 0));
        }
    }

    /** Later in the story, his eyes catch the light in the dark when nothing else does. */
    private static final class Eyes extends RenderLayer<SolaceEntity, SolaceModel> {
        private static final RenderType EYES = RenderType.eyes(Solace.id("textures/entity/solace_eyes.png"));

        Eyes(RenderLayerParent<SolaceEntity, SolaceModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, SolaceEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (entity.getForm() == SolaceEntity.FORM_NORMAL || entity.isInvisible()) return;
            getParentModel().renderToBuffer(poseStack, buffer.getBuffer(EYES), 0xF00000, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        }
    }

    /** The parts that are showing his negative side this frame, drawn inverted and faintly lit so you notice them in the dark. */
    private static final class Negative extends RenderLayer<SolaceEntity, SolaceModel> {
        Negative(RenderLayerParent<SolaceEntity, SolaceModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, SolaceEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            SolaceModel model = getParentModel();
            if (model.negativeMask == 0) return;
            model.showParts(model.negativeMask);
            int light = Math.max(packedLight & 0xFFFF, 0xA0) | (packedLight & 0xFFFF0000); // never fully dark
            model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(NEGATIVE)), light, LivingEntityRenderer.getOverlayCoords(entity, 0));
            model.showParts(SolaceModel.ALL);
        }
    }
}
