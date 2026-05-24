package io.velo.was.aiplatform.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import io.velo.was.aiplatform.persistence.AiPlatformDataStore;
import io.velo.was.aiplatform.persistence.ProviderData;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * Registry of AI provider adapters. The gateway consults this registry
 * to resolve a provider by ID and dispatch actual inference calls.
 *
 * <p>동적 등록된 프로바이더는 {@code providers.json}에 영속화됩니다.
 */
public class AiProviderRegistry {

    private static final Logger LOG = Logger.getLogger(AiProviderRegistry.class.getName());
    private static final String PERSISTENCE_FILE = "providers.json";

    private final ConcurrentMap<String, AiProviderAdapter> adapters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ProviderData> providerDataMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ProviderRuntimeState> runtimeStates = new ConcurrentHashMap<>();
    private volatile AiPlatformDataStore dataStore;
    private volatile long healthCacheTtlMillis = 10_000L;
    private volatile int circuitFailureThreshold = 2;
    private volatile long circuitOpenMillis = 30_000L;

    public void setDataStore(AiPlatformDataStore dataStore) {
        this.dataStore = dataStore;
        loadFromDisk();
    }

    public void configureCircuitBreaker(long healthCacheTtlMillis, int failureThreshold, long openMillis) {
        if (healthCacheTtlMillis <= 0) {
            throw new IllegalArgumentException("healthCacheTtlMillis must be positive");
        }
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        if (openMillis <= 0) {
            throw new IllegalArgumentException("openMillis must be positive");
        }
        this.healthCacheTtlMillis = healthCacheTtlMillis;
        this.circuitFailureThreshold = failureThreshold;
        this.circuitOpenMillis = openMillis;
    }

    public void register(AiProviderAdapter adapter) {
        if (adapter == null || adapter.providerId() == null || adapter.providerId().isBlank()) {
            throw new IllegalArgumentException("Adapter must have a non-blank providerId");
        }
        String key = normalizeKey(adapter.providerId());
        adapters.put(key, adapter);
        runtimeStates.computeIfAbsent(key, ignored -> new ProviderRuntimeState());
    }

    /**
     * 동적 프로바이더 등록 (vLLM, SGLang, OpenAI-compatible, Anthropic, Ollama 등).
     */
    public ProviderData registerDynamic(String providerId, String displayName, String type,
                                         String baseUrl, String apiKey,
                                         List<String> models, Map<String, String> customHeaders) {
        if (providerId == null || providerId.isBlank()) throw new IllegalArgumentException("providerId is required");
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("baseUrl is required");
        if (type == null || type.isBlank()) type = "openai";
        String key = providerId.trim().toLowerCase();
        ProviderData data = new ProviderData(key, displayName != null ? displayName : providerId,
                type.trim().toLowerCase(), baseUrl.trim(), apiKey != null ? apiKey : "",
                models != null ? List.copyOf(models) : List.of(),
                customHeaders != null ? Map.copyOf(customHeaders) : Map.of(),
                true, Instant.now().toString());
        AiProviderAdapter adapter = createAdapter(data);
        adapters.put(key, adapter);
        providerDataMap.put(key, data);
        saveToDisk();
        LOG.info("Dynamic provider registered: " + key + " (" + type + ") → " + baseUrl);
        return data;
    }

    public boolean removeDynamic(String providerId) {
        if (providerId == null) return false;
        String key = providerId.trim().toLowerCase();
        AiProviderAdapter removed = adapters.remove(key);
        providerDataMap.remove(key);
        runtimeStates.remove(key);
        if (removed != null) { saveToDisk(); LOG.info("Dynamic provider removed: " + key); }
        return removed != null;
    }

    public List<ProviderData> listProviderData() { return new ArrayList<>(providerDataMap.values()); }

    public ProviderData getProviderData(String pid) {
        return pid == null ? null : providerDataMap.get(pid.trim().toLowerCase());
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
        String key = normalizeKey(providerId);
        runtimeStates.remove(key);
        return adapters.remove(key);
    }

