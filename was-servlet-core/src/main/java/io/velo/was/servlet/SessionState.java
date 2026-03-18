package io.velo.was.servlet;

import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SessionState {

    private volatile String id;
    private final long creationTime;
    private volatile long lastAccessedTime;
    private volatile long lastModifiedTime;
    private volatile long expiresAtEpochMillis;
    private volatile boolean valid = true;
    private volatile int maxInactiveIntervalSeconds = 1800;
    private volatile String ownerNodeId = "";
    private volatile String stickyRoute = "";
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Map<String, Object> attributesView = new AttributeMapView();
    private volatile SessionNotifier notifier = SessionNotifier.NO_OP;
    private volatile ChangeListener changeListener = ChangeListener.NO_OP;
    private final AtomicBoolean destroyedEventFired = new AtomicBoolean(false);
    private final AtomicLong version = new AtomicLong(0);

    public SessionState(String id) {
        this.id = id;
        this.creationTime = System.currentTimeMillis();
        this.lastAccessedTime = creationTime;
        this.lastModifiedTime = creationTime;
        recalculateExpiry(creationTime);
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

    public long getLastModifiedTime() {
        return lastModifiedTime;
    }

    public long getExpiresAtEpochMillis() {
        return expiresAtEpochMillis;
    }

    public long getVersion() {
        return version.get();
    }

    public String getOwnerNodeId() {
        return ownerNodeId;
    }

    public String getStickyRoute() {
        return stickyRoute;
    }

    public void touch() {
        ChangeListener listener;
        synchronized (this) {
            if (!valid) {
                return;
            }
            long now = System.currentTimeMillis();
            lastAccessedTime = now;
            lastModifiedTime = now;
            version.incrementAndGet();
            recalculateExpiry(now);
            listener = changeListener;
        }
        listener.onMutation(this, MutationType.ACCESSED, null);
    }

    public boolean isValid() {
        return valid;
    }

    public void invalidate() {
        ChangeListener listener;
        synchronized (this) {
            if (!valid) {
                return;
            }
            long now = System.currentTimeMillis();
            valid = false;
            attributes.clear();
            lastModifiedTime = now;
            expiresAtEpochMillis = now;
            version.incrementAndGet();
            listener = changeListener;
        }
        listener.onMutation(this, MutationType.INVALIDATED, null);
    }

    public int getMaxInactiveIntervalSeconds() {
        return maxInactiveIntervalSeconds;
    }

    public void setMaxInactiveIntervalSeconds(int seconds) {
        ChangeListener listener;
        synchronized (this) {
            maxInactiveIntervalSeconds = seconds;
            lastModifiedTime = System.currentTimeMillis();
            version.incrementAndGet();
            recalculateExpiry(lastAccessedTime);
            listener = changeListener;
        }
        listener.onMutation(this, MutationType.MAX_INACTIVE_INTERVAL_CHANGED, null);
    }

    public boolean isExpired() {
        if (!valid) {
            return true;
        }
        if (maxInactiveIntervalSeconds <= 0) {
            return false;
        }
        return System.currentTimeMillis() >= expiresAtEpochMillis;
    }

    public Map<String, Object> attributes() {
        return attributesView;
    }

    Map<String, Object> snapshotAttributes() {
        return new LinkedHashMap<>(attributes);
    }

    Object getAttribute(String name) {
        return attributes.get(name);
    }

    Object putAttribute(String name, Object value) {
        if (value == null) {
            return removeAttribute(name);
        }
        Object previous;
        ChangeListener listener;
        synchronized (this) {
            previous = attributes.put(name, value);
            lastModifiedTime = System.currentTimeMillis();
            version.incrementAndGet();
            listener = changeListener;
        }
        listener.onMutation(this, MutationType.ATTRIBUTE_CHANGED, null);
        return previous;
    }

    Object removeAttribute(String name) {
        Object removed;
        ChangeListener listener;
        synchronized (this) {
            removed = attributes.remove(name);
            if (removed == null) {
                return null;
            }
            lastModifiedTime = System.currentTimeMillis();
            version.incrementAndGet();
            listener = changeListener;
        }
        listener.onMutation(this, MutationType.ATTRIBUTE_CHANGED, null);
        return removed;
    }

    void assignClusterMetadata(String ownerNodeId, String stickyRoute) {
        this.ownerNodeId = ownerNodeId == null ? "" : ownerNodeId;
        this.stickyRoute = stickyRoute == null ? "" : stickyRoute;
    }

    void renameFromStore(String newId, String ownerNodeId, String stickyRoute) {
        synchronized (this) {
            this.id = newId;
            this.ownerNodeId = ownerNodeId == null ? "" : ownerNodeId;
            this.stickyRoute = stickyRoute == null ? "" : stickyRoute;
            this.lastModifiedTime = System.currentTimeMillis();
            version.incrementAndGet();
        }
    }

    void invalidateFromStore() {
        synchronized (this) {
            valid = false;
            attributes.clear();
            lastModifiedTime = System.currentTimeMillis();
            expiresAtEpochMillis = lastModifiedTime;
            version.incrementAndGet();
        }
    }

    void applyRecord(SessionRecord record, Map<String, Object> materializedAttributes) {
        synchronized (this) {
            this.id = record.id();
            this.ownerNodeId = record.ownerNodeId();
            this.stickyRoute = record.stickyRoute();
            this.lastAccessedTime = record.lastAccessedTime();
            this.lastModifiedTime = record.lastModifiedTime();
            this.expiresAtEpochMillis = record.expiresAtEpochMillis();
            this.maxInactiveIntervalSeconds = record.maxInactiveIntervalSeconds();
            this.valid = record.valid();
            this.attributes.clear();
            this.attributes.putAll(materializedAttributes);
            this.version.set(record.version());
        }
    }

    void setNotifier(SessionNotifier notifier) {
        this.notifier = notifier == null ? SessionNotifier.NO_OP : notifier;
    }

    void setChangeListener(ChangeListener changeListener) {
        this.changeListener = changeListener == null ? ChangeListener.NO_OP : changeListener;
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

    private void recalculateExpiry(long accessTimeMillis) {
        if (maxInactiveIntervalSeconds <= 0) {
            expiresAtEpochMillis = Long.MAX_VALUE;
            return;
        }
        expiresAtEpochMillis = accessTimeMillis + (maxInactiveIntervalSeconds * 1000L);
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

    interface ChangeListener {
        ChangeListener NO_OP = (state, mutationType, previousSessionId) -> {
        };

        void onMutation(SessionState state, MutationType mutationType, String previousSessionId);
    }

    enum MutationType {
        ACCESSED,
        ATTRIBUTE_CHANGED,
        MAX_INACTIVE_INTERVAL_CHANGED,
        INVALIDATED
    }

    private final class AttributeMapView extends AbstractMap<String, Object> {
        @Override
        public Object get(Object key) {
            return attributes.get(key);
        }

        @Override
        public Object put(String key, Object value) {
            return putAttribute(key, value);
        }

        @Override
        public Object remove(Object key) {
            if (!(key instanceof String name)) {
                return null;
            }
            return removeAttribute(name);
        }

        @Override
        public void clear() {
            for (String name : List.copyOf(attributes.keySet())) {
                removeAttribute(name);
            }
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            Set<Entry<String, Object>> snapshot = new LinkedHashSet<>();
            attributes.forEach((key, value) -> snapshot.add(Map.entry(key, value)));
            return Set.copyOf(snapshot);
        }
    }
}
