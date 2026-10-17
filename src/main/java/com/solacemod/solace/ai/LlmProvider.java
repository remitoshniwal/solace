package com.solacemod.solace.ai;

import java.util.concurrent.CompletableFuture;

/** Something that can turn a conversation into Solace's next reply text. Runs in the background, never on the game thread. */
public interface LlmProvider {
    CompletableFuture<String> complete(LlmRequest request);

    ProviderSettings settings();

    /** Builds the right provider for the given settings. */
    static LlmProvider create(ProviderSettings settings) {
        return switch (settings.protocol()) {
            case OPENAI -> new OpenAiCompatibleProvider(settings);
            case ANTHROPIC -> new AnthropicProvider(settings);
            case GEMINI -> new GeminiProvider(settings);
            case SCRIPTED -> throw new IllegalArgumentException("Scripted mode has no network provider");
        };
    }
}
