package com.solacemod.solace.ai;

import java.util.concurrent.CompletableFuture;

/** Something that can turn a conversation into Solace's next reply text. Runs in the background, never on the game thread. */
public interface LlmProvider {
    CompletableFuture<String> complete(LlmRequest request);

    ProviderSettings settings();

    /** The model actually in use. Can differ from the config if the configured model was retired and replaced. */
    default String activeModel() {
        return settings().model();
    }

    /** Tells the provider where to report rate-limit information from each successful reply. */
    default void onRateInfo(java.util.function.Consumer<RateInfo> listener) {
    }

    /** Builds the right provider for the given settings. */
    static LlmProvider create(ProviderSettings settings) {
        return switch (settings.protocol()) {
            case OPENAI -> new OpenAiCompatibleProvider(settings);
            case ANTHROPIC -> new AnthropicProvider(settings);
            case GEMINI -> new GeminiProvider(settings);
        };
    }
}
