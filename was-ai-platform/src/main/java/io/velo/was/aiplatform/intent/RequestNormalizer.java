package io.velo.was.aiplatform.intent;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 요청 텍스트 정규화기.
 * 소문자화, 특수문자 정리, 불용어 제거, 공백 정규화를 수행.
 */
public final class RequestNormalizer {

    private static final Pattern SPECIAL_CHARS = Pattern.compile("[^a-zA-Z0-9가-힣ㄱ-ㅎㅏ-ㅣ\\s]");
    private static final Pattern MULTI_SPACES = Pattern.compile("\\s+");

    private static final Set<String> STOP_WORDS_KO = Set.of(
            "은", "는", "이", "가", "을", "를", "에", "에서", "의", "로", "으로",
            "와", "과", "도", "만", "까지", "부터", "에게", "한테", "께",
            "그", "저", "이것", "그것", "저것", "것", "수", "등", "때", "중",
            "좀", "잘", "더", "매우", "아주", "정말", "진짜", "너무"
    );

    private static final Set<String> STOP_WORDS_EN = Set.of(
            "the", "a", "an", "is", "are", "was", "were", "be", "been",
            "do", "does", "did", "will", "would", "could", "should",
            "can", "may", "might", "shall", "have", "has", "had",
            "this", "that", "these", "those", "it", "its",
            "i", "me", "my", "we", "our", "you", "your", "he", "she",
            "and", "or", "but", "if", "then", "so", "for", "of", "to",
            "in", "on", "at", "by", "with", "from", "as", "into",
            "very", "really", "just", "also", "too", "please", "help"
    );

    private RequestNormalizer() {
    }

    /**
     * 텍스트를 정규화하여 키워드 매칭에 최적화된 형태로 변환.
     * 1. 소문자 변환
     * 2. 특수문자 제거
     * 3. 불용어 제거
     * 4. 다중 공백 정리
     */
    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String lower = text.toLowerCase().trim();
        String cleaned = SPECIAL_CHARS.matcher(lower).replaceAll(" ");
        StringBuilder result = new StringBuilder(cleaned.length());
        for (String token : cleaned.split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (STOP_WORDS_KO.contains(token) || STOP_WORDS_EN.contains(token)) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(token);
        }
        return MULTI_SPACES.matcher(result.toString()).replaceAll(" ").trim();
    }

    /**
     * 텍스트의 대략적인 토큰 수 추정 (한국어: 2자=1토큰, 영어: 4자=1토큰).
     */
    public static int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int koreanChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (c >= '\uAC00' && c <= '\uD7AF' || c >= '\u3131' && c <= '\u318E') {
                koreanChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }
        return (koreanChars / 2) + (otherChars / 4) + 1;
    }
}
