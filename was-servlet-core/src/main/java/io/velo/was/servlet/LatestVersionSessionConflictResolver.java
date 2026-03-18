package io.velo.was.servlet;

public final class LatestVersionSessionConflictResolver implements SessionConflictResolver {

    public static final LatestVersionSessionConflictResolver INSTANCE = new LatestVersionSessionConflictResolver();

    private LatestVersionSessionConflictResolver() {
    }

    @Override
    public SessionRecord resolve(SessionRecord current, SessionRecord candidate) {
        if (current == null) {
            return candidate;
        }
        return compare(current, candidate) >= 0 ? current : candidate;
    }

    static int compare(SessionRecord left, SessionRecord right) {
        if (left == right) {
            return 0;
        }
        int versionComparison = Long.compare(left.version(), right.version());
        if (versionComparison != 0) {
            return versionComparison;
        }
        int modifiedComparison = Long.compare(left.lastModifiedTime(), right.lastModifiedTime());
        if (modifiedComparison != 0) {
            return modifiedComparison;
        }
        int expiresComparison = Long.compare(left.expiresAtEpochMillis(), right.expiresAtEpochMillis());
        if (expiresComparison != 0) {
            return expiresComparison;
        }
        int ownerComparison = left.ownerNodeId().compareTo(right.ownerNodeId());
        if (ownerComparison != 0) {
            return ownerComparison;
        }
        return left.id().compareTo(right.id());
    }
}
