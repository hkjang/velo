package io.velo.was.servlet;

import java.util.Arrays;

public record SessionRecord(
        String id,
        String ownerNodeId,
        String stickyRoute,
        long creationTime,
        long lastAccessedTime,
        long lastModifiedTime,
        long expiresAtEpochMillis,
        int maxInactiveIntervalSeconds,
        long version,
        boolean valid,
        byte[] serializedAttributes
) {

    public SessionRecord {
        serializedAttributes = serializedAttributes == null ? new byte[0] : Arrays.copyOf(serializedAttributes, serializedAttributes.length);
        ownerNodeId = ownerNodeId == null ? "" : ownerNodeId;
        stickyRoute = stickyRoute == null ? "" : stickyRoute;
    }

    @Override
    public byte[] serializedAttributes() {
        return Arrays.copyOf(serializedAttributes, serializedAttributes.length);
    }

    public boolean isExpired(long nowMillis) {
        if (!valid) {
            return true;
        }
        if (maxInactiveIntervalSeconds <= 0) {
            return false;
        }
        return nowMillis >= expiresAtEpochMillis;
    }
}
