package io.velo.was.servlet;

import java.util.function.Consumer;

public interface SessionReplicationChannel extends AutoCloseable {

    SessionReplicationChannel NO_OP = new SessionReplicationChannel() {
        @Override
        public Subscription subscribe(String nodeId, Consumer<SessionReplicationMessage> consumer) {
            return Subscription.NO_OP;
        }

        @Override
        public void publish(SessionReplicationMessage message) {
        }
    };

    Subscription subscribe(String nodeId, Consumer<SessionReplicationMessage> consumer);

    void publish(SessionReplicationMessage message);

    @Override
    default void close() {
    }

    interface Subscription extends AutoCloseable {
        Subscription NO_OP = () -> {
        };

        @Override
        void close();
    }
}
