package io.velo.was.aiplatform.registry;

public record AiModelRegistrySummary(
        int totalModels,
        int totalVersions,
        int routableModels,
        int activeVersions,
        int canaryVersions,
        int bundledModels,
        int runtimeModels
) {
}