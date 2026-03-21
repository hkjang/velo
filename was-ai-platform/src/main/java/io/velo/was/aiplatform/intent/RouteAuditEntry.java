package io.velo.was.aiplatform.intent;

/**
 * 라우팅 감사 로그 엔트리.
 */
public record RouteAuditEntry(
        String requestId,
        String tenantId,
        String prompt,
        String normalizedText,
        IntentType resolvedIntent,
        String matchedKeyword,
        String policyId,
        String routeTarget,
        String modelName,
        boolean usedFallback,
        long processingTimeNanos,
        long responseTimeMs,
        long timestamp
) {
}
