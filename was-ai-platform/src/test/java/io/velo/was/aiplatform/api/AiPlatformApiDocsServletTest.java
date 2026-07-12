package io.velo.was.aiplatform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.velo.was.aiplatform.provider.AiProviderRegistry;
import io.velo.was.aiplatform.publishing.AiPublishedApiService;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.aiplatform.tenant.AiTenantService;
import io.velo.was.config.ServerConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPlatformApiDocsServletTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void openApiSpecIncludesDynamicModelsAndMergedModelRegistryOperations() throws Exception {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.validate();
        AiModelRegistryService registryService = new AiModelRegistryService(configuration);
        AiPublishedApiService publishedApiService = new AiPublishedApiService(configuration, registryService);
        AiTenantService tenantService = new AiTenantService(configuration);

        AiPlatformApiDocsServlet servlet = new AiPlatformApiDocsServlet(
                configuration,
                registryService,
                publishedApiService,
                tenantService,
                new AiProviderRegistry()
        );

        JsonNode spec = mapper.readTree(servlet.buildOpenApiSpec("/ai-platform"));

        assertEquals("3.0.3", spec.path("openapi").asText());
        assertTrue(spec.at("/paths/~1api~1models").has("get"));
        assertTrue(spec.at("/paths/~1api~1models").has("post"));
        assertTrue(spec.at("/paths/~1api~1readiness").has("get"));
        assertTrue(spec.at("/paths/~1api~1models~1deployment-plan").has("post"));
        assertTrue(spec.at("/paths/~1api~1models~1{name}~1bundle").has("get"));
        assertTrue(spec.at("/paths/~1api~1tenants~1{id}~1keys~1{keyId}").has("delete"));
        assertTrue(spec.at("/paths/~1api~1tenants~1{id}~1keys~1{keyId}~1rotate").has("post"));
        assertTrue(spec.at("/paths/~1api~1tenants~1{id}~1policies").has("post"));
        assertTrue(spec.at("/paths/~1api~1config~1diagnostics").has("get"));
        assertTrue(spec.at("/paths/~1api~1config~1remediation-plan").has("get"));
        assertTrue(spec.at("/paths/~1api~1config~1patch-bundle").has("get"));
        assertTrue(spec.at("/paths/~1api~1config~1rollout-gate").has("get"));
        assertTrue(spec.at("/paths/~1api~1operations~1runbook").has("get"));
        assertTrue(spec.at("/paths/~1api~1operations~1remediation-plan").has("get"));
        assertTrue(spec.at("/paths/~1api~1operations~1config-patch").has("get"));
        assertTrue(spec.at("/paths/~1api~1operations~1rollout-gate").has("get"));
        assertTrue(spec.at("/paths/~1api~1operations~1canary").has("get"));
        assertTrue(spec.at("/paths/~1api~1metrics~1prometheus").has("get"));
        assertTrue(spec.at("/paths/~1api~1providers~1circuit-breakers").has("get"));
        assertTrue(spec.at("/paths/~1api~1gateway-audit~1export").has("get"));
        assertEquals("X-AI-API-Key", spec.at("/x-velo/apiKeyHeader").asText());
        assertTrue(containsModel(spec.at("/x-velo/models"), "llm-general"));
        assertTrue(containsText(spec.at("/paths/~1invoke~1{model}/post/parameters/0/schema/enum"), "llm-general"));
        assertTrue(spec.at("/paths/~1invoke~1{model}/post/x-velo-publishedEndpoints").isArray());
    }

    private static boolean containsModel(JsonNode models, String modelName) {
        for (JsonNode model : models) {
            if (modelName.equals(model.path("name").asText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsText(JsonNode array, String value) {
        for (JsonNode node : array) {
            if (value.equals(node.asText())) {
                return true;
            }
        }
        return false;
    }
}
