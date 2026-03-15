package io.velo.was.webadmin.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * Append-only audit engine that records all administrative actions.
 * <p>
 * Thread-safe singleton. Events are stored in memory (bounded) and logged
 * to the structured audit log. Future phases will add persistent storage
 * (file, database) and export capabilities.
 */
public final class AuditEngine {

    private static final Logger auditLog = LoggerFactory.getLogger("velo.audit");
    private static final AuditEngine INSTANCE = new AuditEngine();
    private static final int MAX_EVENTS = 10_000;

    private final ConcurrentLinkedDeque<AuditEvent> events = new ConcurrentLinkedDeque<>();

    private AuditEngine() {
    }

    public static AuditEngine instance() {
        return INSTANCE;
    }

    /**
     * Records an audit event.
     */
    public void record(String user, String action, String resource, String detail,
                       String sourceIp, boolean success) {
        AuditEvent event = new AuditEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                user, action, resource, detail, sourceIp, success
        );
        events.addFirst(event);

        // Trim to bounded size
        while (events.size() > MAX_EVENTS) {
            events.removeLast();
        }

        // Write to audit log
        auditLog.info("[AUDIT] user={} action={} resource={} success={} ip={} detail={}",
                user, action, resource, success, sourceIp, detail);
    }

    /**
     * Returns the most recent events (newest first), up to the given limit.
     */
    public List<AuditEvent> recent(int limit) {
        return events.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Returns events filtered by user.
     */
    public List<AuditEvent> byUser(String user, int limit) {
        return events.stream()
                .filter(e -> user.equals(e.user()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Returns events filtered by action type.
     */
    public List<AuditEvent> byAction(String action, int limit) {
        return events.stream()
                .filter(e -> action.equals(e.action()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Returns all events as JSON array.
     */
    public String toJson(int limit) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (AuditEvent event : events) {
            if (limit-- <= 0) break;
            if (!first) sb.append(',');
            first = false;
            sb.append(event.toJson());
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Total number of recorded events.
     */
    public int size() {
        return events.size();
    }
}
