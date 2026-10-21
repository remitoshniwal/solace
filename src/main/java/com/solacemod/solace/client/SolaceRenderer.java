package com.solacemod.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solacemod.solace.client.model.BbModel;
import com.solacemod.solace.entity.Presence;
import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Draws Solace: a tall, headless shadow with long clawed arms, spiked shoulders and a tattered cloak (a Blockbench
 * model, see SolaceBody). No eyes. As the story goes on its spikes grow, and now and then it flickers into its negative:
 * a white, flaming wraith with black bands and scythe blades for hands. Its true form, the Lantern, is drawn by LanternRenderer.
 */
public class SolaceRenderer extends EntityRenderer<SolaceEntity> {
    /** How far it has grown (0..4), by corruption level (= story chapter). */
    private static final int[] STAGE = {0, 0, 0, 1, 2, 3, 3, 4, 4};
    /** Chance of a negative flash in each half second, by corruption level. Chapter 2 about once a minute, chapter 8 every few seconds. */
    private static final double[] FLASH_CHANCE = {0, 0, 0.008, 0.015, 0.025, 0.04, 0.055, 0.08, 0.1};

    private final LanternRenderer lantern;
    private float frozenSwing, frozenAmount, frozenAge, frozenLook, frozenBob;
    private int lastParticleTick = -1;

