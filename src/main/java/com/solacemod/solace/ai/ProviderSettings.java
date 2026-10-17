package com.solacemod.solace.ai;

/**
 * The final, resolved settings for one provider (preset defaults + whatever the player overrode).
 *
 * @param anthropicEffort only used by Anthropic: "low", "medium", "high"... (lower = faster and cheaper)
 */
public record ProviderSettings(String name, Protocol protocol, String baseUrl, String apiKey, String model,
                               int timeoutSeconds, String anthropicEffort) {

    /**
     * Combines a preset with the player's config values. Blank config values fall back to the preset.
     * Returns null if the provider name is unknown.
     */
    public static ProviderSettings resolve(String providerName, String apiKey, String model, String baseUrl,
                                           int timeoutSeconds, String anthropicEffort) {
        ProviderPreset preset = ProviderPreset.get(providerName);
        if (preset == null) return null;
        String url = isBlank(baseUrl) ? preset.baseUrl() : baseUrl.trim();
        String mdl = isBlank(model) ? preset.defaultModel() : model.trim();
        return new ProviderSettings(preset.name(), preset.protocol(), stripSlash(url), KeyResolver.resolve(apiKey), mdl,
                timeoutSeconds, isBlank(anthropicEffort) ? "low" : anthropicEffort.trim());
    }

    /** Returns a problem description if these settings can't possibly work, or null if they look fine. */
    public String problem() {
        if (protocol == Protocol.SCRIPTED) return null;
        ProviderPreset preset = ProviderPreset.get(name);
        if (isBlank(baseUrl)) return "No baseUrl set for provider '" + name + "'.";
        if (isBlank(model)) return "No model set for provider '" + name + "'.";
        if (preset != null && preset.needsKey() && isBlank(apiKey)) {
            return "No API key set for '" + name + "'. Get one at " + preset.keyUrl() + " and paste it into Solace setup (Esc > Mods > Solace > Config)";
        }
        return null;
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String stripSlash(String url) {
        String u = url;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    @Override
    public String toString() {
        // Never print the key itself.
        return name + " (" + model + ")";
    }
}
