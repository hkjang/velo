package io.velo.was.aiplatform.api;

import io.velo.was.aiplatform.edge.AiEdgeDevice;
import io.velo.was.aiplatform.plugin.AiPluginRegistry;
import io.velo.was.aiplatform.provider.AiProviderRegistry;
import io.velo.was.aiplatform.billing.AiBillingLineItem;
import io.velo.was.aiplatform.billing.AiBillingSnapshot;
import io.velo.was.aiplatform.finetuning.AiFineTuningJob;
import io.velo.was.aiplatform.publishing.AiPublishedEndpoint;
import io.velo.was.aiplatform.publishing.AiPublishedInvocationResult;
import io.velo.was.aiplatform.tenant.AiTenantApiKeyInfo;
import io.velo.was.aiplatform.tenant.AiTenantIssuedKey;
import io.velo.was.aiplatform.tenant.AiTenantProfile;
import io.velo.was.aiplatform.tenant.AiTenantSnapshot;
import io.velo.was.aiplatform.tenant.AiTenantUsageInfo;

import java.time.Instant;
import java.util.List;

public final class AiPlatformExtendedJson {

    private AiPlatformExtendedJson() {
    }

    public static String publishedEndpoints(List<AiPublishedEndpoint> endpoints) {
        StringBuilder json = new StringBuilder(1024).append('{')
                .append(q("count")).append(':').append(endpoints.size()).append(',')
                .append(q("endpoints")).append(':').append('[');
        boolean first = true;
        for (AiPublishedEndpoint endpoint : endpoints) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(endpoint(endpoint));
        }
        return json.append("]}").toString();
    }

    public static String publishedInvocation(AiPublishedInvocationResult result) {
        return "{" +
                q("endpoint") + ":" + endpoint(result.endpoint()) + "," +
                q("sessionId") + ":" + q(result.sessionId()) + "," +
                q("provider") + ":" + q(result.provider()) + "," +
                q("expectedLatencyMs") + ":" + result.expectedLatencyMs() + "," +
                q("estimatedTokens") + ":" + result.estimatedTokens() + "," +
                q("estimatedCost") + ":" + format(result.estimatedCost()) + "," +
                q("outputText") + ":" + q(result.outputText()) +
                "}";
    }

    public static String billing(AiBillingSnapshot snapshot) {
        StringBuilder json = new StringBuilder(1024).append('{')
                .append(q("billingEnabled")).append(':').append(snapshot.billingEnabled()).append(',')
                .append(q("currency")).append(':').append(q(snapshot.currency())).append(',')
                .append(q("meteredRequests")).append(':').append(snapshot.meteredRequests()).append(',')
                .append(q("totalEstimatedTokens")).append(':').append(snapshot.totalEstimatedTokens()).append(',')
                .append(q("estimatedTotalCost")).append(':').append(format(snapshot.estimatedTotalCost())).append(',')
                .append(q("averageCostPerRequest")).append(':').append(format(snapshot.averageCostPerRequest())).append(',')
                .append(q("lineItems")).append(':').append('[');
        boolean first = true;
        for (AiBillingLineItem item : snapshot.lineItems()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{')
                    .append(q("modelName")).append(':').append(q(item.modelName())).append(',')
                    .append(q("category")).append(':').append(q(item.category())).append(',')
                    .append(q("requests")).append(':').append(item.requests()).append(',')
                    .append(q("allocatedTokens")).append(':').append(item.allocatedTokens()).append(',')
                    .append(q("estimatedCost")).append(':').append(format(item.estimatedCost()))
                    .append('}');
        }
        return json.append("]}").toString();
    }

    public static String tenants(AiTenantSnapshot snapshot) {
        StringBuilder json = new StringBuilder(2048).append('{')
                .append(q("multiTenantEnabled")).append(':').append(snapshot.multiTenantEnabled()).append(',')
                .append(q("apiKeyHeader")).append(':').append(q(snapshot.apiKeyHeader())).append(',')
                .append(q("totalTenants")).append(':').append(snapshot.totalTenants()).append(',')
                .append(q("activeTenants")).append(':').append(snapshot.activeTenants()).append(',')
                .append(q("bootstrapApiKey")).append(':').append(q(snapshot.bootstrapApiKey())).append(',')
                .append(q("tenants")).append(':').append('[');
        boolean first = true;
        for (AiTenantProfile tenant : snapshot.tenants()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(tenant(tenant));
        }
        return json.append("]}").toString();
    }

    public static String tenant(AiTenantProfile tenant) {
        StringBuilder json = new StringBuilder(1024).append('{')
                .append(q("tenantId")).append(':').append(q(tenant.tenantId())).append(',')
                .append(q("displayName")).append(':').append(q(tenant.displayName())).append(',')
                .append(q("plan")).append(':').append(q(tenant.plan())).append(',')
                .append(q("active")).append(':').append(tenant.active()).append(',')
                .append(q("rateLimitPerMinute")).append(':').append(tenant.rateLimitPerMinute()).append(',')
                .append(q("tokenQuota")).append(':').append(tenant.tokenQuota()).append(',')
                .append(q("createdAt")).append(':').append(q(Instant.ofEpochMilli(tenant.createdAtEpochMillis()).toString())).append(',')
                .append(q("apiKeys")).append(':').append('[');
        boolean first = true;
        for (AiTenantApiKeyInfo keyInfo : tenant.apiKeys()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{')
                    .append(q("keyId")).append(':').append(q(keyInfo.keyId())).append(',')
                    .append(q("label")).append(':').append(q(keyInfo.label())).append(',')
                    .append(q("secretPreview")).append(':').append(q(keyInfo.secretPreview())).append(',')
                    .append(q("active")).append(':').append(keyInfo.active()).append(',')
                    .append(q("createdAt")).append(':').append(q(Instant.ofEpochMilli(keyInfo.createdAtEpochMillis()).toString())).append(',')
                    .append(q("lastUsedAt")).append(':').append(keyInfo.lastUsedAtEpochMillis() > 0 ? q(Instant.ofEpochMilli(keyInfo.lastUsedAtEpochMillis()).toString()) : "null")
                    .append('}');
        }
        return json.append(']').append(',')
                .append(q("usage")).append(':').append(tenantUsagePayload(tenant.usage()))
                .append('}').toString();
    }

    public static String tenantUsage(String tenantId, AiTenantUsageInfo usage) {
        return "{" + q("tenantId") + ":" + q(tenantId) + "," + q("usage") + ":" + tenantUsagePayload(usage) + "}";
    }

    public static String issuedTenantKey(AiTenantIssuedKey issuedKey) {
        return "{" +
                q("tenantId") + ":" + q(issuedKey.tenantId()) + "," +
                q("displayName") + ":" + q(issuedKey.displayName()) + "," +
                q("plan") + ":" + q(issuedKey.plan()) + "," +
                q("keyId") + ":" + q(issuedKey.keyId()) + "," +
                q("label") + ":" + q(issuedKey.label()) + "," +
                q("apiKey") + ":" + q(issuedKey.apiKey()) + "," +
                q("createdAt") + ":" + q(Instant.ofEpochMilli(issuedKey.createdAtEpochMillis()).toString()) +
                "}";
    }

    public static String fineTuningJobs(List<AiFineTuningJob> jobs) {
        StringBuilder json = new StringBuilder(1024).append('{')
                .append(q("count")).append(':').append(jobs.size()).append(',')
                .append(q("jobs")).append(':').append('[');
        boolean first = true;
        for (AiFineTuningJob job : jobs) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(fineTuningJob(job));
        }
        return json.append("]}").toString();
    }

    public static String fineTuningJob(AiFineTuningJob job) {
        return "{" +
                q("jobId") + ":" + q(job.jobId()) + "," +
                q("baseModel") + ":" + q(job.baseModel()) + "," +
                q("datasetUri") + ":" + q(job.datasetUri()) + "," +
                q("tenant") + ":" + q(job.tenant()) + "," +
                q("objective") + ":" + q(job.objective()) + "," +
                q("epochs") + ":" + job.epochs() + "," +
                q("status") + ":" + q(job.status()) + "," +
                q("progressPercent") + ":" + job.progressPercent() + "," +
                q("tunedModelName") + ":" + q(job.tunedModelName()) + "," +
                q("createdAt") + ":" + q(Instant.ofEpochMilli(job.createdAtEpochMillis()).toString()) + "," +
                q("updatedAt") + ":" + q(Instant.ofEpochMilli(job.updatedAtEpochMillis()).toString()) +
                "}";
    }

    public static String edgeDevices(List<AiEdgeDevice> devices) {
        StringBuilder json = new StringBuilder(1024).append('{')
                .append(q("count")).append(':').append(devices.size()).append(',')
                .append(q("devices")).append(':').append('[');
        boolean first = true;
        for (AiEdgeDevice device : devices) {
            if (!first) json.append(',');
            first = false;
            json.append('{')
                    .append(q("deviceId")).append(':').append(q(device.deviceId())).append(',')
                    .append(q("displayName")).append(':').append(q(device.displayName())).append(',')
                    .append(q("deviceType")).append(':').append(q(device.deviceType())).append(',')
                    .append(q("status")).append(':').append(q(device.status())).append(',')
                    .append(q("deployedModel")).append(':').append(q(device.deployedModel())).append(',')
                    .append(q("deployedVersion")).append(':').append(q(device.deployedVersion())).append(',')
                    .append(q("maxMemoryMb")).append(':').append(device.maxMemoryMb())
                    .append('}');
        }
        return json.append("]}").toString();
    }

    public static String edgeDevice(AiEdgeDevice device) {
        return "{" +
                q("deviceId") + ":" + q(device.deviceId()) + "," +
                q("displayName") + ":" + q(device.displayName()) + "," +
                q("deviceType") + ":" + q(device.deviceType()) + "," +
                q("status") + ":" + q(device.status()) + "," +
                q("deployedModel") + ":" + q(device.deployedModel()) + "," +
                q("deployedVersion") + ":" + q(device.deployedVersion()) + "," +
                q("maxMemoryMb") + ":" + device.maxMemoryMb() +
                "}";
    }

    public static String providers(List<AiProviderRegistry.AiProviderInfo> providers) {
        StringBuilder json = new StringBuilder(512).append('{')
                .append(q("count")).append(':').append(providers.size()).append(',')
                .append(q("providers")).append(':').append('[');
        boolean first = true;
        for (AiProviderRegistry.AiProviderInfo provider : providers) {
            if (!first) json.append(',');
            first = false;
            json.append('{')
                    .append(q("providerId")).append(':').append(q(provider.providerId())).append(',')
                    .append(q("displayName")).append(':').append(q(provider.displayName())).append(',')
                    .append(q("protocol")).append(':').append(q(provider.protocol())).append(',')
                    .append(q("supportsStreaming")).append(':').append(provider.supportsStreaming()).append(',')
                    .append(q("healthy")).append(':').append(provider.healthy())
                    .append('}');
        }
        return json.append("]}").toString();
    }

    public static String plugins(List<AiPluginRegistry.AiPluginInfo> plugins) {
        StringBuilder json = new StringBuilder(512).append('{')
                .append(q("count")).append(':').append(plugins.size()).append(',')
                .append(q("plugins")).append(':').append('[');
        boolean first = true;
        for (AiPluginRegistry.AiPluginInfo plugin : plugins) {
            if (!first) json.append(',');
            first = false;
            json.append('{')
                    .append(q("id")).append(':').append(q(plugin.id())).append(',')
                    .append(q("name")).append(':').append(q(plugin.name())).append(',')
                    .append(q("type")).append(':').append(q(plugin.type())).append(',')
                    .append(q("enabled")).append(':').append(plugin.enabled())
                    .append('}');
        }
        return json.append("]}").toString();
    }

    private static String tenantUsagePayload(AiTenantUsageInfo usage) {
        return "{" +
                q("totalRequests") + ":" + usage.totalRequests() + "," +
                q("totalTokens") + ":" + usage.totalTokens() + "," +
                q("currentWindowRequests") + ":" + usage.currentWindowRequests() + "," +
                q("rateLimitPerMinute") + ":" + usage.rateLimitPerMinute() + "," +
                q("remainingWindowRequests") + ":" + usage.remainingWindowRequests() + "," +
                q("tokenQuota") + ":" + usage.tokenQuota() + "," +
                q("remainingTokens") + ":" + usage.remainingTokens() + "," +
                q("lastActivityAt") + ":" + (usage.lastActivityEpochMillis() > 0 ? q(Instant.ofEpochMilli(usage.lastActivityEpochMillis()).toString()) : "null") +
                "}";
    }

    private static String endpoint(AiPublishedEndpoint endpoint) {
        return "{" +
                q("endpointName") + ":" + q(endpoint.endpointName()) + "," +
                q("modelName") + ":" + q(endpoint.modelName()) + "," +
                q("version") + ":" + q(endpoint.version()) + "," +
                q("category") + ":" + q(endpoint.category()) + "," +
                q("method") + ":" + q(endpoint.method()) + "," +
                q("path") + ":" + q(endpoint.path()) + "," +
                q("publicAccess") + ":" + endpoint.publicAccess() + "," +
                q("summary") + ":" + q(endpoint.summary()) +
                "}";
    }

    private static String format(double value) {
        return String.format(java.util.Locale.US, "%.6f", value);
    }

    private static String q(String value) {
        return "\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}