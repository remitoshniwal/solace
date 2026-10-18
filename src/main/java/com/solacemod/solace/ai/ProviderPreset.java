package com.solacemod.solace.ai;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Ready-made settings for popular providers, so a player only has to type the provider name and paste a key.
 * Model names change over time: if one stops working, set "model" in the config to a newer one from the provider's website.
 */
public record ProviderPreset(String name, Protocol protocol, String baseUrl, String defaultModel, boolean needsKey, String keyUrl) {

    private static final Map<String, ProviderPreset> PRESETS = new LinkedHashMap<>();

    static {
        add(new ProviderPreset("groq", Protocol.OPENAI, "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", true, "https://console.groq.com/keys"));
        add(new ProviderPreset("openai", Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5-mini", true, "https://platform.openai.com/api-keys"));
        add(new ProviderPreset("anthropic", Protocol.ANTHROPIC, "https://api.anthropic.com", "claude-opus-5-5", true, "https://console.anthropic.com/settings/keys"));
        add(new ProviderPreset("gemini", Protocol.GEMINI, "https://generativelanguage.googleapis.com", "gemini-flash-latest", true, "https://aistudio.google.com/apikey"));
        add(new ProviderPreset("openrouter", Protocol.OPENAI, "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct", true, "https://openrouter.ai/keys"));
        add(new ProviderPreset("deepseek", Protocol.OPENAI, "https://api.deepseek.com/v1", "deepseek-chat", true, "https://platform.deepseek.com/api_keys"));
        add(new ProviderPreset("mistral", Protocol.OPENAI, "https://api.mistral.ai/v1", "mistral-small-latest", true, "https://console.mistral.ai/api-keys"));
        add(new ProviderPreset("xai", Protocol.OPENAI, "https://api.x.ai/v1", "grok-3-mini", true, "https://console.x.ai"));
        add(new ProviderPreset("together", Protocol.OPENAI, "https://api.together.xyz/v1", "meta-llama/Llama-3.3-70B-Instruct-Turbo", true, "https://api.together.ai/settings/api-keys"));
        add(new ProviderPreset("cerebras", Protocol.OPENAI, "https://api.cerebras.ai/v1", "llama-3.3-70b", true, "https://cloud.cerebras.ai"));
        add(new ProviderPreset("ollama", Protocol.OPENAI, "http://localhost:11434/v1", "llama3.1", false, "https://ollama.com/download"));
        add(new ProviderPreset("lmstudio", Protocol.OPENAI, "http://localhost:1234/v1", "local-model", false, "https://lmstudio.ai"));
        // "custom" = any other OpenAI-compatible server. The player must fill in baseUrl and model.
        add(new ProviderPreset("custom", Protocol.OPENAI, "", "", false, ""));
        add(new ProviderPreset("scripted", Protocol.SCRIPTED, "", "", false, ""));
    }

    private static void add(ProviderPreset preset) {
        PRESETS.put(preset.name(), preset);
    }

    /** Looks up a preset by name (case-insensitive). Unknown names return null. */
    public static ProviderPreset get(String name) {
        if (name == null) return null;
        return PRESETS.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public static Iterable<String> names() {
        return PRESETS.keySet();
    }
}
