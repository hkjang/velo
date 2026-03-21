package io.velo.was.aiplatform.intent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 키워드 매칭 엔진.
 * 정규화된 텍스트에서 등록된 키워드를 탐지하고 우선순위 기반 후보 목록 반환.
 */
public final class KeywordMatcher {

    private KeywordMatcher() {
    }

    /**
     * 정규화된 텍스트에서 매칭되는 키워드를 우선순위 순으로 반환.
     *
     * @param normalizedText 정규화된 입력 텍스트
     * @param keywords       등록된 키워드 목록
     * @return 우선순위 내림차순 정렬된 매칭 결과
     */
    public static List<MatchResult> match(String normalizedText, List<IntentKeyword> keywords) {
        if (normalizedText == null || normalizedText.isBlank() || keywords == null || keywords.isEmpty()) {
            return List.of();
        }

        List<MatchResult> results = new ArrayList<>();
        for (IntentKeyword keyword : keywords) {
            if (!keyword.enabled()) {
                continue;
            }
            String matched = keyword.matchedKeyword(normalizedText);
            if (matched != null) {
                results.add(new MatchResult(keyword, matched));
            }
        }

        // 우선순위 내림차순 정렬 (높은 우선순위 먼저)
        results.sort(Comparator.comparingInt((MatchResult r) -> r.keyword().priority()).reversed());
        return List.copyOf(results);
    }

    /**
     * 매칭 결과.
     */
    public record MatchResult(IntentKeyword keyword, String matchedText) {
    }
}
