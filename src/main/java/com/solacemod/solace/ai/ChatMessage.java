package com.solacemod.solace.ai;

/** One line of conversation. Role is "user" (the player / game events) or "assistant" (Solace). */
public record ChatMessage(String role, String content) {
    public static ChatMessage user(String text) {
        return new ChatMessage("user", text);
    }

    public static ChatMessage assistant(String text) {
        return new ChatMessage("assistant", text);
    }

    public boolean isUser() {
        return "user".equals(role);
    }
}
