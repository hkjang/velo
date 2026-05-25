package io.velo.was.aiplatform.operations;

import io.velo.was.aiplatform.gateway.AiGatewayServlet;
import io.velo.was.aiplatform.registry.AiModelVersionInfo;
import io.velo.was.aiplatform.registry.AiRegisteredModel;

import java.time.Instant;

public final class AiModelBundleService {

    private AiModelBundleService() {
    }

    public static String manifest(AiRegisteredModel model) {
        if (model == null) {
            throw new IllegalArgumentException("Model not found");
        }
        StringBuilder json = new StringBuilder(1024);
        json.append('{')
                .append(q("schemaVersion")).append(':').append(q("velo.ai.bundle/v1")).append(',')
                .append(q("generatedAt")).append(':').append(q(Instant.now().toString())).append(',')
                .append(q("model")).append(":{")
                .append(q("name")).append(':').append(q(model.name())).append(',')
                .append(q("category")).append(':').append(q(model.category())).append(',')
                .append(q("provider")).append(':').append(q(model.provider())).append(',')
                .append(q("source")).append(':').append(q(model.source())).append(',')
                .append(q("activeVersion")).append(':').append(q(model.activeVersion()))
                .append("},")
                .append(q("runner")).append(":{")
                .append(q("type")).append(':').append(q("docker")).append(',')
                .append(q("healthProbe")).append(':').append(q("/health")).append(',')
                .append(q("rollbackStrategy")).append(':').append(q("previous-active-version"))
                .append("},")
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
                    .append(q("enabled")).append(':').append(version.enabled())
                    .append('}');
        }
        return json.append("]}").toString();
    }

    private static String q(String value) {
        return "\"" + AiGatewayServlet.escapeJson(value) + "\"";
    }
}
