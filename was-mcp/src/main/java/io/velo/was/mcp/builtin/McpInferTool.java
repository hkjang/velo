package io.velo.was.mcp.builtin;

import io.velo.was.aiplatform.gateway.AiGatewayInferenceResult;
import io.velo.was.aiplatform.gateway.AiGatewayRequest;
import io.velo.was.aiplatform.gateway.AiGatewayService;
import io.velo.was.mcp.tool.McpTool;
import io.velo.was.mcp.tool.McpToolCallResult;
import io.velo.was.mcp.tool.McpToolExecutor;
import io.velo.was.mcp.tool.McpToolInputSchema;

import java.util.List;
import java.util.Map;

/**
 * MCP tool: {@code infer} — run LLM inference via the AI gateway.
 *
 * <p>Input arguments:
 * <ul>
 *   <li>{@code prompt} (required) — the input text</li>
 *   <li>{@code requestType} (optional) — CHAT | VISION | RECOMMENDATION | AUTO</li>
 *   <li>{@code sessionId} (optional) — used for context cache key</li>
 * </ul>
 *
 * <p>Returns a text content block with the inference output and routing metadata.
 */
public final class McpInferTool {

    private McpInferTool() {}

    public static final String NAME = "infer";

    /** Build the tool descriptor. */
    public static McpTool descriptor() {
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        properties.put("prompt", Map.of(
                "type", "string",
                "description", "Input text prompt for inference"));
        properties.put("requestType", Map.of(
                "type", "string",
                "description", "Request type: CHAT, VISION, RECOMMENDATION, or AUTO",
                "enum", List.of("CHAT", "VISION", "RECOMMENDATION", "AUTO")));
        properties.put("sessionId", Map.of(
                "type", "string",
                "description", "Optional session ID for context cache keying"));

        return new McpTool(NAME,
                "Run AI inference through the Velo gateway. Routes the request to the appropriate model " +
                "and returns the generated output along with routing metadata.",
                new McpToolInputSchema("object", properties, List.of("prompt")));
    }

    /** Build the executor backed by {@link AiGatewayService}. */
    public static McpToolExecutor executor(AiGatewayService gatewayService) {
        return arguments -> {
            String prompt = requireString(arguments, "prompt");
            String requestType = stringOrDefault(arguments, "requestType", "AUTO");
            String sessionId = stringOrDefault(arguments, "sessionId", null);

            AiGatewayRequest req = new AiGatewayRequest(requestType, prompt, sessionId, false);
            AiGatewayInferenceResult result = gatewayService.infer(req);

            String output = result.outputText()
                    + "\n\n[model=" + result.decision().modelName()
                    + " route=" + result.decision().routePolicy()
                    + " tokens≈" + result.estimatedTokens()
                    + " confidence=" + String.format("%.2f", result.confidence()) + "]";

            return McpToolCallResult.text(output);
        };
    }

    private static String requireString(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null || val.toString().isBlank()) {
            throw new IllegalArgumentException("'" + key + "' is required and must not be blank");
        }
        return val.toString();
    }

    private static String stringOrDefault(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return (val != null && !val.toString().isBlank()) ? val.toString() : defaultValue;
    }
}
