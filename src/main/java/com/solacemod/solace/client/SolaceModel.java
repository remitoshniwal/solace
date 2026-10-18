package com.solacemod.solace.client;

import com.solacemod.solace.entity.Presence;
import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The player-shaped body, with the animation tricks from the presence system:
 * gliding (legs stop moving), freeze-frame (the pose stops like a paused video), and the head turn.
 */
public class SolaceModel extends PlayerModel<SolaceEntity> {
    /** Set by the renderer each frame: how far the head should turn (degrees, past any normal neck limit), or NaN. */
    float forcedHeadYaw = Float.NaN;
    private float swing, amount, age, headYaw, pitch;

    public SolaceModel(ModelPart root) {
        super(root, false);
    }

    @Override
    public void setupAnim(SolaceEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        int p = entity.getPresence();
        if (p == Presence.FREEZE) {
            // Keep the last pose exactly, even though it's still moving.
            limbSwing = swing;
            limbSwingAmount = amount;
            ageInTicks = age;
            netHeadYaw = headYaw;
            headPitch = pitch;
        } else {
            if (p == Presence.GLIDE) limbSwingAmount = 0;
            swing = limbSwing;
            amount = limbSwingAmount;
            age = ageInTicks;
            headYaw = netHeadYaw;
            pitch = headPitch;
        }
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        if (p == Presence.HEAD_TURN && !Float.isNaN(forcedHeadYaw)) {
            head.yRot = forcedHeadYaw * ((float) Math.PI / 180F);
            head.zRot = 0.35F; // a tilt no person would hold
            hat.copyFrom(head);
        }
    }
}
