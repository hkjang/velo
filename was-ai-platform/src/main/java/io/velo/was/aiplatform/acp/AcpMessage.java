package io.velo.was.aiplatform.acp;

import java.util.List;
import java.util.Map;

/**
 * ACP 표준 메시지 — 에이전트 간 교환되는 멀티모달 메시지.
 *
 * @param role     메시지 역할: "user", "agent", "system"
 * @param parts    멀티모달 파트 목록 (텍스트, 이미지, JSON 등)
 * @param metadata 메타데이터 (선택)
 */
public record AcpMessage(String role, List<Part> parts, Map<String, String> metadata) {

    /**
     * 메시지의 개별 콘텐츠 파트.
     *
     * @param contentType MIME 타입 (text/plain, application/json, image/png 등)
     * @param text        텍스트 콘텐츠 (text 유형)
     * @param dataBase64  base64 인코딩 바이너리 (이미지, 오디오 등)
     */
    public record Part(String contentType, String text, String dataBase64) {

        /** 텍스트 파트 생성. */
        public static Part text(String text) {
            return new Part("text/plain", text, null);
        }

        /** JSON 파트 생성. */
        public static Part json(String json) {
            return new Part("application/json", json, null);
        }

        /** 바이너리 파트 생성. */
        public static Part binary(String contentType, String dataBase64) {
            return new Part(contentType, null, dataBase64);
        }
    }

    /** 단순 텍스트 메시지 생성. */
    public static AcpMessage text(String role, String text) {
        return new AcpMessage(role, List.of(Part.text(text)), Map.of());
    }

    /** 첫 번째 텍스트 파트 추출. */
    public String textContent() {
        if (parts == null) return "";
        return parts.stream()
                .filter(p -> p.text() != null && !p.text().isBlank())
                .map(Part::text)
                .findFirst()
                .orElse("");
    }

    /** JSON 직렬화. */
    public String toJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"role\":\"").append(esc(role)).append("\",\"parts\":[");
        if (parts != null) {
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) sb.append(',');
                Part p = parts.get(i);
                sb.append("{\"contentType\":\"").append(esc(p.contentType())).append("\"");
                if (p.text() != null) sb.append(",\"text\":\"").append(esc(p.text())).append("\"");
                if (p.dataBase64() != null) sb.append(",\"dataBase64\":\"").append(esc(p.dataBase64())).append("\"");
                sb.append('}');
            }
        }
        sb.append("],\"metadata\":{");
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

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
