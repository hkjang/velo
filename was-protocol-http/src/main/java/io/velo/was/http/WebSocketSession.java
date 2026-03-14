package io.velo.was.http;

import java.net.SocketAddress;

/**
 * Represents an active WebSocket session.
 * Provides methods to send messages and control the connection.
 */
public interface WebSocketSession {

    /** Unique session identifier. */
    String id();

    /** The request URI path that initiated this WebSocket connection. */
    String path();

    /** Remote address of the peer. */
    SocketAddress remoteAddress();

    /** Send a text message to the remote endpoint. */
    void sendText(String message);

    /** Send a binary message to the remote endpoint. */
    void sendBinary(byte[] data);

    /** Close the session with a normal closure (1000). */
    void close();

    /** Close the session with a specific status code and reason. */
    void close(int statusCode, String reason);

    /** Returns {@code true} if the underlying connection is still active. */
    boolean isOpen();
}
