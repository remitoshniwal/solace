package com.solacemod.solace.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Anthropic's Messages API (Claude models).
 * Current Claude models always think a little before answering, so we keep effort low for fast chat replies
 * and leave extra room in max_tokens for that thinking.
 */
public final class AnthropicProvider implements LlmProvider {
    /** Models that support server-side refusal fallbacks (if one model declines, another answers). */
    private static final Set<String> FALLBACK_MODELS = Set.of("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5");

    private final ProviderSettings settings;

    public AnthropicProvider(ProviderSettings settings) {
        this.settings = settings;
    }

    @Override
    public ProviderSettings settings() {
        return settings;
    }

    @Override
    public CompletableFuture<String> complete(LlmRequest request) {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", settings.apiKey());
        headers.put("anthropic-version", "2023-06-01");
        boolean fallbacks = FALLBACK_MODELS.contains(settings.model());
        if (fallbacks) headers.put("anthropic-beta", "server-side-fallback-2026-07-01");
        return Http.postJson(settings.baseUrl() + "/v1/messages", headers, buildBody(request, fallbacks), settings.timeoutSeconds())
                .thenApply(AnthropicProvider::extractText);
    }

    JsonObject buildBody(LlmRequest request, boolean fallbacks) {
        JsonObject body = new JsonObject();
        body.addProperty("model", settings.model());
        // Thinking tokens count toward max_tokens, so leave headroom. You only pay for tokens actually used.
        body.addProperty("max_tokens", Math.max(request.maxTokens(), 4000));
        body.addProperty("system", request.system());
        JsonObject outputConfig = new JsonObject();
        outputConfig.addProperty("effort", settings.anthropicEffort());
        body.add("output_config", outputConfig);
        if (fallbacks) body.addProperty("fallbacks", "default");

        JsonArray messages = new JsonArray();
        // Anthropic requires user/assistant to alternate, so merge neighbours with the same role.
        String lastRole = null;
        JsonObject last = null;
        for (ChatMessage m : request.messages()) {
            if (m.role().equals(lastRole) && last != null) {
                last.addProperty("content", last.get("content").getAsString() + "\n" + m.content());
                continue;
            }
            JsonObject msg = new JsonObject();
            msg.addProperty("role", m.role());
            msg.addProperty("content", m.content());
            messages.add(msg);
            last = msg;
            lastRole = m.role();
        }
        // The first message must come from the user.
        if (!messages.isEmpty() && !"user".equals(messages.get(0).getAsJsonObject().get("role").getAsString())) {
            JsonObject start = new JsonObject();
            start.addProperty("role", "user");
            start.addProperty("content", "[EVENT] The conversation continues.");
            JsonArray fixed = new JsonArray();
            fixed.add(start);
            fixed.addAll(messages);
            messages = fixed;
        }
        body.add("messages", messages);
        return body;
    }

    static String extractText(JsonObject response) {
        String stop = response.has("stop_reason") && !response.get("stop_reason").isJsonNull() ? response.get("stop_reason").getAsString() : "";
        if ("refusal".equals(stop)) {
            // In character, so the game doesn't break.
            return "{\"say\":\"...I don't want to talk about that.\",\"emotion\":\"sad\"}";
        }
        JsonArray content = response.has("content") && response.get("content").isJsonArray() ? response.getAsJsonArray("content") : new JsonArray();
        StringBuilder sb = new StringBuilder();
        for (JsonElement block : content) {
            JsonObject b = block.getAsJsonObject();
            // Skip "thinking" blocks; only "text" blocks are the actual answer.
            if ("text".equals(b.has("type") ? b.get("type").getAsString() : "") && b.has("text")) sb.append(b.get("text").getAsString());
        }
        if (sb.isEmpty()) throw new LlmException(200, "Empty reply (stop_reason=" + stop + ")");
        return sb.toString();
    }
}
