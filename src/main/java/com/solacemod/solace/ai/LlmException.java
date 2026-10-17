package com.solacemod.solace.ai;

/** Thrown when a provider fails. Carries the HTTP status (or -1) so we can show a helpful hint. */
public class LlmException extends RuntimeException {
    private final int status;

    public LlmException(int status, String message) {
        super(message);
        this.status = status;
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
        this.status = -1;
    }

    public int status() {
        return status;
    }

    /** A short, beginner friendly explanation of what probably went wrong. */
    public String hint() {
        return switch (status) {
            case 400 -> "The provider rejected the request (400). The model name may be wrong for this provider.";
            case 401, 403 -> "The API key was rejected (" + status + "). Check your API key in Solace setup.";
            case 404 -> "Not found (404). The model name or base URL is probably wrong.";
            case 429 -> "Rate limited or out of credits (429). Wait a bit, or check your plan with the provider.";
            case 500, 502, 503, 504, 529 -> "The provider is having problems (" + status + "). Try again later.";
            default -> status > 0 ? "Request failed with HTTP " + status + "." : "Could not reach the provider: " + getMessage();
        };
    }
}
