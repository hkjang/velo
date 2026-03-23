package io.velo.was.aiplatform.acp;

import java.time.Instant;

/**
 * ACP/AGP 전용 감사 엔트리.
 */
public record AcpAuditEntry(
        Instant timestamp,
        String eventType,
        String taskId,
        String fromAgent,
        String toAgent,
        String capability,
        String state,
        long durationMs,
        boolean success,
        String errorMsg,
        String remoteAddr
) {

    /** JSON 직렬화. */
    public String toJson() {
        return "{\"timestamp\":\"" + timestamp + "\""
                + ",\"eventType\":\"" + AcpMessage.esc(eventType) + "\""
                + ",\"taskId\":" + jsonStr(taskId)
                + ",\"fromAgent\":" + jsonStr(fromAgent)
                + ",\"toAgent\":" + jsonStr(toAgent)
                + ",\"capability\":" + jsonStr(capability)
                + ",\"state\":" + jsonStr(state)
                + ",\"durationMs\":" + durationMs
                + ",\"success\":" + success
                + ",\"errorMsg\":" + jsonStr(errorMsg)
                + ",\"remoteAddr\":" + jsonStr(remoteAddr)
                + "}";
    }

    private static String jsonStr(String v) {
        return v == null ? "null" : "\"" + AcpMessage.esc(v) + "\"";
    }
}
