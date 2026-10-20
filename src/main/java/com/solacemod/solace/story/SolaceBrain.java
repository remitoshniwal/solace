package com.solacemod.solace.story;

import com.google.gson.JsonObject;
import com.solacemod.solace.Solace;
import com.solacemod.solace.ai.ChatMessage;
import com.solacemod.solace.ai.LlmRequest;
import com.solacemod.solace.ai.ReplyParser;
import com.solacemod.solace.ai.SolaceReply;
import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.entity.SolaceEntity;
import com.solacemod.solace.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Solace's "mind": decides when it's being talked to, asks the AI, and turns the answer
 * into chat, actions, memories and trust. Everything here runs on the server thread; the AI call runs in the background.
 */
public final class SolaceBrain {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Set<UUID> BUSY = new HashSet<>();
    private static final Map<UUID, List<String>> PENDING = new HashMap<>();
    private static final Set<UUID> HINTED = new HashSet<>();
    private static final Map<UUID, Long> LAST_ERROR_NOTICE = new HashMap<>();
    private static final Set<UUID> SUMMARIZING = new HashSet<>();

    private SolaceBrain() {
    }

    /** Clears in-memory state when a world closes, so nothing leaks into the next world. */
    public static void reset() {
        BUSY.clear();
        PENDING.clear();
        HINTED.clear();
        LAST_ERROR_NOTICE.clear();
        SUMMARIZING.clear();
    }

