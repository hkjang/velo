package io.velo.was.aiplatform.tenant;

public record AiTenantUsageInfo(long totalRequests,
                                long totalTokens,
                                int currentWindowRequests,
                                int rateLimitPerMinute,
                                int remainingWindowRequests,
                                long tokenQuota,
                                long remainingTokens,
                                long lastActivityEpochMillis) {
}