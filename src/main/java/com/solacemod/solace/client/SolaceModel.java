package com.solacemod.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solacemod.solace.entity.Presence;
import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Solace's body (see SolaceModels.createBody): a tall headless figure in a long coat, eyes floating in the empty collar.
 * Animations: a slow, composed walk with the coat swinging, elbows that bend, fingers that twitch now and then,
 * eyes that follow you, and the presence tricks (gliding, freeze-frame, the head turn).
 * Parts can be shown or hidden separately, so the renderer can draw some of them in their negative (inverted) form.
 */
public class SolaceModel<T extends LivingEntity> extends EntityModel<T> {
    /** Body parts a negative flash can cover (bit flags). HEAD = the white head appears instead of the eyes. */
    public static final int HEAD = 1, BODY = 2, RIGHT_ARM = 4, LEFT_ARM = 8, LEGS = 16, ALL = 31;

    final ModelPart body, eyes, head, rightArm, leftArm, rightLeg, leftLeg;
    private final ModelPart rightForearm, leftForearm, rightHand, leftHand;
    private final ModelPart coatBack, coatFrontRight, coatFrontLeft, coatSideRight, coatSideLeft;
    private final ModelPart[] fingers;
    private final List<ModelPart> roots;

    /** Set by the renderer each frame: how far the head should turn (degrees, past any normal neck limit), or NaN. */
    float forcedHeadYaw = Float.NaN;
    /** Set by the renderer each frame: which parts are showing their negative side right now. */
    int negativeMask;
    private float swing, amount, age, headYaw, pitch;

    public SolaceModel(ModelPart root) {
        body = root.getChild("body");
        eyes = root.getChild("eyes");
        head = root.getChild("head");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        rightForearm = rightArm.getChild("right_forearm");
        leftForearm = leftArm.getChild("left_forearm");
        rightHand = rightForearm.getChild("right_hand");
        leftHand = leftForearm.getChild("left_hand");
        coatBack = body.getChild("coat_back");
        coatFrontRight = body.getChild("coat_front_right");
        coatFrontLeft = body.getChild("coat_front_left");
        coatSideRight = body.getChild("coat_side_right");
        coatSideLeft = body.getChild("coat_side_left");
        fingers = new ModelPart[6];
        for (int f = 0; f < 3; f++) {
            fingers[f] = rightHand.getChild("right_finger" + f);
            fingers[3 + f] = leftHand.getChild("left_finger" + f);
        }
        roots = List.of(body, eyes, head, rightArm, leftArm, rightLeg, leftLeg);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        int p = entity instanceof SolaceEntity s ? s.getPresence() : Presence.WALK;
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
        float walk = Mth.cos(limbSwing * 0.6662F);
        float breathe = Mth.sin(ageInTicks * 0.06F);

        // Legs: a measured stride.
        rightLeg.xRot = walk * 0.9F * limbSwingAmount;
        leftLeg.xRot = -walk * 0.9F * limbSwingAmount;
        rightLeg.yRot = leftLeg.yRot = 0;

        // Arms hang long and still, elbows a little bent; they barely swing.
        rightArm.xRot = -walk * 0.35F * limbSwingAmount + breathe * 0.02F;
        leftArm.xRot = walk * 0.35F * limbSwingAmount - breathe * 0.02F;
        rightArm.zRot = 0.06F;
        leftArm.zRot = -0.06F;
        rightForearm.xRot = -0.18F - Math.max(0, -walk) * 0.25F * limbSwingAmount;
        leftForearm.xRot = -0.18F - Math.max(0, walk) * 0.25F * limbSwingAmount;
        rightHand.xRot = leftHand.xRot = -0.05F;
        if (attackTime > 0) {
            // The one-tap: a single fast reach.
            float a = Mth.sin(attackTime * (float) Math.PI);
            rightArm.xRot = -2.2F * a;
            rightForearm.xRot = -0.1F;
        }

        // Fingers rest slightly curled, and now and then one twitches.
        int seed = entity.getId() * 31;
        for (int i = 0; i < fingers.length; i++) {
            float twitch = Math.floorMod((int) (ageInTicks / 3) * 7919 + seed + i * 104729, 97) == 0 ? -0.9F : 0;
            fingers[i].xRot = -0.25F + twitch;
        }

        // The coat swings back as the legs move, and settles when he stops.
        float stride = limbSwingAmount * 0.6F;
        coatFrontRight.xRot = Math.min(0, rightLeg.xRot) * 0.9F - 0.02F;
        coatFrontLeft.xRot = Math.min(0, leftLeg.xRot) * 0.9F - 0.02F;
        coatBack.xRot = Math.abs(walk) * stride * 0.5F + 0.04F;
        coatSideRight.zRot = Math.abs(walk) * stride * 0.15F;
        coatSideLeft.zRot = -Math.abs(walk) * stride * 0.15F;

        // The eyes (and, in the negative, the head) look at whoever he's looking at, and drift slightly.
        float yaw = netHeadYaw * ((float) Math.PI / 180F);
        float pitchRad = headPitch * ((float) Math.PI / 180F);
        if (p == Presence.HEAD_TURN && !Float.isNaN(forcedHeadYaw)) yaw = forcedHeadYaw * ((float) Math.PI / 180F);
        eyes.yRot = head.yRot = yaw;
        eyes.xRot = head.xRot = pitchRad;
        eyes.zRot = head.zRot = p == Presence.HEAD_TURN ? 0.35F : 0;
        eyes.y = -7.5F + breathe * 0.25F;
        head.y = -7.5F;

        showNormal(negativeMask);
    }

