package com.solacemod.solace.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    /** Set when Google says the configured model doesn't exist and we switched to one that does. */
    private volatile String replacementModel;

    @Override
    public CompletableFuture<String> complete(LlmRequest request) {
        return send(request, replacementModel != null ? replacementModel : settings.model(), true);
    }

    /** The model actually being used (may differ from the config if the configured one was retired). */
    public String activeModel() {
        return replacementModel != null ? replacementModel : settings.model();
    }

    private CompletableFuture<String> send(LlmRequest request, String model, boolean mayRecover) {
        return attempt(request, model).exceptionallyCompose(error -> {
            Throwable cause = Http.unwrap(error);
            // Google retires model names regularly. If this one is gone, ask Google which models exist and try them in order.
            if (mayRecover && cause instanceof LlmException e && e.status() == 404) {
                return findWorkingModels().thenCompose(candidates -> tryCandidates(request, candidates, model, cause, 0));
            }
            return CompletableFuture.failedFuture(cause);
        });
    }

    /** Tries each candidate model until one answers. Skips models this key isn't allowed to use (403/404/429). */
    private CompletableFuture<String> tryCandidates(LlmRequest request, List<String> candidates, String original, Throwable originalError, int index) {
        if (index >= candidates.size() || index >= 6) return CompletableFuture.failedFuture(originalError);
        String candidate = candidates.get(index);
        if (candidate.equals(original)) return tryCandidates(request, candidates, original, originalError, index + 1);
        return attempt(request, candidate)
                .thenApply(text -> {
                    replacementModel = candidate;
                    return text;
                })
                .exceptionallyCompose(err -> {
                    Throwable c = Http.unwrap(err);
                    if (c instanceof LlmException e && (e.status() == 403 || e.status() == 404 || e.status() == 429)) {
                        return tryCandidates(request, candidates, original, originalError, index + 1);
                    }
                    return CompletableFuture.failedFuture(c);
                });
    }

    private CompletableFuture<String> attempt(LlmRequest request, String model) {
        String url = settings.baseUrl() + "/v1beta/models/" + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent";
        return Http.postJson(url, Map.of("x-goog-api-key", settings.apiKey()), buildBody(request), settings.timeoutSeconds())
                .thenApply(GeminiProvider::extractText);
    }

    /** Lists the models this key can use, best choices first. Empty if the list can't be fetched. */
    CompletableFuture<List<String>> findWorkingModels() {
        return Http.getJson(settings.baseUrl() + "/v1beta/models?pageSize=200", Map.of("x-goog-api-key", settings.apiKey()), settings.timeoutSeconds())
                .thenApply(GeminiProvider::rankModels)
                .exceptionally(err -> List.of());
    }

    /** Orders models: the auto-updating alias, then full Flash models (newest first), then Flash-Lite, then anything else. */
    static List<String> rankModels(JsonObject list) {
        List<String> usable = new ArrayList<>();
        if (list.has("models")) {
            for (JsonElement el : list.getAsJsonArray("models")) {
                JsonObject m = el.getAsJsonObject();
                String name = m.has("name") ? m.get("name").getAsString().replace("models/", "") : "";
                boolean canChat = false;
                if (m.has("supportedGenerationMethods")) {
                    for (JsonElement method : m.getAsJsonArray("supportedGenerationMethods")) {
                        if ("generateContent".equals(method.getAsString())) canChat = true;
                    }
                }
                if (canChat && name.startsWith("gemini") && !name.matches(".*(image|tts|audio|live|embedding|exp|thinking|computer).*")) usable.add(name);
            }
        }
        List<String> ranked = new ArrayList<>();
        for (String alias : List.of("gemini-flash-latest", "gemini-flash-lite-latest")) if (usable.contains(alias)) ranked.add(alias);
        Comparator<String> newestFirst = Comparator.<String>naturalOrder().reversed();
        usable.stream().filter(n -> n.contains("flash") && !n.contains("lite") && !n.contains("preview") && !ranked.contains(n)).sorted(newestFirst).forEach(ranked::add);
        usable.stream().filter(n -> n.contains("flash") && n.contains("lite") && !n.contains("preview") && !ranked.contains(n)).sorted(newestFirst).forEach(ranked::add);
        usable.stream().filter(n -> !ranked.contains(n)).sorted(newestFirst).forEach(ranked::add);
        return ranked;
    }

    static String pickModel(JsonObject list) {
        List<String> ranked = rankModels(list);
        return ranked.isEmpty() ? null : ranked.get(0);
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
