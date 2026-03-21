package io.velo.was.aiplatform.tenant;

public record AiTenantApiKeyInfo(String keyId,
                                 String label,
                                 String secretPreview,
                                 boolean active,
                                 long createdAtEpochMillis,
                                 long lastUsedAtEpochMillis) {
}