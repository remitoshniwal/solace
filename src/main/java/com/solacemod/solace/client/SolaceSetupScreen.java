package com.solacemod.solace.client;

import com.solacemod.solace.ai.ChatMessage;
import com.solacemod.solace.ai.Http;
import com.solacemod.solace.ai.LlmException;
import com.solacemod.solace.ai.LlmProvider;
import com.solacemod.solace.ai.LlmRequest;
import com.solacemod.solace.ai.ProviderPreset;
import com.solacemod.solace.ai.ProviderSettings;
import com.solacemod.solace.config.ClientConfig;
import com.solacemod.solace.config.SolaceConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Solace's in-game settings screen: Esc > Mods > Solace > Config (or click the setup message in chat).
 * Players never need to open a config file. Changes are saved when you press Done.
 */
public final class SolaceSetupScreen extends Screen {
    private enum Page { AI, VOICE, STORY, ABILITIES }

    /** Providers in the order shown, with a short note for beginners. */
    private static final Map<String, String> PROVIDER_NOTES = new java.util.LinkedHashMap<>();

    static {
        PROVIDER_NOTES.put("groq", "Free tier, very fast. Easiest to start.");
        PROVIDER_NOTES.put("gemini", "Free tier from Google.");
        PROVIDER_NOTES.put("ollama", "Free, runs on your PC. No key. Needs a good GPU.");
        PROVIDER_NOTES.put("lmstudio", "Free, runs on your PC. No key.");
        PROVIDER_NOTES.put("openrouter", "Many models, some free (:free).");
        PROVIDER_NOTES.put("openai", "Paid.");
        PROVIDER_NOTES.put("anthropic", "Paid. Best quality. Haiku models are much cheaper.");
        PROVIDER_NOTES.put("deepseek", "Paid, very cheap.");
        PROVIDER_NOTES.put("mistral", "Paid, has a free tier.");
        PROVIDER_NOTES.put("xai", "Paid.");
        PROVIDER_NOTES.put("together", "Paid.");
        PROVIDER_NOTES.put("cerebras", "Paid, has a free tier. Very fast.");
        PROVIDER_NOTES.put("custom", "Any OpenAI-compatible server. Fill in Server URL and Model.");
    }

    private static final List<Double> SPEEDS = List.of(0.5, 1.0, 2.0, 5.0, 10.0);
    private static final List<Double> INTENSITIES = List.of(0.0, 0.5, 1.0, 2.0, 3.0);
    private static final List<Integer> DAILY_LIMITS = List.of(50, 100, 200, 400, 1000, 3000, 0);

    @Nullable
    private final Screen parent;
    @Nullable
    private final ModContainer container;
    private Page page = Page.AI;

    // Working copy of the settings (saved only when Done is pressed)
    /** Brains 1 to 5. Index 0 is brain 1 (the best one). "none" = empty slot (not allowed for brain 1). */
    private static final int BRAINS = SolaceConfig.EXTRA_BRAINS + 1;
    private final String[] providers = new String[BRAINS];
    private final String[] apiKeys = new String[BRAINS];
    private final String[] models = new String[BRAINS];
    private final String[] baseUrls = new String[BRAINS];
    /** Which brain the AI page is showing. */
    private int brain;
    private int dailyLimit;
    private boolean showKey;

    private boolean voiceIn;
    private String sttKey;
    private String ttsProvider;
    private String ttsKey;
    private String ttsVoice;
    private String ttsBaseUrl;
    private String ttsModel;

    private boolean storyEnabled;
    private double storySpeed;
    private double horror;
    private boolean worldChanges;
    private boolean fakeMessages;
    private boolean fakeCrash;
    private boolean screenEffects;
    private boolean glitchComfort;
    private boolean crashComfort;

    private boolean mining;
    private boolean building;
    private boolean combat;
    private boolean crafting;
    private boolean powers;
    private boolean conjuring;
    private String listenMode;
    private boolean ambient;
    private boolean crossWorld;

    private Component status = Component.empty();
    private boolean testing;

    public SolaceSetupScreen(@Nullable Screen parent, @Nullable ModContainer container) {
        super(Component.literal("Solace Setup"));
        this.parent = parent;
        this.container = container;
        load();
    }

