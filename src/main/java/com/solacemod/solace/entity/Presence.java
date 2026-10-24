package com.solacemod.solace.entity;

import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.story.PlayerStory;
import com.solacemod.solace.story.StoryData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * How Solace moves: player-like most of the time, but not always.
 * A hidden "creep meter" fills up over time (faster in later chapters). When it overflows, Solace does one
 * wrong thing, then the meter resets to a random level so you can never predict the next one.
 */
public final class Presence {
    /** Synced to clients (renderer uses these). */
    public static final int WALK = 0, GLIDE = 1, STUTTER = 2, FLICKER = 3, HEAD_TURN = 4, FREEZE = 5, CORNER = 6, WRONG_PLACE = 7, MIRROR = 8;

    private final SolaceEntity solace;
    private double creep;
    private double threshold = 80;
    private int ticksLeft;
    private int lookedAt;

    Presence(SolaceEntity solace) {
        this.solace = solace;
    }

    /** True while a behaviour is moving Solace itself (normal following and jobs wait). */
    public boolean controlsMovement() {
        int b = solace.getPresence();
        return b == STUTTER || b == HEAD_TURN || b == CORNER || b == WRONG_PLACE || b == MIRROR;
    }

    void tick() {
        ServerPlayer owner = solace.getServerOwner();
        if (owner == null || owner.level() != solace.level()) {
            end();
            return;
        }
        if (solace.getPresence() != WALK) {
            run(owner);
            return;
        }
        if (solace.tickCount % 20 != 0) return;
        // Never in real danger, never while it's busy with a job, never if horror is off.
        if (!SolaceConfig.STORY_ENABLED.get() || SolaceConfig.HORROR_INTENSITY.get() <= 0) return;
        if (solace.combat.ownerFighting() || owner.getHealth() <= 8 || solace.getTask() != null || solace.getStareTicks() > 0) return;
        PlayerStory story = story(owner);
        if (story == null || story.bossActive || story.companion) return;
        int chapter = story.chapter;
        double rate = switch (chapter) {
            case 1 -> 0.3;
            case 2 -> 0.6;
            case 3 -> 1.0;
            case 4 -> 1.4;
            default -> 1.8;
        };
        // The more the player owes it, the more often it moves wrong.
        double debtFactor = 1.0 + Math.min(1.5, story.debt / 100.0);
        creep += rate * debtFactor * SolaceConfig.HORROR_INTENSITY.get() * (0.5 + solace.getRandom().nextDouble());
        if (creep < threshold) return;
        creep = solace.getRandom().nextDouble() * 30;
        threshold = 60 + solace.getRandom().nextDouble() * 60;
        start(owner, pick(chapter));
    }

    private int pick(int chapter) {
        List<Integer> options = new ArrayList<>();
        options.add(-1); // blink behind you
        if (chapter >= 2) {
            options.add(GLIDE);
            options.add(STUTTER);
            options.add(FLICKER);
            options.add(FLICKER);
        }
        if (chapter >= 3) {
            options.add(HEAD_TURN);
            options.add(FREEZE);
            options.add(CORNER);
            options.add(CORNER);
        }
        if (chapter >= 4) options.add(WRONG_PLACE);
        if (chapter >= 5) options.add(MIRROR);
        return options.get(solace.getRandom().nextInt(options.size()));
    }

    /** Starts a behaviour now (also used by /solace admin presence). */
    public void start(ServerPlayer owner, int behaviour) {
        if (behaviour == -1) {
            if (!isLookedAt(owner)) solace.blinkBehind(owner);
            return;
        }
        lookedAt = 0;
        ticksLeft = switch (behaviour) {
            case GLIDE -> 20 * 15;
            case STUTTER -> 20 * 3;
            case FLICKER -> 20 * 4;
            case HEAD_TURN -> 20 * 12;
            case FREEZE -> 20 * 6;
            case CORNER -> 20 * 12;
            case WRONG_PLACE -> 20 * 12;
            case MIRROR -> 20 * 60;
            default -> 0;
        };
        solace.setPresence(behaviour);
        solace.getNavigation().stop();
        if (behaviour == CORNER || behaviour == WRONG_PLACE) {
            Vec3 spot = behaviour == CORNER ? peripheralSpot(owner) : wrongSpot(owner);
            if (spot == null) {
                end();
                return;
            }
            solace.moveTo(spot.x, spot.y, spot.z, solace.getYRot(), 0);
        }
    }

