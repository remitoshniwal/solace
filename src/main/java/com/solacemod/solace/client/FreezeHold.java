package com.solacemod.solace.client;

import com.solacemod.solace.Solace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * While this game is told the world is frozen (Solace's chapter 6 freeze), things that aren't alive stay put too.
 * Mobs and rain already stop by themselves, but dropped items, arrows, falling blocks and so on would keep moving
 * when only this player is frozen, because the server keeps sending where they are. This holds each one where it was
 * when the freeze began (or where it appeared), and lets it jump to where it really is when the freeze ends.
 * Things carrying a player (a boat someone is in) are left alone, so nobody's ride gets stuck.
 */
@EventBusSubscriber(modid = Solace.MODID, value = Dist.CLIENT)
public final class FreezeHold {
    /** Each held thing -> where it's shown. */
    private static final Map<Entity, Vec3> HELD = new HashMap<>();
    private static ClientLevel heldIn;

    private FreezeHold() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        hold();
    }

    /** Again just before each frame, after any position updates from the server arrived. */
    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        hold();
    }

    private static void hold() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != heldIn) {
            HELD.clear(); // a new world: nothing to put back
            heldIn = level;
        }
        if (level == null) return;
        // Frozen and not being stepped forward with /tick step.
        if (level.tickRateManager().runsNormally()) {
            if (!HELD.isEmpty()) release();
            return;
        }
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof LivingEntity || e.hasPassenger(p -> p instanceof Player)) continue;
            Vec3 at = HELD.computeIfAbsent(e, k -> e.position());
            if (!e.position().equals(at)) e.setPos(at);
            e.xo = e.xOld = at.x;
            e.yo = e.yOld = at.y;
            e.zo = e.zOld = at.z;
        }
    }

    /** The freeze is over: everything jumps to where the server last said it is. */
    private static void release() {
        for (Entity e : HELD.keySet()) {
            if (e.isRemoved()) continue;
            Vec3 real = e.getPositionCodec().decode(0, 0, 0);
            e.setPos(real);
            e.setOldPosAndRot();
        }
        HELD.clear();
    }
}
