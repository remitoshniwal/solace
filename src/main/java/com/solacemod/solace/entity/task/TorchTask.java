package com.solacemod.solace.entity.task;

import com.solacemod.solace.entity.SolaceEntity;
import com.solacemod.solace.util.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Places torches on dark spots nearby so mobs can't spawn. Crafts torches first if it has coal and sticks. */
public final class TorchTask implements SolaceTask {
    private final List<BlockPos> placedSpots = new ArrayList<>();
    private BlockPos target;
    private int placed;
    private int age;

    @Override
    public boolean tick(SolaceEntity solace) {
        ServerLevel level = (ServerLevel) solace.level();
        if (++age > 20 * 90 || placed >= 10) return finish(solace);
        if (solace.inventory.countItem(Items.TORCH) == 0) {
            CraftHelper.craft(solace, Items.TORCH, 4);
            if (solace.inventory.countItem(Items.TORCH) == 0) {
                TaskUtil.report(solace, placed == 0 ? "I don't have any torches. Coal and sticks would work too." : "Out of torches! I placed " + placed + ".");
                return true;
            }
        }
        if (target == null) {
            target = findDarkSpot(level, solace.blockPosition());
            if (target == null) return finish(solace);
            TaskUtil.walkTo(solace, target, 1.1);
        }
        if (solace.getEyePosition().distanceTo(Vec3.atCenterOf(target)) > 4.5) {
            if (solace.getNavigation().isDone() && !TaskUtil.walkTo(solace, target, 1.1)) {
                placedSpots.add(target); // can't reach, don't retry
                target = null;
            }
            return false;
        }
        if (level.getBlockState(target).isAir()) {
            level.setBlock(target, Blocks.TORCH.defaultBlockState(), Block.UPDATE_ALL);
            Sounds.play(level, Vec3.atCenterOf(target), "block.wood.place", SoundSource.BLOCKS, 1.0F, 1.0F);
            solace.swing(InteractionHand.MAIN_HAND);
            solace.inventory.removeItemType(Items.TORCH, 1);
            placed++;
        }
        placedSpots.add(target);
        target = null;
        return false;
    }

    private BlockPos findDarkSpot(ServerLevel level, BlockPos origin) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    double dist = p.distSqr(origin);
                    if (dist >= bestDist) continue;
                    if (!level.getBlockState(p).isAir()) continue;
                    if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) continue;
                    if (level.getBrightness(LightLayer.BLOCK, p) >= 8) continue;
                    if (level.canSeeSky(p) && level.isDay()) continue;
                    if (tooClose(p)) continue;
                    best = p;
                    bestDist = dist;
                }
            }
        }
        return best;
    }

    private boolean tooClose(BlockPos p) {
        for (BlockPos q : placedSpots) if (q.distSqr(p) < 5 * 5) return true;
        return false;
    }

    private boolean finish(SolaceEntity solace) {
        TaskUtil.report(solace, placed == 0 ? "It's already bright enough here." : "Placed " + placed + " torches. Safer now.");
        return true;
    }

    @Override
    public String describe() {
        return "placing torches (" + placed + ")";
    }
}
