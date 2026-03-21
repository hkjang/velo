package io.velo.was.aiplatform.registry;

import io.velo.was.config.ServerConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}