    private void load() {
        providers[0] = SolaceConfig.PROVIDER.get();
        if (!PROVIDER_NOTES.containsKey(providers[0])) providers[0] = "groq";
        apiKeys[0] = SolaceConfig.API_KEY.get();
        models[0] = SolaceConfig.MODEL.get();
        baseUrls[0] = SolaceConfig.BASE_URL.get();
        for (int i = 1; i < BRAINS; i++) {
            providers[i] = SolaceConfig.BRAIN_PROVIDER.get(i - 1).get();
            if (!PROVIDER_NOTES.containsKey(providers[i])) providers[i] = "none";
            apiKeys[i] = SolaceConfig.BRAIN_API_KEY.get(i - 1).get();
            models[i] = SolaceConfig.BRAIN_MODEL.get(i - 1).get();
            baseUrls[i] = SolaceConfig.BRAIN_BASE_URL.get(i - 1).get();
        }
        // The old single "backup provider" becomes the first empty brain.
        String oldBackup = SolaceConfig.FALLBACK_PROVIDER.get();
        if (PROVIDER_NOTES.containsKey(oldBackup)) {
            for (int i = 1; i < BRAINS; i++) {
                if (!providers[i].equals("none")) continue;
                providers[i] = oldBackup;
                apiKeys[i] = SolaceConfig.FALLBACK_API_KEY.get();
                models[i] = SolaceConfig.FALLBACK_MODEL.get();
                baseUrls[i] = SolaceConfig.FALLBACK_BASE_URL.get();
                break;
            }
        }
        dailyLimit = SolaceConfig.DAILY_REQUEST_LIMIT.get();

        voiceIn = ClientConfig.VOICE_INPUT.get();
        sttKey = ClientConfig.STT_API_KEY.get();
        ttsProvider = ClientConfig.TTS_PROVIDER.get();
        ttsKey = ClientConfig.TTS_API_KEY.get();
        ttsVoice = ClientConfig.TTS_VOICE.get();
        ttsBaseUrl = ClientConfig.TTS_BASE_URL.get();
        ttsModel = ClientConfig.TTS_MODEL.get();

        storyEnabled = SolaceConfig.STORY_ENABLED.get();
        storySpeed = SolaceConfig.STORY_SPEED.get();
        horror = SolaceConfig.HORROR_INTENSITY.get();
        worldChanges = SolaceConfig.ALLOW_WORLD_CHANGES.get();
        fakeMessages = SolaceConfig.ALLOW_FAKE_MESSAGES.get();
        fakeCrash = SolaceConfig.ALLOW_FAKE_CRASH.get();
        screenEffects = SolaceConfig.ALLOW_SCREEN_EFFECTS.get();
        glitchComfort = ClientConfig.GLITCH_EFFECTS.get();
        crashComfort = ClientConfig.FAKE_CRASH_SCREENS.get();

        mining = SolaceConfig.ALLOW_MINING.get();
        building = SolaceConfig.ALLOW_BUILDING.get();
        combat = SolaceConfig.ALLOW_COMBAT.get();
        crafting = SolaceConfig.ALLOW_CRAFTING.get();
        powers = SolaceConfig.ALLOW_POWERS.get();
        conjuring = SolaceConfig.ALLOW_CONJURING.get();
        listenMode = SolaceConfig.LISTEN_MODE.get();
        ambient = SolaceConfig.AMBIENT_CHATTER.get();
        crossWorld = SolaceConfig.CROSS_WORLD_MEMORY.get();
    }

