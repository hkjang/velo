package io.velo.was.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that maps URL paths to {@link SseHandler} instances.
 *
 * <p>Path matching is exact (no wildcards). Register handlers before the
 * server starts; the registry is read-only during normal operation.
 */
public class SseHandlerRegistry {

    private final Map<String, SseHandler> handlers = new ConcurrentHashMap<>();

    /**
     * Register an SSE handler for the given exact path.
     *
     * @param path    e.g. {@code "/mcp"}
     * @param handler handler to invoke on connection
     * @return {@code this} for chaining
     */
    public SseHandlerRegistry register(String path, SseHandler handler) {
        handlers.put(path, handler);
        return this;
    }

    /**
     * Resolve the handler for the given path, or {@code null} if none matches.
     */
    public SseHandler resolve(String path) {
        return handlers.get(path);
    }
}
