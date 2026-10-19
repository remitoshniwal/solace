package com.solacemod.solace.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/**
 * Several AI "brains" (providers) working together, so free limits last much longer.
 *
 * Every request is sorted into a {@link Kind}, and each kind is routed differently:
 * - STORY (story moments): the best brain that has room, in the player's order. Brain 1 is "the best".
 * - CHAT (replies to the player): the same, but if the best brain is being very slow, a much faster one may answer instead.
 * - BACKGROUND (memory summaries, comments Solace makes on its own): whichever brain has the most room left,
 *   saving the best brain for what matters. Skipped entirely when every brain is nearly out.
 *
 * The pool learns each brain's limits from the provider's own replies (remaining requests/tokens headers,
 * "try again in 7s", "daily limit reached"), and rests a brain *before* it runs out where it can.
 * If a brain fails, the request moves on to the next one, so the player rarely sees an error.
 *
 * No Minecraft code here, so it can be tested on its own (see AiSelfTest).
 */
public final class BrainPool {
    public enum Kind { STORY, CHAT, BACKGROUND }

    /** The answer, and which brain gave it. */
    public record Answer(String text, Brain brain) {
    }

    /** Thrown (without any network call) when every brain is resting or too close to its limit for a background request. */
    public static final class BusyException extends LlmException {
        public final long readyInMs;

        public BusyException(long readyInMs) {
            super(-2, "All of Solace's AI brains are resting" + (readyInMs > 0 ? " (back in about " + human(readyInMs) + ")" : ""));
            this.readyInMs = readyInMs;
        }
    }

    /**
     * Rough requests-per-minute of each provider's FREE tier, used only until the provider tells us its real limits.
     * Keeps Solace from hammering a free key into a "too many requests" error. 0 = unknown (no guessing).
     */
    private static final Map<String, Integer> FREE_RPM_GUESS = Map.of("gemini", 10, "groq", 30, "cerebras", 30, "openrouter", 20, "mistral", 30);

    /** Below this share of its limit left, a brain is "nearly out": important requests try other brains first. */
    private static final double LOW = 0.12;
    /** Background requests only use brains with at least this much room left. */
    private static final double BACKGROUND_MIN = 0.35;

    private final List<Brain> brains = new ArrayList<>();
    private final LongSupplier clock;

    public BrainPool(LongSupplier clock) {
        this.clock = clock;
    }

    public BrainPool() {
        this(System::currentTimeMillis);
    }

    /** Sets which brains exist (in order, best first). Brains whose settings didn't change keep what they learned. */
    public synchronized void update(List<LlmProvider> providers) {
        List<Brain> next = new ArrayList<>();
        for (int i = 0; i < providers.size(); i++) {
            LlmProvider p = providers.get(i);
            Brain keep = null;
            for (Brain b : brains) if (b.provider.settings().equals(p.settings())) keep = b;
            Brain b = keep != null ? keep : new Brain(p);
            b.slot = i + 1;
            next.add(b);
        }
        brains.clear();
        brains.addAll(next);
        for (Brain b : brains) b.provider.onRateInfo(rate -> b.onRate(rate, clock.getAsLong()));
    }

    public synchronized List<Brain> brains() {
        return List.copyOf(brains);
    }

    public synchronized boolean isEmpty() {
        return brains.isEmpty();
    }

    /** True if a request of this kind would be sent right now (false = it would be skipped or have to wait). */
    public synchronized boolean hasRoomFor(Kind kind) {
        return pick(kind, 2000, 1200, Set.of()) != null;
    }

    /**
     * Sends the request to the best brain for its kind, moving on to the next brain if one fails.
     *
     * @param full    the normal request
     * @param compact a shorter version (less history and detail), used for background requests and when a brain is low on tokens; may be null
     */
    public CompletableFuture<Answer> complete(LlmRequest full, LlmRequest compact, Kind kind) {
        return attempt(full, compact == null ? full : compact, kind, new HashSet<>(), null);
    }

