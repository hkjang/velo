package io.velo.was.aiplatform;

import io.velo.was.admin.client.AdminClient;
import io.velo.was.admin.client.LocalAdminClient;
import io.velo.was.aiplatform.api.AiPlatformApiDocsServlet;
import io.velo.was.aiplatform.api.AiPlatformApiServlet;
import io.velo.was.aiplatform.billing.AiBillingService;
import io.velo.was.aiplatform.finetuning.AiFineTuningService;
import io.velo.was.aiplatform.gateway.AiGatewayService;
import io.velo.was.aiplatform.gateway.AiGatewayServlet;
import io.velo.was.aiplatform.observability.AiPlatformUsageService;
import io.velo.was.aiplatform.publishing.AiPublishedApiService;
import io.velo.was.aiplatform.publishing.AiPublishedApiServlet;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.aiplatform.servlet.AiPlatformAuthFilter;
import io.velo.was.aiplatform.servlet.AiPlatformDashboardServlet;
import io.velo.was.aiplatform.servlet.AiPlatformLoginServlet;
import io.velo.was.aiplatform.servlet.AiPlatformLogoutServlet;
import io.velo.was.aiplatform.tenant.AiTenantService;
import io.velo.was.config.ServerConfiguration;
import io.velo.was.servlet.SimpleServletApplication;

public final class AiPlatformApplication {

    private static final String APP_NAME = "velo-ai-platform";

    private AiPlatformApplication() {
    }

    public static SimpleServletApplication create(ServerConfiguration configuration) {
        String contextPath = configuration.getServer().getAiPlatform().getConsole().getContextPath();
        AdminClient adminClient = new LocalAdminClient(configuration);
        AiModelRegistryService registryService = new AiModelRegistryService(configuration);
        AiPlatformUsageService usageService = new AiPlatformUsageService();
        AiTenantService tenantService = new AiTenantService(configuration);
        AiPublishedApiService publishedApiService = new AiPublishedApiService(configuration, registryService);
        AiBillingService billingService = new AiBillingService(publishedApiService, registryService);
        AiFineTuningService fineTuningService = new AiFineTuningService(registryService);
        AiGatewayService gatewayService = new AiGatewayService(configuration, registryService);

        var builder = SimpleServletApplication.builder(APP_NAME, contextPath)
                .filter(new AiPlatformAuthFilter())
                .servlet("/", new AiPlatformDashboardServlet(configuration, registryService, gatewayService, usageService, tenantService))
                .servlet("/login", new AiPlatformLoginServlet(configuration, adminClient))
                .servlet("/logout", new AiPlatformLogoutServlet())
                .servlet("/api/*", new AiPlatformApiServlet(configuration, registryService, gatewayService, usageService,
                        publishedApiService, billingService, fineTuningService, tenantService))
                .servlet("/gateway/*", new AiGatewayServlet(configuration, gatewayService, usageService, tenantService))
                .servlet("/invoke/*", new AiPublishedApiServlet(publishedApiService, usageService, tenantService));

        if (configuration.getServer().getAiPlatform().getPlatform().isDeveloperPortalEnabled()) {
            builder.servlet("/api-docs/*", new AiPlatformApiDocsServlet(configuration));
        }

        return builder.build();
    }
}