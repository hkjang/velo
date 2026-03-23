package io.velo.was.aiplatform.provider;

import java.util.List;

/**
 * Adapter interface for AI provider backends. Each implementation connects
 * to a specific LLM provider (OpenAI, Anthropic, vLLM, SGLang, Ollama, etc.)
 * and translates requests/responses into a unified format.
 *
 * <p>The gateway delegates actual inference to adapters when they are registered.
 * Without adapters, the gateway falls back to mock responses.</p>
 *
 * <p>Multimodal methods (imageGeneration, speechToText, textToSpeech, embeddings)
 * have default implementations that throw {@link UnsupportedOperationException},
 * so providers only need to override the modalities they support.</p>
 */
public interface AiProviderAdapter {

    /** Unique provider identifier (e.g. "openai", "anthropic", "ollama"). */
    String providerId();

    /** Human-readable display name. */
    String displayName();

    /** Protocol used (e.g. "REST", "REST/SSE", "gRPC"). */
    String protocol();

    /** Whether this adapter supports streaming (SSE). */
    boolean supportsStreaming();

    /** Test connectivity to the provider. Returns true if reachable. */
    boolean healthCheck();

    /**
     * Execute a chat completion request against this provider.
     * For vision models, the request may contain image data in messages.
     */
    AiProviderResponse chatCompletion(AiProviderRequest request) throws AiProviderException;

    /**
     * Execute a text completion request against this provider.
     */
    default AiProviderResponse textCompletion(AiProviderRequest request) throws AiProviderException {
        return chatCompletion(request);
    }

    /**
     * Generate an image from a text prompt.
     * @throws UnsupportedOperationException if not supported by this provider
     */
    default AiProviderResponse imageGeneration(AiProviderRequest request) throws AiProviderException {
        throw new UnsupportedOperationException(providerId() + " does not support image generation");
    }

    /**
     * Transcribe audio to text (speech-to-text / whisper).
     * @throws UnsupportedOperationException if not supported by this provider
     */
    default AiProviderResponse speechToText(AiProviderRequest request) throws AiProviderException {
        throw new UnsupportedOperationException(providerId() + " does not support speech-to-text");
    }

    /**
     * Synthesize speech from text (text-to-speech).
     * @throws UnsupportedOperationException if not supported by this provider
     */
    default AiProviderResponse textToSpeech(AiProviderRequest request) throws AiProviderException {
        throw new UnsupportedOperationException(providerId() + " does not support text-to-speech");
    }

    /**
     * Generate embeddings for input text.
     * @throws UnsupportedOperationException if not supported by this provider
     */
    default AiProviderResponse embeddings(AiProviderRequest request) throws AiProviderException {
        throw new UnsupportedOperationException(providerId() + " does not support embeddings");
    }

    /**
     * List of modalities this provider supports.
     * Used by the gateway to determine which methods can be called.
     */
    default List<String> supportedModalities() {
        return List.of("text");
    }
}
