package io.velo.was.mcp;

import io.velo.was.mcp.audit.McpAuditLog;
import io.velo.was.mcp.policy.McpPolicyEnforcer;
import io.velo.was.mcp.prompt.McpPrompt;
import io.velo.was.mcp.prompt.McpPromptGetResult;
import io.velo.was.mcp.prompt.McpPromptProvider;
import io.velo.was.mcp.prompt.McpPromptRegistry;
import io.velo.was.mcp.protocol.JsonParseException;
import io.velo.was.mcp.protocol.JsonRpcError;
import io.velo.was.mcp.protocol.JsonRpcRequest;
import io.velo.was.mcp.protocol.JsonRpcResponse;
import io.velo.was.mcp.resource.McpResource;
import io.velo.was.mcp.resource.McpResourceContents;
import io.velo.was.mcp.resource.McpResourceProvider;
import io.velo.was.mcp.resource.McpResourceRegistry;
import io.velo.was.mcp.session.McpSession;
import io.velo.was.mcp.session.McpSessionManager;
import io.velo.was.mcp.tool.McpTool;
import io.velo.was.mcp.tool.McpToolCallResult;
import io.velo.was.mcp.tool.McpToolExecutor;
import io.velo.was.mcp.tool.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Core MCP server runtime: parses JSON-RPC requests and dispatches to
 * tool/resource/prompt registries.
 *
 * <p>Supported methods (Phase 1):
 * <ul>
 *   <li>{@code initialize}             — capability negotiation, returns session ID</li>
 *   <li>{@code notifications/initialized} — client acknowledgement (notification, no response)</li>
 *   <li>{@code ping}                   — health check</li>
 *   <li>{@code tools/list}             — enumerate tools</li>
 *   <li>{@code tools/call}             — invoke a tool</li>
 *   <li>{@code resources/list}         — enumerate resources</li>
 *   <li>{@code resources/read}         — read a resource</li>
 *   <li>{@code prompts/list}           — enumerate prompts</li>
 *   <li>{@code prompts/get}            — render a prompt</li>
 * </ul>
 */
public class McpServer {

    private static final Logger log = LoggerFactory.getLogger(McpServer.class);

    /** MCP protocol version this server speaks. */
    public static final String PROTOCOL_VERSION = "2024-11-05";

    private final McpServerInfo serverInfo;
    private final McpToolRegistry toolRegistry;
    private final McpResourceRegistry resourceRegistry;
    private final McpPromptRegistry promptRegistry;
    private final McpSessionManager sessionManager;
    private volatile McpAuditLog auditLog;
    private volatile McpPolicyEnforcer policyEnforcer;

    public McpServer(McpServerInfo serverInfo,
                     McpToolRegistry toolRegistry,
                     McpResourceRegistry resourceRegistry,
                     McpPromptRegistry promptRegistry,
                     McpSessionManager sessionManager) {
        this.serverInfo = serverInfo;
        this.toolRegistry = toolRegistry;
        this.resourceRegistry = resourceRegistry;
        this.promptRegistry = promptRegistry;
        this.sessionManager = sessionManager;
    }

    /** Attach audit logging (called from McpApplication after construction). */
    public void setAuditLog(McpAuditLog auditLog) { this.auditLog = auditLog; }

    /** Attach policy enforcement (called from McpApplication after construction). */
    public void setPolicyEnforcer(McpPolicyEnforcer policyEnforcer) { this.policyEnforcer = policyEnforcer; }

    /**
     * Handle a raw JSON-RPC request string (from POST body) and return the response JSON,
     * or {@code null} if the message is a notification that requires no response.
     *
     * @param body          raw JSON string
     * @param sessionId     session ID from the {@code Mcp-Session-Id} header, may be null
     * @param outSessionId  array of length 1; if a new session is created its ID is written here
     */
    public String handle(String body, String sessionId, String[] outSessionId) {
        return handle(body, sessionId, outSessionId, null);
    }

    /**
     * Handle with optional remote address for audit logging.
     */
    public String handle(String body, String sessionId, String[] outSessionId, String remoteAddr) {
        long startNanos = System.nanoTime();
        JsonRpcRequest request;
        try {
            request = JsonRpcRequest.parse(body);
        } catch (JsonParseException e) {
            auditFailure(null, sessionId, "parse-error", null, startNanos, JsonRpcError.PARSE_ERROR, e.getMessage(), remoteAddr);
            return JsonRpcResponse.error(null, JsonRpcError.parseError(e.getMessage()));
        } catch (IllegalArgumentException e) {
            auditFailure(null, sessionId, "invalid-request", null, startNanos, JsonRpcError.INVALID_REQUEST, e.getMessage(), remoteAddr);
            return JsonRpcResponse.error(null, JsonRpcError.invalidRequest(e.getMessage()));
        }

        // Notifications have no id — send no response
        if (request.isNotification()) {
            handleNotification(request, sessionId);
            return null;
        }

        try {
            String response = dispatch(request, sessionId, outSessionId);
            auditSuccess(request, sessionId, startNanos, remoteAddr);
            return response;
        } catch (Exception e) {
            log.error("Unhandled error dispatching method={}", request.method(), e);
            auditFailure(request, sessionId, request.method(), null, startNanos, JsonRpcError.INTERNAL_ERROR, e.getMessage(), remoteAddr);
            return JsonRpcResponse.error(request.id(), JsonRpcError.internalError(e.getMessage()));
        }
    }

