package com.solacemod.solace.ai;

/**
 * Lets players write "env:GROQ_API_KEY" in the config instead of pasting the key itself.
 * That way the key lives in an environment variable and never ends up in a shared config file or modpack.
 */
public final class KeyResolver {
    private KeyResolver() {
    }

    public static String resolve(String raw) {
        if (raw == null) return "";
        // Keys never contain spaces; remove any that sneak in when copying (including line breaks and invisible ones).
        String trimmed = raw.replaceAll("[\\s\\u200B\\u00A0\\uFEFF]", "");
        if (trimmed.regionMatches(true, 0, "env:", 0, 4)) {
            String value = System.getenv(trimmed.substring(4));
            return value == null ? "" : value.trim();
        }
        return trimmed;
    }

    /**
     * Hides anything that looks like a key (a long run of letters, digits, dashes or underscores) in a message.
     * Error messages from providers sometimes repeat part of the key; this is used before showing or saving
     * errors from a player's own brain, so they never land in the server's log.
     */
    public static String hideKeys(String text) {
        if (text == null) return "";
        return text.replaceAll("[A-Za-z0-9_\\-]{24,}", "[hidden]");
    }
}
