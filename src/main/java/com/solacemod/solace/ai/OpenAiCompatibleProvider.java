package com.solacemod.solace.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
    private volatile java.util.function.Consumer<RateInfo> rateListener;

    @Override
    public void onRateInfo(java.util.function.Consumer<RateInfo> listener) {
        this.rateListener = listener;
    }
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

    /** Set when the configured model turned out not to exist and we switched to one that does. */
    private volatile String replacementModel;

    @Override
    public String activeModel() {
        return replacementModel != null ? replacementModel : settings.model();
    }

    @Override
    public CompletableFuture<String> complete(LlmRequest request) {
        String model = activeModel();
        return send(request, model, true).exceptionallyCompose(error -> {
            Throwable cause = Http.unwrap(error);
            // Providers retire model names regularly. If this one is gone, look up which models exist and try good ones.
            if (cause instanceof LlmException e && isModelMissing(e)) {
                // A model the player typed is never swapped for another: tell them what's available instead.
                if (settings.modelChosen()) return findCandidates().thenCompose(list -> CompletableFuture.failedFuture(modelNotFound(model, list)));
                return findCandidates().thenCompose(list -> tryCandidates(request, list, model, cause, 0));
            }
            return CompletableFuture.failedFuture(cause);
        });
    }

    /** A clear "that model doesn't exist" error that lists models the key can use. */
    LlmException modelNotFound(String model, List<String> available) {
        return notFound(model, settings.name(), available);
    }

    static LlmException notFound(String model, String provider, List<String> available) {
        String list = available.isEmpty() ? "" : " Models your key can use: " + String.join(", ", available.subList(0, Math.min(8, available.size()))) + ".";
        return new LlmException(404, "Model '" + model + "' wasn't found on " + provider + ". Check the spelling in Solace setup, "
                + "or clear the Model box to use the default." + list);
    }

    /** "Model not found / decommissioned / does not exist" errors look different on every provider. */
    static boolean isModelMissing(LlmException e) {
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (e.status() == 404) return true;
        return (e.status() == 400 || e.status() == 422) && m.contains("model")
                && (m.contains("decommission") || m.contains("does not exist") || m.contains("not found") || m.contains("not_found")
                || m.contains("invalid model") || m.contains("unknown model") || m.contains("no longer") || m.contains("deprecated")
                || m.contains("not a valid model"));
    }

    private CompletableFuture<String> tryCandidates(LlmRequest request, List<String> candidates, String original, Throwable originalError, int index) {
        if (index >= candidates.size() || index >= 5) return CompletableFuture.failedFuture(originalError);
        String candidate = candidates.get(index);
        if (candidate.equals(original)) return tryCandidates(request, candidates, original, originalError, index + 1);
        return send(request, candidate, true)
                .thenApply(text -> {
                    replacementModel = candidate;
                    return text;
                })
                .exceptionallyCompose(err -> {
                    Throwable c = Http.unwrap(err);
                    if (c instanceof LlmException e && (isModelMissing(e) || e.status() == 403 || e.status() == 429)) {
                        return tryCandidates(request, candidates, original, originalError, index + 1);
                    }
                    return CompletableFuture.failedFuture(c);
                });
    }

    /** Asks the provider which models exist (GET /models) and ranks the ones worth trying. */
    CompletableFuture<List<String>> findCandidates() {
        return Http.getJson(settings.baseUrl() + "/models", authHeaders(), settings.timeoutSeconds())
                .thenApply(json -> rankModels(json, ProviderPreset.get(settings.name())))
                .exceptionally(err -> List.of());
    }

    static List<String> rankModels(JsonObject json, ProviderPreset preset) {
        List<String> ids = new ArrayList<>();
        JsonElement data = json.has("data") ? json.get("data") : json.get("models");
        if (data != null && data.isJsonArray()) {
            for (JsonElement el : data.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject m = el.getAsJsonObject();
                String id = m.has("id") ? m.get("id").getAsString() : m.has("name") ? m.get("name").getAsString() : "";
                if (m.has("active") && !m.get("active").getAsBoolean()) continue;
                if (!id.isEmpty() && isChatModel(id)) ids.add(id);
            }
        }
        List<String> ranked = new ArrayList<>();
        // 1. The replacements we picked for this provider, in order.
        if (preset != null) for (String p : preset.preferredModels()) if (ids.contains(p)) ranked.add(p);
        // OpenRouter etc.: never jump to some random (possibly expensive) model.
        if (preset != null && !preset.autoPickAny()) return ranked;
        // 2. Well-known chat model families, then anything else that looks like a chat model.
        String[] families = {"gpt-oss", "llama", "qwen", "mistral", "gemma", "deepseek", "grok", "gpt-"};
        for (String f : families) ids.stream().filter(i -> i.contains(f) && !ranked.contains(i)).sorted(Comparator.reverseOrder()).forEach(ranked::add);
        ids.stream().filter(i -> !ranked.contains(i)).forEach(ranked::add);
        return ranked;
    }

    /** Filters out speech, image, embedding and safety-filter models. */
    static boolean isChatModel(String id) {
        String i = id.toLowerCase();
        return !i.matches(".*(whisper|tts|audio|transcri|speech|embed|guard|moderation|dall-e|image|realtime|rerank|orpheus|playai|compound|vision-only|search).*");
    }

    private Map<String, String> authHeaders() {
        Map<String, String> headers = new HashMap<>();
        if (!settings.apiKey().isEmpty()) headers.put("Authorization", "Bearer " + settings.apiKey());
        if ("openrouter".equals(settings.name())) {
            headers.put("HTTP-Referer", "https://github.com/solacemod/solace");
            headers.put("X-Title", "Solace Minecraft Mod");
        }
        return headers;
    }

    private CompletableFuture<String> send(LlmRequest request, String model, boolean mayRetry) {
        JsonObject body = buildBody(request, model);
        return Http.postJson(settings.baseUrl() + "/chat/completions", authHeaders(), body, settings.timeoutSeconds(), rateListener)
                .thenApply(OpenAiCompatibleProvider::extractText)
                .exceptionallyCompose(error -> {
                    Throwable cause = Http.unwrap(error);
                    if (mayRetry && cause instanceof LlmException e && e.status() == 400 && !isModelMissing(e) && adjustFor(e.getMessage())) {
                        return send(request, model, false);
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

    JsonObject buildBody(LlmRequest request, String model) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        String lower = model.toLowerCase();
        // Models that "think" before answering. Thinking counts toward the length limit, so they need more room.
        boolean thinkingModel = lower.contains("gpt-oss") || lower.contains("qwen3") || lower.contains("qwen-3") || lower.startsWith("gpt-5")
                || lower.matches("o\\d.*") || lower.contains("deepseek-r") || lower.contains("deepseek-v4-pro") || lower.contains("reason")
                || lower.startsWith("grok-4") || lower.contains("magistral");
        // Of those, these accept reasoning_effort to keep thinking short (others would reject it).
        boolean takesEffort = lower.contains("gpt-oss") || lower.startsWith("gpt-5") || lower.matches("o\\d.*");
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
            body.addProperty("max_completion_tokens", Math.max(request.maxTokens(), thinkingModel ? 2000 : request.maxTokens()));
        } else {
            // OpenRouter models may "think" first, and thinking counts toward the limit. Leave room so replies aren't cut off.
            int limit = "openrouter".equals(settings.name()) || thinkingModel ? Math.max(request.maxTokens(), 1500) : request.maxTokens();
            body.addProperty("max_tokens", limit);
        }
        if (!skipTemperature) body.addProperty("temperature", request.temperature());
        if (takesEffort && !"openrouter".equals(settings.name()) && !skipSpeedHints) {
            // Groq, Cerebras, OpenAI: ask "thinking" models to think briefly. Removed automatically if the server rejects it.
            body.addProperty("reasoning_effort", "low");
        }
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
