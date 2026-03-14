package io.velo.was.observability;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

/**
 * Represents a structured error log entry.
 */
public record ErrorLogEntry(
        Instant timestamp,
        String level,
        String logger,
        String message,
        String exceptionClass,
        String exceptionMessage,
        String stackTrace,
        String requestId,
        String uri
) {
    public static ErrorLogEntry of(String logger, String message, Throwable throwable) {
        return of(logger, message, throwable, null, null);
    }

    public static ErrorLogEntry of(String logger, String message, Throwable throwable, String requestId, String uri) {
        String exClass = null;
        String exMessage = null;
        String stack = null;
        if (throwable != null) {
            exClass = throwable.getClass().getName();
            exMessage = throwable.getMessage();
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            stack = sw.toString();
        }
        return new ErrorLogEntry(
                Instant.now(), "ERROR", logger, message,
                exClass, exMessage, stack, requestId, uri);
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"timestamp\":\"").append(timestamp)
                .append("\",\"type\":\"error\"")
                .append(",\"level\":\"").append(level)
                .append("\",\"logger\":\"").append(escapeJson(logger))
                .append("\",\"message\":\"").append(escapeJson(message)).append("\"");
        if (exceptionClass != null) {
            sb.append(",\"exceptionClass\":\"").append(escapeJson(exceptionClass)).append("\"");
        }
        if (exceptionMessage != null) {
            sb.append(",\"exceptionMessage\":\"").append(escapeJson(exceptionMessage)).append("\"");
        }
        if (stackTrace != null) {
            sb.append(",\"stackTrace\":\"").append(escapeJson(stackTrace)).append("\"");
        }
        if (requestId != null) {
            sb.append(",\"requestId\":\"").append(requestId).append("\"");
        }
        if (uri != null) {
            sb.append(",\"uri\":\"").append(escapeJson(uri)).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
