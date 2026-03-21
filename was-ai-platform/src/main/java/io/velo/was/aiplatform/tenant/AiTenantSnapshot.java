package io.velo.was.aiplatform.tenant;

import java.util.List;

public record AiTenantSnapshot(boolean multiTenantEnabled,
                               String apiKeyHeader,
                               int totalTenants,
                               int activeTenants,
                               String bootstrapApiKey,
                               List<AiTenantProfile> tenants) {
}