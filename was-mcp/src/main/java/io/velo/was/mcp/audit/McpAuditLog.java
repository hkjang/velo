package io.velo.was.mcp.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory ring-buffer audit log for MCP operations.
 *
 * <p>Keeps the most recent {@code maxEntries} audit records. Thread-safe.
 * All mutations are logged to two SLF4J loggers:
 * <ul>
 *   <li>{@code io.velo.was.mcp.audit.McpAuditLog} — standard application log (INFO/WARN)</li>
 *   <li>{@code MCP_AUDIT} — dedicated audit logger that outputs JSON lines,
 *       ideal for routing to a separate file via logback/log4j configuration</li>
 * </ul>
 *
 * <h3>Logback configuration example</h3>
 * <pre>{@code
 * <appender name="MCP_AUDIT_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
 *   <file>logs/mcp-audit.log</file>
 *   <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
 *     <fileNamePattern>logs/mcp-audit.%d{yyyy-MM-dd}.log</fileNamePattern>
 *     <maxHistory>90</maxHistory>
 *   </rollingPolicy>
 *   <encoder><pattern>%msg%n</pattern></encoder>
 * </appender>
 * <logger name="MCP_AUDIT" level="INFO" additivity="false">
 *   <appender-ref ref="MCP_AUDIT_FILE" />
 * </logger>
 * }</pre>
 */
public class McpAuditLog {

    private static final Logger log = LoggerFactory.getLogger(McpAuditLog.class);

    /** Dedicated audit logger — outputs JSON lines for file-based audit trail. */
    private static final Logger auditLogger = LoggerFactory.getLogger("MCP_AUDIT");

    private static final int DEFAULT_MAX_ENTRIES = 10_000;

    private final List<McpAuditEntry> buffer;
    private final int maxEntries;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile McpAuditFileLogger fileLogger;

    public McpAuditLog() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public McpAuditLog(int maxEntries) {
        this.maxEntries = maxEntries;
        this.buffer = new ArrayList<>(Math.min(maxEntries, 1024));
    }

    /**
     * Attach a file-based audit logger for persistent JSON-line output.
     * When set, every audit entry is also written to a daily-rotating file.
     */
    public void setFileLogger(McpAuditFileLogger fileLogger) {
        this.fileLogger = fileLogger;
    }

    /** Get the attached file logger, or null. */
    public McpAuditFileLogger fileLogger() {
        return fileLogger;
    }

    /**
     * Record an audit event.
     */
    public void record(McpAuditEntry entry) {
        lock.writeLock().lock();
        try {
            if (buffer.size() >= maxEntries) {
                buffer.remove(0);
            }
            buffer.add(entry);
        } finally {
            lock.writeLock().unlock();
        }

        // ── Standard application log ──
        if (entry.success()) {
            log.info("MCP audit: method={} tool={} session={} client={} duration={}ms addr={}",
                    entry.method(), entry.toolName(), entry.sessionId(),
                    entry.clientName(), entry.durationMs(), entry.remoteAddr());
        } else {
            log.warn("MCP audit FAIL: method={} tool={} session={} client={} err=[{}] {} addr={}",
                    entry.method(), entry.toolName(), entry.sessionId(),
                    entry.clientName(), entry.errorCode(), entry.errorMsg(), entry.remoteAddr());
        }

        // ── Dedicated audit log (JSON line) ──
        if (auditLogger.isInfoEnabled()) {
            auditLogger.info(entry.toJson());
        }

        // ── File-based persistence (daily-rotating JSON lines) ──
        McpAuditFileLogger fl = fileLogger;
        if (fl != null) {
            fl.write(entry);
        }
    }

    /**
     * Convenience: record a successful call.
     */
    public void recordSuccess(String sessionId, String clientName, String method,
                              String toolName, long durationMs, String remoteAddr) {
        recordSuccess(sessionId, clientName, method, toolName, durationMs, remoteAddr, null);
    }

    /**
     * Record a successful call with prompt/arguments data.
     */
    public void recordSuccess(String sessionId, String clientName, String method,
                              String toolName, long durationMs, String remoteAddr,
                              String prompt) {
        record(new McpAuditEntry(Instant.now(), sessionId, clientName, method,
                toolName, durationMs, true, 0, null, remoteAddr, prompt));
    }

    /**
     * Convenience: record a failed call.
     */
    public void recordFailure(String sessionId, String clientName, String method,
                              String toolName, long durationMs, int errorCode,
                              String errorMsg, String remoteAddr) {
        recordFailure(sessionId, clientName, method, toolName, durationMs, errorCode, errorMsg, remoteAddr, null);
    }

    /**
     * Record a failed call with prompt/arguments data.
     */
    public void recordFailure(String sessionId, String clientName, String method,
                              String toolName, long durationMs, int errorCode,
                              String errorMsg, String remoteAddr, String prompt) {
        record(new McpAuditEntry(Instant.now(), sessionId, clientName, method,
                toolName, durationMs, false, errorCode, errorMsg, remoteAddr, prompt));
    }

    /**
     * Return the most recent {@code limit} entries in reverse chronological order.
     *
     * @param limit  max entries to return
     * @param method optional filter on JSON-RPC method; null for all
     */
    public List<McpAuditEntry> query(int limit, String method) {
        lock.readLock().lock();
        try {
            List<McpAuditEntry> result = new ArrayList<>();
            for (int i = buffer.size() - 1; i >= 0 && result.size() < limit; i--) {
                McpAuditEntry e = buffer.get(i);
                if (method == null || method.equals(e.method())) {
                    result.add(e);
                }
            }
            return Collections.unmodifiableList(result);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Total number of buffered entries. */
    public int size() {
        lock.readLock().lock();
        try {
            return buffer.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
