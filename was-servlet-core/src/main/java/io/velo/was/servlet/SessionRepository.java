package io.velo.was.servlet;

import java.util.List;

public interface SessionRepository {

    SessionRecord find(String sessionId);

    SessionRecord save(SessionRecord candidate, SessionConflictResolver conflictResolver);

    void delete(String sessionId);

    List<String> purgeExpired(long nowMillis);
}
