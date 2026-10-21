package com.solacemod.solace.horror;

import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The chapter 6 freeze for one player, when others are online (the whole-world {@link WorldFreeze} would stop their game too).
 * Only this player's game is told "the world is frozen", so on their screen mobs stop mid-step and rain hangs in the air,
 * while the server and everyone else carry on. Because they can't see what's really moving, they can't be hurt
 * while it lasts, and mobs nearby lose interest in them.
 */
public final class PersonalFreeze {
    /** Player -> server tick when their freeze ends. */
    private static final Map<UUID, Long> FROZEN = new HashMap<>();

    private PersonalFreeze() {
    }

    public static void freeze(ServerPlayer player, int ticks) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        FROZEN.put(player.getUUID(), (long) server.getTickCount() + ticks);
        player.connection.send(new ClientboundTickingStatePacket(server.tickRateManager().tickrate(), true));
    }

    /** Safe to call any time: tells their game the real state again. */
    public static void unfreeze(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (FROZEN.remove(player.getUUID()) == null || server == null) return;
        player.connection.send(ClientboundTickingStatePacket.from(server.tickRateManager()));
    }

    public static boolean isFrozen(ServerPlayer player) {
        return FROZEN.containsKey(player.getUUID());
    }

    /** Runs every second: keeps mobs off frozen players, and ends freezes that ran out (in case the scene was cut short). */
    public static void tick(MinecraftServer server) {
        if (FROZEN.isEmpty()) return;
        long now = server.getTickCount();
        for (UUID id : Map.copyOf(FROZEN).keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) {
                FROZEN.remove(id);
                continue;
            }
            if (now > FROZEN.get(id) + 40) {
                unfreeze(p);
                continue;
            }
            for (Mob mob : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), m -> m.getTarget() == p)) {
                mob.setTarget(null);
            }
        }
    }

    /** The player left: their game will get the real state when they join again. */
    public static void forget(ServerPlayer player) {
        FROZEN.remove(player.getUUID());
    }

    public static void reset() {
        FROZEN.clear();
    }
}
