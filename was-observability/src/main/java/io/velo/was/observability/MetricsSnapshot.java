package io.velo.was.observability;

/**
 * Immutable point-in-time snapshot of server metrics.
 */
public record MetricsSnapshot(
        long totalRequests,
        long activeRequests,
        long activeConnections,
        long totalResponseTimeNanos,
        long count1xx,
        long count2xx,
        long count3xx,
        long count4xx,
        long count5xx
) {

    public double averageResponseTimeMs() {
        return totalRequests == 0 ? 0.0 : (totalResponseTimeNanos / 1_000_000.0) / totalRequests;
    }

    public String toJson() {
        return """
                {"totalRequests":%d,"activeRequests":%d,"activeConnections":%d,\
                "averageResponseTimeMs":%.2f,\
                "status":{"1xx":%d,"2xx":%d,"3xx":%d,"4xx":%d,"5xx":%d}}"""
                .formatted(totalRequests, activeRequests, activeConnections,
                        averageResponseTimeMs(),
                        count1xx, count2xx, count3xx, count4xx, count5xx);
    }
}
