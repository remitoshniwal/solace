package com.solacemod.solace.entity.task;

import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Finds blocks of one kind nearby, walks to them, and mines them. Solace doesn't need tools: without one it breaks
 * the block with its power (and still gets the proper drops). A real tool from its pockets makes it faster.
 */
public final class MineTask implements SolaceTask {
    private static final int RADIUS = 14;
    private static final int HEIGHT = 8;
    private static final int TIMEOUT = 20 * 240;

    private final BlockMatcher matcher;
    private final int wanted;
    private final Set<BlockPos> unreachable = new HashSet<>();
    private int mined;
    private BlockPos target;
    private int progress;
    private int breakTime;
    private int stuck;
    private int age;
    private int searchDelay;

    public MineTask(BlockMatcher matcher, int count) {
        this.matcher = matcher;
        this.wanted = Math.max(1, count);
    }

    @Override
    public boolean tick(SolaceEntity solace) {
        ServerLevel level = (ServerLevel) solace.level();
        if (++age > TIMEOUT) {
            TaskUtil.report(solace, "I got tired of looking. I mined " + mined + " " + matcher.displayName() + ".");
            return true;
        }
        if (target == null || !matcher.test(level.getBlockState(target))) {
            clearProgress(level, solace);
            if (--searchDelay > 0) return false;
            searchDelay = 10;
            target = findNearest(level, solace);
            if (target == null) {
                TaskUtil.report(solace, mined == 0 ? "I can't find any " + matcher.displayName() + " nearby."
                        : "That's all the " + matcher.displayName() + " I can reach. I got " + mined + ".");
                return true;
            }
            solace.equipBestToolFor(level.getBlockState(target)); // a tool helps, but isn't needed
            if (!TaskUtil.walkTo(solace, target, 1.1)) {
                unreachable.add(target);
                target = null;
                return false;
            }
            stuck = 0;
        }

        Vec3 center = Vec3.atCenterOf(target);
        if (solace.getEyePosition().distanceTo(center) > 4.5) {
            if (solace.getNavigation().isDone() && !TaskUtil.walkTo(solace, target, 1.1)) stuck += 20;
            if (++stuck > 200) {
                unreachable.add(target);
                target = null;
            }
            return false;
        }

        solace.getNavigation().stop();
        solace.getLookControl().setLookAt(center.x, center.y, center.z);
        BlockState state = level.getBlockState(target);
        if (breakTime == 0) breakTime = breakTime(solace, state, level, target);
        progress++;
        if (progress % 5 == 0) solace.swing(InteractionHand.MAIN_HAND);
        level.destroyBlockProgress(solace.getId(), target, Math.min(9, progress * 10 / breakTime));
        if (progress >= breakTime) {
            BlockPos done = target;
            clearProgress(level, solace);
            target = null;
            solace.harvest(done);
            mined++;
            if (mined >= wanted) {
                TaskUtil.report(solace, "Done! I mined " + mined + " " + matcher.displayName() + ".");
                return true;
            }
        }
        return false;
    }

    private void clearProgress(ServerLevel level, SolaceEntity solace) {
        if (target != null && progress > 0) level.destroyBlockProgress(solace.getId(), target, -1);
        progress = 0;
        breakTime = 0;
    }

    @Override
    public void stop(SolaceEntity solace) {
        clearProgress((ServerLevel) solace.level(), solace);
    }

    /** Roughly how long a player would take with the same tool. With its bare power, about as fast as a stone tool. */
    private static int breakTime(SolaceEntity solace, BlockState state, ServerLevel level, BlockPos pos) {
        float hardness = state.getDestroySpeed(level, pos);
        ItemStack tool = solace.getMainHandItem();
        boolean hasTool = tool.isCorrectToolForDrops(state) || (!state.requiresCorrectToolForDrops() && tool.getDestroySpeed(state) > 1.0F);
        float speed = hasTool ? Math.max(1.0F, tool.getDestroySpeed(state)) : 4.0F;
        int ticks = (int) Math.ceil(hardness * 30 / speed * 0.8);
        return Math.max(4, Math.min(400, ticks));
    }

    private BlockPos findNearest(ServerLevel level, SolaceEntity solace) {
        BlockPos origin = solace.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -HEIGHT; dy <= HEIGHT; dy++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    double dist = dx * dx + dy * dy * 1.5 + dz * dz;
                    if (dist >= bestDist) continue;
                    BlockState state = level.getBlockState(p);
                    if (!matcher.test(state) || unreachable.contains(p)) continue;
                    float hardness = state.getDestroySpeed(level, p);
                    if (hardness < 0 || hardness > 50) continue;
                    if (!isExposed(level, p)) continue;
                    best = p.immutable();
                    bestDist = dist;
                }
            }
        }
        return best;
    }

    /** Only mine blocks that touch air, so Solace can't see ores through walls. */
    private static boolean isExposed(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos n = pos.relative(dir);
            if (level.getBlockState(n).getCollisionShape(level, n).isEmpty()) return true;
        }
        return false;
    }

    @Override
    public String describe() {
        return "mining " + matcher.displayName() + " (" + mined + "/" + wanted + ")";
    }
}
