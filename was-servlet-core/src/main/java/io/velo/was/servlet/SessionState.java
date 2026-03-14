package io.velo.was.servlet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionState {

    private final String id;
    private final long creationTime = System.currentTimeMillis();
    private volatile long lastAccessedTime = creationTime;
    private volatile boolean valid = true;
    private volatile int maxInactiveIntervalSeconds = 1800; // 30 minutes default
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public SessionState(String id) {
        this.id = id;
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
}

