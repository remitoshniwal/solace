package com.solacemod.solace.ai;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * Solace's answer after we've read the AI's JSON.
 *
 * @param say      what Solace says in chat (may be empty if it only acts)
 * @param emotion  calm, happy, sad, angry, afraid or glitch
 * @param actions  things to do in the world, like {"type":"mine","block":"iron_ore","count":5}
 * @param remember new facts to store in long-term memory
 * @param trust    how much this exchange changed Solace's trust in the player (-3..3)
 */
public record SolaceReply(String say, String emotion, List<JsonObject> actions, List<String> remember, int trust) {
    public static SolaceReply text(String say) {
        return new SolaceReply(say, "calm", List.of(), List.of(), 0);
    }

    public static SolaceReply of(String say, String emotion, List<JsonObject> actions) {
        return new SolaceReply(say, emotion, actions, List.of(), 0);
    }
}