    private CompletableFuture<Answer> attempt(LlmRequest full, LlmRequest compact, Kind kind, Set<Brain> tried, Throwable lastError) {
        Choice choice;
        synchronized (this) {
            choice = pick(kind, estimate(full), estimate(compact), tried);
            if (choice == null) {
                if (lastError != null) return CompletableFuture.failedFuture(lastError);
                return CompletableFuture.failedFuture(new BusyException(readyInMs()));
            }
            choice.brain.markSent(clock.getAsLong());
        }
        Brain b = choice.brain;
        LlmRequest req = choice.compact ? compact : full;
        long start = clock.getAsLong();
        return b.provider.complete(req).handle((text, err) -> {
            long now = clock.getAsLong();
            if (err == null) {
                b.onSuccess(now - start);
                return CompletableFuture.completedFuture(new Answer(text, b));
            }
            Throwable cause = Http.unwrap(err);
            boolean tooBig = b.onFailure(cause, now);
            // "Request too large" with the full prompt: try the same brain once more with the short prompt.
            if (tooBig && !choice.compact && compact != full) {
                return b.provider.complete(compact).handle((t2, e2) -> {
                    long n2 = clock.getAsLong();
                    if (e2 == null) {
                        b.onSuccess(n2 - start);
                        return CompletableFuture.completedFuture(new Answer(t2, b));
                    }
                    b.onFailure(Http.unwrap(e2), n2);
                    tried.add(b);
                    return attempt(full, compact, kind, tried, Http.unwrap(e2));
                }).thenCompose(x -> x);
            }
            tried.add(b);
            // Background requests aren't worth burning a second brain on.
            if (kind == Kind.BACKGROUND) return CompletableFuture.<Answer>failedFuture(cause);
            return attempt(full, compact, kind, tried, cause);
        }).thenCompose(x -> x);
    }

    private record Choice(Brain brain, boolean compact) {
    }

    /** The routing decision. Must hold the lock. */
    private Choice pick(Kind kind, long estFull, long estCompact, Set<Brain> exclude) {
        long now = clock.getAsLong();
        List<Brain> ready = new ArrayList<>();
        for (Brain b : brains) if (!exclude.contains(b) && b.available(now)) ready.add(b);
        if (ready.isEmpty()) return null;

        if (kind == Kind.BACKGROUND) {
            Brain best = null;
            double bestRoom = -1;
            for (Brain b : ready) {
                double room = b.headroom(now, estCompact);
                // ">=" so later brains win ties: keep Brain 1 fresh for what matters.
                if (room >= BACKGROUND_MIN && b.fits(now, estCompact) && room >= bestRoom) {
                    best = b;
                    bestRoom = room;
                }
            }
            return best == null ? null : new Choice(best, true);
        }

        // STORY and CHAT: the first brain in the player's order that is comfortably able to answer.
        Brain chosen = null;
        boolean compact = false;
        for (Brain b : ready) {
            if (b.headroom(now, estFull) < LOW) continue;
            boolean preferCompact = now < b.compactUntil;
            if (!preferCompact && b.fits(now, estFull)) {
                chosen = b;
                break;
            }
            if (b.fits(now, estCompact)) {
                chosen = b;
                compact = true;
                break;
            }
        }
        if (chosen != null && kind == Kind.CHAT && chosen.latencyMs > 9000) {
            // The best brain is crawling. A brain that answers in half the time is worth more for a chat reply.
            for (Brain b : ready) {
                if (b != chosen && b.latencyMs > 0 && b.latencyMs < chosen.latencyMs / 2 && b.headroom(now, estFull) >= LOW && b.fits(now, estFull)) {
                    chosen = b;
                    compact = now < b.compactUntil;
                    break;
                }
            }
        }
        if (chosen != null) return new Choice(chosen, compact);
        // Nobody is comfortable: try the one with the most room left anyway, with the short prompt.
        Brain most = ready.get(0);
        for (Brain b : ready) if (b.headroom(now, estCompact) > most.headroom(now, estCompact)) most = b;
        return new Choice(most, true);
    }

    /** How long until some brain is ready again (0 if one is ready now, -1 if none ever will be without a settings change). */
    public synchronized long readyInMs() {
        long now = clock.getAsLong();
        long best = Long.MAX_VALUE;
        for (Brain b : brains) {
            if (b.broken != null) continue;
            best = Math.min(best, Math.max(0, b.restUntil - now));
        }
        return best == Long.MAX_VALUE ? -1 : best;
    }

