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
 * With -Dsolace.tour=playtest it runs the playtest commands instead (/solace note, the playtest log, advancements, the price, a page hint)
 * and takes one picture at the end. With -Dsolace.tour=phase2 it takes pictures of the bond hint, the toasts and the advancements tab.
 * With -Dsolace.tour=sounds it plays each of Solace's own sounds with subtitles on, takes a picture of each subtitle,
 * and writes every solace: sound that played to solace/sounds-tour.txt (pictures can't show how a sound sounds, only that it played).
 * With -Dsolace.tour=together it tries the multiplayer moments alone (a stand-in plays the other player) with the playtest log on,
 * and takes pictures of the Together setup tab, two Solaces meeting, the lines players get, the personal freeze and the boss bar.
 * With -Dsolace.tour=afterward it fakes past endings in the memory and takes pictures of what comes after an ending: each remembered
 * meeting, the slip, the first Watcher, the all-five choice, the reset lines, and "your story here is over" (pictures a_*.png).
 * It runs with no AI, so the pictures show the written lines. At the end it clears the faked endings from the memory.
 * With -Dsolace.tour=dates it moves the player's clock to Halloween, the new year and 3 a.m., and back after a long time away,
 * in each tone of the story (pictures d_*.png, written lines, no AI). Timeline in solace/playtest/.
 */
@EventBusSubscriber(modid = Solace.MODID, value = Dist.CLIENT)
public final class ScreenshotTour {
    private static final boolean PLAYTEST = "playtest".equals(System.getProperty("solace.tour"));
    private static final boolean PHASE2 = "phase2".equals(System.getProperty("solace.tour"));
    private static final boolean SOUNDS = "sounds".equals(System.getProperty("solace.tour"));
    private static final boolean TOGETHER = "together".equals(System.getProperty("solace.tour"));
    private static final boolean AFTERWARD = "afterward".equals(System.getProperty("solace.tour"));
    private static final boolean DATES = "dates".equals(System.getProperty("solace.tour"));
    private static final boolean ENABLED = Boolean.getBoolean("solace.screenshots") || PLAYTEST || PHASE2 || SOUNDS || TOGETHER || AFTERWARD || DATES;
    /** Together tour: the "AI comments" setting before the tour turned it off, put back at the end. */
    private static Boolean aiCommentsBefore;
    /** For the sounds tour: every solace: sound that played, in order. */
    private static final List<String> HEARD = new ArrayList<>();
    private static boolean worldRequested;
    private static int ticks;
    private static int step;
    /** Server ticks left before the next stop. Counted on the server's clock: it can run at half speed during a tour. */
    private static int wait;
    /** The server's tick count when this window last looked, to see how far the server got since. */
    private static int lastServerTick = -1;
    /** Client ticks since Solace first showed up (the tour starts a little after). */
    private static int sinceSolace = -1;

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
        mc.options.hideGui = !PLAYTEST && !PHASE2 && !SOUNDS && !TOGETHER && !AFTERWARD && !DATES;
        mc.options.pauseOnLostFocus = false; // clicking another window must not pause the tour's world
        ticks++;
        // Solace appears 30 seconds (of server time) after the player joins. Start once it's really here, plus 3 seconds to settle.
        if (sinceSolace < 0) {
            boolean here = false;
            for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) if (e instanceof com.solacemod.solace.entity.SolaceEntity) here = true;
            if (here) sinceSolace = 0;
            else if (ticks == 20 * 180) Solace.LOGGER.warn("[screenshots] Solace hasn't shown up after 3 minutes; starting anyway");
            if (!here && ticks < 20 * 180) return;
            if (!here) sinceSolace = 0;
        }
        if (sinceSolace++ < 60) return;
        // How far the server got since the last look. When the game is paused (a menu that pauses), the server stops, so count this window's ticks instead.
        int serverTick = server.getTickCount();
        int passed = lastServerTick < 0 || mc.isPaused() ? 1 : Math.max(0, serverTick - lastServerTick);
        lastServerTick = serverTick;
        if (stops == null) {
            String me = mc.player.getGameProfile().getName();
            stops = PLAYTEST ? playtestPlan(me) : PHASE2 ? phase2Plan(me) : SOUNDS ? soundsPlan(me) : TOGETHER ? togetherPlan(me)
                    : AFTERWARD ? afterwardPlan(me) : DATES ? datesPlan(me) : plan(me);
        }
        if (wait > 0) {
            wait -= passed;
            if (wait > 0) return;
        }
        if (step >= stops.size()) {
            Solace.LOGGER.info("[screenshots] tour finished");
            if (SOUNDS) writeHeard(mc);
            mc.stop();
            return;
        }
        Stop stop = stops.get(step);
        if (stop.shot() != null && stop.commands().isEmpty()) {
            if (TOGETHER && stop.shot().startsWith("t_8")) logFreeze(mc, stop.shot());
            Screenshot.grab(mc.gameDirectory, stop.shot(), mc.getMainRenderTarget(), msg -> Solace.LOGGER.info("[screenshots] {}", msg.getString()));
            step++;
            return;
        }
        for (String command : stop.commands()) {
            if (command.startsWith("#suggest ")) logSuggestions(server, mc.player.getUUID(), command.substring(9));
            else if (command.startsWith("#")) clientAction(mc, command); // things only the game window can do
            else if (command.startsWith("/")) mc.player.connection.sendCommand(command.substring(1)); // typed by the player, so the answer shows in chat
            else server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
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
        list.add(new Stop(List.of("tp " + solace + " 0.5 -60 4.0 180 0", "tp " + me + " 0.5 -60 1.5 0 15"), 30, "6_closeup_night.png"));
        list.add(new Stop(List.of("time set noon"), 20, "6b_closeup_day.png")); // chapter 7: the uneasy form, cracked
        list.add(new Stop(List.of("time set midnight", "tp " + me + " 0.5 -60 1.3 0 14", "solace admin lantern " + me), 40, "7_lantern_night.png"));
        list.add(new Stop(List.of("gamerule doDaylightCycle false"), 25, "8_lantern_night_later.png")); // a moment later: the blobs have moved
        list.add(new Stop(List.of("time set noon"), 20, "9_lantern_day.png"));
        return list;
    }

    /** Tries every playtest tool once. Check run/solace/playtest/ afterwards. */
    private static List<Stop> playtestPlan(String me) {
        String asMe = "execute as " + me + " run ";
        List<Stop> list = new ArrayList<>();
        list.add(new Stop(List.of(asMe + "solace note a note before the log is on"), 20, null));
        list.add(new Stop(List.of("solace admin playtest on"), 20, null));
        list.add(new Stop(List.of("solace admin chapter " + me + " 3"), 60, null));
        list.add(new Stop(List.of("solace admin horror " + me + " footsteps"), 40, null));
        list.add(new Stop(List.of(asMe + "solace say hello, can you hear me?"), 60, null));
        list.add(new Stop(List.of("solace admin fragment " + me + " 2"), 20 * 40, null)); // time for chapter 3's first scenes
        list.add(new Stop(List.of(asMe + "solace note found page two, testing"), 20, null));
        // Phase 2: the price (expect PRICE and ADVANCEMENT first_favour), debt 30 (ADVANCEMENT owed), a page left on the ground (NUDGE after 60 s).
        list.add(new Stop(List.of("solace admin bond " + me + " 1000 0", "solace admin power " + me + " heal"), 20 * 5, null));
        list.add(new Stop(List.of("solace admin bond " + me + " 30 35"), 40, null));
        list.add(new Stop(List.of("solace admin fragment " + me + " 5 ground"), 20 * 70, null));
        list.add(new Stop(List.of("solace admin playtest off"), 20, null));
        list.add(new Stop(List.of("solace admin horror " + me + " footsteps", asMe + "solace note a note after the log is off"), 30, "playtest_end.png"));
        return list;
    }

    /** Pictures for Phase 2 (1.7.0): the bond hint next to the hotbar, the toasts, the advancements tab. The GUI stays on. */
    private static List<Stop> phase2Plan(String me) {
        String solace = "@e[type=solace:solace,limit=1]";
        List<Stop> list = new ArrayList<>();
        // No "[Server: ...]" lines or tutorial pop-up in the pictures, so only Solace's own messages and toasts show.
        list.add(new Stop(List.of("#quiet", "gamerule logAdminCommands false", "gamerule sendCommandFeedback false",
                "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "weather clear", "time set noon",
                "tp " + me + " 0.5 -60 0.5 0 8", "data merge entity " + solace + " {NoAI:1b}",
                "tp " + solace + " 0.5 -60 4.0 180 0", "solace admin bond " + me + " 4 0"), 160, "p2_1_hint_low.png"));
        list.add(new Stop(List.of("solace admin bond " + me + " 1000 0"), 160, "p2_2_hint_full.png"));
        list.add(new Stop(List.of("solace admin power " + me + " heal"), 30, "p2_3_price_toast.png"));
        list.add(new Stop(List.of("solace admin bond " + me + " 1000 65"), 40, "p2_4_owed_toast.png"));
        list.add(new Stop(List.of("time set midnight"), 160, "p2_5_hint_debt_night.png"));
        list.add(new Stop(List.of("time set noon", "item replace entity " + me + " weapon.offhand with minecraft:torch"), 30, "p2_5b_offhand.png"));
        list.add(new Stop(List.of("item replace entity " + me + " weapon.offhand with minecraft:air"), 10, null));
        list.add(new Stop(List.of("solace admin bond " + me + " 1000 0", "time set noon",
                "advancement grant " + me + " only solace:story/knew_my_name", "advancement grant " + me + " only solace:story/first_page"), 30, "p2_6_page_toast.png"));
        list.add(new Stop(List.of("#advancements"), 40, "p2_7_advancements.png"));
        list.add(new Stop(List.of("#close"), 10, null));
        // 1.10.1: Tab after /solace choose lists only what's on offer (nothing, then the three, then the secret one only with trust).
        list.add(new Stop(List.of("#suggest /solace choose ", "solace admin trust " + me + " 0", "solace admin question " + me), 20 * 80, null));
        list.add(new Stop(List.of("#suggest /solace choose "), 20, "p2_8_question.png"));
        list.add(new Stop(List.of("#clear_chat", "/solace choose together"), 20, "p2_9_choose_hidden.png")); // answers like any unknown word
        list.add(new Stop(List.of("solace admin trust " + me + " 100", "#suggest /solace choose "), 20, null));
        // 1.10.2: the help, the status and the Story page.
        list.add(new Stop(List.of("gamerule sendCommandFeedback true", "#clear_chat", "/solace help"), 20, "p2_10_help.png"));
        list.add(new Stop(List.of("#clear_chat", "/solace status"), 20, "p2_11_status.png"));
        list.add(new Stop(List.of("#clear_chat", "#setup_story", "#mouse_corner"), 30, "p2_12_setup_story.png"));
        list.add(new Stop(List.of("#close"), 10, null));
        return list;
    }

    /** Every one of Solace's own sounds, one after the other, each with a picture of its subtitle. The GUI stays on. */
    private static List<Stop> soundsPlan(String me) {
        String solace = "@e[type=solace:solace,limit=1]";
        List<Stop> list = new ArrayList<>();
        list.add(new Stop(List.of("#quiet", "#subtitles", "gamerule logAdminCommands false", "gamerule sendCommandFeedback false",
                "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "weather clear", "time set noon",
                "tp " + me + " 0.5 -60 0.5 0 8", "data merge entity " + solace + " {NoAI:1b}",
                "tp " + solace + " 0.5 -60 4.0 180 0", "solace admin chapter " + me + " 1"), 100, "snd_1_hum.png"));
        list.add(new Stop(List.of("solace admin chapter " + me + " 7"), 120, "snd_2_hum_uneasy.png"));
        list.add(new Stop(List.of("solace admin presence " + me + " flicker"), 50, "snd_3_flicker.png"));
        list.add(new Stop(List.of("solace admin negative " + me), 25, "snd_4_negative_surge.png"));
        // A stop needs a command, or its picture is taken straight away. "time set noon" changes nothing here.
        list.add(new Stop(List.of("time set noon"), 20 * 10 + 20, "snd_5_negative_fade.png")); // the negative wears off after 10 seconds
        list.add(new Stop(List.of("solace admin lantern " + me), 60, "snd_6_lantern_drone.png"));
        list.add(new Stop(List.of("time set noon"), 20 * 10, null)); // the Lantern ends
        list.add(new Stop(List.of("solace admin horror " + me + " watcher"), 30, "snd_7_watcher_near.png"));
        list.add(new Stop(List.of("solace admin sound " + me + " watcher_vanish"), 20, "snd_8_watcher_vanish.png"));
        list.add(new Stop(List.of("solace admin bond " + me + " 4 0"), 80, null));
        list.add(new Stop(List.of("solace admin bond " + me + " 1000 0"), 30, "snd_9_bond_shift.png"));
        list.add(new Stop(List.of("solace admin sound " + me + " page_rustle"), 20, "snd_10_page_rustle.png"));
        return list;
    }

    /** What comes after an ending, with faked memories (no AI: the written lines show). Timeline in run/solace/playtest/. */
    private static List<Stop> afterwardPlan(String me) {
        String solace = "@e[type=solace:solace,limit=1]";
        String memory = "solace admin memory " + me + " ";
        String after = "solace admin afterward " + me + " ";
        String meetSpot = "tp " + solace + " 0.5 -60 4.0 180 0";
        List<Stop> list = new ArrayList<>();
        // Waits are generous: during the tour the game's server can run at half speed, and a line must arrive before its picture.
        list.add(new Stop(List.of("#quiet", "gamerule logAdminCommands false", "gamerule sendCommandFeedback true",
                "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "weather clear", "time set noon",
                "solace admin playtest on", "tp " + me + " 0.5 -60 0.5 0 8", meetSpot), 160, null)); // let the first meeting finish
        // The meeting in a new world, once for each remembered ending.
        int n = 1;
        for (String e : List.of("forever", "collected", "erase", "release", "together")) {
            list.add(new Stop(List.of(memory + "clear", memory + "ending " + e, "#clear_chat", meetSpot, after + "meet"), 60, "a_1" + (char) ('a' + n - 1) + "_meet_" + e + ".png"));
            n++;
        }
        // The same world after a reset.
        list.add(new Stop(List.of(memory + "clear", memory + "ending erase", "#clear_chat", after + "meet_here"), 60, "a_2_meet_here_erase.png"));
        // Chapter 3's slip and chapter 4's first Watcher: once where Maren stayed trapped, once where she was freed.
        for (String e : List.of("erase", "together")) {
            list.add(new Stop(List.of(memory + "clear", memory + "ending " + e, after + "meet"), 60, null));
            list.add(new Stop(List.of("#clear_chat", after + "slip"), 60, "a_3_slip_" + e + ".png"));
            list.add(new Stop(List.of("#clear_chat", "tp " + me + " 0.5 -60 0.5 0 8", after + "watcher"), 260, "a_4_watcher_" + e + ".png"));
            list.add(new Stop(List.of("kill @e[type=solace:watcher]"), 60, null));
        }
        list.add(new Stop(List.of(after + "question"), 20, null)); // AI only: only the timeline shows it was asked
        // All five endings found: the choice, then [Just stay].
        list.add(new Stop(List.of(memory + "all", "#clear_chat", after + "meet"), 200, "a_5_all_five.png"));
        list.add(new Stop(List.of("/solace choose just_stay"), 60, "a_6_just_stay.png"));
        list.add(new Stop(List.of("#clear_chat", "/solace status"), 60, "a_7_status_companion.png"));
        // Reset after an ending: back to a fresh story, the FOREVER ending, the warning, the line, then the meeting that knows.
        list.add(new Stop(List.of(memory + "clear", "/solace reset confirm"), 20 * 60, null));
        list.add(new Stop(List.of(meetSpot, "solace admin ending " + me + " forever"), 300, null));
        list.add(new Stop(List.of("#clear_chat", "/solace reset"), 60, "a_8_reset_warning.png"));
        list.add(new Stop(List.of("/solace reset confirm"), 60, "a_9_reset_watching.png"));
        list.add(new Stop(List.of("#clear_chat"), 20 * 60, null));
        list.add(new Stop(List.of("#open_chat"), 10, "a_10_meet_after_reset.png")); // open chat: the line may have faded by now
        list.add(new Stop(List.of("#close"), 10, null));
        // It's over: ERASE, then what each login says, talking to nobody, the echoes and /solace status.
        list.add(new Stop(List.of("#clear_chat", "solace admin ending " + me + " erase"), 80, "a_11_ending_erase.png"));
        list.add(new Stop(List.of("#clear_chat", after + "login"), 60, "a_12_over_erase.png"));
        list.add(new Stop(List.of("/solace say hello? are you there?"), 60, "a_13_nobody_answers.png"));
        for (int i = 0; i < 6; i++) list.add(new Stop(List.of(after + "echo"), 20 * 3, null)); // 4 sightings, the last sign, then nothing
        list.add(new Stop(List.of("kill @e[type=solace:watcher]", "tp " + me + " 0.5 -60 0.5 180 30"), 60, "a_14_last_sign.png")); // turn around: the sign is behind
        list.add(new Stop(List.of("#clear_chat", "/solace status"), 60, "a_15_status_over.png"));
        // RELEASE: a fresh story, the ending, the login line.
        list.add(new Stop(List.of("/solace reset confirm", "tp " + me + " 20.5 -60 0.5 0 8"), 20 * 60, null));
        list.add(new Stop(List.of("solace admin ending " + me + " release"), 300, null));
        list.add(new Stop(List.of("#clear_chat", after + "login"), 60, "a_16_over_release.png"));
        list.add(new Stop(List.of(memory + "clear"), 20, null)); // don't leave faked endings in the dev memory
        return list;
    }

    /** 1.12.0: every real-date moment in each tone, with a faked clock (no AI: the written lines show). */
    private static List<Stop> datesPlan(String me) {
        String solace = "@e[type=solace:solace,limit=1]";
        String date = "solace admin date " + me + " ";
        String near = "tp " + solace + " 0.5 -60 4.0 180 0";
        List<Stop> list = new ArrayList<>();
        list.add(new Stop(List.of("#quiet", "gamerule logAdminCommands false", "gamerule sendCommandFeedback false",
                "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "weather clear", "time set noon",
                "solace admin playtest on", "tp " + me + " 0.5 -60 0.5 0 8", near, "data merge entity " + solace + " {NoAI:1b}"), 60, null));
        list.add(new Stop(List.of("#setup_abilities", "#mouse_corner"), 30, "d_0_setup.png"));
        list.add(new Stop(List.of("#close"), 10, null));
        // One picture per tone: the four moments one after the other, so all four lines are in the chat.
        String[][] tones = {{"early", "solace admin chapter " + me + " 1"}, {"middle", "solace admin chapter " + me + " 3"},
                {"late", "solace admin chapter " + me + " 5"}, {"kept", "solace admin ending " + me + " forever"}};
        int n = 1;
        for (String[] tone : tones) {
            list.add(new Stop(List.of(tone[1]), tone[0].equals("kept") ? 300 : 60, null));
            list.add(new Stop(List.of("#clear_chat", near, date + "forget", date + "set 2026-10-31 20:00"), 60, null));
            list.add(new Stop(List.of(date + "set 2027-01-01 00:01"), 60, null));
            list.add(new Stop(List.of(date + "set 2026-11-02 03:05"), 60, null));
            list.add(new Stop(List.of(date + "away 12"), 40, null));
            list.add(new Stop(List.of("#open_chat"), 10, "d_" + n++ + "_" + tone[0] + ".png")); // open chat: the first lines fade after 10 seconds
            list.add(new Stop(List.of("#close"), 10, null));
        }
        // Healed: a fresh story that ends in TOGETHER. 3 a.m. stays quiet there (it already tells them to sleep).
        list.add(new Stop(List.of("/solace reset confirm", "data merge entity " + solace + " {NoAI:0b}"), 20 * 60, null));
        list.add(new Stop(List.of(near, "data merge entity " + solace + " {NoAI:1b}", "solace admin ending " + me + " together"), 300, null));
        list.add(new Stop(List.of("#clear_chat", near, date + "forget", date + "set 2026-10-31 20:00"), 60, null));
        list.add(new Stop(List.of(date + "set 2027-01-01 00:01"), 60, null));
        list.add(new Stop(List.of(date + "set 2026-11-02 03:05"), 60, null));
        list.add(new Stop(List.of(date + "away 12"), 40, null));
        list.add(new Stop(List.of("#open_chat"), 10, "d_5_friend.png"));
        list.add(new Stop(List.of("#close"), 10, null));
        // Once only: Halloween again the same year says nothing. Then /solace admin date show.
        list.add(new Stop(List.of("#clear_chat", date + "set 2026-10-31 21:00"), 60, null));
        list.add(new Stop(List.of("gamerule sendCommandFeedback true", "/solace admin date " + me + " show"), 30, "d_6_once_only.png"));
        list.add(new Stop(List.of(date + "clear", "execute as " + me + " run solace reset confirm", "solace admin playtest off"), 20, null));
        return list;
    }

    /**
     * Phase 4 (1.9.0): every multiplayer moment, alone. The other player is a stand-in ("Friend", or a Solace that belongs to nobody).
     * AI comments are switched off for the tour, so the written lines show (there's no AI key in the automatic build anyway).
     * The timeline goes to solace/playtest/.
     */
    private static List<Stop> togetherPlan(String me) {
        String solace = "@e[type=solace:solace,limit=1]";
        String together = "solace admin together " + me + " ";
        List<Stop> list = new ArrayList<>();
        list.add(new Stop(List.of("#quiet", "gamerule logAdminCommands false", "gamerule sendCommandFeedback false",
                "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "weather clear", "time set noon",
                "solace admin playtest on", "solace admin chapter " + me + " 4"), 60, null));
        list.add(new Stop(List.of("#setup_together", "#mouse_corner"), 30, "t_1_setup_tab.png")); // before the tour turns AI comments off
        list.add(new Stop(List.of("#close", "#no_ai_comments"), 10, null));
        // Seen from the side: Solace faces north, the stand-in appears 4 blocks in front of it.
        list.add(new Stop(List.of("tp " + me + " 6.5 -60 2.0 90 10", "tp " + solace + " 0.5 -60 4.0 180 0", "#clear_chat", together + "meet"), 30, "t_2_meet.png"));
        list.add(new Stop(List.of("time set noon"), 60, "t_3_meet_negative.png")); // halfway: both turn negative at once (chapter 3 on)
        list.add(new Stop(List.of("time set noon"), 20 * 8, null)); // the stand-in leaves
        list.add(new Stop(List.of("tp " + me + " 0.5 -60 0.5 0 8", "tp " + solace + " 0.5 -60 4.0 180 0", "solace admin chapter " + me + " 2",
                "#clear_chat", together + "ripple release"), 30, "t_4_ripple_early.png"));
        list.add(new Stop(List.of("solace admin chapter " + me + " 5", "#clear_chat", together + "ripple together"), 30, "t_5_ripple_later.png"));
        list.add(new Stop(List.of("#clear_chat", together + "witness flicker"), 20, "t_6_witness.png"));
        list.add(new Stop(List.of("#clear_chat", together + "jealousy"), 20, "t_7_jealousy.png"));
        // The personal freeze, checked with a red block sliding sideways (no gravity) and the rain. Solace stands aside so it
        // can't hide the block. Without the freeze the block moves about 3 blocks a second; with it, it stays put on this
        // screen (and the rain hangs still) while the server moves it on. When the freeze ends it jumps to where it really is.
        String cow = "summon minecraft:cow -4.5 -60 7.5 {Motion:[0.5d,0.2d,0.0d]}";
        String slider = "summon minecraft:falling_block -6.5 -58 10.5 {BlockState:{Name:\"minecraft:red_wool\"},NoGravity:1b,Time:1,Motion:[0.15d,0.0d,0.0d]}";
        list.add(new Stop(List.of("#clear_chat", "data merge entity " + solace + " {NoAI:1b}", "tp " + solace + " 5.5 -60 6.0 180 0",
                "weather rain", "kill @e[type=minecraft:cow]", slider, cow), 60, "t_8a_moving.png"));
        list.add(new Stop(List.of("time set noon"), 30, "t_8b_moving_later.png"));
        list.add(new Stop(List.of("kill @e[type=minecraft:falling_block]", "kill @e[type=minecraft:cow]", together + "freeze", slider, cow), 60, "t_8c_frozen.png"));
        list.add(new Stop(List.of("time set noon"), 30, "t_8d_frozen_later.png"));
        list.add(new Stop(List.of("weather clear"), 20 * 14, "t_8e_after_freeze.png"));
        // Vanilla /tick freeze and /tick step must still work (FreezeHold lets things move while stepping).
        list.add(new Stop(List.of("kill @e[type=minecraft:falling_block]", "tick freeze", slider), 40, "t_8f_tick_freeze.png"));
        list.add(new Stop(List.of("tick step 20"), 40, "t_8g_tick_step.png"));
        list.add(new Stop(List.of("tick unfreeze"), 10, null));
        list.add(new Stop(List.of("kill @e[type=minecraft:falling_block]", "kill @e[type=minecraft:cow]", "data merge entity " + solace + " {NoAI:0b}"), 5, null));
        list.add(new Stop(List.of("#clear_chat", together + "hollow 2"), 60, "t_9_hollow_bar.png"));
        list.add(new Stop(List.of("gamerule sendCommandFeedback true", "#clear_chat", "/solace admin usage"), 20, "t_10_usage.png"));
        list.add(new Stop(List.of("execute as " + me + " run solace reset confirm", "solace admin playtest off", "#ai_comments_back"), 20, null));
        return list;
    }

    /** Together tour: what this game window thinks at each freeze picture (is it frozen, where is the red block). */
    private static void logFreeze(Minecraft mc, String shot) {
        if (mc.level == null) return;
        StringBuilder blocks = new StringBuilder();
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof net.minecraft.world.entity.item.FallingBlockEntity) blocks.append(String.format(java.util.Locale.ROOT, " x=%.2f", e.getX()));
            if (e instanceof net.minecraft.world.entity.animal.Cow) blocks.append(String.format(java.util.Locale.ROOT, " cow x=%.2f", e.getX()));
        }
        Solace.LOGGER.info("[freeze] {} frozen={} tick={} block{}", shot, mc.level.tickRateManager().isFrozen(), ticks, blocks.length() == 0 ? " none" : blocks);
    }

    /** Writes to the log what Tab would suggest for this half-typed command, asked of the server as this player. */
    private static void logSuggestions(MinecraftServer server, java.util.UUID id, String typed) {
        server.execute(() -> {
            net.minecraft.server.level.ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) return;
            var dispatcher = server.getCommands().getDispatcher();
            var parse = dispatcher.parse(typed.substring(1), p.createCommandSourceStack());
            dispatcher.getCompletionSuggestions(parse).thenAccept(sug -> Solace.LOGGER.info("[suggest] {} -> {}", typed,
                    sug.getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList()));
        });
    }

    /** Sounds tour: notes every solace: sound the game starts playing. */
    @SubscribeEvent
    public static void onSound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
        if (!SOUNDS || event.getSound() == null) return;
        String id = event.getSound().getLocation().toString();
        if (!id.startsWith(Solace.MODID + ":")) return;
        String line = "tick " + ticks + "  " + id + "  step " + step;
        HEARD.add(line);
        Solace.LOGGER.info("[sounds] {}", line);
    }

    private static void writeHeard(Minecraft mc) {
        try {
            java.nio.file.Path file = mc.gameDirectory.toPath().resolve("solace/sounds-tour.txt");
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.write(file, HEARD);
            Solace.LOGGER.info("[sounds] wrote {} sounds to {}", HEARD.size(), file);
        } catch (java.io.IOException e) {
            Solace.LOGGER.error("[sounds] could not write the sounds list", e);
        }
    }

    private static void clientAction(Minecraft mc, String action) {
        if (mc.player == null) return;
        switch (action) {
            case "#advancements" -> {
                net.minecraft.client.multiplayer.ClientAdvancements adv = mc.player.connection.getAdvancements();
                mc.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(adv));
                net.minecraft.advancements.AdvancementHolder root = adv.get(Solace.id("story/root"));
                if (root != null) adv.setSelectedTab(root, true);
            }
            case "#close" -> mc.setScreen(null);
            case "#open_chat" -> mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
            case "#subtitles" -> mc.options.showSubtitles().set(true);
            case "#setup_together" -> mc.setScreen(SolaceSetupScreen.openOnTogether(null));
            case "#setup_story" -> mc.setScreen(SolaceSetupScreen.openOnStory(null));
            case "#setup_abilities" -> mc.setScreen(SolaceSetupScreen.openOnAbilities(null));
            case "#clear_chat" -> mc.gui.getChat().clearMessages(false);
            case "#mouse_corner" -> {
                // Park the mouse in the top left corner, so no button shows its tooltip in the picture.
                try {
                    for (String f : new String[]{"xpos", "ypos"}) {
                        java.lang.reflect.Field field = net.minecraft.client.MouseHandler.class.getDeclaredField(f);
                        field.setAccessible(true);
                        field.setDouble(mc.mouseHandler, 0);
                    }
                } catch (ReflectiveOperationException e) {
                    Solace.LOGGER.warn("[screenshots] could not move the mouse", e);
                }
            }
            case "#no_ai_comments" -> {
                aiCommentsBefore = com.solacemod.solace.config.SolaceConfig.MULTIPLAYER_AI_COMMENTS.get();
                com.solacemod.solace.config.SolaceConfig.MULTIPLAYER_AI_COMMENTS.set(false);
            }
            case "#ai_comments_back" -> {
                if (aiCommentsBefore != null) com.solacemod.solace.config.SolaceConfig.MULTIPLAYER_AI_COMMENTS.set(aiCommentsBefore);
            }
            case "#quiet" -> {
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                mc.getToasts().clear();
            }
            default -> Solace.LOGGER.warn("[screenshots] unknown tour action {}", action);
        }
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
