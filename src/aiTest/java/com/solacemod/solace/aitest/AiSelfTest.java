package com.solacemod.solace.aitest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solacemod.solace.ai.BrainPool;
import com.solacemod.solace.ai.ChatMessage;
import com.solacemod.solace.ai.RateInfo;
import com.solacemod.solace.ai.KeyResolver;
import com.solacemod.solace.ai.LlmException;
import com.solacemod.solace.ai.LlmProvider;
import com.solacemod.solace.ai.LlmRequest;
import com.solacemod.solace.ai.ProviderSettings;
import com.solacemod.solace.ai.ReplyParser;
import com.solacemod.solace.ai.RequestBudget;
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
            if (path.contains("gemini-2.5-flash") || path.contains("gemini-flash-latest")) {
                respond(ex, 404, "{\"error\":{\"code\":404,\"message\":\"models/gemini-2.5-flash is not found\"}}");
                return;
            }
            JsonObject text = new JsonObject();
            text.addProperty("text", reply);
            respond(ex, 200, "{\"candidates\":[{\"content\":{\"parts\":[" + text + "]}}]}");
        });
        // A Groq-like server where the old default model was retired.
        server.createContext("/groq2/models", ex -> {
            respond(ex, 200, "{\"data\":[{\"id\":\"whisper-large-v3\"},{\"id\":\"meta-llama/llama-guard-4-12b\"},"
                    + "{\"id\":\"llama-3.1-8b-instant\"},{\"id\":\"openai/gpt-oss-120b\"},{\"id\":\"openai/gpt-oss-20b\"}]}");
        });
        server.createContext("/groq2/chat/completions", ex -> {
            String body = read(ex);
            lastBody.set(body);
            if (body.contains("llama-3.3-70b-versatile")) {
                respond(ex, 404, "{\"error\":{\"message\":\"The model `llama-3.3-70b-versatile` does not exist or you do not have access to it.\",\"code\":\"model_not_found\"}}");
                return;
            }
            JsonObject msg = new JsonObject();
            msg.addProperty("role", "assistant");
            msg.addProperty("content", reply);
            respond(ex, 200, "{\"choices\":[{\"message\":" + msg + "}]}");
        });
        // A Groq-like server where today's default (openai/gpt-oss-120b) has been retired.
        server.createContext("/groq3/models", ex -> respond(ex, 200, "{\"data\":[{\"id\":\"whisper-large-v3\"},{\"id\":\"llama-3.1-8b-instant\"},{\"id\":\"openai/gpt-oss-20b\"}]}"));
        server.createContext("/groq3/chat/completions", ex -> {
            String body = read(ex);
            lastBody.set(body);
            if (body.contains("gpt-oss-120b")) {
                respond(ex, 404, "{\"error\":{\"message\":\"The model `openai/gpt-oss-120b` has been decommissioned.\",\"code\":\"model_decommissioned\"}}");
                return;
            }
            JsonObject msg = new JsonObject();
            msg.addProperty("role", "assistant");
            msg.addProperty("content", reply);
            respond(ex, 200, "{\"choices\":[{\"message\":" + msg + "}]}");
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

        // OpenRouter gets speed hints
        ProviderSettings or = ProviderSettings.resolve("openrouter", "or-test", "some/model", base + "/openai", 10, "");
        openAiCalls = 0;
        LlmProvider.create(or).complete(req).get();
        JsonObject orBody = JsonParser.parseString(lastBody.get()).getAsJsonObject();
        check("openrouter: fastest-server routing", orBody.has("provider") && orBody.getAsJsonObject("provider").get("sort").getAsString().equals("latency"));
        check("openrouter: low reasoning effort", orBody.has("reasoning") && orBody.getAsJsonObject("reasoning").get("effort").getAsString().equals("low"));
        check("timeout: friendly hint", new LlmException("HttpTimeoutException: request timed out", null).hint().contains("took too long"));

        // A model the player typed is never swapped: they get an error listing the models they can use.
        ProviderSettings gq = ProviderSettings.resolve("groq", "gsk-test", "llama-3.3-70b-versatile", base + "/groq2", 10, "");
        LlmProvider gqp = LlmProvider.create(gq);
        try {
            gqp.complete(req).get();
            check("groq: chosen model that doesn't exist should fail", false);
        } catch (Exception e) {
            String hint = Http_unwrap(e) instanceof LlmException le ? le.hint() : "";
            check("model: a chosen model is never replaced", gqp.activeModel().equals("llama-3.3-70b-versatile"));
            check("model: error names the model and lists usable ones", hint.contains("'llama-3.3-70b-versatile' wasn't found") && hint.contains("openai/gpt-oss-120b"));
        }
        check("model: typed model is used as typed", ProviderSettings.resolve("groq", "k", " qwen/qwen3.6-27b ", "", 10, "").model().equals("qwen/qwen3.6-27b")
                && ProviderSettings.resolve("groq", "k", "qwen/qwen3.6-27b", "", 10, "").modelChosen());
        // Only the DEFAULT model (blank Model box) is replaced when the provider retires it.
        LlmProvider gq3 = LlmProvider.create(ProviderSettings.resolve("groq", "gsk-test", "", base + "/groq3", 10, ""));
        out = gq3.complete(req).get();
        check("groq: retired default is replaced automatically", out.equals(reply) && gq3.activeModel().equals("openai/gpt-oss-20b"));
        gqp = LlmProvider.create(ProviderSettings.resolve("groq", "gsk-test", "openai/gpt-oss-120b", base + "/groq2", 10, ""));
        gqp.complete(req).get();
        check("groq: thinking model asked to think briefly", JsonParser.parseString(lastBody.get()).getAsJsonObject().get("reasoning_effort").getAsString().equals("low"));
        check("groq: default is gpt-oss-120b", ProviderSettings.resolve("groq", "k", "", "", 10, "").model().equals("openai/gpt-oss-120b"));

        // Real error formats seen from providers
        check("errors: Gemini/xAI bad key (400) says key", new LlmException(400, "HTTP 400: {\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\"}}").hint().startsWith("Your API key was rejected"));
        check("errors: Mistral detail field is shown", new LlmException(401, "HTTP 401: {\"detail\":\"Invalid API Key\"}").hint().contains("Invalid API Key"));

        // Provider defaults match current documentation
        check("deepseek: v4 model, no /v1", ProviderSettings.resolve("deepseek", "k", "", "", 10, "").model().equals("deepseek-v4-flash")
                && ProviderSettings.resolve("deepseek", "k", "", "", 10, "").baseUrl().equals("https://api.deepseek.com"));
        check("xai: grok-4.3", ProviderSettings.resolve("xai", "k", "", "", 10, "").model().equals("grok-4.3"));
        check("openai: gpt-5.4-mini", ProviderSettings.resolve("openai", "k", "", "", 10, "").model().equals("gpt-5.4-mini"));
        check("scripted provider is gone", ProviderSettings.resolve("scripted", "", "", "", 10, "") == null);

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

        // Gemini: a chosen model that's gone gives a clear error; the default is replaced automatically
        com.solacemod.solace.ai.GeminiProvider chosen = (com.solacemod.solace.ai.GeminiProvider) LlmProvider.create(
                ProviderSettings.resolve("gemini", "g-test", "gemini-2.5-flash", base + "/gem2", 10, ""));
        try {
            chosen.complete(req).get();
            check("gemini: chosen missing model should fail", false);
        } catch (Exception e) {
            String hint = Http_unwrap(e) instanceof LlmException le ? le.hint() : "";
            check("gemini: chosen model kept, usable models listed", chosen.activeModel().equals("gemini-2.5-flash") && hint.contains("gemini-3.5-flash"));
        }
        ProviderSettings retired = ProviderSettings.resolve("gemini", "g-test", "", base + "/gem2", 10, "");
        com.solacemod.solace.ai.GeminiProvider gp = (com.solacemod.solace.ai.GeminiProvider) LlmProvider.create(retired);
        out = gp.complete(req).get();
        check("gemini: recovers from retired default", out.equals(reply) && lastPath.get().endsWith("gemini-3.5-flash:generateContent"));
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

        // Cut-off replies (length limit) are rescued instead of showing raw JSON
        SolaceReply cut = ReplyParser.parse("{\"say\":\"I don't have any netherite right now, but I can \\\"help\\\" you.\",\"emotion\":\"calm\",\"actions\":[{\"type\":\"follow\"}],\"remember\":");
        check("parser: rescues cut-off reply text", cut.say().equals("I don't have any netherite right now, but I can \"help\" you."));
        check("parser: rescues finished actions from cut-off reply", cut.actions().size() == 1 && cut.emotion().equals("calm"));
        SolaceReply cut2 = ReplyParser.parse("{\"say\":\"I was in the middle of saying");
        check("parser: rescues reply cut inside the text", cut2.say().equals("I was in the middle of saying"));
        check("parser: never shows raw JSON", !ReplyParser.parse("{\"emotion\":\"calm\",\"actio").say().contains("{"));

        budgetTests();
        playerAiTests();

        server.stop(0);
        try {
            poolTests(reply);
        } catch (Exception e) {
            check("brains: unexpected error " + Http_unwrap(e), false);
        }
        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ---------- Cost limits (whole server and each player) ----------

    private static void budgetTests() {
        RequestBudget b = new RequestBudget();
        String day = "2026-10-21";
        long t = 1_000_000;
        // Alone: the per-minute limit is the whole server's.
        int ok = 0;
        for (int i = 0; i < 12; i++) if (b.tryUse("alex", day, t + i, 12, 400, 0, false) == RequestBudget.Result.OK) ok++;
        check("budget: alone, a player can use the whole minute", ok == 12);
        check("budget: the minute is full", b.tryUse("alex", day, t + 20, 12, 400, 0, false) == RequestBudget.Result.RATE_LIMITED);
        check("budget: a minute later there's room again", b.tryUse("alex", day, t + 61_000, 12, 400, 0, false) == RequestBudget.Result.OK);

        // With others online, one player gets half of each minute.
        RequestBudget fair = new RequestBudget();
        int mine = 0;
        for (int i = 0; i < 12; i++) if (fair.tryUse("alex", day, t + i, 12, 400, 0, true) == RequestBudget.Result.OK) mine++;
        check("budget: with others online, one player gets half the minute", mine == 6);
        check("budget: the other player still gets in", fair.tryUse("sam", day, t + 20, 12, 400, 0, true) == RequestBudget.Result.OK);
        check("budget: a tiny per-minute limit still lets each player ask once",
                new RequestBudget().tryUse("alex", day, t, 1, 400, 0, true) == RequestBudget.Result.OK);

        // Each player's daily share.
        RequestBudget share = new RequestBudget();
        for (int i = 0; i < 3; i++) share.tryUse("alex", day, t + i * 61_000L, 12, 400, 3, false);
        check("budget: a player's daily share runs out", share.tryUse("alex", day, t + 400_000, 12, 400, 3, false) == RequestBudget.Result.PLAYER_DAILY_LIMIT);
        check("budget: another player still has theirs", share.tryUse("sam", day, t + 400_000, 12, 400, 3, false) == RequestBudget.Result.OK);
        check("budget: 0 means no per-player limit", share.tryUse("alex", day, t + 500_000, 12, 400, 0, false) == RequestBudget.Result.OK);
        check("budget: counts per player", share.usedToday("alex", day) == 4 && share.usedToday("sam", day) == 1 && share.totalToday(day) == 5);
        check("budget: busiest player listed first", share.playersToday(day).keySet().iterator().next().equals("alex"));

        // The whole server's daily limit.
        RequestBudget server = new RequestBudget();
        server.tryUse("alex", day, t, 12, 2, 0, false);
        server.tryUse("sam", day, t + 1, 12, 2, 0, false);
        check("budget: the server's daily limit stops everyone", server.tryUse("kim", day, t + 2, 12, 2, 0, false) == RequestBudget.Result.DAILY_LIMIT);

        // A new day starts fresh, and saved counts come back.
        check("budget: a new day starts fresh", share.tryUse("alex", "2026-10-22", t + 600_000, 12, 400, 3, false) == RequestBudget.Result.OK
                && share.usedToday("alex", "2026-10-22") == 1 && share.totalToday("2026-10-22") == 1);
        check("budget: yesterday's counts read as 0", share.usedToday("sam", "2026-10-22") == 0 && share.totalToday(day) == 0);
        RequestBudget loaded = new RequestBudget();
        loaded.restore(day, 5, java.util.Map.of("alex", 3));
        check("budget: saved counts come back", loaded.usedToday("alex", day) == 3 && loaded.totalToday(day) == 5
                && loaded.tryUse("alex", day, t, 12, 400, 3, false) == RequestBudget.Result.PLAYER_DAILY_LIMIT);
    }

    // ---------- Several brains working together ----------

    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicInteger> CALLS = new java.util.concurrent.ConcurrentHashMap<>();

    private static int calls(String name) {
        return CALLS.computeIfAbsent(name, k -> new java.util.concurrent.atomic.AtomicInteger()).get();
    }

    private static void poolTests(String reply) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "assistant");
        msg.addProperty("content", reply);
        String ok = "{\"choices\":[{\"message\":" + msg + "}]}";
        fake(server, "limited", ex -> respond(ex, 429, "{\"error\":{\"message\":\"Rate limit reached for model on requests per minute (RPM). Please try again in 30s.\"}}",
                java.util.Map.of("retry-after", "30")));
        fake(server, "daily", ex -> respond(ex, 429, "{\"error\":{\"message\":\"Rate limit reached for model on requests per day (RPD): Limit 1000, Used 1000. Please try again in 1m26.4s.\"}}",
                java.util.Map.of()));
        fake(server, "badkey", ex -> respond(ex, 401, "{\"error\":{\"message\":\"Invalid API Key\"}}", java.util.Map.of()));
        fake(server, "okA", ex -> respond(ex, 200, ok, java.util.Map.of("x-ratelimit-remaining-requests", "900", "x-ratelimit-limit-requests", "1000")));
        fake(server, "okB", ex -> respond(ex, 200, ok, java.util.Map.of()));
        fake(server, "lowtok", ex -> respond(ex, 200, ok, java.util.Map.of("x-ratelimit-remaining-tokens", "50", "x-ratelimit-limit-tokens", "6000",
                "x-ratelimit-reset-tokens", "50s")));
        server.createContext("/big/chat/completions", ex -> {
            String body = read(ex);
            CALLS.computeIfAbsent("big", k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
            if (body.length() > 1500) respond(ex, 413, "{\"error\":{\"message\":\"Request too large for model on tokens per minute (TPM): Limit 6000, Requested 9000\"}}", java.util.Map.of());
            else respond(ex, 200, ok, java.util.Map.of());
        });
        server.start();
        try {
            poolChecks("http://127.0.0.1:" + server.getAddress().getPort());
        } finally {
            server.stop(0);
        }
    }

    private static void poolChecks(String base) throws Exception {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(1_000_000_000L);
        LlmRequest req = new LlmRequest("You are Solace.", List.of(ChatMessage.user("hi")), 100, 0.9, true);
        BrainPool.Kind chat = BrainPool.Kind.CHAT;

        // A brain that says "too many requests" rests, and brain 2 answers instead.
        BrainPool pool = new BrainPool(clock::get);
        pool.update(List.of(fakeBrain(base, "limited"), fakeBrain(base, "okB")));
        BrainPool.Answer a = pool.complete(req, null, chat).get();
        check("brains: 429 moves on to brain 2", a.brain().slot() == 2 && calls("limited") == 1);
        check("brains: rests for exactly as long as the provider asked", pool.brains().get(0).describe(clock.get()).contains("resting 30s (too many requests)"));
        pool.complete(req, null, chat).get();
        check("brains: a resting brain isn't asked again", calls("limited") == 1 && calls("okB") == 2);
        clock.addAndGet(31_000);
        pool.complete(req, null, chat).get();
        check("brains: tried again once the rest is over", calls("limited") == 2);

        // A daily limit means a long rest, not 30 seconds.
        BrainPool daily = new BrainPool(clock::get);
        daily.update(List.of(fakeBrain(base, "daily"), fakeBrain(base, "okB")));
        check("brains: daily limit, brain 2 answers", daily.complete(req, null, chat).get().brain().slot() == 2);
        String d = daily.brains().get(0).describe(clock.get());
        check("brains: daily limit rests for a long time", d.contains("daily limit reached") && d.contains("10 min"));

        // A rejected key is reported, not retried forever.
        BrainPool keys = new BrainPool(clock::get);
        keys.update(List.of(fakeBrain(base, "badkey"), fakeBrain(base, "okB")));
        keys.complete(req, null, chat).get();
        keys.complete(req, null, chat).get();
        check("brains: bad key flagged and skipped", calls("badkey") == 1 && keys.brains().get(0).describe(clock.get()).contains("NOT WORKING"));

        // Smart routing: replies use brain 1, background work goes to the brain with more room, saving brain 1.
        BrainPool smart = new BrainPool(clock::get);
        smart.update(List.of(fakeBrain(base, "okA"), fakeBrain(base, "okB")));
        check("routing: chat uses brain 1", smart.complete(req, null, chat).get().brain().slot() == 1);
        check("routing: background saves brain 1", smart.complete(req, null, BrainPool.Kind.BACKGROUND).get().brain().slot() == 2);
        check("routing: story uses brain 1", smart.complete(req, null, BrainPool.Kind.STORY).get().brain().slot() == 1);
        check("brains: reads remaining requests", smart.brains().get(0).describe(clock.get()).contains("requests left"));

        // A brain that is almost out of tokens this minute is passed over before it fails.
        BrainPool tok = new BrainPool(clock::get);
        tok.update(List.of(fakeBrain(base, "lowtok"), fakeBrain(base, "okB")));
        tok.complete(req, null, chat).get();
        int before = calls("lowtok");
        check("routing: low on tokens, switches before an error", tok.complete(req, null, chat).get().brain().slot() == 2 && calls("lowtok") == before);

        // Everyone resting: background requests are skipped without any network call.
        BrainPool busy = new BrainPool(clock::get);
        busy.update(List.of(fakeBrain(base, "limited")));
        try {
            busy.complete(req, null, chat).get();
        } catch (Exception ignored) {
            // expected: 429
        }
        int lim = calls("limited");
        try {
            busy.complete(req, null, BrainPool.Kind.BACKGROUND).get();
            check("brains: should have been skipped", false);
        } catch (Exception e) {
            check("brains: all resting, background skipped quietly", Http_unwrap(e) instanceof BrainPool.BusyException && calls("limited") == lim);
        }
        check("brains: says when it'll be back", busy.readyInMs() > 25_000);

        // Too big for a free tier's token limit: the same brain is asked again with the short version, and remembers.
        StringBuilder longSystem = new StringBuilder();
        for (int i = 0; i < 80; i++) longSystem.append("Lots of detail about the world. ");
        LlmRequest full = new LlmRequest(longSystem.toString(), List.of(ChatMessage.user("hi")), 100, 0.9, true);
        BrainPool big = new BrainPool(clock::get);
        big.update(List.of(fakeBrain(base, "big")));
        big.complete(full, req, chat).get();
        check("compact: too big, retried with the short prompt", calls("big") == 2);
        big.complete(full, req, chat).get();
        check("compact: remembers to use the short prompt", calls("big") == 3);

        // Reading limits from headers
        check("rate: durations", RateInfo.duration("2m59.56s") == 179_560 && RateInfo.duration("120ms") == 120
                && RateInfo.duration("7.66s") == 7660 && RateInfo.duration("1h2m") == 3_720_000 && RateInfo.duration("30") == 30_000);
        long now = 1_700_000_000_000L;
        RateInfo an = RateInfo.parse(n -> switch (n) {
            case "anthropic-ratelimit-requests-remaining" -> "4";
            case "anthropic-ratelimit-requests-limit" -> "50";
            case "anthropic-ratelimit-requests-reset" -> java.time.Instant.ofEpochMilli(now + 20_000).toString();
            default -> null;
        }, null, now);
        check("rate: Anthropic headers", an.remainingRequests() == 4 && an.limitRequests() == 50 && an.requestsResetMs() == 20_000);
        RateInfo gem = RateInfo.parse(n -> null, "{\"error\":{\"message\":\"Quota exceeded for metric: generate_content_free_tier_requests\",\"details\":[{\"retryDelay\":\"33s\"}]}}", now);
        check("rate: Gemini retryDelay", gem.retryAfterMs() == 33_000 && !gem.daily());
        check("rate: Cerebras daily header", RateInfo.parse(n -> n.equals("x-ratelimit-remaining-requests-day") ? "12" : null, null, now).daily());
    }

    private static LlmProvider fakeBrain(String base, String name) {
        return LlmProvider.create(ProviderSettings.resolve("custom", "k-" + name, "m", base + "/" + name, 10, ""));
    }

    private interface Handler {
        void handle(com.sun.net.httpserver.HttpExchange ex) throws IOException;
    }

    private static void fake(HttpServer server, String name, Handler h) {
        server.createContext("/" + name + "/chat/completions", ex -> {
            read(ex);
            CALLS.computeIfAbsent(name, k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
            h.handle(ex);
        });
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int code, String body, java.util.Map<String, String> headers) throws IOException {
        headers.forEach((k, v) -> ex.getResponseHeaders().add(k, v));
        respond(ex, code, body);
    }

    private static Throwable Http_unwrap(Throwable e) {
        return com.solacemod.solace.ai.Http.unwrap(e);
    }

    /** A player's own brain runs on someone else's server, so it must never reach the owner's machine or secrets. */
    private static void playerAiTests() {
        ProviderSettings groq = ProviderSettings.forPlayer("groq", " gsk_abc 123 ", "", 10, "");
        check("player ai: big provider accepted, key cleaned", groq != null && groq.apiKey().equals("gsk_abc123"));
        check("player ai: provider's own address", groq != null && groq.baseUrl().equals("https://api.groq.com/openai/v1"));
        check("player ai: default model", groq != null && groq.model().equals("openai/gpt-oss-120b") && !groq.modelChosen());
        check("player ai: env: keys refused (they'd read the owner's secrets)", ProviderSettings.forPlayer("groq", "env:GROQ_API_KEY", "", 10, "") == null
                && ProviderSettings.forPlayer("openai", "ENV:x", "", 10, "") == null);
        check("player ai: ollama refused (it would reach the owner's machine)", ProviderSettings.forPlayer("ollama", "k", "", 10, "") == null);
        check("player ai: lm studio refused", ProviderSettings.forPlayer("lmstudio", "k", "", 10, "") == null);
        check("player ai: custom address refused", ProviderSettings.forPlayer("custom", "k", "", 10, "") == null);
        check("player ai: unknown provider refused", ProviderSettings.forPlayer("evil", "k", "", 10, "") == null);
        check("player ai: blank key refused", ProviderSettings.forPlayer("groq", "  ", "", 10, "") == null);
        check("player ai: very long key refused", ProviderSettings.forPlayer("groq", "k".repeat(301), "", 10, "") == null);
        check("player ai: odd model names refused", ProviderSettings.forPlayer("gemini", "k", "../../x", 10, "") == null
                && ProviderSettings.forPlayer("gemini", "k", "a?b", 10, "") == null && ProviderSettings.forPlayer("gemini", "k", "a b", 10, "") == null);
        ProviderSettings or = ProviderSettings.forPlayer("openrouter", "k", "meta-llama/llama-3.3-70b-instruct:free", 10, "");
        check("player ai: normal model names kept", or != null && or.model().equals("meta-llama/llama-3.3-70b-instruct:free") && or.modelChosen());
        check("player ai: key never in the name", groq != null && !groq.toString().contains("gsk_"));
        check("player ai: keys hidden in messages",
                KeyResolver.hideKeys("Incorrect API key provided: sk-proj-abcdefghijklmnopqrstuvwxyz123456.").equals("Incorrect API key provided: [hidden]."));
        check("player ai: normal messages untouched", KeyResolver.hideKeys("Your API key was rejected (401).").equals("Your API key was rejected (401)."));
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