    private void save() {
        SolaceConfig.PROVIDER.set(providers[0]);
        SolaceConfig.API_KEY.set(apiKeys[0].trim());
        SolaceConfig.MODEL.set(models[0].trim());
        SolaceConfig.BASE_URL.set(baseUrls[0].trim());
        for (int i = 1; i < BRAINS; i++) {
            SolaceConfig.BRAIN_PROVIDER.get(i - 1).set(providers[i]);
            SolaceConfig.BRAIN_API_KEY.get(i - 1).set(apiKeys[i].trim());
            SolaceConfig.BRAIN_MODEL.get(i - 1).set(models[i].trim());
            SolaceConfig.BRAIN_BASE_URL.get(i - 1).set(baseUrls[i].trim());
        }
        SolaceConfig.FALLBACK_PROVIDER.set("none"); // moved into the brains above
        SolaceConfig.DAILY_REQUEST_LIMIT.set(dailyLimit);
        SolaceConfig.STORY_ENABLED.set(storyEnabled);
        SolaceConfig.STORY_SPEED.set(storySpeed);
        SolaceConfig.HORROR_INTENSITY.set(horror);
        SolaceConfig.ALLOW_WORLD_CHANGES.set(worldChanges);
        SolaceConfig.ALLOW_FAKE_MESSAGES.set(fakeMessages);
        SolaceConfig.ALLOW_FAKE_CRASH.set(fakeCrash);
        SolaceConfig.ALLOW_SCREEN_EFFECTS.set(screenEffects);
        SolaceConfig.ALLOW_MINING.set(mining);
        SolaceConfig.ALLOW_BUILDING.set(building);
        SolaceConfig.ALLOW_COMBAT.set(combat);
        SolaceConfig.ALLOW_CRAFTING.set(crafting);
        SolaceConfig.ALLOW_POWERS.set(powers);
        SolaceConfig.ALLOW_CONJURING.set(conjuring);
        SolaceConfig.LISTEN_MODE.set(listenMode);
        SolaceConfig.AMBIENT_CHATTER.set(ambient);
        SolaceConfig.CROSS_WORLD_MEMORY.set(crossWorld);
        SolaceConfig.SPEC.save();

        ClientConfig.VOICE_INPUT.set(voiceIn);
        ClientConfig.STT_API_KEY.set(sttKey.trim());
        ClientConfig.TTS_PROVIDER.set(ttsProvider);
        ClientConfig.TTS_API_KEY.set(ttsKey.trim());
        ClientConfig.TTS_VOICE.set(ttsVoice.trim());
        ClientConfig.TTS_BASE_URL.set(ttsBaseUrl.trim());
        ClientConfig.TTS_MODEL.set(ttsModel.trim());
        ClientConfig.GLITCH_EFFECTS.set(glitchComfort);
        ClientConfig.FAKE_CRASH_SCREENS.set(crashComfort);
        ClientConfig.SPEC.save();
    }

    // ---------- Layout ----------

    private int left() {
        return width / 2 - panelWidth() / 2;
    }

    private int panelWidth() {
        return Math.min(340, width - 20);
    }

    private int labelWidth() {
        return Math.min(120, panelWidth() / 3);
    }

    private int controlX() {
        return left() + labelWidth() + 4;
    }

    private int controlWidth() {
        return panelWidth() - labelWidth() - 4;
    }

    private int rowY(int row) {
        return 44 + row * 23;
    }

    private final List<Label> labels = new ArrayList<>();

    private record Label(Component text, int x, int y, int color) {
    }

    private boolean reloadOnReturn;

