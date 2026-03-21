package io.velo.was.aiplatform.publishing;

import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.config.ServerConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPublishedApiServiceTest {

    @Test
    void listsAndInvokesPublishedEndpoint() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.validate();

        AiModelRegistryService registryService = new AiModelRegistryService(configuration);
        AiPublishedApiService service = new AiPublishedApiService(configuration, registryService);

        assertFalse(service.listEndpoints("/ai-platform").isEmpty());

        AiPublishedInvocationResult result = service.invoke("/ai-platform", "llm-general", "Hello published API", "pub-1");

        assertEquals("llm-general", result.endpoint().modelName());
        assertTrue(result.estimatedTokens() > 0);
        assertTrue(result.estimatedCost() > 0.0d);
    }
}