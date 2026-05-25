package io.velo.was.aiplatform.operations;

import io.velo.was.aiplatform.gateway.AiGatewayService;
import io.velo.was.aiplatform.observability.AiPlatformUsageService;
import io.velo.was.aiplatform.provider.AiProviderRegistry;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.aiplatform.tenant.AiTenantService;
import io.velo.was.config.ServerConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiOperationsSupportTest {

    @Test
    void prometheusExporterIncludesGatewayAndTenantMetrics() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.validate();
        AiGatewayService gatewayService = new AiGatewayService(configuration);
        AiModelRegistryService registryService = new AiModelRegistryService(configuration);
        AiPlatformUsageService usageService = new AiPlatformUsageService();
        AiTenantService tenantService = new AiTenantService(configuration);

        String metrics = AiPlatformMetricsExporter.prometheus(
                gatewayService,
                usageService.snapshot(false, gatewayService, registryService),
                tenantService.snapshot(),
                new AiProviderRegistry()
        );

        assertTrue(metrics.contains("velo_ai_route_calls_total"));
        assertTrue(metrics.contains("velo_ai_tenants_total"));
    }

    @Test
    void modelBundleManifestContainsRunnerAndRollbackMetadata() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.validate();
        AiModelRegistryService registryService = new AiModelRegistryService(configuration);

        String manifest = AiModelBundleService.manifest(registryService.findModel("llm-general"));

        assertTrue(manifest.contains("velo.ai.bundle/v1"));
        assertTrue(manifest.contains("rollbackStrategy"));
    }

    @Test
    void gpuSchedulerSnapshotReflectsConfiguration() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getAiPlatform().getAdvanced().setGpuSchedulingEnabled(true);
        configuration.getServer().getAiPlatform().getAdvanced().setGpuQueueCapacity(16);
        configuration.validate();
        AiModelRegistryService registryService = new AiModelRegistryService(configuration);

        AiGpuSchedulerService.GpuSchedulerSnapshot snapshot =
                new AiGpuSchedulerService(configuration, registryService).snapshot();

        assertTrue(snapshot.enabled());
        assertTrue(snapshot.queueCapacity() == 16);
    }

    @Test
    void diagnosticsServiceProducesFindingsAndRunbook() {
        ServerConfiguration configuration = new ServerConfiguration();
        configuration.getServer().getAiPlatform().getAdvanced().setPromptFirewallEnabled(false);
        configuration.validate();
        AiGatewayService gatewayService = new AiGatewayService(configuration);
        AiModelRegistryService registryService = new AiModelRegistryService(configuration);
        AiPlatformUsageService usageService = new AiPlatformUsageService();
        AiTenantService tenantService = new AiTenantService(configuration);

        AiPlatformDiagnosticsService.DiagnosticsSnapshot snapshot = new AiPlatformDiagnosticsService(
                configuration,
                registryService.summary(),
                usageService.snapshot(false, gatewayService, registryService),
                tenantService.snapshot(),
                new AiProviderRegistry()
        ).snapshot();

        assertTrue(snapshot.score() < 100);
        assertTrue(snapshot.findings().stream().anyMatch(f -> "PROMPT_FIREWALL_DISABLED".equals(f.code())));
        assertTrue(snapshot.runbook().stream().anyMatch(step -> "/api/config/diagnostics".equals(step.endpoint())));
    }
}
