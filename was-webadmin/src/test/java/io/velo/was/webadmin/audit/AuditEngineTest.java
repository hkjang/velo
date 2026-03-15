package io.velo.was.webadmin.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditEngineTest {

    private AuditEngine engine;

    @BeforeEach
    void setUp() {
        engine = AuditEngine.instance();
        // Note: singleton, events persist across tests but that's OK for append-only testing
    }

    @Test
    void recordAndRetrieveEvents() {
        int before = engine.size();
        engine.record("testuser", "TEST_ACTION", "test-resource", "detail", "127.0.0.1", true);

        assertTrue(engine.size() > before);

        List<AuditEvent> recent = engine.recent(1);
        assertFalse(recent.isEmpty());
        AuditEvent latest = recent.get(0);
        assertEquals("testuser", latest.user());
        assertEquals("TEST_ACTION", latest.action());
        assertEquals("test-resource", latest.resource());
        assertEquals("detail", latest.detail());
        assertEquals("127.0.0.1", latest.sourceIp());
        assertTrue(latest.success());
        assertNotNull(latest.id());
        assertNotNull(latest.timestamp());
    }

    @Test
    void filterByUser() {
        engine.record("alice", "LOGIN", "webadmin", "", "10.0.0.1", true);
        engine.record("bob", "LOGIN", "webadmin", "", "10.0.0.2", true);

        List<AuditEvent> aliceEvents = engine.byUser("alice", 100);
        assertFalse(aliceEvents.isEmpty());
        assertTrue(aliceEvents.stream().allMatch(e -> "alice".equals(e.user())));
    }

    @Test
    void filterByAction() {
        engine.record("user1", "DEPLOY", "app1", "", "10.0.0.1", true);
        engine.record("user1", "UNDEPLOY", "app2", "", "10.0.0.1", true);

        List<AuditEvent> deployEvents = engine.byAction("DEPLOY", 100);
        assertFalse(deployEvents.isEmpty());
        assertTrue(deployEvents.stream().allMatch(e -> "DEPLOY".equals(e.action())));
    }

    @Test
    void toJsonProducesValidArray() {
        engine.record("jsonuser", "JSON_TEST", "res", "d", "1.1.1.1", true);
        String json = engine.toJson(5);
        assertTrue(json.startsWith("["));
        assertTrue(json.endsWith("]"));
        assertTrue(json.contains("\"user\":\"jsonuser\""));
    }

    @Test
    void recordFailedEvent() {
        engine.record("baduser", "LOGIN", "webadmin", "failed attempt", "192.168.1.1", false);
        List<AuditEvent> events = engine.byUser("baduser", 10);
        assertFalse(events.isEmpty());
        assertFalse(events.get(0).success());
    }

    @Test
    void eventToJsonContainsAllFields() {
        engine.record("u", "a", "r", "d", "ip", true);
        AuditEvent e = engine.recent(1).get(0);
        String json = e.toJson();
        assertTrue(json.contains("\"id\":"));
        assertTrue(json.contains("\"timestamp\":"));
        assertTrue(json.contains("\"user\":"));
        assertTrue(json.contains("\"action\":"));
        assertTrue(json.contains("\"resource\":"));
        assertTrue(json.contains("\"success\":true"));
    }
}
