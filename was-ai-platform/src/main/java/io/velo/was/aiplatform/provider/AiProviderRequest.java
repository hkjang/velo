package io.velo.was.aiplatform.provider;

import java.util.List;

/**
 * Unified request format sent to AI provider adapters.
 * Supports text, vision (image), audio, TTS, and embedding modalities.
 */
public record AiProviderRequest(String model,
                                List<Message> messages,
                                String prompt,
                                double temperature,
                                int maxTokens,
                                boolean stream) {

    /**
     * A single message in a conversation. Supports multimodal content.
     *
     * @param role        message role (system, user, assistant)
     * @param content     text content
     * @param imageUrl    image URL or base64 data URI (for vision)
     * @param audioData   base64-encoded audio data (for STT)
     * @param contentType content type hint: "text", "image_url", "audio"
     */
    public record Message(String role, String content, String imageUrl,
                          String audioData, String contentType) {

        /** Text-only message (backwards compatible). */
        public Message(String role, String content) {
            this(role, content, null, null, "text");
        }

        /** Whether this message contains image data. */
        public boolean hasImage() {
            return imageUrl != null && !imageUrl.isBlank();
        }

        /** Whether this message contains audio data. */
        public boolean hasAudio() {
            return audioData != null && !audioData.isBlank();
        }
    }

    // ── Text factories (backwards compatible) ──

    public static AiProviderRequest chat(String model, String userMessage) {
        return new AiProviderRequest(model,
                List.of(new Message("user", userMessage)),
                userMessage, 0.7d, 1024, false);
    }

    public static AiProviderRequest completion(String model, String prompt) {
        return new AiProviderRequest(model, List.of(), prompt, 0.7d, 1024, false);
    }

    // ── Multimodal factories ──

    /** Vision request with image URL or base64 data URI. */
    public static AiProviderRequest vision(String model, String userMessage, String imageUrl) {
        return new AiProviderRequest(model,
                List.of(new Message("user", userMessage, imageUrl, null, "image_url")),
                userMessage, 0.7d, 1024, false);
    }

    /** Speech-to-text (STT/transcription) request. */
    public static AiProviderRequest audio(String model, String audioData) {
        return new AiProviderRequest(model,
                List.of(new Message("user", "", null, audioData, "audio")),
                "", 0.0d, 0, false);
    }

    /** Text-to-speech (TTS) request. */
    public static AiProviderRequest tts(String model, String text) {
        return new AiProviderRequest(model,
                List.of(new Message("user", text)),
                text, 0.0d, 0, false);
    }

    /** Embedding request. */
    public static AiProviderRequest embedding(String model, String input) {
        return new AiProviderRequest(model,
                List.of(new Message("user", input)),
                input, 0.0d, 0, false);
    }

    /** Whether any message in this request contains image data. */
    public boolean hasImageContent() {
        return messages.stream().anyMatch(Message::hasImage);
    }

    /** Whether any message in this request contains audio data. */
    public boolean hasAudioContent() {
        return messages.stream().anyMatch(Message::hasAudio);
    }
}
