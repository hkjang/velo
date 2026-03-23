package io.velo.was.mcp.admin;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.velo.was.http.HttpExchange;
import io.velo.was.http.HttpHandler;
import io.velo.was.http.HttpResponses;
import io.velo.was.mcp.McpServer;
import io.velo.was.mcp.audit.McpAuditEntry;
import io.velo.was.mcp.audit.McpAuditLog;
import io.velo.was.mcp.policy.McpPolicy;
import io.velo.was.mcp.policy.McpPolicyEnforcer;
import io.velo.was.mcp.session.McpSession;
import io.velo.was.mcp.prompt.McpPrompt;
import io.velo.was.mcp.prompt.McpPromptRegistry;
import io.velo.was.mcp.resource.McpResource;
import io.velo.was.mcp.resource.McpResourceRegistry;
import io.velo.was.mcp.session.McpSession;
import io.velo.was.mcp.session.McpSessionManager;
import io.velo.was.mcp.tool.McpTool;
import io.velo.was.mcp.tool.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP handler for the MCP admin control-plane API ({@code /ai-platform/mcp/admin/*}).
 *
 * <h3>Endpoints</h3>
 * <table>
 *   <tr><th>Path</th><th>Methods</th><th>Description</th></tr>
 *   <tr><td>/ai-platform/mcp/admin/servers</td><td>GET, POST</td><td>MCP server registration</td></tr>
 *   <tr><td>/ai-platform/mcp/admin/tools</td><td>GET, POST, PUT</td><td>Tool catalog management</td></tr>
 *   <tr><td>/ai-platform/mcp/admin/resources</td><td>GET, POST, PUT</td><td>Resource management</td></tr>
 *   <tr><td>/ai-platform/mcp/admin/prompts</td><td>GET, POST, PUT</td><td>Prompt management</td></tr>
 *   <tr><td>/ai-platform/mcp/admin/sessions</td><td>GET</td><td>Session/connection listing</td></tr>
 *   <tr><td>/ai-platform/mcp/admin/audit</td><td>GET</td><td>Audit log query</td></tr>
 *   <tr><td>/ai-platform/mcp/admin/policies</td><td>GET, PUT</td><td>Policy management</td></tr>
 * </table>
 */
public class McpAdminHandler implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(McpAdminHandler.class);

    private static final String PREFIX = "/ai-platform/mcp/admin/";

    private final McpServer server;
    private final McpServerRegistry serverRegistry;
    private final McpAuditLog auditLog;
    private final McpPolicyEnforcer policyEnforcer;

    public McpAdminHandler(McpServer server,
                           McpServerRegistry serverRegistry,
                           McpAuditLog auditLog,
                           McpPolicyEnforcer policyEnforcer) {
        this.server = server;
        this.serverRegistry = serverRegistry;
        this.auditLog = auditLog;
        this.policyEnforcer = policyEnforcer;
    }

    @Override
    public FullHttpResponse handle(HttpExchange exchange) {
        FullHttpRequest request = exchange.request();
        String path = exchange.path();
        HttpMethod method = request.method();

        // Strip the /mcp/admin/ prefix to get the sub-resource
        String sub = path.startsWith(PREFIX) ? path.substring(PREFIX.length()) : "";
        // Remove trailing slash
        if (sub.endsWith("/")) sub = sub.substring(0, sub.length() - 1);

        try {
            return switch (sub) {
                case "servers"   -> handleServers(method, request);
                case "tools"     -> handleTools(method, request);
                case "resources" -> handleResources(method, request);
                case "prompts"   -> handlePrompts(method, request);
                case "sessions"  -> handleSessions(method);
                case "audit"     -> handleAudit(method, request);
                case "policies"  -> handlePolicies(method, request);
                default          -> HttpResponses.notFound("Unknown admin endpoint: " + sub);
            };
        } catch (Exception e) {
            log.error("Admin API error: {} {}", method, path, e);
            return HttpResponses.serverError("Admin error: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  /ai-platform/mcp/admin/servers — GET, POST
    // ═══════════════════════════════════════════════════════════════════════

    private FullHttpResponse handleServers(HttpMethod method, FullHttpRequest request) {
        if (method == HttpMethod.GET) {
            List<McpServerDescriptor> servers = serverRegistry.list();
            StringBuilder sb = new StringBuilder(1024).append("{\"servers\":[");
            appendJsonList(sb, servers, McpServerDescriptor::toJson);
            sb.append("]}");
            return HttpResponses.jsonOk(sb.toString());
        }
        if (method == HttpMethod.POST) {
            Map<String, Object> body = parseBody(request);
            String name = stringVal(body, "name", "unnamed");
            String endpoint = stringVal(body, "endpoint", "");
            String environment = stringVal(body, "environment", "remote");
            String version = stringVal(body, "version", "unknown");
            McpServerDescriptor desc = serverRegistry.register(name, endpoint, environment, version);
            log.info("Admin: registered MCP server id={} name={} endpoint={}", desc.id(), name, endpoint);
            return HttpResponses.jsonOk(desc.toJson());
        }
        return HttpResponses.methodNotAllowed("GET or POST only");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  /ai-platform/mcp/admin/tools — GET, POST, PUT
    // ═══════════════════════════════════════════════════════════════════════

    private FullHttpResponse handleTools(HttpMethod method, FullHttpRequest request) {
        McpToolRegistry tools = server.toolRegistry();
        if (method == HttpMethod.GET) {
            List<McpTool> list = tools.list();
            StringBuilder sb = new StringBuilder(1024).append("{\"tools\":[");
            appendJsonList(sb, list, McpTool::toJson);
            sb.append("],\"count\":").append(list.size()).append('}');
            return HttpResponses.jsonOk(sb.toString());
        }
        if (method == HttpMethod.PUT) {
            // PUT toggles a tool's enabled status (block/unblock via policy)
            Map<String, Object> body = parseBody(request);
            String name = stringVal(body, "name", null);
            String action = stringVal(body, "action", ""); // "block" or "unblock"
            if (name == null) return HttpResponses.badRequest("name is required");

            McpPolicy policy = policyEnforcer.policy();
            List<String> blocked = new ArrayList<>(policy.getBlockedTools());
            if ("block".equalsIgnoreCase(action)) {
                if (!blocked.contains(name)) blocked.add(name);
                policy.setBlockedTools(blocked);
                log.info("Admin: blocked tool '{}'", name);
                return HttpResponses.jsonOk("{\"status\":\"blocked\",\"tool\":\"" + escape(name) + "\"}");
            } else if ("unblock".equalsIgnoreCase(action)) {
                blocked.remove(name);
                policy.setBlockedTools(blocked);
                log.info("Admin: unblocked tool '{}'", name);
                return HttpResponses.jsonOk("{\"status\":\"unblocked\",\"tool\":\"" + escape(name) + "\"}");
            }
            return HttpResponses.badRequest("action must be 'block' or 'unblock'");
        }
        // POST reserved for dynamic tool registration (Phase 3)
        if (method == HttpMethod.POST) {
            return HttpResponses.jsonOk("{\"status\":\"not_implemented\","
                    + "\"message\":\"Dynamic tool registration is planned for Phase 3\"}");
        }
        return HttpResponses.methodNotAllowed("GET, POST, or PUT only");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  /ai-platform/mcp/admin/resources — GET, POST, PUT
    // ═══════════════════════════════════════════════════════════════════════

    private FullHttpResponse handleResources(HttpMethod method, FullHttpRequest request) {
        McpResourceRegistry resources = server.resourceRegistry();
        if (method == HttpMethod.GET) {
            List<McpResource> list = resources.list();
            StringBuilder sb = new StringBuilder(512).append("{\"resources\":[");
            appendJsonList(sb, list, McpResource::toJson);
            sb.append("],\"count\":").append(list.size()).append('}');
            return HttpResponses.jsonOk(sb.toString());
        }
        if (method == HttpMethod.POST || method == HttpMethod.PUT) {
            return HttpResponses.jsonOk("{\"status\":\"not_implemented\","
                    + "\"message\":\"Dynamic resource management is planned for Phase 3\"}");
        }
        return HttpResponses.methodNotAllowed("GET, POST, or PUT only");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  /ai-platform/mcp/admin/prompts — GET, POST, PUT
    // ═══════════════════════════════════════════════════════════════════════

    private FullHttpResponse handlePrompts(HttpMethod method, FullHttpRequest request) {
        McpPromptRegistry prompts = server.promptRegistry();
        if (method == HttpMethod.GET) {
            List<McpPrompt> list = prompts.list();
            StringBuilder sb = new StringBuilder(512).append("{\"prompts\":[");
            appendJsonList(sb, list, McpPrompt::toJson);
            sb.append("],\"count\":").append(list.size()).append('}');
            return HttpResponses.jsonOk(sb.toString());
        }
        if (method == HttpMethod.POST || method == HttpMethod.PUT) {
            return HttpResponses.jsonOk("{\"status\":\"not_implemented\","
                    + "\"message\":\"Dynamic prompt management is planned for Phase 3\"}");
        }
        return HttpResponses.methodNotAllowed("GET, POST, or PUT only");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  /ai-platform/mcp/admin/sessions — GET
    // ═══════════════════════════════════════════════════════════════════════

    private FullHttpResponse handleSessions(HttpMethod method) {
        if (method != HttpMethod.GET) {
            return HttpResponses.methodNotAllowed("GET only");
        }
        McpSessionManager sm = server.sessionManager();
        java.util.List<McpSession> sessions = sm.list();
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{\"activeSessions\":").append(sessions.size()).append(",\"sessions\":[");
        boolean first = true;
        for (McpSession s : sessions) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"id\":\"").append(escape(s.id())).append('"')
              .append(",\"clientName\":\"").append(escape(s.clientName())).append('"')
              .append(",\"clientVersion\":\"").append(escape(s.clientVersion())).append('"')
              .append(",\"protocolVersion\":\"").append(escape(s.protocolVersion())).append('"')
              .append(",\"initialized\":").append(s.isInitialized())
              .append(",\"hasSseConnection\":").append(s.hasSseConnection())
              .append(",\"createdAt\":\"").append(s.createdAt()).append('"')
              .append(",\"lastActivityAt\":\"").append(s.lastActivityAt()).append('"')
              .append('}');
        }
        sb.append("]}");
        return HttpResponses.jsonOk(sb.toString());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  /ai-platform/mcp/admin/audit — GET
    // ═══════════════════════════════════════════════════════════════════════

    private FullHttpResponse handleAudit(HttpMethod method, FullHttpRequest request) {
        if (method != HttpMethod.GET) {
            return HttpResponses.methodNotAllowed("GET only");
        }
        // Query params: ?limit=50&method=tools/call
        Map<String, List<String>> params = new io.netty.handler.codec.http.QueryStringDecoder(
                request.uri()).parameters();
        int limit = intParam(params, "limit", 50);
        String filterMethod = firstParam(params, "method");

        List<McpAuditEntry> entries = auditLog.query(limit, filterMethod);
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\"total\":").append(auditLog.size());
        sb.append(",\"returned\":").append(entries.size());
        sb.append(",\"entries\":[");
        boolean first = true;
        for (McpAuditEntry entry : entries) {
            if (!first) sb.append(',');
            first = false;
            sb.append(entry.toJson());
        }
        sb.append("]}");
        return HttpResponses.jsonOk(sb.toString());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  /ai-platform/mcp/admin/policies — GET, PUT
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private FullHttpResponse handlePolicies(HttpMethod method, FullHttpRequest request) {
        McpPolicy policy = policyEnforcer.policy();
        if (method == HttpMethod.GET) {
            return HttpResponses.jsonOk(policy.toJson());
        }
        if (method == HttpMethod.PUT) {
            Map<String, Object> body = parseBody(request);
            if (body.containsKey("authRequired"))
                policy.setAuthRequired(Boolean.TRUE.equals(body.get("authRequired")));
            if (body.containsKey("apiKeyHeader"))
                policy.setApiKeyHeader(body.get("apiKeyHeader").toString());
            if (body.containsKey("rateLimitPerMinute"))
                policy.setRateLimitPerMinute(intVal(body, "rateLimitPerMinute", 0));
            if (body.containsKey("maxConcurrentToolCalls"))
                policy.setMaxConcurrentToolCalls(intVal(body, "maxConcurrentToolCalls", 0));
            if (body.containsKey("blockedTools"))
                policy.setBlockedTools(toStringList(body.get("blockedTools")));
            if (body.containsKey("blockedPromptPatterns"))
                policy.setBlockedPromptPatterns(toStringList(body.get("blockedPromptPatterns")));
            if (body.containsKey("blockedClients"))
                policy.setBlockedClients(toStringList(body.get("blockedClients")));
            if (body.containsKey("dataMaskingPatterns"))
                policy.setDataMaskingPatterns(toStringList(body.get("dataMaskingPatterns")));

            log.info("Admin: policies updated");
            return HttpResponses.jsonOk(policy.toJson());
        }
        return HttpResponses.methodNotAllowed("GET or PUT only");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(FullHttpRequest request) {
        String body = request.content().toString(StandardCharsets.UTF_8).trim();
        if (body.isEmpty()) return Map.of();
        try {
            Object parsed = io.velo.was.mcp.protocol.SimpleJsonParser.parse(body);
            if (parsed instanceof Map<?, ?> m) return (Map<String, Object>) m;
        } catch (Exception e) {
            log.debug("Failed to parse admin request body: {}", e.getMessage());
        }
        return Map.of();
    }

    private static String stringVal(Map<String, Object> map, String key, String defaultValue) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    private static int intVal(Map<String, Object> map, String key, int defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) result.add(item.toString());
            }
            return result;
        }
        return List.of();
    }

    private static String firstParam(Map<String, List<String>> params, String key) {
        List<String> values = params.get(key);
        return (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    private static int intParam(Map<String, List<String>> params, String key, int defaultValue) {
        String v = firstParam(params, key);
        if (v != null) {
            try { return Integer.parseInt(v); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private static <T> void appendJsonList(StringBuilder sb, List<T> list,
                                           java.util.function.Function<T, String> toJson) {
        boolean first = true;
        for (T item : list) {
            if (!first) sb.append(',');
            first = false;
            sb.append(toJson.apply(item));
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
