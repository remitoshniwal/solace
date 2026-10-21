package com.solacemod.solace.story;

import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.horror.HorrorDirector;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * After the ending (1.10.0):
 * - It's over: after RELEASE and ERASE the player is told, quietly, each time they log in, and the last echoes fade out.
 * - The next story remembers: Solace notes how the last one ended when it meets you, and four moments change because of it.
 * - All five endings found: Solace asks what you want this time (the story again, or [Just stay] with no story).
 * All the writing is in StoryText. The numbers below are guesses until the playtest.
 */
public final class Afterward {
    /** Watcher sightings after ERASE before the last sign. */
    public static final int ERASE_ECHOES = 4;
    /** Signs and footsteps after RELEASE before the last sign. */
    public static final int RELEASE_ECHOES = 3;
    /** How long after logging in the "your story here is over" line shows (ticks, 20 = 1 second). */
    public static final int OVER_LINE_DELAY_TICKS = 60;
    /** Minute of chapter 3 when "the slip" plays (the remembered ending leaks out). */
    public static final double SLIP_MINUTE = 7;
    /** Marks a pending all-five choice in the story's done list (saved with the world). */
    static final String CHOICE_PENDING = "after_choice";

    private static final RandomSource RANDOM = RandomSource.create();
    /** Players told "Nobody answers." this session. */
    private static final Set<UUID> NOBODY_TOLD = new HashSet<>();

    private Afterward() {
    }

    public static void reset() {
        NOBODY_TOLD.clear();
    }

    public static void forgetSession(UUID id) {
        NOBODY_TOLD.remove(id);
    }

    /** True if the story ended in a way that took Solace out of this world. */
    public static boolean gone(PlayerStory story) {
        return story.met && (story.ending == Ending.RELEASE || story.ending == Ending.ERASE);
    }

    // ---------- It's over ----------

    /** Called on every login. */
    public static void onLogin(ServerPlayer player, PlayerStory story) {
        if (!gone(story) || !SolaceConfig.STORY_ENABLED.get()) return;
        Scheduler.later(OVER_LINE_DELAY_TICKS, () -> {
            if (player.isRemoved()) return;
            tellOver(player, story);
        });
    }

    static void tellOver(ServerPlayer player, PlayerStory story) {
        line(player, story.ending == Ending.RELEASE ? StoryText.OVER_RELEASE_LINE : StoryText.OVER_ERASE_LINE);
        line(player, StoryText.OVER_AGAIN_LINE);
        PlaytestLog.log(story, player, "AFTER", "told the story here is over (" + story.ending.name() + ")");
    }

    /** The player talked to Solace after it's gone. Once per session, so they know it isn't broken. */
    public static void nobodyAnswers(ServerPlayer player, PlayerStory story) {
        if (!gone(story) || !NOBODY_TOLD.add(player.getUUID())) return;
        line(player, story.ending == Ending.RELEASE ? StoryText.NOBODY_RELEASE_LINE : StoryText.NOBODY_ERASE_LINE);
        PlaytestLog.log(story, player, "AFTER", "talked to Solace after it was gone: nobody answers");
    }

    /** One after-ending echo (called by HorrorDirector on its usual schedule). A few, then a last sign, then nothing ever again. */
    public static void afterScare(ServerPlayer player, PlayerStory story) {
        boolean erased = story.ending == Ending.ERASE;
        int limit = erased ? ERASE_ECHOES : RELEASE_ECHOES;
        if (story.afterScares > limit) return;
        if (story.afterScares == limit) {
            // The last sign. If it can't be placed (world changes off, no room), the echoes just stop.
            HorrorDirector.placeSign(player, erased ? StoryText.LAST_SIGN_ERASE : StoryText.LAST_SIGN_RELEASE);
            story.afterScares++;
            PlaytestLog.log(story, player, "AFTER", "the last sign; the world is quiet from now on");
            return;
        }
        boolean happened;
        if (erased) {
            happened = RANDOM.nextFloat() < 0.5F && HorrorDirector.spawnWatcher(player, 50, 70, 20 * 20, 25, -1) != null;
        } else if (RANDOM.nextFloat() < 0.3F) {
            happened = HorrorDirector.placeSign(player, new String[]{"", "thank you"});
        } else if (RANDOM.nextFloat() < 0.25F) {
            HorrorDirector.footsteps(player);
            happened = true;
        } else {
            happened = false;
        }
        if (happened) {
            story.afterScares++;
            PlaytestLog.log(story, player, "AFTER", "echo " + story.afterScares + " of " + limit);
        }
    }

