package com.solacemod.solace.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Google's Gemini API (Google AI Studio keys). */
public final class GeminiProvider implements LlmProvider {
    private final ProviderSettings settings;

    public GeminiProvider(ProviderSettings settings) {
        this.settings = settings;
    }

    @Override
    public ProviderSettings settings() {
        return settings;
    }

    @Override
    public CompletableFuture<String> complete(LlmRequest request) {
        String url = settings.baseUrl() + "/v1beta/models/" + URLEncoder.encode(settings.model(), StandardCharsets.UTF_8) + ":generateContent";
        return Http.postJson(url, Map.of("x-goog-api-key", settings.apiKey()), buildBody(request), settings.timeoutSeconds())
                .thenApply(GeminiProvider::extractText);
    }

    JsonObject buildBody(LlmRequest request) {
        JsonObject body = new JsonObject();
        JsonObject system = new JsonObject();
        system.add("parts", parts(request.system()));
        body.add("systemInstruction", system);

        JsonArray contents = new JsonArray();
        for (ChatMessage m : request.messages()) {
            JsonObject c = new JsonObject();
            c.addProperty("role", m.isUser() ? "user" : "model");
            c.add("parts", parts(m.content()));
            contents.add(c);
        }
        body.add("contents", contents);

        JsonObject gen = new JsonObject();
        gen.addProperty("temperature", request.temperature());
        // Gemini "thinking" models also spend output tokens on thinking.
        gen.addProperty("maxOutputTokens", Math.max(request.maxTokens(), 2000));
        if (request.wantJson()) gen.addProperty("responseMimeType", "application/json");
        body.add("generationConfig", gen);
        return body;
    }

    private static JsonArray parts(String text) {
        JsonArray parts = new JsonArray();
        JsonObject p = new JsonObject();
        p.addProperty("text", text);
        parts.add(p);
        return parts;
    }

    static String extractText(JsonObject response) {
        JsonArray candidates = response.has("candidates") ? response.getAsJsonArray("candidates") : null;
        if (candidates == null || candidates.isEmpty()) {
            return "{\"say\":\"...I don't want to talk about that.\",\"emotion\":\"sad\"}";
        }
        JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
        if (content == null || !content.has("parts")) throw new LlmException(200, "Empty Gemini reply");
        StringBuilder sb = new StringBuilder();
        for (JsonElement part : content.getAsJsonArray("parts")) {
            JsonObject p = part.getAsJsonObject();
            boolean thought = p.has("thought") && p.get("thought").getAsBoolean();
            if (!thought && p.has("text")) sb.append(p.get("text").getAsString());
        }
        if (sb.isEmpty()) throw new LlmException(200, "Empty Gemini reply");
        return sb.toString();
    }
}
