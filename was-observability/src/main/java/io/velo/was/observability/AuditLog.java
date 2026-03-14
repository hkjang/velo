package io.velo.was.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Structured audit logger. Writes JSON-formatted audit log entries
 * to a dedicated SLF4J logger category.
 * <p>
 * Configure the logger "velo.audit" in your logging framework to direct
 * audit log output to a separate file.
 */
public final class AuditLog {

    private static final Logger log = LoggerFactory.getLogger("velo.audit");

    private AuditLog() {
    }

    /**
     * Logs an audit entry in JSON format.
     */
    public static void log(AuditLogEntry entry) {
        log.info(entry.toJson());
    }

    /**
     * Convenience method for simple audit events.
     */
    public static void log(String action, String actor, String target, String result) {
        log(AuditLogEntry.of(action, actor, target, result));
    }

    /**
     * Convenience method for audit events with details.
     */
    public static void log(String action, String actor, String target, String result, Map<String, String> details) {
        log(AuditLogEntry.of(action, actor, target, result, details));
    }

    public static Logger logger() {
        return log;
    }
}
