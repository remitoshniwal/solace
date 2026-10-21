package com.solacemod.solace.story;

import com.solacemod.solace.Solace;
import com.solacemod.solace.ai.BrainPool;
import com.solacemod.solace.ai.Http;
import com.solacemod.solace.ai.LlmException;
import com.solacemod.solace.ai.LlmProvider;
import com.solacemod.solace.ai.LlmRequest;
import com.solacemod.solace.ai.ProviderSettings;
import com.solacemod.solace.ai.RequestBudget;
import com.solacemod.solace.config.SolaceConfig;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Connects the game to Solace's AI brains (up to 5 providers, see {@link BrainPool}), with cost limits on top.
 */
public final class AiService {
    public enum Gate { OK, NOT_CONFIGURED, RATE_LIMITED, DAILY_LIMIT, PLAYER_LIMIT }

    private static final BrainPool POOL = new BrainPool();
    private static List<ProviderSettings> poolSettings = List.of();
    private static boolean migrated;

    private AiService() {
    }

    /** Settings for brain 1 (the main provider). */
    public static ProviderSettings primarySettings() {
        return settingsFor(1);
    }

    /** Settings for brain 1 to 5, or null if that slot is empty or unknown. */
    public static ProviderSettings settingsFor(int slot) {
        if (slot == 1) {
            return ProviderSettings.resolve(SolaceConfig.PROVIDER.get(), SolaceConfig.API_KEY.get(), SolaceConfig.MODEL.get(),
                    SolaceConfig.BASE_URL.get(), SolaceConfig.TIMEOUT_SECONDS.get(), SolaceConfig.ANTHROPIC_EFFORT.get());
        }
        int i = slot - 2;
        if (i < 0 || i >= SolaceConfig.EXTRA_BRAINS) return null;
        return ProviderSettings.resolve(SolaceConfig.BRAIN_PROVIDER.get(i).get(), SolaceConfig.BRAIN_API_KEY.get(i).get(),
                SolaceConfig.BRAIN_MODEL.get(i).get(), SolaceConfig.BRAIN_BASE_URL.get(i).get(), SolaceConfig.TIMEOUT_SECONDS.get(),
                SolaceConfig.ANTHROPIC_EFFORT.get());
    }

    private static boolean usable(ProviderSettings s) {
        return s != null && s.problem() == null;
    }

    /** The old single "backup provider" setting becomes brain 2 (once). */
    private static void migrateFallback() {
        if (migrated) return;
        migrated = true;
        try {
            String old = SolaceConfig.FALLBACK_PROVIDER.get();
            if (old == null || old.isBlank() || old.equals("none")) return;
            int free = -1;
            for (int i = 0; i < SolaceConfig.EXTRA_BRAINS && free < 0; i++) {
                if ("none".equals(SolaceConfig.BRAIN_PROVIDER.get(i).get())) free = i;
            }
            if (free < 0) return;
            SolaceConfig.BRAIN_PROVIDER.get(free).set(old);
            SolaceConfig.BRAIN_API_KEY.get(free).set(SolaceConfig.FALLBACK_API_KEY.get());
            SolaceConfig.BRAIN_MODEL.get(free).set(SolaceConfig.FALLBACK_MODEL.get());
            SolaceConfig.BRAIN_BASE_URL.get(free).set(SolaceConfig.FALLBACK_BASE_URL.get());
            SolaceConfig.FALLBACK_PROVIDER.set("none");
            SolaceConfig.SPEC.save();
            Solace.LOGGER.info("Solace: moved the backup provider '{}' into brain {}", old, free + 2);
        } catch (RuntimeException e) {
            Solace.LOGGER.warn("Solace: could not move the old backup provider setting", e);
        }
    }

    /** Keeps the pool in sync with the config (the player can change settings at any time). */
    private static synchronized BrainPool pool() {
        migrateFallback();
        List<ProviderSettings> now = new ArrayList<>();
        for (int slot = 1; slot <= SolaceConfig.EXTRA_BRAINS + 1; slot++) {
            ProviderSettings s = settingsFor(slot);
            if (usable(s)) now.add(s);
        }
        if (!now.equals(poolSettings)) {
            List<LlmProvider> existing = new ArrayList<>();
            for (BrainPool.Brain b : POOL.brains()) existing.add(b.provider());
            List<LlmProvider> providers = new ArrayList<>();
            for (ProviderSettings s : now) {
                LlmProvider keep = null;
                for (LlmProvider p : existing) if (p.settings().equals(s)) keep = p;
                providers.add(keep != null ? keep : LlmProvider.create(s));
            }
            POOL.update(providers);
            poolSettings = now;
        }
        return POOL;
    }

