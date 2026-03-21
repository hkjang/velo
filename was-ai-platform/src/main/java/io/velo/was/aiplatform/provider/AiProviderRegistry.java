package io.velo.was.aiplatform.provider;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry of AI provider adapters. The gateway consults this registry
 * to resolve a provider by ID and dispatch actual inference calls.
 */
public class AiProviderRegistry {

    private final ConcurrentMap<String, AiProviderAdapter> adapters = new ConcurrentHashMap<>();

    public void register(AiProviderAdapter adapter) {
        if (adapter == null || adapter.providerId() == null || adapter.providerId().isBlank()) {
            throw new IllegalArgumentException("Adapter must have a non-blank providerId");
        }
        adapters.put(adapter.providerId().trim().toLowerCase(), adapter);
    }

    public AiProviderAdapter resolve(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return null;
        }
        return adapters.get(providerId.trim().toLowerCase());
    }

    public AiProviderAdapter unregister(String providerId) {
        if (providerId == null) {
            return null;
        }
        return adapters.remove(providerId.trim().toLowerCase());
    }

    public List<AiProviderInfo> listProviders() {
        return adapters.values().stream()
                .sorted(Comparator.comparing(AiProviderAdapter::providerId))
                .map(adapter -> {
                    boolean healthy;
                    try {
                        healthy = adapter.healthCheck();
                    } catch (Exception e) {
                        healthy = false;
                    }
                    return new AiProviderInfo(
                            adapter.providerId(),
                            adapter.displayName(),
                            adapter.protocol(),
                            adapter.supportsStreaming(),
                            healthy
                    );
                })
                .toList();
    }

    public int size() {
        return adapters.size();
    }

    /**
     * Try to call a provider. Returns null if no adapter is registered
     * for the given provider, allowing the gateway to fall back to mock.
     */
    public AiProviderResponse tryInfer(String providerId, AiProviderRequest request) {
        AiProviderAdapter adapter = resolve(providerId);
        if (adapter == null) {
            return null;
        }
        return adapter.chatCompletion(request);
    }

    public record AiProviderInfo(String providerId,
                                 String displayName,
                                 String protocol,
                                 boolean supportsStreaming,
                                 boolean healthy) {
    }
}
