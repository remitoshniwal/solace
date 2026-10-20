package com.solacemod.solace.story;

import com.solacemod.solace.Solace;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The "Solace" tab in the advancements screen (L), with a pop-up toast for each story moment.
 * The files are in data/solace/advancement/story/. They all use the "impossible" trigger, so only this class can grant them.
 * None are announced in chat, so other players on a server aren't spoiled. The endings stay hidden until you reach them.
 */
public final class StoryAdvancements {
    /** Taken away again by /solace reset confirm, so a replay shows the toasts again. The endings are kept. */
    private static final List<String> STORY = List.of("root", "knew_my_name", "first_page", "all_pages", "house", "kept_her",
            "the_question", "first_favour", "owed", "as_asked");
    private static final Set<String> WARNED = new HashSet<>();

    private StoryAdvancements() {
    }

    /** Gives the player one advancement (does nothing if they already have it). */
    public static void grant(ServerPlayer player, String id) {
        AdvancementHolder holder = find(player, id);
        if (holder == null) return;
        PlayerAdvancements progress = player.getAdvancements();
        if (progress.getOrStartProgress(holder).isDone()) return;
        if (progress.award(holder, "done") && player.getServer() != null) {
            PlaytestLog.log(StoryData.get(player.getServer()).get(player.getUUID()), player, "ADVANCEMENT", id);
        }
    }

    public static void grantEnding(ServerPlayer player, Ending ending) {
        if (ending != Ending.NONE) grant(player, "end_" + ending.name().toLowerCase(Locale.ROOT));
    }

    /**
     * On login: grants what the saved story already shows. Worlds from before 1.7.0 get the advancements
     * their player earned back then, and nothing is lost if the server missed one.
     */
    public static void catchUp(ServerPlayer player, PlayerStory story) {
        if (!story.met) return;
        grant(player, "root");
        if (story.chapter >= 3) grant(player, "knew_my_name");
        if (!story.fragmentsFound.isEmpty()) grant(player, "first_page");
        if (story.fragmentsFound.size() >= StoryText.FRAGMENT_COUNT) grant(player, "all_pages");
        // "house_visited" is also set when no house could be built, so check that the house exists.
        if (story.beatsDone.contains("house_visited") && story.houseCorner != null) grant(player, "house");
        if (story.chapter >= 6 && story.beatsDone.contains("c6_confess")) grant(player, "kept_her");
        if (story.choicePending || (story.ending != Ending.NONE && story.ending != Ending.COLLECTED)) grant(player, "the_question");
        if (story.totalGiven > 0) grant(player, "first_favour");
        if (story.debt >= 30) grant(player, "owed");
        grantEnding(player, story.ending);
        if (GlobalMemory.distinctEndings(player.getUUID()) >= 5) grant(player, "every_ending");
    }

    /** /solace reset confirm: takes back the story and bond advancements. Endings stay earned. */
    public static void revokeStory(ServerPlayer player) {
        PlayerAdvancements progress = player.getAdvancements();
        for (String id : STORY) {
            AdvancementHolder holder = find(player, id);
            if (holder != null) progress.revoke(holder, "done");
        }
    }

    @Nullable
    private static AdvancementHolder find(ServerPlayer player, String id) {
        MinecraftServer server = player.getServer();
        if (server == null) return null;
        AdvancementHolder holder = server.getAdvancements().get(Solace.id("story/" + id));
        if (holder == null && WARNED.add(id)) Solace.LOGGER.warn("Solace: the advancement solace:story/{} is missing (check data/solace/advancement/story/)", id);
        return holder;
    }
}