    /** Shows "Solace is thinking..." above the hotbar while a reply is on its way (called once per second). */
    public static void tickThinking(MinecraftServer server) {
        for (UUID id : BUSY) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.displayClientMessage(Component.literal("Solace is thinking...").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), true);
        }
    }

    // ---------- Input ----------

    /** Called for every chat message. Decides whether it was meant for Solace. */
    public static void onPlayerChat(ServerPlayer player, String text) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        // Hop onto the server thread, then wait one tick so the player's own message shows up first.
        server.execute(() -> Scheduler.later(1, () -> {
            if (player.isRemoved()) return;
            PlayerStory story = StoryData.get(server).get(player.getUUID());
            if (!story.met || !story.solaceShouldExist()) return;
            SolaceEntity solace = SolaceManager.find(server, story);
            if (!isAddressed(player, solace, text)) return;
            talk(player, stripPrefix(text));
        }));
    }

    /** Called when the player spoke out loud (already transcribed on their computer). */
    public static void onPlayerVoice(ServerPlayer player, String text) {
        if (text.isBlank()) return;
        player.sendSystemMessage(Component.literal("[voice] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(player.getGameProfile().getName() + ": " + text).withStyle(ChatFormatting.GRAY)));
        talk(player, text);
    }

    private static boolean isAddressed(ServerPlayer player, @Nullable SolaceEntity solace, String text) {
        String lower = text.toLowerCase(Locale.ROOT).trim();
        boolean named = lower.startsWith("@solace") || lower.startsWith("solace") || lower.contains(" solace");
        return switch (SolaceConfig.LISTEN_MODE.get()) {
            case "always" -> true;
            case "prefix" -> lower.startsWith("@solace") || lower.startsWith("solace");
            default -> named || (solace != null && solace.level() == player.level()
                    && solace.distanceTo(player) <= SolaceConfig.HEARING_RANGE.get());
        };
    }

    private static String stripPrefix(String text) {
        return text.replaceFirst("(?i)^@?solace[,:!]?\\s*", "").trim();
    }

    /** The player said something to Solace. */
    public static void talk(ServerPlayer player, String text) {
        MinecraftServer server = player.getServer();
        if (server == null || text.isBlank()) return;
        PlayerStory story = StoryData.get(server).get(player.getUUID());
        if (!story.met || !story.solaceShouldExist()) return;
        story.lastInteractionTick = server.getTickCount();
        Bond.onTalk(story, server.getTickCount());
        PlaytestLog.log(story, player, "YOU", "\"" + text + "\"");
        String line = player.getGameProfile().getName() + ": " + text;
        if (BUSY.contains(player.getUUID())) {
            PENDING.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(line);
            return;
        }
        request(player, story, line, CommandParser.parseCommands(text), true, false);
    }

    /**
     * Something happened that Solace should react to. The AI decides what (if anything) Solace says.
     *
     * @param situation what to tell the AI, e.g. "The player just died to a creeper."
     * @param important if false, the event is skipped when the AI is busy or rate limited
     */
    public static void event(ServerPlayer player, String situation, boolean important) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        PlayerStory story = StoryData.get(server).get(player.getUUID());
        if (!story.met || !story.solaceShouldExist()) return;
        if (BUSY.contains(player.getUUID())) {
            if (important) PENDING.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add("[EVENT] " + situation);
            return;
        }
        request(player, story, "[EVENT] " + situation, List.of(), important, false);
    }

    /** Solace's final words, spoken even though Solace is leaving the world (used by endings). */
    public static void lastWords(ServerPlayer player, String situation) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        PlayerStory story = StoryData.get(server).get(player.getUUID());
        request(player, story, "[EVENT] " + situation, List.of(), true, true);
    }

    /** A job (mining, building...) finished or failed. Solace tells the player in its own words. */
    public static void reportJob(ServerPlayer owner, String result) {
        MinecraftServer server = owner.getServer();
        if (server != null) StoryData.get(server).get(owner.getUUID()).addEvent("your job: " + result);
        event(owner, "Your job just ended. What happened: \"" + result + "\" Tell the player in your own words, briefly.", true);
    }

    // ---------- The request ----------

    /**
     * @param fallbackActions actions to still carry out (silently) if the AI can't answer, so basic commands keep working
     * @param ignoreExistence speak even if Solace is gone (last words of an ending)
     */
    private static void request(ServerPlayer player, PlayerStory story, String userContent, List<JsonObject> fallbackActions,
                                boolean important, boolean ignoreExistence) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        com.solacemod.solace.ai.BrainPool.Kind kind = classify(userContent, important);
        // Background chatter isn't worth a request when every brain is nearly out: save them for when the player talks.
        if (kind == com.solacemod.solace.ai.BrainPool.Kind.BACKGROUND && AiService.configured()
                && !AiService.hasRoomFor(com.solacemod.solace.ai.BrainPool.Kind.BACKGROUND)) {
            return;
        }
        AiService.Gate gate = AiService.gate(server);
        if (gate != AiService.Gate.OK) {
            if (gate == AiService.Gate.NOT_CONFIGURED && isAdmin(player) && HINTED.add(player.getUUID())) {
                player.sendSystemMessage(setupMessage(StoryText.SETUP_HINT));
            }
            if (gate == AiService.Gate.DAILY_LIMIT && HINTED.add(player.getUUID())) {
                player.sendSystemMessage(Component.literal("[Solace] Daily AI request limit reached (change it in Solace setup). Solace is quiet until tomorrow.")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (gate == AiService.Gate.RATE_LIMITED && important) {
                player.displayClientMessage(Component.literal("Solace is overwhelmed. Try again in a moment.").withStyle(ChatFormatting.DARK_GRAY), true);
            }
            runSilently(player, story, fallbackActions);
            return;
        }

        SolaceEntity solace = SolaceManager.find(server, story);
        LlmRequest req = PromptBuilder.build(player, story, solace, userContent, false);
        LlmRequest compact = PromptBuilder.build(player, story, solace, userContent, true);
        UUID id = player.getUUID();
        BUSY.add(id);
        player.displayClientMessage(Component.literal("Solace is thinking...").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), true);
        AiService.complete(req, compact, kind).whenComplete((text, err) -> server.execute(() -> {
            BUSY.remove(id);
            ServerPlayer live = server.getPlayerList().getPlayer(id);
            if (live == null) return;
            live.displayClientMessage(Component.empty(), true); // clear "thinking..."
            PlayerStory liveStory = StoryData.get(server).get(id);
            if (!ignoreExistence && !liveStory.solaceShouldExist()) return;
            if (err != null) {
                boolean busy = com.solacemod.solace.ai.Http.unwrap(err) instanceof com.solacemod.solace.ai.BrainPool.BusyException;
                if (busy) PlaytestLog.log(liveStory, live, "AI BUSY", com.solacemod.solace.ai.Http.unwrap(err).getMessage());
                if (busy && important) {
                    live.displayClientMessage(Component.literal(com.solacemod.solace.ai.Http.unwrap(err).getMessage()).withStyle(ChatFormatting.DARK_GRAY), true);
                } else if (!busy) {
                    notifyError(live, err);
                }
                runSilently(live, liveStory, fallbackActions);
            } else {
                apply(live, liveStory, userContent, ReplyParser.parse(text));
            }
            List<String> queued = PENDING.remove(id);
            if (queued != null && !queued.isEmpty()) {
                String combined = String.join("\n", queued.subList(Math.max(0, queued.size() - 4), queued.size()));
                request(live, liveStory, combined, CommandParser.parseCommands(combined), true, false);
            }
        }));
    }

    /**
     * Sorts a request so the brains can route it: what the player said and their jobs are CHAT,
     * important story moments are STORY, and things Solace does on its own are BACKGROUND.
     */
    static com.solacemod.solace.ai.BrainPool.Kind classify(String userContent, boolean important) {
        if (!userContent.startsWith("[EVENT]")) return com.solacemod.solace.ai.BrainPool.Kind.CHAT;
        if (!important) return com.solacemod.solace.ai.BrainPool.Kind.BACKGROUND;
        if (userContent.startsWith("[EVENT] Results of") || userContent.startsWith("[EVENT] Your job")) return com.solacemod.solace.ai.BrainPool.Kind.CHAT;
        return com.solacemod.solace.ai.BrainPool.Kind.STORY;
    }

    /** When the AI can't answer, still do what the player clearly asked ("follow me", "mine iron"), without words. */
    private static void runSilently(ServerPlayer player, PlayerStory story, List<JsonObject> actions) {
        if (actions.isEmpty() || player.getServer() == null) return;
        SolaceEntity solace = SolaceManager.find(player.getServer(), story);
        if (solace == null) solace = SolaceManager.summon(player, story);
        if (solace == null || solace.isHollow()) return;
        for (String result : ActionExecutor.execute(player, solace, story, actions)) story.addEvent("tried to help: " + result);
    }

    private static void notifyError(ServerPlayer player, Throwable err) {
        Solace.LOGGER.warn("Solace AI request failed: {}", AiService.hintFor(err));
        if (player.getServer() != null) PlaytestLog.log(StoryData.get(player.getServer()).get(player.getUUID()), player, "AI ERROR", AiService.hintFor(err));
        long now = System.currentTimeMillis();
        Long last = LAST_ERROR_NOTICE.get(player.getUUID());
        if (last != null && now - last < 5 * 60_000) return;
        LAST_ERROR_NOTICE.put(player.getUUID(), now);
        if (isAdmin(player)) {
            player.sendSystemMessage(setupMessage("AI problem: " + AiService.hintFor(err) + " (Solace stays quiet until this works; simple commands still work)"));
        }
    }

    /** "[Solace] ... [Open setup]" with a clickable button that opens the setup screen. */
    public static Component setupMessage(String text) {
        return Component.literal("[Solace] " + text + " ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("[Open setup]").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/solace setup"))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Opens Solace's settings. (Also: Esc > Mods > Solace > Config)")))));
    }

    /** Server operators, or the player in singleplayer, get setup/error hints. */
    public static boolean isAdmin(ServerPlayer player) {
        return player.hasPermissions(2) || (player.getServer() != null && player.getServer().isSingleplayer());
    }

    /** Applies a reply: speak, act, remember, adjust trust, and save it to the conversation history. */
    private static void apply(ServerPlayer player, PlayerStory story, String userContent, SolaceReply reply) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        SolaceEntity solace = SolaceManager.find(server, story);

        List<JsonObject> actions = reply.actions();
        String say = reply.say();
        // While Solace is upset (a story moment), it refuses to act. The AI is told about this in the game state.
        if (story.defiantUntilTick > server.getTickCount()) actions = List.of();

        if (solace == null && !actions.isEmpty() && story.respawnAtTick < 0) {
            // Solace is far away in an unloaded area (e.g. told to stay). Being asked to act calls it back.
            solace = SolaceManager.summon(player, story);
        }
        // Act first, speak second: Solace only says what it's doing once it knows whether it worked.
        List<String> results = new ArrayList<>();
        if (solace != null && !actions.isEmpty() && !solace.isHollow()) {
            results.addAll(ActionExecutor.execute(player, solace, story, actions));
        }
        boolean followUpAllowed = !userContent.startsWith("[EVENT] Results");
        List<String> problems = results.stream().filter(ActionExecutor::isProblem).toList();
        // Something it was about to promise didn't happen ("here's some coal", but it has none and can't make it).
        // Don't show that line at all: ask for one honest reply instead, so the player never sees two messages that disagree.
        boolean retract = !problems.isEmpty() && followUpAllowed && !say.isEmpty();
        if (!say.isEmpty() && !retract) display(player, solace, story, say, reply.emotion());
        for (String fact : reply.remember()) {
            story.addFact(fact);
            GlobalMemory.addFact(player.getUUID(), fact);
        }
        if (SolaceConfig.STORY_ENABLED.get()) story.changeTrust(reply.trust());

        // History: what was said, and what Solace answered (as JSON, so the AI keeps using the format).
        story.history.add(new PlayerStory.Turn("user", userContent));
        JsonObject saved = new JsonObject();
        saved.addProperty("say", retract ? "" : say);
        if (!actions.isEmpty()) saved.add("actions", ReplyParser.toJsonArray(actions));
        story.history.add(new PlayerStory.Turn("assistant", saved.toString()));
        trimHistory(player, story);
        StoryData.get(server).setDirty();

        // Some actions have results Solace should mention ("I don't have any diamonds", "found a village at...").
        // Tell the AI so it can say it naturally. Results of results don't chain, so this can't loop.
        if (!results.isEmpty() && followUpAllowed) {
            String joined = String.join(" | ", results);
            results.forEach(r -> story.addEvent("action result: " + r));
            if (retract) {
                event(player, "Results of what you just tried to do: " + joined + ". You haven't said anything to the player yet. "
                        + "Reply ONCE, honestly and in character: don't claim it worked. Say what you couldn't do and why, "
                        + "and offer something you CAN do instead (mine it, find it, or make it with your power once you're strong enough).", true);
            } else {
                event(player, "Results of what you just tried to do: " + joined + ". Tell the player briefly, in your own words.", true);
            }
        }
    }

    /** Keeps history short. Old turns get summarized into long-term memory (if enabled). */
    private static void trimHistory(ServerPlayer player, PlayerStory story) {
        int max = SolaceConfig.HISTORY_MESSAGES.get();
        if (story.history.size() <= max + 8) return;
        List<PlayerStory.Turn> old = new ArrayList<>(story.history.subList(0, 8));
        story.history.subList(0, 8).clear();
        if (!SolaceConfig.SUMMARIZE_MEMORY.get() || !AiService.configured() || SUMMARIZING.contains(player.getUUID())) return;
        MinecraftServer server = player.getServer();
        if (server == null || !AiService.hasRoomFor(com.solacemod.solace.ai.BrainPool.Kind.BACKGROUND)) return;
        if (AiService.gate(server) != AiService.Gate.OK) return;

        StringBuilder transcript = new StringBuilder();
        for (PlayerStory.Turn t : old) transcript.append(t.role().equals("user") ? "" : "Solace: ").append(t.text()).append('\n');
        String system = "You maintain the long-term memory of a character named Solace. Merge the old summary and the new conversation into ONE short paragraph "
                + "(max 80 words) written from Solace's point of view, keeping only what matters: names, feelings, promises, important events. Plain text only.";
        String user = "Old summary: " + (story.summary.isEmpty() ? "(none)" : story.summary) + "\n\nNew conversation:\n" + transcript;
        UUID id = player.getUUID();
        SUMMARIZING.add(id);
        AiService.complete(new LlmRequest(system, List.of(ChatMessage.user(user)), 300, 0.3, false), null, com.solacemod.solace.ai.BrainPool.Kind.BACKGROUND)
                .whenComplete((text, err) -> server.execute(() -> {
                    SUMMARIZING.remove(id);
                    if (err != null || text == null) return;
                    PlayerStory s = StoryData.get(server).get(id);
                    String clean = ReplyParser.cleanSay(text);
                    s.summary = clean.length() > 700 ? clean.substring(0, 700) : clean;
                    StoryData.get(server).setDirty();
                }));
    }

    // ---------- Output ----------

    /** Shows Solace's line in chat and sends it to the player's game for text-to-speech. */
    public static void display(ServerPlayer owner, @Nullable SolaceEntity solace, PlayerStory story, String text, String emotion) {
        Component message = format(story, solace, text, emotion);
        PlaytestLog.log(story, owner, "SOLACE", "\"" + text + "\"" + (emotion == null || emotion.isBlank() ? "" : " (" + emotion + ")"));
        if (solace != null && emotion != null && java.util.Set.of("angry", "sad", "glitch", "afraid")
                .contains(emotion.toLowerCase(java.util.Locale.ROOT))) {
            solace.agitate(20 * 8);
            // From chapter 4, real anger shows: he flares into his negative.
            if (solace.getCorruption() >= 4 && ("angry".equalsIgnoreCase(emotion) || "glitch".equalsIgnoreCase(emotion))) solace.goNegative(60);
        }
        if (SolaceConfig.REPLIES_VISIBLE_TO_ALL.get() && owner.getServer() != null) {
            owner.getServer().getPlayerList().broadcastSystemMessage(message, false);
        } else {
            owner.sendSystemMessage(message);
        }
        PacketDistributor.sendToPlayer(owner, new ModNetwork.SpeechPayload(solace == null ? -1 : solace.getId(), text, emotion));
    }

    private static Component format(PlayerStory story, @Nullable SolaceEntity solace, String text, String emotion) {
        boolean story_ = SolaceConfig.STORY_ENABLED.get();
        ChatFormatting color;
        if (solace != null && solace.isHollow()) color = ChatFormatting.DARK_RED;
        else if (story.ending == Ending.FOREVER || story.ending == Ending.COLLECTED) color = ChatFormatting.GRAY;
        else if (!story_ || story.ending == Ending.TOGETHER || story.part() <= 2) color = ChatFormatting.AQUA;
        else if (story.part() == 3) color = ChatFormatting.DARK_AQUA;
        else color = ChatFormatting.RED;

        MutableComponent name = Component.empty();
        if (story_ && story.part() >= 3 && story.ending == Ending.NONE && RANDOM.nextFloat() < 0.2F) {
            name.append(Component.literal("Sol").withStyle(color))
                    .append(Component.literal("a").withStyle(Style.EMPTY.withColor(color).withObfuscated(true)))
                    .append(Component.literal("ce").withStyle(color));
        } else {
            name.append(Component.literal(solace != null && solace.isHollow() ? "The Hollow" : "Solace").withStyle(color));
        }
        MutableComponent line = Component.literal("<").append(name).append("> ");

        boolean glitch = "glitch".equals(emotion) || (story_ && story.part() >= 3 && story.ending == Ending.NONE && RANDOM.nextFloat() < 0.12F);
        String[] words = text.split(" ");
        int glitchWords = glitch ? 1 + RANDOM.nextInt(Math.min(3, Math.max(1, words.length / 4 + 1))) : 0;
        Set<Integer> obf = new HashSet<>();
        while (obf.size() < glitchWords && obf.size() < words.length) obf.add(RANDOM.nextInt(words.length));
        for (int i = 0; i < words.length; i++) {
            Style style = obf.contains(i) ? Style.EMPTY.withObfuscated(true).withColor(ChatFormatting.DARK_GRAY) : Style.EMPTY.withColor(ChatFormatting.WHITE);
            line.append(Component.literal(words[i] + (i < words.length - 1 ? " " : "")).withStyle(style));
        }
        return line;
    }

    // ---------- Reactions to game events ----------

    public static void onGift(ServerPlayer player, SolaceEntity solace, net.minecraft.world.item.ItemStack gift) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        PlayerStory story = StoryData.get(server).get(player.getUUID());
        story.giftsGiven++;
        if (story.giftsGiven % 3 == 1) story.changeTrust(2);
        Bond.onGift(player, story, gift);
        event(player, "The player gave you " + gift.getCount() + " " + gift.getHoverName().getString() + ". It's now in your pockets.", false);
    }

    public static void onOwnerHurtSolace(ServerPlayer player, SolaceEntity solace) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        PlayerStory story = StoryData.get(server).get(player.getUUID());
        story.timesHurtSolace++;
        Bond.onHit(story);
        long now = server.getTickCount();
        if (now - story.lastHurtCommentTick < 20 * 20) return;
        story.lastHurtCommentTick = now;
        story.changeTrust(-4);
        story.addEvent("the player hit you");
        event(player, "The player just tried to hit you (" + story.timesHurtSolace + " times total). It can't hurt you; nothing can. React.", true);
    }
}
