package io.velo.was.mcp.gateway;

import io.velo.was.mcp.audit.McpAuditEntry;
import io.velo.was.mcp.audit.McpAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central gateway service that monitors and audits MCP traffic originating from
 * deployed applications (WAR files).
 *
 * <p>When a user-deployed application exposes MCP endpoints (e.g. at {@code /mcp}),
 * the servlet container's {@link McpAppTrafficInterceptor} detects MCP JSON-RPC
 * requests/responses and reports them here for unified monitoring.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li>Per-application endpoint registration and discovery</li>
 *   <li>Per-application session tracking</li>
 *   <li>Centralized audit logging (shared ring buffer)</li>
 *   <li>Traffic statistics (request counts, errors, latency)</li>
 * </ul>
 */
public class McpAppGatewayService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpAppGatewayService.class);

    /** Registered application MCP endpoints. Key = contextPath. */
    private final ConcurrentHashMap<String, McpAppEndpoint> endpoints = new ConcurrentHashMap<>();

    /** Per-app active session tracking. Key = contextPath + ":" + sessionId. */
    private final ConcurrentHashMap<String, McpAppSession> sessions = new ConcurrentHashMap<>();

    /** Shared audit log (same instance as the built-in MCP audit log). */
    private final McpAuditLog auditLog;

    public McpAppGatewayService(McpAuditLog auditLog) {
        this.auditLog = auditLog;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Endpoint registration
    // ═══════════════════════════════════════════════════════════════════════

    /** Register or update an application MCP endpoint. Called when MCP traffic is detected. */
    public McpAppEndpoint registerEndpoint(String contextPath, String appName) {
        return endpoints.computeIfAbsent(contextPath, cp -> {
            log.info("App MCP endpoint discovered: contextPath={} app={}", cp, appName);
            return new McpAppEndpoint(cp, appName);
        });
    }

    /** Remove an endpoint (e.g. when application is undeployed). */
    public void removeEndpoint(String contextPath) {
        McpAppEndpoint removed = endpoints.remove(contextPath);
        if (removed != null) {
            // Remove associated sessions
            sessions.entrySet().removeIf(e -> e.getValue().contextPath().equals(contextPath));
            log.info("App MCP endpoint removed: contextPath={}", contextPath);
        }
    }

    /** List all registered app MCP endpoints. */
    public List<McpAppEndpoint> listEndpoints() {
        return List.copyOf(endpoints.values());
    }

    /** Get endpoint by context path. */
    public McpAppEndpoint getEndpoint(String contextPath) {
        return endpoints.get(contextPath);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Session tracking
    // ═══════════════════════════════════════════════════════════════════════

    /** Track a session for an application MCP endpoint. */
    public McpAppSession trackSession(String contextPath, String sessionId,
                                       String clientName, String clientVersion) {
        String key = contextPath + ":" + sessionId;
        return sessions.computeIfAbsent(key, k -> {
            log.debug("App MCP session tracked: ctx={} session={} client={}/{}",
                    contextPath, sessionId, clientName, clientVersion);
            return new McpAppSession(contextPath, sessionId, clientName, clientVersion);
        });
    }

    /** Touch a session (update last activity). */
    public void touchSession(String contextPath, String sessionId) {
        String key = contextPath + ":" + sessionId;
        McpAppSession session = sessions.get(key);
        if (session != null) {
            session.touch();
        }
    }

    /** Remove a session. */
    public void removeSession(String contextPath, String sessionId) {
        sessions.remove(contextPath + ":" + sessionId);
    }

    /** List all active app MCP sessions. */
    public List<McpAppSession> listSessions() {
        return List.copyOf(sessions.values());
    }

    /** List sessions for a specific application. */
    public List<McpAppSession> listSessions(String contextPath) {
        return sessions.values().stream()
                .filter(s -> s.contextPath().equals(contextPath))
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Traffic recording
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Record a MCP request/response from an application endpoint.
     * This writes to the shared audit log with the app context prefix.
     */
    /**
     * Record MCP traffic without prompt data (backwards-compatible).
     */
    public void recordTraffic(String contextPath, String appName,
                              String sessionId, String clientName,
                              String method, String toolName,
                              long durationMs, boolean success,
                              int errorCode, String errorMsg,
                              String remoteAddr) {
        recordTraffic(contextPath, appName, sessionId, clientName, method, toolName,
                durationMs, success, errorCode, errorMsg, remoteAddr, null);
    }

    /**
     * Record MCP traffic with prompt/arguments data for audit logging.
     */
    public void recordTraffic(String contextPath, String appName,
                              String sessionId, String clientName,
                              String method, String toolName,
                              long durationMs, boolean success,
                              int errorCode, String errorMsg,
                              String remoteAddr, String prompt) {
        // Update endpoint stats
        McpAppEndpoint ep = endpoints.get(contextPath);
        if (ep != null) {
            ep.recordRequest(durationMs, success);
        }

        // Touch session
        if (sessionId != null) {
            touchSession(contextPath, sessionId);
        }

        // Write to shared audit log with app-prefixed source
        String auditMethod = "[" + contextPath + "] " + method;
        if (success) {
            auditLog.recordSuccess(sessionId, clientName, auditMethod, toolName, durationMs, remoteAddr, prompt);
        } else {
            auditLog.recordFailure(sessionId, clientName, auditMethod, toolName, durationMs,
                    errorCode, errorMsg, remoteAddr, prompt);
        }
    }

    @Override
    public void close() {
        endpoints.clear();
        sessions.clear();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Inner types
    // ═══════════════════════════════════════════════════════════════════════

    /** Represents a discovered application MCP endpoint. */
    public static final class McpAppEndpoint {
        private final String contextPath;
        private final String appName;
        private final Instant discoveredAt;
        private final AtomicLong totalRequests = new AtomicLong();
        private final AtomicLong totalErrors = new AtomicLong();
        private final AtomicLong totalDurationMs = new AtomicLong();
        private volatile Instant lastRequestAt;

        public McpAppEndpoint(String contextPath, String appName) {
            this.contextPath = contextPath;
            this.appName = appName;
            this.discoveredAt = Instant.now();
            this.lastRequestAt = discoveredAt;
        }

        public String contextPath() { return contextPath; }
        public String appName() { return appName; }
        public Instant discoveredAt() { return discoveredAt; }
        public long totalRequests() { return totalRequests.get(); }
        public long totalErrors() { return totalErrors.get(); }
        public Instant lastRequestAt() { return lastRequestAt; }

        public double avgDurationMs() {
            long reqs = totalRequests.get();
            return reqs == 0 ? 0 : (double) totalDurationMs.get() / reqs;
        }

        void recordRequest(long durationMs, boolean success) {
            totalRequests.incrementAndGet();
            totalDurationMs.addAndGet(durationMs);
            lastRequestAt = Instant.now();
            if (!success) totalErrors.incrementAndGet();
        }

        public String toJson() {
            return "{\"contextPath\":\"" + escape(contextPath) + "\""
                    + ",\"appName\":\"" + escape(appName) + "\""
                    + ",\"discoveredAt\":\"" + discoveredAt + "\""
                    + ",\"totalRequests\":" + totalRequests.get()
                    + ",\"totalErrors\":" + totalErrors.get()
                    + ",\"avgDurationMs\":" + String.format("%.1f", avgDurationMs())
                    + ",\"lastRequestAt\":\"" + lastRequestAt + "\""
                    + "}";
        }

        private static String escape(String s) {
            return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }
    }

    /** Represents a tracked session from an application MCP endpoint. */
    public static final class McpAppSession {
        private final String contextPath;
        private final String sessionId;
        private final String clientName;
        private final String clientVersion;
        private final Instant createdAt;
        private volatile Instant lastActivityAt;

        public McpAppSession(String contextPath, String sessionId,
                             String clientName, String clientVersion) {
            this.contextPath = contextPath;
            this.sessionId = sessionId;
            this.clientName = clientName;
            this.clientVersion = clientVersion;
            this.createdAt = Instant.now();
            this.lastActivityAt = createdAt;
        }

        public String contextPath() { return contextPath; }
        public String sessionId() { return sessionId; }
        public String clientName() { return clientName; }
        public String clientVersion() { return clientVersion; }
        public Instant createdAt() { return createdAt; }
        public Instant lastActivityAt() { return lastActivityAt; }

        public void touch() { this.lastActivityAt = Instant.now(); }

        public String toJson() {
            return "{\"contextPath\":\"" + escape(contextPath) + "\""
                    + ",\"sessionId\":\"" + escape(sessionId) + "\""
                    + ",\"clientName\":\"" + escape(clientName) + "\""
                    + ",\"clientVersion\":\"" + escape(clientVersion) + "\""
                    + ",\"createdAt\":\"" + createdAt + "\""
                    + ",\"lastActivityAt\":\"" + lastActivityAt + "\""
                    + "}";
        }

        private static String escape(String s) {
            return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }
    }
}
