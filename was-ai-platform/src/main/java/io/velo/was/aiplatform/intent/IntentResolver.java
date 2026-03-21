package io.velo.was.aiplatform.intent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 의도 결정기.
 * 키워드 매칭 결과와 정책을 종합하여 최종 의도를 결정.
 *
 * 결정 순서:
 * 1. 키워드 매칭 결과의 최고 우선순위 의도 채택
 * 2. 동일 우선순위 복수 매칭 시 빈도 기반 결정
 * 3. 매칭 없으면 GENERAL
 */
public final class IntentResolver {

    private IntentResolver() {
    }

    /**
     * 키워드 매칭 결과로부터 최종 의도를 결정.
     *
     * @param matchResults 키워드 매칭 결과 (우선순위 정렬됨)
     * @return 결정된 의도 유형
     */
    public static ResolvedIntent resolve(List<KeywordMatcher.MatchResult> matchResults) {
        if (matchResults == null || matchResults.isEmpty()) {
            return new ResolvedIntent(IntentType.GENERAL, null, "키워드 미매칭", 0);
        }

        // 최고 우선순위 값
        int topPriority = matchResults.get(0).keyword().priority();

        // 동일 최고 우선순위 후보 수집
        Map<IntentType, Integer> intentCounts = new ConcurrentHashMap<>();
        String firstMatchedKeyword = null;
        for (KeywordMatcher.MatchResult result : matchResults) {
            if (result.keyword().priority() < topPriority) {
                break; // 우선순위가 내려가면 중단
            }
            intentCounts.merge(result.keyword().intent(), 1, Integer::sum);
            if (firstMatchedKeyword == null) {
                firstMatchedKeyword = result.matchedText();
            }
        }

        // 가장 많이 매칭된 의도 선택
        IntentType bestIntent = IntentType.GENERAL;
        int maxCount = 0;
        for (Map.Entry<IntentType, Integer> entry : intentCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                bestIntent = entry.getKey();
            }
        }

        String reasoning;
        if (intentCounts.size() == 1) {
            reasoning = "키워드 '" + firstMatchedKeyword + "' 매칭 → " + bestIntent.label() + " 의도 결정";
        } else {
            reasoning = "복수 의도 후보 중 빈도 기반 결정: " + bestIntent.label() + " (" + maxCount + "건)";
        }

        return new ResolvedIntent(bestIntent, firstMatchedKeyword, reasoning, topPriority);
    }

    /**
     * 결정된 의도.
     */
    public record ResolvedIntent(IntentType intent, String matchedKeyword, String reasoning, int priority) {
    }
}
