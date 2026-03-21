package io.velo.was.aiplatform.tenant;

import java.util.List;

public record AiTenantProfile(String tenantId,
                              String displayName,
                              String plan,
                              boolean active,
                              int rateLimitPerMinute,
                              long tokenQuota,
                              long createdAtEpochMillis,
                              List<AiTenantApiKeyInfo> apiKeys,
                              AiTenantUsageInfo usage) {
}