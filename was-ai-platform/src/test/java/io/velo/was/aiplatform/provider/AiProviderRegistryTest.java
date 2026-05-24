package io.velo.was.aiplatform.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProviderRegistryTest {

    @Test
    void registerAndListProviders() {
        AiProviderRegistry registry = new AiProviderRegistry();
        registry.register(new StubProviderAdapter("test-provider", "Test Provider"));

        List<AiProviderRegistry.AiProviderInfo> providers = registry.listProviders();
        assertEquals(1, providers.size());
        assertEquals("test-provider", providers.get(0).providerId());
        assertEquals("Test Provider", providers.get(0).displayName());
        assertTrue(providers.get(0).healthy());
    }

    @Test
    void resolveReturnsRegisteredAdapter() {
        AiProviderRegistry registry = new AiProviderRegistry();
        registry.register(new StubProviderAdapter("openai", "OpenAI Stub"));

        AiProviderAdapter adapter = registry.resolve("openai");
        assertNotNull(adapter);
        assertEquals("openai", adapter.providerId());
    }

    @Test
    void resolveReturnsNullForUnknown() {
        AiProviderRegistry registry = new AiProviderRegistry();
        assertNull(registry.resolve("nonexistent"));
    }

    @Test
    void tryInferDelegatesToAdapter() {
        AiProviderRegistry registry = new AiProviderRegistry();
        registry.register(new StubProviderAdapter("stub", "Stub"));

        AiProviderResponse response = registry.tryInfer("stub", AiProviderRequest.chat("model", "hello"));
        assertNotNull(response);
        assertEquals("stub", response.providerId());
        assertTrue(response.content().contains("Stub response"));
    }

    @Test
    void tryInferReturnsNullForMissingProvider() {
        AiProviderRegistry registry = new AiProviderRegistry();
        AiProviderResponse response = registry.tryInfer("missing", AiProviderRequest.chat("model", "hello"));
        assertNull(response);
    }

    @Test
    void unregisterRemovesProvider() {
        AiProviderRegistry registry = new AiProviderRegistry();
        registry.register(new StubProviderAdapter("removable", "Removable"));
        assertEquals(1, registry.size());

        AiProviderAdapter removed = registry.unregister("removable");
        assertNotNull(removed);
        assertEquals(0, registry.size());
    }

    @Test
    void circuitBreakerOpensAfterRepeatedProviderFailures() {
        AiProviderRegistry registry = new AiProviderRegistry();
        registry.configureCircuitBreaker(1, 2, 60_000);
        registry.register(new FailingProviderAdapter("unstable"));

        assertThrows(RuntimeException.class, () -> registry.tryInfer("unstable", AiProviderRequest.chat("model", "hello")));
        assertThrows(RuntimeException.class, () -> registry.tryInfer("unstable", AiProviderRequest.chat("model", "hello")));

        AiProviderRegistry.AiProviderCircuitInfo circuit = registry.listCircuitBreakers().get(0);
        assertEquals("OPEN", circuit.state());
        assertEquals(2, circuit.failureCount());
        assertNull(registry.tryInfer("unstable", AiProviderRequest.chat("model", "hello")));
    }

    @Test
    void healthCheckIsCachedWithinConfiguredTtl() {
        AiProviderRegistry registry = new AiProviderRegistry();
        registry.configureCircuitBreaker(60_000, 2, 60_000);
        CountingHealthProviderAdapter adapter = new CountingHealthProviderAdapter("cached");
        registry.register(adapter);

        registry.listProviders();
        registry.listProviders();

        assertEquals(1, adapter.healthChecks);
        assertTrue(registry.listProviders().get(0).healthy());
    }

    private static class StubProviderAdapter implements AiProviderAdapter {
        private final String id;
        private final String name;

        StubProviderAdapter(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override public String providerId() { return id; }
        @Override public String displayName() { return name; }
        @Override public String protocol() { return "STUB"; }
        @Override public boolean supportsStreaming() { return false; }
        @Override public boolean healthCheck() { return true; }

        @Override
        public AiProviderResponse chatCompletion(AiProviderRequest request) {
            return AiProviderResponse.of(id, request.model(),
                    "Stub response for: " + request.prompt(), 32, 1L);
        }
    }

    private static final class FailingProviderAdapter extends StubProviderAdapter {
        FailingProviderAdapter(String id) {
            super(id, "Failing");
        }

        @Override
        public AiProviderResponse chatCompletion(AiProviderRequest request) {
            throw new RuntimeException("provider down");
        }
    }

    private static final class CountingHealthProviderAdapter extends StubProviderAdapter {
        private int healthChecks;

        CountingHealthProviderAdapter(String id) {
            super(id, "Counting");
        }

        @Override
        public boolean healthCheck() {
            healthChecks++;
            return true;
        }
    }
}
