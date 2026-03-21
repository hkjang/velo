package io.velo.was.http;

/**
 * Sink for pushing Server-Sent Events to a connected client.
 * Implementations are thread-safe; events may be emitted from any thread.
 */
public interface SseSink {

    /**
     * Emit an unnamed event with the given data payload.
     * Multi-line data is automatically split across "data:" lines.
     */
    void emit(String data);

    /**
     * Emit a named event with the given data payload.
     *
     * @param eventType SSE event type field (e.g. "message", "error")
     * @param data      JSON or text payload
     */
    void emit(String eventType, String data);

    /**
     * Send a comment line (": ping\n\n") to keep the connection alive.
     * Browsers and proxies typically reset idle timers on any SSE frame.
     */
    void ping();

    /** Returns {@code true} if the underlying channel is still open. */
    boolean isOpen();

    /**
     * Close the SSE stream gracefully by flushing any buffered events
     * and writing the HTTP terminal chunk, then closing the channel.
     */
    void close();
}
