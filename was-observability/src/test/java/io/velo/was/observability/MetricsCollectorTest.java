package io.velo.was.observability;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class MetricsCollectorTest {

    @Test
    void singletonReturnsSameInstance() {
        assertSame(MetricsCollector.instance(), MetricsCollector.instance());
    }

    @Test
    void requestLifecycleUpdatesCounters() {
        MetricsCollector collector = MetricsCollector.instance();
        MetricsSnapshot before = collector.snapshot();

        collector.requestStarted();
        MetricsSnapshot during = collector.snapshot();
        assertEquals(before.activeRequests() + 1, during.activeRequests());

        collector.requestCompleted(5_000_000, 200);
        MetricsSnapshot after = collector.snapshot();
        assertEquals(before.activeRequests(), after.activeRequests());
        assertTrue(after.totalRequests() > before.totalRequests());
        assertTrue(after.count2xx() > before.count2xx());
    }

    @Test
    void connectionLifecycleUpdatesCounters() {
        MetricsCollector collector = MetricsCollector.instance();
        MetricsSnapshot before = collector.snapshot();

        collector.connectionOpened();
        MetricsSnapshot during = collector.snapshot();
        assertEquals(before.activeConnections() + 1, during.activeConnections());

        collector.connectionClosed();
        MetricsSnapshot after = collector.snapshot();
        assertEquals(before.activeConnections(), after.activeConnections());
    }

    @Test
    void statusBucketsAreSeparated() {
        MetricsCollector collector = MetricsCollector.instance();
        MetricsSnapshot before = collector.snapshot();

        collector.requestStarted();
        collector.requestCompleted(1_000_000, 404);
        collector.requestStarted();
        collector.requestCompleted(1_000_000, 500);

        MetricsSnapshot after = collector.snapshot();
        assertEquals(before.count4xx() + 1, after.count4xx());
        assertEquals(before.count5xx() + 1, after.count5xx());
    }

    @Test
    void concurrentAccessIsSafe() throws Exception {
        MetricsCollector collector = MetricsCollector.instance();
        int threads = 10;
        int iterations = 1000;
        MetricsSnapshot before = collector.snapshot();

        CountDownLatch latch = new CountDownLatch(threads);
        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    for (int i = 0; i < iterations; i++) {
                        collector.requestStarted();
                        collector.requestCompleted(1_000, 200);
                    }
                    latch.countDown();
                });
            }
            latch.await();
        }

        MetricsSnapshot after = collector.snapshot();
        assertEquals(before.totalRequests() + (long) threads * iterations, after.totalRequests());
        assertEquals(before.activeRequests(), after.activeRequests());
    }

    @Test
    void snapshotToJsonProducesValidFormat() {
        MetricsCollector collector = MetricsCollector.instance();
        String json = collector.snapshot().toJson();
        assertTrue(json.contains("\"totalRequests\":"));
        assertTrue(json.contains("\"activeRequests\":"));
        assertTrue(json.contains("\"activeConnections\":"));
        assertTrue(json.contains("\"averageResponseTimeMs\":"));
        assertTrue(json.contains("\"status\":{"));
    }
}
