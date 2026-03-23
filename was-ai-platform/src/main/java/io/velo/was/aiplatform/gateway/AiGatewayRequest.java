package io.velo.was.aiplatform.gateway;

/**
 * Gateway inference request supporting text and multimodal modalities.
 *
 * @param requestType        request type hint (CHAT, VISION, IMAGE_GENERATION, STT, TTS, EMBEDDING, AUTO)
 * @param prompt             text prompt or user message
 * @param sessionId          session ID for context caching
 * @param streamingRequested whether streaming (SSE) is requested
 * @param imageUrl           image URL or base64 data URI (for vision / image input)
 * @param audioData          base64-encoded audio data (for STT)
 * @param modality           modality hint: text, vision, image_gen, stt, tts, embedding (null defaults to text)
 */
public record AiGatewayRequest(String requestType,
                               String prompt,
                               String sessionId,
                               boolean streamingRequested,
                               String imageUrl,
                               String audioData,
                               String modality) {

    /** Backwards-compatible constructor for text-only requests. */
    public AiGatewayRequest(String requestType, String prompt, String sessionId, boolean streamingRequested) {
        this(requestType, prompt, sessionId, streamingRequested, null, null, "text");
    }

    /** Resolve the effective modality — defaults to "text" if null/blank. */
    public String effectiveModality() {
        if (modality != null && !modality.isBlank()) return modality;
        if (imageUrl != null && !imageUrl.isBlank()) return "vision";
        if (audioData != null && !audioData.isBlank()) return "stt";
        return "text";
    }
}
