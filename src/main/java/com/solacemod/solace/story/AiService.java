package com.solacemod.solace.story;

import com.solacemod.solace.Solace;
import com.solacemod.solace.ai.Http;
import com.solacemod.solace.ai.LlmException;
import com.solacemod.solace.ai.LlmProvider;
import com.solacemod.solace.ai.LlmRequest;
import com.solacemod.solace.ai.ProviderSettings;
import com.solacemod.solace.config.SolaceConfig;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;

/** Connects the game to the configured AI provider(s), with cost limits and a backup provider. */
public final class AiService {
    public enum Gate { OK, NOT_CONFIGURED, RATE_LIMITED, DAILY_LIMIT }

    private static ProviderSettings primarySettings;
    private static LlmProvider primary;
    private static ProviderSettings fallbackSettings;
    private static LlmProvider fallback;
    private static final Deque<Long> RECENT = new ArrayDeque<>();

    private AiService() {
    }

    public static ProviderSettings primarySettings() {
        return ProviderSettings.resolve(SolaceConfig.PROVIDER.get(), SolaceConfig.API_KEY.get(), SolaceConfig.MODEL.get(),
                SolaceConfig.BASE_URL.get(), SolaceConfig.TIMEOUT_SECONDS.get(), SolaceConfig.ANTHROPIC_EFFORT.get());
    }

    public static ProviderSettings fallbackSettings() {
        return ProviderSettings.resolve(SolaceConfig.FALLBACK_PROVIDER.get(), SolaceConfig.FALLBACK_API_KEY.get(), SolaceConfig.FALLBACK_MODEL.get(),
                SolaceConfig.FALLBACK_BASE_URL.get(), SolaceConfig.TIMEOUT_SECONDS.get(), SolaceConfig.ANTHROPIC_EFFORT.get());
    }

    private static boolean usable(ProviderSettings s) {
        return s != null && s.problem() == null;
    }

    /** A human readable reason the AI isn't set up, or null if it is. */
    public static String setupProblem() {
        ProviderSettings s = primarySettings();
        if (s == null) return "No AI provider chosen. Pick one in Solace setup.";
        return s.problem();
    }

    public static synchronized boolean configured() {
        return usable(primarySettings()) || usable(fallbackSettings());
    }

    public static String describe() {
        ProviderSettings s = primarySettings();
        String problem = setupProblem();
        return problem == null ? s.toString() : "not set up (" + problem + ")";
    }

    /** Checks the cost limits. Counts the request if allowed. */
    public static synchronized Gate gate(MinecraftServer server) {
        if (!configured()) return Gate.NOT_CONFIGURED;
        long now = System.currentTimeMillis();
        while (!RECENT.isEmpty() && now - RECENT.peekFirst() > 60_000) RECENT.pollFirst();
        if (RECENT.size() >= SolaceConfig.MAX_REQUESTS_PER_MINUTE.get()) return Gate.RATE_LIMITED;
        if (!StoryData.get(server).tryUseRequest(SolaceConfig.DAILY_REQUEST_LIMIT.get())) return Gate.DAILY_LIMIT;
        RECENT.addLast(now);
        return Gate.OK;
    }

    private static synchronized LlmProvider primary() {
        ProviderSettings s = primarySettings();
        if (!usable(s)) return null;
        if (!s.equals(primarySettings)) {
            primarySettings = s;
            primary = LlmProvider.create(s);
        }
        return primary;
    }

    private static synchronized LlmProvider fallback() {
        ProviderSettings s = fallbackSettings();
        if (!usable(s)) return null;
        if (!s.equals(fallbackSettings)) {
            fallbackSettings = s;
            fallback = LlmProvider.create(s);
        }
        return fallback;
    }

    /** Sends the request to the main provider, then the backup if the main one fails. Runs in the background. */
    public static CompletableFuture<String> complete(LlmRequest request) {
        LlmProvider main = primary();
        LlmProvider backup = fallback();
        if (main == null && backup == null) return CompletableFuture.failedFuture(new LlmException(-1, "No AI provider configured"));
        if (main == null) return backup.complete(request);
        return main.complete(request).thenApply(text -> {
            rememberReplacement(main);
            return text;
        }).exceptionallyCompose(err -> {
            Throwable cause = Http.unwrap(err);
            Solace.LOGGER.warn("Solace AI provider {} failed: {}", main.settings(), cause.getMessage());
            if (backup != null) return backup.complete(request);
            return CompletableFuture.failedFuture(cause);
        });
    }

    /** If the configured model was retired and the provider switched to a working one, save that so it sticks. */
    private static void rememberReplacement(LlmProvider provider) {
        String active = provider.activeModel();
        if (active == null || active.equals(provider.settings().model())) return;
        Solace.LOGGER.info("Solace: model '{}' is no longer available, switched to '{}'", provider.settings().model(), active);
        try {
            SolaceConfig.MODEL.set(active);
            SolaceConfig.SPEC.save();
        } catch (RuntimeException e) {
            Solace.LOGGER.warn("Could not save the replacement model", e);
        }
    }

    public static String hintFor(Throwable err) {
        Throwable cause = Http.unwrap(err);
        if (cause instanceof LlmException e) return e.hint();
        return "Unexpected error: " + cause.getMessage();
    }
}
