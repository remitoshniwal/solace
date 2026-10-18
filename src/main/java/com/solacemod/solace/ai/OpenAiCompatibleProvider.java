package com.solacemod.solace.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The "OpenAI chat completions" format. Almost every provider supports it, including local ones like Ollama and LM Studio.
 *
 * Newer OpenAI models refuse some old parameters ("max_tokens", custom "temperature"). If the server complains,
 * we automatically retry once without the parameter it didn't like, and remember that for next time.
 */
public final class OpenAiCompatibleProvider implements LlmProvider {
    private final ProviderSettings settings;
    private volatile boolean useMaxCompletionTokens;
    private volatile boolean skipTemperature;
    private volatile boolean skipSpeedHints;

    public OpenAiCompatibleProvider(ProviderSettings settings) {
        this.settings = settings;
        // OpenAI's own reasoning models only accept these newer settings.
        this.useMaxCompletionTokens = "openai".equals(settings.name());
        this.skipTemperature = "openai".equals(settings.name());
    }

    @Override
    public ProviderSettings settings() {
        return settings;
    }

    @Override
    public CompletableFuture<String> complete(LlmRequest request) {
        return send(request, true);
    }

    private CompletableFuture<String> send(LlmRequest request, boolean mayRetry) {
        JsonObject body = buildBody(request);
        Map<String, String> headers = new HashMap<>();
        if (!settings.apiKey().isEmpty()) headers.put("Authorization", "Bearer " + settings.apiKey());
        if ("openrouter".equals(settings.name())) {
            headers.put("HTTP-Referer", "https://github.com/solacemod/solace");
            headers.put("X-Title", "Solace Minecraft Mod");
        }
        return Http.postJson(settings.baseUrl() + "/chat/completions", headers, body, settings.timeoutSeconds())
                .thenApply(OpenAiCompatibleProvider::extractText)
                .exceptionallyCompose(error -> {
                    Throwable cause = Http.unwrap(error);
                    if (mayRetry && cause instanceof LlmException e && e.status() == 400 && adjustFor(e.getMessage())) {
                        return send(request, false);
                    }
                    return CompletableFuture.failedFuture(cause);
                });
    }

    /** Looks at a 400 error and turns off whatever parameter the server rejected. Returns true if something changed. */
    private boolean adjustFor(String error) {
        String msg = error == null ? "" : error.toLowerCase();
        boolean changed = false;
        if (msg.contains("max_completion_tokens") && !useMaxCompletionTokens) {
            useMaxCompletionTokens = true;
            changed = true;
        } else if (msg.contains("max_completion_tokens") && useMaxCompletionTokens) {
            useMaxCompletionTokens = false;
            changed = true;
        }
        if (msg.contains("temperature") && !skipTemperature) {
            skipTemperature = true;
            changed = true;
        }
        if ((msg.contains("reasoning") || msg.contains("provider")) && !skipSpeedHints) {
            skipSpeedHints = true;
            changed = true;
        }
        return changed;
    }

    JsonObject buildBody(LlmRequest request) {
        JsonObject body = new JsonObject();
        body.addProperty("model", settings.model());
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", request.system());
        messages.add(system);
        for (ChatMessage m : request.messages()) {
            JsonObject msg = new JsonObject();
            msg.addProperty("role", m.role());
            msg.addProperty("content", m.content());
            messages.add(msg);
        }
        body.add("messages", messages);
        if (useMaxCompletionTokens) {
            // Reasoning models spend some of this on hidden thinking, so give them room.
            body.addProperty("max_completion_tokens", Math.max(request.maxTokens(), 2000));
        } else {
            body.addProperty("max_tokens", request.maxTokens());
        }
        if (!skipTemperature) body.addProperty("temperature", request.temperature());
        if ("openrouter".equals(settings.name()) && !skipSpeedHints) {
            // Send the request to whichever server hosting this model answers fastest.
            JsonObject provider = new JsonObject();
            provider.addProperty("sort", "latency");
            body.add("provider", provider);
            // "Thinking" models can spend 20+ seconds reasoning first; chat replies don't need much. Ignored by other models.
            JsonObject reasoning = new JsonObject();
            reasoning.addProperty("effort", "low");
            reasoning.addProperty("exclude", true);
            body.add("reasoning", reasoning);
        }
        return body;
    }

    static String extractText(JsonObject response) {
        JsonArray choices = response.has("choices") && response.get("choices").isJsonArray() ? response.getAsJsonArray("choices") : null;
        if (choices == null || choices.isEmpty()) throw new LlmException(200, "No choices in response: " + Http.shorten(response.toString()));
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null) throw new LlmException(200, "No message in response");
        JsonElement content = message.get("content");
        if (content == null || content.isJsonNull()) {
            // Some servers return a refusal field instead of content.
            if (message.has("refusal") && !message.get("refusal").isJsonNull()) return message.get("refusal").getAsString();
            throw new LlmException(200, "Empty reply (the model may have used all its tokens thinking; raise maxReplyTokens)");
        }
        if (content.isJsonArray()) {
            // A few servers return content as a list of parts.
            StringBuilder sb = new StringBuilder();
            for (JsonElement part : content.getAsJsonArray()) {
                if (part.isJsonObject() && part.getAsJsonObject().has("text")) sb.append(part.getAsJsonObject().get("text").getAsString());
            }
            return sb.toString();
        }
        return content.getAsString();
    }
}
