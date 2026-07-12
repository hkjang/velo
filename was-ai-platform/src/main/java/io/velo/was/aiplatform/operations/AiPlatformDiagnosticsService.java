package io.velo.was.aiplatform.operations;

import io.velo.was.aiplatform.observability.AiPlatformUsageSnapshot;
import io.velo.was.aiplatform.provider.AiProviderRegistry;
import io.velo.was.aiplatform.registry.AiModelRegistrySummary;
import io.velo.was.aiplatform.tenant.AiTenantSnapshot;
import io.velo.was.config.ServerConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AiPlatformDiagnosticsService {

    private final ServerConfiguration configuration;
    private final AiModelRegistrySummary registrySummary;
    private final AiPlatformUsageSnapshot usageSnapshot;
    private final AiTenantSnapshot tenantSnapshot;
    private final AiProviderRegistry providerRegistry;

    public AiPlatformDiagnosticsService(ServerConfiguration configuration,
                                        AiModelRegistrySummary registrySummary,
                                        AiPlatformUsageSnapshot usageSnapshot,
                                        AiTenantSnapshot tenantSnapshot,
                                        AiProviderRegistry providerRegistry) {
        this.configuration = configuration;
        this.registrySummary = registrySummary;
        this.usageSnapshot = usageSnapshot;
        this.tenantSnapshot = tenantSnapshot;
        this.providerRegistry = providerRegistry;
    }

    public DiagnosticsSnapshot snapshot() {
        List<DiagnosticFinding> findings = new ArrayList<>();
        ServerConfiguration.AiPlatform ai = configuration.getServer().getAiPlatform();
        ServerConfiguration.Serving serving = ai.getServing();
        ServerConfiguration.Platform platform = ai.getPlatform();
        ServerConfiguration.Advanced advanced = ai.getAdvanced();

        if (!ai.isEnabled()) {
            finding(findings, "CRITICAL", "AI_PLATFORM_DISABLED", "AI platform is disabled",
                    "Gateway, registry, and platform APIs are configured off.",
                    "Enable server.aiPlatform.enabled before exposing AI endpoints.",
                    "server.aiPlatform.enabled");
        }
        if (registrySummary.routableModels() == 0) {
            finding(findings, "CRITICAL", "NO_ROUTABLE_MODELS", "No routable models are available",
                    "Model routing cannot serve inference traffic without an enabled active model.",
                    "Register or enable at least one model version.",
                    "server.aiPlatform.serving.models");
        }
        if (!advanced.isReadinessFailureStatusEnabled()) {
            finding(findings, "WARN", "READINESS_STATUS_SOFT", "Readiness does not fail with HTTP 503",
                    "Health checks may report HTTP 200 even when AI serving is disabled or unroutable.",
                    "Enable readinessFailureStatusEnabled for orchestrator-friendly readiness checks.",
                    "server.aiPlatform.advanced.readinessFailureStatusEnabled");
        }
        if (!advanced.isPromptFirewallEnabled()) {
            finding(findings, "WARN", "PROMPT_FIREWALL_DISABLED", "Prompt firewall is disabled",
                    "Prompt injection or oversized input protection is not enforced at the gateway.",
                    "Enable promptFirewallEnabled and maintain blocked terms for high-risk tenants.",
                    "server.aiPlatform.advanced.promptFirewallEnabled");
        }
        if (!advanced.isObservabilityEnabled() || !advanced.isObservabilityExportEnabled()) {
            finding(findings, "WARN", "OBSERVABILITY_EXPORT_LIMITED", "Metrics export is limited",
                    "Operators may not have enough signal for per-model latency, routing, and tenant usage.",
                    "Enable observability and observabilityExport for production operations.",
                    "server.aiPlatform.advanced.observabilityExportEnabled");
        }
        if (platform.isBillingEnabled() && !platform.isMultiTenantEnabled()) {
            finding(findings, "WARN", "BILLING_WITHOUT_TENANTS", "Billing is enabled without multi-tenancy",
                    "Metered requests cannot be reliably attributed to tenants.",
                    "Enable multiTenantEnabled or keep billing disabled until tenant keys are configured.",
                    "server.aiPlatform.platform.multiTenantEnabled");
        }
        if (platform.isMultiTenantEnabled() && tenantSnapshot.activeTenants() == 0) {
            finding(findings, "WARN", "NO_ACTIVE_TENANTS", "Multi-tenancy has no active tenants",
                    "Gateway authorization will reject tenant traffic unless API keys are issued.",
                    "Create at least one active tenant and rotate an API key.",
                    "/api/tenants");
        }
        if (!advanced.isProviderFailoverEnabled()) {
            finding(findings, "WARN", "PROVIDER_FAILOVER_DISABLED", "Provider failover is disabled",
                    "A single provider failure can surface directly to clients.",
                    "Enable providerFailoverEnabled for model/provider redundancy.",
                    "server.aiPlatform.advanced.providerFailoverEnabled");
        }
        if (!advanced.isProviderRetryEnabled() || advanced.getProviderMaxRetries() == 0) {
            finding(findings, "INFO", "PROVIDER_RETRY_LOW", "Provider retry budget is minimal",
                    "Transient upstream errors may not get a second chance before failing over.",
                    "Use providerMaxRetries >= 1 with a small backoff for unstable providers.",
                    "server.aiPlatform.advanced.providerMaxRetries");
        }
        if (!advanced.isSemanticCacheEnabled()) {
            finding(findings, "INFO", "SEMANTIC_CACHE_DISABLED", "Semantic cache is disabled",
                    "Repeated similar prompts will always execute a full routing/inference path.",
                    "Enable semanticCacheEnabled for lower latency on repetitive workloads.",
                    "server.aiPlatform.advanced.semanticCacheEnabled");
        }
        if (advanced.isShadowTestingEnabled() && advanced.getShadowModelName().isBlank()) {
            finding(findings, "WARN", "SHADOW_MODEL_MISSING", "Shadow testing has no model target",
                    "Shadow traffic is enabled but cannot compare a candidate model.",
                    "Set shadowModelName to a registered candidate model.",
                    "server.aiPlatform.advanced.shadowModelName");
        }
        if (advanced.isCanaryAutomationEnabled() && registrySummary.canaryVersions() == 0) {
            finding(findings, "INFO", "CANARY_AUTOMATION_IDLE", "Canary automation has no candidate",
                    "Automation is ready but no canary version is currently registered.",
                    "Register a canary version before expecting promotion or rollback decisions.",
                    "/api/operations/canary");
        }
        if (!advanced.isCanaryAutomationEnabled() && registrySummary.canaryVersions() > 0) {
            finding(findings, "WARN", "CANARY_AUTOMATION_DISABLED", "Canary versions require manual action",
                    "Canary candidates exist but automatic promote/rollback decisions are disabled.",
                    "Enable canaryAutomationEnabled or review canary decisions manually.",
                    "server.aiPlatform.advanced.canaryAutomationEnabled");
        }
        if (advanced.isGpuSchedulingEnabled() && advanced.getGpuQueueCapacity() < Math.max(1, registrySummary.routableModels())) {
            finding(findings, "WARN", "GPU_QUEUE_SMALL", "GPU scheduler queue is smaller than routable model count",
                    "GPU-backed requests can be rejected or delayed under burst traffic.",
                    "Increase gpuQueueCapacity or reduce concurrently routable GPU models.",
                    "server.aiPlatform.advanced.gpuQueueCapacity");
        }
        if ("SAAS".equals(ai.getMode()) && (!platform.isBillingEnabled() || !platform.isMultiTenantEnabled())) {
            finding(findings, "WARN", "SAAS_GUARDRAILS_INCOMPLETE", "SaaS mode guardrails are incomplete",
                    "SaaS mode should normally enable both tenant isolation and billing attribution.",
                    "Enable billingEnabled and multiTenantEnabled before external SaaS onboarding.",
                    "server.aiPlatform.platform");
        }

        Set<String> enabledModels = new HashSet<>();
        for (ServerConfiguration.ModelProfile model : serving.getModels()) {
            if (model.isEnabled()) {
                enabledModels.add(model.getName());
            }
        }
        for (ServerConfiguration.RoutePolicy policy : serving.getRoutePolicies()) {
            if (!enabledModels.contains(policy.getTargetModel())) {
                finding(findings, "CRITICAL", "ROUTE_TARGET_UNAVAILABLE", "Route policy targets an unavailable model",
                        "Policy " + policy.getName() + " points to " + policy.getTargetModel() + ".",
                        "Update the route target or enable/register the referenced model.",
                        "server.aiPlatform.serving.routePolicies");
            }
        }

        int openCircuits = 0;
        for (AiProviderRegistry.AiProviderCircuitInfo circuit : providerRegistry.listCircuitBreakers()) {
            if ("OPEN".equalsIgnoreCase(circuit.state())) {
                openCircuits++;
            }
        }
        if (openCircuits > 0) {
            finding(findings, "WARN", "PROVIDER_CIRCUIT_OPEN", "Provider circuit breaker is open",
                    openCircuits + " provider circuit(s) are currently open.",
                    "Inspect /api/providers/circuit-breakers and provider health before routing production traffic.",
                    "/api/providers/circuit-breakers");
        }

        int critical = count(findings, "CRITICAL");
        int warnings = count(findings, "WARN");
        int info = count(findings, "INFO");
        int score = Math.max(0, 100 - critical * 25 - warnings * 10 - info * 2);
        String posture = posture(score, critical, warnings);
        return new DiagnosticsSnapshot(
                ai.getMode(),
                posture,
                score,
                critical,
                warnings,
                info,
                registrySummary.routableModels(),
                providerRegistry.size(),
                tenantSnapshot.activeTenants(),
                usageSnapshot.inferCalls(),
                List.copyOf(findings),
                runbook(findings, ai)
        );
    }

    public RemediationPlan remediationPlan() {
        DiagnosticsSnapshot diagnostics = snapshot();
        List<RemediationAction> actions = new ArrayList<>();
        int priority = 1;
        for (DiagnosticFinding finding : diagnostics.findings()) {
            RemediationAction action = remediationAction(priority, finding);
            if (action != null) {
                actions.add(action);
                priority++;
            }
        }
        return new RemediationPlan(
                diagnostics.posture(),
                diagnostics.score(),
                diagnostics.criticalCount(),
                diagnostics.warningCount(),
                actions.size(),
                List.copyOf(actions)
        );
    }

    public ConfigPatchBundle configPatchBundle() {
        RemediationPlan plan = remediationPlan();
        List<RemediationAction> safeActions = new ArrayList<>();
        List<RemediationAction> manualActions = new ArrayList<>();
        for (RemediationAction action : plan.actions()) {
            if (isSafeConfigPatch(action)) {
                safeActions.add(action);
            } else {
                manualActions.add(action);
            }
        }
        List<String> patchLines = patchLines(safeActions);
        return new ConfigPatchBundle(
                plan.posture(),
                plan.score(),
                safeActions.size(),
                manualActions.size(),
                !safeActions.isEmpty(),
                "/api/config/diagnostics",
                propertyPatch(patchLines),
                yamlPatch(patchLines),
                List.copyOf(safeActions),
                List.copyOf(manualActions)
        );
    }

    public RolloutGate rolloutGate() {
        DiagnosticsSnapshot diagnostics = snapshot();
        ConfigPatchBundle patchBundle = configPatchBundle();
        ServerConfiguration.AiPlatform ai = configuration.getServer().getAiPlatform();

        List<GateCheck> checks = new ArrayList<>();
        checks.add(gateCheck("readiness",
                diagnostics.criticalCount() > 0 ? "BLOCK" : diagnostics.warningCount() > 0 ? "WARN" : "PASS",
                "Readiness posture",
                diagnostics.posture() + " with score " + diagnostics.scoreLabel()));
        checks.add(gateCheck("routing",
                diagnostics.routableModels() > 0 && !has(diagnostics.findings(), "ROUTE_TARGET_UNAVAILABLE") ? "PASS" : "BLOCK",
                "Model routing",
                diagnostics.routableModels() + " routable model(s) available."));
        checks.add(gateCheck("security",
                ai.getAdvanced().isPromptFirewallEnabled() ? "PASS" : "WARN",
                "Prompt firewall",
                ai.getAdvanced().isPromptFirewallEnabled()
                        ? "Prompt firewall is enabled."
                        : "Prompt firewall is disabled before rollout."));
        checks.add(gateCheck("observability",
                ai.getAdvanced().isObservabilityEnabled() && ai.getAdvanced().isObservabilityExportEnabled() ? "PASS" : "WARN",
                "Observability export",
                ai.getAdvanced().isObservabilityExportEnabled()
                        ? "Metrics export is available for rollout monitoring."
                        : "Metrics export is limited; watch rollout manually."));
        if (ai.getPlatform().isMultiTenantEnabled()) {
            checks.add(gateCheck("tenancy",
                    diagnostics.activeTenants() > 0 ? "PASS" : "BLOCK",
                    "Tenant readiness",
                    diagnostics.activeTenants() + " active tenant(s) configured."));
        }
        checks.add(gateCheck("canary",
                has(diagnostics.findings(), "CANARY_AUTOMATION_DISABLED") ? "WARN" : "PASS",
                "Canary automation",
                ai.getAdvanced().isCanaryAutomationEnabled()
                        ? "Canary automation is enabled."
                        : "Use manual canary evaluation before promotion."));
        checks.add(gateCheck("rollback",
                ai.getAdvanced().isModelBundleEnabled() ? "PASS" : "WARN",
                "Rollback manifest",
                ai.getAdvanced().isModelBundleEnabled()
                        ? "Model bundle manifests are available for rollback."
                        : "Model bundle manifests are disabled."));

        int blockers = countStatus(checks, "BLOCK");
        int warnings = countStatus(checks, "WARN");
        String decision = blockers > 0 ? "BLOCK" : warnings > 0 || patchBundle.safePatchCount() > 0 ? "REVIEW" : "GO";
        return new RolloutGate(
                decision,
                diagnostics.posture(),
                diagnostics.score(),
                blockers,
                warnings,
                patchBundle.safePatchCount(),
                patchBundle.manualReviewCount(),
                "GO".equals(decision),
                trafficRecommendation(decision),
                operatorSummary(decision, blockers, warnings, patchBundle),
                List.copyOf(checks),
                rolloutActions(decision, patchBundle)
        );
    }

    private static void finding(List<DiagnosticFinding> findings, String severity, String code, String title,
                                String detail, String recommendation, String path) {
        findings.add(new DiagnosticFinding(severity, code, title, detail, recommendation, path));
    }

    private static int count(List<DiagnosticFinding> findings, String severity) {
        int count = 0;
        for (DiagnosticFinding finding : findings) {
            if (severity.equals(finding.severity())) {
                count++;
            }
        }
        return count;
    }

    private static String posture(int score, int critical, int warnings) {
        if (critical > 0) {
            return "BLOCKED";
        }
        if (score >= 90 && warnings == 0) {
            return "READY";
        }
        if (score >= 75) {
            return "ATTENTION";
        }
        return "RISKY";
    }

    private static List<RunbookStep> runbook(List<DiagnosticFinding> findings, ServerConfiguration.AiPlatform ai) {
        List<RunbookStep> steps = new ArrayList<>();
        int order = 1;
        steps.add(new RunbookStep(order++, "preflight", "Check AI Platform readiness", "/api/readiness",
                "Expect HTTP 200, or HTTP 503 when readinessFailureStatusEnabled catches a serving blocker."));
        steps.add(new RunbookStep(order++, "configuration", "Review diagnostics and resolve critical findings",
                "/api/config/diagnostics", "Critical findings should be fixed before public traffic."));
        if (has(findings, "ROUTE_TARGET_UNAVAILABLE") || has(findings, "NO_ROUTABLE_MODELS")) {
            steps.add(new RunbookStep(order++, "routing", "Fix model registration and route targets",
                    "/api/models", "Confirm every route policy points to an enabled model."));
        }
        if (ai.getPlatform().isMultiTenantEnabled()) {
            steps.add(new RunbookStep(order++, "tenant", "Validate tenant keys, quota, and allowed models",
                    "/api/tenants", "Issue or rotate keys before onboarding clients."));
        }
        steps.add(new RunbookStep(order++, "release", "Evaluate canary promotion or rollback",
                "/api/operations/canary", "Apply only when success rate and latency match the release policy."));
        if (ai.getAdvanced().isGpuSchedulingEnabled()) {
            steps.add(new RunbookStep(order++, "capacity", "Check GPU queue capacity",
                    "/api/gpu/scheduler", "Increase queue capacity before GPU-backed burst traffic."));
        }
        if (ai.getAdvanced().isObservabilityExportEnabled()) {
            steps.add(new RunbookStep(order++, "observe", "Scrape Prometheus metrics",
                    "/api/metrics/prometheus", "Wire the endpoint into monitoring before rollout."));
        }
        steps.add(new RunbookStep(order, "rollback", "Keep model bundle manifests available for rollback",
                "/api/models/{name}/bundle", "Use the manifest to reconstruct runner and version metadata."));
        return List.copyOf(steps);
    }

    private static boolean has(List<DiagnosticFinding> findings, String code) {
        for (DiagnosticFinding finding : findings) {
            if (code.equals(finding.code())) {
                return true;
            }
        }
        return false;
    }

    private static GateCheck gateCheck(String id, String status, String title, String detail) {
        return new GateCheck(id, status, title, detail);
    }

    private static int countStatus(List<GateCheck> checks, String status) {
        int count = 0;
        for (GateCheck check : checks) {
            if (status.equals(check.status())) {
                count++;
            }
        }
        return count;
    }

    private static String trafficRecommendation(String decision) {
        return switch (decision) {
            case "GO" -> "Proceed with configured rollout policy.";
            case "REVIEW" -> "Limit to canary or low-percentage traffic until warnings are reviewed.";
            default -> "Hold external traffic until blockers are resolved.";
        };
    }

    private static String operatorSummary(String decision, int blockers, int warnings, ConfigPatchBundle patchBundle) {
        return switch (decision) {
            case "GO" -> "All rollout gates passed. Continue monitoring metrics and rollback readiness.";
            case "REVIEW" -> "Review " + warnings + " warning gate(s), " + patchBundle.safePatchCount()
                    + " safe patch(es), and " + patchBundle.manualReviewCount() + " manual item(s) before promotion.";
            default -> "Resolve " + blockers + " blocker gate(s) before exposing production traffic.";
        };
    }

    private static List<RolloutAction> rolloutActions(String decision, ConfigPatchBundle patchBundle) {
        List<RolloutAction> actions = new ArrayList<>();
        int order = 1;
        if (patchBundle.safePatchCount() > 0) {
            actions.add(new RolloutAction(order++, "Apply safe configuration patch",
                    "/api/config/patch-bundle", "Review yamlPatch, merge it into application.yaml, then reload."));
        }
        if (patchBundle.manualReviewCount() > 0) {
            actions.add(new RolloutAction(order++, "Review manual remediation actions",
                    "/api/config/remediation-plan", "Resolve tenant, routing, provider, or model-specific items manually."));
        }
        actions.add(new RolloutAction(order++, "Re-run diagnostics",
                "/api/config/diagnostics", "Confirm critical findings are gone and warnings are understood."));
        if ("BLOCK".equals(decision)) {
            actions.add(new RolloutAction(order, "Keep traffic closed",
                    "/api/readiness", "Expect rollout readiness to remain blocked until all blocker gates pass."));
        } else {
            actions.add(new RolloutAction(order, "Evaluate canary promotion",
                    "/api/operations/canary", "Promote only after latency, success rate, and rollback readiness look healthy."));
        }
        return List.copyOf(actions);
    }

    private static RemediationAction remediationAction(int priority, DiagnosticFinding finding) {
        return switch (finding.code()) {
            case "AI_PLATFORM_DISABLED" -> action(priority, finding, true, "LOW",
                    "Enable the AI Platform before exposing AI routes.",
                    "server.aiPlatform.enabled: true",
                    "Restart or reload the WAS configuration after enabling the platform.");
            case "NO_ROUTABLE_MODELS" -> action(priority, finding, false, "MEDIUM",
                    "Register or enable at least one routable model.",
                    "server.aiPlatform.serving.models[0].enabled: true",
                    "Use /api/models to verify an active model version exists.");
            case "READINESS_STATUS_SOFT" -> action(priority, finding, true, "LOW",
                    "Make readiness fail with HTTP 503 when serving is unavailable.",
                    "server.aiPlatform.advanced.readinessFailureStatusEnabled: true",
                    "This improves orchestrator behavior without changing inference routing.");
            case "PROMPT_FIREWALL_DISABLED" -> action(priority, finding, true, "LOW",
                    "Enable prompt firewall protections.",
                    "server.aiPlatform.advanced.promptFirewallEnabled: true",
                    "Review blocked terms before internet-facing rollout.");
            case "OBSERVABILITY_EXPORT_LIMITED" -> action(priority, finding, true, "LOW",
                    "Enable AI Platform observability and Prometheus export.",
                    "server.aiPlatform.advanced.observabilityEnabled: true\nserver.aiPlatform.advanced.observabilityExportEnabled: true",
                    "Wire /api/metrics/prometheus into monitoring after rollout.");
            case "BILLING_WITHOUT_TENANTS" -> action(priority, finding, false, "MEDIUM",
                    "Enable tenant attribution or disable billing until tenants are ready.",
                    "server.aiPlatform.platform.multiTenantEnabled: true",
                    "Issue tenant API keys before enforcing external traffic.");
            case "NO_ACTIVE_TENANTS" -> action(priority, finding, false, "MEDIUM",
                    "Create at least one active tenant and issue a key.",
                    "POST /api/tenants\nPOST /api/tenants/{id}/keys",
                    "Keep tenant model allowlists explicit for paid plans.");
            case "PROVIDER_FAILOVER_DISABLED" -> action(priority, finding, true, "LOW",
                    "Enable provider failover.",
                    "server.aiPlatform.advanced.providerFailoverEnabled: true",
                    "Confirm at least one fallback provider/model is healthy.");
            case "PROVIDER_RETRY_LOW" -> action(priority, finding, true, "LOW",
                    "Use a small retry budget for transient upstream failures.",
                    "server.aiPlatform.advanced.providerRetryEnabled: true\nserver.aiPlatform.advanced.providerMaxRetries: 1",
                    "Keep retry backoff small to avoid hurting p99 latency.");
            case "SEMANTIC_CACHE_DISABLED" -> action(priority, finding, true, "LOW",
                    "Enable semantic cache for repetitive workloads.",
                    "server.aiPlatform.advanced.semanticCacheEnabled: true",
                    "Start with conservative similarity threshold before production broadening.");
            case "SHADOW_MODEL_MISSING" -> action(priority, finding, false, "LOW",
                    "Set a registered shadow model target.",
                    "server.aiPlatform.advanced.shadowModelName: <candidate-model>",
                    "Use shadow mode only after validating tenant data boundaries.");
            case "CANARY_AUTOMATION_DISABLED" -> action(priority, finding, true, "LOW",
                    "Enable canary automation or manually evaluate canary decisions.",
                    "server.aiPlatform.advanced.canaryAutomationEnabled: true",
                    "Use /api/operations/canary/evaluate?apply=true only after reviewing decisions.");
            case "GPU_QUEUE_SMALL" -> action(priority, finding, true, "LOW",
                    "Increase GPU scheduler queue capacity.",
                    "server.aiPlatform.advanced.gpuQueueCapacity: <routable-models-or-burst-capacity>",
                    "Size queue capacity from observed burst traffic and GPU worker count.");
            case "SAAS_GUARDRAILS_INCOMPLETE" -> action(priority, finding, false, "MEDIUM",
                    "Enable SaaS guardrails before external onboarding.",
                    "server.aiPlatform.platform.multiTenantEnabled: true\nserver.aiPlatform.platform.billingEnabled: true",
                    "Pair with tenant API key rotation and allowlist policies.");
            case "ROUTE_TARGET_UNAVAILABLE" -> action(priority, finding, false, "MEDIUM",
                    "Update route policy target to an enabled model.",
                    "server.aiPlatform.serving.routePolicies[].targetModel: <enabled-model>",
                    "Validate with /api/config/diagnostics before reopening traffic.");
            case "PROVIDER_CIRCUIT_OPEN" -> action(priority, finding, false, "MEDIUM",
                    "Investigate provider health and reset traffic after recovery.",
                    "GET /api/providers/circuit-breakers",
                    "Check provider credentials, base URL, model name, and upstream health.");
            default -> action(priority, finding, false, "LOW",
                    finding.recommendation(),
                    finding.configPath(),
                    "Review the diagnostic finding before changing production settings.");
        };
    }

    private static RemediationAction action(int priority, DiagnosticFinding finding, boolean autoApplicable,
                                            String risk, String action, String patchSnippet, String validationHint) {
        return new RemediationAction(
                priority,
                finding.severity(),
                finding.code(),
                finding.title(),
                autoApplicable,
                risk,
                action,
                finding.configPath(),
                patchSnippet,
                validationHint
        );
    }

    private static boolean isSafeConfigPatch(RemediationAction action) {
        if (!action.autoApplicable()) {
            return false;
        }
        boolean hasPatchLine = false;
        for (String rawLine : action.patchSnippet().split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0 || line.contains("<")) {
                return false;
            }
            if (!line.substring(0, colon).trim().startsWith("server.aiPlatform.")) {
                return false;
            }
            hasPatchLine = true;
        }
        return hasPatchLine;
    }

    private static List<String> patchLines(List<RemediationAction> actions) {
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        for (RemediationAction action : actions) {
            for (String rawLine : action.patchSnippet().split("\\R")) {
                String line = rawLine.trim();
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return List.copyOf(lines);
    }

    private static String propertyPatch(List<String> patchLines) {
        if (patchLines.isEmpty()) {
            return "# No safe automatic configuration patch is currently available.\n";
        }
        StringBuilder sb = new StringBuilder(512);
        sb.append("# Velo AI Platform remediation patch\n");
        sb.append("# Review, merge into application.yaml, then validate with /api/config/diagnostics.\n");
        for (String line : patchLines) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static String yamlPatch(List<String> patchLines) {
        if (patchLines.isEmpty()) {
            return "# No safe automatic configuration patch is currently available.\n";
        }
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        for (String line : patchLines) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            putYamlValue(root, line.substring(0, colon).trim(), line.substring(colon + 1).trim());
        }
        StringBuilder sb = new StringBuilder(512);
        appendYaml(sb, root, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void putYamlValue(Map<String, Object> root, String dottedPath, String value) {
        String[] segments = dottedPath.split("\\.");
        Map<String, Object> current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            Object next = current.get(segments[i]);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                current.put(segments[i], next);
            }
            current = (Map<String, Object>) next;
        }
        current.put(segments[segments.length - 1], value);
    }

    @SuppressWarnings("unchecked")
    private static void appendYaml(StringBuilder sb, Map<String, Object> node, int indent) {
        String spaces = " ".repeat(indent);
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                sb.append(spaces).append(entry.getKey()).append(":\n");
                appendYaml(sb, (Map<String, Object>) value, indent + 2);
            } else {
                sb.append(spaces).append(entry.getKey()).append(": ").append(value).append('\n');
            }
        }
    }

    public record DiagnosticsSnapshot(String mode,
                                      String posture,
                                      int score,
                                      int criticalCount,
                                      int warningCount,
                                      int infoCount,
                                      int routableModels,
                                      int providerCount,
                                      int activeTenants,
                                      long inferenceCalls,
                                      List<DiagnosticFinding> findings,
                                      List<RunbookStep> runbook) {

        public String scoreLabel() {
            return String.format(Locale.US, "%d/100", score);
        }
    }

    public record DiagnosticFinding(String severity,
                                    String code,
                                    String title,
                                    String detail,
                                    String recommendation,
                                    String configPath) {
    }

    public record RunbookStep(int order,
                              String phase,
                              String action,
                              String endpoint,
                              String operatorHint) {
    }

    public record RemediationPlan(String posture,
                                  int score,
                                  int criticalCount,
                                  int warningCount,
                                  int actionCount,
                                  List<RemediationAction> actions) {
    }

    public record ConfigPatchBundle(String posture,
                                    int score,
                                    int safePatchCount,
                                    int manualReviewCount,
                                    boolean restartRequired,
                                    String validationEndpoint,
                                    String propertyPatch,
                                    String yamlPatch,
                                    List<RemediationAction> safeActions,
                                    List<RemediationAction> manualReviewActions) {
    }

    public record RolloutGate(String decision,
                              String posture,
                              int score,
                              int blockers,
                              int warnings,
                              int safePatchCount,
                              int manualReviewCount,
                              boolean canPromote,
                              String trafficRecommendation,
                              String operatorSummary,
                              List<GateCheck> checks,
                              List<RolloutAction> nextActions) {
    }

    public record GateCheck(String id,
                            String status,
                            String title,
                            String detail) {
    }

    public record RolloutAction(int order,
                                String title,
                                String endpoint,
                                String operatorHint) {
    }

    public record RemediationAction(int priority,
                                    String severity,
                                    String findingCode,
                                    String title,
                                    boolean autoApplicable,
                                    String risk,
                                    String action,
                                    String configPath,
                                    String patchSnippet,
                                    String validationHint) {
    }
}
