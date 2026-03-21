package io.velo.was.aiplatform.tenant;

public record AiTenantIssuedKey(String tenantId,
                                String displayName,
                                String plan,
                                String keyId,
                                String label,
                                String apiKey,
                                long createdAtEpochMillis) {
}