    public List<AiProviderInfo> listProviders() {
        return adapters.values().stream()
                .sorted(Comparator.comparing(AiProviderAdapter::providerId))
                .map(adapter -> {
                    ProviderData pd = providerDataMap.get(adapter.providerId().trim().toLowerCase());
                    return new AiProviderInfo(
                            adapter.providerId(),
                            adapter.displayName(),
                            adapter.protocol(),
                            adapter.supportsStreaming(),
                            cachedHealthCheck(adapter),
                            pd != null ? pd.getBaseUrl() : "",
                            pd != null ? pd.getModels() : List.of(),
                            pd != null ? pd.getType() : "unknown",
                            pd != null,
                            circuitState(adapter.providerId(), System.currentTimeMillis()),
                            stateFor(adapter.providerId()).failureCount,
                            stateFor(adapter.providerId()).openUntilEpochMillis,
                            stateFor(adapter.providerId()).lastHealthCheckEpochMillis
                    );
                })
                .toList();
    }

    public List<AiProviderCircuitInfo> listCircuitBreakers() {
        long now = System.currentTimeMillis();
        return adapters.keySet().stream()
                .sorted()
                .map(providerId -> {
                    ProviderRuntimeState state = stateFor(providerId);
                    return new AiProviderCircuitInfo(
                            providerId,
                            circuitState(providerId, now),
                            state.failureCount,
                            state.openUntilEpochMillis,
                            state.lastHealthCheckEpochMillis,
                            state.lastHealthy,
                            state.lastError
                    );
                })
                .toList();
    }

    public int size() {
        return adapters.size();
    }

    private boolean cachedHealthCheck(AiProviderAdapter adapter) {
        String key = normalizeKey(adapter.providerId());
        ProviderRuntimeState state = stateFor(key);
        long now = System.currentTimeMillis();
        if (!isCallAllowed(key, now)) {
            return false;
        }
        if (state.lastHealthCheckEpochMillis > 0
                && now - state.lastHealthCheckEpochMillis < healthCacheTtlMillis) {
            return state.lastHealthy;
        }
        try {
            boolean healthy = adapter.healthCheck();
            state.lastHealthCheckEpochMillis = now;
            if (healthy) {
                recordSuccess(key, now);
            } else {
                recordFailure(key, "healthCheck returned false", now);
            }
            return healthy;
        } catch (RuntimeException e) {
            state.lastHealthCheckEpochMillis = now;
            recordFailure(key, e.getMessage(), now);
            return false;
        }
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
        String key = normalizeKey(providerId);
        long now = System.currentTimeMillis();
        if (!isCallAllowed(key, now)) {
            return null;
        }
        try {
            AiProviderResponse response = adapter.chatCompletion(request);
            recordSuccess(key, now);
            return response;
        } catch (RuntimeException e) {
            recordFailure(key, e.getMessage(), now);
            throw e;
        }
    }

    /**
     * Try to call a provider with modality-aware dispatch.
     * Routes to the appropriate adapter method based on the modality.
     *
     * @param providerId provider to call
     * @param request    the inference request
     * @param modality   modality hint: text, vision, image_gen, stt, tts, embedding
     * @return response or null if no adapter registered
     */
    public AiProviderResponse tryInferMultimodal(String providerId, AiProviderRequest request, String modality) {
        AiProviderAdapter adapter = resolve(providerId);
        if (adapter == null) {
            return null;
        }
        String key = normalizeKey(providerId);
        long now = System.currentTimeMillis();
        if (!isCallAllowed(key, now)) {
            return null;
        }
        try {
            AiProviderResponse response = switch (modality != null ? modality : "text") {
                case "vision" -> adapter.chatCompletion(request); // vision uses chat with image content
                case "image_gen" -> adapter.imageGeneration(request);
                case "stt" -> adapter.speechToText(request);
                case "tts" -> adapter.textToSpeech(request);
                case "embedding" -> adapter.embeddings(request);
                default -> adapter.chatCompletion(request);
            };
            recordSuccess(key, now);
            return response;
        } catch (RuntimeException e) {
            recordFailure(key, e.getMessage(), now);
            throw e;
        }
    }

