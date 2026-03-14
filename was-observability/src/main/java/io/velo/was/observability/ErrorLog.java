package io.velo.was.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured error logger. Writes JSON-formatted error log entries
 * to a dedicated SLF4J logger category.
 * <p>
 * Configure the logger "velo.error" in your logging framework to direct
 * error log output to a separate file.
 */
public final class ErrorLog {

    private static final Logger log = LoggerFactory.getLogger("velo.error");

    private ErrorLog() {
    }

    /**
     * Logs an error entry in JSON format.
     */
    public static void log(ErrorLogEntry entry) {
        log.error(entry.toJson());
    }

    /**
     * Convenience method to log an exception with context.
     */
    public static void log(String logger, String message, Throwable throwable) {
        log(ErrorLogEntry.of(logger, message, throwable));
    }

    /**
     * Convenience method to log an exception with request context.
     */
    public static void log(String logger, String message, Throwable throwable, String requestId, String uri) {
        log(ErrorLogEntry.of(logger, message, throwable, requestId, uri));
    }

    public static Logger logger() {
        return log;
    }
}
