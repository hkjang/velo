package io.velo.was.aiplatform.provider;

/**
 * Unified response format returned from AI provider adapters.
 */
public record AiProviderResponse(String providerId,
                                 String model,
                                 String content,
                                 int promptTokens,
                                 int completionTokens,
                                 int totalTokens,
                                 String finishReason,
                                 long latencyMs) {

    public static AiProviderResponse of(String providerId, String model, String content,
                                        int totalTokens, long latencyMs) {
        int promptTokens1 = Math.max(8, totalTokens / 3);
        return new AiProviderResponse(providerId, model, content,
                promptTokens1, totalTokens - promptTokens1, totalTokens, "stop", latencyMs);
    }
}
