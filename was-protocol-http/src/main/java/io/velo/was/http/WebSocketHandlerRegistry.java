package io.velo.was.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry that maps URI paths to {@link WebSocketHandler} instances.
 */
public class WebSocketHandlerRegistry {

    private final Map<String, WebSocketHandler> handlers = new ConcurrentHashMap<>();

    public WebSocketHandlerRegistry register(String path, WebSocketHandler handler) {
        handlers.put(path, handler);
        return this;
    }

    public WebSocketHandler resolve(String path) {
        return handlers.get(path);
    }

    public boolean hasHandler(String path) {
        return handlers.containsKey(path);
    }

    public boolean isEmpty() {
        return handlers.isEmpty();
    }
}
