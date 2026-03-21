package io.velo.was.aiplatform.api;

import io.velo.was.aiplatform.observability.AiPlatformUsageSnapshot;
import io.velo.was.aiplatform.registry.AiModelRegistrySummary;
import io.velo.was.aiplatform.registry.AiModelVersionInfo;
import io.velo.was.aiplatform.registry.AiRegisteredModel;
import io.velo.was.aiplatform.tenant.AiTenantSnapshot;
import io.velo.was.config.ServerConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AiPlatformApiJson {

    private AiPlatformApiJson() {
    }

    public static String status(ServerConfiguration configuration, AiModelRegistrySummary summary) {
        ServerConfiguration.Server server = configuration.getServer();
        ServerConfiguration.AiPlatform ai = server.getAiPlatform();
        return "{" +
                q("status") + ":" + q("UP") + "," +
                q("serverName") + ":" + q(server.getName()) + "," +
                q("nodeId") + ":" + q(server.getNodeId()) + "," +
                q("mode") + ":" + q(ai.getMode()) + "," +
                q("contextPath") + ":" + q(ai.getConsole().getContextPath()) + "," +
                q("enabled") + ":" + ai.isEnabled() + "," +
                q("registeredModels") + ":" + summary.totalModels() + "," +
                q("routableModels") + ":" + summary.routableModels() +
                "}";
    }

    public static String overview(ServerConfiguration configuration,
                                  AiModelRegistrySummary summary,
                                  AiPlatformUsageSnapshot usage,
                                  AiTenantSnapshot tenantSnapshot) {
        ServerConfiguration.Server server = configuration.getServer();
        ServerConfiguration.AiPlatform ai = server.getAiPlatform();
        return "{" +
                q("enabled") + ":" + ai.isEnabled() + "," +
                q("mode") + ":" + q(ai.getMode()) + "," +
                q("server") + ":{" +
                q("name") + ":" + q(server.getName()) + "," +
                q("nodeId") + ":" + q(server.getNodeId()) + "}," +
                q("console") + ":{" +
                q("enabled") + ":" + ai.getConsole().isEnabled() + "," +
                q("contextPath") + ":" + q(ai.getConsole().getContextPath()) + "}," +
                q("registry") + ":{" +
                q("totalModels") + ":" + summary.totalModels() + "," +
                q("totalVersions") + ":" + summary.totalVersions() + "," +
                q("routableModels") + ":" + summary.routableModels() + "," +
                q("canaryVersions") + ":" + summary.canaryVersions() + "}," +
                q("usage") + ":{" +
                q("meteredRequests") + ":" + usage.meteredRequests() + "," +
                q("routeCalls") + ":" + usage.routeCalls() + "," +
                q("inferCalls") + ":" + usage.inferCalls() + "," +
                q("streamCalls") + ":" + usage.streamCalls() + "," +
                q("publishedInvokeCalls") + ":" + usage.publishedInvokeCalls() + "," +
                q("cacheHits") + ":" + usage.cacheHits() + "," +
                q("totalEstimatedTokens") + ":" + usage.totalEstimatedTokens() + "}," +
                q("tenancy") + ":{" +
                q("enabled") + ":" + tenantSnapshot.multiTenantEnabled() + "," +
                q("apiKeyHeader") + ":" + q(tenantSnapshot.apiKeyHeader()) + "," +
                q("totalTenants") + ":" + tenantSnapshot.totalTenants() + "," +
                q("activeTenants") + ":" + tenantSnapshot.activeTenants() + "}," +
                q("flags") + ":{" +
                q("modelRegistrationEnabled") + ":" + ai.getPlatform().isModelRegistrationEnabled() + "," +
                q("versionManagementEnabled") + ":" + ai.getPlatform().isVersionManagementEnabled() + "," +
                q("billingEnabled") + ":" + ai.getPlatform().isBillingEnabled() + "," +
                q("developerPortalEnabled") + ":" + ai.getPlatform().isDeveloperPortalEnabled() + "," +
                q("multiTenantEnabled") + ":" + ai.getPlatform().isMultiTenantEnabled() + "," +
                q("promptRoutingEnabled") + ":" + ai.getAdvanced().isPromptRoutingEnabled() + "," +
                q("contextCacheEnabled") + ":" + ai.getAdvanced().isContextCacheEnabled() + "," +
                q("streamingResponseEnabled") + ":" + ai.getDifferentiation().isStreamingResponseEnabled() + "}," +
                q("roadmap") + ":{" +
                q("currentStage") + ":" + ai.getRoadmap().getCurrentStage() + "," +
                q("stages") + ":" + ai.getRoadmap().getStages().size() + "}" +
                "}";
    }

    public static String models(List<AiRegisteredModel> models, AiModelRegistrySummary summary) {
        StringBuilder json = new StringBuilder(4096);
        json.append("{").append(q("summary")).append(":{")
                .append(q("totalModels")).append(':').append(summary.totalModels()).append(',')
                .append(q("totalVersions")).append(':').append(summary.totalVersions()).append(',')
                .append(q("routableModels")).append(':').append(summary.routableModels()).append(',')
                .append(q("activeVersions")).append(':').append(summary.activeVersions())
                .append("},").append(q("models")).append(\":\[");
        boolean first = true;
        for (AiRegisteredModel model : models) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(model(model));
        }
        return json.append("]}").toString();
    }

    public static String model(AiRegisteredModel model) {
        StringBuilder json = new StringBuilder(1024);
        json.append('{')
                .append(q("name")).append(':').append(q(model.name())).append(',')
                .append(q("category")).append(':').append(q(model.category())).append(',')
                .append(q("provider")).append(':').append(q(model.provider())).append(',')
                .append(q("enabled")).append(':').append(model.enabled()).append(',')
                .append(q("source")).append(':').append(q(model.source())).append(',')
                .append(q("activeVersion")).append(':').append(q(model.activeVersion())).append(',')
                .append(q("registeredAt")).append(':').append(q(Instant.ofEpochMilli(model.registeredAtEpochMillis()).toString())).append(',')
                .append(q("routeEligible")).append(':').append(model.enabled() && model.activeVersion() != null && !model.activeVersion().isBlank()).append(',')
                .append(q("versions")).append(':').append('[');
        boolean first = true;
        for (AiModelVersionInfo version : model.versions()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('{')
                    .append(q("version")).append(':').append(q(version.version())).append(',')
                    .append(q("status")).append(':').append(q(version.status())).append(',')
                    .append(q("latencyTier")).append(':').append(q(version.latencyTier())).append(',')
                    .append(q("latencyMs")).append(':').append(version.latencyMs()).append(',')
                    .append(q("accuracyScore")).append(':').append(version.accuracyScore()).append(',')
                    .append(q("defaultSelected")).append(':').append(version.defaultSelected()).append(',')
                    .append(q("enabled")).append(':').append(version.enabled()).append(',')
                    .append(q("registeredAt")).append(':').append(q(Instant.ofEpochMilli(version.registeredAtEpochMillis()).toString()))
                    .append('}');
        }
        return json.append("]}").toString();
    }

    public static String usage(AiPlatformUsageSnapshot usage) {
        return "{" +
                q("requests") + ":{" +
                q("controlPlaneCalls") + ":" + usage.controlPlaneCalls() + "," +
                q("routeCalls") + ":" + usage.routeCalls() + "," +
                q("inferCalls") + ":" + usage.inferCalls() + "," +
                q("streamCalls") + ":" + usage.streamCalls() + "," +
                q("publishedInvokeCalls") + ":" + usage.publishedInvokeCalls() + "," +
                q("meteredRequests") + ":" + usage.meteredRequests() + "}," +
                q("billing") + ":{" +
                q("enabled") + ":" + usage.billingEnabled() + "," +
                q("registryMutations") + ":" + usage.registryMutations() + "," +
                q("totalEstimatedTokens") + ":" + usage.totalEstimatedTokens() + "}," +
                q("cache") + ":{" +
                q("hits") + ":" + usage.cacheHits() + "," +
                q("activeContexts") + ":" + usage.cachedContexts() + "}," +
                q("registry") + ":{" +
                q("registeredModels") + ":" + usage.registeredModels() + "," +
                q("totalVersions") + ":" + usage.totalVersions() + "," +
                q("routableModels") + ":" + usage.routableModels() + "}," +
                q("gateway") + ":{" +
                q("routeDecisions") + ":" + usage.gatewayRouteDecisions() + "," +
                q("modelRequestCounts") + ":" + longMap(usage.modelRequestCounts()) + "}," +
                q("endpointCounts") + ":" + longMap(usage.endpointCounts()) + "," +
                q("resolvedTypeCounts") + ":" + longMap(usage.resolvedTypeCounts()) + "," +
                q("registryActionCounts") + ":" + longMap(usage.registryActionCounts()) +
                "}";
    }

    private static String longMap(Map<String, Long> values) {
        StringBuilder json = new StringBuilder(256).append('{');
        boolean first = true;
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(q(entry.getKey())).append(':').append(entry.getValue());
        }
        return json.append('}').toString();
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