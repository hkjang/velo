package io.velo.was.aiplatform.registry;

import java.util.List;

public record AiRegisteredModel(
        String name,
        String category,
        String provider,
        boolean enabled,
        String source,
        String activeVersion,
        long registeredAtEpochMillis,
        List<AiModelVersionInfo> versions
) {
}