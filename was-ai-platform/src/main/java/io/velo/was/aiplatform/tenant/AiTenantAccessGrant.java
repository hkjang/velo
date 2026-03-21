package io.velo.was.aiplatform.tenant;

public record AiTenantAccessGrant(String tenantId,
                                  String displayName,
                                  String plan,
                                  String keyId,
                                  int currentWindowRequests,
                                  int rateLimitPerMinute,
                                  int remainingWindowRequests,
                                  long tokenQuota,
                                  long totalTokensBeforeRequest,
                                  long remainingTokensBeforeRequest,
                                  boolean tracked) {
}