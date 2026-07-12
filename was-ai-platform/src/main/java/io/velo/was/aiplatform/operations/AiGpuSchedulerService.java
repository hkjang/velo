package io.velo.was.aiplatform.operations;

import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.config.ServerConfiguration;

public class AiGpuSchedulerService {

    private final ServerConfiguration configuration;
    private final AiModelRegistryService registryService;

    public AiGpuSchedulerService(ServerConfiguration configuration, AiModelRegistryService registryService) {
        this.configuration = configuration;
        this.registryService = registryService;
    }

    public GpuSchedulerSnapshot snapshot() {
        ServerConfiguration.Advanced advanced = configuration.getServer().getAiPlatform().getAdvanced();
        long gpuCandidates = registryService.weightedRoutingModels().stream()
                .filter(model -> model.getProvider() != null
                        && (model.getProvider().toLowerCase(java.util.Locale.ROOT).contains("gpu")
                        || model.getProvider().toLowerCase(java.util.Locale.ROOT).contains("vllm")
                        || model.getProvider().toLowerCase(java.util.Locale.ROOT).contains("sglang")))
                .count();
        int queueCapacity = advanced.getGpuQueueCapacity();
        int reservedSlots = advanced.isGpuSchedulingEnabled() ? Math.min(queueCapacity, (int) Math.max(1, gpuCandidates) * 4) : 0;
        return new GpuSchedulerSnapshot(
                advanced.isGpuSchedulingEnabled(),
                queueCapacity,
                reservedSlots,
                Math.max(0, queueCapacity - reservedSlots),
                gpuCandidates,
                advanced.isGpuSchedulingEnabled() ? "READY" : "DISABLED"
        );
    }

    public record GpuSchedulerSnapshot(boolean enabled,
                                       int queueCapacity,
                                       int reservedSlots,
                                       int availableSlots,
                                       long gpuCandidateModels,
                                       String state) {
    }
}
