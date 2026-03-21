package io.velo.was.aiplatform.intent;

import java.util.List;

/**
 * 의도 기반 라우팅 최종 결정 엔진.
 *
 * 처리 흐름:
 * 1. RequestNormalizer로 텍스트 정규화
 * 2. KeywordMatcher로 키워드 탐지
 * 3. IntentResolver로 의도 결정
 * 4. IntentRoutingPolicyService에서 정책 조회
 * 5. 최종 라우팅 결정 생성
 */
public class RouteDecisionEngine {

    private final IntentRoutingPolicyService policyService;
    private final String defaultModel;

    public RouteDecisionEngine(IntentRoutingPolicyService policyService, String defaultModel) {
        this.policyService = policyService;
        this.defaultModel = defaultModel != null ? defaultModel : "llm-general";
    }

    /**
     * 프롬프트를 분석하여 의도 기반 라우팅 결정을 내림.
     *
     * @param prompt   사용자 입력 프롬프트
     * @param tenantId 테넌트 ID (null 가능)
     * @return 라우팅 결정 결과
     */
    public IntentRouteDecision decide(String prompt, String tenantId) {
        long startNanos = System.nanoTime();

        // 1. 정규화
        String normalized = RequestNormalizer.normalize(prompt);
        if (normalized.isBlank()) {
            return IntentRouteDecision.defaultRoute(defaultModel, System.nanoTime() - startNanos);
        }

        // 2. 키워드 매칭
        List<IntentKeyword> allKeywords = policyService.listKeywords();
        List<KeywordMatcher.MatchResult> matchResults = KeywordMatcher.match(normalized, allKeywords);

        // 3. 의도 결정
        IntentResolver.ResolvedIntent resolved = IntentResolver.resolve(matchResults);

        // 4. 정책 조회
        List<RoutingPolicy> policies = policyService.getPoliciesForIntent(resolved.intent());
        int estimatedTokens = RequestNormalizer.estimateTokenCount(prompt);

        RoutingPolicy bestPolicy = null;
        for (RoutingPolicy policy : policies) {
            if (policy.isApplicable(resolved.intent(), tenantId, estimatedTokens)) {
                bestPolicy = policy;
                break; // 이미 우선순위 정렬됨
            }
        }

        // 5. 기본 라우팅 (정책 미발견 시)
        if (bestPolicy == null) {
            // GENERAL 정책 확인
            List<RoutingPolicy> generalPolicies = policyService.getPoliciesForIntent(IntentType.GENERAL);
            for (RoutingPolicy gp : generalPolicies) {
                if (gp.isApplicable(IntentType.GENERAL, tenantId, estimatedTokens)) {
                    bestPolicy = gp;
                    break;
                }
            }
        }

        long elapsed = System.nanoTime() - startNanos;

        if (bestPolicy == null) {
            return IntentRouteDecision.defaultRoute(defaultModel, elapsed);
        }

        // 후보 키워드 목록
        List<String> candidateKeywords = matchResults.stream()
                .map(KeywordMatcher.MatchResult::matchedText)
                .distinct()
                .toList();

        return new IntentRouteDecision(
                resolved.intent(),
                resolved.matchedKeyword(),
                bestPolicy.policyId(),
                bestPolicy.routeTarget(),
                bestPolicy.modelName(),
                bestPolicy.fallbackModel(),
                bestPolicy.streamingPreferred(),
                bestPolicy.priority(),
                resolved.reasoning(),
                candidateKeywords,
                elapsed
        );
    }

    /**
     * 테스트용: 프롬프트의 정규화 결과와 매칭된 키워드 미리보기.
     */
    public PreviewResult preview(String prompt) {
        String normalized = RequestNormalizer.normalize(prompt);
        List<IntentKeyword> allKeywords = policyService.listKeywords();
        List<KeywordMatcher.MatchResult> matchResults = KeywordMatcher.match(normalized, allKeywords);
        IntentResolver.ResolvedIntent resolved = IntentResolver.resolve(matchResults);
        int estimatedTokens = RequestNormalizer.estimateTokenCount(prompt);
        return new PreviewResult(normalized, estimatedTokens, matchResults, resolved);
    }

    public record PreviewResult(
            String normalizedText,
            int estimatedTokens,
            List<KeywordMatcher.MatchResult> matchResults,
            IntentResolver.ResolvedIntent resolvedIntent
    ) {
    }
}