    // ── Dispatch ─────────────────────────────────────────────────────────────

    private String dispatch(JsonRpcRequest req, String sessionId, String[] outSessionId) throws Exception {
        return switch (req.method()) {
            case "initialize"      -> handleInitialize(req, outSessionId);
            case "ping"            -> JsonRpcResponse.success(req.id(), "{}");
            case "tools/list"      -> handleToolsList(req);
            case "tools/call"      -> handleToolsCall(req);
            case "resources/list"  -> handleResourcesList(req);
            case "resources/read"  -> handleResourcesRead(req);
            case "prompts/list"    -> handlePromptsList(req);
            case "prompts/get"     -> handlePromptsGet(req);
            default                -> JsonRpcResponse.error(req.id(), JsonRpcError.methodNotFound(req.method()));
        };
    }

    // ── initialize ───────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String handleInitialize(JsonRpcRequest req, String[] outSessionId) {
        Map<String, Object> params = req.params() != null ? req.params() : Map.of();

        String clientName = "unknown";
        String clientVersion = "0.0.0";
        if (params.get("clientInfo") instanceof Map<?, ?> ci) {
            clientName = stringOrDefault(((Map<String, Object>) ci).get("name"), clientName);
            clientVersion = stringOrDefault(((Map<String, Object>) ci).get("version"), clientVersion);
        }
        String negotiatedProtocol = stringOrDefault(params.get("protocolVersion"), PROTOCOL_VERSION);

        // Policy: check blocked clients
        if (policyEnforcer != null) {
            JsonRpcError clientError = policyEnforcer.checkClientAllowed(clientName);
            if (clientError != null) {
                return JsonRpcResponse.error(req.id(), clientError);
            }
        }

        McpSession session = sessionManager.create(clientName, clientVersion, negotiatedProtocol);
        if (outSessionId != null && outSessionId.length > 0) {
            outSessionId[0] = session.id();
        }

        log.info("MCP initialize: client={}/{} protocol={} session={}", clientName, clientVersion,
                negotiatedProtocol, session.id());

