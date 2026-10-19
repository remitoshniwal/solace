package com.solacemod.solace.ai;

import java.time.Instant;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a provider told us about our limits, read from its response headers (and, for errors, its message).
 * Every number is -1 when the provider didn't say. Times are milliseconds from when the response arrived.
 *
 * Different providers use different header names, so this checks all the common ones:
 * OpenAI, Groq, Together, xAI, DeepSeek ("x-ratelimit-*"), Cerebras ("x-ratelimit-*-day/-minute"),
 * Anthropic ("anthropic-ratelimit-*"), OpenRouter ("x-ratelimit-remaining/reset") and the standard "retry-after".
 */
public record RateInfo(long remainingRequests, long limitRequests, long requestsResetMs,
                       long remainingTokens, long limitTokens, long tokensResetMs,
                       long retryAfterMs, boolean daily) {
    public static final RateInfo NONE = new RateInfo(-1, -1, -1, -1, -1, -1, -1, false);

    private static final Pattern TRY_AGAIN = Pattern.compile("(?:try again|retry) in ([0-9hms.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RETRY_DELAY = Pattern.compile("\"retryDelay\"\\s*:\\s*\"([0-9.]+)s\"");

    public boolean known() {
        return remainingRequests >= 0 || remainingTokens >= 0 || retryAfterMs >= 0;
    }

    /**
     * @param header looks up a response header by name (case doesn't matter), or returns null
     * @param body   the response body for errors (to find "try again in 7s" and "per day"), or null
     */
    public static RateInfo parse(Function<String, String> header, String body, long nowMs) {
        long remReq = -1, limReq = -1, resetReq = -1, remTok = -1, limTok = -1, resetTok = -1, retry = -1;
        boolean daily = false;

        String v;
        if ((v = header.apply("x-ratelimit-remaining-requests")) != null) remReq = num(v);
        else if ((v = header.apply("x-ratelimit-remaining-requests-minute")) != null) remReq = num(v);
        else if ((v = header.apply("anthropic-ratelimit-requests-remaining")) != null) remReq = num(v);
        else if ((v = header.apply("x-ratelimit-remaining-requests-day")) != null) {
            remReq = num(v);
            daily = true;
        } else if ((v = header.apply("x-ratelimit-remaining")) != null) remReq = num(v);

        if ((v = header.apply("x-ratelimit-limit-requests")) != null) limReq = num(v);
        else if ((v = header.apply("x-ratelimit-limit-requests-minute")) != null) limReq = num(v);
        else if ((v = header.apply("anthropic-ratelimit-requests-limit")) != null) limReq = num(v);
        else if ((v = header.apply("x-ratelimit-limit-requests-day")) != null) limReq = num(v);
        else if ((v = header.apply("x-ratelimit-limit")) != null) limReq = num(v);

        if ((v = header.apply("x-ratelimit-reset-requests")) != null) resetReq = duration(v);
        else if ((v = header.apply("x-ratelimit-reset-requests-minute")) != null) resetReq = duration(v);
        else if ((v = header.apply("anthropic-ratelimit-requests-reset")) != null) resetReq = timestamp(v, nowMs);
        else if ((v = header.apply("x-ratelimit-reset-requests-day")) != null) resetReq = duration(v);
        else if ((v = header.apply("x-ratelimit-reset")) != null) resetReq = epochOrDuration(v, nowMs);

        if ((v = header.apply("x-ratelimit-remaining-tokens")) != null) remTok = num(v);
        else if ((v = header.apply("x-ratelimit-remaining-tokens-minute")) != null) remTok = num(v);
        else if ((v = header.apply("anthropic-ratelimit-input-tokens-remaining")) != null) remTok = num(v);
        else if ((v = header.apply("anthropic-ratelimit-tokens-remaining")) != null) remTok = num(v);

        if ((v = header.apply("x-ratelimit-limit-tokens")) != null) limTok = num(v);
        else if ((v = header.apply("x-ratelimit-limit-tokens-minute")) != null) limTok = num(v);
        else if ((v = header.apply("anthropic-ratelimit-input-tokens-limit")) != null) limTok = num(v);
        else if ((v = header.apply("anthropic-ratelimit-tokens-limit")) != null) limTok = num(v);

        if ((v = header.apply("x-ratelimit-reset-tokens")) != null) resetTok = duration(v);
        else if ((v = header.apply("x-ratelimit-reset-tokens-minute")) != null) resetTok = duration(v);
        else if ((v = header.apply("anthropic-ratelimit-input-tokens-reset")) != null) resetTok = timestamp(v, nowMs);
        else if ((v = header.apply("anthropic-ratelimit-tokens-reset")) != null) resetTok = timestamp(v, nowMs);

        if ((v = header.apply("retry-after-ms")) != null) retry = num(v);
        else if ((v = header.apply("retry-after")) != null) retry = retryAfter(v, nowMs);

        if (body != null) {
            Matcher m = TRY_AGAIN.matcher(body);
            if (retry < 0 && m.find()) retry = duration(m.group(1));
            m = RETRY_DELAY.matcher(body);
            if (retry < 0 && m.find()) retry = (long) (Double.parseDouble(m.group(1)) * 1000);
            daily |= mentionsDaily(body);
        }
        return new RateInfo(remReq, limReq, resetReq, remTok, limTok, resetTok, retry, daily);
    }

    /** True if an error message says a per-day limit was hit (rather than a per-minute one). */
    public static boolean mentionsDaily(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("per day") || t.contains("perday") || t.contains("per_day") || t.contains("daily")
                || t.contains("(rpd)") || t.contains("(tpd)");
    }

    private static long num(String v) {
        try {
            return (long) Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Reads durations like "7.66s", "2m59.56s", "1h2m", "120ms" or a plain number of seconds ("30", "0.5").
     * Returns milliseconds, or -1 if it can't be read.
     */
    public static long duration(String v) {
        String s = v.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return -1;
        try {
            return (long) (Double.parseDouble(s) * 1000);
        } catch (NumberFormatException ignored) {
            // has units
        }
        Matcher m = Pattern.compile("([0-9.]+)(ms|h|m|s)").matcher(s);
        double total = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            double n = Double.parseDouble(m.group(1));
            total += switch (m.group(2)) {
                case "ms" -> n;
                case "h" -> n * 3_600_000;
                case "m" -> n * 60_000;
                default -> n * 1000;
            };
        }
        return any ? (long) total : -1;
    }

    private static long timestamp(String v, long nowMs) {
        try {
            return Math.max(0, Instant.parse(v.trim()).toEpochMilli() - nowMs);
        } catch (RuntimeException e) {
            return duration(v);
        }
    }

    /** OpenRouter sends a time in epoch milliseconds; others send seconds. */
    private static long epochOrDuration(String v, long nowMs) {
        long n = num(v);
        if (n > 1_000_000_000_000L) return Math.max(0, n - nowMs);
        if (n > 1_000_000_000L) return Math.max(0, n * 1000 - nowMs);
        return duration(v);
    }

    private static long retryAfter(String v, long nowMs) {
        long d = duration(v);
        if (d >= 0) return d;
        try {
            return Math.max(0, java.time.ZonedDateTime.parse(v.trim(), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli() - nowMs);
        } catch (RuntimeException e) {
            return -1;
        }
    }
}
