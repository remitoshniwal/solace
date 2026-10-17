package com.solacemod.solace.entity.task;

import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.core.BlockPos;

/** Walks to a spot. */
public final class GoToTask implements SolaceTask {
    private final BlockPos destination;
    private final String name;
    private int age;
    private int retries;

    public GoToTask(BlockPos destination, String name) {
        this.destination = destination;
        this.name = name;
    }

    @Override
    public boolean tick(SolaceEntity solace) {
        if (solace.blockPosition().distSqr(destination) < 2.5 * 2.5) {
            TaskUtil.report(solace, "I'm at " + name + ".");
            return true;
        }
        if (++age > 20 * 120) {
            TaskUtil.report(solace, "I couldn't find a way to " + name + ".");
            return true;
        }
        if (solace.getNavigation().isDone()) {
            if (!solace.getNavigation().moveTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5, 1.15) && ++retries > 5) {
                TaskUtil.report(solace, "I can't get to " + name + " from here.");
                return true;
            }
        }
        return false;
    }

    @Override
    public String describe() {
        return "walking to " + name;
    }
}
