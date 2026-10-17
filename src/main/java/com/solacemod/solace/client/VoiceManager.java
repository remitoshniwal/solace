package com.solacemod.solace.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solacemod.solace.ai.Http;
import com.solacemod.solace.ai.KeyResolver;
import com.solacemod.solace.config.ClientConfig;
import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Voice chat with Solace, all on the player's own computer:
 *  - Hold the talk key: your microphone is recorded, sent to a speech-to-text service, and the text goes to Solace.
 *  - When Solace speaks, its words go to a text-to-speech service and the audio plays (quieter when it's far away).
 * Uses Java's built-in audio. Nothing is recorded unless you hold the key.
 */
public final class VoiceManager {
    private static final int MIC_RATE = 16000;
    private static final int MAX_SECONDS = 30;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Solace-Voice");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService PLAYER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Solace-Speaker");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean RECORDING = new AtomicBoolean(false);
    private static final AtomicBoolean STOP_PLAYBACK = new AtomicBoolean(false);
    private static volatile TargetDataLine micLine;
    private static ByteArrayOutputStream recorded;
    private static boolean wasDown;
    private static long lastWarning;

    private VoiceManager() {
    }

    // ---------- Push to talk ----------

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean down = ClientConfig.VOICE_INPUT.get() && mc.screen == null && ClientModEvents.TALK_KEY.isDown();
        if (down && !wasDown) startRecording();
        else if (!down && wasDown) stopRecording();
        wasDown = down;
        if (RECORDING.get()) mc.gui.setOverlayMessage(Component.literal("Solace is listening...").withStyle(ChatFormatting.AQUA), false);
    }

    private static void startRecording() {
        if (!RECORDING.compareAndSet(false, true)) return;
        recorded = new ByteArrayOutputStream();
        final ByteArrayOutputStream target = recorded;
        Thread t = new Thread(() -> {
            AudioFormat format = new AudioFormat(MIC_RATE, 16, 1, true, false);
            try {
                TargetDataLine line = AudioSystem.getTargetDataLine(format);
                line.open(format);
                line.start();
                micLine = line;
                byte[] buf = new byte[3200];
                long maxBytes = (long) MIC_RATE * 2 * MAX_SECONDS;
                while (RECORDING.get() && target.size() < maxBytes) {
                    int n = line.read(buf, 0, buf.length);
                    if (n > 0) target.write(buf, 0, n);
                }
                line.stop();
                line.close();
            } catch (Exception e) {
                RECORDING.set(false);
                warn("Microphone error: " + e.getMessage() + ". Check that Minecraft is allowed to use your microphone.");
            } finally {
                micLine = null;
            }
        }, "Solace-Mic");
        t.setDaemon(true);
        t.start();
    }

    private static void stopRecording() {
        if (!RECORDING.compareAndSet(true, false)) return;
        final ByteArrayOutputStream data = recorded;
        // Give the mic thread a moment to flush the last bit of audio.
        WORKER.execute(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] pcm = data.toByteArray();
            if (pcm.length < MIC_RATE / 2) return; // under a quarter second: ignore
            try {
                String text = transcribe(wav(pcm, MIC_RATE));
                if (text == null || text.isBlank()) return;
                String clean = text.trim();
                Minecraft.getInstance().execute(() -> PacketDistributor.sendToServer(new ModNetwork.VoiceTextPayload(clean)));
            } catch (Exception e) {
                warn("Speech-to-text failed: " + e.getMessage());
            }
        });
    }

    private static String transcribe(byte[] wavBytes) throws Exception {
        String key = KeyResolver.resolve(ClientConfig.STT_API_KEY.get());
        if (key.isEmpty()) key = sharedKeyFor(ClientConfig.STT_BASE_URL.get());
        String boundary = "----Solace" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        field(body, boundary, "model", ClientConfig.STT_MODEL.get());
        field(body, boundary, "response_format", "json");
        if (!ClientConfig.STT_LANGUAGE.get().isBlank()) field(body, boundary, "language", ClientConfig.STT_LANGUAGE.get().trim());
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(wavBytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        String url = stripSlash(ClientConfig.STT_BASE_URL.get()) + "/audio/transcriptions";
        Map<String, String> headers = key.isEmpty() ? Map.of() : Map.of("Authorization", "Bearer " + key);
        byte[] response = Http.postForBytes(url, headers, body.toByteArray(), "multipart/form-data; boundary=" + boundary, 30).get();
        JsonObject json = JsonParser.parseString(new String(response, StandardCharsets.UTF_8)).getAsJsonObject();
        return json.has("text") ? json.get("text").getAsString() : null;
    }

    private static void field(ByteArrayOutputStream out, String boundary, String name, String value) {
        out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    /** In singleplayer, reuse the main AI key if it's for the same service. */
    private static String sharedKeyFor(String url) {
        try {
            String provider = SolaceConfig.PROVIDER.get().toLowerCase(Locale.ROOT);
            String u = url.toLowerCase(Locale.ROOT);
            if ((u.contains("groq.com") && provider.equals("groq")) || (u.contains("openai.com") && provider.equals("openai"))) {
                return KeyResolver.resolve(SolaceConfig.API_KEY.get());
            }
        } catch (RuntimeException ignored) {
            // config not loaded
        }
        return "";
    }

    // ---------- Solace speaking ----------

    public static void speak(int entityId, String text, String emotion) {
        String provider = ClientConfig.TTS_PROVIDER.get();
        if ("none".equals(provider) || text.isBlank()) return;
        String spoken = text.replaceAll("§.", "");
        PLAYER.execute(() -> {
            try {
                byte[] audio = "elevenlabs".equals(provider) ? elevenLabs(spoken) : openAiSpeech(spoken, emotion);
                play(audio, entityId, "glitch".equals(emotion));
            } catch (Exception e) {
                warn("Text-to-speech failed: " + e.getMessage());
            }
        });
    }

    private static byte[] openAiSpeech(String text, String emotion) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", ClientConfig.TTS_MODEL.get());
        body.addProperty("input", text);
        body.addProperty("voice", ClientConfig.TTS_VOICE.get());
        body.addProperty("response_format", "pcm");
        if (ClientConfig.TTS_MODEL.get().contains("gpt-4o")) {
            body.addProperty("instructions", "Speak softly and a little hesitantly, like a lonely, gentle presence. Current emotion: " + emotion + ".");
        }
        String key = KeyResolver.resolve(ClientConfig.TTS_API_KEY.get());
        if (key.isEmpty()) key = sharedKeyFor(ClientConfig.TTS_BASE_URL.get());
        Map<String, String> headers = key.isEmpty() ? Map.of() : Map.of("Authorization", "Bearer " + key);
        return Http.postForBytes(stripSlash(ClientConfig.TTS_BASE_URL.get()) + "/audio/speech", headers,
                Http.GSON.toJson(body).getBytes(StandardCharsets.UTF_8), "application/json", 30).get();
    }

    private static byte[] elevenLabs(String text) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("model_id", ClientConfig.TTS_MODEL.get().startsWith("eleven") ? ClientConfig.TTS_MODEL.get() : "eleven_flash_v2_5");
        String voice = URLEncoder.encode(ClientConfig.TTS_VOICE.get(), StandardCharsets.UTF_8);
        String url = "https://api.elevenlabs.io/v1/text-to-speech/" + voice + "?output_format=pcm_" + ClientConfig.TTS_SAMPLE_RATE.get();
        return Http.postForBytes(url, Map.of("xi-api-key", KeyResolver.resolve(ClientConfig.TTS_API_KEY.get())),
                Http.GSON.toJson(body).getBytes(StandardCharsets.UTF_8), "application/json", 30).get();
    }

    private static void play(byte[] audio, int entityId, boolean glitch) throws Exception {
        AudioFormat format;
        byte[] pcm;
        if (audio.length > 12 && audio[0] == 'R' && audio[1] == 'I' && audio[2] == 'F' && audio[3] == 'F') {
            try (AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audio))) {
                format = in.getFormat();
                pcm = in.readAllBytes();
            }
        } else if (audio.length > 3 && ((audio[0] == 'I' && audio[1] == 'D' && audio[2] == '3') || (audio[0] & 0xFF) == 0xFF && (audio[1] & 0xE0) == 0xE0)) {
            throw new IllegalStateException("the voice service sent MP3 audio; it must send raw PCM or WAV");
        } else {
            format = new AudioFormat(ClientConfig.TTS_SAMPLE_RATE.get(), 16, 1, true, false);
            pcm = audio;
        }
        if (format.getSampleSizeInBits() == 16) {
            float volume = (float) (ClientConfig.TTS_VOLUME.get() * distanceVolume(entityId));
            pcm = adjust(pcm, format.isBigEndian(), volume, glitch);
        }
        STOP_PLAYBACK.set(false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
            line.open(format);
            line.start();
            int chunk = 4096;
            for (int i = 0; i < pcm.length && !STOP_PLAYBACK.get(); i += chunk) {
                line.write(pcm, i, Math.min(chunk, pcm.length - i));
            }
            if (!STOP_PLAYBACK.get()) line.drain();
            line.stop();
        }
    }

    /** Quieter the farther Solace is from you. */
    private static double distanceVolume(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || entityId < 0) return 1.0;
        Entity e = mc.level.getEntity(entityId);
        if (e == null) return 0.6;
        double d = e.distanceTo(mc.player);
        return Math.max(0.15, 1.0 - d / 40.0);
    }

    /** Applies volume, and for "glitch" lines a stutter + crunch effect. */
    private static byte[] adjust(byte[] pcm, boolean bigEndian, float volume, boolean glitch) {
        ByteBuffer in = ByteBuffer.wrap(pcm).order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        ByteArrayOutputStream out = new ByteArrayOutputStream(pcm.length + (glitch ? pcm.length / 4 : 0));
        int samples = pcm.length / 2;
        short[] data = new short[samples];
        for (int i = 0; i < samples; i++) data[i] = in.getShort(i * 2);
        ByteBuffer one = ByteBuffer.allocate(2).order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        java.util.Random rnd = new java.util.Random();
        int i = 0;
        while (i < samples) {
            int s = Math.round(data[i] * volume);
            if (glitch) s = (s >> 9) << 9; // crunchy
            s = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s));
            one.clear();
            one.putShort((short) s);
            out.write(one.array(), 0, 2);
            // Occasionally jump back a little (stutter).
            if (glitch && i % 2400 == 0 && i > 4800 && rnd.nextFloat() < 0.25F) {
                i -= 2400;
                continue;
            }
            i++;
        }
        return out.toByteArray();
    }

    public static void stopAll() {
        RECORDING.set(false);
        STOP_PLAYBACK.set(true);
        TargetDataLine line = micLine;
        if (line != null) line.stop();
    }

    // ---------- Helpers ----------

    /** Builds a WAV file from raw 16-bit mono audio. */
    static byte[] wav(byte[] pcm, int rate) {
        ByteBuffer b = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);
        return b.array();
    }

    private static String stripSlash(String url) {
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static void warn(String message) {
        long now = System.currentTimeMillis();
        if (now - lastWarning < 30_000) return;
        lastWarning = now;
        Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("[Solace voice] " + message).withStyle(ChatFormatting.GRAY), false);
        });
    }
}
