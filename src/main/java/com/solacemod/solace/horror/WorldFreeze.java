package com.solacemod.solace.horror;

import net.minecraft.server.MinecraftServer;

/**
 * Stops the world (like /tick freeze): mobs stop mid-step, rain hangs in the air. Players can still look around.
 * Only used when one player is online, so it never freezes someone else's game.
 */
public final class WorldFreeze {
    private static boolean frozenByUs;

    private WorldFreeze() {
    }

    public static boolean freeze(MinecraftServer server) {
        if (server.getPlayerList().getPlayerCount() != 1 || server.tickRateManager().isFrozen()) return false;
        server.tickRateManager().setFrozen(true);
        frozenByUs = true;
        return true;
    }

    /** Safe to call any time. Only unfreezes if Solace froze it. */
    public static void unfreeze(MinecraftServer server) {
        if (!frozenByUs) return;
        frozenByUs = false;
        server.tickRateManager().setFrozen(false);
    }
}
