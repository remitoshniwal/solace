package com.solacemod.solace.story;

import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.entity.SolaceEntity;
import com.solacemod.solace.horror.OnItsOwn;
import com.solacemod.solace.horror.PersonalFreeze;
import com.solacemod.solace.story.StoryText.Tone;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Small moments on real dates and times (1.12.0): Halloween, the new year, 3 a.m., and coming back after a long time away.
 * Each one happens once (a year, or a night), never changes the story's progress, and waits until nothing dangerous is going on.
 * The clock is the player's own: their game sends its time zone when they join (kept in memory only, never saved), so a friend
 * in another country gets 3 a.m. at their own 3 a.m. All the writing is in StoryText. The numbers below are guesses until the playtest.
 */
public final class RealDates {
    /** Seconds online before a date moment can happen, so it doesn't pile onto the hello. */
    public static final int AFTER_LOGIN_SECONDS = 120;
    /** Days away that count as "a long time": the return greeting becomes a moment with a written line. */
    public static final int LONG_AWAY_DAYS = 7;
    /** Solace must be this close (blocks) for a date moment. */
    public static final int NEAR = 32;

    /** The moments, by the name saved in PlayerStory.datesSeen. */
    public static final List<String> MOMENTS = List.of("halloween", "new_year", "three_am");

    /** Each player's time zone (minutes from UTC), sent by their game. Missing = the server's own clock. */
    private static final Map<UUID, Integer> OFFSET_MINUTES = new HashMap<>();
    /** /solace admin date: how far each player's clock is moved, for testing (seconds). */
    private static final Map<UUID, Long> FAKE_SHIFT = new HashMap<>();
    private static final Map<UUID, Long> ONLINE_SINCE = new HashMap<>();

    private RealDates() {
    }

    public static void reset() {
        OFFSET_MINUTES.clear();
        FAKE_SHIFT.clear();
        ONLINE_SINCE.clear();
    }

    public static void forget(UUID id) {
        OFFSET_MINUTES.remove(id);
        FAKE_SHIFT.remove(id);
        ONLINE_SINCE.remove(id);
    }

    /** The player's game told us its time zone. Anything outside the real range (-12 to +14 hours) is ignored. */
    public static void onClock(ServerPlayer player, int offsetMinutes) {
        if (offsetMinutes < -12 * 60 || offsetMinutes > 14 * 60) return;
        OFFSET_MINUTES.put(player.getUUID(), offsetMinutes);
    }

    /** What time it is for this player right now (their own clock if their game said, else the server's). */
    public static LocalDateTime now(ServerPlayer player) {
        Instant instant = Instant.now().plusSeconds(FAKE_SHIFT.getOrDefault(player.getUUID(), 0L));
        Integer offset = OFFSET_MINUTES.get(player.getUUID());
        ZoneId zone = offset == null ? ZoneId.systemDefault() : ZoneOffset.ofTotalSeconds(offset * 60);
        return LocalDateTime.ofInstant(instant, zone);
    }

    // ---------- Halloween, the new year, 3 a.m. ----------

    /** Once a second for each player who has met Solace. */
    public static void tick(ServerPlayer player, PlayerStory story) {
        if (!SolaceConfig.REAL_DATES.get() || !story.met || !story.solaceShouldExist()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        long since = ONLINE_SINCE.computeIfAbsent(player.getUUID(), k -> (long) server.getTickCount());
        if (server.getTickCount() - since < AFTER_LOGIN_SECONDS * 20L) return;
        LocalDateTime t = now(player);
        String moment = dueNow(t);
        if (moment == null || stamp(moment, t).equals(story.datesSeen.get(moment))) return;
        // TOGETHER already tells them to go to sleep late at night (StoryDirector), once is enough.
        if (moment.equals("three_am") && story.ending == Ending.TOGETHER) return;
        if (!ready(player, story, server)) return;
        play(player, story, moment, t);
    }

    /** Which moment it is at this time, or null. */
    static String dueNow(LocalDateTime t) {
        if (t.getMonthValue() == 10 && t.getDayOfMonth() == 31) return "halloween";
        if (t.getMonthValue() == 1 && t.getDayOfMonth() == 1) return "new_year";
        if (t.getHour() == 3) return "three_am";
        return null;
    }

    /** When a moment last happened, as saved: the year for Halloween and the new year, the date for 3 a.m. */
    private static String stamp(String moment, LocalDateTime t) {
        return moment.equals("three_am") ? t.toLocalDate().toString() : String.valueOf(t.getYear());
    }

    /** Nothing dangerous or important going on, and Solace close by. */
    private static boolean ready(ServerPlayer player, PlayerStory story, MinecraftServer server) {
        if (story.bossActive || story.choicePending || story.beatsDone.contains(Afterward.CHOICE_PENDING)) return false;
        if (server.tickRateManager().isFrozen() || PersonalFreeze.isFrozen(player) || !OnItsOwn.calm(player)) return false;
        SolaceEntity solace = SolaceManager.find(server, story);
        return solace != null && solace.level() == player.level() && !solace.isHollow() && solace.distanceTo(player) < NEAR;
    }

    private static void play(ServerPlayer player, PlayerStory story, String moment, LocalDateTime t) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        story.datesSeen.put(moment, stamp(moment, t));
        StoryData.get(server).setDirty();
        Tone tone = story.tone();
        PlaytestLog.log(story, player, "DATE", moment + " (" + tone.name().toLowerCase(java.util.Locale.ROOT) + ", the player's clock says "
                + t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + ")");
        switch (moment) {
            case "halloween" -> {
                if (tone == Tone.EARLY) pumpkin(player);
                SolaceBrain.comment(player, StoryText.halloween(tone), StoryText.halloweenLine(tone));
            }
            case "new_year" -> SolaceBrain.comment(player, StoryText.newYear(tone, t.getYear()), StoryText.newYearLine(tone));
            default -> SolaceBrain.comment(player, StoryText.threeAm(tone), StoryText.threeAmLine(tone));
        }
    }

