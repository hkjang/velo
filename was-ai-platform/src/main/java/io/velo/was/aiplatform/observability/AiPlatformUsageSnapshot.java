package io.velo.was.aiplatform.observability;

import java.util.Map;

public record AiPlatformUsageSnapshot(
        long controlPlaneCalls,
        long routeCalls,
        long inferCalls,
        long streamCalls,
        long publishedInvokeCalls,
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
        Map<String, Long> endpointCounts,
        Map<String, Long> resolvedTypeCounts,
        Map<String, Long> registryActionCounts,
        Map<String, Long> modelRequestCounts
) {
}