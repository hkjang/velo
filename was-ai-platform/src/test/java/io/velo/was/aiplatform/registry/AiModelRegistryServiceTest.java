package io.velo.was.aiplatform.registry;

import io.velo.was.config.ServerConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiModelRegistryServiceTest {

    @Test
    void promotesRuntimeVersionToActiveRoutingProfile() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.validate();

        AiModelRegistryService service = new AiModelRegistryService(configuration);
        service.registerOrUpdate(new AiModelRegistrationRequest(
                "llm-general", "LLM", "builtin", "v2", "balanced", 210, 89, false, true, "CANARY", "runtime"
        ));

        AiRegisteredModel promoted = service.updateVersionStatus("llm-general", "v2", "ACTIVE");

        assertEquals("v2", promoted.activeVersion());
        assertTrue(service.routingModels().stream()
                .anyMatch(model -> model.getName().equals("llm-general") && model.getVersion().equals("v2")));
    }

    @Test
    void retiresActiveVersionAndFallsForwardToCanary() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.validate();

        AiModelRegistryService service = new AiModelRegistryService(configuration);
        service.registerOrUpdate(new AiModelRegistrationRequest(
                "reco-personalization", "RECOMMENDER", "builtin", "v2", "balanced", 88, 84, false, true, "CANARY", "runtime"
        ));

        AiRegisteredModel model = service.updateVersionStatus("reco-personalization", "v1", "INACTIVE");

        assertEquals("v2", model.activeVersion());
        assertEquals("ACTIVE", model.versions().stream()
                .filter(version -> version.version().equals("v2"))
                .findFirst()
                .orElseThrow()
                .status());
    }

    @Test
    void weightedRoutingModelsExposeActiveAndCanaryVersions() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.validate();

        AiModelRegistryService service = new AiModelRegistryService(configuration);
        service.registerOrUpdate(new AiModelRegistrationRequest(
                "llm-general", "LLM", "builtin", "v2", "balanced", 240, 90, false, true, "CANARY", "runtime"
        ));

        assertTrue(service.weightedRoutingModels().stream()
                .anyMatch(model -> model.getName().equals("llm-general")
                        && model.getVersion().equals("v1")
                        && model.getTrafficWeight() == 100));
        assertTrue(service.weightedRoutingModels().stream()
                .anyMatch(model -> model.getName().equals("llm-general")
                        && model.getVersion().equals("v2")
                        && model.getTrafficWeight() == 10));
        assertEquals(1, service.routingModels().stream()
                .filter(model -> model.getName().equals("llm-general"))
                .count());
    }

    @Test
    void deploymentPlannerDetectsCanaryDryRunWithoutMutatingRegistry() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.validate();

        AiModelRegistryService service = new AiModelRegistryService(configuration);
        AiModelDeploymentPlanner.DeploymentPlan plan = AiModelDeploymentPlanner.plan(service,
                new AiModelRegistrationRequest("llm-general", "LLM", "builtin", "v3", "balanced",
                        180, 92, false, true, "CANARY", "runtime"),
                "/ai-platform");

        assertFalse(plan.newModel());
        assertTrue(plan.newVersion());
        assertTrue(plan.canaryDeployment());
        assertEquals("v1", plan.currentActiveVersion());
        assertEquals("/ai-platform/invoke/llm-general", plan.affectedEndpoint());
        assertTrue(service.findModel("llm-general").versions().stream()
                .noneMatch(version -> version.version().equals("v3")));
    }
}
