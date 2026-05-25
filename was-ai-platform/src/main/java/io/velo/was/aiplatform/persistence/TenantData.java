package io.velo.was.aiplatform.persistence;

import java.util.List;

/**
 * 테넌트 영속화용 DTO.
 */
public record TenantData(
        String tenantId,
        String displayName,
        String plan,
        boolean active,
        int rateLimitPerMinute,
        long tokenQuota,
        long createdAt,
        long totalRequests,
        long totalTokens,
        long currentWindowEpochMinute,
        int currentWindowRequests,
        long lastActivityAt,
        List<ApiKeyData> apiKeys,
        List<String> allowedModels
) {
    public TenantData(String tenantId,
                      String displayName,
                      String plan,
                      boolean active,
                      int rateLimitPerMinute,
                      long tokenQuota,
                      long createdAt,
                      long totalRequests,
                      long totalTokens,
                      long currentWindowEpochMinute,
                      int currentWindowRequests,
                      long lastActivityAt,
                      List<ApiKeyData> apiKeys) {
        this(tenantId, displayName, plan, active, rateLimitPerMinute, tokenQuota, createdAt,
                totalRequests, totalTokens, currentWindowEpochMinute, currentWindowRequests,
                lastActivityAt, apiKeys, List.of());
    }

    public TenantData(String tenantId,
                      String displayName,
                      String plan,
                      boolean active,
                      int rateLimitPerMinute,
                      long tokenQuota,
                      long createdAt,
                      List<ApiKeyData> apiKeys) {
        this(tenantId, displayName, plan, active, rateLimitPerMinute, tokenQuota, createdAt,
                0L, 0L, 0L, 0, 0L, apiKeys, List.of());
    }

    public record ApiKeyData(
            String keyId,
            String label,
            String secret,
            boolean active,
            long createdAt,
            long lastUsedAt,
            long expiresAt
    ) {
        public ApiKeyData(String keyId, String label, String secret, boolean active,
                          long createdAt, long lastUsedAt) {
            this(keyId, label, secret, active, createdAt, lastUsedAt, 0L);
        }

        public ApiKeyData(String keyId, String label, String secret, boolean active, long createdAt) {
            this(keyId, label, secret, active, createdAt, 0L, 0L);
        }
    }
}
