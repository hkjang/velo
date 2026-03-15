package io.velo.was.webadmin.audit;

import java.time.Instant;

/**
 * Immutable audit event record.
 * Captures who did what, when, to which resource, and whether it succeeded.
 */
public record AuditEvent(
        String id,
        Instant timestamp,
        String user,
        String action,
        String resource,
        String detail,
        String sourceIp,
        boolean success
) {

    public String toJson() {
        return """
                {"id":"%s","timestamp":"%s","user":"%s","action":"%s",\
                "resource":"%s","detail":"%s","sourceIp":"%s","success":%s}""".formatted(
                esc(id), timestamp.toString(), esc(user), esc(action),
                esc(resource), esc(detail), esc(sourceIp), success
        );
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
