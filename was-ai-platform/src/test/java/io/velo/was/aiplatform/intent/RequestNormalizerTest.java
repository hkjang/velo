package io.velo.was.aiplatform.intent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequestNormalizerTest {

    @Test
    void normalizeLowercasesText() {
        String result = RequestNormalizer.normalize("Hello WORLD");
        assertEquals("hello world", result);
    }

    @Test
    void normalizeRemovesSpecialChars() {
        String result = RequestNormalizer.normalize("요약해줘! @#$% 테스트?");
        assertFalse(result.contains("!"));
        assertFalse(result.contains("@"));
    }

    @Test
    void normalizeRemovesKoreanStopWords() {
        String result = RequestNormalizer.normalize("이 문서를 요약해 주세요");
        assertFalse(result.contains(" 이 "));
    }

    @Test
    void normalizeRemovesEnglishStopWords() {
        String result = RequestNormalizer.normalize("please help me summarize this document");
        assertFalse(result.contains("please"));
        assertFalse(result.contains("help"));
    }

    @Test
    void normalizeNullReturnsEmpty() {
        assertEquals("", RequestNormalizer.normalize(null));
    }

    @Test
    void normalizeBlankReturnsEmpty() {
        assertEquals("", RequestNormalizer.normalize("   "));
    }

    @Test
    void estimateTokenCountKorean() {
        int tokens = RequestNormalizer.estimateTokenCount("한국어 텍스트");
        assertTrue(tokens > 0);
    }

    @Test
    void estimateTokenCountNull() {
        assertEquals(0, RequestNormalizer.estimateTokenCount(null));
    }
}