    // ── 영속화 ──────────────────────────────────────────────────

    private void saveToDisk() {
        if (dataStore == null) return;
        try { dataStore.save(PERSISTENCE_FILE, new ArrayList<>(providerDataMap.values())); }
        catch (Exception e) { LOG.warning("Failed to save providers: " + e.getMessage()); }
    }

    private void loadFromDisk() {
        if (dataStore == null) return;
        try {
            List<ProviderData> list = dataStore.loadList(PERSISTENCE_FILE, new TypeReference<>() {});
            if (list != null) {
                for (ProviderData pd : list) {
                    if (pd.getProviderId() == null) continue;
                    String key = pd.getProviderId().trim().toLowerCase();
                    providerDataMap.put(key, pd);
                    if (pd.isEnabled()) adapters.put(key, createAdapter(pd));
                }
                LOG.info("Loaded " + list.size() + " providers from disk");
            }
        } catch (Exception e) { LOG.warning("Failed to load providers: " + e.getMessage()); }
    }

    private AiProviderAdapter createAdapter(ProviderData pd) {
        return switch (pd.getType()) {
            case "anthropic" -> new AnthropicProviderAdapter(pd.getApiKey());
            case "ollama" -> new OllamaProviderAdapter(pd.getBaseUrl());
            default -> new OpenAiProviderAdapter(pd.getProviderId(), pd.getDisplayName(), pd.getBaseUrl(), pd.getApiKey());
        };
    }

    private ProviderRuntimeState stateFor(String providerId) {
        return runtimeStates.computeIfAbsent(normalizeKey(providerId), ignored -> new ProviderRuntimeState());
    }

    private boolean isCallAllowed(String providerId, long now) {
        return stateFor(providerId).openUntilEpochMillis <= now;
    }

    private void recordSuccess(String providerId, long now) {
        ProviderRuntimeState state = stateFor(providerId);
        state.failureCount = 0;
        state.openUntilEpochMillis = 0L;
        state.lastHealthy = true;
        state.lastError = "";
        state.lastSuccessEpochMillis = now;
    }

    private void recordFailure(String providerId, String error, long now) {
        ProviderRuntimeState state = stateFor(providerId);
        state.failureCount++;
        state.lastHealthy = false;
        state.lastError = error == null || error.isBlank() ? "provider call failed" : error;
        state.lastFailureEpochMillis = now;
        if (state.failureCount >= circuitFailureThreshold) {
            state.openUntilEpochMillis = now + circuitOpenMillis;
        }
    }

    private String circuitState(String providerId, long now) {
        ProviderRuntimeState state = stateFor(providerId);
        if (state.openUntilEpochMillis > now) {
            return "OPEN";
        }
        if (state.failureCount >= circuitFailureThreshold && state.openUntilEpochMillis > 0) {
            return "HALF_OPEN";
        }
        return "CLOSED";
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static final class ProviderRuntimeState {
        private volatile int failureCount;
        private volatile long openUntilEpochMillis;
        private volatile long lastHealthCheckEpochMillis;
        private volatile boolean lastHealthy = true;
        private volatile String lastError = "";
        private volatile long lastSuccessEpochMillis;
        private volatile long lastFailureEpochMillis;
    }

    public record AiProviderInfo(String providerId, String displayName, String protocol,
                                 boolean supportsStreaming, boolean healthy,
                                 String baseUrl, List<String> models, String type, boolean dynamic,
                                 String circuitState, int failureCount,
                                 long circuitOpenUntilEpochMillis, long lastHealthCheckEpochMillis) {
    }

    public record AiProviderCircuitInfo(String providerId, String state, int failureCount,
                                        long openUntilEpochMillis, long lastHealthCheckEpochMillis,
                                        boolean lastHealthy, String lastError) {
    }
}
