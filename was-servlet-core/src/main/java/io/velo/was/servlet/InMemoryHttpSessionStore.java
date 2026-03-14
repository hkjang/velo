package io.velo.was.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class InMemoryHttpSessionStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryHttpSessionStore.class);

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final List<Consumer<SessionState>> expirationListeners = new ArrayList<>();

    /**
     * Finds a session by ID. Returns {@code null} if the session does not exist
     * or has expired. Expired sessions are automatically removed.
     */
    public SessionState find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        SessionState state = sessions.get(sessionId);
        if (state != null && state.isExpired()) {
            expire(sessionId, state);
            return null;
        }
        return state;
    }

    public SessionState create() {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        SessionState state = new SessionState(sessionId);
        sessions.put(sessionId, state);
        return state;
    }

    public void invalidate(String sessionId) {
        if (sessionId != null) {
            SessionState removed = sessions.remove(sessionId);
            if (removed != null) {
                removed.invalidate();
            }
        }
    }

    /**
     * Registers a listener that is called when a session expires (TTL exceeded).
     * Used to fire {@code HttpSessionListener.sessionDestroyed} events.
     */
    public void addExpirationListener(Consumer<SessionState> listener) {
        expirationListeners.add(listener);
    }

    /**
     * Scans all sessions and removes those that have expired.
     * Returns the number of sessions removed.
     */
    public int purgeExpired() {
        int count = 0;
        for (Map.Entry<String, SessionState> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired()) {
                expire(entry.getKey(), entry.getValue());
                count++;
            }
        }
        if (count > 0) {
            log.info("Session purge completed: expired={} remaining={}", count, sessions.size());
        }
        return count;
    }

    /** Current number of active (non-expired) sessions. */
    public int size() {
        return sessions.size();
    }

    private void expire(String sessionId, SessionState state) {
        sessions.remove(sessionId);
        state.invalidate();
        for (Consumer<SessionState> listener : expirationListeners) {
            try {
                listener.accept(state);
            } catch (Exception e) {
                log.warn("Session expiration listener error for session={}", sessionId, e);
            }
        }
    }
}