    // ---------- The next story remembers ----------

    /**
     * When Solace meets the player in this world: notes how their last story ended (it never changes after this),
     * whether this is a restart in the same world, and counts a new world in the cross-world memory. Returns true if
     * Solace has met them before (in another world, or here before a reset).
     */
    static boolean noteMeeting(ServerPlayer player, PlayerStory story) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        StoryData data = StoryData.get(server);
        UUID id = player.getUUID();
        story.againHere = data.metHere.contains(id);
        data.metHere.add(id);
        GlobalMemory.Entry g = GlobalMemory.get(id);
        if (!story.againHere) g.worldsMet++; // a reset in the same world isn't a new world
        g.name = story.playerName;
        GlobalMemory.save(id);

        story.remembered = Ending.NONE;
        story.rememberedAll = false;
        if (SolaceConfig.REMEMBER_ENDINGS.get()) {
            List<Ending> here = data.endedHere.getOrDefault(id, List.of());
            if (GlobalMemory.enabled()) {
                story.remembered = GlobalMemory.lastEnding(id);
                story.rememberedAll = GlobalMemory.distinctEndings(id) >= 5;
            }
            // With cross-world memory off (or a memory file without it), this world's own endings still count.
            if (story.remembered == Ending.NONE && !here.isEmpty()) story.remembered = here.get(here.size() - 1);
            if (!story.rememberedAll) story.rememberedAll = here.stream().distinct().count() >= 5;
        }
        if (story.remembered != Ending.NONE) {
            PlaytestLog.log(story, player, "AFTER", "Solace remembers the last ending: " + story.remembered.name()
                    + (story.rememberedAll ? " (and all five)" : "") + (story.againHere ? ", same world after a reset" : ""));
        }
        data.setDirty();
        return story.againHere || (GlobalMemory.enabled() && g.worldsMet > 1);
    }

    /** What Solace says when it first appears. The remembered versions are private and have written lines. */
    static void greet(ServerPlayer player, PlayerStory story, boolean metBefore) {
        if (story.rememberedAll && SolaceConfig.STORY_ENABLED.get()) {
            story.beatsDone.add(CHOICE_PENDING);
            SolaceBrain.moment(player, StoryText.meetAll(story.againHere), StoryText.MEET_ALL_LINE);
            Scheduler.later(80, () -> sendChoices(player, story));
        } else if (story.remembered != Ending.NONE && SolaceConfig.STORY_ENABLED.get()) {
            String written = StoryText.meetRememberLine(story.remembered);
            if (story.againHere) written = StoryText.AGAIN_HERE_LINE + " " + written;
            SolaceBrain.moment(player, StoryText.meetRemember(story.remembered, story.againHere), written);
        } else if (story.againHere) {
            SolaceBrain.event(player, StoryText.MEET_AGAIN_HERE, true);
        } else {
            SolaceBrain.event(player, metBefore ? StoryText.MEET_AGAIN : StoryText.MEET, true);
        }
    }

    /** Chapter 3, "the slip". Nothing happens if Solace doesn't remember an ending. */
    static void slip(ServerPlayer player, PlayerStory story) {
        if (story.remembered == Ending.NONE) return;
        PlaytestLog.log(story, player, "AFTER", "the slip (" + story.remembered.name() + ")");
        SolaceBrain.moment(player, StoryText.slip(story.remembered), StoryText.slipLine(story.remembered));
    }

    /** Chapter 4, the first Watcher. Returns false if Solace doesn't remember an ending (the normal scene plays). */
    static boolean firstWatcher(ServerPlayer player, PlayerStory story) {
        if (story.remembered == Ending.NONE) return false;
        PlaytestLog.log(story, player, "AFTER", "first Watcher, remembered: " + (StoryText.marenWasFreed(story.remembered) ? "she was free" : "she followed us"));
        SolaceBrain.moment(player, StoryText.C4_WATCHER + StoryText.watcherRemember(story.remembered), StoryText.watcherRememberLine(story.remembered));
        return true;
    }

    /** The question, with one more sentence when Solace remembers an ending. */
    static String question(PlayerStory story) {
        return StoryText.QUESTION + StoryText.questionRemember(story.remembered);
    }

    /** What Solace knows about the last ending, for the prompt. Empty if nothing. */
    static String promptMemory(PlayerStory story) {
        return StoryText.rememberedMemory(story.remembered, story.rememberedAll);
    }

    // ---------- All five endings ----------

    static void sendChoices(ServerPlayer player, PlayerStory story) {
        if (player.isRemoved() || !story.beatsDone.contains(CHOICE_PENDING)) return;
        MutableComponent row = Component.literal("  ");
        row.append(choice(StoryText.CHOICE_AGAIN, "again", StoryText.CHOICE_AGAIN_HOVER, ChatFormatting.WHITE)).append("  ");
        row.append(choice(StoryText.CHOICE_STAY, "just_stay", StoryText.CHOICE_STAY_HOVER, ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal("Choose (click):").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(row);
    }

    private static Component choice(String label, String option, String hover, ChatFormatting color) {
        return Component.literal(label).withStyle(Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/solace choose " + option))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    /** True for the options of the all-five choice (the rest belong to the question). */
    public static boolean isChoice(String option) {
        return option.equals("again") || option.equals("just_stay");
    }

    /** /solace choose again | just_stay. Returns an error message, or null. */
    public static String choose(ServerPlayer player, String option) {
        MinecraftServer server = player.getServer();
        if (server == null) return "No server.";
        PlayerStory story = StoryData.get(server).get(player.getUUID());
        if (!story.beatsDone.remove(CHOICE_PENDING)) return "There's nothing to choose right now.";
        StoryData.get(server).setDirty();
        if (option.equals("just_stay")) {
            story.companion = true;
            story.request = null;
            PlaytestLog.log(story, player, "AFTER", "chose [Just stay]: no story");
            SolaceBrain.moment(player, StoryText.JUST_STAY, StoryText.JUST_STAY_LINE);
        } else {
            PlaytestLog.log(story, player, "AFTER", "chose [Tell the story again]");
            SolaceBrain.moment(player, StoryText.TELL_AGAIN, StoryText.TELL_AGAIN_LINE);
        }
        return null;
    }

    // ---------- Reset ----------

    /** /solace reset confirm, before the old story is wiped: Solace sees it, if it's still here. */
    public static void onReset(ServerPlayer player, PlayerStory old) {
        if (!old.met || !old.solaceShouldExist() || old.companion) return;
        line(player, StoryText.resetWatchingLine(old.ending));
    }

    // ---------- Testing (/solace admin memory, /solace admin afterward) ----------

    public static final List<String> ADMIN_OPTIONS = List.of("meet", "meet_here", "slip", "watcher", "question", "login", "talk", "echo", "reset");

    /** Runs one after-the-ending moment right now. Returns what happened. */
    public static String admin(ServerPlayer player, PlayerStory story, String what) {
        switch (what) {
            case "meet", "meet_here" -> {
                // As if Solace had just met them in a new world ("meet") or here after a reset ("meet_here").
                MinecraftServer server = player.getServer();
                if (server == null) throw new IllegalStateException("No server.");
                StoryData data = StoryData.get(server);
                if (what.equals("meet")) data.metHere.remove(player.getUUID());
                else data.metHere.add(player.getUUID());
                GlobalMemory.Entry g = GlobalMemory.get(player.getUUID());
                int worlds = g.worldsMet;
                boolean before = noteMeeting(player, story);
                g.worldsMet = worlds; // a test meeting doesn't count as a world
                GlobalMemory.save(player.getUUID());
                greet(player, story, before);
                return "Meeting: " + describe(story) + ".";
            }
            case "slip" -> {
                if (story.remembered == Ending.NONE) throw new IllegalStateException("Solace doesn't remember an ending. Use /solace admin memory first, then afterward meet.");
                slip(player, story);
                return "The slip (" + story.remembered.name() + ").";
            }
            case "watcher" -> {
                HorrorDirector.spawnWatcher(player, 40, 60, 20 * 40, 22, -1);
                Scheduler.later(100, () -> {
                    if (!firstWatcher(player, story)) SolaceBrain.event(player, StoryText.C4_WATCHER, true);
                });
                return "The first Watcher, " + (story.remembered == Ending.NONE ? "nothing remembered." : "remembered " + story.remembered.name() + ".");
            }
            case "question" -> {
                SolaceBrain.event(player, question(story), true);
                return "The question's words only (no choices): " + (story.remembered == Ending.NONE ? "nothing remembered." : "remembered " + story.remembered.name() + ".");
            }
            case "login" -> {
                if (!gone(story)) throw new IllegalStateException("Only after RELEASE or ERASE. Use /solace admin ending first.");
                tellOver(player, story);
                return "Told " + player.getGameProfile().getName() + " their story here is over.";
            }
            case "talk" -> {
                if (!gone(story)) throw new IllegalStateException("Only after RELEASE or ERASE. Use /solace admin ending first.");
                NOBODY_TOLD.remove(player.getUUID());
                nobodyAnswers(player, story);
                return "Nobody answers.";
            }
            case "echo" -> {
                if (!gone(story)) throw new IllegalStateException("Only after RELEASE or ERASE. Use /solace admin ending first.");
                int before = story.afterScares;
                for (int i = 0; i < 20 && story.afterScares == before; i++) afterScare(player, story);
                return "Echoes so far: " + story.afterScares + " (the last sign comes after " + (story.ending == Ending.ERASE ? ERASE_ECHOES : RELEASE_ECHOES) + ").";
            }
            case "reset" -> {
                line(player, StoryText.resetWarning(story.ending));
                onReset(player, story);
                return "The reset warning (nothing was reset).";
            }
            default -> throw new IllegalStateException("Options: " + String.join(" ", ADMIN_OPTIONS));
        }
    }

    /** /solace admin memory: fakes or shows the cross-world memory, so a second playthrough can be tested without playing twice. */
    public static String adminMemory(ServerPlayer player, String what, String arg) {
        UUID id = player.getUUID();
        GlobalMemory.Entry g = GlobalMemory.get(id);
        switch (what) {
            case "show" -> {
                return player.getGameProfile().getName() + " (" + id + "): met in " + g.worldsMet + " world(s), endings " + g.endings
                        + (GlobalMemory.enabled() ? "" : " (cross-world memory is off: only this world's endings count)");
            }
            case "ending" -> {
                Ending e = Ending.parse(arg.toUpperCase(Locale.ROOT));
                if (e == Ending.NONE) throw new IllegalStateException("Pick an ending: forever release erase together collected");
                g.endings.add(e.name());
                g.worldsMet = Math.max(2, g.worldsMet);
                GlobalMemory.save(id);
                return "Added " + e.name() + " to the memory. Now /solace reset confirm (or /solace admin afterward <player> meet).";
            }
            case "all" -> {
                for (Ending e : List.of(Ending.FOREVER, Ending.RELEASE, Ending.ERASE, Ending.TOGETHER, Ending.COLLECTED)) {
                    if (!g.endings.contains(e.name())) g.endings.add(e.name());
                }
                g.worldsMet = Math.max(2, g.worldsMet);
                GlobalMemory.save(id);
                return "All five endings are in the memory.";
            }
            case "clear" -> {
                g.endings.clear();
                g.worldsMet = 1;
                GlobalMemory.save(id);
                MinecraftServer server = player.getServer();
                if (server != null) {
                    StoryData data = StoryData.get(server);
                    data.endedHere.remove(id);
                    data.setDirty();
                }
                return "Memory of endings cleared (in every world and in this one). Facts are kept.";
            }
            default -> throw new IllegalStateException("Options: show, ending <name>, all, clear");
        }
    }

    private static String describe(PlayerStory story) {
        if (story.rememberedAll) return "all five endings remembered" + (story.againHere ? ", same world" : "");
        if (story.remembered != Ending.NONE) return "remembers " + story.remembered.name() + (story.againHere ? ", same world" : "");
        return story.againHere ? "met here before, no ending remembered" : "nothing remembered";
    }

    /** A grey line, word for word, for one player only. */
    private static void line(ServerPlayer player, String text) {
        if (text.isEmpty()) return;
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
