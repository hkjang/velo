package io.velo.was.aiplatform.registry;

public record AiModelRegistrationRequest(
        String name,
        String category,
        String provider,
        String version,
        String latencyTier,
        int latencyMs,
        int accuracyScore,
        boolean defaultSelected,
        boolean enabled,
        String status,
        String source
) {
}