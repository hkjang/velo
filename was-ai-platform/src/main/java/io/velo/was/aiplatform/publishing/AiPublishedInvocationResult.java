package io.velo.was.aiplatform.publishing;

public record AiPublishedInvocationResult(
        AiPublishedEndpoint endpoint,
        String sessionId,
        String outputText,
        int estimatedTokens,
        double estimatedCost,
        String provider,
        int expectedLatencyMs
) {
}