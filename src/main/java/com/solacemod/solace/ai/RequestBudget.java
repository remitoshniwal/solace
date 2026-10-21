package com.solacemod.solace.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts AI requests so nobody gets a surprise bill: a limit per minute and per day for the whole server,
 * plus each player's own daily share, so one chatty player can't use up the owner's key for everyone.
 * No Minecraft code here, so aiTest can check it.
 */
public final class RequestBudget {
    public enum Result { OK, RATE_LIMITED, DAILY_LIMIT, PLAYER_DAILY_LIMIT }

    private record Recent(long timeMs, String player) {
    }

    private final Deque<Recent> recent = new ArrayDeque<>();
    private String day = "";
    private int total;
    private final Map<String, Integer> perPlayer = new LinkedHashMap<>();

    /**
     * Counts one request if the limits allow it.
     *
     * @param player         who it's for (a UUID as text)
     * @param today          today's date as text (a new date starts a new day)
     * @param perMinute      most requests per minute for the whole server
     * @param daily          most requests per day for the whole server, 0 = no limit
     * @param perPlayerDaily most requests per day for one player, 0 = no limit
     * @param othersOnline   true when other players are online: then one player may only use half of each minute
     */
    public synchronized Result tryUse(String player, String today, long nowMs, int perMinute, int daily, int perPlayerDaily, boolean othersOnline) {
        newDay(today);
        while (!recent.isEmpty() && nowMs - recent.peekFirst().timeMs() > 60_000) recent.pollFirst();
        if (recent.size() >= perMinute) return Result.RATE_LIMITED;
        if (othersOnline) {
            int mine = 0;
            for (Recent r : recent) if (r.player().equals(player)) mine++;
            if (mine >= Math.max(1, perMinute / 2)) return Result.RATE_LIMITED;
        }
        if (daily > 0 && total >= daily) return Result.DAILY_LIMIT;
        if (perPlayerDaily > 0 && usedToday(player, today) >= perPlayerDaily) return Result.PLAYER_DAILY_LIMIT;
        recent.addLast(new Recent(nowMs, player));
        total++;
        perPlayer.merge(player, 1, Integer::sum);
        return Result.OK;
    }

    private void newDay(String today) {
        if (today.equals(day)) return;
        day = today;
        total = 0;
        perPlayer.clear();
    }

    /** One player's requests today (0 if the saved day is over). */
    public synchronized int usedToday(String player, String today) {
        return today.equals(day) ? perPlayer.getOrDefault(player, 0) : 0;
    }

    /** Requests used today by the whole server (0 if the saved day is over). */
    public synchronized int totalToday(String today) {
        return today.equals(day) ? total : 0;
    }

    /** Each player's requests today, most first. */
    public synchronized Map<String, Integer> playersToday(String today) {
        Map<String, Integer> sorted = new LinkedHashMap<>();
        if (!today.equals(day)) return sorted;
        perPlayer.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    // Saving (the daily counts survive restarts; the per-minute window doesn't need to)

    public synchronized String day() {
        return day;
    }

    public synchronized int total() {
        return total;
    }

    public synchronized Map<String, Integer> perPlayer() {
        return new LinkedHashMap<>(perPlayer);
    }

    public synchronized void restore(String day, int total, Map<String, Integer> perPlayer) {
        this.day = day == null ? "" : day;
        this.total = total;
        this.perPlayer.clear();
        this.perPlayer.putAll(perPlayer);
    }
}
