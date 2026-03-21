package io.velo.was.aiplatform.tenant;

public record AiTenantRegistrationRequest(String tenantId,
                                          String displayName,
                                          String plan,
                                          int rateLimitPerMinute,
                                          long tokenQuota,
                                          boolean active) {
}