        String result = McpJson.initializeResult(serverInfo, PROTOCOL_VERSION,
                !toolRegistry.list().isEmpty(),
                !resourceRegistry.list().isEmpty(),
                !promptRegistry.list().isEmpty());
        return JsonRpcResponse.success(req.id(), result);
    }

    // ── tools ─────────────────────────────────────────────────────────────────

    private String handleToolsList(JsonRpcRequest req) {
        List<McpTool> tools = toolRegistry.list();
        StringBuilder sb = new StringBuilder(1024).append("{\"tools\":[");
        boolean first = true;
        for (McpTool tool : tools) {
            if (!first) sb.append(',');
            first = false;
            sb.append(tool.toJson());
        }
        sb.append("]}");
        return JsonRpcResponse.success(req.id(), sb.toString());
    }

    @SuppressWarnings("unchecked")
    private String handleToolsCall(JsonRpcRequest req) throws Exception {
        Map<String, Object> params = req.params();
        if (params == null) {
            return JsonRpcResponse.error(req.id(), JsonRpcError.invalidParams("params required for tools/call"));
        }
        String name = stringOrDefault(params.get("name"), null);
        if (name == null || name.isBlank()) {
            return JsonRpcResponse.error(req.id(), JsonRpcError.invalidParams("name is required"));
        }
        // Policy: check tool blocked and rate limit
        if (policyEnforcer != null) {
            JsonRpcError policyError = policyEnforcer.checkToolCall(name, null);
            if (policyError != null) {
                return JsonRpcResponse.error(req.id(), policyError);
            }
        }

        McpToolExecutor executor = toolRegistry.executor(name);
        if (executor == null) {
            return JsonRpcResponse.error(req.id(), JsonRpcError.toolNotFound(name));
        }
        Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();
        try {
            McpToolCallResult result = executor.execute(arguments);
            return JsonRpcResponse.success(req.id(), result.toJson());
        } catch (Exception e) {
            log.warn("Tool '{}' execution failed: {}", name, e.getMessage(), e);
            return JsonRpcResponse.success(req.id(), McpToolCallResult.error(e.getMessage()).toJson());
        }
    }

    // ── resources ─────────────────────────────────────────────────────────────

    private String handleResourcesList(JsonRpcRequest req) {
        List<McpResource> resources = resourceRegistry.list();
        StringBuilder sb = new StringBuilder(512).append("{\"resources\":[");
        boolean first = true;
        for (McpResource resource : resources) {
            if (!first) sb.append(',');
            first = false;
            sb.append(resource.toJson());
        }
        sb.append("]}");
        return JsonRpcResponse.success(req.id(), sb.toString());
    }

    private String handleResourcesRead(JsonRpcRequest req) throws Exception {
        Map<String, Object> params = req.params();
        if (params == null) {
            return JsonRpcResponse.error(req.id(), JsonRpcError.invalidParams("params required for resources/read"));
        }
        String uri = stringOrDefault(params.get("uri"), null);
        if (uri == null || uri.isBlank()) {
            return JsonRpcResponse.error(req.id(), JsonRpcError.invalidParams("uri is required"));
        }
        McpResourceProvider provider = resourceRegistry.provider(uri);
        if (provider == null) {
            return JsonRpcResponse.error(req.id(), JsonRpcError.resourceNotFound(uri));
        }
        McpResourceContents contents = provider.read(uri);
        String result = "{\"contents\":[" + contents.toJson() + "]}";
        return JsonRpcResponse.success(req.id(), result);
    }

    // ── prompts ───────────────────────────────────────────────────────────────

    private String handlePromptsList(JsonRpcRequest req) {
        List<McpPrompt> prompts = promptRegistry.list();
        StringBuilder sb = new StringBuilder(512).append("{\"prompts\":[");
        boolean first = true;
        for (McpPrompt prompt : prompts) {
            if (!first) sb.append(',');
            first = false;
            sb.append(prompt.toJson());
        }
        sb.append("]}");
        return JsonRpcResponse.success(req.id(), sb.toString());
    }

    @SuppressWarnings("unchecked")
    private String handlePromptsGet(JsonRpcRequest req) throws Exception {
        Map<String, Object> params = req.params();
        if (params == null) {
            return JsonRpcResponse.error(req.id(), JsonRpcError.invalidParams("params required for prompts/get"));
        }
        String name = stringOrDefault(params.get("name"), null);
        if (name == null || name.isBlank()) {
            return JsonRpcResponse.error(req.id(), JsonRpcError.invalidParams("name is required"));
        }
        McpPromptProvider provider = promptRegistry.provider(name);
        if (provider == null) {
            return JsonRpcResponse.error(req.id(), JsonRpcError.promptNotFound(name));
        }
        // arguments is Map<String, String> per spec
        Map<String, String> arguments = Map.of();
        if (params.get("arguments") instanceof Map<?, ?> rawArgs) {
            Map<String, Object> rawMap = (Map<String, Object>) rawArgs;
            Map<String, String> stringArgs = new java.util.LinkedHashMap<>();
            rawMap.forEach((k, v) -> stringArgs.put(k, v != null ? v.toString() : ""));
            arguments = stringArgs;
        }
        McpPromptGetResult result = provider.get(arguments);
        return JsonRpcResponse.success(req.id(), result.toJson());
    }

    // ── Notifications (no response) ────────────────────────────────────────────

    private void handleNotification(JsonRpcRequest req, String sessionId) {
        switch (req.method()) {
            case "notifications/initialized" -> {
                McpSession session = sessionManager.get(sessionId);
                if (session != null) {
                    session.markInitialized();
                    log.debug("MCP session initialized id={}", sessionId);
                }
            }
            case "notifications/cancelled" -> log.debug("MCP client cancelled a request, sessionId={}", sessionId);
            default -> log.debug("Unhandled MCP notification method={}", req.method());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String stringOrDefault(Object value, String defaultValue) {
        if (value instanceof String s) return s;
        if (value != null) return value.toString();
        return defaultValue;
    }

    // ── Audit helpers ──────────────────────────────────────────────────────────

    private void auditSuccess(JsonRpcRequest req, String sessionId, long startNanos, String remoteAddr) {
        if (auditLog == null) return;
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        String toolName = extractToolName(req);
        McpSession session = sessionId != null ? sessionManager.get(sessionId) : null;
        String clientName = session != null ? session.clientName() : null;
        auditLog.recordSuccess(sessionId, clientName, req.method(), toolName, durationMs, remoteAddr);
    }

    private void auditFailure(JsonRpcRequest req, String sessionId, String method,
                              String toolName, long startNanos, int errorCode, String errorMsg, String remoteAddr) {
        if (auditLog == null) return;
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        McpSession session = sessionId != null ? sessionManager.get(sessionId) : null;
        String clientName = session != null ? session.clientName() : null;
        String resolvedMethod = req != null ? req.method() : method;
        String resolvedTool = toolName != null ? toolName : (req != null ? extractToolName(req) : null);
        auditLog.recordFailure(sessionId, clientName, resolvedMethod, resolvedTool, durationMs, errorCode, errorMsg, remoteAddr);
    }

    @SuppressWarnings("unchecked")
    private static String extractToolName(JsonRpcRequest req) {
        if (req.params() == null) return null;
        Object name = req.params().get("name");
        if (name instanceof String s) return s;
        Object uri = req.params().get("uri");
        if (uri instanceof String s) return s;
        return null;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public McpSessionManager sessionManager() { return sessionManager; }
    public McpToolRegistry toolRegistry() { return toolRegistry; }
    public McpResourceRegistry resourceRegistry() { return resourceRegistry; }
    public McpPromptRegistry promptRegistry() { return promptRegistry; }
    public McpAuditLog auditLog() { return auditLog; }
    public McpPolicyEnforcer policyEnforcer() { return policyEnforcer; }
}
