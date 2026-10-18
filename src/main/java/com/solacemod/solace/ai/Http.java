package com.solacemod.solace.ai;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Small helper around Java's built-in HTTP client, shared by every provider. */
public final class Http {
    public static final Gson GSON = new Gson();

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private Http() {
    }

    /** POSTs JSON and returns the parsed JSON response, or fails with an {@link LlmException}. */
    public static CompletableFuture<JsonObject> postJson(String url, Map<String, String> headers, JsonObject body, int timeoutSeconds) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Solace-Minecraft-Mod/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)));
        headers.forEach(builder::header);
        return CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        throw new LlmException(cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
                    }
                    if (response.statusCode() / 100 != 2) {
                        throw new LlmException(response.statusCode(), "HTTP " + response.statusCode() + ": " + shorten(response.body()));
                    }
                    try {
                        JsonElement parsed = JsonParser.parseString(response.body());
                        if (!parsed.isJsonObject()) throw new LlmException(response.statusCode(), "Response was not a JSON object");
                        return parsed.getAsJsonObject();
                    } catch (LlmException e) {
                        throw e;
                    } catch (RuntimeException e) {
                        throw new LlmException(response.statusCode(), "Could not read response: " + shorten(response.body()));
                    }
                });
    }

    /** GETs JSON (used to look up which models a provider has). */
    public static CompletableFuture<JsonObject> getJson(String url, Map<String, String> headers, int timeoutSeconds) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .header("User-Agent", "Solace-Minecraft-Mod/1.0")
                .GET();
        headers.forEach(builder::header);
        return CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        throw new LlmException(cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
                    }
                    if (response.statusCode() / 100 != 2) {
                        throw new LlmException(response.statusCode(), "HTTP " + response.statusCode() + ": " + shorten(response.body()));
                    }
                    JsonElement parsed = JsonParser.parseString(response.body());
                    if (!parsed.isJsonObject()) throw new LlmException(response.statusCode(), "Response was not a JSON object");
                    return parsed.getAsJsonObject();
                });
    }

    /** POSTs raw bytes (used for audio) and returns the raw response bytes. */
    public static CompletableFuture<byte[]> postForBytes(String url, Map<String, String> headers, byte[] body, String contentType, int timeoutSeconds) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .header("Content-Type", contentType)
                .header("User-Agent", "Solace-Minecraft-Mod/1.0")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        headers.forEach(builder::header);
        return CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
                .handle((response, error) -> {
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        throw new LlmException(cause.getClass().getSimpleName() + ": " + cause.getMessage(), cause);
                    }
                    if (response.statusCode() / 100 != 2) {
                        throw new LlmException(response.statusCode(), "HTTP " + response.statusCode() + ": " + shorten(new String(response.body())));
                    }
                    return response.body();
                });
    }

    /** Unwraps the real error out of CompletableFuture's wrapper exceptions. */
    public static Throwable unwrap(Throwable t) {
        Throwable cur = t;
        while ((cur instanceof java.util.concurrent.CompletionException || cur instanceof java.util.concurrent.ExecutionException)
                && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur;
    }

    static String shorten(String s) {
        if (s == null) return "";
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 1500 ? one.substring(0, 1500) + "..." : one;
    }
}
