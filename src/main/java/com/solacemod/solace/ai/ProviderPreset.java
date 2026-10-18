package com.solacemod.solace.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ready-made settings for popular providers, so a player only has to type the provider name and paste a key.
 * Model names change over time: if one stops working, set "model" in the config to a newer one from the provider's website.
 */
public record ProviderPreset(String name, Protocol protocol, String baseUrl, String defaultModel, boolean needsKey, String keyUrl,
                             List<String> preferredModels, boolean autoPickAny) {

    /** Short form: no special replacement list; any chat model may be picked if the configured one disappears. */
    public ProviderPreset(String name, Protocol protocol, String baseUrl, String defaultModel, boolean needsKey, String keyUrl) {
        this(name, protocol, baseUrl, defaultModel, needsKey, keyUrl, List.of(), true);
    }

    private static final Map<String, ProviderPreset> PRESETS = new LinkedHashMap<>();

    static {
        add(new ProviderPreset("groq", Protocol.OPENAI, "https://api.groq.com/openai/v1", "openai/gpt-oss-120b", true, "https://console.groq.com/keys",
                List.of("openai/gpt-oss-120b", "openai/gpt-oss-20b", "qwen/qwen3.6-27b", "llama-3.1-8b-instant"), true));
        add(new ProviderPreset("openai", Protocol.OPENAI, "https://api.openai.com/v1", "gpt-5.4-mini", true, "https://platform.openai.com/api-keys",
                List.of("gpt-5.4-mini", "gpt-5-mini", "gpt-5.4-nano", "gpt-5-nano", "gpt-4.1-mini"), false));
        add(new ProviderPreset("anthropic", Protocol.ANTHROPIC, "https://api.anthropic.com", "claude-opus-5-5", true, "https://console.anthropic.com/settings/keys"));
        add(new ProviderPreset("gemini", Protocol.GEMINI, "https://generativelanguage.googleapis.com", "gemini-flash-latest", true, "https://aistudio.google.com/apikey"));
        // OpenRouter has hundreds of models with very different prices, so only switch to ones from this list.
        add(new ProviderPreset("openrouter", Protocol.OPENAI, "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct", true, "https://openrouter.ai/keys",
                List.of("meta-llama/llama-3.3-70b-instruct", "openai/gpt-oss-120b", "openai/gpt-oss-20b", "google/gemini-flash-latest", "qwen/qwen3.6-27b"), false));
        add(new ProviderPreset("deepseek", Protocol.OPENAI, "https://api.deepseek.com", "deepseek-v4-flash", true, "https://platform.deepseek.com/api_keys",
                List.of("deepseek-v4-flash", "deepseek-v4-pro"), false));
        add(new ProviderPreset("mistral", Protocol.OPENAI, "https://api.mistral.ai/v1", "mistral-small-latest", true, "https://console.mistral.ai/api-keys",
                List.of("mistral-small-latest", "mistral-medium-latest", "ministral-8b-latest"), false));
        add(new ProviderPreset("xai", Protocol.OPENAI, "https://api.x.ai/v1", "grok-4.3", true, "https://console.x.ai",
                List.of("grok-4.3", "grok-4.7", "grok-4.6", "grok-4.5"), false));
        add(new ProviderPreset("together", Protocol.OPENAI, "https://api.together.xyz/v1", "meta-llama/Llama-3.3-70B-Instruct-Turbo", true, "https://api.together.ai/settings/api-keys",
                List.of("meta-llama/Llama-3.3-70B-Instruct-Turbo", "openai/gpt-oss-120b", "openai/gpt-oss-20b"), false));
        add(new ProviderPreset("cerebras", Protocol.OPENAI, "https://api.cerebras.ai/v1", "gpt-oss-120b", true, "https://cloud.cerebras.ai",
                List.of("gpt-oss-120b", "qwen-3.8-27b"), true));
        add(new ProviderPreset("ollama", Protocol.OPENAI, "http://localhost:11434/v1", "llama3.1", false, "https://ollama.com/download"));
        add(new ProviderPreset("lmstudio", Protocol.OPENAI, "http://localhost:1234/v1", "local-model", false, "https://lmstudio.ai"));
        // "custom" = any other OpenAI-compatible server. The player must fill in baseUrl and model.
        add(new ProviderPreset("custom", Protocol.OPENAI, "", "", false, ""));
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
