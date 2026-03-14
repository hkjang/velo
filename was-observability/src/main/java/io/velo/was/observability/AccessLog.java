package io.velo.was.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured access logger. Writes JSON-formatted access log entries
 * to a dedicated SLF4J logger category.
 * <p>
 * Configure the logger "velo.access" in your logging framework to direct
 * access log output to a separate file.
 */
public final class AccessLog {

    private static final Logger log = LoggerFactory.getLogger("velo.access");

    private AccessLog() {
    }

    /**
     * Logs an access entry in JSON format.
     */
    public static void log(AccessLogEntry entry) {
        if (log.isInfoEnabled()) {
            log.info(entry.toJson());
        }
    }

    /**
     * Returns the underlying SLF4J logger for custom configuration checks.
     */
    public static Logger logger() {
        return log;
    }
}
