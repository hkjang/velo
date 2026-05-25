package io.velo.was.aiplatform.operations;

import io.velo.was.aiplatform.gateway.AiGatewayService;
import io.velo.was.aiplatform.observability.AiPlatformUsageSnapshot;
import io.velo.was.aiplatform.provider.AiProviderRegistry;
import io.velo.was.aiplatform.tenant.AiTenantSnapshot;

public final class AiPlatformMetricsExporter {

    private AiPlatformMetricsExporter() {
    }

    public static String prometheus(AiGatewayService gatewayService,
                                    AiPlatformUsageSnapshot usage,
                                    AiTenantSnapshot tenants,
                                    AiProviderRegistry providerRegistry) {
        StringBuilder out = new StringBuilder(2048);
        metric(out, "velo_ai_route_calls_total", usage.routeCalls());
        metric(out, "velo_ai_infer_calls_total", usage.inferCalls());
        metric(out, "velo_ai_stream_calls_total", usage.streamCalls());
        metric(out, "velo_ai_published_invoke_calls_total", usage.publishedInvokeCalls());
        metric(out, "velo_ai_metered_requests_total", usage.meteredRequests());
        metric(out, "velo_ai_estimated_tokens_total", usage.totalEstimatedTokens());
        metric(out, "velo_ai_context_cache_hits_total", gatewayService.getCacheHitCount());
        metric(out, "velo_ai_semantic_cache_hits_total", gatewayService.getSemanticCacheHitCount());
        metric(out, "velo_ai_context_cache_entries", gatewayService.getContextCacheSize());
        metric(out, "velo_ai_semantic_cache_entries", gatewayService.getSemanticCacheSize());
        metric(out, "velo_ai_provider_failovers_total", gatewayService.getFailoverCount());
        metric(out, "velo_ai_shadow_requests_total", gatewayService.getShadowRequestCount());
        metric(out, "velo_ai_tenants_total", tenants.totalTenants());
        metric(out, "velo_ai_tenants_active", tenants.activeTenants());
        for (AiProviderRegistry.AiProviderCircuitInfo circuit : providerRegistry.listCircuitBreakers()) {
            labeledMetric(out, "velo_ai_provider_circuit_open", circuit.state().equals("OPEN") ? 1 : 0,
                    "provider", circuit.providerId());
            labeledMetric(out, "velo_ai_provider_failures", circuit.failureCount(),
                    "provider", circuit.providerId());
        }
        gatewayService.getModelRequestCounts().forEach((model, count) ->
                labeledMetric(out, "velo_ai_model_requests_total", count, "model", model));
        return out.toString();
    }

    private static void metric(StringBuilder out, String name, long value) {
        out.append(name).append(' ').append(value).append('\n');
    }

    private static void labeledMetric(StringBuilder out, String name, long value, String label, String labelValue) {
        out.append(name)
                .append('{').append(label).append("=\"").append(escape(labelValue)).append("\"} ")
                .append(value).append('\n');
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
