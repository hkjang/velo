package io.velo.was.mcp.gateway;

import io.velo.was.mcp.protocol.SimpleJsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP client for communicating with a remote MCP server over JSON-RPC 2.0.
 *
 * <p>Uses {@link java.net.http.HttpClient} (Java 21, no external deps).
 * Handles the initialize handshake, capability discovery, tool/resource/prompt
 * proxy calls, and health checking.
 */
public class McpRemoteClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpRemoteClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final String serverId;
    private final String endpoint;
    private final HttpClient httpClient;
    private final AtomicLong requestIdSeq = new AtomicLong(1);
    private volatile String remoteSessionId;

    public McpRemoteClient(String serverId, String endpoint) {
        this.serverId = serverId;
        this.endpoint = endpoint;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /** Perform the MCP initialize handshake with the remote server. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> initialize() throws Exception {
        String params = "{\"protocolVersion\":\"" + PROTOCOL_VERSION + "\","
                + "\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"velo-gateway\",\"version\":\"1.0\"}}";
        RpcResult result = sendJsonRpcRaw("initialize", params);
        if (result.error != null) {
            throw new RuntimeException("Remote initialize failed: " + result.error);
        }
        // Send notifications/initialized (notification — no id, no response expected)
        String notifyBody = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        HttpRequest notifyReq = buildPostRequest(notifyBody);
        httpClient.send(notifyReq, HttpResponse.BodyHandlers.discarding());

        log.info("Remote MCP server initialized: serverId={} endpoint={} sessionId={}",
                serverId, endpoint, remoteSessionId);
        return result.resultMap;
    }

    /** Send a JSON-RPC request and return the parsed result. */
    @SuppressWarnings("unchecked")
    public String sendJsonRpc(String method, String paramsJson) throws Exception {
        RpcResult result = sendJsonRpcRaw(method, paramsJson);
        if (result.error != null) {
            return result.error; // raw error JSON
        }
        return result.rawResult;
    }

    /** List tools from remote server. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listTools() throws Exception {
        RpcResult result = sendJsonRpcRaw("tools/list", null);
        if (result.resultMap != null && result.resultMap.get("tools") instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    /** List resources from remote server. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listResources() throws Exception {
        RpcResult result = sendJsonRpcRaw("resources/list", null);
        if (result.resultMap != null && result.resultMap.get("resources") instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    /** List prompts from remote server. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listPrompts() throws Exception {
        RpcResult result = sendJsonRpcRaw("prompts/list", null);
        if (result.resultMap != null && result.resultMap.get("prompts") instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    /** Call a tool on the remote server. Returns raw result JSON. */
    public String callTool(String name, Map<String, Object> arguments) throws Exception {
        String paramsJson = "{\"name\":\"" + escape(name) + "\",\"arguments\":" + mapToJson(arguments) + "}";
        return sendJsonRpc("tools/call", paramsJson);
    }

    /** Read a resource from the remote server. Returns raw result JSON. */
    public String readResource(String uri) throws Exception {
        String paramsJson = "{\"uri\":\"" + escape(uri) + "\"}";
        return sendJsonRpc("resources/read", paramsJson);
    }

    /** Get a prompt from the remote server. Returns raw result JSON. */
    public String getPrompt(String name, Map<String, String> arguments) throws Exception {
        StringBuilder argsJson = new StringBuilder("{");
        boolean first = true;
        for (var entry : arguments.entrySet()) {
            if (!first) argsJson.append(',');
            first = false;
            argsJson.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
        }
        argsJson.append('}');
        String paramsJson = "{\"name\":\"" + escape(name) + "\",\"arguments\":" + argsJson + "}";
        return sendJsonRpc("prompts/get", paramsJson);
    }

    /** Health check via ping. */
    public boolean ping() {
        try {
            RpcResult result = sendJsonRpcRaw("ping", null);
            return result.error == null;
        } catch (Exception e) {
            log.debug("Ping failed for remote server {}: {}", serverId, e.getMessage());
            return false;
        }
    }

    public String remoteSessionId() { return remoteSessionId; }

    @Override
    public void close() {
        // HttpClient doesn't need explicit close in Java 21
        remoteSessionId = null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Internal
    // ═══════════════════════════════════════════════════════════════════════

    private record RpcResult(Map<String, Object> resultMap, String rawResult, String error) {}

    @SuppressWarnings("unchecked")
    private RpcResult sendJsonRpcRaw(String method, String paramsJson) throws Exception {
        long id = requestIdSeq.getAndIncrement();
        StringBuilder body = new StringBuilder(256);
        body.append("{\"jsonrpc\":\"2.0\",\"id\":").append(id)
                .append(",\"method\":\"").append(method).append('"');
        if (paramsJson != null) {
            body.append(",\"params\":").append(paramsJson);
        }
        body.append('}');

        HttpRequest request = buildPostRequest(body.toString());
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Capture session ID from response header
        response.headers().firstValue("Mcp-Session-Id").ifPresent(sid -> this.remoteSessionId = sid);

        if (response.statusCode() == 204) {
            return new RpcResult(Map.of(), "{}", null);
        }

        String responseBody = response.body();
        if (responseBody == null || responseBody.isBlank()) {
            return new RpcResult(Map.of(), "{}", null);
        }

        Object parsed = SimpleJsonParser.parse(responseBody);
        if (parsed instanceof Map<?, ?> rpcResponse) {
            Map<String, Object> rpcMap = (Map<String, Object>) rpcResponse;
            if (rpcMap.containsKey("error")) {
                return new RpcResult(null, null, responseBody);
            }
            Object result = rpcMap.get("result");
            if (result instanceof Map<?, ?> m) {
                // Re-serialize result to JSON for raw result
                return new RpcResult((Map<String, Object>) m, toJsonString(result), null);
            }
            return new RpcResult(Map.of(), toJsonString(result), null);
        }
        return new RpcResult(null, null, responseBody);
    }

    private HttpRequest buildPostRequest(String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (remoteSessionId != null) {
            builder.header("Mcp-Session-Id", remoteSessionId);
        }
        return builder.build();
    }

    // ── JSON helpers ─────────────────────────────────────────────────────

    private static String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(entry.getKey())).append("\":");
            sb.append(valueToJson(entry.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static String valueToJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + escape(s) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> m) return mapToJson((Map<String, Object>) m);
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                sb.append(valueToJson(item));
            }
            sb.append(']');
            return sb.toString();
        }
        return "\"" + escape(value.toString()) + "\"";
    }

    private static String toJsonString(Object value) {
        return valueToJson(value);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
