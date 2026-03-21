package io.velo.was.aiplatform.edge;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service for managing edge AI device registrations and model deployments.
 * Edge devices register themselves with the platform and receive lightweight
 * model profiles for local inference execution.
 */
public class AiEdgeService {

    private final ConcurrentMap<String, MutableDevice> devices = new ConcurrentHashMap<>();

    public synchronized AiEdgeDevice register(String deviceId, String displayName, String deviceType, int maxMemoryMb) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId is required");
        }
        String normalizedId = deviceId.trim().toLowerCase();
        long now = System.currentTimeMillis();
        MutableDevice device = devices.computeIfAbsent(normalizedId, id -> new MutableDevice(deviceId.trim(), now));
        device.displayName = displayName == null || displayName.isBlank() ? deviceId.trim() : displayName.trim();
        device.deviceType = deviceType == null || deviceType.isBlank() ? "generic" : deviceType.trim();
        device.maxMemoryMb = Math.max(64, maxMemoryMb);
        device.status = "ONLINE";
        device.lastHeartbeatEpochMillis = now;
        return snapshot(device);
    }

    public synchronized AiEdgeDevice heartbeat(String deviceId) {
        MutableDevice device = devices.get(normalize(deviceId));
        if (device == null) {
            throw new NoSuchElementException("Edge device not found: " + deviceId);
        }
        device.lastHeartbeatEpochMillis = System.currentTimeMillis();
        device.status = "ONLINE";
        return snapshot(device);
    }

    public synchronized AiEdgeDevice deploy(String deviceId, String modelName, String version) {
        MutableDevice device = devices.get(normalize(deviceId));
        if (device == null) {
            throw new NoSuchElementException("Edge device not found: " + deviceId);
        }
        device.deployedModel = modelName == null ? "" : modelName.trim();
        device.deployedVersion = version == null ? "" : version.trim();
        device.lastHeartbeatEpochMillis = System.currentTimeMillis();
        return snapshot(device);
    }

    public synchronized List<AiEdgeDevice> listDevices() {
        long now = System.currentTimeMillis();
        return devices.values().stream()
                .peek(d -> {
                    if (now - d.lastHeartbeatEpochMillis > 120_000L) {
                        d.status = "OFFLINE";
                    }
                })
                .map(this::snapshot)
                .sorted(Comparator.comparing(AiEdgeDevice::deviceId))
                .toList();
    }

    public synchronized AiEdgeDevice getDevice(String deviceId) {
        MutableDevice device = devices.get(normalize(deviceId));
        if (device == null) {
            throw new NoSuchElementException("Edge device not found: " + deviceId);
        }
        return snapshot(device);
    }

    public int size() {
        return devices.size();
    }

    private AiEdgeDevice snapshot(MutableDevice device) {
        return new AiEdgeDevice(
                device.deviceId, device.displayName, device.deviceType,
                device.status, device.deployedModel, device.deployedVersion,
                device.maxMemoryMb, device.lastHeartbeatEpochMillis, device.registeredAtEpochMillis);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static final class MutableDevice {
        private final String deviceId;
        private String displayName;
        private String deviceType = "generic";
        private String status = "ONLINE";
        private String deployedModel = "";
        private String deployedVersion = "";
        private int maxMemoryMb = 512;
        private long lastHeartbeatEpochMillis;
        private final long registeredAtEpochMillis;

        private MutableDevice(String deviceId, long registeredAtEpochMillis) {
            this.deviceId = deviceId;
            this.displayName = deviceId;
            this.registeredAtEpochMillis = registeredAtEpochMillis;
            this.lastHeartbeatEpochMillis = registeredAtEpochMillis;
        }
    }
}
