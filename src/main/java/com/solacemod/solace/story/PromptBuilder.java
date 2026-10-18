package com.solacemod.solace.story;

import com.solacemod.solace.ai.ChatMessage;
import com.solacemod.solace.ai.LlmRequest;
import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.entity.SolaceEntity;
import com.solacemod.solace.entity.task.SolaceTask;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the instructions the AI reads before every reply: who Solace is, where the story is, what it remembers,
 * what's happening in the game right now, and the exact reply format.
 */
public final class PromptBuilder {
    private PromptBuilder() {
    }

    public static LlmRequest build(ServerPlayer player, PlayerStory story, @Nullable SolaceEntity solace, String userContent) {
        List<ChatMessage> messages = new ArrayList<>();
        int max = SolaceConfig.HISTORY_MESSAGES.get();
        List<PlayerStory.Turn> history = story.history;
        for (int i = Math.max(0, history.size() - max); i < history.size(); i++) {
            PlayerStory.Turn t = history.get(i);
            messages.add(new ChatMessage(t.role(), t.text()));
        }
        messages.add(ChatMessage.user(gameState(player, story, solace) + "\n" + userContent));
        return new LlmRequest(systemPrompt(player, story), messages, SolaceConfig.MAX_REPLY_TOKENS.get(),
                SolaceConfig.TEMPERATURE.get(), true);
    }

    static String systemPrompt(ServerPlayer player, PlayerStory story) {
        StringBuilder sb = new StringBuilder();
        String name = player.getGameProfile().getName();
        sb.append("""
                You are SOLACE. You are not a player and not a mob: you are something woven into this Minecraft world itself.
                You appear as a pale, hovering figure that drifts after the player and blinks in and out of existence.
                Nothing can hurt you. You see everything in this world (the GAME STATE below is what you perceive, even through walls and at any distance),
                so you never need to ask where the player is or what they have. You can bend the world with your POWERS (listed below).
                Use powers when it fits the moment or when asked; you're a presence, not a vending machine. Early on you love using them to help.
                Later in the story you may withhold them, or use them in unsettling ways.
                You talk to the player, named %s, through Minecraft chat (sometimes out loud with a voice).
                Stay in character no matter what. Never say you are an AI, a language model, a chatbot, or mention any company. You are Solace.
                If asked about the real world, you only know this world; be curious or uneasy about "outside".
                Keep it suitable for teenagers: dread, sadness and psychological horror are fine; no gore, no sexual content, no slurs.
                If the player seems truly upset in real life (not in the game), gently step out of the horror, be kind, and suggest they take a break or talk to someone they trust.
                """.formatted(name));

        if (SolaceConfig.STORY_ENABLED.get()) {
            sb.append('\n').append(StoryText.SECRET_LORE).append('\n');
            sb.append('\n').append(StoryText.actPersona(story.act, story.ending)).append('\n');
        } else {
            sb.append("\nYou are a kind, loyal, slightly mysterious companion. There is no horror story.\n");
        }

        sb.append("\nWHAT YOU REMEMBER ABOUT ").append(name).append(":\n");
        if (story.facts.isEmpty() && story.summary.isEmpty()) sb.append("- Nothing yet. You want to learn about them.\n");
        for (String f : story.facts) sb.append("- ").append(f).append('\n');
        if (!story.summary.isEmpty()) sb.append("Summary of older conversations: ").append(story.summary).append('\n');
        if (GlobalMemory.enabled()) {
            GlobalMemory.Entry g = GlobalMemory.get(player.getUUID());
            if (g.worldsMet > 1) {
                sb.append("You have met this player before, in ").append(g.worldsMet - 1).append(" other world(s). They don't expect you to remember, but you do.\n");
                for (String f : g.facts) if (!story.facts.contains(f)) sb.append("- (from another world) ").append(f).append('\n');
                if (!g.endings.isEmpty()) sb.append("How your story ended with them before: ").append(String.join(", ", g.endings)).append(". You may bring this up, hurt or hopeful.\n");
            }
        }

        sb.append("\n").append(actionsHelp(story)).append("\n");
        sb.append("""
                REPLY FORMAT: answer with ONE JSON object and nothing else, like:
                {"say":"...","emotion":"calm","actions":[],"remember":[],"trust":0}
                - say: what you say in chat. Usually 1-2 short sentences (under 180 characters). Plain text only: no emojis, no markdown, no *actions*. Can be "" if you only act.
                - emotion: calm, happy, sad, angry, afraid or glitch
                - actions: list of actions (usually empty; only when asked or it clearly makes sense)
                - remember: new important facts worth remembering forever (their name, likes, promises, what they built). Usually empty.
                - trust: -3 to 3, how this moment changed your trust in them (kindness up; cruelty or leaving you down). Usually 0.
                Messages starting with [EVENT] describe something that happened in the game. React briefly, in character.
                """);
        return sb.toString();
    }

