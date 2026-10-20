package com.solacemod.solace.story;

import com.solacemod.solace.Solace;
import com.solacemod.solace.config.SolaceConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The playtest log: a plain text timeline of everything that happens in the story, one line each.
 * Saved in &lt;game folder&gt;/solace/playtest/&lt;world&gt;-&lt;date&gt;.txt. Only written when playtestLog is on
 * (/solace admin playtest on), except for notes from /solace note, which are always saved.
 */
public final class PlaytestLog {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static boolean warned;

    private PlaytestLog() {
    }

    public static boolean enabled() {
        return SolaceConfig.PLAYTEST_LOG.get();
    }

    /** Adds one line, if the log is on. Kinds are short words like CHAPTER, PAGE, SCARE, SOLACE. */
    public static void log(PlayerStory story, @Nullable ServerPlayer player, String kind, String text) {
        if (enabled()) write(story, player, kind, text);
    }

    /** A note typed with /solace note. Always saved, even when the log is off. Returns the file it went to. */
    @Nullable
    public static Path note(PlayerStory story, ServerPlayer player, String text) {
        return write(story, player, "NOTE", text);
    }

    /** Where today's log for this world goes. */
    @Nullable
    public static Path file() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        String world = server.getWorldData().getLevelName().replaceAll("[^A-Za-z0-9 _-]", "_").trim();
        if (world.isEmpty()) world = "world";
        return FMLPaths.GAMEDIR.get().resolve("solace").resolve("playtest").resolve(world + "-" + LocalDate.now() + ".txt");
    }

    @Nullable
    private static Path write(PlayerStory story, @Nullable ServerPlayer player, String kind, String text) {
        Path f = file();
        if (f == null) return null;
        String line = LocalTime.now().format(CLOCK) + "  " + context(story, player) + "  " + kind + "  " + text.replace('\n', ' ') + System.lineSeparator();
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Never let the log break the game. Warn once in the game log.
            if (!warned) Solace.LOGGER.warn("Could not write the Solace playtest log {}", f, e);
            warned = true;
        }
        return f;
    }

    /** Who, where in the story, and where in the world: "Remi  ch3 +12.4m  pages 1/7  trust 18 tether 40 debt 5  (120, 64, -88)". */
    private static String context(PlayerStory story, @Nullable ServerPlayer player) {
        if (player == null) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) player = server.getPlayerList().getPlayer(story.playerId);
        }
        String name = player != null ? player.getGameProfile().getName() : story.playerName;
        double inChapter = story.playMinutes() * SolaceConfig.STORY_SPEED.get() - story.chapterStartMin;
        StringBuilder sb = new StringBuilder(name.isEmpty() ? "?" : name);
        if (story.met) {
            sb.append(String.format(Locale.ROOT, "  ch%d +%.1fm  pages %d/%d  trust %d tether %d debt %d",
                    story.chapter, inChapter, story.fragmentsFound.size(), StoryText.FRAGMENT_COUNT, story.trust, (int) story.tether, (int) story.debt));
        } else {
            sb.append("  (not met yet)");
        }
        if (player != null) sb.append("  (").append(player.getBlockX()).append(", ").append(player.getBlockY()).append(", ").append(player.getBlockZ()).append(")");
        return sb.toString();
    }
}
