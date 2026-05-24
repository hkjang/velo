package io.velo.was.aiplatform.registry;

import java.util.ArrayList;
import java.util.List;

public final class AiModelDeploymentPlanner {

    private AiModelDeploymentPlanner() {
    }

    public static DeploymentPlan plan(AiModelRegistryService registryService,
                                      AiModelRegistrationRequest request,
                                      String contextPath) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Model name is required");
        }
        if (request.version() == null || request.version().isBlank()) {
            throw new IllegalArgumentException("Model version is required");
        }

        AiRegisteredModel existing = registryService.findModel(request.name());
        String requestedStatus = normalizeStatus(request.status(), request.enabled());
        String currentActiveVersion = existing == null ? "" : existing.activeVersion();
        boolean newModel = existing == null;
        boolean newVersion = existing == null || existing.versions().stream()
                .noneMatch(version -> request.version().equalsIgnoreCase(version.version()));
        boolean promotesActive = "ACTIVE".equals(requestedStatus);
        boolean canaryDeployment = "CANARY".equals(requestedStatus);
        boolean replacesActive = promotesActive
                && currentActiveVersion != null
                && !currentActiveVersion.isBlank()
                && !request.version().equalsIgnoreCase(currentActiveVersion);

        List<String> warnings = new ArrayList<>();
        if (!request.enabled()) {
            warnings.add("The submitted version is disabled and will not be routable.");
        }
        if (request.latencyMs() > 1200) {
            warnings.add("latencyMs is above the default p99 target of 1200ms.");
        }
        if (request.accuracyScore() < 60) {
            warnings.add("accuracyScore is low; prefer CANARY before promoting to ACTIVE.");
        }
        if (replacesActive) {
            warnings.add("Promoting this version will move the current ACTIVE version to CANARY.");
        }

        String normalizedContextPath = contextPath == null || contextPath.isBlank() ? "" : contextPath;
        return new DeploymentPlan(
                request.name().trim(),
                request.version().trim(),
                requestedStatus,
                newModel,
                newVersion,
                promotesActive,
                canaryDeployment,
                replacesActive,
                currentActiveVersion == null ? "" : currentActiveVersion,
                normalizedContextPath + "/invoke/" + request.name().trim(),
                warnings
        );
    }

    private static String normalizeStatus(String value, boolean enabled) {
        if (!enabled) {
            return "INACTIVE";
        }
        if (value == null || value.isBlank()) {
            return "ACTIVE";
        }
        return value.trim().toUpperCase();
    }

    public record DeploymentPlan(String modelName,
                                 String version,
                                 String requestedStatus,
                                 boolean newModel,
                                 boolean newVersion,
                                 boolean promotesActive,
                                 boolean canaryDeployment,
                                 boolean replacesActive,
                                 String currentActiveVersion,
                                 String affectedEndpoint,
                                 List<String> warnings) {
    }
}