    /** Puts the figure in a curled-up pose: knees up, arms around them (the tiny figure inside the Lantern). */
    public void curlUp() {
        body.xRot = 0.5F;
        rightLeg.xRot = leftLeg.xRot = -1.9F;
        rightLeg.y = leftLeg.y = 4.0F;
        rightLeg.z = leftLeg.z = -4.0F;
        rightArm.xRot = leftArm.xRot = -1.2F;
        rightForearm.xRot = leftForearm.xRot = -1.0F;
        rightArm.zRot = 0.3F;
        leftArm.zRot = -0.3F;
        eyes.z = -1.0F;
        eyes.y = -6.0F;
        head.visible = false;
    }

    /** The normal pass: the headless figure with its eyes. Parts in the mask are skipped (the negative layer draws them). */
    void showNormal(int mask) {
        head.visible = false;
        eyes.visible = (mask & HEAD) == 0;
        body.visible = (mask & BODY) == 0;
        rightArm.visible = (mask & RIGHT_ARM) == 0;
        leftArm.visible = (mask & LEFT_ARM) == 0;
        rightLeg.visible = leftLeg.visible = (mask & LEGS) == 0;
    }

    /** The negative pass: only the parts in the mask, and the white head instead of the eyes. */
    void showNegative(int mask) {
        head.visible = (mask & HEAD) != 0;
        eyes.visible = false;
        body.visible = (mask & BODY) != 0;
        rightArm.visible = (mask & RIGHT_ARM) != 0;
        leftArm.visible = (mask & LEFT_ARM) != 0;
        rightLeg.visible = leftLeg.visible = (mask & LEGS) != 0;
    }

    /** Copies the pose of another body (used for the energy shell that moves with him). */
    void copyPose(SolaceModel<?> other) {
        copy(other.body, body);
        copy(other.eyes, eyes);
        copy(other.head, head);
        copy(other.rightArm, rightArm);
        copy(other.leftArm, leftArm);
        copy(other.rightLeg, rightLeg);
        copy(other.leftLeg, leftLeg);
        copy(other.rightForearm, rightForearm);
        copy(other.leftForearm, leftForearm);
        copy(other.coatBack, coatBack);
        copy(other.coatFrontRight, coatFrontRight);
        copy(other.coatFrontLeft, coatFrontLeft);
        copy(other.coatSideRight, coatSideRight);
        copy(other.coatSideLeft, coatSideLeft);
    }

    private static void copy(ModelPart from, ModelPart to) {
        to.copyFrom(from);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        for (ModelPart part : roots) part.render(poseStack, consumer, light, overlay, color);
    }

    /** Just the eyes, for drawing them glowing. */
    public void renderEyes(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        eyes.render(poseStack, consumer, light, overlay, color);
    }
}
