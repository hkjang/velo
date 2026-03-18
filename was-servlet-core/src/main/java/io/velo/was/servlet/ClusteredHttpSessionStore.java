package io.velo.was.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ClusteredHttpSessionStore implements HttpSessionStore {

    private static final Logger log = LoggerFactory.getLogger(ClusteredHttpSessionStore.class);

    private final String nodeId;
    private final String stickyRoute;
    private final boolean stickySessionsEnabled;
    private final int defaultMaxInactiveIntervalSeconds;
    private final SessionRepository repository;
    private final SessionSerializer serializer;
    private final SessionConflictResolver conflictResolver;
    private final SessionReplicationChannel replicationChannel;
    private final SessionReplicationChannel.Subscription replicationSubscription;
    private final ExecutorService replicationExecutor;
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final List<Consumer<SessionState>> expirationListeners = new CopyOnWriteArrayList<>();

    public ClusteredHttpSessionStore(String nodeId,
                                     int defaultMaxInactiveIntervalSeconds,
                                     SessionRepository repository) {
        this(nodeId,
                nodeId,
                true,
                defaultMaxInactiveIntervalSeconds,
                repository,
                SessionReplicationChannel.NO_OP,
                new JavaSessionSerializer(SessionSerializationPolicy.STRICT),
                LatestVersionSessionConflictResolver.INSTANCE);
    }

    public ClusteredHttpSessionStore(String nodeId,
                                     String stickyRoute,
                                     boolean stickySessionsEnabled,
                                     int defaultMaxInactiveIntervalSeconds,
                                     SessionRepository repository,
                                     SessionReplicationChannel replicationChannel,
                                     SessionSerializer serializer,
                                     SessionConflictResolver conflictResolver) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId is required for clustered session storage");
        }
        this.nodeId = nodeId;
        this.stickyRoute = stickyRoute == null || stickyRoute.isBlank() ? nodeId : stickyRoute;
        this.stickySessionsEnabled = stickySessionsEnabled;
        this.defaultMaxInactiveIntervalSeconds = defaultMaxInactiveIntervalSeconds;
        this.repository = Objects.requireNonNull(repository, "repository");
        this.replicationChannel = replicationChannel == null ? SessionReplicationChannel.NO_OP : replicationChannel;
        this.serializer = serializer == null
                ? new JavaSessionSerializer(SessionSerializationPolicy.STRICT)
                : serializer;
        this.conflictResolver = conflictResolver == null
                ? LatestVersionSessionConflictResolver.INSTANCE
                : conflictResolver;
        this.replicationExecutor = Executors.newSingleThreadExecutor(
                Thread.ofVirtual().name("session-replication-" + nodeId + "-", 0).factory());
        this.replicationSubscription = this.replicationChannel.subscribe(nodeId, this::applyReplicationMessage);
    }

    @Override
    public SessionState find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }

        SessionState local = sessions.get(sessionId);
        if (local != null) {
            if (local.isExpired()) {
                expire(sessionId, local);
                return null;
            }
            return local;
        }

        SessionRecord record = repository.find(sessionId);
        if (record == null) {
            return null;
        }
        if (record.isExpired(System.currentTimeMillis())) {
            repository.delete(sessionId);
            scheduleReplication(SessionReplicationMessage.invalidate(nodeId, sessionId));
            return null;
        }

        SessionState materialized = materialize(record);
        SessionState existing = sessions.putIfAbsent(sessionId, materialized);
        SessionState resolved = existing == null ? materialized : existing;
        if (existing != null && shouldApplyIncoming(existing, record)) {
            applyRecord(existing, record);
        }
        return resolved.isExpired() ? null : resolved;
    }

    @Override
    public SessionState create() {
        SessionState state = new SessionState(nextSessionId());
        state.assignClusterMetadata(nodeId, stickySessionsEnabled ? stickyRoute : "");
        if (defaultMaxInactiveIntervalSeconds != state.getMaxInactiveIntervalSeconds()) {
            state.setMaxInactiveIntervalSeconds(defaultMaxInactiveIntervalSeconds);
        }
        state.setChangeListener(this::handleStateMutation);
        sessions.put(state.getId(), state);
        SessionRecord resolved = persist(state);
        scheduleReplication(SessionReplicationMessage.upsert(nodeId, resolved));
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
        state.renameFromStore(newId, nodeId, stickySessionsEnabled ? stickyRoute : "");
        sessions.put(newId, state);
        repository.delete(oldId);
        SessionRecord resolved = persist(state);
        scheduleReplication(SessionReplicationMessage.invalidate(nodeId, oldId));
        scheduleReplication(SessionReplicationMessage.upsert(nodeId, resolved));
        return newId;
    }

    @Override
    public void invalidate(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        repository.delete(sessionId);
        scheduleReplication(SessionReplicationMessage.invalidate(nodeId, sessionId));
        SessionState removed = sessions.remove(sessionId);
        if (removed != null) {
            removed.setChangeListener(SessionState.ChangeListener.NO_OP);
            removed.invalidateFromStore();
            removed.fireSessionDestroyed();
        }
    }

    @Override
    public void addExpirationListener(Consumer<SessionState> listener) {
        if (listener != null) {
            expirationListeners.add(listener);
        }
    }

    @Override
    public int purgeExpired() {
        long now = System.currentTimeMillis();
        Set<String> invalidatedIds = new LinkedHashSet<>();
        int localPurged = 0;

        for (Map.Entry<String, SessionState> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired() && sessions.remove(entry.getKey(), entry.getValue())) {
                localPurged++;
                invalidatedIds.add(entry.getKey());
                SessionState removed = entry.getValue();
                removed.setChangeListener(SessionState.ChangeListener.NO_OP);
                removed.invalidateFromStore();
                removed.fireSessionDestroyed();
                notifyExpirationListeners(removed, entry.getKey());
            }
        }

        List<String> repositoryPurged = repository.purgeExpired(now);
        invalidatedIds.addAll(repositoryPurged);
        for (String sessionId : repositoryPurged) {
            SessionState removed = sessions.remove(sessionId);
            if (removed != null) {
                removed.setChangeListener(SessionState.ChangeListener.NO_OP);
                removed.invalidateFromStore();
                removed.fireSessionDestroyed();
                notifyExpirationListeners(removed, sessionId);
            }
        }

        for (String sessionId : invalidatedIds) {
            scheduleReplication(SessionReplicationMessage.invalidate(nodeId, sessionId));
        }

        int purged = invalidatedIds.size();
        if (purged > 0) {
            log.info("Clustered session purge completed: purged={} localRemaining={}", purged, sessions.size());
        }
        return purged;
    }

    @Override
    public int size() {
        return sessions.size();
    }

    @Override
    public void close() {
        replicationSubscription.close();
        replicationExecutor.shutdownNow();
    }

    private void handleStateMutation(SessionState state, SessionState.MutationType mutationType, String previousSessionId) {
        if (mutationType == SessionState.MutationType.INVALIDATED) {
            repository.delete(state.getId());
            scheduleReplication(SessionReplicationMessage.invalidate(nodeId, state.getId()));
            sessions.remove(state.getId(), state);
            return;
        }
        if (previousSessionId != null && !previousSessionId.equals(state.getId())) {
            repository.delete(previousSessionId);
            scheduleReplication(SessionReplicationMessage.invalidate(nodeId, previousSessionId));
        }
        SessionRecord resolved = persist(state);
        scheduleReplication(SessionReplicationMessage.upsert(nodeId, resolved));
    }

    private SessionRecord persist(SessionState state) {
        SessionRecord candidate = toRecord(state);
        SessionRecord resolved = repository.save(candidate, conflictResolver);
        if (!matchesState(state, resolved) && shouldApplyIncoming(state, resolved)) {
            applyRecord(state, resolved);
        }
        return resolved;
    }

    private SessionRecord toRecord(SessionState state) {
        return new SessionRecord(
                state.getId(),
                state.getOwnerNodeId(),
                state.getStickyRoute(),
                state.getCreationTime(),
                state.getLastAccessedTime(),
                state.getLastModifiedTime(),
                state.getExpiresAtEpochMillis(),
                state.getMaxInactiveIntervalSeconds(),
                state.getVersion(),
                state.isValid(),
                serializer.serialize(state.snapshotAttributes()));
    }

    private SessionState materialize(SessionRecord record) {
        SessionState state = new SessionState(record.id());
        state.applyRecord(record, serializer.deserialize(record.serializedAttributes()));
        state.setChangeListener(this::handleStateMutation);
        return state;
    }

    private void applyRecord(SessionState state, SessionRecord record) {
        state.applyRecord(record, serializer.deserialize(record.serializedAttributes()));
    }

    private boolean matchesState(SessionState state, SessionRecord record) {
        return state.getId().equals(record.id())
                && state.getOwnerNodeId().equals(record.ownerNodeId())
                && state.getStickyRoute().equals(record.stickyRoute())
                && state.getVersion() == record.version()
                && state.getLastModifiedTime() == record.lastModifiedTime()
                && state.getExpiresAtEpochMillis() == record.expiresAtEpochMillis()
                && state.isValid() == record.valid();
    }

    private boolean shouldApplyIncoming(SessionState state, SessionRecord incoming) {
        SessionRecord localRecord = new SessionRecord(
                state.getId(),
                state.getOwnerNodeId(),
                state.getStickyRoute(),
                state.getCreationTime(),
                state.getLastAccessedTime(),
                state.getLastModifiedTime(),
                state.getExpiresAtEpochMillis(),
                state.getMaxInactiveIntervalSeconds(),
                state.getVersion(),
                state.isValid(),
                new byte[0]);
        return LatestVersionSessionConflictResolver.compare(localRecord, incoming) < 0;
    }

    private void applyReplicationMessage(SessionReplicationMessage message) {
        if (message == null) {
            return;
        }
        switch (message.type()) {
            case UPSERT -> applyReplicatedUpsert(message.record());
            case INVALIDATE -> applyReplicatedInvalidation(message.sessionId());
        }
    }

    private void applyReplicatedUpsert(SessionRecord record) {
        if (record == null) {
            return;
        }
        if (record.isExpired(System.currentTimeMillis())) {
            applyReplicatedInvalidation(record.id());
            return;
        }
        SessionState existing = sessions.get(record.id());
        if (existing == null) {
            SessionState materialized = materialize(record);
            SessionState previous = sessions.putIfAbsent(record.id(), materialized);
            if (previous != null && shouldApplyIncoming(previous, record)) {
                applyRecord(previous, record);
            }
            return;
        }
        if (shouldApplyIncoming(existing, record)) {
            applyRecord(existing, record);
        }
    }

    private void applyReplicatedInvalidation(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        SessionState removed = sessions.remove(sessionId);
        if (removed != null) {
            removed.setChangeListener(SessionState.ChangeListener.NO_OP);
            removed.invalidateFromStore();
            removed.fireSessionDestroyed();
        }
    }

    private void expire(String sessionId, SessionState state) {
        repository.delete(sessionId);
        sessions.remove(sessionId, state);
        state.setChangeListener(SessionState.ChangeListener.NO_OP);
        state.invalidateFromStore();
        state.fireSessionDestroyed();
        notifyExpirationListeners(state, sessionId);
        scheduleReplication(SessionReplicationMessage.invalidate(nodeId, sessionId));
    }

    private void notifyExpirationListeners(SessionState state, String sessionId) {
        for (Consumer<SessionState> listener : expirationListeners) {
            try {
                listener.accept(state);
            } catch (Exception e) {
                log.warn("Session expiration listener error for session={}", sessionId, e);
            }
        }
    }

    private void scheduleReplication(SessionReplicationMessage message) {
        replicationExecutor.execute(() -> {
            try {
                replicationChannel.publish(message);
            } catch (Exception e) {
                log.warn("Asynchronous session replication failed for node={} type={} session={}",
                        nodeId, message.type(), message.sessionId(), e);
            }
        });
    }

    private String nextSessionId() {
        String randomId = UUID.randomUUID().toString().replace("-", "");
        if (!stickySessionsEnabled || stickyRoute == null || stickyRoute.isBlank()) {
            return randomId;
        }
        return stickyRoute + "." + randomId;
    }
}
