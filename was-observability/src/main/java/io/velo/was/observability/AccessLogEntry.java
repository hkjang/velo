package io.velo.was.observability;

import java.time.Instant;

/**
 * Represents a single HTTP access log entry with all fields needed for structured logging.
 */
public record AccessLogEntry(
        Instant timestamp,
        String method,
        String uri,
        String protocol,
        int status,
        long contentLength,
        long durationMs,
        String remoteAddress,
        String userAgent,
        String referer,
        String requestId
) {
    /**
     * Renders this entry as a JSON string (no external library dependency).
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"timestamp\":\"").append(timestamp)
                .append("\",\"type\":\"access\"")
                .append(",\"method\":\"").append(method)
                .append("\",\"uri\":\"").append(escapeJson(uri))
                .append("\",\"protocol\":\"").append(protocol)
                .append("\",\"status\":").append(status)
                .append(",\"contentLength\":").append(contentLength)
                .append(",\"durationMs\":").append(durationMs)
                .append(",\"remoteAddress\":\"").append(remoteAddress != null ? remoteAddress : "")
                .append("\"");
        if (userAgent != null) {
            sb.append(",\"userAgent\":\"").append(escapeJson(userAgent)).append("\"");
        }
        if (referer != null) {
            sb.append(",\"referer\":\"").append(escapeJson(referer)).append("\"");
        }
        if (requestId != null) {
            sb.append(",\"requestId\":\"").append(requestId).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Renders this entry in Combined Log Format (CLF) for traditional access log compatibility.
     */
    public String toCombinedLogFormat() {
        return "%s - - [%s] \"%s %s %s\" %d %d \"%s\" \"%s\"".formatted(
                remoteAddress != null ? remoteAddress : "-",
                timestamp,
                method,
                uri,
                protocol,
                status,
                contentLength,
                referer != null ? referer : "-",
                userAgent != null ? userAgent : "-");
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
