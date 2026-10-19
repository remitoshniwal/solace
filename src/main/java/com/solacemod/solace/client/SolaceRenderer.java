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
    private static final ResourceLocation NEGATIVE_GLOW = Solace.id("textures/entity/solace_negative_glow.png");
    private static final ResourceLocation AURA = Solace.id("textures/entity/solace_aura.png");
    private final SolaceModel auraModel;
    private int lastParticleTick = -1;
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
        auraModel = new SolaceModel(context.bakeLayer(SolaceModels.BODY_AURA));
        addLayer(new Negative(this, auraModel));
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
        if (entity.isNegative()) return SolaceModel.ALL; // fully transformed: no flicker
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
        int mask = negativeMask(entity, t);
        getModel().negativeMask = mask;
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        if (entity.tickCount != lastParticleTick) {
            lastParticleTick = entity.tickCount;
            particles(entity, mask, partialTick);
        }
        poseStack.popPose();
    }

    /** Neon brightness: a slow pulse, with the odd buzz-and-dip of a real neon tube. */
    static float neon(float t, int seed) {
        float pulse = 0.82F + 0.18F * Mth.sin(t * 0.25F);
        int frame = (int) t;
        int h = frame * 0x27D4EB2D ^ seed * 0x165667B1;
        h ^= h >>> 15;
        return Math.floorMod(h, 29) == 0 ? pulse * 0.45F : pulse;
    }

    /**
     * Sparks and light coming off him while he's negative: glowing specks drifting up, white light at his hands,
     * crackles, and dark smoke curling away. In the late chapters, a wisp of dark smoke now and then even when he looks normal.
     */
    private static void particles(SolaceEntity e, int mask, float partialTick) {
        net.minecraft.world.level.Level level = e.level();
        net.minecraft.util.RandomSource r = e.getRandom();
        double x = Mth.lerp(partialTick, e.xo, e.getX()), y = Mth.lerp(partialTick, e.yo, e.getY()) + 0.12, z = Mth.lerp(partialTick, e.zo, e.getZ());
        if (mask == 0) {
            if (e.getCorruption() >= 5 && r.nextInt(40) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, x, y + 1.45, z, 0, 0.02, 0);
            return;
        }
        boolean full = e.isNegative();
        float yaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot) * ((float) Math.PI / 180F);
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);   // his right side
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);     // in front of him
        var white = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.95F, 0.97F, 1.0F), 0.7F);
        for (int side : new int[]{1, -1}) {
            int part = side == 1 ? SolaceModel.RIGHT_ARM : SolaceModel.LEFT_ARM;
            if ((mask & part) == 0) continue;
            double hx = x + rx * 0.36 * side + fx * 0.05, hy = y + 0.72, hz = z + rz * 0.36 * side + fz * 0.05;
            level.addParticle(white, hx + r.nextGaussian() * 0.05, hy + r.nextGaussian() * 0.05, hz + r.nextGaussian() * 0.05, 0, 0, 0);
            if (r.nextInt(full ? 3 : 6) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, hx, hy, hz,
                    r.nextGaussian() * 0.08, r.nextGaussian() * 0.08, r.nextGaussian() * 0.08);
        }
        if ((mask & SolaceModel.HEAD) != 0 && r.nextInt(full ? 2 : 4) == 0) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, x + fx * 0.25, y + 1.6, z + fz * 0.25, 0, 0.01, 0);
        }
        if (full) {
            for (int i = 0; i < 2; i++) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, x + r.nextGaussian() * 0.3, y + 0.3 + r.nextDouble() * 1.5,
                        z + r.nextGaussian() * 0.3, 0, 0.02 + r.nextDouble() * 0.03, 0);
            }
            if (r.nextInt(4) == 0) level.addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, x + r.nextGaussian() * 0.3,
                    y + 0.4 + r.nextDouble(), z + r.nextGaussian() * 0.3, 0, 0.02, 0);
        }
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

    /**
     * The parts showing his negative side this frame: inverted and lit, white neon veins and burning eyes on top,
     * and a shell of flowing light around them. All drawn glowing, so it shines even in total darkness.
     */
    private static final class Negative extends RenderLayer<SolaceEntity, SolaceModel> {
        private final SolaceModel aura;

        Negative(RenderLayerParent<SolaceEntity, SolaceModel> parent, SolaceModel aura) {
            super(parent);
            this.aura = aura;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, SolaceEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            SolaceModel model = getParentModel();
            int mask = model.negativeMask;
            if (mask == 0) return;
            int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0);
            int full = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            model.showParts(mask);
            // 1. The inverted body: near-black skin, white suit, white hair.
            model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(NEGATIVE)), full, overlay);
            // 2. Neon: white veins over his skin, burning eyes, light along the seams. Added on top, so it shines.
            int glow = grey(neon(ageInTicks, entity.getId()));
            model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.eyes(NEGATIVE_GLOW)), full, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, glow);
            // 3. Energy flowing over and around him (a slightly bigger copy of the body, with the light scrolling across it).
            aura.copyPose(model);
            aura.showParts(mask);
            float flow = ageInTicks * 0.01F;
            int strength = grey(entity.isNegative() ? 0.75F : 0.45F);
            aura.renderToBuffer(poseStack, buffer.getBuffer(RenderType.energySwirl(AURA, flow % 1.0F, (ageInTicks * 0.025F) % 1.0F)), full,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, strength);
            model.showParts(SolaceModel.ALL);
        }

        private static int grey(float level) {
            int v = Mth.clamp((int) (level * 255), 0, 255);
            return 0xFF000000 | v << 16 | v << 8 | v;
        }
    }
}
