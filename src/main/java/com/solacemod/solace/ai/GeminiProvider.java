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
        String url = settings.baseUrl() + "/v1beta/models/" + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent";
        return Http.postJson(url, Map.of("x-goog-api-key", settings.apiKey()), buildBody(request), settings.timeoutSeconds())
                .thenApply(GeminiProvider::extractText)
                .exceptionallyCompose(error -> {
                    Throwable cause = Http.unwrap(error);
                    // Google retires model names regularly. If this one is gone, ask Google which models exist and use one.
                    if (mayRecover && cause instanceof LlmException e && e.status() == 404) {
                        return findWorkingModel().thenCompose(found -> {
                            if (found == null || found.equals(model)) return CompletableFuture.failedFuture(cause);
                            replacementModel = found;
                            return send(request, found, false);
                        });
                    }
                    return CompletableFuture.failedFuture(cause);
                });
    }

    /** Lists the models this key can use and picks the best Flash model (fast and on the free tier). */
    CompletableFuture<String> findWorkingModel() {
        return Http.getJson(settings.baseUrl() + "/v1beta/models?pageSize=200", Map.of("x-goog-api-key", settings.apiKey()), settings.timeoutSeconds())
                .thenApply(GeminiProvider::pickModel)
                .exceptionally(err -> null);
    }

    static String pickModel(JsonObject list) {
        if (!list.has("models")) return null;
        java.util.List<String> usable = new java.util.ArrayList<>();
        for (JsonElement el : list.getAsJsonArray("models")) {
            JsonObject m = el.getAsJsonObject();
            String name = m.has("name") ? m.get("name").getAsString().replace("models/", "") : "";
            boolean canChat = false;
            if (m.has("supportedGenerationMethods")) {
                for (JsonElement method : m.getAsJsonArray("supportedGenerationMethods")) {
                    if ("generateContent".equals(method.getAsString())) canChat = true;
                }
            }
            if (canChat && !name.isEmpty()) usable.add(name);
        }
        if (usable.contains("gemini-flash-latest")) return "gemini-flash-latest";
        // Otherwise the newest plain "flash" model (skip image, speech, live and preview variants).
        java.util.List<String> flash = usable.stream()
                .filter(n -> n.startsWith("gemini-") && n.contains("flash"))
                .filter(n -> !n.matches(".*(image|tts|audio|live|embedding|exp|preview|thinking).*"))
                .toList();
        return flash.stream().filter(n -> !n.contains("lite")).max(java.util.Comparator.naturalOrder())
                .or(() -> flash.stream().max(java.util.Comparator.naturalOrder()))
                .orElse(usable.stream().filter(n -> n.startsWith("gemini-")).max(java.util.Comparator.naturalOrder()).orElse(null));
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
