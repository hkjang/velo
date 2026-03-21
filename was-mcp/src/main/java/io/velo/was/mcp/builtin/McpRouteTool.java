package io.velo.was.mcp.builtin;

import io.velo.was.aiplatform.gateway.AiGatewayRequest;
import io.velo.was.aiplatform.gateway.AiGatewayRouteDecision;
import io.velo.was.aiplatform.gateway.AiGatewayService;
import io.velo.was.mcp.tool.McpTool;
import io.velo.was.mcp.tool.McpToolCallResult;
import io.velo.was.mcp.tool.McpToolExecutor;
import io.velo.was.mcp.tool.McpToolInputSchema;

import java.util.List;
import java.util.Map;

/**
 * MCP tool: {@code route} — get the AI gateway routing decision for a request
 * without running full inference.
 *
 * <p>Useful for debugging routing policies, model selection, and cache behaviour.
 */
public final class McpRouteTool {

    private McpRouteTool() {}

    public static final String NAME = "route";

    public static McpTool descriptor() {
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        properties.put("prompt", Map.of(
                "type", "string",
                "description", "Input prompt used for prompt-based routing analysis"));
        properties.put("requestType", Map.of(
                "type", "string",
                "description", "Request type: CHAT, VISION, RECOMMENDATION, or AUTO",
                "enum", List.of("CHAT", "VISION", "RECOMMENDATION", "AUTO")));
        properties.put("sessionId", Map.of(
                "type", "string",
                "description", "Optional session ID for context cache lookup"));

        return new McpTool(NAME,
                "Resolve the AI gateway routing decision for a given prompt without running inference. " +
                "Returns the selected model, route policy, and reasoning.",
                new McpToolInputSchema("object", properties, List.of("prompt")));
    }

    public static McpToolExecutor executor(AiGatewayService gatewayService) {
        return arguments -> {
            String prompt = require(arguments, "prompt");
            String requestType = stringOrDefault(arguments, "requestType", "AUTO");
            String sessionId = stringOrDefault(arguments, "sessionId", null);

            AiGatewayRequest req = new AiGatewayRequest(requestType, prompt, sessionId, false);
            AiGatewayRouteDecision decision = gatewayService.route(req);

            String output = "Routing decision:"
                    + "\n  model       = " + decision.modelName()
                    + "\n  category    = " + decision.modelCategory()
                    + "\n  provider    = " + decision.provider()
                    + "\n  version     = " + decision.version()
                    + "\n  resolvedType= " + decision.resolvedType()
                    + "\n  routePolicy = " + decision.routePolicy()
                    + "\n  strategy    = " + decision.strategyApplied()
                    + "\n  cacheHit    = " + decision.cacheHit()
                    + "\n  streaming   = " + decision.streamingSupported()
                    + "\n  latencyMs   = " + decision.expectedLatencyMs()
                    + "\n  accuracy    = " + decision.accuracyScore()
                    + "\n  reasoning   = " + decision.reasoning();

            return McpToolCallResult.text(output);
        };
    }

    private static String require(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null || val.toString().isBlank()) {
            throw new IllegalArgumentException("'" + key + "' is required");
        }
        return val.toString();
    }

    private static String stringOrDefault(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return (val != null && !val.toString().isBlank()) ? val.toString() : defaultValue;
    }
}
