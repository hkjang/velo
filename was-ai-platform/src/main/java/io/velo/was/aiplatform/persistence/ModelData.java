package io.velo.was.aiplatform.persistence;

import java.util.List;

/**
 * 모델 레지스트리 영속화용 DTO.
 * 런타임에 추가된 모델만 저장 (YAML 부트스트랩 모델은 제외).
 */
public record ModelData(
        String name,
        String category,
        String provider,
        String source,
        long registeredAt,
        List<VersionData> versions
) {
    public record VersionData(
            String version,
            String latencyTier,
            int latencyMs,
            int accuracyScore,
            boolean defaultSelected,
            boolean enabled,
            String status,
            long registeredAt
    ) {
    }
}
