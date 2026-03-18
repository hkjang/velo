package io.velo.was.servlet;

public record SessionReplicationMessage(
        Type type,
        String originNodeId,
        String sessionId,
        SessionRecord record
) {

    public static SessionReplicationMessage upsert(String originNodeId, SessionRecord record) {
        return new SessionReplicationMessage(Type.UPSERT, originNodeId, record.id(), record);
    }

    public static SessionReplicationMessage invalidate(String originNodeId, String sessionId) {
        return new SessionReplicationMessage(Type.INVALIDATE, originNodeId, sessionId, null);
    }

    public enum Type {
        UPSERT,
        INVALIDATE
    }
}
