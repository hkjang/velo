package io.velo.was.aiplatform.registry;

public record AiModelVersionInfo(
        String version,
        String status,
        String latencyTier,
        int latencyMs,
        int accuracyScore,
        boolean defaultSelected,
        boolean enabled,
        long registeredAtEpochMillis
) {
}