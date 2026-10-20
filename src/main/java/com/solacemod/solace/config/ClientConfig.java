package com.solacemod.solace.config;

import net.neoforged.neoforge.common.ModConfigSpec;


/**
 * Per-player settings saved in config/solace-client.toml: voice chat and visual comfort options.
 * Voice keys live here (not on the server) so each player pays for their own voice usage.
 */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue VOICE_INPUT;
    public static final ModConfigSpec.ConfigValue<String> STT_BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> STT_API_KEY;
    public static final ModConfigSpec.ConfigValue<String> STT_MODEL;
    public static final ModConfigSpec.ConfigValue<String> STT_LANGUAGE;

    public static final ModConfigSpec.ConfigValue<String> TTS_PROVIDER;
    public static final ModConfigSpec.ConfigValue<String> TTS_BASE_URL;
    public static final ModConfigSpec.ConfigValue<String> TTS_API_KEY;
    public static final ModConfigSpec.ConfigValue<String> TTS_MODEL;
    public static final ModConfigSpec.ConfigValue<String> TTS_VOICE;
    public static final ModConfigSpec.IntValue TTS_SAMPLE_RATE;
    public static final ModConfigSpec.DoubleValue TTS_VOLUME;

    public static final ModConfigSpec.BooleanValue GLITCH_EFFECTS;
    public static final ModConfigSpec.BooleanValue FAKE_CRASH_SCREENS;
    public static final ModConfigSpec.BooleanValue BOND_HINT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Talk to Solace with your microphone. Hold the push-to-talk key (default G) while speaking.",
                "Uses a speech-to-text service. Groq's Whisper is fast and has a free tier.").push("voice_input");
        VOICE_INPUT = b.define("enabled", false);
        STT_BASE_URL = b.comment("Any OpenAI-compatible speech-to-text server. Groq: https://api.groq.com/openai/v1  OpenAI: https://api.openai.com/v1")
                .define("baseUrl", "https://api.groq.com/openai/v1");
        STT_API_KEY = b.comment("API key for speech-to-text. Leave blank to reuse the main AI key (only works in singleplayer when the main provider is the same service).")
                .define("apiKey", "");
        STT_MODEL = b.comment("Groq: whisper-large-v3-turbo   OpenAI: whisper-1 or gpt-4o-mini-transcribe")
                .define("model", "whisper-large-v3-turbo");
        STT_LANGUAGE = b.comment("Language code like en, es, fr. Blank = auto-detect.")
                .define("language", "en");
        b.pop();

        b.comment("Hear Solace speak out loud.").push("voice_output");
        TTS_PROVIDER = b.comment("none, openai_compatible (OpenAI, or free local servers like Kokoro-FastAPI), elevenlabs")
                .defineInList("provider", "none", java.util.Arrays.asList("none", "openai_compatible", "elevenlabs"));
        TTS_BASE_URL = b.comment("For openai_compatible. OpenAI: https://api.openai.com/v1  Local Kokoro: http://localhost:8880/v1")
                .define("baseUrl", "https://api.openai.com/v1");
        TTS_API_KEY = b.comment("API key for the voice service (blank for local servers).")
                .define("apiKey", "");
        TTS_MODEL = b.comment("OpenAI: gpt-4o-mini-tts   Kokoro: kokoro   ElevenLabs: eleven_flash_v2_5")
                .define("model", "gpt-4o-mini-tts");
        TTS_VOICE = b.comment("Voice name. OpenAI: alloy, ash, sage, shimmer...  Kokoro: af_sky...  ElevenLabs: a voice ID from your voice library")
                .define("voice", "sage");
        TTS_SAMPLE_RATE = b.comment("Audio sample rate of raw PCM replies. OpenAI and Kokoro use 24000.")
                .defineInRange("sampleRate", 24000, 8000, 48000);
        TTS_VOLUME = b.defineInRange("volume", 1.0, 0.0, 2.0);
        b.pop();

        b.comment("Comfort settings. Turn these off if flashing or sudden effects bother you.").push("comfort");
        GLITCH_EFFECTS = b.comment("Screen static / glitch overlays.").define("glitchEffects", true);
        FAKE_CRASH_SCREENS = b.comment("The fake crash screen scare.").define("fakeCrashScreens", true);
        b.pop();

        b.comment("What Solace shows on your screen.").push("display");
        BOND_HINT = b.comment("A small flame in the top left corner: how strong Solace is right now, and how much you owe it. No numbers.")
                .define("bondHint", true);
        b.pop();

        SPEC = b.build();
    }

    private ClientConfig() {
    }
}