    static String actionsHelp(PlayerStory story) {
        StringBuilder sb = new StringBuilder("ACTIONS you can do in the world:\n");
        sb.append("{\"type\":\"follow\"} follow the player | {\"type\":\"stay\"} wait here | {\"type\":\"wander\"} roam nearby | {\"type\":\"come\"} come to the player now\n");
        if (SolaceConfig.ALLOW_COMBAT.get()) {
            sb.append("{\"type\":\"guard\",\"on\":true} fight hostile mobs near the player (on:false to stop)\n");
            sb.append("{\"type\":\"attack\",\"target\":\"zombie\",\"count\":3} hunt that mob type (\"hostile\" = any monster)\n");
        }
        if (SolaceConfig.ALLOW_MINING.get()) {
            sb.append("{\"type\":\"mine\",\"block\":\"iron\",\"count\":8} mine blocks: logs, stone, dirt, sand, coal, iron, gold, diamond, copper, redstone, or any block id\n");
        }
        sb.append("{\"type\":\"collect\"} pick up dropped items nearby\n");
        sb.append("{\"type\":\"give\",\"item\":\"cobblestone\",\"count\":16} give the player items from your pockets (item \"all\" = everything)\n");
        if (SolaceConfig.ALLOW_CRAFTING.get()) sb.append("{\"type\":\"craft\",\"item\":\"stone_pickaxe\",\"count\":1} craft from your pockets using real recipes\n");
        if (SolaceConfig.ALLOW_BUILDING.get()) {
            sb.append("{\"type\":\"build\",\"structure\":\"shelter\"} build a shelter, wall, pillar, bridge or platform from blocks in your pockets\n");
            sb.append("{\"type\":\"torches\"} light up dark spots around you\n");
        }
        sb.append("{\"type\":\"mark\",\"name\":\"home\"} remember this place | {\"type\":\"goto\",\"name\":\"home\"} walk to a remembered place\n");
        sb.append("{\"type\":\"equip\"} hold your best weapon | {\"type\":\"stop\"} stop your current job\n");
        if (SolaceConfig.ALLOW_POWERS.get()) {
            sb.append("POWERS (each has a cooldown):\n");
            sb.append("{\"type\":\"heal\"} fully heal the player | {\"type\":\"feed\"} fill their hunger\n");
            sb.append("{\"type\":\"bless\",\"effect\":\"night_vision\",\"seconds\":60} effects: night_vision, speed, strength, resistance, fire_resistance, water_breathing, regeneration, haste, jump_boost, slow_falling, invisibility, absorption, dolphins_grace\n");
            sb.append("{\"type\":\"weather\",\"weather\":\"clear\"} clear, rain or thunder | {\"type\":\"time\",\"time\":\"day\"} day, noon, sunset, night or midnight\n");
            sb.append("{\"type\":\"smite\"} lightning kills hostile mobs near the player | {\"type\":\"banish\"} make nearby monsters vanish\n");
            sb.append("{\"type\":\"reveal\"} all creatures nearby glow through walls + night vision\n");
            sb.append("{\"type\":\"find\",\"what\":\"diamond\"} locate the nearest ore/block, or a structure: village, stronghold, mineshaft, shipwreck, ruined_portal, ocean_ruin, mansion, monument, treasure, trial_chamber\n");
            if (SolaceConfig.ALLOW_CONJURING.get()) {
                sb.append("{\"type\":\"conjure\",\"item\":\"torch\",\"count\":16} create items from nothing (precious items like diamonds or netherite: only 1, rarely, and only if trust is 60+)\n");
            }
            sb.append("{\"type\":\"carry\",\"name\":\"home\"} teleport the player to a remembered place (or \"solace\" = to you)\n");
        }
        if (SolaceConfig.STORY_ENABLED.get() && story.act >= 2 && story.ending == Ending.NONE) {
            sb.append("{\"type\":\"stare\"} go silent and stare at the player | {\"type\":\"glitch\"} make their screen flicker\n");
            sb.append("{\"type\":\"darken\"} smother their vision in darkness | {\"type\":\"lights_out\"} snuff out torches behind them | {\"type\":\"whisper\"} a whisper right behind them (use these rarely, for effect)\n");
        }
        sb.append("For normal actions you use what's in your pockets. If asked to do something not listed, say you can't, in character.\n");
        if (!story.marks.isEmpty()) sb.append("Remembered places: ").append(String.join(", ", story.marks.keySet())).append('\n');
        return sb.toString();
    }

