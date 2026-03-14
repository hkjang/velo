package io.velo.was.tcp.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active TCP sessions. Tracks session creation, removal, and provides
 * idle session cleanup and query capabilities.
 */
public class TcpSessionManager {

    private static final Logger log = LoggerFactory.getLogger(TcpSessionManager.class);

    private final Map<String, TcpSession> sessions = new ConcurrentHashMap<>();

    /**
     * Creates and registers a new session for a connection.
     */
    public TcpSession createSession(SocketAddress remoteAddress, SocketAddress localAddress) {
        TcpSession session = new TcpSession(remoteAddress, localAddress);
        sessions.put(session.sessionId(), session);
        log.debug("Session created: {} remote={}", session.sessionId(), remoteAddress);
        return session;
    }

    /**
     * Removes a session by ID.
     */
    public TcpSession removeSession(String sessionId) {
        TcpSession removed = sessions.remove(sessionId);
        if (removed != null) {
            log.debug("Session removed: {} remote={}", sessionId, removed.remoteAddress());
        }
        return removed;
    }

    /**
     * Looks up a session by ID.
     */
    public TcpSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Returns the number of active sessions.
     */
    public int activeCount() {
        return sessions.size();
    }

    /**
     * Returns all active sessions.
     */
    public Collection<TcpSession> allSessions() {
        return sessions.values();
    }

    /**
     * Removes sessions that have been idle for longer than the given duration.
     * Returns the number of sessions removed.
     */
    public int cleanupIdleSessions(Duration maxIdleTime) {
        Instant cutoff = Instant.now().minus(maxIdleTime);
        int removed = 0;
        var iter = sessions.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            if (entry.getValue().lastActiveAt().isBefore(cutoff)) {
                iter.remove();
                removed++;
                log.debug("Idle session removed: {} remote={}", entry.getKey(),
                        entry.getValue().remoteAddress());
            }
        }
        if (removed > 0) {
            log.info("Cleaned up {} idle sessions (maxIdle={})", removed, maxIdleTime);
        }
        return removed;
    }

    /**
     * Removes all sessions.
     */
    public void clear() {
        int count = sessions.size();
        sessions.clear();
        log.info("All {} sessions cleared", count);
    }
}
