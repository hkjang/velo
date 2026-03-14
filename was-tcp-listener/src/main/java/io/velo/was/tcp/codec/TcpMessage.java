package io.velo.was.tcp.codec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic TCP message wrapper carrying a message type, headers, and binary payload.
 */
public class TcpMessage {

    private final String messageType;
    private final Map<String, String> headers;
    private final byte[] payload;

    public TcpMessage(String messageType, byte[] payload) {
        this(messageType, Collections.emptyMap(), payload);
    }

    public TcpMessage(String messageType, Map<String, String> headers, byte[] payload) {
        this.messageType = messageType;
        this.headers = headers != null ? Map.copyOf(headers) : Collections.emptyMap();
        this.payload = payload;
    }

    public String messageType() { return messageType; }
    public Map<String, String> headers() { return headers; }
    public byte[] payload() { return payload; }
    public int payloadLength() { return payload != null ? payload.length : 0; }

    @Override
    public String toString() {
        return "TcpMessage{type='" + messageType + "', headers=" + headers.size() +
                ", payloadLen=" + payloadLength() + "}";
    }
}
