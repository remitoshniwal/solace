package com.solacemod.solace.network;

import com.solacemod.solace.Solace;
import com.solacemod.solace.story.SolaceBrain;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Messages sent between the server (where the AI and story run) and each player's game (sound, voice, screen effects).
 * Client-side handlers live in the client package and are only touched inside lambdas, so dedicated servers never load them.
 */
public final class ModNetwork {
    private ModNetwork() {
    }

    /** Server -> client: Solace said something (used for text-to-speech). */
    public record SpeechPayload(int entityId, String text, String emotion) implements CustomPacketPayload {
        public static final Type<SpeechPayload> TYPE = new Type<>(Solace.id("speech"));
        public static final StreamCodec<ByteBuf, SpeechPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SpeechPayload::entityId,
                ByteBufCodecs.STRING_UTF8, SpeechPayload::text,
                ByteBufCodecs.STRING_UTF8, SpeechPayload::emotion,
                SpeechPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: show a screen glitch. kind: 0 static, 1 red pulse, 2 flash message. */
    public record GlitchPayload(int kind, int durationTicks, String message) implements CustomPacketPayload {
        public static final Type<GlitchPayload> TYPE = new Type<>(Solace.id("glitch"));
        public static final StreamCodec<ByteBuf, GlitchPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, GlitchPayload::kind,
                ByteBufCodecs.VAR_INT, GlitchPayload::durationTicks,
                ByteBufCodecs.STRING_UTF8, GlitchPayload::message,
                GlitchPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: show the fake crash screen. */
    public record FakeCrashPayload(String line) implements CustomPacketPayload {
        public static final Type<FakeCrashPayload> TYPE = new Type<>(Solace.id("fake_crash"));
        public static final StreamCodec<ByteBuf, FakeCrashPayload> CODEC = ByteBufCodecs.STRING_UTF8.map(FakeCrashPayload::new, FakeCrashPayload::line);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: open the Solace setup screen (from the clickable chat message or /solace setup). */
    public record OpenSetupPayload(boolean firstTime) implements CustomPacketPayload {
        public static final Type<OpenSetupPayload> TYPE = new Type<>(Solace.id("open_setup"));
        public static final StreamCodec<ByteBuf, OpenSetupPayload> CODEC = ByteBufCodecs.BOOL.map(OpenSetupPayload::new, OpenSetupPayload::firstTime);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: what the player said out loud (already turned into text on their computer). */
    public record VoiceTextPayload(String text) implements CustomPacketPayload {
        public static final Type<VoiceTextPayload> TYPE = new Type<>(Solace.id("voice_text"));
        public static final StreamCodec<ByteBuf, VoiceTextPayload> CODEC = ByteBufCodecs.STRING_UTF8.map(VoiceTextPayload::new, VoiceTextPayload::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SpeechPayload.TYPE, SpeechPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> com.solacemod.solace.client.ClientHooks.onSpeech(payload)));
        registrar.playToClient(GlitchPayload.TYPE, GlitchPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> com.solacemod.solace.client.ClientHooks.onGlitch(payload)));
        registrar.playToClient(FakeCrashPayload.TYPE, FakeCrashPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> com.solacemod.solace.client.ClientHooks.onFakeCrash(payload)));
        registrar.playToClient(OpenSetupPayload.TYPE, OpenSetupPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> com.solacemod.solace.client.ClientHooks.onOpenSetup(payload)));
        registrar.playToServer(VoiceTextPayload.TYPE, VoiceTextPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        String text = payload.text().length() > 500 ? payload.text().substring(0, 500) : payload.text();
                        SolaceBrain.onPlayerVoice(player, text);
                    }
                }));
    }
}
