package io.velo.was.tcp.session;

import java.net.SocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a TCP connection context with session state, authentication info,
 * and user-defined attributes.
 */
public class TcpSession {

    private final String sessionId;
    private final SocketAddress remoteAddress;
    private final SocketAddress localAddress;
    private final Instant createdAt;
    private volatile Instant lastActiveAt;
    private volatile boolean authenticated;
    private volatile String authenticatedUser;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public TcpSession(SocketAddress remoteAddress, SocketAddress localAddress) {
        this.sessionId = UUID.randomUUID().toString().replace("-", "");
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
        this.createdAt = Instant.now();
        this.lastActiveAt = this.createdAt;
    }

    public String sessionId() { return sessionId; }
    public SocketAddress remoteAddress() { return remoteAddress; }
    public SocketAddress localAddress() { return localAddress; }
    public Instant createdAt() { return createdAt; }
    public Instant lastActiveAt() { return lastActiveAt; }

    public void touch() { this.lastActiveAt = Instant.now(); }

    public boolean isAuthenticated() { return authenticated; }
    public String authenticatedUser() { return authenticatedUser; }

    public void authenticate(String user) {
        this.authenticated = true;
        this.authenticatedUser = user;
    }

    public void clearAuthentication() {
        this.authenticated = false;
        this.authenticatedUser = null;
    }

    public void setAttribute(String key, Object value) { attributes.put(key, value); }
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) { return (T) attributes.get(key); }
    public void removeAttribute(String key) { attributes.remove(key); }
    public Map<String, Object> attributes() { return Map.copyOf(attributes); }

    @Override
    public String toString() {
        return "TcpSession{id=" + sessionId + ", remote=" + remoteAddress +
                ", authenticated=" + authenticated + "}";
    }
}
