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
        List<ApiKeyData> apiKeys
) {
    public record ApiKeyData(
            String keyId,
            String label,
            String secret,
            boolean active,
            long createdAt
    ) {
    }
}
