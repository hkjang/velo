package io.velo.was.observability;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructuredLogTest {

    @Test
    void accessLogEntryProducesValidJson() {
        AccessLogEntry entry = new AccessLogEntry(
                Instant.parse("2025-01-01T00:00:00Z"),
                "GET", "/api/test", "HTTP/1.1",
                200, 1234, 15,
                "127.0.0.1", "Mozilla/5.0", "https://example.com", "req-001");

        String json = entry.toJson();
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"type\":\"access\""));
        assertTrue(json.contains("\"method\":\"GET\""));
        assertTrue(json.contains("\"uri\":\"/api/test\""));
        assertTrue(json.contains("\"status\":200"));
        assertTrue(json.contains("\"contentLength\":1234"));
        assertTrue(json.contains("\"durationMs\":15"));
        assertTrue(json.contains("\"remoteAddress\":\"127.0.0.1\""));
        assertTrue(json.contains("\"userAgent\":\"Mozilla/5.0\""));
        assertTrue(json.contains("\"referer\":\"https://example.com\""));
        assertTrue(json.contains("\"requestId\":\"req-001\""));
    }

    @Test
    void accessLogEntryHandlesNullOptionalFields() {
        AccessLogEntry entry = new AccessLogEntry(
                Instant.now(), "POST", "/submit", "HTTP/1.1",
                201, 0, 5, "10.0.0.1", null, null, null);

        String json = entry.toJson();
        assertTrue(json.contains("\"method\":\"POST\""));
        assertFalse(json.contains("\"userAgent\""));
        assertFalse(json.contains("\"referer\""));
        assertFalse(json.contains("\"requestId\""));
    }

    @Test
    void accessLogEntryCombinedLogFormat() {
        AccessLogEntry entry = new AccessLogEntry(
                Instant.parse("2025-06-15T10:30:00Z"),
                "GET", "/index.html", "HTTP/1.1",
                200, 5678, 3, "192.168.1.1", "curl/7.68", null, null);

        String clf = entry.toCombinedLogFormat();
        assertTrue(clf.contains("192.168.1.1"));
        assertTrue(clf.contains("GET /index.html HTTP/1.1"));
        assertTrue(clf.contains("200 5678"));
    }

    @Test
    void accessLogEscapesSpecialCharacters() {
        AccessLogEntry entry = new AccessLogEntry(
                Instant.now(), "GET", "/path?q=\"test\"&a=b", "HTTP/1.1",
                200, 0, 1, "127.0.0.1", null, null, null);

        String json = entry.toJson();
        assertFalse(json.contains("\"test\"")); // Should be escaped
        assertTrue(json.contains("\\\"test\\\"")); // Escaped quotes
    }

    @Test
    void errorLogEntryProducesValidJson() {
        ErrorLogEntry entry = ErrorLogEntry.of(
                "io.velo.was.Test", "Something failed",
                new RuntimeException("boom"), "req-002", "/api/fail");

        String json = entry.toJson();
        assertTrue(json.contains("\"type\":\"error\""));
        assertTrue(json.contains("\"level\":\"ERROR\""));
        assertTrue(json.contains("\"message\":\"Something failed\""));
        assertTrue(json.contains("\"exceptionClass\":\"java.lang.RuntimeException\""));
        assertTrue(json.contains("\"exceptionMessage\":\"boom\""));
        assertTrue(json.contains("\"stackTrace\":"));
        assertTrue(json.contains("\"requestId\":\"req-002\""));
        assertTrue(json.contains("\"uri\":\"/api/fail\""));
    }

    @Test
    void errorLogEntryHandlesNullThrowable() {
        ErrorLogEntry entry = ErrorLogEntry.of("test.Logger", "warning message", null);

        String json = entry.toJson();
        assertTrue(json.contains("\"message\":\"warning message\""));
        assertFalse(json.contains("\"exceptionClass\""));
        assertFalse(json.contains("\"stackTrace\""));
    }

    @Test
    void auditLogEntryProducesValidJson() {
        AuditLogEntry entry = AuditLogEntry.of(
                "DEPLOY", "admin", "sample-app", "SUCCESS",
                Map.of("contextPath", "/app", "version", "1.0"));

        String json = entry.toJson();
        assertTrue(json.contains("\"type\":\"audit\""));
        assertTrue(json.contains("\"action\":\"DEPLOY\""));
        assertTrue(json.contains("\"actor\":\"admin\""));
        assertTrue(json.contains("\"target\":\"sample-app\""));
        assertTrue(json.contains("\"result\":\"SUCCESS\""));
        assertTrue(json.contains("\"details\":{"));
        assertTrue(json.contains("\"contextPath\":\"/app\""));
        assertTrue(json.contains("\"version\":\"1.0\""));
    }

    @Test
    void auditLogEntryWithoutDetails() {
        AuditLogEntry entry = AuditLogEntry.of("SHUTDOWN", "system", "server", "SUCCESS");

        String json = entry.toJson();
        assertTrue(json.contains("\"action\":\"SHUTDOWN\""));
        assertFalse(json.contains("\"details\""));
    }

    @Test
    void auditLogEntryWithEmptyDetails() {
        AuditLogEntry entry = AuditLogEntry.of("START", "system", "server", "OK", Map.of());

        String json = entry.toJson();
        assertFalse(json.contains("\"details\""));
    }
}
