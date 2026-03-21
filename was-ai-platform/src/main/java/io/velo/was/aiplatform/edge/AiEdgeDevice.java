package io.velo.was.aiplatform.edge;

/**
 * Represents a registered edge device that can run lightweight AI models.
 * Edge devices connect to the WAS platform and receive model deployments
 * for local inference (e.g. mobile, IoT, on-premise servers).
 */
public record AiEdgeDevice(String deviceId,
                           String displayName,
                           String deviceType,
                           String status,
                           String deployedModel,
                           String deployedVersion,
                           int maxMemoryMb,
                           long lastHeartbeatEpochMillis,
                           long registeredAtEpochMillis) {
}
