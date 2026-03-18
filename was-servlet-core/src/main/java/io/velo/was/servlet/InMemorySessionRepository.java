package io.velo.was.servlet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySessionRepository implements SessionRepository {

    private final Map<String, SessionRecord> records = new ConcurrentHashMap<>();

    @Override
    public SessionRecord find(String sessionId) {
        return records.get(sessionId);
    }

    @Override
    public SessionRecord save(SessionRecord candidate, SessionConflictResolver conflictResolver) {
        return records.compute(candidate.id(), (ignored, current) -> {
            SessionConflictResolver resolver = conflictResolver == null
                    ? LatestVersionSessionConflictResolver.INSTANCE
                    : conflictResolver;
            return resolver.resolve(current, candidate);
        });
    }

    @Override
    public void delete(String sessionId) {
        if (sessionId != null) {
            records.remove(sessionId);
        }
    }

    @Override
    public List<String> purgeExpired(long nowMillis) {
        List<String> purgedIds = new ArrayList<>();
        for (Map.Entry<String, SessionRecord> entry : records.entrySet()) {
            if (entry.getValue().isExpired(nowMillis) && records.remove(entry.getKey(), entry.getValue())) {
                purgedIds.add(entry.getKey());
            }
        }
        return purgedIds;
    }
}
