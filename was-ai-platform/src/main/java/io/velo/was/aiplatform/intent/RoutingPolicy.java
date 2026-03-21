package io.velo.was.aiplatform.intent;

/**
 * 의도별 라우팅 정책 정의.
 * 키워드 매칭 결과에 따라 어떤 모델/엔드포인트로 라우팅할지 결정하는 규칙.
 */
public record RoutingPolicy(
        String policyId,
        IntentType intent,
        int priority,
        String routeTarget,
        String modelName,
        String fallbackModel,
        boolean streamingPreferred,
        boolean enabled,
        String tenantOverride,
        int maxInputTokens,
        long createdAt
) {
    public RoutingPolicy {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId is required");
        }
        if (intent == null) {
            intent = IntentType.GENERAL;
        }
        if (routeTarget == null || routeTarget.isBlank()) {
            routeTarget = "default";
        }
        if (modelName == null || modelName.isBlank()) {
            modelName = "llm-general";
        }
        if (fallbackModel == null || fallbackModel.isBlank()) {
            fallbackModel = modelName;
        }
        if (maxInputTokens <= 0) {
            maxInputTokens = Integer.MAX_VALUE;
        }
    }

    /**
     * 이 정책이 주어진 조건에 적용 가능한지 판단.
     */
    public boolean isApplicable(IntentType resolvedIntent, String tenantId, int inputLength) {
        if (!enabled) {
            return false;
        }
        if (intent != resolvedIntent) {
            return false;
        }
        if (tenantOverride != null && !tenantOverride.isBlank() && !tenantOverride.equals(tenantId)) {
            return false;
        }
        return inputLength <= maxInputTokens;
    }
}
