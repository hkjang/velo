package io.velo.was.observability;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a structured audit log entry for administrative operations.
 * Captures deployment, configuration changes, server lifecycle events, etc.
 */
public record AuditLogEntry(
        Instant timestamp,
        String action,
        String actor,
        String target,
        String result,
        Map<String, String> details
) {
    public static AuditLogEntry of(String action, String actor, String target, String result) {
        return new AuditLogEntry(Instant.now(), action, actor, target, result, Map.of());
    }

    public static AuditLogEntry of(String action, String actor, String target, String result, Map<String, String> details) {
        return new AuditLogEntry(Instant.now(), action, actor, target, result, details);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"timestamp\":\"").append(timestamp)
                .append("\",\"type\":\"audit\"")
                .append(",\"action\":\"").append(escapeJson(action))
                .append("\",\"actor\":\"").append(escapeJson(actor))
                .append("\",\"target\":\"").append(escapeJson(target))
                .append("\",\"result\":\"").append(escapeJson(result)).append("\"");
        if (details != null && !details.isEmpty()) {
            sb.append(",\"details\":{");
            boolean first = true;
            for (Map.Entry<String, String> entry : details.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey()))
                        .append("\":\"").append(escapeJson(entry.getValue())).append("\"");
                first = false;
            }
            sb.append("}");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
