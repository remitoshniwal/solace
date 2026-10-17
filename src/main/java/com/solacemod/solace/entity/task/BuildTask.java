package com.solacemod.solace.entity.task;

import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds simple structures using blocks from Solace's pockets.
 * Give it blocks first by right-clicking it while holding them.
 */
public final class BuildTask implements SolaceTask {
    private final String structure;
    private final List<BlockPos> plan;
    @Nullable
    private final Block preferred;
    private int index;
    private int placed;
    private int cooldown;
    private int stuck;

    private BuildTask(String structure, List<BlockPos> plan, @Nullable Block preferred) {
        this.structure = structure;
        this.plan = plan;
        this.preferred = preferred;
    }

    public static final List<String> STRUCTURES = List.of("shelter", "wall", "pillar", "bridge", "platform");

    /** Plans a structure around a center point, facing a direction. Returns null for unknown structure names. */
    @Nullable
    public static BuildTask create(String structure, BlockPos center, Direction facing, @Nullable Block preferred) {
        String s = structure == null ? "shelter" : structure.toLowerCase(Locale.ROOT).trim();
        if (s.equals("house") || s.equals("hut") || s.equals("base")) s = "shelter";
        if (s.equals("tower")) s = "pillar";
        if (s.equals("floor")) s = "platform";
        List<BlockPos> plan = new ArrayList<>();
        Direction side = facing.getClockWise();
        switch (s) {
            case "shelter" -> {
                // 5x5 room around the player, 3 tall, with a doorway in front and a roof.
                for (int y = 0; y < 3; y++) {
                    for (int a = -2; a <= 2; a++) {
                        for (int b = -2; b <= 2; b++) {
                            if (Math.abs(a) != 2 && Math.abs(b) != 2) continue;
                            BlockPos p = center.relative(facing, a).relative(side, b).above(y);
                            boolean doorway = a == 2 && b == 0 && y < 2;
                            if (!doorway) plan.add(p);
                        }
                    }
                }
                for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) plan.add(center.relative(facing, a).relative(side, b).above(3));
            }
            case "wall" -> {
                for (int y = 0; y < 3; y++) for (int b = -3; b <= 3; b++) plan.add(center.relative(facing, 2).relative(side, b).above(y));
            }
            case "pillar" -> {
                for (int y = 0; y < 6; y++) plan.add(center.relative(side, 2).above(y));
            }
            case "bridge" -> {
                for (int a = 1; a <= 12; a++) for (int b = 0; b <= 1; b++) plan.add(center.relative(facing, a).relative(side, b).below());
            }
            case "platform" -> {
                for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) plan.add(center.relative(facing, a).relative(side, b).below());
            }
            default -> {
                return null;
            }
        }
        return new BuildTask(s, plan, preferred);
    }

    @Override
    public boolean tick(SolaceEntity solace) {
        ServerLevel level = (ServerLevel) solace.level();
        if (--cooldown > 0) return false;
        // Skip spots that are already filled.
        while (index < plan.size() && !canPlace(level, plan.get(index))) index++;
        if (index >= plan.size()) {
            TaskUtil.report(solace, placed == 0 ? "There's no room to build a " + structure + " here." : "Finished the " + structure + "! (" + placed + " blocks)");
            return true;
        }
        BlockPos pos = plan.get(index);
        int slot = findBlockSlot(solace);
        if (slot < 0) {
            TaskUtil.report(solace, placed == 0 ? "I don't have any blocks. Give me some (right-click me holding them)."
                    : "I ran out of blocks after placing " + placed + ". Give me more and ask again.");
            return true;
        }
        Vec3 c = Vec3.atCenterOf(pos);
        if (solace.getEyePosition().distanceTo(c) > 5.0) {
            if (solace.getNavigation().isDone()) TaskUtil.walkTo(solace, pos, 1.1);
            if (++stuck > 200) {
                index++; // give up on this spot
                stuck = 0;
            }
            return false;
        }
        stuck = 0;
        ItemStack stack = solace.inventory.getItem(slot);
        BlockState state = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
        if (!level.isUnobstructed(state, pos, CollisionContext.empty())) {
            index++; // someone is standing there
            return false;
        }
        solace.getLookControl().setLookAt(c.x, c.y, c.z);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
        solace.swing(InteractionHand.MAIN_HAND);
        stack.shrink(1);
        placed++;
        index++;
        cooldown = 4;
        return false;
    }

    private static boolean canPlace(ServerLevel level, BlockPos pos) {
        return level.isInWorldBounds(pos) && level.getBlockState(pos).canBeReplaced();
    }

    /** Uses the preferred block if Solace has it, otherwise whichever full block it has the most of. */
    private int findBlockSlot(SolaceEntity solace) {
        int best = -1;
        int bestCount = 0;
        for (int i = 0; i < solace.inventory.getContainerSize(); i++) {
            ItemStack s = solace.inventory.getItem(i);
            if (s.isEmpty() || !(s.getItem() instanceof BlockItem bi)) continue;
            Block block = bi.getBlock();
            if (!block.defaultBlockState().isCollisionShapeFullBlock(solace.level(), solace.blockPosition())) continue;
            if (block.defaultBlockState().hasBlockEntity()) continue;
            if (preferred != null && block == preferred) return i;
            if (s.getCount() > bestCount) {
                best = i;
                bestCount = s.getCount();
            }
        }
        return best;
    }

    @Override
    public String describe() {
        return "building a " + structure + " (" + placed + "/" + plan.size() + " blocks)";
    }
}
