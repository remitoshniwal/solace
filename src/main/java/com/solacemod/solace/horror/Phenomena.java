package com.solacemod.solace.horror;

import com.solacemod.solace.story.Scheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Things Solace does to the world (and to you) that are impossible for a normal mob. Never harmful: everything is undone.
 */
public final class Phenomena {
    private record Saved(ServerLevel level, Vec3 pos, float yRot, float xRot) {
    }

    /** Players currently "displaced" (mid-Fall), so they can be put back if anything goes wrong. */
    private static final Map<UUID, Saved> DISPLACED = new HashMap<>();

    private Phenomena() {
    }

    /**
     * THE FALL: the player is suddenly near the top of the world, falling, for {@code ticks} ticks
     * (6 ticks = a third of a second), then put back exactly where they were, as if nothing happened. No damage.
     */
    public static boolean fall(ServerPlayer player, int ticks) {
        if (DISPLACED.containsKey(player.getUUID()) || player.isPassenger() || player.isSleeping() || !player.isAlive() || player.isSpectator()) return false;
        ServerLevel level = player.serverLevel();
        Saved saved = new Saved(level, player.position(), player.getYRot(), player.getXRot());
        DISPLACED.put(player.getUUID(), saved);
        double top = level.getMaxBuildHeight() - 4;
        player.teleportTo(level, player.getX(), top, player.getZ(), player.getYRot(), 70F); // looking down at the world far below
        player.setDeltaMovement(Vec3.ZERO);
        Scheduler.later(Math.max(2, ticks), () -> restore(player));
        return true;
    }

    /** Puts a displaced player back exactly where they were. Safe to call any time. */
    public static void restore(ServerPlayer player) {
        Saved s = DISPLACED.remove(player.getUUID());
        if (s == null) return;
        player.teleportTo(s.level(), s.pos().x, s.pos().y, s.pos().z, s.yRot(), s.xRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0;
    }

    public static boolean isDisplaced(ServerPlayer player) {
        return DISPLACED.containsKey(player.getUUID());
    }
}
