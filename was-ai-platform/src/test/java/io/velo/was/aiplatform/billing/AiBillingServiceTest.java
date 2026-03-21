package io.velo.was.aiplatform.billing;

import io.velo.was.aiplatform.observability.AiPlatformUsageService;
import io.velo.was.aiplatform.observability.AiPlatformUsageSnapshot;
import io.velo.was.aiplatform.publishing.AiPublishedApiService;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.config.ServerConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiBillingServiceTest {

    @Test
    void buildsBillingPreviewFromMeteredUsage() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getAiPlatform().getPlatform().setBillingEnabled(true);
        configuration.validate();

        AiModelRegistryService registryService = new AiModelRegistryService(configuration);
        AiPublishedApiService publishedApiService = new AiPublishedApiService(configuration, registryService);
        AiPlatformUsageService usageService = new AiPlatformUsageService();
        usageService.recordPublishedInvocation("llm-general", 240);
        usageService.recordPublishedInvocation("llm-general", 200);

        AiPlatformUsageSnapshot snapshot = usageService.snapshot(true,
                new io.velo.was.aiplatform.gateway.AiGatewayService(configuration, registryService),
                registryService);
        AiBillingSnapshot billing = new AiBillingService(publishedApiService, registryService).snapshot(snapshot);

        assertTrue(billing.billingEnabled());
        assertTrue(billing.estimatedTotalCost() > 0.0d);
        assertFalse(billing.lineItems().isEmpty());
    }
}