package io.velo.was.mcp.session;

import io.velo.was.http.SseSink;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a single MCP client session.
 *
 * <p>A session is created on successful {@code initialize} and expires after
 * a configurable idle timeout. The optional {@link SseSink} is set when the
 * client opens a GET SSE connection associated with the session.
 */
public final class McpSession {

    private final String id;
    private final String clientName;
    private final String clientVersion;
    private final String protocolVersion;
    private final Instant createdAt;

    /** Live SSE sink; null if no SSE connection is open for this session. */
    private final AtomicReference<SseSink> sseSink = new AtomicReference<>();
    private volatile Instant lastActivityAt;
    private volatile boolean initialized;

    public McpSession(String id, String clientName, String clientVersion, String protocolVersion) {
        this.id = id;
        this.clientName = clientName;
        this.clientVersion = clientVersion;
        this.protocolVersion = protocolVersion;
        this.createdAt = Instant.now();
        this.lastActivityAt = createdAt;
        this.initialized = false;
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public String id() { return id; }
    public String clientName() { return clientName; }
    public String clientVersion() { return clientVersion; }
    public String protocolVersion() { return protocolVersion; }
    public Instant createdAt() { return createdAt; }
    public Instant lastActivityAt() { return lastActivityAt; }
    public boolean isInitialized() { return initialized; }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /** Called after the client sends the {@code notifications/initialized} notification. */
    public void markInitialized() {
        this.initialized = true;
        touch();
    }

    /** Update the last activity timestamp. */
    public void touch() {
        this.lastActivityAt = Instant.now();
    }

    // ── SSE link ─────────────────────────────────────────────────────────────

    /** Associate an SSE sink with this session. Replaces any existing sink. */
    public void setSseSink(SseSink sink) {
        sseSink.set(sink);
    }

    /** Push a JSON-RPC notification to the client via SSE, if a sink is connected. */
    public void pushNotification(String notificationJson) {
        SseSink sink = sseSink.get();
        if (sink != null && sink.isOpen()) {
            sink.emit("message", notificationJson);
        }
    }

    /** {@code true} if an active SSE connection is open for this session. */
    public boolean hasSseConnection() {
        SseSink sink = sseSink.get();
        return sink != null && sink.isOpen();
    }
}