    /** Rough token count: about 3.5 characters per token, plus room for the reply. */
    static long estimate(LlmRequest r) {
        long chars = r.system() == null ? 0 : r.system().length();
        for (ChatMessage m : r.messages()) chars += m.content().length() + 8;
        return (long) (chars / 3.5) + r.maxTokens();
    }

    static String human(long ms) {
        long s = Math.max(1, ms / 1000);
        if (s < 90) return s + "s";
        long m = s / 60;
        if (m < 90) return m + " min";
        return (m / 60) + "h " + (m % 60) + "min";
    }

    // ---------- One brain ----------

    /** One provider in the pool, and everything the pool has learned about it. */
    public static final class Brain {
        final LlmProvider provider;
        int slot;
        long restUntil;
        String restReason = "";
        /** Non-null when this brain can't work until its settings change (e.g. the key was rejected). */
        String broken;
        int failStreak;
        int dailyStreak;
        long compactUntil;
        long latencyMs = -1;
        long successes;
        long failures;
        RateInfo rate = RateInfo.NONE;
        long rateAt;
        private final Deque<Long> sent = new ArrayDeque<>();
        private final int rpmGuess;

        Brain(LlmProvider provider) {
            this.provider = provider;
            this.rpmGuess = FREE_RPM_GUESS.getOrDefault(provider.settings().name(), 0);
        }

        public int slot() {
            return slot;
        }

        public LlmProvider provider() {
            return provider;
        }

        synchronized boolean available(long now) {
            return broken == null && now >= restUntil;
        }

        synchronized void markSent(long now) {
            sent.addLast(now);
            while (!sent.isEmpty() && now - sent.peekFirst() > 60_000) sent.pollFirst();
        }

        private int sentLastMinute(long now) {
            while (!sent.isEmpty() && now - sent.peekFirst() > 60_000) sent.pollFirst();
            return sent.size();
        }

        private boolean requestsInfoFresh(long now) {
            long life = rate.requestsResetMs() >= 0 ? rate.requestsResetMs() : 60_000;
            return rate.remainingRequests() >= 0 && now - rateAt < life;
        }

        private boolean tokensInfoFresh(long now) {
            long life = rate.tokensResetMs() >= 0 ? rate.tokensResetMs() : 60_000;
            return rate.remainingTokens() >= 0 && now - rateAt < life;
        }

        /** How much room this brain has left, from 0 (none) to 1 (plenty). */
        synchronized double headroom(long now, long estTokens) {
            double room = 1.0;
            if (requestsInfoFresh(now)) {
                long rem = rate.remainingRequests();
                // A brand new request since the headers came in also counts.
                long since = sent.stream().filter(t -> t > rateAt).count();
                rem = Math.max(0, rem - since);
                room = Math.min(room, rate.limitRequests() > 0 ? (double) rem / rate.limitRequests() : Math.min(1.0, rem / 5.0));
            } else if (rpmGuess > 0 && rate.remainingRequests() < 0) {
                room = Math.min(room, 1.0 - (double) sentLastMinute(now) / rpmGuess);
            }
            if (tokensInfoFresh(now)) {
                double rem = rate.remainingTokens();
                room = Math.min(room, rate.limitTokens() > 0 ? rem / rate.limitTokens() : Math.min(1.0, rem / (estTokens * 3.0)));
            }
            return Math.max(0, room);
        }

        /** True if a request of about this many tokens should go through right now. */
        synchronized boolean fits(long now, long estTokens) {
            if (tokensInfoFresh(now) && rate.remainingTokens() < estTokens) return false;
            if (requestsInfoFresh(now)) {
                long since = sent.stream().filter(t -> t > rateAt).count();
                if (rate.remainingRequests() - since <= 0) return false;
            }
            return rpmGuess <= 0 || rate.remainingRequests() >= 0 || sentLastMinute(now) < rpmGuess;
        }

        synchronized void onRate(RateInfo info, long now) {
            if (!info.known()) return;
            rate = info;
            rateAt = now;
            // Out of requests until the reset: rest until then instead of collecting an error.
            if (info.remainingRequests() == 0 && info.requestsResetMs() > 0) {
                rest(now, info.requestsResetMs(), info.daily() ? "used up today's requests" : "used up its requests for now");
            }
        }

