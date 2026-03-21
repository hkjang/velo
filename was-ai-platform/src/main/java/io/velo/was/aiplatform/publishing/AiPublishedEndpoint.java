package io.velo.was.aiplatform.publishing;

public record AiPublishedEndpoint(
        String endpointName,
        String modelName,
        String version,
        String category,
        String method,
        String path,
        boolean publicAccess,
        String summary
) {
}