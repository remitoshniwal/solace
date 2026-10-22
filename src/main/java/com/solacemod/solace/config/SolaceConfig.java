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
    /** Extra brains (Brain 2 to 5). Brain 1 is provider/apiKey/model/baseUrl above. */
    public static final int EXTRA_BRAINS = 4;
    public static final java.util.List<ModConfigSpec.ConfigValue<String>> BRAIN_PROVIDER = new java.util.ArrayList<>();
    public static final java.util.List<ModConfigSpec.ConfigValue<String>> BRAIN_API_KEY = new java.util.ArrayList<>();
    public static final java.util.List<ModConfigSpec.ConfigValue<String>> BRAIN_MODEL = new java.util.ArrayList<>();
    public static final java.util.List<ModConfigSpec.ConfigValue<String>> BRAIN_BASE_URL = new java.util.ArrayList<>();
    public static final ModConfigSpec.DoubleValue TEMPERATURE;
    public static final ModConfigSpec.IntValue MAX_REPLY_TOKENS;
    public static final ModConfigSpec.IntValue TIMEOUT_SECONDS;
    public static final ModConfigSpec.ConfigValue<String> ANTHROPIC_EFFORT;

    // Cost safety
    public static final ModConfigSpec.IntValue MAX_REQUESTS_PER_MINUTE;
    public static final ModConfigSpec.IntValue DAILY_REQUEST_LIMIT;
    public static final ModConfigSpec.IntValue DAILY_REQUESTS_PER_PLAYER;

    // Memory
    public static final ModConfigSpec.IntValue HISTORY_MESSAGES;
    public static final ModConfigSpec.BooleanValue SUMMARIZE_MEMORY;
    public static final ModConfigSpec.BooleanValue CROSS_WORLD_MEMORY;
    public static final ModConfigSpec.BooleanValue REMEMBER_ENDINGS;

    // Chat
    public static final ModConfigSpec.ConfigValue<String> LISTEN_MODE;
    public static final ModConfigSpec.IntValue HEARING_RANGE;
    public static final ModConfigSpec.BooleanValue REPLIES_VISIBLE_TO_ALL;
    public static final ModConfigSpec.BooleanValue AMBIENT_CHATTER;
    public static final ModConfigSpec.IntValue AMBIENT_CHATTER_MINUTES;

    // Story
    public static final ModConfigSpec.BooleanValue STORY_ENABLED;
    public static final ModConfigSpec.DoubleValue STORY_SPEED;
    public static final ModConfigSpec.ConfigValue<String> WHO_GETS_SOLACE;
    public static final ModConfigSpec.BooleanValue PLAYTEST_LOG;
    public static final ModConfigSpec.BooleanValue STORY_NUDGES;

    // Horror
    public static final ModConfigSpec.DoubleValue HORROR_INTENSITY;
    public static final ModConfigSpec.BooleanValue ALLOW_WORLD_CHANGES;
    public static final ModConfigSpec.BooleanValue ALLOW_FAKE_MESSAGES;
    public static final ModConfigSpec.BooleanValue ALLOW_FAKE_CRASH;
    public static final ModConfigSpec.BooleanValue ALLOW_SCREEN_EFFECTS;

    // Multiplayer (players together)
    public static final ModConfigSpec.BooleanValue JEALOUSY;
    public static final ModConfigSpec.BooleanValue WITNESSES;
    public static final ModConfigSpec.BooleanValue SOLACES_MEET;
    public static final ModConfigSpec.BooleanValue ENDINGS_RIPPLE;
    public static final ModConfigSpec.BooleanValue HOLLOW_WITH_FRIENDS;
    public static final ModConfigSpec.BooleanValue PERSONAL_FREEZE;
    public static final ModConfigSpec.BooleanValue PLAYER_AI;
    public static final ModConfigSpec.ConfigValue<String> LEND_AI;
    public static final ModConfigSpec.BooleanValue MULTIPLAYER_AI_COMMENTS;

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
        FALLBACK_PROVIDER = b.comment("Old setting: the backup provider. It's moved into brain 2 automatically. Use the [ai.brains] section instead.")
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
        b.comment("More brains (up to 5 in total), so free limits last longer. The provider above is brain 1, the best one.",
                "Solace uses brain 1 for story moments and replies, and moves to the next brain before one runs out.",
                "Background work (memory summaries, comments on its own) goes to whichever brain has the most room left.",
                "Use 'none' for an empty slot. Tip: use different providers. Making several free accounts on one service breaks most providers' rules.")
                .push("brains");
        for (int i = 2; i <= EXTRA_BRAINS + 1; i++) {
            b.push("brain" + i);
            BRAIN_PROVIDER.add(b.comment("Provider for brain " + i + " (same names as above), or none.").define("provider", "none"));
            BRAIN_API_KEY.add(b.define("apiKey", ""));
            BRAIN_MODEL.add(b.comment("Blank = the provider's default model.").define("model", ""));
            BRAIN_BASE_URL.add(b.comment("Blank = the provider's default address.").define("baseUrl", ""));
            b.pop();
        }
        b.pop();
        b.pop();

        b.comment("Limits that protect you from surprise bills. Each Solace reply is one request.").push("cost_safety");
        MAX_REQUESTS_PER_MINUTE = b.comment("Most AI requests allowed per minute for the whole server.")
                .defineInRange("maxRequestsPerMinute", 12, 1, 600);
        DAILY_REQUEST_LIMIT = b.comment("Most AI requests allowed per real-world day. After this Solace goes quiet until tomorrow. 0 = no limit.")
                .defineInRange("dailyRequestLimit", 400, 0, 1_000_000);
        DAILY_REQUESTS_PER_PLAYER = b.comment("Multiplayer: most AI requests one player can use per real-world day, so one chatty player",
                        "can't use up the whole server's limit. Not used in single player (unless the world is open to LAN). 0 = no limit.")
                .defineInRange("dailyRequestsPerPlayer", 150, 0, 1_000_000);
        b.pop();

        b.push("memory");
        HISTORY_MESSAGES = b.comment("How many recent chat lines Solace sees each time. More = smarter but each request costs more.")
                .defineInRange("historyMessages", 10, 2, 100);
        SUMMARIZE_MEMORY = b.comment("When old messages fall out of history, ask the AI to summarize them into long-term memory (1 extra request now and then).")
                .define("summarizeMemory", true);
        CROSS_WORLD_MEMORY = b.comment("Solace remembers you across different worlds. Stored in the 'solace' folder of your game directory.")
                .define("crossWorldMemory", true);
        REMEMBER_ENDINGS = b.comment("Solace remembers how your story ended (in other worlds, or here before a /solace reset), and the next story changes a little.",
                        "Uses about 1 extra AI request per new story.")
                .define("rememberEndings", true);
        b.pop();

        b.push("chat");
        LISTEN_MODE = b.comment("When does Solace treat your chat as talking to it?",
                        "nearby = when Solace is close or you say its name, prefix = only messages starting with 'solace' or '@solace', always = every message")
                .defineInList("listenMode", "nearby", java.util.Arrays.asList("nearby", "prefix", "always"));
        HEARING_RANGE = b.comment("Blocks away Solace can hear you in 'nearby' mode.")
                .defineInRange("hearingRange", 24, 4, 128);
        REPLIES_VISIBLE_TO_ALL = b.comment("Multiplayer: everyone sees Solace's answers when its owner talks to it. Story moments always stay private.")
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
        WHO_GETS_SOLACE = b.comment("Multiplayer: who gets their own Solace.",
                        "everyone = every player. opt_in = only players who type /solace join (saves AI requests on busy servers).")
                .defineInList("whoGetsSolace", "everyone", java.util.Arrays.asList("everyone", "opt_in"));
        PLAYTEST_LOG = b.comment("Playtesting: write a timeline of everything that happens (chapters, pages, scares, what Solace said)",
                        "to <game folder>/solace/playtest/. Notes from /solace note are always saved. See docs/PLAYTEST.md.")
                .define("playtestLog", false);
        STORY_NUDGES = b.comment("Small hints when a player seems stuck: a rustle where a page is lying, Solace warning them away from the house.",
                        "The timings are guesses until the story has been playtested (story/Nudges.java).")
                .define("nudges", true);
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

        b.comment("Multiplayer: what happens when several players have a Solace. None of this does anything in single player.").push("multiplayer");
        JEALOUSY = b.comment("Solace notices who you spend time with, and gets jealous (later in the story).")
                .define("jealousy", true);
        WITNESSES = b.comment("Other players can see your Solace doing strange things you missed, and get told about it.")
                .define("witnesses", true);
        SOLACES_MEET = b.comment("When two players' Solaces get close, they stop and look at each other.")
                .define("solacesMeet", true);
        ENDINGS_RIPPLE = b.comment("When one player finishes the story, other players' Solaces feel it.")
                .define("endingsRipple", true);
        HOLLOW_WITH_FRIENDS = b.comment("Friends can join a boss fight: they see the boss bar, it gets stronger for each friend who fights, and it fights back.")
                .define("hollowWithFriends", true);
        PERSONAL_FREEZE = b.comment("With other players online, one strange moment happens only on that player's screen instead of being skipped.")
                .define("personalFreeze", true);
        MULTIPLAYER_AI_COMMENTS = b.comment("Solace talks about these moments using the AI (jealous remarks, what you witnessed, endings it felt).",
                        "Uses AI requests, paid from the server owner's key. Off = short written lines instead, no extra requests.")
                .define("aiComments", true);
        PLAYER_AI = b.comment("Players may give Solace their own AI key, so their requests don't cost you anything.",
                        "Only for servers among friends: a player's key passes through this server (it is never saved, logged or shown,",
                        "and is forgotten when they leave, but whoever runs the server could still get at it). Off by default.")
                .define("playerAi", false);
        LEND_AI = b.comment("Who may use YOUR AI brains (the ones above).",
                        "always = players without their own AI, and players whose own AI has run out or fails",
                        "no_backup = only players without their own AI",
                        "never = nobody else: players without their own AI get a Solace that only follows simple orders")
                .defineInList("lendAi", "always", java.util.Arrays.asList("always", "no_backup", "never"));
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
