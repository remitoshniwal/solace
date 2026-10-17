package com.solacemod.solace.ai;

import java.util.List;

/**
 * Everything a provider needs to make one request.
 *
 * @param system      the hidden instructions (Solace's personality, the story, the rules)
 * @param messages    the conversation so far, oldest first; the last one is always from "user"
 * @param maxTokens   upper limit on how long the reply can be
 * @param temperature how random the reply is (0 = predictable, 1+ = creative)
 * @param wantJson    true when we expect the model to answer with a JSON object
 */
public record LlmRequest(String system, List<ChatMessage> messages, int maxTokens, double temperature, boolean wantJson) {
}
