package io.velo.was.servlet;

import java.util.function.Consumer;

public interface HttpSessionStore extends AutoCloseable {

    SessionState find(String sessionId);

    SessionState create();

    String changeSessionId(SessionState state);

    void invalidate(String sessionId);

    void addExpirationListener(Consumer<SessionState> listener);

    int purgeExpired();

    int size();

    @Override
    default void close() {
    }
}
