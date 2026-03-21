package io.velo.was.aiplatform;

import io.velo.was.admin.client.AdminClient;
import io.velo.was.admin.client.LocalAdminClient;
import io.velo.was.aiplatform.api.AiPlatformApiDocsServlet;
import io.velo.was.aiplatform.api.AiPlatformApiServlet;
import io.velo.was.aiplatform.billing.AiBillingService;
import io.velo.was.aiplatform.plugin.AiContentFilterPlugin;
import io.velo.was.aiplatform.plugin.AiPluginRegistry;
import io.velo.was.aiplatform.provider.AiProviderRegistry;
import io.velo.was.aiplatform.intent.IntentRoutingPolicyService;
import io.velo.was.aiplatform.intent.RouteAuditLogger;
import io.velo.was.aiplatform.intent.RouteDecisionEngine;
import io.velo.was.aiplatform.persistence.AiPlatformDataStore;
import io.velo.was.aiplatform.gateway.AiChatCompletionServlet;
import io.velo.was.aiplatform.gateway.AiGatewayService;
import io.velo.was.aiplatform.gateway.AiGatewayServlet;
import io.velo.was.aiplatform.gateway.AiTextCompletionServlet;
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

        // 영속화 레이어 (Jackson 기반 JSON 파일)
        AiPlatformDataStore dataStore = new AiPlatformDataStore(java.nio.file.Path.of("."));

        AiModelRegistryService registryService = new AiModelRegistryService(configuration, dataStore);
        AiPlatformUsageService usageService = new AiPlatformUsageService();
        AiTenantService tenantService = new AiTenantService(configuration, dataStore);
        AiPublishedApiService publishedApiService = new AiPublishedApiService(configuration, registryService);
        AiBillingService billingService = new AiBillingService(publishedApiService, registryService);
        AiPluginRegistry pluginRegistry = new AiPluginRegistry();
        if (configuration.getServer().getAiPlatform().getDifferentiation().isPluginFrameworkEnabled()) {
            pluginRegistry.register(new AiContentFilterPlugin());
        }
        AiProviderRegistry providerRegistry = new AiProviderRegistry();
        AiGatewayService gatewayService = new AiGatewayService(configuration, registryService, providerRegistry);

        // 의도 기반 라우팅 엔진 설정 (영속화 연결)
        IntentRoutingPolicyService intentPolicyService = new IntentRoutingPolicyService(dataStore);
        RouteAuditLogger auditLogger = new RouteAuditLogger();
        String defaultModel = configuration.getServer().getAiPlatform().getServing().getModels().stream()
                .filter(ServerConfiguration.ModelProfile::isDefaultSelected)
                .findFirst()
                .map(ServerConfiguration.ModelProfile::getName)
                .orElse("llm-general");
        RouteDecisionEngine intentEngine = new RouteDecisionEngine(intentPolicyService, defaultModel);
        gatewayService.setIntentEngine(intentEngine);

        var builder = SimpleServletApplication.builder(APP_NAME, contextPath)
                .filter(new AiPlatformAuthFilter())
                .servlet("/", new AiPlatformDashboardServlet(configuration, registryService, gatewayService, usageService, tenantService))
                .servlet("/login", new AiPlatformLoginServlet(configuration, adminClient))
                .servlet("/logout", new AiPlatformLogoutServlet())
                .servlet("/api/*", new AiPlatformApiServlet(configuration, registryService, gatewayService, usageService,
                        publishedApiService, billingService, tenantService, pluginRegistry, providerRegistry,
                        new io.velo.was.aiplatform.edge.AiEdgeService(), intentPolicyService, auditLogger, intentEngine))
                .servlet("/gateway/*", new AiGatewayServlet(configuration, gatewayService, usageService, tenantService))
                .servlet("/invoke/*", new AiPublishedApiServlet(publishedApiService, usageService, tenantService))
                .servlet("/v1/chat/completions", new AiChatCompletionServlet(configuration, gatewayService, usageService, tenantService))
                .servlet("/v1/completions", new AiTextCompletionServlet(configuration, gatewayService, usageService, tenantService))
                .servlet("/v1/models", new AiChatCompletionServlet(configuration, gatewayService, usageService, tenantService));

        if (configuration.getServer().getAiPlatform().getPlatform().isDeveloperPortalEnabled()) {
            builder.servlet("/api-docs/*", new AiPlatformApiDocsServlet(configuration));
        }

        return builder.build();
    }
}