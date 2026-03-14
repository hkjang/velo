package io.velo.was.tcp.observability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TcpMetricsTest {

    @Test
    void initialState() {
        TcpMetrics metrics = new TcpMetrics("test-listener");
        assertEquals("test-listener", metrics.listenerName());
        assertEquals(0, metrics.activeConnections());
        assertEquals(0, metrics.connectionsAccepted());
        assertEquals(0, metrics.messagesReceived());
        assertEquals(0, metrics.errors());
    }

    @Test
    void connectionTracking() {
        TcpMetrics metrics = new TcpMetrics("test");
        metrics.onConnectionAccepted();
        metrics.onConnectionAccepted();
        metrics.onConnectionAccepted();
        metrics.onConnectionClosed();

        assertEquals(3, metrics.connectionsAccepted());
        assertEquals(1, metrics.connectionsClosed());
        assertEquals(2, metrics.activeConnections());
    }

    @Test
    void messageTracking() {
        TcpMetrics metrics = new TcpMetrics("test");
        metrics.onMessageReceived();
        metrics.onMessageReceived();
        metrics.onMessageProcessed(1_000_000); // 1ms
        metrics.onMessageProcessed(3_000_000); // 3ms

        assertEquals(2, metrics.messagesReceived());
        assertEquals(2, metrics.messagesProcessed());
        assertEquals(2.0, metrics.averageProcessingMillis(), 0.01);
    }

    @Test
    void errorTracking() {
        TcpMetrics metrics = new TcpMetrics("test");
        metrics.onError();
        metrics.onError();
        assertEquals(2, metrics.errors());
    }

    @Test
    void snapshotFormatted() {
        TcpMetrics metrics = new TcpMetrics("my-listener");
        metrics.onConnectionAccepted();
        metrics.onMessageReceived();
        metrics.onMessageProcessed(1_000_000);

        String snapshot = metrics.snapshot();
        assertTrue(snapshot.contains("my-listener"));
        assertTrue(snapshot.contains("active=1"));
    }
}
