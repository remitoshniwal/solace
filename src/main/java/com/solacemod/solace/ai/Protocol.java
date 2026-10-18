package com.solacemod.solace.ai;

/** The "language" a provider's API speaks. Most providers copy OpenAI's format. */
public enum Protocol {
    /** POST {baseUrl}/chat/completions. Used by OpenAI, Groq, OpenRouter, Ollama, LM Studio, DeepSeek, Mistral, xAI, ... */
    OPENAI,
    /** POST {baseUrl}/v1/messages. Anthropic (Claude). */
    ANTHROPIC,
    /** POST {baseUrl}/v1beta/models/{model}:generateContent. Google Gemini. */
    GEMINI
}
