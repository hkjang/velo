package io.velo.was.webadmin.config;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a staged configuration change draft.
 * Follows the lifecycle: DRAFT → VALIDATED → REVIEWED → APPROVED → APPLIED (or REJECTED / ROLLED_BACK).
 */
public record ConfigDraft(
        String id,
        String author,
        Instant createdAt,
        String target,
        String description,
        Map<String, String> changes,
        DraftStatus status,
        String reviewer,
        Instant reviewedAt,
        String approver,
        Instant approvedAt
) {

    public enum DraftStatus {
        DRAFT, VALIDATED, REVIEWED, APPROVED, APPLIED, REJECTED, ROLLED_BACK
    }

    public String toJson() {
        StringBuilder changesJson = new StringBuilder("{");
        boolean first = true;
        for (var entry : changes.entrySet()) {
            if (!first) changesJson.append(',');
            first = false;
            changesJson.append("\"%s\":\"%s\"".formatted(esc(entry.getKey()), esc(entry.getValue())));
        }
        changesJson.append("}");

        return """
                {"id":"%s","author":"%s","createdAt":"%s","target":"%s",\
                "description":"%s","changes":%s,"status":"%s",\
                "reviewer":"%s","approver":"%s"}""".formatted(
                esc(id), esc(author), createdAt.toString(), esc(target),
                esc(description), changesJson, status.name(),
                esc(reviewer != null ? reviewer : ""),
                esc(approver != null ? approver : "")
        );
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
