package com.solacemod.solace.entity;

import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.entity.task.SolaceTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Solace's behaviours. Lower priority numbers (set in SolaceEntity.registerGoals) win when two want to run. */
public final class SolaceGoals {
    private SolaceGoals() {
    }

    /** Freezes and stares at the owner (a horror moment). */
    public static final class StareGoal extends Goal {
        private final SolaceEntity solace;

        public StareGoal(SolaceEntity solace) {
            this.solace = solace;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return solace.getStareTicks() > 0 && !solace.isHollow();
        }

        @Override
        public void tick() {
            solace.getNavigation().stop();
            net.minecraft.world.entity.Entity target = solace.stareTarget();
            // Staring at itself means staring at nothing: it keeps facing wherever it was facing.
            if (target != null && target != solace) solace.getLookControl().setLookAt(target, 180F, 180F);
            solace.tickStare();
        }
    }

    /** Holds Solace still (or lets Presence move it) while one of its wrong movements is playing. */
    public static final class PresenceGoal extends Goal {
        private final SolaceEntity solace;

        public PresenceGoal(SolaceEntity solace) {
            this.solace = solace;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return !solace.isHollow() && solace.presence.controlsMovement();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    /** Runs the current job (mining, building, collecting...). Pauses while fighting. */
    public static final class TaskGoal extends Goal {
        private final SolaceEntity solace;

        public TaskGoal(SolaceEntity solace) {
            this.solace = solace;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            SolaceTask task = solace.getTask();
            return task != null && !task.isCombat() && !solace.isHollow() && (solace.getTarget() == null || !task.pausesForCombat());
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            SolaceTask task = solace.getTask();
            if (task != null && task.tick(solace)) {
                solace.setTask(null);
            }
        }
    }

    /** Walks after the owner, and teleports if left far behind. */
    public static final class FollowOwnerGoal extends Goal {
        private final SolaceEntity solace;
        private Player owner;
        private int repathTimer;

        public FollowOwnerGoal(SolaceEntity solace) {
            this.solace = solace;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (solace.getMode() != SolaceEntity.Mode.FOLLOW || solace.isHollow()) return false;
            Player o = solace.getOwner();
            if (o == null || o.isSpectator() || solace.distanceToSqr(o) < 6 * 6) return false;
            owner = o;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && !solace.getNavigation().isDone() && solace.getMode() == SolaceEntity.Mode.FOLLOW
                    && solace.distanceToSqr(owner) > 3 * 3;
        }

        @Override
        public void start() {
            repathTimer = 0;
        }

        @Override
        public void stop() {
            owner = null;
            solace.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (owner == null) return;
            solace.getLookControl().setLookAt(owner, 10F, solace.getMaxHeadXRot());
            if (--repathTimer > 0) return;
            repathTimer = 10;
            // Solace doesn't chase you across the world. It just... appears behind you.
            if (solace.distanceToSqr(owner) > 14 * 14 || (solace.getNavigation().isDone() && solace.distanceToSqr(owner) > 8 * 8)) {
                solace.blinkBehind(owner);
            } else {
                solace.getNavigation().moveTo(owner, 1.15);
            }
        }
    }

    /** Tries a few spots around the owner and teleports to the first safe one. */
    public static boolean teleportNear(SolaceEntity solace, LivingEntity target) {
        BlockPos base = target.blockPosition();
        for (int i = 0; i < 12; i++) {
            int dx = solace.getRandom().nextInt(7) - 3;
            int dz = solace.getRandom().nextInt(7) - 3;
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos pos = base.offset(dx, dy, dz);
                BlockState below = solace.level().getBlockState(pos.below());
                if (below.getCollisionShape(solace.level(), pos.below()).isEmpty()) continue;
                Vec3 dest = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                if (solace.level().noCollision(solace, solace.getBoundingBox().move(dest.subtract(solace.position())))) {
                    solace.getNavigation().stop();
                    solace.moveTo(dest.x, dest.y, dest.z, solace.getYRot(), solace.getXRot());
                    return true;
                }
            }
        }
        return false;
    }

    /** Stays near the spot where it was told to wait. */
    public static final class StayGoal extends Goal {
        private final SolaceEntity solace;

        public StayGoal(SolaceEntity solace) {
            this.solace = solace;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            BlockPos pos = solace.getStayPos();
            return solace.getMode() == SolaceEntity.Mode.STAY && pos != null && !solace.isHollow()
                    && solace.blockPosition().distSqr(pos) > 3 * 3;
        }

        @Override
        public void start() {
            BlockPos pos = solace.getStayPos();
            if (pos != null) solace.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return !solace.getNavigation().isDone();
        }
    }

    /** Wanders around when told to. */
    public static final class WanderGoal extends WaterAvoidingRandomStrollGoal {
        private final SolaceEntity solace;

        public WanderGoal(SolaceEntity solace) {
            super(solace, 0.8);
            this.solace = solace;
        }

        @Override
        public boolean canUse() {
            return solace.getMode() == SolaceEntity.Mode.WANDER && !solace.isHollow() && super.canUse();
        }
    }

    static boolean allowedTarget(LivingEntity target) {
        if (target instanceof SolaceEntity || target instanceof WatcherEntity) return false;
        if (target instanceof Player) return SolaceConfig.DEFEND_AGAINST_PLAYERS.get();
        return true;
    }
}