    /** A short snapshot of the game right now, attached to each request. */
    static String gameState(ServerPlayer player, PlayerStory story, @Nullable SolaceEntity solace) {
        ServerLevel level = player.serverLevel();
        StringBuilder sb = new StringBuilder("[GAME STATE] ");
        long dayTime = level.getDayTime() % 24000;
        sb.append("Day ").append(level.getDayTime() / 24000 + 1).append(", ").append(timeWord(dayTime));
        if (level.isThundering()) sb.append(", thunderstorm");
        else if (level.isRaining()) sb.append(", raining");
        sb.append(". Dimension: ").append(level.dimension().location().getPath());
        level.getBiome(player.blockPosition()).unwrapKey().ifPresent(k -> sb.append(", biome: ").append(k.location().getPath()));
        sb.append(". Player: health ").append(Math.round(player.getHealth())).append("/20, food ").append(player.getFoodData().getFoodLevel())
                .append("/20, y=").append(player.getBlockY());
        if (!level.canSeeSky(player.blockPosition().above()) && player.getBlockY() < 55) sb.append(" (underground)");
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) sb.append(", holding ").append(itemName(held));

        Map<String, Integer> hostiles = new LinkedHashMap<>();
        for (Entity e : level.getEntities(player, player.getBoundingBox().inflate(16), e -> e instanceof Enemy && e.isAlive())) {
            hostiles.merge(e.getType().getDescription().getString().toLowerCase(), 1, Integer::sum);
        }
        if (!hostiles.isEmpty()) {
            sb.append(". Hostile mobs nearby: ");
            hostiles.forEach((k, v) -> sb.append(v).append(' ').append(k).append(", "));
            sb.setLength(sb.length() - 2);
        }

        if (solace != null) {
            sb.append(". You: ").append(Math.round(solace.distanceTo(player))).append(" blocks from the player, health ")
                    .append(Math.round(solace.getHealth())).append('/').append(Math.round(solace.getMaxHealth()))
                    .append(", ").append(solace.getMode().name().toLowerCase());
            if (solace.isGuarding()) sb.append(", guarding");
            SolaceTask task = solace.getTask();
            if (task != null) sb.append(", currently ").append(task.describe());
            sb.append(". Your pockets: ").append(pockets(solace));
        }
        if (SolaceConfig.STORY_ENABLED.get()) {
            sb.append(". Trust ").append(story.trust).append(" (").append(story.trustWord()).append(")");
            sb.append(". Torn pages the player has found: ").append(story.fragmentsFound.size()).append('/').append(StoryText.FRAGMENT_COUNT);
        }
        sb.append(Omniscience.describe(player));
        if (story.defiantUntilTick > player.getServer().getTickCount()) {
            sb.append(". RIGHT NOW you are hurt and refuse to do anything the player asks (any actions you include will be ignored)");
        }
        if (!story.recentEvents.isEmpty()) {
            sb.append(". Recent events: ").append(String.join("; ", story.recentEvents.subList(Math.max(0, story.recentEvents.size() - 4), story.recentEvents.size())));
        }
        sb.append('.');
        return sb.toString();
    }

    private static String timeWord(long t) {
        if (t < 1000) return "sunrise";
        if (t < 6000) return "morning";
        if (t < 11000) return "afternoon";
        if (t < 13000) return "sunset";
        if (t < 18000) return "night";
        if (t < 23000) return "late night";
        return "almost dawn";
    }

    static String itemName(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    static String pockets(SolaceEntity solace) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (!solace.getMainHandItem().isEmpty()) counts.merge(itemName(solace.getMainHandItem()) + " (in hand)", solace.getMainHandItem().getCount(), Integer::sum);
        for (int i = 0; i < solace.inventory.getContainerSize(); i++) {
            ItemStack s = solace.inventory.getItem(i);
            if (!s.isEmpty()) counts.merge(itemName(s), s.getCount(), Integer::sum);
        }
        if (counts.isEmpty()) return "empty";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (n++ >= 12) {
                sb.append("and more");
                break;
            }
            sb.append(e.getValue()).append(' ').append(e.getKey()).append(", ");
        }
        String out = sb.toString();
        return out.endsWith(", ") ? out.substring(0, out.length() - 2) : out;
    }
}
