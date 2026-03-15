package io.velo.was.webadmin.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigChangeEngineTest {

    private final ConfigChangeEngine engine = ConfigChangeEngine.instance();

    @Test
    void fullDraftLifecycle() {
        ConfigDraft draft = engine.createDraft("admin", "server.yaml",
                "Update thread pool", Map.of("threading.workerThreads", "16"));

        assertNotNull(draft.id());
        assertEquals(ConfigDraft.DraftStatus.DRAFT, draft.status());
        assertEquals("admin", draft.author());
        assertEquals("server.yaml", draft.target());

        // Validate
        ConfigDraft validated = engine.validate(draft.id(), "admin");
        assertEquals(ConfigDraft.DraftStatus.VALIDATED, validated.status());

        // Review
        ConfigDraft reviewed = engine.review(draft.id(), "reviewer1");
        assertEquals(ConfigDraft.DraftStatus.REVIEWED, reviewed.status());
        assertEquals("reviewer1", reviewed.reviewer());
        assertNotNull(reviewed.reviewedAt());

        // Approve
        ConfigDraft approved = engine.approve(draft.id(), "approver1");
        assertEquals(ConfigDraft.DraftStatus.APPROVED, approved.status());
        assertEquals("approver1", approved.approver());
        assertNotNull(approved.approvedAt());

        // Apply
        ConfigDraft applied = engine.apply(draft.id(), "admin");
        assertEquals(ConfigDraft.DraftStatus.APPLIED, applied.status());
    }

    @Test
    void rollbackAppliedDraft() {
        ConfigDraft draft = engine.createDraft("admin", "listener",
                "Change port", Map.of("port", "9090"));
        engine.validate(draft.id(), "admin");
        engine.review(draft.id(), "admin");
        engine.approve(draft.id(), "admin");
        engine.apply(draft.id(), "admin");

        ConfigDraft rolledBack = engine.rollback(draft.id(), "admin");
        assertEquals(ConfigDraft.DraftStatus.ROLLED_BACK, rolledBack.status());
    }

    @Test
    void rejectDraft() {
        ConfigDraft draft = engine.createDraft("admin", "security",
                "Change auth", Map.of("auth", "ldap"));
        engine.validate(draft.id(), "admin");

        ConfigDraft rejected = engine.reject(draft.id(), "reviewer", "Not approved for production");
        assertEquals(ConfigDraft.DraftStatus.REJECTED, rejected.status());
    }

    @Test
    void getDraft() {
        ConfigDraft draft = engine.createDraft("admin", "config",
                "Test get", Map.of("key", "value"));
        ConfigDraft retrieved = engine.get(draft.id());
        assertNotNull(retrieved);
        assertEquals(draft.id(), retrieved.id());
    }

    @Test
    void listAllAndByStatus() {
        int before = engine.size();
        engine.createDraft("admin", "test", "List test", Map.of("k", "v"));

        List<ConfigDraft> all = engine.listAll();
        assertTrue(all.size() > before);

        List<ConfigDraft> drafts = engine.listByStatus(ConfigDraft.DraftStatus.DRAFT);
        assertTrue(drafts.stream().allMatch(d -> d.status() == ConfigDraft.DraftStatus.DRAFT));
    }

    @Test
    void draftToJsonContainsFields() {
        ConfigDraft draft = engine.createDraft("admin", "target",
                "desc", Map.of("key", "val"));
        String json = draft.toJson();
        assertTrue(json.contains("\"id\":"));
        assertTrue(json.contains("\"author\":\"admin\""));
        assertTrue(json.contains("\"target\":\"target\""));
        assertTrue(json.contains("\"status\":\"DRAFT\""));
        assertTrue(json.contains("\"key\":\"val\""));
    }

    @Test
    void validateNonDraftThrows() {
        ConfigDraft draft = engine.createDraft("admin", "t", "d", Map.of());
        engine.validate(draft.id(), "admin");
        // Already validated, should throw
        assertThrows(IllegalStateException.class, () -> engine.validate(draft.id(), "admin"));
    }

    @Test
    void getNonExistentReturnsNull() {
        assertNull(engine.get("nonexistent-id"));
    }

    @Test
    void findNonExistentDraftThrowsOnAction() {
        assertThrows(IllegalArgumentException.class, () -> engine.validate("nope", "admin"));
        assertThrows(IllegalArgumentException.class, () -> engine.review("nope", "admin"));
        assertThrows(IllegalArgumentException.class, () -> engine.approve("nope", "admin"));
        assertThrows(IllegalArgumentException.class, () -> engine.apply("nope", "admin"));
        assertThrows(IllegalArgumentException.class, () -> engine.rollback("nope", "admin"));
    }
}
