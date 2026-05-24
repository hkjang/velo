package io.velo.was.aiplatform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.aiplatform.tenant.AiTenantService;
import io.velo.was.config.ServerConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPlatformApiJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readinessReportsRoutableModelsAndTenantChecks() throws Exception {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.validate();
        AiModelRegistryService registryService = new AiModelRegistryService(configuration);
        AiTenantService tenantService = new AiTenantService(configuration);

        String json = AiPlatformApiJson.readiness(
                configuration,
                registryService.summary(),
                tenantService.snapshot(),
                List.of()
        );

        JsonNode readiness = mapper.readTree(json);
        assertEquals("UP", readiness.path("status").asText());
        assertTrue(readiness.path("ready").asBoolean());
        assertTrue(readiness.at("/checks/routableModels").asInt() > 0);
        assertEquals(0, readiness.path("warnings").size());
    }
}