    @Override
    protected void init() {
        if (reloadOnReturn) {
            // Coming back from the Advanced screen: pick up anything changed there.
            reloadOnReturn = false;
            load();
        }
        labels.clear();
        int x = left();
        int w = panelWidth();
        // Page tabs
        Page[] pages = Page.values();
        String[] names = {"AI Brain", "Voice", "Story & Horror", "Abilities & Chat"};
        int tabW = (w - 3 * 3) / 4;
        for (int i = 0; i < pages.length; i++) {
            Page p = pages[i];
            Button tab = Button.builder(Component.literal(names[i]), b -> {
                page = p;
                rebuildWidgets();
            }).bounds(x + i * (tabW + 3), 18, tabW, 20).build();
            tab.active = page != p;
            addRenderableWidget(tab);
        }

        switch (page) {
            case AI -> initAiPage();
            case VOICE -> initVoicePage();
            case STORY -> initStoryPage();
            case ABILITIES -> initAbilitiesPage();
        }

        // Bottom buttons
        int by = height - 26;
        int bw = (w - 6) / 3;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            save();
            onClose();
        }).bounds(x, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + bw + 3, by, bw, 20).build());
        Button advanced = Button.builder(Component.literal("Advanced..."), b -> {
            save();
            reloadOnReturn = true;
            if (minecraft != null && container != null) minecraft.setScreen(new ConfigurationScreen(container, this));
        }).bounds(x + 2 * (bw + 3), by, bw, 20)
                .tooltip(Tooltip.create(Component.literal("Every setting, including cost limits, memory and chat range."))).build();
        advanced.active = container != null;
        addRenderableWidget(advanced);
    }

    private void label(int row, String text) {
        labels.add(new Label(Component.literal(text), left(), rowY(row) + 6, 0xFFFFFFFF));
    }

    /** Adds a (possibly multi-line) note at y and returns the y just below it. */
    private int stack(int y, Component text, int color) {
        labels.add(new Label(text, left(), y, color));
        return y + Math.max(1, font.split(text, panelWidth()).size()) * 10 + 3;
    }

    private void note(int y, Component text) {
        labels.add(new Label(text, left(), y, 0xFFAAAAAA));
    }

    private void initAiPage() {
        // Row 0: which brain you're editing. Brain 1 is used first; the others take over before it runs out.
        label(0, "Brain");
        int tabW = (controlWidth() - (BRAINS - 1) * 3) / BRAINS;
        for (int i = 0; i < BRAINS; i++) {
            int index = i;
            boolean used = !providers[i].equals("none");
            String text = used ? String.valueOf(i + 1) : "+";
            Button tab = Button.builder(Component.literal(text).withStyle(used ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY), b -> {
                brain = index;
                status = Component.empty();
                rebuildWidgets();
            }).bounds(controlX() + i * (tabW + 3), rowY(0), tabW, 20)
                    .tooltip(Tooltip.create(Component.literal("Brain " + (i + 1) + ": " + (used ? providerName(providers[i]).getString() : "not used")
                            + liveStatus(i)))).build();
            tab.active = brain != i;
            addRenderableWidget(tab);
        }

        String provider = providers[brain];
        List<String> names = new ArrayList<>(PROVIDER_NOTES.keySet());
        if (brain > 0) names.add(0, "none");
        label(1, "Provider");
        addRenderableWidget(CycleButton.<String>builder(SolaceSetupScreen::providerName)
                .withValues(names).withInitialValue(names.contains(provider) ? provider : names.get(0)).displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.literal(v.equals("none") ? "Leave this brain empty." : PROVIDER_NOTES.get(v))))
                .create(controlX(), rowY(1), controlWidth(), 20, Component.literal("Provider"), (btn, v) -> {
                    // A model name or server address from one provider means nothing to another.
                    if (!v.equals(providers[brain])) {
                        models[brain] = "";
                        baseUrls[brain] = "";
                    }
                    providers[brain] = v;
                    status = Component.empty();
                    rebuildWidgets();
                }));

        ProviderPreset preset = ProviderPreset.get(provider);
        boolean empty = preset == null; // "none": nothing to fill in
        boolean needsKey = preset != null && preset.needsKey();
        int row = 2;
        if (!empty) {
            label(row, needsKey ? "API key" : "API key (optional)");
            int keyW = controlWidth() - 2 * 47;
            EditBox key = secretBox(controlX(), rowY(row), keyW, apiKeys[brain], v -> apiKeys[brain] = v);
            key.setHint(Component.literal(needsKey ? "paste your key here" : "not needed").withStyle(ChatFormatting.DARK_GRAY));
            addRenderableWidget(key);
            addRenderableWidget(Button.builder(Component.literal(showKey ? "Hide" : "Show"), b -> {
                showKey = !showKey;
                rebuildWidgets();
            }).bounds(controlX() + keyW + 3, rowY(row), 44, 20).build());
            Button getKey = Button.builder(Component.literal("Get key"), b -> {
                if (!preset.keyUrl().isEmpty()) ConfirmLinkScreen.confirmLinkNow(this, preset.keyUrl());
            }).bounds(controlX() + keyW + 50, rowY(row), 44, 20)
                    .tooltip(Tooltip.create(Component.literal("Opens the website where you get a key"))).build();
            getKey.active = !preset.keyUrl().isEmpty();
            addRenderableWidget(getKey);
            row++;

            label(row, "Model");
            EditBox modelBox = textBox(controlX(), rowY(row), controlWidth(), models[brain], v -> models[brain] = v);
            String def = preset.defaultModel();
            modelBox.setHint(Component.literal(def.isEmpty() ? "model name" : "default: " + def).withStyle(ChatFormatting.DARK_GRAY));
            addRenderableWidget(modelBox);
            row++;

            // Only custom servers and programs on your PC need an address; the big providers fill it in automatically.
            boolean showUrl = preset.baseUrl().isEmpty() || !preset.needsKey() || !baseUrls[brain].isBlank();
            if (showUrl) {
                label(row, "Server URL");
                EditBox urlBox = textBox(controlX(), rowY(row), controlWidth(), baseUrls[brain], v -> baseUrls[brain] = v);
                String defUrl = preset.baseUrl();
                urlBox.setHint(Component.literal(defUrl.isEmpty() ? "https://..." : "default: " + defUrl).withStyle(ChatFormatting.DARK_GRAY));
                addRenderableWidget(urlBox);
                row++;
            }
        }

        // One row: the daily limit (for all brains together), then Test and Move up for this brain.
        label(row, "Daily limit");
        int limitW = controlWidth() / 2;
        int smallW = (controlWidth() - limitW - 6) / 2;
        addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(v == 0 ? "Unlimited!" : v + " a day"))
                .withValues(DAILY_LIMITS).withInitialValue(DAILY_LIMITS.contains(dailyLimit) ? dailyLimit : 400).displayOnlyValue()
                .withTooltip(v -> Tooltip.create(Component.literal("Most replies per day, for all brains together. Protects you from surprise bills. "
                        + "After the limit, Solace goes quiet until tomorrow.")))
                .create(controlX(), rowY(row), limitW, 20, Component.literal("Daily limit"), (btn, v) -> dailyLimit = v));
        if (!empty) {
            Button test = Button.builder(Component.literal(testing ? "..." : "Test"), b -> runTest())
                    .bounds(controlX() + limitW + 3, rowY(row), smallW, 20)
                    .tooltip(Tooltip.create(Component.literal("Sends one tiny message to brain " + (brain + 1) + " to check it works."))).build();
            test.active = !testing;
            addRenderableWidget(test);
        }
        if (brain > 0) {
            // Brain 1 is the one Solace prefers, so let the player reorder.
            addRenderableWidget(Button.builder(Component.literal("Move up"), b -> {
                swap(brain, brain - 1);
                brain--;
                status = Component.empty();
                rebuildWidgets();
            }).bounds(controlX() + limitW + smallW + 6, rowY(row), smallW, 20)
                    .tooltip(Tooltip.create(Component.literal("Solace prefers lower numbers. Put your best (or paid) brain first."))).build());
        }
        row++;

        // Stack the notes so long (wrapped) messages never overlap the next one. Only what's useful right now.
        int y = rowY(row) + 2;
        String live = liveStatus(brain);
        if (!status.getString().isEmpty()) y = stack(y, status, 0xFFFFFFFF);
        else if (!live.isEmpty()) y = stack(y, Component.literal("Right now" + live), 0xFFAAAAAA);
        else if (!empty) y = stack(y, Component.literal(PROVIDER_NOTES.getOrDefault(provider, "")), 0xFFAAAAAA);
        int used = 0;
        for (String p : providers) if (!p.equals("none")) used++;
        if (used < 2) {
            y = stack(y, Component.literal("Tip: add a second brain (+) from a different provider. Solace switches before one runs out, so free limits last longer.")
                    .withStyle(ChatFormatting.DARK_AQUA), 0xFFAAAAAA);
        }
        if (needsKey && apiKeys[brain].isBlank()) {
            y = stack(y, Component.literal("Keys are like passwords: never share them or show them on stream.").withStyle(ChatFormatting.GOLD), 0xFFAAAAAA);
        }
        if (isRemoteServer()) {
            stack(y, Component.literal("You're on a server: the server owner sets Solace's AI there. This only changes your own worlds.")
                    .withStyle(ChatFormatting.YELLOW), 0xFFAAAAAA);
        }
    }

    private void swap(int a, int b) {
        for (String[] arr : List.of(providers, apiKeys, models, baseUrls)) {
            String t = arr[a];
            arr[a] = arr[b];
            arr[b] = t;
        }
    }

    /** What the running game knows about this brain (singleplayer only), like ": ready, 120 requests left today". */
    private String liveStatus(int index) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.hasSingleplayerServer() || providers[index].equals("none")) return "";
        try {
            String s = com.solacemod.solace.story.AiService.brainStatus(index + 1);
            if (s == null) return "";
            int colon = s.indexOf(": ");
            return colon >= 0 ? ":" + s.substring(colon + 1) : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void initVoicePage() {
        label(0, "Talk with mic");
        addRenderableWidget(CycleButton.onOffBuilder(voiceIn)
                .withTooltip(v -> Tooltip.create(Component.literal("Hold G (change it in Options > Controls) while speaking. Uses Groq's speech-to-text by default.")))
                .create(controlX(), rowY(0), controlWidth(), 20, Component.literal("Talk with mic"), (b, v) -> voiceIn = v));
        label(1, "Mic API key");
        EditBox stt = secretBox(controlX(), rowY(1), controlWidth(), sttKey, v -> sttKey = v);
        stt.setHint(Component.literal("blank = reuse your Groq/OpenAI AI key").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(stt);

        label(2, "Solace's voice");
        addRenderableWidget(CycleButton.<String>builder(v -> Component.literal(switch (v) {
                    case "openai_compatible" -> "OpenAI / local server";
                    case "elevenlabs" -> "ElevenLabs";
                    default -> "Off";
                })).withValues(List.of("none", "openai_compatible", "elevenlabs")).withInitialValue(ttsProvider).displayOnlyValue()
                .create(controlX(), rowY(2), controlWidth(), 20, Component.literal("Voice"), (b, v) -> {
                    ttsProvider = v;
                    if (v.equals("elevenlabs") && ttsModel.startsWith("gpt")) ttsModel = "eleven_flash_v2_5";
                    if (v.equals("openai_compatible") && ttsModel.startsWith("eleven")) ttsModel = "gpt-4o-mini-tts";
                    rebuildWidgets();
                }));
        if (!ttsProvider.equals("none")) {
            label(3, "Voice API key");
            EditBox tts = secretBox(controlX(), rowY(3), controlWidth(), ttsKey, v -> ttsKey = v);
            tts.setHint(Component.literal("blank for local servers").withStyle(ChatFormatting.DARK_GRAY));
            addRenderableWidget(tts);
            label(4, ttsProvider.equals("elevenlabs") ? "Voice ID" : "Voice name");
            addRenderableWidget(textBox(controlX(), rowY(4), controlWidth(), ttsVoice, v -> ttsVoice = v));
            if (ttsProvider.equals("openai_compatible")) {
                label(5, "Voice server");
                EditBox url = textBox(controlX(), rowY(5), controlWidth(), ttsBaseUrl, v -> ttsBaseUrl = v);
                url.setTooltip(Tooltip.create(Component.literal("OpenAI: https://api.openai.com/v1   Free local Kokoro: http://localhost:8880/v1")));
                addRenderableWidget(url);
            }
        }
        note(rowY(6) + 2, Component.literal("Voice is optional. Paid voice services charge per line Solace speaks."));
    }

    private void initStoryPage() {
        label(0, "Story");
        addRenderableWidget(CycleButton.onOffBuilder(storyEnabled)
                .withTooltip(v -> Tooltip.create(Component.literal("Off = Solace is just a friendly companion. No horror.")))
                .create(controlX(), rowY(0), controlWidth(), 20, Component.literal("Story"), (b, v) -> storyEnabled = v));
        label(1, "Story speed");
        addRenderableWidget(CycleButton.<Double>builder(v -> Component.literal(v == 1.0 ? "Normal (about 3 hours)" : v + "x"))
                .withValues(SPEEDS).withInitialValue(nearest(SPEEDS, storySpeed)).displayOnlyValue()
                .create(controlX(), rowY(1), controlWidth(), 20, Component.literal("Story speed"), (b, v) -> storySpeed = v));
        label(2, "Scares");
        addRenderableWidget(CycleButton.<Double>builder(v -> Component.literal(v == 0 ? "Off" : v == 0.5 ? "Rare" : v == 1 ? "Normal" : v == 2 ? "Often" : "Constant"))
                .withValues(INTENSITIES).withInitialValue(nearest(INTENSITIES, horror)).displayOnlyValue()
                .create(controlX(), rowY(2), controlWidth(), 20, Component.literal("Scares"), (b, v) -> horror = v));
        int half = (controlWidth() - 3) / 2;
        label(3, "World tricks");
        addRenderableWidget(toggle(controlX(), rowY(3), half, "Torches/doors/signs", worldChanges, v -> worldChanges = v,
                "Torches go out, doors open, signs appear. Never breaks your builds."));
        addRenderableWidget(toggle(controlX() + half + 3, rowY(3), half, "Fake messages", fakeMessages, v -> fakeMessages = v,
                "Fake chat messages and fake 'player joined' messages."));
        label(4, "Screen tricks");
        addRenderableWidget(toggle(controlX(), rowY(4), half, "Glitches", screenEffects && glitchComfort, v -> {
            screenEffects = v;
            glitchComfort = v;
        }, "Soft screen static. No bright strobing."));
        addRenderableWidget(toggle(controlX() + half + 3, rowY(4), half, "Fake crash", fakeCrash && crashComfort, v -> {
            fakeCrash = v;
            crashComfort = v;
        }, "A harmless fake crash screen. Closes by itself or with Esc."));
        note(rowY(5) + 2, Component.literal("Story spoilers are in docs/STORY.md. Don't peek!"));
    }

    private void initAbilitiesPage() {
        int half = (controlWidth() - 3) / 2;
        label(0, "Solace can...");
        addRenderableWidget(toggle(controlX(), rowY(0), half, "Mine", mining, v -> mining = v, "Mine blocks when you ask."));
        addRenderableWidget(toggle(controlX() + half + 3, rowY(0), half, "Build", building, v -> building = v, "Build shelters, walls, bridges, place torches."));
        addRenderableWidget(toggle(controlX(), rowY(1), half, "Fight", combat, v -> combat = v, "Fight mobs and protect you."));
        addRenderableWidget(toggle(controlX() + half + 3, rowY(1), half, "Craft", crafting, v -> crafting = v, "Craft items from what's in its pockets."));
        label(2, "Powers");
        addRenderableWidget(toggle(controlX(), rowY(2), half, "Powers", powers, v -> powers = v,
                "Heal, bless, weather, time, smite and banish monsters, reveal, find anything, carry you home."));
        addRenderableWidget(toggle(controlX() + half + 3, rowY(2), half, "Conjure items", conjuring, v -> conjuring = v,
                "Create items out of nothing. Precious items only when Solace trusts you."));
        label(3, "Hears you when");
        addRenderableWidget(CycleButton.<String>builder(v -> Component.literal(switch (v) {
                    case "prefix" -> "Message starts with \"Solace\"";
                    case "always" -> "Every chat message";
                    default -> "It's nearby or you say its name";
                })).withValues(List.of("nearby", "prefix", "always")).withInitialValue(listenMode).displayOnlyValue()
                .create(controlX(), rowY(3), controlWidth(), 20, Component.literal("Hears you"), (b, v) -> listenMode = v));
        label(4, "Talks on its own");
        addRenderableWidget(CycleButton.onOffBuilder(ambient)
                .withTooltip(v -> Tooltip.create(Component.literal("Comments on night falling, low health, caves... Uses AI requests.")))
                .create(controlX(), rowY(4), controlWidth(), 20, Component.literal("Talks on its own"), (b, v) -> ambient = v));
        label(5, "Remembers you");
        addRenderableWidget(CycleButton.<Boolean>builder(v -> Component.literal(v ? "Across all worlds" : "Only in this world"))
                .withValues(List.of(true, false)).withInitialValue(crossWorld).displayOnlyValue()
                .create(controlX(), rowY(5), controlWidth(), 20, Component.literal("Memory"), (b, v) -> crossWorld = v));
        note(rowY(6) + 2, Component.literal("Give Solace items by right-clicking it while holding them."));
    }

    // ---------- Widgets ----------

    private EditBox textBox(int x, int y, int w, String value, Consumer<String> onChange) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.empty());
        box.setMaxLength(512);
        box.setValue(value);
        box.setResponder(onChange);
        return box;
    }

    /** A text box that shows dots instead of the text unless "Show" is on. */
    private EditBox secretBox(int x, int y, int w, String value, Consumer<String> onChange) {
        EditBox box = textBox(x, y, w, value, onChange);
        if (!showKey) {
            box.setFormatter((text, start) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        }
        return box;
    }

    private CycleButton<Boolean> toggle(int x, int y, int w, String name, boolean value, Consumer<Boolean> onChange, String tooltip) {
        return CycleButton.onOffBuilder(value).withTooltip(v -> Tooltip.create(Component.literal(tooltip)))
                .create(x, y, w, 20, Component.literal(name), (b, v) -> onChange.accept(v));
    }

    private static Double nearest(List<Double> options, double value) {
        Double best = options.get(0);
        for (Double o : options) if (Math.abs(o - value) < Math.abs(best - value)) best = o;
        return best;
    }

    private static Component providerName(String id) {
        return Component.literal(switch (id) {
            case "groq" -> "Groq";
            case "gemini" -> "Google Gemini";
            case "ollama" -> "Ollama (on your PC)";
            case "lmstudio" -> "LM Studio (on your PC)";
            case "openrouter" -> "OpenRouter";
            case "openai" -> "OpenAI";
            case "anthropic" -> "Anthropic (Claude)";
            case "deepseek" -> "DeepSeek";
            case "mistral" -> "Mistral";
            case "xai" -> "xAI (Grok)";
            case "together" -> "Together AI";
            case "cerebras" -> "Cerebras";
            case "custom" -> "Custom (OpenAI-compatible)";
            case "none" -> "Not used";
            default -> id;
        });
    }

    private static boolean isRemoteServer() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && !mc.hasSingleplayerServer();
    }

    // ---------- Test button ----------

    private void runTest() {
        int index = brain;
        String provider = providers[index];
        ProviderSettings settings = ProviderSettings.resolve(provider, apiKeys[index], models[index], baseUrls[index], 20, SolaceConfig.ANTHROPIC_EFFORT.get());
        if (settings == null) {
            status = Component.literal("Unknown provider.").withStyle(ChatFormatting.RED);
            rebuildWidgets();
            return;
        }
        String problem = settings.problem();
        if (problem != null) {
            status = Component.literal(problem.contains("No API key") ? "Paste your API key first (use the Get key button)." : problem)
                    .withStyle(ChatFormatting.RED);
            rebuildWidgets();
            return;
        }
        testing = true;
        status = Component.literal("Asking " + providerName(provider).getString() + "...").withStyle(ChatFormatting.GRAY);
        rebuildWidgets();
        LlmRequest req = new LlmRequest("Reply with exactly: {\"say\":\"I can hear you.\"}", List.of(ChatMessage.user("Test")), 200, 0.0, true);
        long start = System.currentTimeMillis();
        LlmProvider llm = LlmProvider.create(settings);
        llm.complete(req).whenComplete((text, err) -> Minecraft.getInstance().execute(() -> {
            testing = false;
            if (err != null) {
                Throwable cause = Http.unwrap(err);
                String hint = cause instanceof LlmException e ? e.hint() : cause.getMessage();
                status = Component.literal("Failed: " + hint).withStyle(ChatFormatting.RED);
            } else {
                String used = llm.activeModel();
                String note = used.equals(settings.model()) ? "" : " (the default, " + settings.model() + ", is retired)";
                status = Component.literal("It works! Model: " + used + note + " (" + (System.currentTimeMillis() - start) + " ms). Press Done to save.")
                        .withStyle(ChatFormatting.GREEN);
            }
            if (minecraft != null && minecraft.screen == this) rebuildWidgets();
        }));
    }

    // ---------- Drawing ----------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 6, 0xFFFFFFFF);
        for (Label l : labels) {
            // Wrap long notes so they fit the panel.
            List<FormattedCharSequence> lines = font.split(l.text(), panelWidth());
            int y = l.y();
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, l.x(), y, l.color());
                y += 10;
            }
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
