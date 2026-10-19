package com.solacemod.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solacemod.solace.Solace;
import com.solacemod.solace.entity.WatcherEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/** Draws the Watcher: a faded, see-through, player-shaped figure. */
public class WatcherRenderer extends MobRenderer<WatcherEntity, EntityModel<WatcherEntity>> {
    private static final ResourceLocation TEXTURE = Solace.id("textures/entity/watcher.png");
    private static final ResourceLocation SOLACE = Solace.id("textures/entity/solace.png");
    private final EntityModel<WatcherEntity> slim;
    /** The "two places" copy of Solace uses his body, glowing eyes and all. */
    private final SolaceModel<WatcherEntity> suited;

    public WatcherRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.0F);
        slim = getModel();
        suited = new SolaceModel<>(context.bakeLayer(SolaceModels.BODY));
        addLayer(new net.minecraft.client.renderer.entity.layers.RenderLayer<>(this) {
            @Override
            public void render(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffer, int light, WatcherEntity entity,
                               float limbSwing, float limbSwingAmount, float partialTick, float age, float yaw, float pitch) {
                if (entity.isMimic()) suited.renderEyes(pose, buffer.getBuffer(RenderType.eyes(SOLACE)), 0xF000F0,
                        net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, -1);
            }
        });
    }

    /** A mimic is drawn as a solid copy of Solace, hovering like it does. */
    @Override
    public void render(WatcherEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        model = entity.isMimic() ? suited : slim;
        poseStack.pushPose();
        if (entity.isMimic()) poseStack.translate(0.0, 0.12 + Math.sin((entity.tickCount + partialTick) * 0.06) * 0.06, 0.0);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(WatcherEntity entity) {
        return entity.isMimic() ? SOLACE : TEXTURE;
    }

    @Override
    protected void scale(WatcherEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Nullable
    @Override
    protected RenderType getRenderType(WatcherEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return entity.isMimic() ? RenderType.entityCutoutNoCull(SOLACE) : RenderType.entityTranslucent(getTextureLocation(entity));
    }

    @Override
    protected boolean shouldShowName(WatcherEntity entity) {
        return false;
    }
}
