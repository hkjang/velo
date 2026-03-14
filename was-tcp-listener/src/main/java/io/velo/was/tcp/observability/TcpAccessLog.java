package io.velo.was.tcp.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.time.Instant;

/**
 * TCP access logger, following the same structured logging pattern as
 * {@code was-observability} module.
 */
public class TcpAccessLog {

    private static final Logger log = LoggerFactory.getLogger("tcp.access");

    private final String listenerName;

    public TcpAccessLog(String listenerName) {
        this.listenerName = listenerName;
    }

    public void logConnect(String sessionId, SocketAddress remoteAddress) {
        log.info("CONNECT listener={} session={} remote={}",
                listenerName, sessionId, remoteAddress);
    }

    public void logDisconnect(String sessionId, SocketAddress remoteAddress, long durationMs) {
        log.info("DISCONNECT listener={} session={} remote={} duration={}ms",
                listenerName, sessionId, remoteAddress, durationMs);
    }

    public void logMessage(String sessionId, String messageType, int payloadSize, long processingMs) {
        log.info("MESSAGE listener={} session={} type={} size={} processing={}ms",
                listenerName, sessionId, messageType, payloadSize, processingMs);
    }

    public void logError(String sessionId, String errorType, String message) {
        log.error("ERROR listener={} session={} error={} message={}",
                listenerName, sessionId, errorType, message);
    }

    public void logSecurityEvent(String event, SocketAddress remoteAddress, String detail) {
        log.warn("SECURITY listener={} event={} remote={} detail={}",
                listenerName, event, remoteAddress, detail);
    }
}
