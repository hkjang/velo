package io.velo.was.aiplatform.intent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouteDecisionEngineTest {

    private IntentRoutingPolicyService policyService;
    private RouteDecisionEngine engine;

    @BeforeEach
    void setUp() {
        policyService = new IntentRoutingPolicyService();
        engine = new RouteDecisionEngine(policyService, "llm-general");
    }

    @Test
    void routeSummarizationIntent() {
        IntentRouteDecision decision = engine.decide("이번 달 매출 보고서를 요약해 주세요", null);
        assertEquals(IntentType.SUMMARIZATION, decision.resolvedIntent());
        assertNotNull(decision.matchedKeyword());
        assertEquals("pol-sum", decision.policyId());
        assertTrue(decision.processingTimeNanos() > 0);
    }

    @Test
    void routeCodeIntent() {
        IntentRouteDecision decision = engine.decide("SQL 쿼리를 작성해서 코드 리뷰해줘", null);
        // CODE or GENERATION depending on keyword priority
        assertTrue(decision.resolvedIntent() == IntentType.CODE || decision.resolvedIntent() == IntentType.GENERATION);
        assertNotNull(decision.matchedKeyword());
    }

    @Test
    void routeTranslationIntent() {
        IntentRouteDecision decision = engine.decide("이 문장을 영어로 번역해줘", null);
        assertEquals(IntentType.TRANSLATION, decision.resolvedIntent());
        assertEquals("번역", decision.matchedKeyword());
    }

    @Test
    void routeConversationIntent() {
        IntentRouteDecision decision = engine.decide("안녕하세요 오늘 날씨 어때요", null);
        assertEquals(IntentType.CONVERSATION, decision.resolvedIntent());
    }

    @Test
    void routeEmptyPromptReturnsDefault() {
        IntentRouteDecision decision = engine.decide("", null);
        assertEquals(IntentType.GENERAL, decision.resolvedIntent());
        assertEquals("llm-general", decision.modelName());
    }

    @Test
    void routeNullPromptReturnsDefault() {
        IntentRouteDecision decision = engine.decide(null, null);
        assertEquals(IntentType.GENERAL, decision.resolvedIntent());
    }

    @Test
    void routeUnknownTextReturnsGeneral() {
        IntentRouteDecision decision = engine.decide("xyz abc 123", null);
        assertEquals(IntentType.GENERAL, decision.resolvedIntent());
    }

    @Test
    void previewShowsNormalizedTextAndMatches() {
        RouteDecisionEngine.PreviewResult preview = engine.preview("이번 달 매출 보고서를 요약해 주세요");
        assertNotNull(preview.normalizedText());
        assertFalse(preview.normalizedText().isBlank());
        assertTrue(preview.estimatedTokens() > 0);
        assertFalse(preview.matchResults().isEmpty());
        assertEquals(IntentType.SUMMARIZATION, preview.resolvedIntent().intent());
    }

    @Test
    void multipleCandidateKeywordsTracked() {
        IntentRouteDecision decision = engine.decide("이 문서를 요약 정리해서 핵심만 추출해줘", null);
        // "요약" and "핵심" are both SUMMARIZATION keywords, "추출" is EXTRACTION
        assertFalse(decision.candidateKeywords().isEmpty());
        assertTrue(decision.candidateKeywords().size() >= 2, "Should have multiple candidate keywords");
    }

    @Test
    void processingTimeIsReasonable() {
        IntentRouteDecision decision = engine.decide("간단한 테스트", null);
        // Should complete in < 100ms (typically < 1ms)
        assertTrue(decision.processingTimeNanos() < 100_000_000L, "Processing should be under 100ms");
    }
}
