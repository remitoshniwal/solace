package com.solacemod.solace.client;

import com.solacemod.solace.entity.Presence;
import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The suited body (see SolaceModels.createBody), with the animation tricks from the presence system:
 * gliding (legs stop moving), freeze-frame (the pose stops like a paused video), the head turn,
 * and the "negative" flashes (some parts are hidden here and drawn inverted by the renderer's negative layer).
 */
public class SolaceModel extends HumanoidModel<SolaceEntity> {
    /** Body parts a negative flash can cover (bit flags). */
    public static final int HEAD = 1, BODY = 2, RIGHT_ARM = 4, LEFT_ARM = 8, LEGS = 16, ALL = 31;

    private final ModelPart jacketHem;
    /** Set by the renderer each frame: how far the head should turn (degrees, past any normal neck limit), or NaN. */
    float forcedHeadYaw = Float.NaN;
    /** Set by the renderer each frame: which parts are showing their negative side right now. */
    int negativeMask;
    private float swing, amount, age, headYaw, pitch;

    public SolaceModel(ModelPart root) {
        super(root);
        this.jacketHem = body.getChild("jacket_hem");
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
        // A composed man: arms hang closer and swing less than a player's.
        rightArm.xRot *= 0.6F;
        leftArm.xRot *= 0.6F;
        // The jacket hem swings back as the legs move, and settles when he stops.
        jacketHem.xRot = Math.abs(Mth.sin(limbSwing * 0.6662F)) * limbSwingAmount * 0.35F + Mth.sin(ageInTicks * 0.05F) * 0.01F;
        if (p == Presence.HEAD_TURN && !Float.isNaN(forcedHeadYaw)) {
            head.yRot = forcedHeadYaw * ((float) Math.PI / 180F);
            head.zRot = 0.35F; // a tilt no person would hold
            hat.copyFrom(head);
        }
        showParts(~negativeMask);
    }

    /** Shows only the parts in the mask (used to draw the normal and the negative parts separately). */
    void showParts(int mask) {
        head.visible = hat.visible = (mask & HEAD) != 0;
        body.visible = (mask & BODY) != 0;
        rightArm.visible = (mask & RIGHT_ARM) != 0;
        leftArm.visible = (mask & LEFT_ARM) != 0;
        rightLeg.visible = leftLeg.visible = (mask & LEGS) != 0;
    }
}
