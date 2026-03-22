package io.velo.was.aiplatform.observability;

import java.util.Map;

public record AiPlatformUsageSnapshot(
        long controlPlaneCalls,
        long routeCalls,
        long inferCalls,
        long streamCalls,
        long publishedInvokeCalls,
        long intentRouteCalls,
        long registryMutations,
        long cacheHits,
        long totalEstimatedTokens,
        long meteredRequests,
        boolean billingEnabled,
        int registeredModels,
        int totalVersions,
        int routableModels,
        long gatewayRouteDecisions,
        int cachedContexts,
        long failoverCount,
        long ensembleCount,
        Map<String, Long> endpointCounts,
        Map<String, Long> resolvedTypeCounts,
        Map<String, Long> registryActionCounts,
        Map<String, Long> modelRequestCounts,
        Map<String, Long> abTestGroupACounts,
        Map<String, Long> abTestGroupBCounts
) {
}