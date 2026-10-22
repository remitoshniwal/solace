package com.solacemod.solace.story;

import com.solacemod.solace.ai.ProviderSettings;
import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A player's own AI on someone else's server (only when the owner turns on "playerAi").
 * Their game sends their brains here; they're kept in memory by AiService, never saved or logged,
 * and forgotten when the player leaves. Only the big online providers are accepted (see ProviderSettings.forPlayer).
 */
public final class PlayerAi {
    /** Players can't flood the server with new keys: one change every few seconds. */
    private static final long MIN_MS_BETWEEN = 3000;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private PlayerAi() {
    }

    public static void onOwnBrains(ServerPlayer player, List<ModNetwork.OwnBrain> brains) {
        long now = System.currentTimeMillis();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < MIN_MS_BETWEEN) return;
        LAST.put(player.getUUID(), now);

        boolean had = AiService.hasOwn(player.getUUID());
        if (!SolaceConfig.PLAYER_AI.get()) {
            // Their game shouldn't send one here; if it does, it's dropped at once.
            AiService.forgetOwn(player.getUUID());
            say(player, "[Solace] This server doesn't take players' own AI, so your key was ignored and not kept.");
            return;
        }
        List<ProviderSettings> accepted = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int skipped = 0;
        for (ModNetwork.OwnBrain b : brains) {
            ProviderSettings s = ProviderSettings.forPlayer(b.provider(), b.key(), b.model(),
                    SolaceConfig.TIMEOUT_SECONDS.get(), SolaceConfig.ANTHROPIC_EFFORT.get());
            if (s == null) {
                skipped++;
                continue;
            }
            // Testing only: the server owner (never a player) can send players' brains to a pretend AI on this computer.
            String testUrl = System.getProperty("solace.testPlayerAiUrl");
            if (testUrl != null && !testUrl.isBlank()) {
                s = new ProviderSettings(s.name(), s.protocol(), testUrl.trim(), s.apiKey(), s.model(), s.timeoutSeconds(), s.anthropicEffort(), s.modelChosen());
            }
            accepted.add(s);
            names.add(s.toString()); // "groq (model)": never the key
        }
        AiService.setOwn(player.getUUID(), accepted);
        PlayerStory story = StoryData.get(player.getServer()).get(player.getUUID());
        if (accepted.isEmpty()) {
            if (had) {
                PlaytestLog.log(story, player, "OWN AI", "stopped using their own AI");
                say(player, "[Solace] Solace stopped using your own AI here." + fallbackNote());
            } else if (skipped > 0) {
                say(player, "[Solace] Your AI couldn't be used here: only the big online providers work on a server (not Ollama, LM Studio or a custom address).");
            }
            return;
        }
        PlaytestLog.log(story, player, "OWN AI", "using their own AI: " + String.join(", ", names));
        say(player, "[Solace] Solace uses your own AI here now (" + String.join(", ", names) + "). The server keeps it only while you're online and never saves it."
                + (skipped > 0 ? " " + skipped + " brain" + (skipped == 1 ? " was" : "s were") + " skipped: only the big online providers work on a server." : ""));
    }

    /** What happens without their own AI, in a few words. */
    private static String fallbackNote() {
        return switch (SolaceConfig.LEND_AI.get()) {
            case "never" -> " The owner doesn't share theirs, so Solace only follows simple orders.";
            default -> AiService.configured() ? " It uses the server owner's AI again." : "";
        };
    }

    private static void say(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    /** Forgets the flood guard (the server is stopping). */
    public static void reset() {
        LAST.clear();
    }
}
