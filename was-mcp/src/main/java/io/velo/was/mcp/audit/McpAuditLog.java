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
 * All mutations also log to SLF4J at INFO level for downstream shipping.
 *
 * <p>For production, replace with a persistent store (database, Elasticsearch, etc.).
 */
public class McpAuditLog {

    private static final Logger log = LoggerFactory.getLogger(McpAuditLog.class);

    private static final int DEFAULT_MAX_ENTRIES = 10_000;

    private final List<McpAuditEntry> buffer;
    private final int maxEntries;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public McpAuditLog() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public McpAuditLog(int maxEntries) {
        this.maxEntries = maxEntries;
        this.buffer = new ArrayList<>(Math.min(maxEntries, 1024));
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

        if (entry.success()) {
            log.info("MCP audit: method={} tool={} session={} client={} duration={}ms addr={}",
                    entry.method(), entry.toolName(), entry.sessionId(),
                    entry.clientName(), entry.durationMs(), entry.remoteAddr());
        } else {
            log.warn("MCP audit FAIL: method={} tool={} session={} client={} err=[{}] {} addr={}",
                    entry.method(), entry.toolName(), entry.sessionId(),
                    entry.clientName(), entry.errorCode(), entry.errorMsg(), entry.remoteAddr());
        }
    }

    /**
     * Convenience: record a successful call.
     */
    public void recordSuccess(String sessionId, String clientName, String method,
                              String toolName, long durationMs, String remoteAddr) {
        record(new McpAuditEntry(Instant.now(), sessionId, clientName, method,
                toolName, durationMs, true, 0, null, remoteAddr));
    }

    /**
     * Convenience: record a failed call.
     */
    public void recordFailure(String sessionId, String clientName, String method,
                              String toolName, long durationMs, int errorCode,
                              String errorMsg, String remoteAddr) {
        record(new McpAuditEntry(Instant.now(), sessionId, clientName, method,
                toolName, durationMs, false, errorCode, errorMsg, remoteAddr));
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
