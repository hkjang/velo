package io.velo.was.aiplatform.acp;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * ACP 에이전트 카드 — 에이전트의 능력, 엔드포인트, 인증 방식을 광고하는 메타데이터.
 * {@code /.well-known/agent-card.json} 으로 발행되거나, AGP 레지스트리에 등록된다.
 */
public record AcpAgentCard(
        String agentId,
        String name,
        String description,
        String version,
        String provider,
        String endpoint,
        List<String> capabilities,
        List<String> modalities,
        List<String> protocols,
        String authScheme,
        Instant registeredAt,
        Map<String, String> metadata
) {

    /** JSON 직렬화. */
    public String toJson() {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"agentId\":\"").append(esc(agentId)).append("\"");
        sb.append(",\"name\":\"").append(esc(name)).append("\"");
        sb.append(",\"description\":\"").append(esc(description)).append("\"");
        sb.append(",\"version\":\"").append(esc(version)).append("\"");
        sb.append(",\"provider\":\"").append(esc(provider)).append("\"");
        sb.append(",\"endpoint\":\"").append(esc(endpoint)).append("\"");
        sb.append(",\"capabilities\":").append(listToJson(capabilities));
        sb.append(",\"modalities\":").append(listToJson(modalities));
        sb.append(",\"protocols\":").append(listToJson(protocols));
        sb.append(",\"authScheme\":\"").append(esc(authScheme)).append("\"");
        sb.append(",\"registeredAt\":\"").append(registeredAt != null ? registeredAt : "").append("\"");
        sb.append(",\"metadata\":{");
        if (metadata != null) {
            boolean first = true;
            for (var entry : metadata.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append("\"").append(esc(entry.getKey())).append("\":\"").append(esc(entry.getValue())).append("\"");
            }
        }
        sb.append("}}");
        return sb.toString();
    }

    private static String listToJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append("\"").append(esc(list.get(i))).append("\"");
        }
        sb.append(']');
        return sb.toString();
    }

    private static String esc(String s) {
        return AcpMessage.esc(s);
    }
}