    public SolaceRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.45F;
        this.lantern = new LanternRenderer();
    }

    @Override
    public ResourceLocation getTextureLocation(SolaceEntity entity) {
        return SolaceBody.NORMAL;
    }

    /**
     * Which body areas are showing their negative side right now. A flash lasts half a second, and inside it he
     * flickers between the two, like a bad signal. Worked out from the time, so it costs nothing to sync.
     */
    static int negativeMask(SolaceEntity entity, float t) {
        if (entity.isNegative()) return BbModel.ALL; // fully transformed: no flicker
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
        return switch (Math.floorMod(h >>> 16, 5)) {
            case 0, 1 -> BbModel.ALL;
            case 2 -> BbModel.RIGHT_ARM;
            case 3 -> BbModel.LEFT_ARM;
            default -> BbModel.BODY | BbModel.RIGHT_ARM;
        };
    }

    @Override
    public void render(SolaceEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float t = entity.tickCount + partialTick;
        if (entity.isLanternForm()) {
            lantern.render(entity, partialTick, poseStack, buffer, packedLight);
            return;
        }
        int presence = entity.getPresence();
        float shift = 0;
        if (presence == Presence.FLICKER) {
            // Gone for a few frames, back, gone again. Sometimes it's drawn slightly to one side.
            int frame = (int) (t / 2);
            int noise = (frame * 73856093) ^ (entity.getId() * 19349663);
            if (Math.floorMod(noise, 3) == 0) return;
            if (Math.floorMod(noise, 7) == 0) shift = Math.floorMod(noise, 2) == 0 ? 0.4F : -0.4F;
        }

        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float swing = entity.walkAnimation.position(partialTick);
        float amount = Math.min(1.0F, entity.walkAnimation.speed(partialTick));
        // No head to turn: where it looks, its upper body follows a little.
        float look = Mth.wrapDegrees(Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot) - bodyYaw) * 0.35F;
        if (presence == Presence.HEAD_TURN) {
            // Its whole upper body twists round to face you, however far that is, while its legs stay put.
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double dx = cam.x - Mth.lerp(partialTick, entity.xo, entity.getX());
            double dz = cam.z - Mth.lerp(partialTick, entity.zo, entity.getZ());
            look = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F - bodyYaw);
        }
        if (presence == Presence.GLIDE) amount = 0;
        float bob = (float) Math.sin(t * 0.06) * 0.06F;
        float age = t;
        if (presence == Presence.FREEZE) {
            // Keep the last pose exactly, even though it's still moving: like a paused video.
            swing = frozenSwing;
            amount = frozenAmount;
            age = frozenAge;
            look = frozenLook;
            bob = frozenBob;
        } else {
            frozenSwing = swing;
            frozenAmount = amount;
            frozenAge = age;
            frozenLook = look;
            frozenBob = bob;
        }
        int stage = STAGE[Math.max(0, Math.min(8, entity.getCorruption()))];
        SolaceBody.pose(swing, amount, age, -look, entity.getAttackAnim(partialTick), entity.getId() * 31, stage);

        int mask = negativeMask(entity, t);
        int overlay = OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(entity.hurtTime > 0));

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        poseStack.translate(shift, 0.04 + bob, 0);
        // The faint light along its edges grows stronger as the story goes on, and stutters like a bad bulb.
        // Once it's uneasy (later chapters) the bulb is failing: dirtier, and it cuts out far more often.
        boolean uneasy = entity.getForm() == SolaceEntity.FORM_UNEASY;
        float glow = (0.25F + 0.15F * stage) * (uneasy ? failingNeon(t, entity.getId() + 7) : neon(t, entity.getId() + 7));
        SolaceBody.draw(poseStack, buffer, packedLight, overlay, mask, glow, age, amount, uneasy);
        poseStack.popPose();

        if (entity.tickCount != lastParticleTick) {
            lastParticleTick = entity.tickCount;
            particles(entity, mask, partialTick);
        }
        // (No name tag: it's not a pet.)
    }

    /** Neon brightness: a slow pulse, with the odd buzz-and-dip of a real neon tube. */
    static float neon(float t, int seed) {
        float pulse = 0.82F + 0.18F * Mth.sin(t * 0.25F);
        int h = (int) t * 0x27D4EB2D ^ seed * 0x165667B1;
        h ^= h >>> 15;
        return Math.floorMod(h, 29) == 0 ? pulse * 0.45F : pulse;
    }

    /** A failing neon tube: it buzzes between half and full and cuts out often, sometimes for a few ticks at a time. */
    static float failingNeon(float t, int seed) {
        float pulse = 0.75F + 0.25F * Mth.sin(t * 0.25F);
        int h = (int) (t / 3) * 0x27D4EB2D ^ seed * 0x165667B1;
        h ^= h >>> 15;
        int roll = Math.floorMod(h, 9);
        return roll == 0 ? 0.1F : roll == 1 ? pulse * 0.5F : pulse;
    }

    /** A few sparks and specks of light while it's negative; wisps of dark smoke off its shoulders in the late chapters. */
    private static void particles(SolaceEntity e, int mask, float partialTick) {
        Level level = e.level();
        RandomSource r = e.getRandom();
        double x = Mth.lerp(partialTick, e.xo, e.getX()), y = Mth.lerp(partialTick, e.yo, e.getY()) + 0.12, z = Mth.lerp(partialTick, e.zo, e.getZ());
        if (mask == 0) {
            if (e.getCorruption() >= 4 && r.nextInt(30) == 0) {
                level.addParticle(ParticleTypes.SMOKE, x + r.nextGaussian() * 0.2, y + 1.85, z + r.nextGaussian() * 0.2, 0, 0.02, 0);
            }
            return;
        }
        boolean full = e.isNegative();
        float yaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);   // his right side
        DustParticleOptions white = new DustParticleOptions(new Vector3f(0.95F, 0.97F, 1.0F), 0.45F);
        for (int side : new int[]{1, -1}) {
            int part = side == 1 ? BbModel.RIGHT_ARM : BbModel.LEFT_ARM;
            if ((mask & part) == 0) continue;
            double hx = x + rx * 0.3 * side, hy = y + 0.5, hz = z + rz * 0.3 * side;
            if (r.nextInt(2) == 0) level.addParticle(white, hx + r.nextGaussian() * 0.05, hy + r.nextGaussian() * 0.05, hz + r.nextGaussian() * 0.05, 0, 0, 0);
            if (r.nextInt(full ? 5 : 9) == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK, hx, hy, hz, r.nextGaussian() * 0.08, r.nextGaussian() * 0.08, r.nextGaussian() * 0.08);
            }
        }
        if (full) {
            if (r.nextInt(3) == 0) {
                level.addParticle(white, x + r.nextGaussian() * 0.3, y + 0.3 + r.nextDouble() * 1.4, z + r.nextGaussian() * 0.3, 0, 0, 0);
            }
            if (r.nextInt(8) == 0) level.addParticle(ParticleTypes.SMOKE, x + r.nextGaussian() * 0.25, y + 0.5 + r.nextDouble() * 1.1, z + r.nextGaussian() * 0.25, 0, 0.015, 0);
        }
    }
}
