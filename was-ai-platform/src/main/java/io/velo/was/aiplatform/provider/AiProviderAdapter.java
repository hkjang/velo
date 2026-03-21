package io.velo.was.aiplatform.provider;

/**
 * Adapter interface for AI provider backends. Each implementation connects
 * to a specific LLM provider (OpenAI, Anthropic, vLLM, SGLang, Ollama, etc.)
 * and translates requests/responses into a unified format.
 *
 * <p>The gateway delegates actual inference to adapters when they are registered.
 * Without adapters, the gateway falls back to mock responses.</p>
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
     *
     * @param request the unified inference request
     * @return the inference response
     * @throws AiProviderException if the provider call fails
     */
    AiProviderResponse chatCompletion(AiProviderRequest request) throws AiProviderException;

    /**
     * Execute a text completion request against this provider.
     *
     * @param request the unified inference request
     * @return the inference response
     * @throws AiProviderException if the provider call fails
     */
    default AiProviderResponse textCompletion(AiProviderRequest request) throws AiProviderException {
        return chatCompletion(request);
    }
}
