package com.solacemod.solace.event;

import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.story.PlayerStory;
import com.solacemod.solace.story.SolaceBrain;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Lets Solace speak up on its own sometimes: night falling, low health, deep caves, or just a quiet moment. */
final class AmbientChatter {
    private AmbientChatter() {
    }

    static void tick(ServerPlayer player, PlayerStory story) {
        if (!SolaceConfig.AMBIENT_CHATTER.get() || !story.solaceShouldExist() || story.bossActive) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        long now = server.getTickCount();
        long gap = SolaceConfig.AMBIENT_CHATTER_MINUTES.get() * 1200L;
        if (now - story.lastInteractionTick < gap || now - story.lastAmbientTick < gap) return;

        ServerLevel level = player.serverLevel();
        long time = level.getDayTime() % 24000;
        String situation;
        if (player.getHealth() <= 6) situation = "The player is badly hurt (health " + Math.round(player.getHealth()) + "/20). Say something worried.";
        else if (time >= 12500 && time < 13500) situation = "The sun is setting and night is coming.";
        else if (player.getBlockY() < 30 && !level.canSeeSky(player.blockPosition())) situation = "The player is deep underground in the dark.";
        else if (level.isThundering()) situation = "There's a thunderstorm.";
        else if (player.getRandom().nextFloat() < 0.5F) situation = "It's been quiet for a while. Say something on your own: a thought, a question for the player, or something you notice.";
        else {
            story.lastAmbientTick = now - gap / 2; // try again a bit later
            return;
        }
        story.lastAmbientTick = now;
        SolaceBrain.event(player, situation, false);
    }
}
