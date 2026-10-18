package com.solacemod.solace.config;

import net.neoforged.neoforge.common.ModConfigSpec;


/**
 * Settings saved in config/solace-common.toml. You can also edit them in-game: Mods > Solace > Config.
 *
 * WARNING: your API key is stored in this file. Never share this file or put it in a modpack.
 * Tip: write env:MY_KEY_NAME instead of the key to read it from an environment variable.
 */
public final class SolaceConfig {
    public static final ModConfigSpec SPEC;

    // AI brain
    public static final ModConfigSpec.ConfigValue<String> PROVIDER;
    public static final ModConfigSpec.ConfigValue<String> API_KEY;
    public static final ModConfigSpec.ConfigValue<String> MODEL;
    public static final ModConfigSpec.ConfigValue<String> BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> FALLBACK_PROVIDER;
    public static final ModConfigSpec.ConfigValue<String> FALLBACK_API_KEY;
    public static final ModConfigSpec.ConfigValue<String> FALLBACK_MODEL;
    public static final ModConfigSpec.ConfigValue<String> FALLBACK_BASE_URL;
    public static final ModConfigSpec.DoubleValue TEMPERATURE;
    public static final ModConfigSpec.IntValue MAX_REPLY_TOKENS;
    public static final ModConfigSpec.IntValue TIMEOUT_SECONDS;
    public static final ModConfigSpec.ConfigValue<String> ANTHROPIC_EFFORT;

    // Cost safety
    public static final ModConfigSpec.IntValue MAX_REQUESTS_PER_MINUTE;
    public static final ModConfigSpec.IntValue DAILY_REQUEST_LIMIT;

    // Memory
    public static final ModConfigSpec.IntValue HISTORY_MESSAGES;
    public static final ModConfigSpec.BooleanValue SUMMARIZE_MEMORY;
    public static final ModConfigSpec.BooleanValue CROSS_WORLD_MEMORY;

    // Chat
    public static final ModConfigSpec.ConfigValue<String> LISTEN_MODE;
    public static final ModConfigSpec.IntValue HEARING_RANGE;
    public static final ModConfigSpec.BooleanValue REPLIES_VISIBLE_TO_ALL;
    public static final ModConfigSpec.BooleanValue AMBIENT_CHATTER;
    public static final ModConfigSpec.IntValue AMBIENT_CHATTER_MINUTES;

    // Story
    public static final ModConfigSpec.BooleanValue STORY_ENABLED;
    public static final ModConfigSpec.DoubleValue STORY_SPEED;

    // Horror
    public static final ModConfigSpec.DoubleValue HORROR_INTENSITY;
    public static final ModConfigSpec.BooleanValue ALLOW_WORLD_CHANGES;
    public static final ModConfigSpec.BooleanValue ALLOW_FAKE_MESSAGES;
    public static final ModConfigSpec.BooleanValue ALLOW_FAKE_CRASH;
    public static final ModConfigSpec.BooleanValue ALLOW_SCREEN_EFFECTS;

