package io.velo.was.aiplatform.intent;

import java.util.List;

/**
 * 의도 분류용 키워드 정의.
 * 키워드 그룹과 동의어를 포함하며 의도 유형에 매핑됨.
 */
public record IntentKeyword(
        String keywordId,
        String primaryKeyword,
        List<String> synonyms,
        IntentType intent,
        int priority,
        boolean enabled,
        long createdAt
) {
    public IntentKeyword {
        if (keywordId == null || keywordId.isBlank()) {
            throw new IllegalArgumentException("keywordId is required");
        }
        if (primaryKeyword == null || primaryKeyword.isBlank()) {
            throw new IllegalArgumentException("primaryKeyword is required");
        }
        if (synonyms == null) {
            synonyms = List.of();
        }
        if (intent == null) {
            intent = IntentType.GENERAL;
        }
        if (priority < 0) {
            priority = 50;
        }
    }

    /**
     * 주어진 정규화된 텍스트에서 이 키워드 또는 동의어가 포함되는지 확인.
     */
    public boolean matches(String normalizedText) {
        if (!enabled) {
            return false;
        }
        if (normalizedText.contains(primaryKeyword.toLowerCase())) {
            return true;
        }
        for (String synonym : synonyms) {
            if (normalizedText.contains(synonym.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 매칭된 키워드 문자열을 반환 (디버깅/감사용).
     */
    public String matchedKeyword(String normalizedText) {
        if (normalizedText.contains(primaryKeyword.toLowerCase())) {
            return primaryKeyword;
        }
        for (String synonym : synonyms) {
            if (normalizedText.contains(synonym.toLowerCase())) {
                return synonym;
            }
        }
        return null;
    }
}
