package io.velo.was.aiplatform.intent;

import java.util.List;

/**
 * 의도 기반 라우팅 최종 결정 결과.
 */
public record IntentRouteDecision(
        IntentType resolvedIntent,
        String matchedKeyword,
        String policyId,
        String routeTarget,
        String modelName,
        String fallbackModel,
        boolean streamingPreferred,
        int priority,
        String reasoning,
        List<String> candidateKeywords,
        long processingTimeNanos
) {
    public static IntentRouteDecision defaultRoute(String defaultModel, long nanos) {
        return new IntentRouteDecision(
                IntentType.GENERAL,
                null,
                "default",
                "default",
                defaultModel,
                defaultModel,
                false,
                0,
                "키워드 미매칭 — 기본 라우팅 적용",
                List.of(),
                nanos
        );
    }
}
