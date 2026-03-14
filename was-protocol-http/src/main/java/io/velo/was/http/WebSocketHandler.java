package io.velo.was.http;

/**
 * Callback interface for handling WebSocket lifecycle events.
 * Implementations are registered per-path via {@link WebSocketHandlerRegistry}.
 */
public interface WebSocketHandler {

    /** Called when a new WebSocket connection is established. */
    default void onOpen(WebSocketSession session) {}

    /** Called when a text message is received. */
    default void onText(WebSocketSession session, String message) {}

    /** Called when a binary message is received. */
    default void onBinary(WebSocketSession session, byte[] data) {}

    /** Called when the remote endpoint sends a close frame or the connection is lost. */
    default void onClose(WebSocketSession session, int statusCode, String reason) {}

    /** Called when an error occurs on the WebSocket connection. */
    default void onError(WebSocketSession session, Throwable cause) {}
}
