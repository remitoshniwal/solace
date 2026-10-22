package com.solacemod.solace.client;

import com.solacemod.solace.Solace;
import com.solacemod.solace.config.ClientConfig;
import com.solacemod.solace.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Your own AI on someone else's server. Your key only leaves this computer when ALL of these are true:
 * the server allows players' own AI, you turned it on in setup, you said you trust this server, and the
 * connection is encrypted (online mode). Otherwise nothing is sent.
 */
@EventBusSubscriber(modid = Solace.MODID, value = Dist.CLIENT)
public final class OwnAi {
    /** What the server we're on allows, or null if it hasn't said (not on a server, or an older Solace). */
    @Nullable
    private static ModNetwork.ServerAiPayload server;
    /** True once we've sent brains to this server, so turning it off can tell the server to forget them. */
    private static boolean sent;
    private static boolean warnedUnencrypted;

    private OwnAi() {
    }

    public static void onServerAi(ModNetwork.ServerAiPayload payload) {
        server = payload;
        send();
    }

    @SubscribeEvent
    public static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        server = null;
        sent = false;
        warnedUnencrypted = false;
    }

    /** What the server allows, or null if it hasn't said. */
    @Nullable
    public static ModNetwork.ServerAiPayload serverInfo() {
        return server;
    }

    /** The address of the server we're on (how it's remembered as trusted), or null in single player. */
    @Nullable
    public static String serverAddress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() || mc.getCurrentServer() == null) return null;
        return mc.getCurrentServer().ip.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean trusted() {
        String address = serverAddress();
        return address != null && ClientConfig.TRUSTED_SERVERS.get().contains(address);
    }

    /** Remembers that the player trusts this server with their key. */
    public static void trust() {
        String address = serverAddress();
        if (address == null || trusted()) return;
        List<String> list = new ArrayList<>(ClientConfig.TRUSTED_SERVERS.get());
        list.add(address);
        ClientConfig.TRUSTED_SERVERS.set(list);
        ClientConfig.SPEC.save();
    }

    /**
     * True if the connection to the server is encrypted, so nobody in between can read the key.
     * Servers with online mode off aren't. (The two test players in build.gradle set solace.allowUnencryptedKeys,
     * because the local test server runs with online mode off. Nobody else should.)
     */
    public static boolean encrypted() {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener == null) return false;
        return listener.getConnection().isEncrypted() || Boolean.getBoolean("solace.allowUnencryptedKeys");
    }

    /** The brains the player filled in (a provider and a key). */
    public static List<ModNetwork.OwnBrain> brains() {
        List<ModNetwork.OwnBrain> list = new ArrayList<>();
        for (int i = 0; i < ClientConfig.OWN_BRAINS; i++) {
            String provider = ClientConfig.OWN_PROVIDER.get(i).get();
            String key = ClientConfig.OWN_API_KEY.get(i).get().trim();
            if (provider.equals("none") || key.isEmpty()) continue;
            list.add(new ModNetwork.OwnBrain(provider, key, ClientConfig.OWN_MODEL.get(i).get().trim()));
        }
        return list;
    }

    /** True if the player wants their own AI here, but hasn't said they trust this server yet (setup asks then). */
    public static boolean needsTrust() {
        return server != null && server.playerAi() && ClientConfig.OWN_AI.get() && !brains().isEmpty() && !trusted();
    }

    /**
     * Sends the player's brains to the server if everything allows it, or tells it to forget them if they turned it off.
     * Returns why nothing was sent (for the setup screen), or null if it was sent.
     */
    @Nullable
    public static String send() {
        if (serverAddress() == null) return null;
        if (server == null) return "This server didn't say whether it takes players' own AI.";
        if (!server.playerAi()) return "This server doesn't take players' own AI.";
        List<ModNetwork.OwnBrain> brains = brains();
        if (!ClientConfig.OWN_AI.get() || brains.isEmpty()) {
            if (sent) {
                // Tell the server to forget them (no keys in this message).
                PacketDistributor.sendToServer(new ModNetwork.OwnAiPayload(List.of()));
                sent = false;
            }
            return ClientConfig.OWN_AI.get() ? "Add a brain first." : null;
        }
        if (!trusted()) return "Not sent: you haven't said you trust this server.";
        if (!encrypted()) {
            if (!warnedUnencrypted && Minecraft.getInstance().player != null) {
                warnedUnencrypted = true;
                Minecraft.getInstance().player.displayClientMessage(Component.literal(
                        "[Solace] Your own AI key was NOT sent: this server's connection isn't encrypted (online mode is off).")
                        .withStyle(ChatFormatting.GOLD), false);
            }
            return "Not sent: this server's connection isn't encrypted.";
        }
        PacketDistributor.sendToServer(new ModNetwork.OwnAiPayload(brains));
        sent = true;
        return null;
    }
}
