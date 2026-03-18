package io.velo.was.servlet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class SessionState {

    private volatile String id;
    private final long creationTime;
    private volatile long lastAccessedTime;
    private volatile boolean valid = true;
    private volatile int maxInactiveIntervalSeconds = 1800; // 30 minutes default
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private volatile SessionNotifier notifier = SessionNotifier.NO_OP;
    private final AtomicBoolean destroyedEventFired = new AtomicBoolean(false);

    public SessionState(String id) {
        this.id = id;
        this.creationTime = System.currentTimeMillis();
        this.lastAccessedTime = creationTime;
    }

    public String getId() {
        return id;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getLastAccessedTime() {
        return lastAccessedTime;
    }

    public void touch() {
        this.lastAccessedTime = System.currentTimeMillis();
    }

    public boolean isValid() {
        return valid;
    }

    public void invalidate() {
        this.valid = false;
        this.attributes.clear();
    }

    public int getMaxInactiveIntervalSeconds() {
        return maxInactiveIntervalSeconds;
    }

    public void setMaxInactiveIntervalSeconds(int seconds) {
        this.maxInactiveIntervalSeconds = seconds;
    }

    /**
     * A session is considered expired if:
     * <ul>
     *   <li>maxInactiveIntervalSeconds is positive (0 or negative means never expires)</li>
     *   <li>the elapsed time since lastAccessedTime exceeds maxInactiveIntervalSeconds</li>
     * </ul>
     */
    public boolean isExpired() {
        if (!valid) {
            return true;
        }
        if (maxInactiveIntervalSeconds <= 0) {
            return false; // 0 or negative = never expires
        }
        long idleMillis = System.currentTimeMillis() - lastAccessedTime;
        return idleMillis > (maxInactiveIntervalSeconds * 1000L);
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    void setId(String id) {
        this.id = id;
    }

    void setNotifier(SessionNotifier notifier) {
        this.notifier = notifier == null ? SessionNotifier.NO_OP : notifier;
    }

    void fireSessionCreated() {
        notifier.sessionCreated(this);
    }

    void fireSessionDestroyed() {
        if (destroyedEventFired.compareAndSet(false, true)) {
            notifier.sessionDestroyed(this);
        }
    }

    void fireSessionIdChanged(String oldSessionId) {
        notifier.sessionIdChanged(this, oldSessionId);
    }

    void fireAttributeAdded(String name, Object value) {
        notifier.attributeAdded(this, name, value);
    }

    void fireAttributeRemoved(String name, Object value) {
        notifier.attributeRemoved(this, name, value);
    }

    void fireAttributeReplaced(String name, Object oldValue) {
        notifier.attributeReplaced(this, name, oldValue);
    }

    interface SessionNotifier {
        SessionNotifier NO_OP = new SessionNotifier() {
        };

        default void sessionCreated(SessionState state) {
        }

        default void sessionDestroyed(SessionState state) {
        }

        default void sessionIdChanged(SessionState state, String oldSessionId) {
        }

        default void attributeAdded(SessionState state, String name, Object value) {
        }

        default void attributeRemoved(SessionState state, String name, Object value) {
        }

        default void attributeReplaced(SessionState state, String name, Object oldValue) {
        }
    }
}