    /** A human readable reason the AI isn't set up, or null if it is. */
    public static String setupProblem() {
        if (configured()) return null;
        ProviderSettings s = primarySettings();
        if (s == null) return "No AI provider chosen. Pick one in Solace setup.";
        return s.problem();
    }

    public static boolean configured() {
        return !pool().isEmpty();
    }

    public static String describe() {
        if (!configured()) return "not set up (" + setupProblem() + ")";
        List<BrainPool.Brain> brains = pool().brains();
        return brains.size() == 1 ? brains.get(0).provider().settings().toString() : brains.size() + " brains";
    }

    /** One line per brain, for /solace status. */
    public static List<String> brainStatus() {
        List<String> lines = new ArrayList<>();
        for (BrainPool.Brain b : pool().brains()) lines.add("Brain " + b.slot() + ": " + b.describe());
        return lines;
    }

    /** What the pool currently knows about one configured slot (for the setup screen), or null. */
    public static String brainStatus(int slot) {
        ProviderSettings s = settingsFor(slot);
        if (!usable(s)) return null;
        for (BrainPool.Brain b : pool().brains()) if (b.provider().settings().equals(s)) return b.describe();
        return null;
    }

    /** False if a request of this kind would be skipped right now (all brains resting or nearly out). */
    public static boolean hasRoomFor(BrainPool.Kind kind) {
        return pool().hasRoomFor(kind);
    }

    /** Checks the cost limits for a request made for this player. Counts the request if allowed. */
    public static synchronized Gate gate(MinecraftServer server, UUID player) {
        if (!configured()) return Gate.NOT_CONFIGURED;
        StoryData data = StoryData.get(server);
        RequestBudget.Result result = data.budget.tryUse(player.toString(), StoryData.today(), System.currentTimeMillis(),
                SolaceConfig.MAX_REQUESTS_PER_MINUTE.get(), SolaceConfig.DAILY_REQUEST_LIMIT.get(), perPlayerLimit(server),
                server.getPlayerCount() > 1);
        return switch (result) {
            case OK -> {
                data.setDirty();
                yield Gate.OK;
            }
            case RATE_LIMITED -> Gate.RATE_LIMITED;
            case DAILY_LIMIT -> Gate.DAILY_LIMIT;
            case PLAYER_DAILY_LIMIT -> Gate.PLAYER_LIMIT;
        };
    }

    /** Each player's daily share, or 0 (no limit) in a single-player world that isn't open to LAN. */
    public static int perPlayerLimit(MinecraftServer server) {
        boolean shared = server.isDedicatedServer() || server.isPublished();
        return shared ? SolaceConfig.DAILY_REQUESTS_PER_PLAYER.get() : 0;
    }

    /** Sends a request, letting the brains decide who answers. Runs in the background. */
    public static CompletableFuture<String> complete(LlmRequest request, LlmRequest compact, BrainPool.Kind kind) {
        BrainPool pool = pool();
        if (pool.isEmpty()) return CompletableFuture.failedFuture(new LlmException(-1, "No AI provider configured"));
        return pool.complete(request, compact, kind).thenApply(answer -> {
            logReplacement(answer.brain());
            return answer.text();
        }).whenComplete((text, err) -> {
            if (err != null && !(Http.unwrap(err) instanceof BrainPool.BusyException)) {
                Solace.LOGGER.warn("Solace AI request failed on every brain: {}", Http.unwrap(err).getMessage());
            }
        });
    }

    public static CompletableFuture<String> complete(LlmRequest request) {
        return complete(request, null, BrainPool.Kind.CHAT);
    }

    /**
     * If a brain's DEFAULT model was retired and the provider switched to a working one, note it in the log.
     * Nothing is written to the config: the player's settings are never changed behind their back.
     */
    private static void logReplacement(BrainPool.Brain brain) {
        LlmProvider provider = brain.provider();
        String active = provider.activeModel();
        if (active == null || active.equals(provider.settings().model()) || !REPLACEMENTS_LOGGED.add(provider.settings().name() + active)) return;
        Solace.LOGGER.info("Solace: brain {}'s default model '{}' is no longer available, using '{}' instead", brain.slot(), provider.settings().model(), active);
    }

    private static final java.util.Set<String> REPLACEMENTS_LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static String hintFor(Throwable err) {
        Throwable cause = Http.unwrap(err);
        if (cause instanceof LlmException e) return e.hint();
        return "Unexpected error: " + cause.getMessage();
    }
}
