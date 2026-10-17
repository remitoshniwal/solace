package com.solacemod.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solacemod.solace.Solace;
import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** Draws Solace as a player-shaped figure with glowing eyes. Its look changes as the story darkens. */
public class SolaceRenderer extends HumanoidMobRenderer<SolaceEntity, PlayerModel<SolaceEntity>> {
    private static final ResourceLocation NORMAL = Solace.id("textures/entity/solace.png");
    private static final ResourceLocation UNEASY = Solace.id("textures/entity/solace_uneasy.png");
    private static final ResourceLocation HOLLOW = Solace.id("textures/entity/solace_hollow.png");

    public SolaceRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
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
