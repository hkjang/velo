package io.velo.was.http;

import io.netty.handler.codec.http.FullHttpResponse;

public interface HttpHandler {
    FullHttpResponse handle(HttpExchange exchange);
}

