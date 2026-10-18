package com.solacemod.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solacemod.solace.Solace;
import com.solacemod.solace.entity.Presence;
import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * The Mimic's lumps: things moving under its skin. Each one slowly swells, stretches and shrinks like something elastic.
 * There are 1-2 in chapter 1 and it is covered in them by the end (the server decides how many).
 */
public class LumpLayer extends RenderLayer<SolaceEntity, PlayerModel<SolaceEntity>> {
    private static final ResourceLocation TEXTURE = Solace.id("textures/entity/solace_lump.png");
    private static final int MAX = 24;

    /** Where a lump sits: which body part, a point on its surface, which way is "out", and how it moves. */
    private record Spot(int part, float x, float y, float z, int axis, float sign, float size, float phase, float speed) {
    }

    private static final Spot[] SPOTS = generate();
    private final ModelPart lump;

    public LumpLayer(RenderLayerParent<SolaceEntity, PlayerModel<SolaceEntity>> parent, EntityModelSet models) {
        super(parent);
        this.lump = models.bakeLayer(SolaceModels.LUMP).getChild("lump");
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, SolaceEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        int count = Math.min(MAX, entity.getLumps());
        if (count <= 0 || entity.isInvisible()) return;
        PlayerModel<SolaceEntity> model = getParentModel();
        // Faster when it's upset, or doing something it shouldn't.
        float speed = entity.isAgitated() ? 3.0F : entity.getPresence() != Presence.WALK ? 1.8F : 1.0F;
        // Later on they get bigger and push out past the body's outline.
        float growth = 0.7F + count / 40F;
        float t = entity.getPresence() == Presence.FREEZE ? 0 : ageInTicks;
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);
        for (int i = 0; i < count; i++) {
            Spot s = SPOTS[i];
            ModelPart part = switch (s.part) {
                case 0 -> model.head;
                case 1, 2 -> model.body;
                case 3 -> model.rightArm;
                case 4 -> model.leftArm;
                case 5 -> model.rightLeg;
                default -> model.leftLeg;
            };
            if (!part.visible) continue;
            float wave = Mth.sin(t * 0.07F * s.speed * speed + s.phase);   // -1..1
            float swell = 0.35F + 0.65F * (wave * 0.5F + 0.5F);              // how far it pushes out
            float size = s.size * growth;
            // Elastic: when it pushes out it gets thinner, when it sinks back it spreads.
            float out = size * (0.4F + swell * 1.1F);
            float wide = size * (1.15F - swell * 0.35F);
            poseStack.pushPose();
            part.translateAndRotate(poseStack);
            poseStack.translate(s.x / 16F, s.y / 16F, s.z / 16F);
            poseStack.translate(s.axis == 0 ? s.sign * out * 0.02F : 0, 0, s.axis == 2 ? s.sign * out * 0.02F : 0);
            poseStack.scale(s.axis == 0 ? out : wide, wide * (1.0F + 0.15F * Mth.sin(t * 0.11F + s.phase)), s.axis == 2 ? out : wide);
            lump.render(poseStack, consumer, packedLight, overlay);
            poseStack.popPose();
        }
    }

    /** Picks the lump spots once. The same spots every time, so a lump you noticed is still there later. */
    private static Spot[] generate() {
        Random r = new Random(0x50ACE);
        // Body part boxes in pixels: minX, maxX, minY, maxY, minZ, maxZ (player model, wide arms)
        float[][] boxes = {
                {-4, 4, -8, 0, -4, 4},   // head
                {-4, 4, 0, 12, -2, 2},   // body
                {-4, 4, 0, 12, -2, 2},   // body again (it gets more lumps)
                {-3, 1, -2, 10, -2, 2},  // right arm
                {-1, 3, -2, 10, -2, 2},  // left arm
                {-2, 2, 0, 12, -2, 2},   // right leg
                {-2, 2, 0, 12, -2, 2},   // left leg
        };
        // The first few are on the body and arms, where you'd notice them but not right away.
        int[] order = {1, 3, 2, 4, 1, 6, 0, 5, 2, 3, 4, 1, 0, 6, 5, 2, 1, 3, 4, 0, 2, 5, 6, 1};
        Spot[] spots = new Spot[MAX];
        for (int i = 0; i < MAX; i++) {
            int part = order[i];
            float[] b = boxes[part];
            int axis;
            float sign;
            do {
                axis = r.nextBoolean() ? 0 : 2;
                sign = r.nextBoolean() ? 1 : -1;
            } while (part == 0 && axis == 2 && sign < 0); // never on its face
            float x = axis == 0 ? (sign > 0 ? b[1] : b[0]) : b[0] + 1 + r.nextFloat() * (b[1] - b[0] - 2);
            float z = axis == 2 ? (sign > 0 ? b[5] : b[4]) : b[4] + 1 + r.nextFloat() * (b[5] - b[4] - 2);
            float y = b[2] + 1.5F + r.nextFloat() * (b[3] - b[2] - 3);
            spots[i] = new Spot(part, x, y, z, axis, sign, 0.7F + r.nextFloat() * 0.8F, r.nextFloat() * 6.28F, 0.6F + r.nextFloat() * 0.9F);
        }
        return spots;
    }
}
