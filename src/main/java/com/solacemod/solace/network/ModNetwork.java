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

    /**
     * Server -> client: the bond, for the little flame next to the hotbar (client/BondHint).
     * tetherPercent = Solace's strength compared with this chapter's maximum (0 to 100). debtBand: 0 = under 30, 1 = 30+, 2 = 60+, 3 = 100+.
     */
    public record BondPayload(int tetherPercent, int debtBand, boolean request, boolean show) implements CustomPacketPayload {
        public static final Type<BondPayload> TYPE = new Type<>(Solace.id("bond"));
        public static final StreamCodec<ByteBuf, BondPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BondPayload::tetherPercent,
                ByteBufCodecs.VAR_INT, BondPayload::debtBand,
                ByteBufCodecs.BOOL, BondPayload::request,
                ByteBufCodecs.BOOL, BondPayload::show,
                BondPayload::new);

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

    /**
     * Server -> client: does this server take players' own AI? Sent when they join and when they open setup.
     * Their game never sends a key unless playerAi is true (and they said they trust this server).
     * @param lendAi "always", "no_backup" or "never": who may use the owner's brains
     * @param ownerHasAi the owner has at least one working brain
     */
    public record ServerAiPayload(boolean playerAi, String lendAi, boolean ownerHasAi) implements CustomPacketPayload {
        public static final Type<ServerAiPayload> TYPE = new Type<>(Solace.id("server_ai"));
        public static final StreamCodec<ByteBuf, ServerAiPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ServerAiPayload::playerAi,
                ByteBufCodecs.STRING_UTF8, ServerAiPayload::lendAi,
                ByteBufCodecs.BOOL, ServerAiPayload::ownerHasAi,
                ServerAiPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One of a player's own brains: provider name, key and model (blank = the provider's default). */
    public record OwnBrain(String provider, String key, String model) {
        public static final StreamCodec<ByteBuf, OwnBrain> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), OwnBrain::provider,
                ByteBufCodecs.stringUtf8(300), OwnBrain::key,
                ByteBufCodecs.stringUtf8(100), OwnBrain::model,
                OwnBrain::new);

        @Override
        public String toString() {
            return provider + " (" + model + ")"; // never the key
        }
    }

    /** Client -> server: the player's own brains for this server (empty = stop using them). At most 5. */
    public record OwnAiPayload(java.util.List<OwnBrain> brains) implements CustomPacketPayload {
        public static final Type<OwnAiPayload> TYPE = new Type<>(Solace.id("own_ai"));
        public static final StreamCodec<ByteBuf, OwnAiPayload> CODEC = OwnBrain.CODEC.apply(ByteBufCodecs.list(5))
                .map(OwnAiPayload::new, OwnAiPayload::brains);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        @Override
        public String toString() {
            return "OwnAiPayload(" + brains.size() + " brains)"; // never the keys
        }
    }

    /** Tells this player's game what the server allows (see ServerAiPayload). */
    public static void sendServerAi(ServerPlayer player) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ServerAiPayload(
                com.solacemod.solace.config.SolaceConfig.PLAYER_AI.get(), com.solacemod.solace.config.SolaceConfig.LEND_AI.get(),
                com.solacemod.solace.story.AiService.configured()));
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
        registrar.playToClient(BondPayload.TYPE, BondPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> com.solacemod.solace.client.ClientHooks.onBond(payload)));
        registrar.playToClient(ServerAiPayload.TYPE, ServerAiPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> com.solacemod.solace.client.OwnAi.onServerAi(payload)));
        registrar.playToServer(OwnAiPayload.TYPE, OwnAiPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) com.solacemod.solace.story.PlayerAi.onOwnBrains(player, payload.brains());
                }));
        registrar.playToServer(VoiceTextPayload.TYPE, VoiceTextPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        String text = payload.text().length() > 500 ? payload.text().substring(0, 500) : payload.text();
                        SolaceBrain.onPlayerVoice(player, text);
                    }
                }));
    }
}
