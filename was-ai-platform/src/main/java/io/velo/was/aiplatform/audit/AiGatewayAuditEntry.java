package io.velo.was.aiplatform.audit;

import java.time.Instant;

/**
 * AI 게이트웨이 감사 이벤트의 불변 스냅샷.
 *
 * <p>누가 어떤 모델에 어떤 프롬프트를 요청했는지, 결과가 어떠했는지를 기록한다.
 *
 * @param timestamp       요청 시각
 * @param requestId       고유 요청 ID
 * @param tenantId        테넌트 ID (미인증 시 null)
 * @param endpoint        요청 엔드포인트 (gateway/infer, v1/chat/completions 등)
 * @param modelName       선택/라우팅된 모델명
 * @param provider        프로바이더 (openai, anthropic, ollama, vllm 등)
 * @param requestType     요청 유형 (CHAT, VISION, AUTO 등)
 * @param prompt          요청 프롬프트 (최대 1000자)
 * @param durationMs      처리 시간 (밀리초)
 * @param estimatedTokens 추정 토큰 수
 * @param success         성공 여부
 * @param streaming       스트리밍 요청 여부
 * @param errorMsg        에러 메시지 (실패 시, 성공 시 null)
 * @param remoteAddr      클라이언트 IP/주소
 * @param routePolicy     적용된 라우팅 정책
 * @param intentType      감지된 의도 (SUMMARIZATION, GENERATION 등)
 * @param modality        요청 모달리티 (text, vision, image_gen, stt, tts, embedding)
 */
public record AiGatewayAuditEntry(
        Instant timestamp,
        String requestId,
        String tenantId,
        String endpoint,
        String modelName,
        String provider,
        String requestType,
        String prompt,
        long durationMs,
        int estimatedTokens,
        boolean success,
        boolean streaming,
        String errorMsg,
        String remoteAddr,
        String routePolicy,
        String intentType,
        String modality
) {

    /** JSON 문자열로 직렬화 (admin API 및 파일 로거용). */
    public String toJson() {
        return "{\"timestamp\":\"" + timestamp + "\""
                + ",\"requestId\":" + jsonStr(requestId)
                + ",\"tenantId\":" + jsonStr(tenantId)
                + ",\"endpoint\":" + jsonStr(endpoint)
                + ",\"modelName\":" + jsonStr(modelName)
                + ",\"provider\":" + jsonStr(provider)
                + ",\"requestType\":" + jsonStr(requestType)
                + ",\"prompt\":" + jsonStr(prompt)
                + ",\"durationMs\":" + durationMs
                + ",\"estimatedTokens\":" + estimatedTokens
                + ",\"success\":" + success
                + ",\"streaming\":" + streaming
                + ",\"errorMsg\":" + jsonStr(errorMsg)
                + ",\"remoteAddr\":" + jsonStr(remoteAddr)
                + ",\"routePolicy\":" + jsonStr(routePolicy)
                + ",\"intentType\":" + jsonStr(intentType)
                + ",\"modality\":" + jsonStr(modality)
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
