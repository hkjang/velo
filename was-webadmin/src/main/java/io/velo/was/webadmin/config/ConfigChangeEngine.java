package io.velo.was.webadmin.config;

import io.velo.was.webadmin.audit.AuditEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Engine managing the configuration change workflow:
 * Draft → Validate → Review → Approve → Apply → (Rollback).
 * <p>
 * Thread-safe. Stores drafts in memory; future phases will
 * add persistent storage with versioning.
 */
public final class ConfigChangeEngine {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangeEngine.class);
    private static final ConfigChangeEngine INSTANCE = new ConfigChangeEngine();

    private final Map<String, ConfigDraft> drafts = new ConcurrentHashMap<>();

    private ConfigChangeEngine() {
    }

    public static ConfigChangeEngine instance() {
        return INSTANCE;
    }

    /**
     * Creates a new configuration change draft.
     */
    public ConfigDraft createDraft(String author, String target, String description,
                                   Map<String, String> changes) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        ConfigDraft draft = new ConfigDraft(
                id, author, Instant.now(), target, description,
                new LinkedHashMap<>(changes),
                ConfigDraft.DraftStatus.DRAFT,
                null, null, null, null
        );
        drafts.put(id, draft);
        log.info("Config draft created: id={} target={} by={}", id, target, author);
        AuditEngine.instance().record(author, "CREATE_DRAFT", target, description, "", true);
        return draft;
    }

    /**
     * Validates a draft (checks syntax, dependencies, policies).
     */
    public ConfigDraft validate(String draftId, String user) {
        ConfigDraft draft = drafts.get(draftId);
        if (draft == null) throw new IllegalArgumentException("Draft not found: " + draftId);
        if (draft.status() != ConfigDraft.DraftStatus.DRAFT) {
            throw new IllegalStateException("Draft is not in DRAFT status");
        }

        // Phase 1: always passes validation
        ConfigDraft validated = new ConfigDraft(
                draft.id(), draft.author(), draft.createdAt(), draft.target(),
                draft.description(), draft.changes(),
                ConfigDraft.DraftStatus.VALIDATED,
                null, null, null, null
        );
        drafts.put(draftId, validated);
        AuditEngine.instance().record(user, "VALIDATE_DRAFT", draft.target(), "Draft " + draftId, "", true);
        return validated;
    }

    /**
     * Reviews a validated draft (adds reviewer).
     */
    public ConfigDraft review(String draftId, String reviewer) {
        ConfigDraft draft = drafts.get(draftId);
        if (draft == null) throw new IllegalArgumentException("Draft not found: " + draftId);

        ConfigDraft reviewed = new ConfigDraft(
                draft.id(), draft.author(), draft.createdAt(), draft.target(),
                draft.description(), draft.changes(),
                ConfigDraft.DraftStatus.REVIEWED,
                reviewer, Instant.now(), null, null
        );
        drafts.put(draftId, reviewed);
        AuditEngine.instance().record(reviewer, "REVIEW_DRAFT", draft.target(), "Draft " + draftId, "", true);
        return reviewed;
    }

    /**
     * Approves a reviewed draft.
     */
    public ConfigDraft approve(String draftId, String approver) {
        ConfigDraft draft = drafts.get(draftId);
        if (draft == null) throw new IllegalArgumentException("Draft not found: " + draftId);

        ConfigDraft approved = new ConfigDraft(
                draft.id(), draft.author(), draft.createdAt(), draft.target(),
                draft.description(), draft.changes(),
                ConfigDraft.DraftStatus.APPROVED,
                draft.reviewer(), draft.reviewedAt(),
                approver, Instant.now()
        );
        drafts.put(draftId, approved);
        AuditEngine.instance().record(approver, "APPROVE_DRAFT", draft.target(), "Draft " + draftId, "", true);
        return approved;
    }

    /**
     * Applies an approved draft. In Phase 1, marks as applied without
     * actually modifying the running configuration (future phases will
     * delegate to ConfigStore for live updates).
     */
    public ConfigDraft apply(String draftId, String user) {
        ConfigDraft draft = drafts.get(draftId);
        if (draft == null) throw new IllegalArgumentException("Draft not found: " + draftId);

        ConfigDraft applied = new ConfigDraft(
                draft.id(), draft.author(), draft.createdAt(), draft.target(),
                draft.description(), draft.changes(),
                ConfigDraft.DraftStatus.APPLIED,
                draft.reviewer(), draft.reviewedAt(),
                draft.approver(), draft.approvedAt()
        );
        drafts.put(draftId, applied);
        log.info("Config draft applied: id={} target={}", draftId, draft.target());
        AuditEngine.instance().record(user, "APPLY_DRAFT", draft.target(),
                "Draft " + draftId + " applied", "", true);
        return applied;
    }

    /**
     * Rolls back a previously applied draft.
     */
    public ConfigDraft rollback(String draftId, String user) {
        ConfigDraft draft = drafts.get(draftId);
        if (draft == null) throw new IllegalArgumentException("Draft not found: " + draftId);

        ConfigDraft rolledBack = new ConfigDraft(
                draft.id(), draft.author(), draft.createdAt(), draft.target(),
                draft.description(), draft.changes(),
                ConfigDraft.DraftStatus.ROLLED_BACK,
                draft.reviewer(), draft.reviewedAt(),
                draft.approver(), draft.approvedAt()
        );
        drafts.put(draftId, rolledBack);
        log.info("Config draft rolled back: id={} target={}", draftId, draft.target());
        AuditEngine.instance().record(user, "ROLLBACK_DRAFT", draft.target(),
                "Draft " + draftId + " rolled back", "", true);
        return rolledBack;
    }

    /**
     * Rejects a draft.
     */
    public ConfigDraft reject(String draftId, String user, String reason) {
        ConfigDraft draft = drafts.get(draftId);
        if (draft == null) throw new IllegalArgumentException("Draft not found: " + draftId);

        ConfigDraft rejected = new ConfigDraft(
                draft.id(), draft.author(), draft.createdAt(), draft.target(),
                reason, draft.changes(),
                ConfigDraft.DraftStatus.REJECTED,
                draft.reviewer(), draft.reviewedAt(),
                null, null
        );
        drafts.put(draftId, rejected);
        AuditEngine.instance().record(user, "REJECT_DRAFT", draft.target(), reason, "", true);
        return rejected;
    }

    public ConfigDraft get(String draftId) {
        return drafts.get(draftId);
    }

    public List<ConfigDraft> listAll() {
        return List.copyOf(drafts.values());
    }

    public List<ConfigDraft> listByStatus(ConfigDraft.DraftStatus status) {
        return drafts.values().stream()
                .filter(d -> d.status() == status)
                .collect(Collectors.toList());
    }

    public int size() {
        return drafts.size();
    }
}
