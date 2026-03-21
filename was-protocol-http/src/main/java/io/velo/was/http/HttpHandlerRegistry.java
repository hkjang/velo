package io.velo.was.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HttpHandlerRegistry {

    private final Map<String, HttpHandler> handlers = new ConcurrentHashMap<>();
    private volatile HttpHandler fallbackHandler = exchange -> HttpResponses.notFound("No route for " + exchange.uri());

    /**
     * Register a handler for a path, served on any HTTP method.
     * The handler itself is responsible for method validation where needed.
     */
    public HttpHandlerRegistry registerGet(String path, HttpHandler handler) {
        handlers.put(path, handler);
        return this;
    }

    /**
     * Alias for {@link #registerGet} — registers a handler for any method at the given path.
     * The handler is expected to check the HTTP method internally.
     */
    public HttpHandlerRegistry register(String path, HttpHandler handler) {
        handlers.put(path, handler);
        return this;
    }

    public HttpHandlerRegistry fallback(HttpHandler handler) {
        this.fallbackHandler = handler;
        return this;
    }

    public HttpHandler resolve(HttpExchange exchange) {
        return handlers.getOrDefault(exchange.path(), fallbackHandler);
    }
}
