package com.solacemod.solace.client;

import com.solacemod.solace.config.ClientConfig;
import com.solacemod.solace.network.ModNetwork;
import net.minecraft.client.Minecraft;

/** Handles messages from the server. Only ever loaded on the player's computer. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void onSpeech(ModNetwork.SpeechPayload payload) {
        VoiceManager.speak(payload.entityId(), payload.text(), payload.emotion());
    }

    public static void onGlitch(ModNetwork.GlitchPayload payload) {
        if (!ClientConfig.GLITCH_EFFECTS.get()) return;
        GlitchOverlay.start(payload.kind(), payload.durationTicks(), payload.message());
    }

    public static void onBond(ModNetwork.BondPayload payload) {
        BondHint.update(payload);
    }

    public static void onOpenSetup(ModNetwork.OpenSetupPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        var container = net.neoforged.fml.ModList.get().getModContainerById(com.solacemod.solace.Solace.MODID).orElse(null);
        mc.setScreen(new SolaceSetupScreen(mc.screen, container));
    }

    public static void onFakeCrash(ModNetwork.FakeCrashPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.FAKE_CRASH_SCREENS.get()) {
            GlitchOverlay.start(0, 30, "");
            return;
        }
        mc.setScreen(new FakeCrashScreen(mc.screen, payload.line()));
    }
}