    private void run(ServerPlayer owner) {
        int b = solace.getPresence();
        if (--ticksLeft <= 0) {
            boolean wasAway = b == CORNER || b == WRONG_PLACE;
            end();
            // Back beside you, as if it never left. (Only when you're not looking, of course.)
            if (wasAway && !isLookedAt(owner)) solace.blinkBehind(owner);
            return;
        }
        switch (b) {
            case STUTTER -> {
                // Jumps toward you a block at a time, like it's lagging.
                if (ticksLeft % 4 == 0 && solace.distanceTo(owner) > 2.5) {
                    Vec3 step = owner.position().subtract(solace.position()).multiply(1, 0, 1).normalize();
                    Vec3 to = solace.position().add(step);
                    if (solace.level().noCollision(solace, solace.getBoundingBox().move(step))) solace.moveTo(to.x, solace.getY(), to.z, solace.getYRot(), 0);
                }
            }
            case HEAD_TURN -> {
                // Walks away from you. Its head doesn't (the client turns it all the way round to keep watching you).
                if (solace.getNavigation().isDone()) {
                    Vec3 away = solace.position().subtract(owner.position()).multiply(1, 0, 1);
                    if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
                    Vec3 to = solace.position().add(away.normalize().scale(5));
                    solace.getNavigation().moveTo(to.x, solace.getY(), to.z, 0.55);
                }
            }
            case FLICKER -> {
                // Halfway through, it's suddenly standing a little to one side.
                if (ticksLeft == 40 && solace.getRandom().nextBoolean()) {
                    Vec3 side = new Vec3(solace.getRandom().nextDouble() * 3 - 1.5, 0, solace.getRandom().nextDouble() * 3 - 1.5);
                    if (solace.level().noCollision(solace, solace.getBoundingBox().move(side))
                            && !solace.level().noCollision(solace, solace.getBoundingBox().move(side).move(0, -0.5, 0))) {
                        solace.moveTo(solace.getX() + side.x, solace.getY(), solace.getZ() + side.z, solace.getYRot(), solace.getXRot());
                    }
                }
            }
            case CORNER -> {
                // Stands somewhere it shouldn't be, at the edge of your vision. Look straight at it and it's gone.
                solace.getNavigation().stop();
                solace.getLookControl().setLookAt(owner, 180F, 180F);
                if (isLookedAt(owner) && ++lookedAt > 6) {
                    end();
                    solace.blinkBehind(owner);
                }
            }
            case WRONG_PLACE -> {
                solace.getNavigation().stop();
                solace.getLookControl().setLookAt(owner, 180F, 180F);
            }
            case MIRROR -> mirror(owner);
            default -> {
            }
        }
    }

    /** Copies everything the player does, standing in front of them facing them. */
    private void mirror(ServerPlayer owner) {
        Vec3 look = owner.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) return;
        look = look.normalize();
        Vec3 target = owner.position().add(look.scale(3));
        solace.getNavigation().stop();
        if (solace.level().noCollision(solace, solace.getBoundingBox().move(target.subtract(solace.position())))) {
            solace.moveTo(target.x, owner.getY(), target.z, owner.getYRot() + 180F, owner.getXRot());
        }
        solace.setYHeadRot(owner.getYHeadRot() + 180F);
        solace.setYBodyRot(owner.yBodyRot + 180F);
        solace.setShiftKeyDown(owner.isShiftKeyDown());
        if (owner.swinging && !solace.swinging) solace.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    public void end() {
        if (solace.getPresence() == HEAD_TURN) solace.getNavigation().stop();
        if (solace.getPresence() != WALK) solace.setPresence(WALK);
        solace.setShiftKeyDown(false);
        ticksLeft = 0;
    }

    public static final String[] NAMES = {"walk", "glide", "stutter", "flicker", "head_turn", "freeze", "corner", "wrong_place", "mirror"};

    /** For /solace admin presence: "blink" or one of NAMES. Returns -2 if unknown. */
    public static int byName(String name) {
        if (name.equals("blink")) return -1;
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return i;
        return -2;
    }

    // ---------- Helpers ----------

    private boolean isLookedAt(ServerPlayer p) {
        Vec3 view = p.getViewVector(1.0F).normalize();
        Vec3 to = solace.getEyePosition().subtract(p.getEyePosition()).normalize();
        return view.dot(to) > 0.96 && p.hasLineOfSight(solace);
    }

    /** A spot 14-28 blocks away, off to the side of where the player is looking. */
    private Vec3 peripheralSpot(ServerPlayer p) {
        ServerLevel level = (ServerLevel) solace.level();
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(1, 0, 0);
        look = look.normalize();
        for (int i = 0; i < 12; i++) {
            double angle = (solace.getRandom().nextBoolean() ? 1 : -1) * (0.9 + solace.getRandom().nextDouble() * 0.5);
            double dist = 14 + solace.getRandom().nextDouble() * 14;
            double x = p.getX() + (look.x * Math.cos(angle) - look.z * Math.sin(angle)) * dist;
            double z = p.getZ() + (look.x * Math.sin(angle) + look.z * Math.cos(angle)) * dist;
            Vec3 spot = groundAt(level, BlockPos.containing(x, p.getY(), z), p.getBlockY());
            if (spot != null) return spot;
        }
        return null;
    }

    /** Somewhere a person can't (or wouldn't) stand: on top of the tallest thing nearby. */
    private Vec3 wrongSpot(ServerPlayer p) {
        ServerLevel level = (ServerLevel) solace.level();
        BlockPos best = null;
        for (int i = 0; i < 20; i++) {
            BlockPos column = p.blockPosition().offset(solace.getRandom().nextInt(25) - 12, 0, solace.getRandom().nextInt(25) - 12);
            if (!level.hasChunkAt(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            if (top.getY() - p.getBlockY() > 4 && top.getY() - p.getBlockY() < 30 && (best == null || top.getY() > best.getY())) best = top;
        }
        return best == null ? null : Vec3.atBottomCenterOf(best);
    }

    private static Vec3 groundAt(ServerLevel level, BlockPos column, int nearY) {
        if (!level.hasChunkAt(column)) return null;
        for (int dy = 6; dy >= -6; dy--) {
            BlockPos p = new BlockPos(column.getX(), nearY + dy, column.getZ());
            if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()
                    && level.getFluidState(p).isEmpty()) {
                return Vec3.atBottomCenterOf(p);
            }
        }
        return null;
    }

    private static PlayerStory story(ServerPlayer p) {
        return p.getServer() == null ? null : StoryData.get(p.getServer()).getIfPresent(p.getUUID());
    }
}
