package io.velo.was.http;

/**
 * Handles an SSE (Server-Sent Events) connection.
 *
 * <p>Called once when a client opens a GET endpoint with
 * {@code Accept: text/event-stream}. The implementation receives a
 * {@link SseSink} and may hold a reference to it for the lifetime of
 * the connection to push events asynchronously from any thread.
 *
 * <p>The HTTP response headers are written before this method is called;
 * the implementation should <em>not</em> attempt to send headers itself.
 */
@FunctionalInterface
public interface SseHandler {

    /**
     * Called when the SSE connection is established.
     *
     * @param exchange the original HTTP exchange (method, headers, path, etc.)
     * @param sink     live sink for sending events; valid until the channel closes
     */
    void onConnect(HttpExchange exchange, SseSink sink);
}
