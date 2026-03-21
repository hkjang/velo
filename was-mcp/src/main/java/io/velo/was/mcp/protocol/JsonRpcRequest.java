package io.velo.was.mcp.protocol;

import java.util.List;
import java.util.Map;

/**
 * Represents a parsed JSON-RPC 2.0 request or notification.
 *
 * <p>If {@code id} is {@code null} the message is a notification and must not receive a response.
 */
public record JsonRpcRequest(
        /** Must be "2.0". */
        String jsonrpc,
        /**
         * Request identifier. May be a {@link String}, {@link Long}, or {@code null}
         * (null → notification, no response expected).
         */
        Object id,
        /** JSON-RPC method name, e.g. "tools/call". */
        String method,
        /**
         * Optional params object or array. For MCP this is always an object represented
         * as {@code Map<String, Object>}, or {@code null} when absent.
         */
        Map<String, Object> params
) {

    /** {@code true} when this message is a notification (no {@code id} field). */
    public boolean isNotification() {
        return id == null;
    }

    /**
     * Parse a JSON-RPC request from a raw JSON string.
     *
     * @throws JsonParseException   if the JSON is malformed
     * @throws IllegalArgumentException if required fields are missing
     */
    @SuppressWarnings("unchecked")
    public static JsonRpcRequest parse(String json) {
        Object parsed = SimpleJsonParser.parse(json);
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("JSON-RPC message must be a JSON object");
        }
        Map<String, Object> msg = (Map<String, Object>) root;

        String jsonrpc = (String) msg.get("jsonrpc");
        Object id = msg.get("id");       // may be absent (notification) or null
        String method = (String) msg.get("method");
        Object rawParams = msg.get("params");

        if (!"2.0".equals(jsonrpc)) {
            throw new IllegalArgumentException("jsonrpc field must be \"2.0\"");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("method field is required");
        }

        Map<String, Object> params = null;
        if (rawParams instanceof Map<?, ?> paramsMap) {
            params = (Map<String, Object>) paramsMap;
        } else if (rawParams instanceof List<?>) {
            // MCP does not use positional params arrays, but tolerate gracefully
            params = Map.of();
        }

        return new JsonRpcRequest(jsonrpc, id, method, params);
    }
}
