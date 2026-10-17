package com.solacemod.solace.entity.task;

import com.solacemod.solace.entity.SolaceEntity;
import com.solacemod.solace.story.SolaceBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.pathfinder.Path;

final class TaskUtil {
    private TaskUtil() {
    }

    /** Solace tells its owner how a job went (pre-written, no AI request). */
    static void report(SolaceEntity solace, String text) {
        ServerPlayer owner = solace.getServerOwner();
        if (owner != null) SolaceBrain.sayScripted(owner, solace, text, "calm");
    }

    /** Starts walking toward a block. Returns false if there's no way to get there. */
    static boolean walkTo(SolaceEntity solace, BlockPos pos, double speed) {
        Path path = solace.getNavigation().createPath(pos, 1);
        if (path == null) return false;
        return solace.getNavigation().moveTo(path, speed);
    }
}
