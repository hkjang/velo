package io.velo.was.aiplatform.registry;

import io.velo.was.config.ServerConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AiModelRegistryService {

    private final ConcurrentMap<String, MutableRegisteredModel> models = new ConcurrentHashMap<>();

    public AiModelRegistryService(ServerConfiguration configuration) {
        bootstrap(configuration.getServer().getAiPlatform().getServing().getModels());
    }

    public synchronized List<AiRegisteredModel> listModels() {
        return models.values().stream()
                .map(this::snapshot)
                .sorted(Comparator.comparing(AiRegisteredModel::name))
                .toList();
    }

    public synchronized AiRegisteredModel findModel(String modelName) {
        MutableRegisteredModel model = models.get(normalizeKey(modelName));
        return model == null ? null : snapshot(model);
    }

    public synchronized AiRegisteredModel registerOrUpdate(AiModelRegistrationRequest request) {
        String name = normalizeRequired(request.name(), "Model name");
        String category = normalizeCategory(request.category());
        String provider = normalizeProvider(request.provider());
        String version = normalizeRequired(request.version(), "Model version");
        String latencyTier = normalizeLatencyTier(request.latencyTier());
        int latencyMs = normalizeLatencyMs(request.latencyMs());
        int accuracyScore = normalizeAccuracyScore(request.accuracyScore());
        boolean enabled = request.enabled();
        String status = normalizeStatus(request.status(), enabled);
        String source = normalizeSource(request.source());

        long now = System.currentTimeMillis();
        MutableRegisteredModel model = models.computeIfAbsent(normalizeKey(name), ignored -> new MutableRegisteredModel(name, now));
        model.name = name;
        model.category = category;
        model.provider = provider;
        model.source = source;

        MutableVersion mutableVersion = model.versions.computeIfAbsent(normalizeKey(version), ignored -> new MutableVersion(version, now));
        mutableVersion.version = version;
        mutableVersion.latencyTier = latencyTier;
        mutableVersion.latencyMs = latencyMs;
        mutableVersion.accuracyScore = accuracyScore;
        mutableVersion.defaultSelected = request.defaultSelected();
        mutableVersion.enabled = enabled;
        mutableVersion.status = status;

        if ("ACTIVE".equals(status)) {
            promoteVersion(model, version);
        } else {
            rebalanceActiveVersion(model);
        }
        model.enabled = model.versions.values().stream().anyMatch(versionInfo -> versionInfo.enabled);
        return snapshot(model);
    }

    public synchronized AiRegisteredModel updateVersionStatus(String modelName, String version, String nextStatus) {
        MutableRegisteredModel model = models.get(normalizeKey(modelName));
        if (model == null) {
            throw new NoSuchElementException("Model not found: " + modelName);
        }

        MutableVersion mutableVersion = model.versions.get(normalizeKey(version));
        if (mutableVersion == null) {
            throw new NoSuchElementException("Model version not found: " + modelName + '@' + version);
        }

        mutableVersion.status = normalizeStatus(nextStatus, mutableVersion.enabled);
        if ("ACTIVE".equals(mutableVersion.status)) {
            promoteVersion(model, mutableVersion.version);
        } else {
            rebalanceActiveVersion(model);
        }
        model.enabled = model.versions.values().stream().anyMatch(versionInfo -> versionInfo.enabled);
        return snapshot(model);
    }

    public synchronized void removeModel(String modelName) {
        MutableRegisteredModel removed = models.remove(normalizeKey(modelName));
        if (removed == null) {
            throw new NoSuchElementException("모델을 찾을 수 없습니다: " + modelName);
        }
    }

    public synchronized List<ServerConfiguration.ModelProfile> routingModels() {
        List<ServerConfiguration.ModelProfile> routed = new ArrayList<>();
        for (MutableRegisteredModel model : models.values()) {
            MutableVersion activeVersion = resolveRouteVersion(model);
            if (activeVersion == null || !model.enabled || !isRouteEligible(activeVersion)) {
                continue;
            }
            routed.add(new ServerConfiguration.ModelProfile(
                    model.name,
                    model.category,
                    model.provider,
                    activeVersion.version,
                    activeVersion.latencyTier,
                    activeVersion.latencyMs,
                    activeVersion.accuracyScore,
                    activeVersion.defaultSelected,
                    true
            ));
        }
        routed.sort(Comparator.comparing(ServerConfiguration.ModelProfile::getName));
        return List.copyOf(routed);
    }

    public synchronized AiModelRegistrySummary summary() {
        int totalModels = models.size();
        int totalVersions = 0;
        int activeVersions = 0;
        int canaryVersions = 0;
        int bundledModels = 0;
        int runtimeModels = 0;
        int routableModels = 0;

        for (MutableRegisteredModel model : models.values()) {
            totalVersions += model.versions.size();
            if ("bundled".equalsIgnoreCase(model.source)) {
                bundledModels++;
            } else {
                runtimeModels++;
            }
            if (resolveRouteVersion(model) != null && model.enabled) {
                routableModels++;
            }
            for (MutableVersion version : model.versions.values()) {
                if ("ACTIVE".equals(version.status)) {
                    activeVersions++;
                }
                if ("CANARY".equals(version.status)) {
                    canaryVersions++;
                }
            }
        }

        return new AiModelRegistrySummary(totalModels, totalVersions, routableModels, activeVersions, canaryVersions, bundledModels, runtimeModels);
    }

    private void bootstrap(List<ServerConfiguration.ModelProfile> profiles) {
        for (ServerConfiguration.ModelProfile model : profiles) {
            registerOrUpdate(new AiModelRegistrationRequest(
                    model.getName(),
                    model.getCategory(),
                    model.getProvider(),
                    model.getVersion(),
                    model.getLatencyTier(),
                    model.getLatencyMs(),
                    model.getAccuracyScore(),
                    model.isDefaultSelected(),
                    model.isEnabled(),
                    model.isEnabled() ? "ACTIVE" : "INACTIVE",
                    "bundled"
            ));
        }
    }

    private void promoteVersion(MutableRegisteredModel model, String version) {
        String normalizedVersion = normalizeKey(version);
        for (Map.Entry<String, MutableVersion> entry : model.versions.entrySet()) {
            MutableVersion candidate = entry.getValue();
            if (entry.getKey().equals(normalizedVersion)) {
                candidate.status = candidate.enabled ? "ACTIVE" : "INACTIVE";
                model.activeVersion = candidate.version;
            } else if ("ACTIVE".equals(candidate.status) && candidate.enabled) {
                candidate.status = "CANARY";
            }
        }
        rebalanceActiveVersion(model);
    }

    private void rebalanceActiveVersion(MutableRegisteredModel model) {
        MutableVersion activeVersion = resolveRouteVersion(model);
        if (activeVersion != null) {
            if (!"ACTIVE".equals(activeVersion.status)) {
                activeVersion.status = "ACTIVE";
            }
            model.activeVersion = activeVersion.version;
            for (MutableVersion version : model.versions.values()) {
                if (!version.version.equalsIgnoreCase(activeVersion.version) && "ACTIVE".equals(version.status) && version.enabled) {
                    version.status = "CANARY";
                }
            }
            return;
        }
        model.activeVersion = "";
    }

    private MutableVersion resolveRouteVersion(MutableRegisteredModel model) {
        MutableVersion explicitActive = model.activeVersion == null || model.activeVersion.isBlank()
                ? null
                : model.versions.get(normalizeKey(model.activeVersion));
        if (isRouteEligible(explicitActive)) {
            return explicitActive;
        }
        return model.versions.values().stream()
                .filter(this::isRouteEligible)
                .sorted(Comparator
                        .comparingInt(this::statusRank)
                        .thenComparing(Comparator.comparingLong((MutableVersion version) -> version.registeredAtEpochMillis).reversed()))
                .findFirst()
                .orElse(null);
    }

    private boolean isRouteEligible(MutableVersion version) {
        return version != null
                && version.enabled
                && !"INACTIVE".equals(version.status)
                && !"DEPRECATED".equals(version.status);
    }

    private int statusRank(MutableVersion version) {
        return switch (version.status) {
            case "ACTIVE" -> 0;
            case "CANARY" -> 1;
            case "INACTIVE" -> 2;
            case "DEPRECATED" -> 3;
            default -> 4;
        };
    }

    private AiRegisteredModel snapshot(MutableRegisteredModel model) {
        List<AiModelVersionInfo> versions = model.versions.values().stream()
                .sorted(Comparator
                        .comparingInt(this::statusRank)
                        .thenComparing(Comparator.comparingLong((MutableVersion version) -> version.registeredAtEpochMillis).reversed()))
                .map(version -> new AiModelVersionInfo(
                        version.version,
                        version.status,
                        version.latencyTier,
                        version.latencyMs,
                        version.accuracyScore,
                        version.defaultSelected,
                        version.enabled,
                        version.registeredAtEpochMillis
                ))
                .toList();
        return new AiRegisteredModel(
                model.name,
                model.category,
                model.provider,
                model.enabled,
                model.source,
                model.activeVersion,
                model.registeredAtEpochMillis,
                versions
        );
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String normalizeCategory(String value) {
        if (value == null || value.isBlank()) {
            return "LLM";
        }
        return value.trim().toUpperCase();
    }

    private static String normalizeProvider(String value) {
        if (value == null || value.isBlank()) {
            return "builtin";
        }
        return value.trim();
    }

    private static String normalizeLatencyTier(String value) {
        if (value == null || value.isBlank()) {
            return "balanced";
        }
        return value.trim();
    }

    private static int normalizeLatencyMs(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("latencyMs must be positive");
        }
        return value;
    }

    private static int normalizeAccuracyScore(int value) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("accuracyScore must be between 0 and 100");
        }
        return value;
    }

    private static String normalizeStatus(String value, boolean enabled) {
        String normalized = (value == null || value.isBlank()) ? (enabled ? "ACTIVE" : "INACTIVE") : value.trim().toUpperCase();
        if (!enabled && "ACTIVE".equals(normalized)) {
            return "INACTIVE";
        }
        return switch (normalized) {
            case "ACTIVE", "CANARY", "INACTIVE", "DEPRECATED" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported model status: " + value);
        };
    }

    private static String normalizeSource(String value) {
        if (value == null || value.isBlank()) {
            return "runtime";
        }
        return value.trim().toLowerCase();
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static final class MutableRegisteredModel {
        private String name;
        private String category = "LLM";
        private String provider = "builtin";
        private boolean enabled = true;
        private String source = "runtime";
        private String activeVersion = "";
        private final long registeredAtEpochMillis;
        private final ConcurrentMap<String, MutableVersion> versions = new ConcurrentHashMap<>();

        private MutableRegisteredModel(String name, long registeredAtEpochMillis) {
            this.name = name;
            this.registeredAtEpochMillis = registeredAtEpochMillis;
        }
    }

    private static final class MutableVersion {
        private String version;
        private String status = "ACTIVE";
        private String latencyTier = "balanced";
        private int latencyMs = 250;
        private int accuracyScore = 75;
        private boolean defaultSelected;
        private boolean enabled = true;
        private final long registeredAtEpochMillis;

        private MutableVersion(String version, long registeredAtEpochMillis) {
            this.version = version;
            this.registeredAtEpochMillis = registeredAtEpochMillis;
        }
    }
}