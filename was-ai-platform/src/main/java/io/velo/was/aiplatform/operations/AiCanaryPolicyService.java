package io.velo.was.aiplatform.operations;

import io.velo.was.aiplatform.audit.AiGatewayAuditEntry;
import io.velo.was.aiplatform.audit.AiGatewayAuditLog;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.aiplatform.registry.AiModelVersionInfo;
import io.velo.was.aiplatform.registry.AiRegisteredModel;
import io.velo.was.config.ServerConfiguration;

import java.util.ArrayList;
import java.util.List;

public class AiCanaryPolicyService {

    private final ServerConfiguration configuration;
    private final AiModelRegistryService registryService;
    private final AiGatewayAuditLog auditLog;

    public AiCanaryPolicyService(ServerConfiguration configuration,
                                 AiModelRegistryService registryService,
                                 AiGatewayAuditLog auditLog) {
        this.configuration = configuration;
        this.registryService = registryService;
        this.auditLog = auditLog;
    }

    public List<CanaryDecision> evaluate(boolean apply) {
        ServerConfiguration.Advanced advanced = configuration.getServer().getAiPlatform().getAdvanced();
        List<CanaryDecision> decisions = new ArrayList<>();
        for (AiRegisteredModel model : registryService.listModels()) {
            List<AiModelVersionInfo> canaries = model.versions().stream()
                    .filter(version -> "CANARY".equals(version.status()) && version.enabled())
                    .toList();
            for (AiModelVersionInfo canary : canaries) {
                List<AiGatewayAuditEntry> entries = auditLog == null
                        ? List.of()
                        : auditLog.query(10_000, null, null, model.name(), null);
                Metrics metrics = metrics(entries);
                String action = actionFor(metrics, advanced);
                boolean applied = false;
                if (apply && advanced.isCanaryAutomationEnabled()) {
                    if ("PROMOTE".equals(action)) {
                        registryService.updateVersionStatus(model.name(), canary.version(), "ACTIVE");
                        applied = true;
                    } else if ("ROLLBACK".equals(action)) {
                        registryService.updateVersionStatus(model.name(), canary.version(), "INACTIVE");
                        applied = true;
                    }
                }
                decisions.add(new CanaryDecision(
                        model.name(),
                        canary.version(),
                        action,
                        applied,
                        metrics.totalRequests(),
                        metrics.successRate(),
                        metrics.avgLatencyMs(),
                        reasonFor(action, metrics, advanced)
                ));
            }
        }
        return decisions;
    }

    private static String actionFor(Metrics metrics, ServerConfiguration.Advanced advanced) {
        if (metrics.totalRequests() < advanced.getCanaryPromotionMinRequests()) {
            return "HOLD";
        }
        if (metrics.successRate() < advanced.getCanaryRollbackSuccessRate()
                || metrics.avgLatencyMs() > advanced.getCanaryMaxAvgLatencyMs()) {
            return "ROLLBACK";
        }
        if (metrics.successRate() >= advanced.getCanaryPromotionSuccessRate()) {
            return "PROMOTE";
        }
        return "HOLD";
    }

    private static String reasonFor(String action, Metrics metrics, ServerConfiguration.Advanced advanced) {
        return switch (action) {
            case "PROMOTE" -> "Canary meets success-rate and latency thresholds.";
            case "ROLLBACK" -> "Canary violates rollback success-rate or latency threshold.";
            default -> metrics.totalRequests() < advanced.getCanaryPromotionMinRequests()
                    ? "Not enough requests to evaluate canary."
                    : "Canary is within guardrails but not ready for promotion.";
        };
    }

    private static Metrics metrics(List<AiGatewayAuditEntry> entries) {
        if (entries.isEmpty()) {
            return new Metrics(0, 100.0d, 0.0d);
        }
        long success = entries.stream().filter(AiGatewayAuditEntry::success).count();
        double successRate = (double) success / entries.size() * 100.0d;
        double avgLatency = entries.stream().mapToLong(AiGatewayAuditEntry::durationMs).average().orElse(0.0d);
        return new Metrics(entries.size(), successRate, avgLatency);
    }

    public record CanaryDecision(String modelName,
                                 String version,
                                 String action,
                                 boolean applied,
                                 long requests,
                                 double successRate,
                                 double avgLatencyMs,
                                 String reason) {
    }

    private record Metrics(long totalRequests, double successRate, double avgLatencyMs) {
    }
}
