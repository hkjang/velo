package io.velo.was.aiplatform.gateway;

public record AiGatewayRouteDecision(String requestedType,
                                     String resolvedType,
                                     String modelName,
                                     String modelCategory,
                                     String provider,
                                     String version,
                                     String routePolicy,
                                     String strategyApplied,
                                     boolean cacheHit,
                                     boolean promptRouted,
                                     boolean streamingSupported,
                                     boolean contextCacheEnabled,
                                     int expectedLatencyMs,
                                     int accuracyScore,
                                     long totalRequests,
                                     long modelRequestCount,
                                     String cacheKey,
                                     String reasoning) {
}