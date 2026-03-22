package io.velo.was.aiplatform.observability;

import io.velo.was.aiplatform.gateway.AiGatewayInferenceResult;
import io.velo.was.aiplatform.gateway.AiGatewayRouteDecision;
import io.velo.was.aiplatform.gateway.AiGatewayService;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.aiplatform.registry.AiModelRegistrySummary;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public class AiPlatformUsageService {

    private final AtomicLong controlPlaneCalls = new AtomicLong();
    private final AtomicLong routeCalls = new AtomicLong();
    private final AtomicLong inferCalls = new AtomicLong();
    private final AtomicLong streamCalls = new AtomicLong();
    private final AtomicLong publishedInvokeCalls = new AtomicLong();
    private final AtomicLong intentRouteCalls = new AtomicLong();
    private final AtomicLong registryMutations = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong totalEstimatedTokens = new AtomicLong();
    private final ConcurrentMap<String, AtomicLong> endpointCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> resolvedTypeCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> registryActionCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> publishedModelCounts = new ConcurrentHashMap<>();

    public void recordControlPlaneAccess(String endpoint) {
        controlPlaneCalls.incrementAndGet();
        increment(endpointCounts, endpoint);
    }

    public void recordRoute(AiGatewayRouteDecision decision) {
        routeCalls.incrementAndGet();
        increment(endpointCounts, "/gateway/route");
        increment(resolvedTypeCounts, decision.resolvedType());
        if (decision.cacheHit()) {
            cacheHits.incrementAndGet();
        }
    }

    public void recordInference(AiGatewayInferenceResult result, boolean stream) {
        if (stream) {
            streamCalls.incrementAndGet();
            increment(endpointCounts, "/gateway/stream");
        } else {
            inferCalls.incrementAndGet();
            increment(endpointCounts, "/gateway/infer");
        }
        increment(resolvedTypeCounts, result.decision().resolvedType());
        totalEstimatedTokens.addAndGet(result.estimatedTokens());
        if (result.decision().cacheHit()) {
            cacheHits.incrementAndGet();
        }
    }

    public void recordIntentRoute() {
        intentRouteCalls.incrementAndGet();
        increment(endpointCounts, "/gateway/intent-route");
    }

    public void recordPublishedInvocation(String modelName, int estimatedTokens) {
        publishedInvokeCalls.incrementAndGet();
        increment(endpointCounts, "/invoke/{model}");
        increment(publishedModelCounts, modelName);
        totalEstimatedTokens.addAndGet(Math.max(0, estimatedTokens));
    }

    public void recordRegistryMutation(String action) {
        registryMutations.incrementAndGet();
        increment(endpointCounts, "/api/models");
        increment(registryActionCounts, action);
    }

    public AiPlatformUsageSnapshot snapshot(boolean billingEnabled,
                                            AiGatewayService gatewayService,
                                            AiModelRegistryService registryService) {
        AiModelRegistrySummary registrySummary = registryService.summary();
        long meteredRequests = routeCalls.get() + inferCalls.get() + streamCalls.get() + publishedInvokeCalls.get() + intentRouteCalls.get();
        return new AiPlatformUsageSnapshot(
                controlPlaneCalls.get(),
                routeCalls.get(),
                inferCalls.get(),
                streamCalls.get(),
                publishedInvokeCalls.get(),
                intentRouteCalls.get(),
                registryMutations.get(),
                cacheHits.get(),
                totalEstimatedTokens.get(),
                meteredRequests,
                billingEnabled,
                registrySummary.totalModels(),
                registrySummary.totalVersions(),
                registrySummary.routableModels(),
                gatewayService.getTotalRequests(),
                gatewayService.getContextCacheSize(),
                gatewayService.getFailoverCount(),
                gatewayService.getEnsembleCount(),
                snapshot(endpointCounts),
                snapshot(resolvedTypeCounts),
                snapshot(registryActionCounts),
                mergeModelCounts(gatewayService.getModelRequestCounts(), snapshot(publishedModelCounts)),
                gatewayService.getAbTestGroupACounts(),
                gatewayService.getAbTestGroupBCounts()
        );
    }

    private static Map<String, Long> mergeModelCounts(Map<String, Long> gatewayCounts, Map<String, Long> publishedCounts) {
        Map<String, Long> merged = new TreeMap<>(gatewayCounts);
        publishedCounts.forEach((key, value) -> merged.merge(key, value, Long::sum));
        return new LinkedHashMap<>(merged);
    }

    private static void increment(ConcurrentMap<String, AtomicLong> counts, String key) {
        counts.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    private static Map<String, Long> snapshot(ConcurrentMap<String, AtomicLong> counts) {
        Map<String, Long> copy = new TreeMap<>();
        counts.forEach((key, value) -> copy.put(key, value.get()));
        return new LinkedHashMap<>(copy);
    }
}