        synchronized void onSuccess(long latency) {
            successes++;
            failStreak = 0;
            dailyStreak = 0;
            latencyMs = latencyMs < 0 ? latency : (latencyMs * 2 + latency) / 3;
            if (restReason.startsWith("not answering")) restReason = "";
        }

        /** Learns from an error. Returns true if the request was too big (so a shorter prompt is worth trying). */
        synchronized boolean onFailure(Throwable cause, long now) {
            failures++;
            failStreak++;
            LlmException e = cause instanceof LlmException le ? le : new LlmException(cause.getMessage(), cause);
            int status = e.status();
            String detail = ((e.getMessage() == null ? "" : e.getMessage()) + " " + (e.providerMessage() == null ? "" : e.providerMessage()))
                    .toLowerCase(Locale.ROOT);
            RateInfo r = e.rate();
            if (r.known()) {
                rate = r;
                rateAt = now;
            }
            boolean keyProblem = (status == 400 || status == 401 || status == 403) && (detail.contains("api key") || detail.contains("api_key")
                    || detail.contains("authentication") || detail.contains("unauthorized") || detail.contains("invalid key"));
            if (status == 401 || keyProblem || (status == 403 && detail.contains("denied access"))) {
                broken = e.hint();
                return false;
            }
            if (status == 402 || detail.contains("insufficient_quota") || detail.contains("credit") || detail.contains("billing")
                    || detail.contains("payment required")) {
                rest(now, 6 * 3_600_000L, "out of credits");
                return false;
            }
            if (status == 413 || detail.contains("too large") || detail.contains("context length") || detail.contains("context_length")
                    || detail.contains("maximum context") || detail.contains("reduce the length")) {
                // No rest needed: the shorter prompt is the fix. (If there isn't one, the next brain answers.)
                compactUntil = now + 10 * 60_000L;
                return true;
            }
            if (status == 429) {
                if (r.daily() || RateInfo.mentionsDaily(detail)) {
                    dailyStreak++;
                    long wait = r.retryAfterMs() > 0 ? Math.max(r.retryAfterMs(), 10 * 60_000L)
                            : Math.min(6 * 3_600_000L, 30 * 60_000L << Math.min(4, dailyStreak - 1));
                    rest(now, wait, "daily limit reached");
                } else {
                    long wait = r.retryAfterMs() > 0 ? r.retryAfterMs()
                            : Math.max(r.requestsResetMs(), r.tokensResetMs()) > 0 ? Math.max(r.requestsResetMs(), r.tokensResetMs())
                            : Math.min(5 * 60_000L, 20_000L << Math.min(4, failStreak - 1));
                    rest(now, wait + 500, "too many requests");
                }
                return false;
            }
            if (status >= 500 || status == -1) {
                rest(now, Math.min(2 * 60_000L, 15_000L << Math.min(3, failStreak - 1)), "not answering");
                return false;
            }
            // Anything else (wrong model name, bad request): probably a settings problem. Try again later.
            rest(now, 2 * 60_000L, e.hint());
            return false;
        }

        private void rest(long now, long ms, String why) {
            if (now + ms > restUntil) {
                restUntil = now + ms;
                restReason = why;
            }
        }

        /** One line for /solace status and the setup screen, e.g. "resting 40s (too many requests)". */
        public synchronized String describe(long now) {
            String who = provider.settings().name() + " (" + provider.activeModel() + ")";
            if (broken != null) return who + ": NOT WORKING. " + broken;
            if (now < restUntil) return who + ": resting " + human(restUntil - now) + " (" + restReason + ")";
            StringBuilder sb = new StringBuilder(who).append(": ready");
            if (requestsInfoFresh(now)) sb.append(", ").append(rate.remainingRequests()).append(" requests left").append(rate.daily() ? " today" : "");
            if (tokensInfoFresh(now)) sb.append(", ").append(rate.remainingTokens()).append(" tokens left this minute");
            if (latencyMs > 0) sb.append(", answers in ~").append(Math.round(latencyMs / 100.0) / 10.0).append("s");
            if (successes + failures > 0) sb.append(" (").append(successes).append(" ok, ").append(failures).append(" failed)");
            return sb.toString();
        }

        public String describe() {
            return describe(System.currentTimeMillis());
        }
    }
}
