package io.velo.was.mcp.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * In-memory MCP session store with idle-timeout eviction.
 *
 * <p>Sessions are keyed by their string ID. A background task evicts sessions
 * that have been idle longer than {@link #idleTimeout}.
 *
 * <p>For horizontal scaling, replace this implementation with a Redis-backed
 * store that stores serialized {@link McpSession} state.
 */
public class McpSessionManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpSessionManager.class);

    private static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(30);

    private final ConcurrentMap<String, McpSession> sessions = new ConcurrentHashMap<>();
    private final Duration idleTimeout;
    private final ScheduledExecutorService evictionScheduler;

    public McpSessionManager() {
        this(DEFAULT_IDLE_TIMEOUT);
    }

    public McpSessionManager(Duration idleTimeout) {
        this.idleTimeout = idleTimeout;
        this.evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-session-evictor");
            t.setDaemon(true);
            return t;
        });
        evictionScheduler.scheduleAtFixedRate(this::evictExpired, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Create a new session and return it. The session ID is randomly generated.
     */
    public McpSession create(String clientName, String clientVersion, String protocolVersion) {
        String id = UUID.randomUUID().toString();
        McpSession session = new McpSession(id, clientName, clientVersion, protocolVersion);
        sessions.put(id, session);
        log.debug("MCP session created id={} client={}/{}", id, clientName, clientVersion);
        return session;
    }

    /**
     * Look up an existing session by ID. Touches it if found.
     *
     * @return the session, or {@code null} if not found or expired
     */
    public McpSession get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        McpSession session = sessions.get(sessionId);
        if (session != null) session.touch();
        return session;
    }

    /** Remove a session immediately (e.g. on client disconnect). */
    public void remove(String sessionId) {
        McpSession removed = sessions.remove(sessionId);
        if (removed != null) {
            log.debug("MCP session removed id={}", sessionId);
        }
    }

    /** Current number of active sessions. */
    public int size() {
        return sessions.size();
    }

    /** Snapshot of all active sessions (for admin listing). */
    public java.util.List<McpSession> list() {
        return java.util.List.copyOf(sessions.values());
    }

    @Override
    public void close() {
        evictionScheduler.shutdownNow();
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void evictExpired() {
        Instant cutoff = Instant.now().minus(idleTimeout);
        int[] count = {0};
        sessions.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().lastActivityAt().isBefore(cutoff);
            if (expired) {
                log.debug("Evicting idle MCP session id={}", entry.getKey());
                count[0]++;
            }
            return expired;
        });
        if (count[0] > 0) {
            log.info("Evicted {} idle MCP sessions", count[0]);
        }
    }
}
