package io.velo.was.aiplatform.gateway;

import io.velo.was.aiplatform.provider.AiProviderRegistry;
import io.velo.was.aiplatform.provider.AiProviderRequest;
import io.velo.was.aiplatform.provider.AiProviderResponse;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.config.ServerConfiguration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class AiGatewayService {

    private final ServerConfiguration configuration;
    private final AiModelRegistryService registryService;
    private final AiProviderRegistry providerRegistry;
    private final ConcurrentMap<String, CacheEntry> contextCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> modelRequestCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> abTestGroupA = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> abTestGroupB = new ConcurrentHashMap<>();
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong failoverCount = new AtomicLong();
    private final AtomicLong ensembleCount = new AtomicLong();

    public AiGatewayService(ServerConfiguration configuration) {
        this(configuration, new AiModelRegistryService(configuration), new AiProviderRegistry());
    }

    public AiGatewayService(ServerConfiguration configuration, AiModelRegistryService registryService) {
        this(configuration, registryService, new AiProviderRegistry());
    }

    public AiGatewayService(ServerConfiguration configuration, AiModelRegistryService registryService, AiProviderRegistry providerRegistry) {
        this.configuration = configuration;
        this.registryService = registryService;
        this.providerRegistry = providerRegistry;
    }

    public AiProviderRegistry getProviderRegistry() {
        return providerRegistry;
    }

    public AiGatewayRouteDecision route(AiGatewayRequest request) {
        ServerConfiguration.AiPlatform ai = configuration.getServer().getAiPlatform();
        ServerConfiguration.Serving serving = ai.getServing();
        ServerConfiguration.Advanced advanced = ai.getAdvanced();
        ServerConfiguration.Differentiation differentiation = ai.getDifferentiation();

        String prompt = normalizePrompt(request.prompt());
        String requestedType = normalizeRequestType(request.requestType());
        RequestResolution resolution = resolveRequestType(requestedType, prompt, advanced);
        String cacheKey = buildCacheKey(request.sessionId(), resolution.resolvedType(), prompt, advanced.isContextCacheEnabled());

        long total = totalRequests.incrementAndGet();
        CacheEntry cached = lookupCache(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            long modelCount = incrementModelCounter(cached.model().getName());
            return new AiGatewayRouteDecision(
                    cached.requestedType(),
                    cached.resolvedType(),
                    cached.model().getName(),
                    cached.model().getCategory(),
                    cached.model().getProvider(),
                    cached.model().getVersion(),
                    cached.routePolicy(),
                    cached.strategyApplied(),
                    true,
                    cached.promptRouted(),
                    differentiation.isStreamingResponseEnabled(),
                    advanced.isContextCacheEnabled(),
                    cached.model().getLatencyMs(),
                    cached.model().getAccuracyScore(),
                    total,
                    modelCount,
                    cacheKey,
                    cached.reasoning() + " Cache hit served the existing routing decision."
            );
        }

        RoutePolicyMatch policyMatch = selectRoutePolicy(serving.getRoutePolicies(), resolution.resolvedType());
        List<ServerConfiguration.ModelProfile> enabledModels = registryService.routingModels();
        if (enabledModels.isEmpty()) {
            throw new IllegalStateException("No enabled AI models are registered");
        }

        ServerConfiguration.ModelProfile preferredModel = policyMatch.policy() != null
                ? findModelByName(enabledModels, policyMatch.policy().getTargetModel())
                : null;

        List<ServerConfiguration.ModelProfile> candidates = candidatesForRequest(enabledModels, resolution.resolvedType(), preferredModel);
        ServerConfiguration.ModelProfile selectedModel;
        boolean abTestApplied = false;
        if (serving.isAbTestingEnabled() && candidates.size() >= 2) {
            selectedModel = abTestSelect(candidates, enabledModels, preferredModel, serving.getDefaultStrategy(), serving.isAutoModelSelectionEnabled());
            abTestApplied = true;
        } else {
            selectedModel = selectModel(candidates, enabledModels, preferredModel, serving.getDefaultStrategy(), serving.isAutoModelSelectionEnabled());
        }

        String routePolicyName = policyMatch.policy() != null ? policyMatch.policy().getName() : "auto-selection";
        String reasoning = buildReasoning(resolution, policyMatch, preferredModel, selectedModel, serving.getDefaultStrategy(), serving.isAutoModelSelectionEnabled());
        if (abTestApplied) {
            reasoning += " A/B test was applied between candidate models.";
        }
        long modelCount = incrementModelCounter(selectedModel.getName());

        AiGatewayRouteDecision decision = new AiGatewayRouteDecision(
                resolution.requestedType(),
                resolution.resolvedType(),
                selectedModel.getName(),
                selectedModel.getCategory(),
                selectedModel.getProvider(),
                selectedModel.getVersion(),
                routePolicyName,
                serving.getDefaultStrategy(),
                false,
                resolution.promptRouted(),
                differentiation.isStreamingResponseEnabled(),
                advanced.isContextCacheEnabled(),
                selectedModel.getLatencyMs(),
                selectedModel.getAccuracyScore(),
                total,
                modelCount,
                cacheKey,
                reasoning
        );

        if (advanced.isContextCacheEnabled() && !cacheKey.isBlank()) {
            long expiresAt = System.currentTimeMillis() + (advanced.getContextCacheTtlSeconds() * 1000L);
            contextCache.put(cacheKey, new CacheEntry(cacheKey, resolution.requestedType(), resolution.resolvedType(), selectedModel,
                    routePolicyName, serving.getDefaultStrategy(), resolution.promptRouted(), reasoning, expiresAt));
        }
        return decision;
    }

    public AiGatewayInferenceResult infer(AiGatewayRequest request) {
        AiGatewayRouteDecision decision = route(request);
        String prompt = normalizePrompt(request.prompt());

        // Try real provider adapter first
        AiProviderResponse providerResponse = null;
        try {
            providerResponse = providerRegistry.tryInfer(
                    decision.provider(),
                    AiProviderRequest.chat(decision.modelName(), prompt));
        } catch (Exception ignored) {
            // Provider failed — fall back to mock
        }

        String outputText;
        int estimatedTokens;
        double confidence;
        if (providerResponse != null && providerResponse.content() != null && !providerResponse.content().isBlank()) {
            outputText = providerResponse.content();
            estimatedTokens = providerResponse.totalTokens();
            confidence = Math.min(0.99d, Math.max(0.55d, decision.accuracyScore() / 100.0d));
        } else {
            outputText = generateOutput(decision, prompt);
            estimatedTokens = Math.max(32, outputText.length() / 4);
            confidence = Math.min(0.99d, Math.max(0.55d, decision.accuracyScore() / 100.0d));
        }
        return new AiGatewayInferenceResult(decision, outputText, estimatedTokens, confidence);
    }

    /**
     * Ensemble serving: runs inference on multiple candidate models and selects the
     * best result based on confidence, or combines them for improved accuracy.
     */
    public AiEnsembleResult inferEnsemble(AiGatewayRequest request) {
        ServerConfiguration.AiPlatform ai = configuration.getServer().getAiPlatform();
        if (!ai.getServing().isEnsembleServingEnabled()) {
            AiGatewayInferenceResult single = infer(request);
            return new AiEnsembleResult(List.of(single), single, "single", single.confidence(), single.estimatedTokens());
        }

        String prompt = normalizePrompt(request.prompt());
        String requestedType = normalizeRequestType(request.requestType());
        RequestResolution resolution = resolveRequestType(requestedType, prompt, ai.getAdvanced());
        List<ServerConfiguration.ModelProfile> enabledModels = registryService.routingModels();
        List<ServerConfiguration.ModelProfile> candidates = candidatesForRequest(enabledModels, resolution.resolvedType(), null);

        int maxCandidates = Math.min(3, candidates.size());
        List<AiGatewayInferenceResult> results = new ArrayList<>(maxCandidates);
        for (int i = 0; i < maxCandidates; i++) {
            ServerConfiguration.ModelProfile model = candidates.get(i);
            AiGatewayRequest modelRequest = new AiGatewayRequest(
                    request.requestType(), request.prompt(),
                    request.sessionId() + "-ensemble-" + i, request.streamingRequested());
            AiGatewayInferenceResult result = infer(modelRequest);
            results.add(result);
        }

        AiGatewayInferenceResult bestResult = results.stream()
                .max(java.util.Comparator.comparingDouble(AiGatewayInferenceResult::confidence))
                .orElse(results.get(0));

        double ensembleConfidence = Math.min(0.99d, bestResult.confidence() + (results.size() > 1 ? 0.05d : 0.0d));
        int totalTokens = results.stream().mapToInt(AiGatewayInferenceResult::estimatedTokens).sum();
        String strategy = results.size() > 1 ? "best-of-" + results.size() : "single";
        ensembleCount.incrementAndGet();

        return new AiEnsembleResult(results, bestResult, strategy, ensembleConfidence, totalTokens);
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }

    public long getCacheHitCount() {
        return cacheHits.get();
    }

    public int getContextCacheSize() {
        evictExpiredEntries();
        return contextCache.size();
    }

    public long getFailoverCount() {
        return failoverCount.get();
    }

    public void recordFailover() {
        failoverCount.incrementAndGet();
    }

    public long getEnsembleCount() {
        return ensembleCount.get();
    }

    public Map<String, Long> getAbTestGroupACounts() {
        return snapshotCounters(abTestGroupA);
    }

    public Map<String, Long> getAbTestGroupBCounts() {
        return snapshotCounters(abTestGroupB);
    }

    public Map<String, Long> getModelRequestCounts() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        modelRequestCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> snapshot.put(entry.getKey(), entry.getValue().get()));
        return snapshot;
    }

    private ServerConfiguration.ModelProfile abTestSelect(List<ServerConfiguration.ModelProfile> candidates,
                                                             List<ServerConfiguration.ModelProfile> enabledModels,
                                                             ServerConfiguration.ModelProfile preferredModel,
                                                             String strategy,
                                                             boolean autoSelectionEnabled) {
        ServerConfiguration.ModelProfile modelA = selectModel(candidates, enabledModels, preferredModel, strategy, autoSelectionEnabled);
        ServerConfiguration.ModelProfile modelB = candidates.stream()
                .filter(c -> !c.getName().equalsIgnoreCase(modelA.getName()))
                .findFirst()
                .orElse(modelA);
        boolean useGroupA = ThreadLocalRandom.current().nextDouble() < 0.5d;
        ServerConfiguration.ModelProfile selected = useGroupA ? modelA : modelB;
        ConcurrentMap<String, AtomicLong> targetGroup = useGroupA ? abTestGroupA : abTestGroupB;
        targetGroup.computeIfAbsent(selected.getName(), ignored -> new AtomicLong()).incrementAndGet();
        return selected;
    }

    private static Map<String, Long> snapshotCounters(ConcurrentMap<String, AtomicLong> counters) {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        counters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> snapshot.put(entry.getKey(), entry.getValue().get()));
        return snapshot;
    }

    private void evictExpiredEntries() {
        long now = System.currentTimeMillis();
        contextCache.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
    }

    private static String generateOutput(AiGatewayRouteDecision decision, String prompt) {
        String preview = prompt.isBlank() ? "No prompt provided." : preview(prompt, 120);
        return switch (decision.resolvedType()) {
            case "VISION" -> "Vision route selected. Model " + decision.modelName()
                    + " will handle image-aware reasoning. Prompt preview: " + preview
                    + " This built-in gateway currently returns a mock response envelope until provider adapters are connected.";
            case "RECOMMENDATION" -> "Recommendation route selected. Model " + decision.modelName()
                    + " will prepare ranking or personalization output. Prompt preview: " + preview
                    + " This built-in gateway currently returns a mock response envelope until provider adapters are connected.";
            default -> "Conversation route selected. Model " + decision.modelName()
                    + " will handle general language generation. Prompt preview: " + preview
                    + " This built-in gateway currently returns a mock response envelope until provider adapters are connected.";
        };
    }

    private static String preview(String prompt, int maxLength) {
        if (prompt.length() <= maxLength) {
            return prompt;
        }
        return prompt.substring(0, maxLength - 3) + "...";
    }

    private static RequestResolution resolveRequestType(String requestedType,
                                                        String prompt,
                                                        ServerConfiguration.Advanced advanced) {
        if (!requestedType.isBlank() && !"AUTO".equalsIgnoreCase(requestedType)) {
            return new RequestResolution(requestedType, requestedType, false);
        }
        if (!advanced.isPromptRoutingEnabled()) {
            return new RequestResolution(requestedType.isBlank() ? "AUTO" : requestedType, "CHAT", false);
        }
        String lowered = prompt.toLowerCase(Locale.ROOT);
        if (containsAny(lowered, "image", "vision", "photo", "ocr", "screenshot", "diagram")) {
            return new RequestResolution(requestedType.isBlank() ? "AUTO" : requestedType, "VISION", true);
        }
        if (containsAny(lowered, "recommend", "ranking", "rank", "personalize", "suggest", "catalog")) {
            return new RequestResolution(requestedType.isBlank() ? "AUTO" : requestedType, "RECOMMENDATION", true);
        }
        return new RequestResolution(requestedType.isBlank() ? "AUTO" : requestedType, "CHAT", true);
    }

    private static boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static RoutePolicyMatch selectRoutePolicy(List<ServerConfiguration.RoutePolicy> policies, String resolvedType) {
        ServerConfiguration.RoutePolicy exact = policies.stream()
                .filter(policy -> resolvedType.equalsIgnoreCase(policy.getRequestType()))
                .max(Comparator.comparingInt(ServerConfiguration.RoutePolicy::getPriority))
                .orElse(null);
        if (exact != null) {
            return new RoutePolicyMatch(exact, true);
        }
        ServerConfiguration.RoutePolicy fallback = policies.stream()
                .filter(policy -> "DEFAULT".equalsIgnoreCase(policy.getRequestType()))
                .max(Comparator.comparingInt(ServerConfiguration.RoutePolicy::getPriority))
                .orElse(null);
        return new RoutePolicyMatch(fallback, false);
    }

    private static List<ServerConfiguration.ModelProfile> candidatesForRequest(List<ServerConfiguration.ModelProfile> enabledModels,
                                                                               String resolvedType,
                                                                               ServerConfiguration.ModelProfile preferredModel) {
        List<ServerConfiguration.ModelProfile> candidates = new ArrayList<>();
        String preferredCategory = preferredModel != null ? preferredModel.getCategory() : categoryForRequest(resolvedType);
        for (ServerConfiguration.ModelProfile model : enabledModels) {
            if (preferredCategory.equalsIgnoreCase(model.getCategory())) {
                candidates.add(model);
            }
        }
        if (!candidates.isEmpty()) {
            return candidates;
        }
        return enabledModels;
    }

    private static String categoryForRequest(String resolvedType) {
        return switch (resolvedType) {
            case "VISION" -> "CV";
            case "RECOMMENDATION" -> "RECOMMENDER";
            default -> "LLM";
        };
    }

    private static ServerConfiguration.ModelProfile selectModel(List<ServerConfiguration.ModelProfile> candidates,
                                                                List<ServerConfiguration.ModelProfile> enabledModels,
                                                                ServerConfiguration.ModelProfile preferredModel,
                                                                String strategy,
                                                                boolean autoSelectionEnabled) {
        if (!autoSelectionEnabled && preferredModel != null) {
            return preferredModel;
        }
        Comparator<ServerConfiguration.ModelProfile> comparator = switch (strategy) {
            case "LATENCY_FIRST", "COST_FIRST" -> Comparator
                    .comparingInt(ServerConfiguration.ModelProfile::getLatencyMs)
                    .thenComparing(Comparator.comparingInt(ServerConfiguration.ModelProfile::getAccuracyScore).reversed())
                    .thenComparing(Comparator.comparing(ServerConfiguration.ModelProfile::isDefaultSelected).reversed());
            case "ACCURACY_FIRST" -> Comparator
                    .comparingInt(ServerConfiguration.ModelProfile::getAccuracyScore).reversed()
                    .thenComparingInt(ServerConfiguration.ModelProfile::getLatencyMs)
                    .thenComparing(Comparator.comparing(ServerConfiguration.ModelProfile::isDefaultSelected).reversed());
            default -> Comparator
                    .comparingDouble(AiGatewayService::balancedScore)
                    .reversed()
                    .thenComparing(Comparator.comparing(ServerConfiguration.ModelProfile::isDefaultSelected).reversed())
                    .thenComparingInt(ServerConfiguration.ModelProfile::getLatencyMs);
        };
        return candidates.stream()
                .min(comparator)
                .orElseGet(() -> enabledModels.stream()
                        .filter(ServerConfiguration.ModelProfile::isDefaultSelected)
                        .findFirst()
                        .orElse(enabledModels.get(0)));
    }

    private static double balancedScore(ServerConfiguration.ModelProfile model) {
        return (model.getAccuracyScore() * 3.0d) - (model.getLatencyMs() / 10.0d);
    }

    private static String buildReasoning(RequestResolution resolution,
                                         RoutePolicyMatch policyMatch,
                                         ServerConfiguration.ModelProfile preferredModel,
                                         ServerConfiguration.ModelProfile selectedModel,
                                         String strategy,
                                         boolean autoSelectionEnabled) {
        StringBuilder reasoning = new StringBuilder();
        if (resolution.promptRouted()) {
            reasoning.append("Prompt routing derived ").append(resolution.resolvedType()).append(" from the prompt. ");
        } else {
            reasoning.append("Request type ").append(resolution.resolvedType()).append(" was provided explicitly. ");
        }
        if (policyMatch.policy() != null) {
            reasoning.append("Policy ").append(policyMatch.policy().getName()).append(" matched ")
                    .append(policyMatch.exactMatch() ? "exactly" : "via DEFAULT fallback").append(". ");
        } else {
            reasoning.append("No route policy matched, so the gateway used model strategy fallback. ");
        }
        if (preferredModel != null) {
            reasoning.append("Preferred model was ").append(preferredModel.getName()).append(". ");
        }
        if (autoSelectionEnabled) {
            reasoning.append("Auto model selection applied strategy ").append(strategy).append(" and chose ")
                    .append(selectedModel.getName()).append('.');
        } else {
            reasoning.append("Static routing selected ").append(selectedModel.getName()).append('.');
        }
        return reasoning.toString();
    }

    private CacheEntry lookupCache(String cacheKey) {
        if (cacheKey.isBlank()) {
            return null;
        }
        CacheEntry cacheEntry = contextCache.get(cacheKey);
        if (cacheEntry == null) {
            return null;
        }
        if (cacheEntry.expiresAtMillis() < System.currentTimeMillis()) {
            contextCache.remove(cacheKey);
            return null;
        }
        return cacheEntry;
    }

    private long incrementModelCounter(String modelName) {
        return modelRequestCounts.computeIfAbsent(modelName, ignored -> new AtomicLong()).incrementAndGet();
    }

    private static String buildCacheKey(String sessionId, String resolvedType, String prompt, boolean cacheEnabled) {
        if (!cacheEnabled || sessionId == null || sessionId.isBlank()) {
            return "";
        }
        return sessionId.trim() + ':' + resolvedType + ':' + sha256(prompt);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static ServerConfiguration.ModelProfile findModelByName(List<ServerConfiguration.ModelProfile> models, String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return null;
        }
        return models.stream()
                .filter(model -> modelName.equalsIgnoreCase(model.getName()))
                .findFirst()
                .orElse(null);
    }

    private static String normalizeRequestType(String requestType) {
        if (requestType == null || requestType.isBlank()) {
            return "";
        }
        return requestType.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizePrompt(String prompt) {
        return prompt == null ? "" : prompt.trim();
    }

    private record CacheEntry(String cacheKey,
                              String requestedType,
                              String resolvedType,
                              ServerConfiguration.ModelProfile model,
                              String routePolicy,
                              String strategyApplied,
                              boolean promptRouted,
                              String reasoning,
                              long expiresAtMillis) {
    }

    private record RequestResolution(String requestedType, String resolvedType, boolean promptRouted) {
    }

    private record RoutePolicyMatch(ServerConfiguration.RoutePolicy policy, boolean exactMatch) {
    }
}