    // Abilities
    public static final ModConfigSpec.BooleanValue ALLOW_MINING;
    public static final ModConfigSpec.BooleanValue ALLOW_BUILDING;
    public static final ModConfigSpec.BooleanValue ALLOW_COMBAT;
    public static final ModConfigSpec.BooleanValue ALLOW_CRAFTING;
    public static final ModConfigSpec.BooleanValue DEFEND_AGAINST_PLAYERS;
    public static final ModConfigSpec.BooleanValue ALLOW_POWERS;
    public static final ModConfigSpec.BooleanValue ALLOW_CONJURING;
    public static final ModConfigSpec.IntValue MAX_MINE_COUNT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Solace's AI brain. Solace needs one to talk (free options: groq, gemini, ollama, lmstudio).",
                "WARNING: Never share this file or include it in a modpack - it contains your API key.").push("ai");
        PROVIDER = b.comment("Which AI service to use. One of:",
                        "groq (free tier, fast), openai, anthropic, gemini (free tier), openrouter, deepseek, mistral, xai, together, cerebras,",
                        "ollama (free, runs on your PC), lmstudio (free, runs on your PC), custom (any OpenAI-compatible server)")
                .define("provider", "groq");
        API_KEY = b.comment("Your API key for the provider above. Or write env:VARIABLE_NAME to read it from an environment variable.",
                        "Not needed for ollama or lmstudio.")
                .define("apiKey", "");
        MODEL = b.comment("Model name. Leave blank for the provider's default. Check your provider's website for current model names.")
                .define("model", "");
        BASE_URL = b.comment("Server address. Leave blank for the provider's default. Required for 'custom'.")
                .define("baseUrl", "");
        FALLBACK_PROVIDER = b.comment("Backup provider if the main one fails (same names as above). 'none' = no backup.")
                .define("fallbackProvider", "none");
        FALLBACK_API_KEY = b.define("fallbackApiKey", "");
        FALLBACK_MODEL = b.define("fallbackModel", "");
        FALLBACK_BASE_URL = b.define("fallbackBaseUrl", "");
        TEMPERATURE = b.comment("How creative/random replies are. 0.0 = predictable, 1.2 = wild.")
                .defineInRange("temperature", 0.9, 0.0, 2.0);
        MAX_REPLY_TOKENS = b.comment("Maximum reply length in tokens (about 3/4 of a word each).")
                .defineInRange("maxReplyTokens", 800, 64, 8000);
        TIMEOUT_SECONDS = b.comment("How long to wait for a reply before giving up.")
                .defineInRange("timeoutSeconds", 45, 5, 180);
        ANTHROPIC_EFFORT = b.comment("Anthropic only: how hard Claude thinks. low = fastest and cheapest. low, medium, high")
                .define("anthropicEffort", "low");
        b.pop();

        b.comment("Limits that protect you from surprise bills. Each Solace reply is one request.").push("cost_safety");
        MAX_REQUESTS_PER_MINUTE = b.comment("Most AI requests allowed per minute for the whole server.")
                .defineInRange("maxRequestsPerMinute", 12, 1, 600);
        DAILY_REQUEST_LIMIT = b.comment("Most AI requests allowed per real-world day. After this Solace goes quiet until tomorrow. 0 = no limit.")
                .defineInRange("dailyRequestLimit", 400, 0, 1_000_000);
        b.pop();

        b.push("memory");
        HISTORY_MESSAGES = b.comment("How many recent chat lines Solace sees each time. More = smarter but each request costs more.")
                .defineInRange("historyMessages", 10, 2, 100);
        SUMMARIZE_MEMORY = b.comment("When old messages fall out of history, ask the AI to summarize them into long-term memory (1 extra request now and then).")
                .define("summarizeMemory", true);
        CROSS_WORLD_MEMORY = b.comment("Solace remembers you across different worlds. Stored in the 'solace' folder of your game directory.")
                .define("crossWorldMemory", true);
        b.pop();

        b.push("chat");
        LISTEN_MODE = b.comment("When does Solace treat your chat as talking to it?",
                        "nearby = when Solace is close or you say its name, prefix = only messages starting with 'solace' or '@solace', always = every message")
                .defineInList("listenMode", "nearby", java.util.Arrays.asList("nearby", "prefix", "always"));
        HEARING_RANGE = b.comment("Blocks away Solace can hear you in 'nearby' mode.")
                .defineInRange("hearingRange", 24, 4, 128);
        REPLIES_VISIBLE_TO_ALL = b.comment("Multiplayer: show Solace's replies to everyone instead of only its owner.")
                .define("repliesVisibleToAll", false);
        AMBIENT_CHATTER = b.comment("Solace sometimes talks on its own (night falling, low health, caves...). Uses AI requests.")
                .define("ambientChatter", true);
        AMBIENT_CHATTER_MINUTES = b.comment("Minimum minutes between unprompted comments.")
                .defineInRange("ambientChatterMinutes", 6, 1, 120);
        b.pop();

        b.push("story");
        STORY_ENABLED = b.comment("Run the story (8 chapters, torn pages, the Watcher, the endings). If false, Solace is just a friendly companion.")
                .define("storyEnabled", true);
        STORY_SPEED = b.comment("Story pace. 1.0 = about 3 hours of play to reach the ending. 2.0 = twice as fast.")
                .defineInRange("storySpeed", 1.0, 0.1, 20.0);
        b.pop();

        b.push("horror");
        HORROR_INTENSITY = b.comment("How often scary things happen. 0 = never, 1 = normal, 3 = constant.")
                .defineInRange("horrorIntensity", 1.0, 0.0, 5.0);
        ALLOW_WORLD_CHANGES = b.comment("Horror events may change blocks: torches going out, doors opening, signs appearing. Never breaks your builds.")
                .define("allowWorldChanges", true);
        ALLOW_FAKE_MESSAGES = b.comment("Fake chat messages and fake 'player joined' messages.")
                .define("allowFakeMessages", true);
        ALLOW_FAKE_CRASH = b.comment("A fake crash screen (harmless, press Esc to close).")
                .define("allowFakeCrash", true);
        ALLOW_SCREEN_EFFECTS = b.comment("Screen static and glitch overlays. Each player can also turn these off in their client config.")
                .define("allowScreenEffects", true);
        b.pop();

        b.push("abilities");
        ALLOW_MINING = b.define("allowMining", true);
        ALLOW_BUILDING = b.define("allowBuilding", true);
        ALLOW_COMBAT = b.define("allowCombat", true);
        ALLOW_CRAFTING = b.define("allowCrafting", true);
        DEFEND_AGAINST_PLAYERS = b.comment("Multiplayer: Solace attacks other players who hurt its owner.")
                .define("defendAgainstPlayers", false);
        MAX_MINE_COUNT = b.comment("Most blocks Solace will mine for one request.")
                .defineInRange("maxMineCount", 32, 1, 256);
        ALLOW_POWERS = b.comment("Solace's powers: heal you, bless you with effects, change weather and time, strike or banish monsters,",
                        "reveal hidden creatures, find ores and structures, carry you to saved places.")
                .define("allowPowers", true);
        ALLOW_CONJURING = b.comment("Solace can create items out of nothing (precious items only when it trusts you, and rarely).")
                .define("allowConjuring", true);
        b.pop();

        SPEC = b.build();
    }

    private SolaceConfig() {
    }
}