    /** A carved pumpkin at the player's feet (a gift, not a block: nothing in the world changes). */
    private static void pumpkin(ServerPlayer player) {
        ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.2, player.getZ(), new ItemStack(Items.CARVED_PUMPKIN));
        item.setDeltaMovement(0, 0.1, 0);
        item.setNoPickUpDelay();
        player.level().addFreshEntity(item);
    }

    // ---------- Coming back after a long time ----------

    /**
     * Called when the player logs back in. After a long time away the usual greeting becomes a moment with a written line
     * (the same one request as before, not an extra one). Returns false if it's not long enough (the usual greeting plays).
     */
    static boolean longReturn(ServerPlayer player, PlayerStory story, long awayMinutes) {
        long days = awayMinutes / (60 * 24);
        if (!SolaceConfig.REAL_DATES.get() || days < LONG_AWAY_DAYS) return false;
        Tone tone = story.tone();
        PlaytestLog.log(story, player, "DATE", "back after " + days + " days (" + tone.name().toLowerCase(java.util.Locale.ROOT) + ")");
        SolaceBrain.moment(player, StoryText.longAway(tone, days), StoryText.longAwayLine(tone, days));
        return true;
    }

    /** Meeting in a new world after a long time away from every world: Solace knows how long (a memory, no AI request). */
    static void noteLongGap(PlayerStory story, long lastSeenMs) {
        if (!SolaceConfig.REAL_DATES.get() || lastSeenMs <= 0) return;
        long days = (System.currentTimeMillis() - lastSeenMs) / (1000L * 60 * 60 * 24);
        if (days >= LONG_AWAY_DAYS) story.addEvent(String.format(StoryText.LONG_GAP_EVENT, days));
    }

    // ---------- Testing (/solace admin date) ----------

    public static final List<String> ADMIN_OPTIONS = List.of("show", "set", "clear", "forget", "away");

    /** Runs one test action. Returns what happened. */
    public static String admin(ServerPlayer player, PlayerStory story, String what, String arg) {
        UUID id = player.getUUID();
        String name = player.getGameProfile().getName();
        switch (what) {
            case "show" -> {
                Integer offset = OFFSET_MINUTES.get(id);
                return name + "'s clock: " + now(player).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        + (offset == null ? " (the server's time zone: their game hasn't said)" : String.format(" (UTC%+.1f)", offset / 60.0))
                        + (FAKE_SHIFT.containsKey(id) ? ", faked" : "") + ". Done: " + story.datesSeen;
            }
            case "set" -> {
                // "2026-10-31 03:00": moves this player's clock there (it keeps ticking from there).
                LocalDateTime wanted;
                try {
                    wanted = LocalDateTime.parse(arg.trim().replace(' ', 'T'));
                } catch (java.time.format.DateTimeParseException e) {
                    throw new IllegalStateException("Write the date and time like 2026-10-31 03:00");
                }
                FAKE_SHIFT.remove(id);
                long shift = Math.round(java.time.Duration.between(now(player), wanted).toMillis() / 1000.0);
                FAKE_SHIFT.put(id, shift);
                ONLINE_SINCE.put(id, -1_000_000L); // no waiting after "logging in"
                return name + "'s clock now says " + now(player).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + ".";
            }
            case "clear" -> {
                FAKE_SHIFT.remove(id);
                return name + "'s clock is real again.";
            }
            case "forget" -> {
                story.datesSeen.clear();
                return "Forgot which date moments " + name + " has had: they can happen again.";
            }
            case "away" -> {
                // As if they had just come back after this many days.
                long days;
                try {
                    days = Long.parseLong(arg.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("How many days? Like: away 12");
                }
                if (days < LONG_AWAY_DAYS) throw new IllegalStateException("Under " + LONG_AWAY_DAYS + " days the usual greeting plays.");
                if (!longReturn(player, story, days * 60 * 24)) throw new IllegalStateException("Real dates are switched off.");
                return "Back after " + days + " days.";
            }
            default -> throw new IllegalStateException("Options: " + String.join(" ", ADMIN_OPTIONS));
        }
    }
}
