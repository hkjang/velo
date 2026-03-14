package io.velo.was.http;

import io.netty.handler.codec.http.FullHttpResponse;

/**
 * Callback for sending an HTTP response asynchronously.
 * Used by async servlet processing to deliver the response
 * after the initial handler call has returned.
 */
@FunctionalInterface
public interface ResponseSink {
    void send(FullHttpResponse response);
}
