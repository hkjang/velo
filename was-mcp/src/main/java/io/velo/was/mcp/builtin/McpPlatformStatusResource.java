package io.velo.was.mcp.builtin;

import io.velo.was.aiplatform.gateway.AiGatewayService;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.aiplatform.registry.AiModelRegistrySummary;
import io.velo.was.mcp.resource.McpResource;
import io.velo.was.mcp.resource.McpResourceContents;
import io.velo.was.mcp.resource.McpResourceProvider;

/**
 * MCP resource: {@code mcp://platform/status} — real-time AI platform health and metrics.
 */
public final class McpPlatformStatusResource {

    private McpPlatformStatusResource() {}

    public static final String URI = "mcp://platform/status";

    public static McpResource descriptor() {
        return new McpResource(URI, "AI Platform Status",
                "Real-time status snapshot of the AI platform: model counts, gateway metrics, and cache state.",
                "application/json");
    }

    public static McpResourceProvider provider(AiModelRegistryService registryService,
                                               AiGatewayService gatewayService) {
        return uri -> {
            AiModelRegistrySummary summary = registryService.summary();
            long totalRequests = gatewayService.getTotalRequests();
            long cacheHits = gatewayService.getCacheHitCount();
            int cacheSize = gatewayService.getContextCacheSize();

            String json = "{"
                    + "\"registry\":{"
                    + "\"totalModels\":" + summary.totalModels() + ","
                    + "\"totalVersions\":" + summary.totalVersions() + ","
                    + "\"routableModels\":" + summary.routableModels() + ","
                    + "\"activeVersions\":" + summary.activeVersions() + ","
                    + "\"canaryVersions\":" + summary.canaryVersions()
                    + "},"
                    + "\"gateway\":{"
                    + "\"totalRequests\":" + totalRequests + ","
                    + "\"cacheHits\":" + cacheHits + ","
                    + "\"contextCacheSize\":" + cacheSize + ","
                    + "\"modelRequestCounts\":" + mapToJson(gatewayService.getModelRequestCounts())
                    + "}"
                    + "}";
            return McpResourceContents.text(uri, "application/json", json);
        };
    }

    private static String mapToJson(java.util.Map<String, Long> map) {
        StringBuilder sb = new StringBuilder(128).append('{');
        boolean first = true;
        for (java.util.Map.Entry<String, Long> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey().replace("\"", "\\\"")).append("\":").append(e.getValue());
        }
        return sb.append('}').toString();
    }
}
