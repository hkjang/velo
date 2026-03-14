package io.velo.was.tcp.admin;

/**
 * JMX MBean interface for TCP listener management and monitoring.
 */
public interface TcpListenerAdminMBean {

    /** Returns the listener name. */
    String getListenerName();

    /** Returns the bind host. */
    String getHost();

    /** Returns the bind port. */
    int getPort();

    /** Returns whether the listener is currently running. */
    boolean isRunning();

    /** Returns the number of active connections. */
    long getActiveConnections();

    /** Returns the total number of accepted connections. */
    long getConnectionsAccepted();

    /** Returns the total number of messages received. */
    long getMessagesReceived();

    /** Returns the total number of messages processed. */
    long getMessagesProcessed();

    /** Returns the total number of errors. */
    long getErrors();

    /** Returns the average processing time in milliseconds. */
    double getAverageProcessingMillis();

    /** Returns a formatted metrics snapshot. */
    String getMetricsSnapshot();

    /** Starts the listener. */
    void start() throws Exception;

    /** Stops the listener. */
    void stop();
}
