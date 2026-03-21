package io.velo.was.mcp.protocol;

/**
 * Builds JSON-RPC 2.0 response JSON strings.
 *
 * <p>Exactly one of {@code result} or {@code error} must be present.
 */
public final class JsonRpcResponse {

    private JsonRpcResponse() {}

    /**
     * Success response: {@code {"jsonrpc":"2.0","id":<id>,"result":<resultJson>}}.
     *
     * @param id         request id (String, Long, or null for notifications — though
     *                   notifications must not receive responses per spec)
     * @param resultJson pre-serialized JSON for the result field
     */
    public static String success(Object id, String resultJson) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + serializeId(id) + ",\"result\":" + resultJson + "}";
    }

    /**
     * Error response: {@code {"jsonrpc":"2.0","id":<id>,"error":{...}}}.
     */
    public static String error(Object id, JsonRpcError error) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + serializeId(id) + ",\"error\":" + error.toJson() + "}";
    }

    /**
     * Server notification (no id): {@code {"jsonrpc":"2.0","method":"...","params":{...}}}.
     */
    public static String notification(String method, String paramsJson) {
        if (paramsJson == null) {
            return "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\"}";
        }
        return "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"params\":" + paramsJson + "}";
    }

    private static String serializeId(Object id) {
        if (id == null) return "null";
        if (id instanceof String s) return "\"" + s.replace("\"", "\\\"") + "\"";
        return id.toString(); // Long or other number
    }
}
