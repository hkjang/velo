package io.velo.was.mcp.builtin;

import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.aiplatform.registry.AiRegisteredModel;
import io.velo.was.aiplatform.registry.AiModelVersionInfo;
import io.velo.was.mcp.resource.McpResource;
import io.velo.was.mcp.resource.McpResourceContents;
import io.velo.was.mcp.resource.McpResourceProvider;

import java.util.List;

/**
 * MCP resource: {@code mcp://models} — returns the list of registered AI models as JSON.
 */
public final class McpModelListResource {

    private McpModelListResource() {}

    public static final String URI = "mcp://models";

    public static McpResource descriptor() {
        return new McpResource(URI, "AI Model Registry",
                "List of all registered AI models with their versions, status, and routing information.",
                "application/json");
    }

    public static McpResourceProvider provider(AiModelRegistryService registryService) {
        return uri -> {
            List<AiRegisteredModel> models = registryService.listModels();
            StringBuilder json = new StringBuilder(2048).append("{\"models\":[");
            boolean firstModel = true;
            for (AiRegisteredModel model : models) {
                if (!firstModel) json.append(',');
                firstModel = false;
                json.append("{\"name\":\"").append(escape(model.name())).append('"')
                    .append(",\"category\":\"").append(escape(model.category())).append('"')
                    .append(",\"provider\":\"").append(escape(model.provider())).append('"')
                    .append(",\"enabled\":").append(model.enabled())
                    .append(",\"activeVersion\":\"").append(escape(model.activeVersion())).append('"')
                    .append(",\"routeEligible\":").append(model.enabled() && model.activeVersion() != null && !model.activeVersion().isBlank())
                    .append(",\"versions\":[");
                boolean firstVer = true;
                for (AiModelVersionInfo v : model.versions()) {
                    if (!firstVer) json.append(',');
                    firstVer = false;
                    json.append("{\"version\":\"").append(escape(v.version())).append('"')
                        .append(",\"status\":\"").append(escape(v.status())).append('"')
                        .append(",\"latencyMs\":").append(v.latencyMs())
                        .append(",\"accuracyScore\":").append(v.accuracyScore())
                        .append(",\"enabled\":").append(v.enabled())
                        .append('}');
                }
                json.append("]}");
            }
            json.append("]}");
            return McpResourceContents.text(uri, "application/json", json.toString());
        };
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
