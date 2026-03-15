package io.velo.was.observability;

import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight, thread-safe metrics collector for HTTP request processing.
 * Uses {@link LongAdder} for minimal contention under high concurrency.
 */
public final class MetricsCollector {

    private static final MetricsCollector INSTANCE = new MetricsCollector();

    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder activeRequests = new LongAdder();
    private final LongAdder activeConnections = new LongAdder();
    private final LongAdder totalResponseTimeNanos = new LongAdder();
    private final LongAdder[] statusBuckets = new LongAdder[6]; // index 1=1xx .. 5=5xx

    private MetricsCollector() {
        for (int i = 0; i < statusBuckets.length; i++) {
            statusBuckets[i] = new LongAdder();
        }
    }

    public static MetricsCollector instance() {
        return INSTANCE;
    }

    public void requestStarted() {
        totalRequests.increment();
        activeRequests.increment();
    }

    public void requestCompleted(long durationNanos, int statusCode) {
        activeRequests.decrement();
        totalResponseTimeNanos.add(durationNanos);
        int bucket = statusCode / 100;
        if (bucket >= 1 && bucket <= 5) {
            statusBuckets[bucket].increment();
        }
    }

    public void connectionOpened() {
        activeConnections.increment();
    }

    public void connectionClosed() {
        activeConnections.decrement();
    }

    public MetricsSnapshot snapshot() {
        return new MetricsSnapshot(
                totalRequests.sum(),
                activeRequests.sum(),
                activeConnections.sum(),
                totalResponseTimeNanos.sum(),
                statusBuckets[1].sum(),
                statusBuckets[2].sum(),
                statusBuckets[3].sum(),
                statusBuckets[4].sum(),
                statusBuckets[5].sum());
    }
}
