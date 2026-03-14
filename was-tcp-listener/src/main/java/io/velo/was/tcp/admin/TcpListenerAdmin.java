package io.velo.was.tcp.admin;

import io.velo.was.tcp.bootstrap.TcpListenerServer;
import io.velo.was.tcp.observability.TcpMetrics;

/**
 * JMX MBean implementation for TCP listener management.
 */
public class TcpListenerAdmin implements TcpListenerAdminMBean {

    private final TcpListenerServer server;
    private final TcpMetrics metrics;

    public TcpListenerAdmin(TcpListenerServer server, TcpMetrics metrics) {
        this.server = server;
        this.metrics = metrics;
    }

    @Override public String getListenerName() { return server.name(); }
    @Override public String getHost() { return server.host(); }
    @Override public int getPort() { return server.port(); }
    @Override public boolean isRunning() { return server.isRunning(); }
    @Override public long getActiveConnections() { return metrics.activeConnections(); }
    @Override public long getConnectionsAccepted() { return metrics.connectionsAccepted(); }
    @Override public long getMessagesReceived() { return metrics.messagesReceived(); }
    @Override public long getMessagesProcessed() { return metrics.messagesProcessed(); }
    @Override public long getErrors() { return metrics.errors(); }
    @Override public double getAverageProcessingMillis() { return metrics.averageProcessingMillis(); }
    @Override public String getMetricsSnapshot() { return metrics.snapshot(); }

    @Override
    public void start() throws Exception {
        server.start();
    }

    @Override
    public void stop() {
        server.stop();
    }
}
