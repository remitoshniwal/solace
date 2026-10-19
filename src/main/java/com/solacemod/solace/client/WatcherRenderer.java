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

/** Draws the Watcher: a faded, see-through, player-shaped figure. Or, for "two places", a solid copy of Solace. */
public class WatcherRenderer extends MobRenderer<WatcherEntity, EntityModel<WatcherEntity>> {
    private static final ResourceLocation TEXTURE = Solace.id("textures/entity/watcher.png");
    /** The "two places" copy of Solace uses its body, glowing eyes and all. */
    private final com.solacemod.solace.client.mesh.Figure copy = new com.solacemod.solace.client.mesh.Figure(0);

    public WatcherRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true), 0.0F);
    }

    @Override
    public void render(WatcherEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        if (!entity.isMimic()) {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }
        float t = entity.tickCount + partialTick;
        float bodyYaw = net.minecraft.util.Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        copy.pose(0, 0, t, 0, 0, 0, entity.getId());
        poseStack.pushPose();
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-bodyYaw));
        poseStack.translate(0.0, 0.12 + Math.sin(t * 0.06) * 0.06, 0.0);
        FigureRenderer.draw(copy, poseStack, buffer, packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0, 0, 1.0F, false, t);
        poseStack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(WatcherEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(WatcherEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Nullable
    @Override
    protected RenderType getRenderType(WatcherEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucent(getTextureLocation(entity));
    }

    @Override
    protected boolean shouldShowName(WatcherEntity entity) {
        return false;
    }
}
