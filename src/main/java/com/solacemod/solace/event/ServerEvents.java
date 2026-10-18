package com.solacemod.solace.event;

import com.solacemod.solace.Solace;
import com.solacemod.solace.command.SolaceCommand;
import com.solacemod.solace.horror.HorrorDirector;
import com.solacemod.solace.story.PlayerStory;
import com.solacemod.solace.story.Scheduler;
import com.solacemod.solace.story.SolaceBrain;
import com.solacemod.solace.story.SolaceManager;
import com.solacemod.solace.story.StoryData;
import com.solacemod.solace.story.StoryDirector;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Hooks Solace into the game: chat, ticks, joining/leaving, deaths and commands. Server side only. */
@EventBusSubscriber(modid = Solace.MODID)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        SolaceCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        SolaceBrain.onPlayerChat(event.getPlayer(), event.getRawText());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            StoryDirector.onLogin(player, StoryData.get(player.getServer()).get(player.getUUID()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            com.solacemod.solace.horror.Phenomena.restore(player);
            PlayerStory story = StoryData.get(player.getServer()).getIfPresent(player.getUUID());
            if (story != null) StoryDirector.onLogout(player, story);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;
        PlayerStory story = StoryData.get(player.getServer()).getIfPresent(player.getUUID());
        if (story == null || !story.met) return;
        story.deaths++;
        String how = event.getSource().getLocalizedDeathMessage(player).getString();
        story.addEvent("the player died (" + how + ")");
        StoryDirector.onPlayerDeath(player, story);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isEndConquered() || player.getServer() == null) return;
        PlayerStory story = StoryData.get(player.getServer()).getIfPresent(player.getUUID());
        if (story == null || !story.met || story.bossActive) return;
        Scheduler.later(40, () -> SolaceBrain.event(player, "The player just died and respawned (death #" + story.deaths + "). React.", true));
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AdvancementHolder holder = event.getAdvancement();
        holder.value().display().ifPresent(display -> {
            if (!display.shouldAnnounceChat()) return;
            String title = display.getTitle().getString();
            SolaceBrain.event(player, "The player just earned the advancement \"" + title + "\". Comment briefly.", false);
        });
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Scheduler.tick();
        int tick = server.getTickCount();
        if (tick % 20 != 0) return;
        SolaceBrain.tickThinking(server);
        StoryData data = StoryData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerStory story = data.get(player.getUUID());
            try {
                StoryDirector.tick(player, story);
                if (story.met) {
                    if (tick % 40 == 0) SolaceManager.tick(player, story);
                    HorrorDirector.tick(player, story);
                    com.solacemod.solace.horror.OnItsOwn.tickSleep(player, story);
                    com.solacemod.solace.horror.OnItsOwn.tickGifts(player, story);
                    AmbientChatter.tick(player, story);
                }
            } catch (RuntimeException e) {
                Solace.LOGGER.error("Solace tick failed for {}", player.getGameProfile().getName(), e);
            }
        }
        if (tick % (20 * 30) == 0) data.setDirty();
    }

    @SubscribeEvent
    public static void onWake(net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;
        PlayerStory story = StoryData.get(player.getServer()).getIfPresent(player.getUUID());
        if (story != null && story.met) com.solacemod.solace.horror.OnItsOwn.onWake(player, story);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        Scheduler.clear();
        com.solacemod.solace.horror.WorldFreeze.unfreeze(event.getServer());
        com.solacemod.solace.horror.OnItsOwn.reset();
        SolaceBrain.reset();
        StoryDirector.reset();
    }
}
