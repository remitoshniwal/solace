package com.solacemod.solace.ai;

/** Thrown when a provider fails. Carries the HTTP status (or -1) so we can show a helpful hint. */
public class LlmException extends RuntimeException {
    private final int status;
    private final RateInfo rate;

    public LlmException(int status, String message) {
        this(status, message, RateInfo.NONE);
    }

    public LlmException(int status, String message, RateInfo rate) {
        super(message);
        this.status = status;
        this.rate = rate == null ? RateInfo.NONE : rate;
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
        this.status = -1;
        this.rate = RateInfo.NONE;
    }

    /** What the provider said about its limits along with this error (when to retry, whether a daily limit was hit). */
    public RateInfo rate() {
        return rate;
    }

    public int status() {
        return status;
    }

    /**
     * The provider's own explanation, pulled out of its JSON error (e.g. Google's "error.message"), or null.
     * This is usually the most useful thing to show, because it says exactly what's wrong.
     */
    public String providerMessage() {
        String msg = getMessage();
        if (msg == null) return null;
        int brace = msg.indexOf('{');
        if (brace < 0) return null;
        try {
            com.google.gson.JsonElement el = com.google.gson.JsonParser.parseString(msg.substring(brace));
            if (!el.isJsonObject()) return null;
            com.google.gson.JsonObject o = el.getAsJsonObject();
            com.google.gson.JsonElement err = o.get("error");
            if (err != null && err.isJsonObject() && err.getAsJsonObject().has("message")) {
                return err.getAsJsonObject().get("message").getAsString();
            }
            if (err != null && err.isJsonPrimitive()) return err.getAsString();
            if (o.has("message") && o.get("message").isJsonPrimitive()) return o.get("message").getAsString();
            if (o.has("detail") && o.get("detail").isJsonPrimitive()) return o.get("detail").getAsString(); // Mistral
            if (o.has("error_description")) return o.get("error_description").getAsString();
        } catch (RuntimeException ignored) {
            // The body was cut off or isn't JSON
        }
        return null;
    }

    /** A short, beginner friendly explanation, plus the provider's own reason when it gave one. */
    public String hint() {
        if (status == -2) return getMessage(); // every brain is resting: the message already says so
        if (status == 404 && getMessage() != null && getMessage().startsWith("Model '")) return getMessage(); // already explains, with the list
        String base = baseHint();
        String detail = providerMessage();
        if (status == 403 && detail != null && detail.toLowerCase().contains("denied access")) {
            // Google (and others) block whole accounts/projects. Nothing in the mod can fix that.
            return "The provider has blocked this account or project (403). This can't be fixed from the mod. "
                    + "Pick a different provider, or contact the provider's support. Provider says: \"" + detail + "\"";
        }
        if (detail == null || detail.isBlank()) return base;
        String d = detail.length() > 220 ? detail.substring(0, 220) + "..." : detail;
        return base + " Provider says: \"" + d + "\"";
    }

    private String baseHint() {
        // Some providers (Gemini, xAI) report a bad key as 400 instead of 401.
        String reason = providerMessage() == null ? "" : providerMessage().toLowerCase();
        if ((status == 400 || status == 401 || status == 403) && (reason.contains("api key") || reason.contains("api_key")
                || reason.contains("authentication") || reason.contains("unauthorized"))) {
            return "Your API key was rejected (" + status + "). Copy it again from the provider's website and paste it into Solace setup.";
        }
        return switch (status) {
            case 400 -> "The provider rejected the request (400). The model name may be wrong for this provider.";
            case 401 -> "The API key was rejected (401). Check your API key in Solace setup.";
            case 403 -> "Access denied (403). The key works but isn't allowed to do this (see the reason below).";
            case 404 -> "Model not found (404). The provider may have retired it. In Solace setup, put a current model name in the Model box (check the provider's website).";
            case 429 -> "Rate limited or out of credits (429). Wait a bit, or check your plan with the provider.";
            case 500, 502, 503, 504, 529 -> "The provider is having problems (" + status + "). Try again later.";
            default -> {
                if (status > 0) yield "Request failed with HTTP " + status + ".";
                String m = getMessage() == null ? "" : getMessage().toLowerCase();
                if (m.contains("timeout") || m.contains("timed out")) {
                    yield "The AI took too long to answer. Free models are often slow or busy: try a faster model, "
                            + "or raise timeoutSeconds in Solace setup > Advanced.";
                }
                yield "Could not reach the provider: " + getMessage();
            }
        };
    }
}
