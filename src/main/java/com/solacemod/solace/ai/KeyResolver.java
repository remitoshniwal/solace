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
}
