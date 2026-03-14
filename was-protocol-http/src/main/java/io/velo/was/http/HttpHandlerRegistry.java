package io.velo.was.http;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HttpHandlerRegistry {

    private final Map<String, HttpHandler> getHandlers = new ConcurrentHashMap<>();
    private volatile HttpHandler fallbackHandler = exchange -> HttpResponses.notFound("No route for " + exchange.uri());

    public HttpHandlerRegistry registerGet(String path, HttpHandler handler) {
        getHandlers.put(path, handler);
        return this;
    }

    public HttpHandlerRegistry fallback(HttpHandler handler) {
        this.fallbackHandler = handler;
        return this;
    }

    public HttpHandler resolve(HttpExchange exchange) {
        return getHandlers.getOrDefault(exchange.path(), fallbackHandler);
    }
}
