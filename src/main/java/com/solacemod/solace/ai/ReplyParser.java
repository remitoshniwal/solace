package com.solacemod.solace.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Turns whatever the AI wrote into a {@link SolaceReply}.
 * Models don't always follow instructions perfectly (extra text, code fences, broken JSON), so this is forgiving:
 * if no JSON can be found, the whole text becomes what Solace says.
 */
public final class ReplyParser {
    private static final Set<String> EMOTIONS = Set.of("calm", "happy", "sad", "angry", "afraid", "glitch");
    private static final int MAX_SAY = 320;
    private static final int MAX_ACTIONS = 4;

    private ReplyParser() {
    }

    public static SolaceReply parse(String raw) {
        if (raw == null) return SolaceReply.text("...");
        JsonObject obj = findJsonObject(raw);
        if (obj == null) {
            return SolaceReply.text(cleanSay(raw));
        }
        String say = obj.has("say") && obj.get("say").isJsonPrimitive() ? obj.get("say").getAsString() : "";
        String emotion = obj.has("emotion") && obj.get("emotion").isJsonPrimitive()
                ? obj.get("emotion").getAsString().toLowerCase(Locale.ROOT).trim() : "calm";
        if (!EMOTIONS.contains(emotion)) emotion = "calm";

        List<JsonObject> actions = new ArrayList<>();
        JsonElement acts = obj.get("actions");
        if (acts != null && acts.isJsonArray()) {
            for (JsonElement a : acts.getAsJsonArray()) {
                if (actions.size() >= MAX_ACTIONS) break;
                if (a.isJsonObject() && a.getAsJsonObject().has("type")) actions.add(a.getAsJsonObject());
                // Allow the short form "actions": ["follow"]
                else if (a.isJsonPrimitive()) {
                    JsonObject shortForm = new JsonObject();
                    shortForm.addProperty("type", a.getAsString());
                    actions.add(shortForm);
                }
            }
        } else if (acts != null && acts.isJsonObject() && acts.getAsJsonObject().has("type")) {
            actions.add(acts.getAsJsonObject());
        }

        List<String> remember = new ArrayList<>();
        JsonElement rem = obj.get("remember");
        if (rem != null && rem.isJsonArray()) {
            for (JsonElement r : rem.getAsJsonArray()) {
                if (r.isJsonPrimitive() && !r.getAsString().isBlank() && remember.size() < 3) remember.add(clip(r.getAsString().trim(), 140));
            }
        } else if (rem != null && rem.isJsonPrimitive() && !rem.getAsString().isBlank()) {
            remember.add(clip(rem.getAsString().trim(), 140));
        }

        int trust = 0;
        JsonElement t = obj.get("trust");
        if (t != null && t.isJsonPrimitive()) {
            try {
                trust = (int) Math.round(t.getAsDouble());
            } catch (NumberFormatException ignored) {
                trust = 0;
            }
        }
        trust = Math.max(-3, Math.min(3, trust));
        return new SolaceReply(cleanSay(say), emotion, actions, remember, trust);
    }

    /** Finds the first complete JSON object in the text, skipping code fences or chatter around it. */
    static JsonObject findJsonObject(String raw) {
        String text = raw.trim();
        int start = text.indexOf('{');
        while (start >= 0) {
            try {
                JsonReader reader = new JsonReader(new StringReader(text.substring(start)));
                reader.setLenient(true);
                JsonElement el = JsonParser.parseReader(reader);
                if (el.isJsonObject()) {
                    JsonObject o = el.getAsJsonObject();
                    if (o.has("say") || o.has("actions")) return o;
                }
            } catch (RuntimeException ignored) {
                // Not valid JSON here; try the next '{'
            }
            start = text.indexOf('{', start + 1);
        }
        return null;
    }

    /** Makes the text safe and tidy for Minecraft chat. */
    public static String cleanSay(String s) {
        String out = s.replace("```json", "").replace("```", "")
                .replace('§', ' ') // no Minecraft formatting codes from the AI
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\*+", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        // Strip a "Solace:" prefix the model sometimes adds.
        out = out.replaceFirst("(?i)^(<solace>|solace:)\\s*", "");
        if (out.length() >= 2 && out.startsWith("\"") && out.endsWith("\"")) out = out.substring(1, out.length() - 1);
        return clip(out, MAX_SAY);
    }

    static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3).trim() + "...";
    }

    /** For tests and debugging. */
    public static JsonArray toJsonArray(List<JsonObject> list) {
        JsonArray arr = new JsonArray();
        list.forEach(arr::add);
        return arr;
    }
}
