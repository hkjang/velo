package io.velo.was.mcp.protocol;

/**
 * JSON-RPC 2.0 standard error codes and error object builder.
 *
 * <p>Standard codes defined by the JSON-RPC 2.0 specification:
 * <pre>
 *  -32700  Parse error
 *  -32600  Invalid Request
 *  -32601  Method not found
 *  -32602  Invalid params
 *  -32603  Internal error
 * </pre>
 *
 * <p>MCP-reserved server-error range: -32099 to -32000.
 */
public final class JsonRpcError {

    // Standard JSON-RPC error codes
    public static final int PARSE_ERROR      = -32700;
    public static final int INVALID_REQUEST  = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS   = -32602;
    public static final int INTERNAL_ERROR   = -32603;

    // MCP-specific server errors
    public static final int TOOL_NOT_FOUND        = -32001;
    public static final int RESOURCE_NOT_FOUND    = -32002;
    public static final int PROMPT_NOT_FOUND      = -32003;
    public static final int QUOTA_EXCEEDED        = -32010;
    public static final int MODEL_UNAVAILABLE     = -32011;
    public static final int UNSAFE_PROMPT_BLOCKED = -32012;

    private final int code;
    private final String message;
    private final String data; // optional, JSON string or null

    public JsonRpcError(int code, String message) {
        this(code, message, null);
    }

    public JsonRpcError(int code, String message, String data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public int code() { return code; }
    public String message() { return message; }
    public String data() { return data; }

    /** Serialize to the JSON error object used inside a JSON-RPC response. */
    public String toJson() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"code\":").append(code)
          .append(",\"message\":\"").append(escape(message)).append('"');
        if (data != null) {
            sb.append(",\"data\":\"").append(escape(data)).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    // ── Factory shortcuts ────────────────────────────────────────────────────

    public static JsonRpcError parseError(String detail) {
        return new JsonRpcError(PARSE_ERROR, "Parse error", detail);
    }

    public static JsonRpcError invalidRequest(String detail) {
        return new JsonRpcError(INVALID_REQUEST, "Invalid Request", detail);
    }

    public static JsonRpcError methodNotFound(String method) {
        return new JsonRpcError(METHOD_NOT_FOUND, "Method not found: " + method);
    }

    public static JsonRpcError invalidParams(String detail) {
        return new JsonRpcError(INVALID_PARAMS, "Invalid params", detail);
    }

    public static JsonRpcError internalError(String detail) {
        return new JsonRpcError(INTERNAL_ERROR, "Internal error", detail);
    }

    public static JsonRpcError toolNotFound(String name) {
        return new JsonRpcError(TOOL_NOT_FOUND, "Tool not found: " + name);
    }

    public static JsonRpcError resourceNotFound(String uri) {
        return new JsonRpcError(RESOURCE_NOT_FOUND, "Resource not found: " + uri);
    }

    public static JsonRpcError promptNotFound(String name) {
        return new JsonRpcError(PROMPT_NOT_FOUND, "Prompt not found: " + name);
    }
}
