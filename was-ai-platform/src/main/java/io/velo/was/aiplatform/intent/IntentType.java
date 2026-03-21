package io.velo.was.aiplatform.intent;

/**
 * 사용자 의도 유형 열거형.
 * 키워드 기반 라우팅의 핵심 분류 기준.
 */
public enum IntentType {
    SUMMARIZATION("요약", "long-context 모델로 라우팅"),
    GENERATION("생성", "고품질 생성 모델로 라우팅"),
    CLASSIFICATION("분류", "소형 고속 모델로 라우팅"),
    EXTRACTION("추출", "구조화 출력 특화 모델로 라우팅"),
    CODE("코드", "코드 특화 모델로 라우팅"),
    SEARCH("검색", "RAG 파이프라인으로 라우팅"),
    VALIDATION("검증", "reasoning/guard 모델로 라우팅"),
    TRANSLATION("번역", "경량 번역 모델로 라우팅"),
    CONVERSATION("대화", "범용 대화 모델로 라우팅"),
    GENERAL("일반", "기본 범용 모델로 라우팅");

    private final String label;
    private final String description;

    IntentType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public static IntentType fromString(String value) {
        if (value == null || value.isBlank()) {
            return GENERAL;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return GENERAL;
        }
    }
}
