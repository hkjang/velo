package io.velo.was.aiplatform.provider;

/**
 * Unified response format returned from AI provider adapters.
 * Supports text content and binary data (audio, image) via base64.
 *
 * @param providerId       provider identifier
 * @param model            model used
 * @param content          text content (for text/chat responses)
 * @param promptTokens     prompt token count
 * @param completionTokens completion token count
 * @param totalTokens      total token count
 * @param finishReason     finish reason (stop, length, etc.)
 * @param latencyMs        response latency in milliseconds
 * @param contentType      response content type: "text", "audio/mp3", "audio/wav", "image/png", etc.
 * @param dataBase64       base64-encoded binary response data (for audio/image outputs)
 */
public record AiProviderResponse(String providerId,
                                 String model,
                                 String content,
                                 int promptTokens,
                                 int completionTokens,
                                 int totalTokens,
                                 String finishReason,
                                 long latencyMs,
                                 String contentType,
                                 String dataBase64) {

    /** Backwards-compatible constructor for text responses. */
    public AiProviderResponse(String providerId, String model, String content,
                              int promptTokens, int completionTokens, int totalTokens,
                              String finishReason, long latencyMs) {
        this(providerId, model, content, promptTokens, completionTokens, totalTokens,
                finishReason, latencyMs, "text", null);
    }

    /** Convenience factory for text responses. */
    public static AiProviderResponse of(String providerId, String model, String content,
                                        int totalTokens, long latencyMs) {
        int promptTokens1 = Math.max(8, totalTokens / 3);
        return new AiProviderResponse(providerId, model, content,
                promptTokens1, totalTokens - promptTokens1, totalTokens, "stop", latencyMs,
                "text", null);
    }

    /** Factory for binary responses (audio, image). */
    public static AiProviderResponse binary(String providerId, String model,
                                            String contentType, String dataBase64,
                                            int totalTokens, long latencyMs) {
        return new AiProviderResponse(providerId, model, null,
                0, 0, totalTokens, "stop", latencyMs,
                contentType, dataBase64);
    }

    /** Whether this response contains binary data. */
    public boolean hasBinaryData() {
        return dataBase64 != null && !dataBase64.isBlank();
    }
}
