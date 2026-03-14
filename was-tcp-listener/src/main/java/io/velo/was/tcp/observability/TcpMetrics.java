package io.velo.was.tcp.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Collects TCP listener metrics:
 * <ul>
 *     <li>Total connections accepted / closed</li>
 *     <li>Active connections (computed)</li>
 *     <li>Messages received / processed</li>
 *     <li>Error count</li>
 *     <li>Total and average processing time (nanoseconds)</li>
 * </ul>
 */
public class TcpMetrics {

    private final String listenerName;
    private final LongAdder connectionsAccepted = new LongAdder();
    private final LongAdder connectionsClosed = new LongAdder();
    private final LongAdder messagesReceived = new LongAdder();
    private final LongAdder messagesProcessed = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder totalProcessingNanos = new LongAdder();
    private final Instant startedAt = Instant.now();

    public TcpMetrics(String listenerName) {
        this.listenerName = listenerName;
    }

    public void onConnectionAccepted() { connectionsAccepted.increment(); }
    public void onConnectionClosed() { connectionsClosed.increment(); }
    public void onMessageReceived() { messagesReceived.increment(); }
    public void onMessageProcessed(long processingNanos) {
        messagesProcessed.increment();
        totalProcessingNanos.add(processingNanos);
    }
    public void onError() { errors.increment(); }

    // --- Accessors ---

    public String listenerName() { return listenerName; }
    public long connectionsAccepted() { return connectionsAccepted.sum(); }
    public long connectionsClosed() { return connectionsClosed.sum(); }
    public long activeConnections() { return connectionsAccepted.sum() - connectionsClosed.sum(); }
    public long messagesReceived() { return messagesReceived.sum(); }
    public long messagesProcessed() { return messagesProcessed.sum(); }
    public long errors() { return errors.sum(); }
    public long totalProcessingNanos() { return totalProcessingNanos.sum(); }
    public Instant startedAt() { return startedAt; }

    public double averageProcessingMillis() {
        long processed = messagesProcessed.sum();
        if (processed == 0) return 0.0;
        return (totalProcessingNanos.sum() / 1_000_000.0) / processed;
    }

    /**
     * Returns a snapshot of all metrics as a formatted string.
     */
    public String snapshot() {
        return String.format(
                "TcpMetrics{listener=%s, active=%d, accepted=%d, closed=%d, " +
                        "received=%d, processed=%d, errors=%d, avgMs=%.2f}",
                listenerName, activeConnections(), connectionsAccepted(), connectionsClosed(),
                messagesReceived(), messagesProcessed(), errors(), averageProcessingMillis());
    }
}
