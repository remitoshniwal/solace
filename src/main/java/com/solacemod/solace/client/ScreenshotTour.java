package com.solacemod.solace.client;

import com.solacemod.solace.Solace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-only "photo tour", used by the automatic build to take real screenshots of Solace's forms.
 * Only runs when the game is started with -Dsolace.screenshots=true (players never see it):
 * it makes a flat creative world, waits for Solace to appear, poses it, takes pictures, and quits.
 */
@EventBusSubscriber(modid = Solace.MODID, value = Dist.CLIENT)
public final class ScreenshotTour {
    private static final boolean ENABLED = Boolean.getBoolean("solace.screenshots");
    private static boolean worldRequested;
    private static int ticks;
    private static int step;
    private static int wait;

    /** One stop on the tour: commands to run, how long to wait, then the picture to take (null = none). */
    private record Stop(List<String> commands, int waitTicks, String shot) {
    }

    private static List<Stop> stops;

    private ScreenshotTour() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen)) {
                worldRequested = true;
                createWorld(mc);
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        mc.options.hideGui = true;
        ticks++;
        // Solace appears 30 seconds after the player joins.
        if (ticks < 20 * 36) return;
        if (stops == null) stops = plan(mc.player.getGameProfile().getName());
        if (wait > 0) {
            wait--;
            return;
        }
        if (step >= stops.size()) {
            Solace.LOGGER.info("[screenshots] tour finished");
            mc.stop();
            return;
        }
        Stop stop = stops.get(step);
        if (stop.shot() != null && stop.commands().isEmpty()) {
            Screenshot.grab(mc.gameDirectory, stop.shot(), mc.getMainRenderTarget(), msg -> Solace.LOGGER.info("[screenshots] {}", msg.getString()));
            step++;
            return;
        }
        for (String command : stop.commands()) {
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
        }
        wait = stop.waitTicks();
        // Turn "commands, then picture" into two stops: the picture is taken after the wait.
        stops.set(step, new Stop(List.of(), 0, stop.shot()));
        if (stop.shot() == null) step++;
    }

    private static List<Stop> plan(String me) {
        String solace = "@e[type=solace:solace,limit=1]";
        List<Stop> list = new ArrayList<>();
        list.add(new Stop(List.of("gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "weather clear", "time set noon",
                "tp " + me + " 0.5 -60 0.5 0 8", "data merge entity " + solace + " {NoAI:1b}",
                "tp " + solace + " 0.5 -60 4.0 180 0"), 40, "1_normal_day.png"));
        list.add(new Stop(List.of("time set midnight"), 30, "2_normal_night.png"));
        list.add(new Stop(List.of("tp " + me + " 3.2 -60 2.0 60 8"), 20, "3_normal_side_night.png"));
        list.add(new Stop(List.of("tp " + me + " 0.5 -60 0.5 0 8", "solace admin negative " + me), 30, "4_negative_night.png"));
        list.add(new Stop(List.of("time set noon"), 20, "5_negative_day.png"));
        list.add(new Stop(List.of("solace admin chapter " + me + " 7", "time set midnight"), 220, null)); // let the negative wear off
        list.add(new Stop(List.of("tp " + solace + " 0.5 -60 4.0 180 0", "tp " + me + " 0.5 -60 1.5 0 15"), 30, "6_veins_closeup_night.png"));
        list.add(new Stop(List.of("tp " + me + " 0.5 -60 -0.5 0 -4", "solace admin lantern " + me), 40, "7_lantern_night.png"));
        list.add(new Stop(List.of("gamerule doDaylightCycle false"), 25, "8_lantern_night_later.png")); // a moment later: the blobs have moved
        list.add(new Stop(List.of("time set noon"), 20, "9_lantern_day.png"));
        return list;
    }

    private static void createWorld(Minecraft mc) {
        try {
            GameRules rules = new GameRules();
            LevelSettings settings = new LevelSettings("solace_tour", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("solace_tour", settings, new WorldOptions(1234L, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    new TitleScreen());
            Solace.LOGGER.info("[screenshots] creating the tour world");
        } catch (RuntimeException e) {
            Solace.LOGGER.error("[screenshots] could not create the tour world", e);
        }
    }
}
