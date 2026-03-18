package io.velo.was.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class InMemoryHttpSessionStore implements HttpSessionStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryHttpSessionStore.class);

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final List<Consumer<SessionState>> expirationListeners = new ArrayList<>();
    private final int defaultMaxInactiveIntervalSeconds;

    public InMemoryHttpSessionStore() {
        this(1800);
    }

    public InMemoryHttpSessionStore(int defaultMaxInactiveIntervalSeconds) {
        this.defaultMaxInactiveIntervalSeconds = defaultMaxInactiveIntervalSeconds;
    }

    /**
     * Finds a session by ID. Returns {@code null} if the session does not exist
     * or has expired. Expired sessions are automatically removed.
     */
    @Override
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

    @Override
    public SessionState create() {
        String sessionId = nextSessionId();
        SessionState state = new SessionState(sessionId);
        state.setMaxInactiveIntervalSeconds(defaultMaxInactiveIntervalSeconds);
        sessions.put(sessionId, state);
        return state;
    }

    @Override
    public String changeSessionId(SessionState state) {
        if (state == null) {
            throw new IllegalArgumentException("Session state is required");
        }
        String oldId = state.getId();
        String newId = nextSessionId();
        sessions.remove(oldId, state);
        state.renameFromStore(newId, state.getOwnerNodeId(), state.getStickyRoute());
        sessions.put(newId, state);
        return newId;
    }

    @Override
    public void invalidate(String sessionId) {
        if (sessionId != null) {
            SessionState removed = sessions.remove(sessionId);
            if (removed != null) {
                removed.setChangeListener(SessionState.ChangeListener.NO_OP);
                removed.invalidateFromStore();
                removed.fireSessionDestroyed();
            }
        }
    }

    /**
     * Registers a listener that is called when a session expires (TTL exceeded).
     * Used to fire {@code HttpSessionListener.sessionDestroyed} events.
     */
    @Override
    public void addExpirationListener(Consumer<SessionState> listener) {
        expirationListeners.add(listener);
    }

    /**
     * Scans all sessions and removes those that have expired.
     * Returns the number of sessions removed.
     */
    @Override
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
    @Override
    public int size() {
        return sessions.size();
    }

    private void expire(String sessionId, SessionState state) {
        sessions.remove(sessionId, state);
        state.setChangeListener(SessionState.ChangeListener.NO_OP);
        state.invalidateFromStore();
        state.fireSessionDestroyed();
        for (Consumer<SessionState> listener : expirationListeners) {
            try {
                listener.accept(state);
            } catch (Exception e) {
                log.warn("Session expiration listener error for session={}", sessionId, e);
            }
        }
    }

    private String nextSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
