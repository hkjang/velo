package io.velo.was.mcp.audit;

import java.time.Instant;

/**
 * Immutable snapshot of a single MCP audit event.
 *
 * <p>Captures who called what, when, and whether it succeeded.
 *
 * @param timestamp  when the call occurred
 * @param sessionId  MCP session ID (may be null for unauthenticated calls)
 * @param clientName name provided during {@code initialize}
 * @param method     JSON-RPC method, e.g. "tools/call"
 * @param toolName   tool/resource/prompt name if applicable, else null
 * @param durationMs wall-clock duration of the call in milliseconds
 * @param success    true if the call completed without error
 * @param errorCode  JSON-RPC error code if failed, else 0
 * @param errorMsg   error message summary if failed, else null
 * @param remoteAddr client IP/address string
 */
public record McpAuditEntry(
        Instant timestamp,
        String sessionId,
        String clientName,
        String method,
        String toolName,
        long durationMs,
        boolean success,
        int errorCode,
        String errorMsg,
        String remoteAddr
) {

    /** Serialize to a JSON string for the admin audit API response. */
    public String toJson() {
        return "{\"timestamp\":\"" + timestamp + "\""
                + ",\"sessionId\":" + jsonStr(sessionId)
                + ",\"clientName\":" + jsonStr(clientName)
                + ",\"method\":\"" + escape(method) + "\""
                + ",\"toolName\":" + jsonStr(toolName)
                + ",\"durationMs\":" + durationMs
                + ",\"success\":" + success
                + ",\"errorCode\":" + errorCode
                + ",\"errorMsg\":" + jsonStr(errorMsg)
                + ",\"remoteAddr\":" + jsonStr(remoteAddr)
                + "}";
    }

    private static String jsonStr(String v) {
        return v == null ? "null" : "\"" + escape(v) + "\"";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
