package io.velo.was.servlet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class InMemorySessionReplicationChannel implements SessionReplicationChannel {

    private final Map<String, Consumer<SessionReplicationMessage>> subscribers = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(
            Thread.ofVirtual().name("session-replication-channel-", 0).factory());

    @Override
    public Subscription subscribe(String nodeId, Consumer<SessionReplicationMessage> consumer) {
        subscribers.put(nodeId, consumer);
        return () -> subscribers.remove(nodeId, consumer);
    }

    @Override
    public void publish(SessionReplicationMessage message) {
        for (Map.Entry<String, Consumer<SessionReplicationMessage>> entry : subscribers.entrySet()) {
            if (entry.getKey().equals(message.originNodeId())) {
                continue;
            }
            executor.execute(() -> entry.getValue().accept(message));
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        subscribers.clear();
    }
}
