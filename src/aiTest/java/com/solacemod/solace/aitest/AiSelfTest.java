package com.solacemod.solace.aitest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solacemod.solace.ai.ChatMessage;
import com.solacemod.solace.ai.LlmException;
import com.solacemod.solace.ai.LlmProvider;
import com.solacemod.solace.ai.LlmRequest;
import com.solacemod.solace.ai.ProviderSettings;
import com.solacemod.solace.ai.ReplyParser;
import com.solacemod.solace.ai.SolaceReply;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests the AI code without Minecraft and without spending money:
 * a tiny fake server on this computer pretends to be each provider.
 */
public final class AiSelfTest {
    private static int passed;
    private static int failed;
    private static final AtomicReference<String> lastBody = new AtomicReference<>();
    private static final AtomicReference<String> lastPath = new AtomicReference<>();
    private static final AtomicReference<String> lastAuth = new AtomicReference<>();
    private static volatile int openAiCalls;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String reply = "{\"say\":\"Hello, friend.\",\"emotion\":\"happy\",\"actions\":[{\"type\":\"follow\"}],\"remember\":[\"likes cats\"],\"trust\":2}";

        server.createContext("/openai/chat/completions", ex -> {
            String body = read(ex);
            lastBody.set(body);
            lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            openAiCalls++;
            // Pretend to be a newer model that rejects max_tokens.
            if (body.contains("\"max_tokens\"")) {
                respond(ex, 400, "{\"error\":{\"message\":\"Unsupported parameter: 'max_tokens'. Use 'max_completion_tokens' instead.\"}}");
                return;
            }
            JsonObject msg = new JsonObject();
            msg.addProperty("role", "assistant");
            msg.addProperty("content", reply);
            respond(ex, 200, "{\"choices\":[{\"message\":" + msg + "}]}");
        });
        server.createContext("/anthropic/v1/messages", ex -> {
            lastBody.set(read(ex));
            lastAuth.set(ex.getRequestHeaders().getFirst("x-api-key") + "|" + ex.getRequestHeaders().getFirst("anthropic-version"));
            JsonObject text = new JsonObject();
            text.addProperty("type", "text");
            text.addProperty("text", "Sure! " + reply);
            respond(ex, 200, "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"thinking\",\"thinking\":\"\"}," + text + "]}");
        });
        server.createContext("/gemini/v1beta/models/", ex -> {
            lastPath.set(ex.getRequestURI().getPath());
            lastBody.set(read(ex));
            lastAuth.set(ex.getRequestHeaders().getFirst("x-goog-api-key"));
            JsonObject text = new JsonObject();
            text.addProperty("text", "```json\n" + reply + "\n```");
            respond(ex, 200, "{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"hmm\"}," + text + "]}}]}");
        });
        // A Gemini server where the configured model was retired: 404 for it, a model list, and a working newer model.
        server.createContext("/gem2/v1beta/models", ex -> {
            String path = ex.getRequestURI().getPath();
            if (ex.getRequestMethod().equals("GET")) {
                respond(ex, 200, "{\"models\":["
                        + "{\"name\":\"models/gemini-3.5-flash-lite\",\"supportedGenerationMethods\":[\"generateContent\"]},"
                        + "{\"name\":\"models/gemini-3.5-flash\",\"supportedGenerationMethods\":[\"generateContent\"]},"
                        + "{\"name\":\"models/gemini-4-flash\",\"supportedGenerationMethods\":[\"generateContent\"]},"
                        + "{\"name\":\"models/gemini-3.5-flash-image\",\"supportedGenerationMethods\":[\"generateContent\"]},"
                        + "{\"name\":\"models/text-embedding-004\",\"supportedGenerationMethods\":[\"embedContent\"]}]}");
                return;
            }
            read(ex);
            lastPath.set(path);
            if (path.contains("gemini-4-flash")) {
                respond(ex, 403, "{\"error\":{\"code\":403,\"message\":\"This model is not available on the free tier.\",\"status\":\"PERMISSION_DENIED\"}}");
                return;
            }
            if (path.contains("gemini-2.5-flash")) {
                respond(ex, 404, "{\"error\":{\"code\":404,\"message\":\"models/gemini-2.5-flash is not found\"}}");
                return;
            }
            JsonObject text = new JsonObject();
            text.addProperty("text", reply);
            respond(ex, 200, "{\"candidates\":[{\"content\":{\"parts\":[" + text + "]}}]}");
        });
        server.createContext("/bad/chat/completions", ex -> {
            read(ex);
            respond(ex, 401, "{\"error\":\"invalid key\"}");
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        LlmRequest req = new LlmRequest("You are Solace.",
                List.of(ChatMessage.user("hi"), ChatMessage.assistant("hello"), ChatMessage.user("follow me"), ChatMessage.user("please")),
                300, 0.9, true);

        // OpenAI-compatible, with automatic retry from max_tokens to max_completion_tokens
        ProviderSettings oa = ProviderSettings.resolve("custom", "sk-test", "test-model", base + "/openai/", 10, "");
        String out = LlmProvider.create(oa).complete(req).get();
        check("openai: reply text", out.equals(reply));
        check("openai: retried once with max_completion_tokens", openAiCalls == 2 && lastBody.get().contains("max_completion_tokens"));
        check("openai: bearer key sent", "Bearer sk-test".equals(lastAuth.get()));
        check("openai: system message first", JsonParser.parseString(lastBody.get()).getAsJsonObject()
                .getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString().equals("system"));

        // Anthropic
        ProviderSettings an = ProviderSettings.resolve("anthropic", "ak-test", "", base + "/anthropic", 10, "low");
        out = LlmProvider.create(an).complete(req).get();
        JsonObject anBody = JsonParser.parseString(lastBody.get()).getAsJsonObject();
        check("anthropic: default model", "claude-opus-5-5".equals(anBody.get("model").getAsString()));
        check("anthropic: headers", "ak-test|2023-06-01".equals(lastAuth.get()));
        check("anthropic: same-role messages merged", anBody.getAsJsonArray("messages").size() == 3);
        check("anthropic: effort low", anBody.getAsJsonObject("output_config").get("effort").getAsString().equals("low"));
        check("anthropic: no temperature sent", !anBody.has("temperature"));
        check("anthropic: thinking skipped", out.startsWith("Sure!"));
        check("anthropic: parse with chatter", ReplyParser.parse(out).say().equals("Hello, friend."));

        // Gemini
        ProviderSettings ge = ProviderSettings.resolve("gemini", "g-test", "gemini-test", base + "/gemini", 10, "");
        out = LlmProvider.create(ge).complete(req).get();
        check("gemini: url", lastPath.get().equals("/gemini/v1beta/models/gemini-test:generateContent"));
        check("gemini: key header", "g-test".equals(lastAuth.get()));
        check("gemini: assistant mapped to model role", lastBody.get().contains("\"role\":\"model\""));
        SolaceReply gr = ReplyParser.parse(out);
        check("gemini: fenced json parsed", gr.say().equals("Hello, friend.") && gr.actions().size() == 1);

        // Gemini: retired model is replaced automatically
        ProviderSettings retired = ProviderSettings.resolve("gemini", "g-test", "gemini-2.5-flash", base + "/gem2", 10, "");
        com.solacemod.solace.ai.GeminiProvider gp = (com.solacemod.solace.ai.GeminiProvider) LlmProvider.create(retired);
        out = gp.complete(req).get();
        check("gemini: recovers from retired model", out.equals(reply) && lastPath.get().endsWith("gemini-3.5-flash:generateContent"));
        check("gemini: skipped forbidden model, remembers working one", gp.activeModel().equals("gemini-3.5-flash"));
        LlmException forbidden = new LlmException(403, "HTTP 403: {\"error\":{\"code\":403,\"message\":\"Generative Language API has not been used in project 123\",\"status\":\"PERMISSION_DENIED\"}}");
        check("errors: provider's own reason is shown", forbidden.hint().contains("Generative Language API has not been used"));
        check("gemini: default model is the auto-updating alias", ProviderSettings.resolve("gemini", "k", "", "", 10, "").model().equals("gemini-flash-latest"));

        // Errors
        ProviderSettings bad = ProviderSettings.resolve("custom", "nope", "m", base + "/bad", 10, "");
        try {
            LlmProvider.create(bad).complete(req).get();
            check("error: should have failed", false);
        } catch (Exception e) {
            Throwable cause = com.solacemod.solace.ai.Http.unwrap(e);
            check("error: 401 gives key hint", cause instanceof LlmException le && le.status() == 401 && le.hint().contains("API key"));
        }
        ProviderSettings unreachable = ProviderSettings.resolve("custom", "", "m", "http://127.0.0.1:1", 5, "");
        try {
            LlmProvider.create(unreachable).complete(req).get();
            check("unreachable: should have failed", false);
        } catch (Exception e) {
            check("unreachable: friendly error", Http_unwrap(e) instanceof LlmException);
        }
        check("missing key detected", ProviderSettings.resolve("groq", "", "", "", 10, "").problem() != null);
        check("ollama needs no key", ProviderSettings.resolve("ollama", "", "", "", 10, "").problem() == null);
        check("unknown provider is null", ProviderSettings.resolve("nonsense", "", "", "", 10, "") == null);

        // Parser edge cases
        SolaceReply p1 = ReplyParser.parse("I think I'll just talk normally.");
        check("parser: plain text", p1.say().equals("I think I'll just talk normally.") && p1.actions().isEmpty());
        SolaceReply p2 = ReplyParser.parse("{\"say\":\"ok\",\"actions\":[\"stay\"],\"trust\":99}");
        check("parser: short action + trust clamp", p2.actions().get(0).get("type").getAsString().equals("stay") && p2.trust() == 3);
        SolaceReply p3 = ReplyParser.parse("{say: 'lenient', emotion: 'WEIRD'}");
        check("parser: lenient json + bad emotion", p3.say().equals("lenient") && p3.emotion().equals("calm"));
        SolaceReply p4 = ReplyParser.parse("{\"say\":\"§kcolor\\ncodes\"}");
        check("parser: strips formatting codes/newlines", !p4.say().contains("§") && !p4.say().contains("\n"));
        check("parser: keeps 'Solace' at start of sentence", ReplyParser.cleanSay("Solace is my name.").equals("Solace is my name."));
        check("parser: strips 'Solace:' prefix", ReplyParser.cleanSay("Solace: hi").equals("hi"));
        check("parser: braces in chatter", ReplyParser.parse("use {curly} ok {\"say\":\"x\"}").say().equals("x"));

        server.stop(0);
        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static Throwable Http_unwrap(Throwable e) {
        return com.solacemod.solace.ai.Http.unwrap(e);
    }

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        if (ok) passed++;
        else failed++;
    }

    private static String read(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
