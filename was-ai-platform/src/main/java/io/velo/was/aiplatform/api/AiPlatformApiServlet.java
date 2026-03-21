package io.velo.was.aiplatform.api;

import io.velo.was.aiplatform.edge.AiEdgeService;
import io.velo.was.aiplatform.intent.*;
import io.velo.was.aiplatform.plugin.AiPluginRegistry;
import io.velo.was.aiplatform.provider.AiProviderRegistry;
import io.velo.was.aiplatform.billing.AiBillingService;
import io.velo.was.aiplatform.billing.AiBillingSnapshot;
// Fine-tuning feature removed
import io.velo.was.aiplatform.gateway.AiGatewayService;
import io.velo.was.aiplatform.gateway.AiGatewayServlet;
import io.velo.was.aiplatform.observability.AiPlatformUsageService;
import io.velo.was.aiplatform.observability.AiPlatformUsageSnapshot;
import io.velo.was.aiplatform.publishing.AiPublishedApiService;
import io.velo.was.aiplatform.registry.AiModelRegistrationRequest;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.aiplatform.registry.AiModelRegistrySummary;
import io.velo.was.aiplatform.registry.AiRegisteredModel;
import io.velo.was.aiplatform.tenant.AiTenantIssuedKey;
import io.velo.was.aiplatform.tenant.AiTenantRegistrationRequest;
import io.velo.was.aiplatform.tenant.AiTenantService;
import io.velo.was.config.ServerConfiguration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AiPlatformApiServlet extends HttpServlet {

    private static final Pattern JSON_STRING_FIELD = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"((?:\\\\.|[^\\\"])*)\"", Pattern.DOTALL);
    private static final Pattern JSON_BOOLEAN_FIELD = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_NUMBER_FIELD = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*(-?[0-9]+)", Pattern.CASE_INSENSITIVE);

    private final ServerConfiguration configuration;
    private final AiModelRegistryService registryService;
    private final AiGatewayService gatewayService;
    private final AiPlatformUsageService usageService;
    private final AiPublishedApiService publishedApiService;
    private final AiBillingService billingService;
    // Fine-tuning removed
    private final AiTenantService tenantService;
    private final AiPluginRegistry pluginRegistry;
    private final AiProviderRegistry providerRegistry;
    private final AiEdgeService edgeService;
    private final IntentRoutingPolicyService intentPolicyService;
    private final RouteAuditLogger auditLogger;
    private final RouteDecisionEngine intentEngine;

    public AiPlatformApiServlet(ServerConfiguration configuration,
                                AiModelRegistryService registryService,
                                AiGatewayService gatewayService,
                                AiPlatformUsageService usageService,
                                AiPublishedApiService publishedApiService,
                                AiBillingService billingService,
                                AiTenantService tenantService) {
        this(configuration, registryService, gatewayService, usageService, publishedApiService, billingService,
                tenantService, new AiPluginRegistry(), new AiProviderRegistry(), new AiEdgeService());
    }

    public AiPlatformApiServlet(ServerConfiguration configuration,
                                AiModelRegistryService registryService,
                                AiGatewayService gatewayService,
                                AiPlatformUsageService usageService,
                                AiPublishedApiService publishedApiService,
                                AiBillingService billingService,
                                AiTenantService tenantService,
                                AiPluginRegistry pluginRegistry,
                                AiProviderRegistry providerRegistry) {
        this(configuration, registryService, gatewayService, usageService, publishedApiService, billingService,
                tenantService, pluginRegistry, providerRegistry, new AiEdgeService());
    }

    public AiPlatformApiServlet(ServerConfiguration configuration,
                                AiModelRegistryService registryService,
                                AiGatewayService gatewayService,
                                AiPlatformUsageService usageService,
                                AiPublishedApiService publishedApiService,
                                AiBillingService billingService,
                                AiTenantService tenantService,
                                AiPluginRegistry pluginRegistry,
                                AiProviderRegistry providerRegistry,
                                AiEdgeService edgeService) {
        this(configuration, registryService, gatewayService, usageService, publishedApiService, billingService,
                tenantService, pluginRegistry, providerRegistry, edgeService, null, null, null);
    }

    public AiPlatformApiServlet(ServerConfiguration configuration,
                                AiModelRegistryService registryService,
                                AiGatewayService gatewayService,
                                AiPlatformUsageService usageService,
                                AiPublishedApiService publishedApiService,
                                AiBillingService billingService,
                                AiTenantService tenantService,
                                AiPluginRegistry pluginRegistry,
                                AiProviderRegistry providerRegistry,
                                AiEdgeService edgeService,
                                IntentRoutingPolicyService intentPolicyService,
                                RouteAuditLogger auditLogger,
                                RouteDecisionEngine intentEngine) {
        this.configuration = configuration;
        this.registryService = registryService;
        this.gatewayService = gatewayService;
        this.usageService = usageService;
        this.publishedApiService = publishedApiService;
        this.billingService = billingService;
        this.tenantService = tenantService;
        this.pluginRegistry = pluginRegistry;
        this.providerRegistry = providerRegistry;
        this.edgeService = edgeService;
        this.intentPolicyService = intentPolicyService;
        this.auditLogger = auditLogger;
        this.intentEngine = intentEngine;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        prepare(resp);
        String path = normalizePath(req.getPathInfo());
        AiModelRegistrySummary summary = registryService.summary();
        AiPlatformUsageSnapshot usage = usageService.snapshot(
                configuration.getServer().getAiPlatform().getPlatform().isBillingEnabled(),
                gatewayService,
                registryService
        );
        if ("/".equals(path) || "/overview".equals(path)) {
            usageService.recordControlPlaneAccess("/api/overview");
            resp.getWriter().write(AiPlatformApiJson.overview(configuration, summary, usage, tenantService.snapshot()));
            return;
        }
        if ("/status".equals(path)) {
            usageService.recordControlPlaneAccess("/api/status");
            resp.getWriter().write(AiPlatformApiJson.status(configuration, summary));
            return;
        }
        if ("/models".equals(path)) {
            usageService.recordControlPlaneAccess("/api/models");
            resp.getWriter().write(AiPlatformApiJson.models(registryService.listModels(), summary));
            return;
        }
        if (path.startsWith("/models/")) {
            usageService.recordControlPlaneAccess("/api/models/{name}");
            AiRegisteredModel model = registryService.findModel(path.substring("/models/".length()));
            if (model == null) {
                notFound(resp, "Model not found");
                return;
            }
            resp.getWriter().write(AiPlatformApiJson.model(model));
            return;
        }
        if ("/usage".equals(path) || "/metrics".equals(path)) {
            usageService.recordControlPlaneAccess("/api/usage");
            resp.getWriter().write(AiPlatformApiJson.usage(usage));
            return;
        }
        if ("/published-apis".equals(path)) {
            usageService.recordControlPlaneAccess("/api/published-apis");
            resp.getWriter().write(AiPlatformExtendedJson.publishedEndpoints(publishedApiService.listEndpoints(req.getContextPath())));
            return;
        }
        if ("/billing".equals(path)) {
            usageService.recordControlPlaneAccess("/api/billing");
            AiBillingSnapshot billing = billingService.snapshot(usage);
            resp.getWriter().write(AiPlatformExtendedJson.billing(billing));
            return;
        }
        if ("/tenants".equals(path)) {
            usageService.recordControlPlaneAccess("/api/tenants");
            resp.getWriter().write(AiPlatformExtendedJson.tenants(tenantService.snapshot()));
            return;
        }
        if (path.startsWith("/tenants/")) {
            usageService.recordControlPlaneAccess("/api/tenants/{id}");
            handleTenantGet(resp, path);
            return;
        }
        if ("/plugins".equals(path)) {
            usageService.recordControlPlaneAccess("/api/plugins");
            resp.getWriter().write(AiPlatformExtendedJson.plugins(pluginRegistry.listPlugins()));
            return;
        }
        if ("/providers".equals(path)) {
            usageService.recordControlPlaneAccess("/api/providers");
            resp.getWriter().write(AiPlatformExtendedJson.providers(providerRegistry.listProviders()));
            return;
        }
        if ("/edge/devices".equals(path)) {
            if (!configuration.getServer().getAiPlatform().getServing().isEdgeAiEnabled()) {
                unavailable(resp, "Edge AI is disabled in configuration");
                return;
            }
            usageService.recordControlPlaneAccess("/api/edge/devices");
            resp.getWriter().write(AiPlatformExtendedJson.edgeDevices(edgeService.listDevices()));
            return;
        }
        if ("/fine-tuning/jobs".equals(path) || path.startsWith("/fine-tuning/jobs/")) {
            unavailable(resp, "\ud30c\uc778\ud29c\ub2dd \uae30\ub2a5\uc774 \uc81c\uac70\ub418\uc5c8\uc2b5\ub2c8\ub2e4.");
            return;
        }
        if ("/config".equals(path)) {
            usageService.recordControlPlaneAccess("/api/config");
            resp.getWriter().write(buildConfigJson());
            return;
        }
        // 의도 기반 라우팅 API
        if ("/intent/keywords".equals(path) && intentPolicyService != null) {
            usageService.recordControlPlaneAccess("/api/intent/keywords");
            resp.getWriter().write(buildIntentKeywordsJson());
            return;
        }
        if ("/intent/policies".equals(path) && intentPolicyService != null) {
            usageService.recordControlPlaneAccess("/api/intent/policies");
            resp.getWriter().write(buildIntentPoliciesJson());
            return;
        }
        if ("/intent/audit".equals(path) && auditLogger != null) {
            usageService.recordControlPlaneAccess("/api/intent/audit");
            int limit = parseInteger(req.getParameter("limit"), 50);
            resp.getWriter().write(buildAuditJson(limit));
            return;
        }
        if ("/intent/stats".equals(path) && auditLogger != null) {
            usageService.recordControlPlaneAccess("/api/intent/stats");
            resp.getWriter().write(buildIntentStatsJson());
            return;
        }
        notFound(resp, "Not Found");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        prepare(resp);
        String path = normalizePath(req.getPathInfo());
        try {
            if (path.startsWith("/models/")) {
                String modelName = path.substring("/models/".length());
                if (modelName.isBlank()) {
                    badRequest(resp, "Model name is required");
                    return;
                }
                AiRegisteredModel model = registryService.findModel(modelName);
                if (model == null) {
                    notFound(resp, "모델을 찾을 수 없습니다: " + modelName);
                    return;
                }
                registryService.removeModel(modelName);
                usageService.recordRegistryMutation("delete");
                resp.getWriter().write("{\"deleted\":true,\"model\":\"" + AiGatewayServlet.escapeJson(modelName) + "\"}");
                return;
            }
            if (path.startsWith("/tenants/")) {
                String tenantId = path.substring("/tenants/".length());
                if (tenantId.isBlank()) {
                    badRequest(resp, "Tenant ID is required");
                    return;
                }
                tenantService.removeTenant(tenantId);
                resp.getWriter().write("{\"deleted\":true,\"tenantId\":\"" + AiGatewayServlet.escapeJson(tenantId) + "\"}");
                return;
            }
            // 의도 키워드 삭제
            if (path.startsWith("/intent/keywords/") && intentPolicyService != null) {
                String keywordId = path.substring("/intent/keywords/".length());
                intentPolicyService.removeKeyword(keywordId);
                resp.getWriter().write("{\"deleted\":true,\"keywordId\":\"" + esc(keywordId) + "\"}");
                return;
            }
            // 의도 정책 삭제
            if (path.startsWith("/intent/policies/") && intentPolicyService != null) {
                String policyId = path.substring("/intent/policies/".length());
                intentPolicyService.removePolicy(policyId);
                resp.getWriter().write("{\"deleted\":true,\"policyId\":\"" + esc(policyId) + "\"}");
                return;
            }
            notFound(resp, "Not Found");
        } catch (NoSuchElementException e) {
            notFound(resp, e.getMessage() != null ? e.getMessage() : "Not Found");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        prepare(resp);
        String path = normalizePath(req.getPathInfo());
        String body = req.getReader().lines().collect(Collectors.joining("\n"));
        try {
            // 의도 키워드 수정
            if (path.startsWith("/intent/keywords/") && intentPolicyService != null) {
                String keywordId = path.substring("/intent/keywords/".length());
                String primaryKeyword = firstNonBlank(req.getParameter("primaryKeyword"), extractJsonString(body, "primaryKeyword"));
                String synonymsStr = firstNonBlank(req.getParameter("synonyms"), extractJsonString(body, "synonyms"));
                String intentStr = firstNonBlank(req.getParameter("intent"), extractJsonString(body, "intent"));
                int priority = parseInteger(firstNonBlank(req.getParameter("priority"), extractJsonNumber(body, "priority")), 50);
                boolean enabled = parseBoolean(firstNonBlank(req.getParameter("enabled"), extractJsonBoolean(body, "enabled")), true);
                java.util.List<String> synonyms = synonymsStr.isBlank() ? java.util.List.of()
                        : java.util.Arrays.stream(synonymsStr.split("[,;|]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
                IntentKeyword updated = intentPolicyService.updateKeyword(keywordId, primaryKeyword, synonyms, IntentType.fromString(intentStr), priority, enabled);
                resp.getWriter().write(buildKeywordJson(updated));
                return;
            }
            // 의도 정책 수정
            if (path.startsWith("/intent/policies/") && intentPolicyService != null) {
                String policyId = path.substring("/intent/policies/".length());
                String intentStr = firstNonBlank(req.getParameter("intent"), extractJsonString(body, "intent"));
                int priority = parseInteger(firstNonBlank(req.getParameter("priority"), extractJsonNumber(body, "priority")), 50);
                String routeTarget = firstNonBlank(req.getParameter("routeTarget"), extractJsonString(body, "routeTarget"));
                String modelName = firstNonBlank(req.getParameter("modelName"), extractJsonString(body, "modelName"));
                String fallback = firstNonBlank(req.getParameter("fallbackModel"), extractJsonString(body, "fallbackModel"));
                boolean streaming = parseBoolean(firstNonBlank(req.getParameter("streamingPreferred"), extractJsonBoolean(body, "streamingPreferred")), false);
                boolean enabled = parseBoolean(firstNonBlank(req.getParameter("enabled"), extractJsonBoolean(body, "enabled")), true);
                String tenantOverride = firstNonBlank(req.getParameter("tenantOverride"), extractJsonString(body, "tenantOverride"));
                int maxTokens = parseInteger(firstNonBlank(req.getParameter("maxInputTokens"), extractJsonNumber(body, "maxInputTokens")), 0);
                RoutingPolicy updated = intentPolicyService.updatePolicy(policyId, IntentType.fromString(intentStr), priority, routeTarget, modelName, fallback, streaming, enabled, tenantOverride, maxTokens);
                resp.getWriter().write(buildPolicyJson(updated));
                return;
            }
            notFound(resp, "Not Found");
        } catch (IllegalArgumentException e) {
            badRequest(resp, e.getMessage());
        } catch (NoSuchElementException e) {
            notFound(resp, e.getMessage() != null ? e.getMessage() : "Not Found");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        prepare(resp);
        String path = normalizePath(req.getPathInfo());
        String body = req.getReader().lines().collect(Collectors.joining("\n"));
        try {
            if ("/models".equals(path)) {
                if (!configuration.getServer().getAiPlatform().getPlatform().isModelRegistrationEnabled()) {
                    unavailable(resp, "Model registration is disabled in configuration");
                    return;
                }
                AiRegisteredModel model = registryService.registerOrUpdate(readRegistrationRequest(req, body));
                usageService.recordRegistryMutation("register");
                resp.setStatus(HttpServletResponse.SC_CREATED);
                resp.getWriter().write(AiPlatformApiJson.model(model));
                return;
            }
            if (path.startsWith("/models/")) {
                handleStatusChange(req, resp, path, body);
                return;
            }
            if ("/tenants".equals(path)) {
                usageService.recordControlPlaneAccess("/api/tenants");
                resp.setStatus(HttpServletResponse.SC_CREATED);
                resp.getWriter().write(AiPlatformExtendedJson.tenant(tenantService.registerOrUpdate(readTenantRequest(req, body))));
                return;
            }
            if (path.startsWith("/tenants/")) {
                handleTenantPost(req, resp, path, body);
                return;
            }
            if ("/edge/devices".equals(path)) {
                if (!configuration.getServer().getAiPlatform().getServing().isEdgeAiEnabled()) {
                    unavailable(resp, "Edge AI is disabled in configuration");
                    return;
                }
                usageService.recordControlPlaneAccess("/api/edge/devices");
                resp.setStatus(HttpServletResponse.SC_CREATED);
                resp.getWriter().write(AiPlatformExtendedJson.edgeDevice(edgeService.register(
                        firstNonBlank(req.getParameter("deviceId"), extractJsonString(body, "deviceId")),
                        firstNonBlank(req.getParameter("displayName"), extractJsonString(body, "displayName")),
                        firstNonBlank(req.getParameter("deviceType"), extractJsonString(body, "deviceType")),
                        parseInteger(firstNonBlank(req.getParameter("maxMemoryMb"), extractJsonNumber(body, "maxMemoryMb")), 512)
                )));
                return;
            }
            if (path.startsWith("/edge/devices/") && path.endsWith("/deploy")) {
                if (!configuration.getServer().getAiPlatform().getServing().isEdgeAiEnabled()) {
                    unavailable(resp, "Edge AI is disabled in configuration");
                    return;
                }
                String deviceId = path.substring("/edge/devices/".length(), path.length() - "/deploy".length());
                resp.getWriter().write(AiPlatformExtendedJson.edgeDevice(edgeService.deploy(deviceId,
                        firstNonBlank(req.getParameter("model"), extractJsonString(body, "model")),
                        firstNonBlank(req.getParameter("version"), extractJsonString(body, "version"))
                )));
                return;
            }
            if ("/fine-tuning/jobs".equals(path) || (path.startsWith("/fine-tuning/jobs/") && path.endsWith("/cancel"))) {
                unavailable(resp, "\ud30c\uc778\ud29c\ub2dd \uae30\ub2a5\uc774 \uc81c\uac70\ub418\uc5c8\uc2b5\ub2c8\ub2e4.");
                return;
            }
            // 의도 기반 라우팅 API
            if ("/intent/test".equals(path) && intentEngine != null) {
                usageService.recordControlPlaneAccess("/api/intent/test");
                String prompt = firstNonBlank(req.getParameter("prompt"), extractJsonString(body, "prompt"));
                String tenant = firstNonBlank(req.getParameter("tenantId"), extractJsonString(body, "tenantId"));
                if (prompt.isBlank()) {
                    badRequest(resp, "prompt is required");
                    return;
                }
                IntentRouteDecision decision = intentEngine.decide(prompt, tenant.isBlank() ? null : tenant);
                if (auditLogger != null) {
                    String normalized = RequestNormalizer.normalize(prompt);
                    auditLogger.log(tenant.isBlank() ? "console" : tenant, prompt, normalized, decision, false, 0);
                }
                resp.getWriter().write(buildIntentDecisionJson(decision));
                return;
            }
            if ("/intent/preview".equals(path) && intentEngine != null) {
                usageService.recordControlPlaneAccess("/api/intent/preview");
                String prompt = firstNonBlank(req.getParameter("prompt"), extractJsonString(body, "prompt"));
                if (prompt.isBlank()) {
                    badRequest(resp, "prompt is required");
                    return;
                }
                RouteDecisionEngine.PreviewResult preview = intentEngine.preview(prompt);
                resp.getWriter().write(buildPreviewJson(preview));
                return;
            }
            if ("/intent/keywords".equals(path) && intentPolicyService != null) {
                usageService.recordControlPlaneAccess("/api/intent/keywords");
                String primaryKeyword = firstNonBlank(req.getParameter("primaryKeyword"), extractJsonString(body, "primaryKeyword"));
                String synonymsStr = firstNonBlank(req.getParameter("synonyms"), extractJsonString(body, "synonyms"));
                String intentStr = firstNonBlank(req.getParameter("intent"), extractJsonString(body, "intent"));
                int priority = parseInteger(firstNonBlank(req.getParameter("priority"), extractJsonNumber(body, "priority")), 50);
                java.util.List<String> synonyms = synonymsStr.isBlank() ? java.util.List.of()
                        : java.util.Arrays.stream(synonymsStr.split("[,;|]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
                IntentKeyword kw = intentPolicyService.addKeyword(primaryKeyword, synonyms, IntentType.fromString(intentStr), priority);
                resp.setStatus(HttpServletResponse.SC_CREATED);
                resp.getWriter().write(buildKeywordJson(kw));
                return;
            }
            if ("/intent/policies".equals(path) && intentPolicyService != null) {
                usageService.recordControlPlaneAccess("/api/intent/policies");
                String intentStr = firstNonBlank(req.getParameter("intent"), extractJsonString(body, "intent"));
                int priority = parseInteger(firstNonBlank(req.getParameter("priority"), extractJsonNumber(body, "priority")), 50);
                String routeTarget = firstNonBlank(req.getParameter("routeTarget"), extractJsonString(body, "routeTarget"));
                String modelName = firstNonBlank(req.getParameter("modelName"), extractJsonString(body, "modelName"));
                String fallback = firstNonBlank(req.getParameter("fallbackModel"), extractJsonString(body, "fallbackModel"));
                boolean streaming = parseBoolean(firstNonBlank(req.getParameter("streamingPreferred"), extractJsonBoolean(body, "streamingPreferred")), false);
                String tenantOverride = firstNonBlank(req.getParameter("tenantOverride"), extractJsonString(body, "tenantOverride"));
                int maxTokens = parseInteger(firstNonBlank(req.getParameter("maxInputTokens"), extractJsonNumber(body, "maxInputTokens")), 0);
                RoutingPolicy pol = intentPolicyService.addPolicy(IntentType.fromString(intentStr), priority, routeTarget, modelName, fallback, streaming, tenantOverride, maxTokens);
                resp.setStatus(HttpServletResponse.SC_CREATED);
                resp.getWriter().write(buildPolicyJson(pol));
                return;
            }
            notFound(resp, "Not Found");
        } catch (IllegalArgumentException e) {
            badRequest(resp, e.getMessage());
        } catch (NoSuchElementException e) {
            String hint = e.getMessage();
            if (hint != null && hint.contains("Model")) {
                hint += " — 먼저 [버전 등록]으로 모델을 등록하세요.";
            } else if (hint != null && hint.contains("Tenant")) {
                hint += " — 먼저 [테넌트 등록]으로 테넌트를 생성하세요.";
            }
            notFound(resp, hint != null ? hint : "Not Found");
        }
    }

    private void handleTenantGet(HttpServletResponse resp, String path) throws IOException {
        String[] segments = Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
        if (segments.length == 2 && "tenants".equals(segments[0])) {
            resp.getWriter().write(AiPlatformExtendedJson.tenant(tenantService.getTenant(segments[1])));
            return;
        }
        if (segments.length == 3 && "tenants".equals(segments[0]) && "usage".equals(segments[2])) {
            resp.getWriter().write(AiPlatformExtendedJson.tenantUsage(segments[1], tenantService.getTenantUsage(segments[1])));
            return;
        }
        notFound(resp, "Not Found");
    }

    private void handleTenantPost(HttpServletRequest req, HttpServletResponse resp, String path, String body) throws IOException {
        String[] segments = Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
        if (segments.length == 3 && "tenants".equals(segments[0]) && "keys".equals(segments[2])) {
            usageService.recordControlPlaneAccess("/api/tenants/{id}/keys");
            AiTenantIssuedKey issuedKey = tenantService.issueApiKey(segments[1], firstNonBlank(req.getParameter("label"), extractJsonString(body, "label")));
            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.getWriter().write(AiPlatformExtendedJson.issuedTenantKey(issuedKey));
            return;
        }
        notFound(resp, "Not Found");
    }

    private void handleStatusChange(HttpServletRequest req, HttpServletResponse resp, String path, String body) throws IOException {
        if (!configuration.getServer().getAiPlatform().getPlatform().isVersionManagementEnabled()) {
            unavailable(resp, "Model version management is disabled in configuration");
            return;
        }
        String[] segments = Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toArray(String[]::new);
        if (segments.length == 5 && "models".equals(segments[0]) && "versions".equals(segments[2]) && "status".equals(segments[4])) {
            String state = firstNonBlank(req.getParameter("state"), extractJsonString(body, "state"));
            if (state.isBlank()) {
                throw new IllegalArgumentException("state is required");
            }
            AiRegisteredModel model = registryService.updateVersionStatus(segments[1], segments[3], state);
            usageService.recordRegistryMutation("status:" + state.trim().toUpperCase());
            resp.getWriter().write(AiPlatformApiJson.model(model));
            return;
        }
        notFound(resp, "Not Found");
    }

    private AiModelRegistrationRequest readRegistrationRequest(HttpServletRequest req, String body) {
        return new AiModelRegistrationRequest(
                firstNonBlank(req.getParameter("name"), extractJsonString(body, "name")),
                firstNonBlank(req.getParameter("category"), extractJsonString(body, "category")),
                firstNonBlank(req.getParameter("provider"), extractJsonString(body, "provider")),
                firstNonBlank(req.getParameter("version"), extractJsonString(body, "version")),
                firstNonBlank(req.getParameter("latencyTier"), extractJsonString(body, "latencyTier")),
                parseInteger(firstNonBlank(req.getParameter("latencyMs"), extractJsonNumber(body, "latencyMs")), 250),
                parseInteger(firstNonBlank(req.getParameter("accuracyScore"), extractJsonNumber(body, "accuracyScore")), 75),
                parseBoolean(firstNonBlank(req.getParameter("defaultSelected"), extractJsonBoolean(body, "defaultSelected")), false),
                parseBoolean(firstNonBlank(req.getParameter("enabled"), extractJsonBoolean(body, "enabled")), true),
                firstNonBlank(req.getParameter("status"), extractJsonString(body, "status")),
                firstNonBlank(req.getParameter("source"), extractJsonString(body, "source"))
        );
    }

    private AiTenantRegistrationRequest readTenantRequest(HttpServletRequest req, String body) {
        return new AiTenantRegistrationRequest(
                firstNonBlank(req.getParameter("tenantId"), extractJsonString(body, "tenantId")),
                firstNonBlank(req.getParameter("displayName"), extractJsonString(body, "displayName")),
                firstNonBlank(req.getParameter("plan"), extractJsonString(body, "plan")),
                parseInteger(firstNonBlank(req.getParameter("rateLimitPerMinute"), extractJsonNumber(body, "rateLimitPerMinute")), 0),
                parseLong(firstNonBlank(req.getParameter("tokenQuota"), extractJsonNumber(body, "tokenQuota")), 0L),
                parseBoolean(firstNonBlank(req.getParameter("active"), extractJsonBoolean(body, "active")), true)
        );
    }

    private static void prepare(HttpServletResponse resp) {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
    }

    private static void unavailable(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        resp.getWriter().write(errorJson(message));
    }

    private static void badRequest(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        resp.getWriter().write(errorJson(message));
    }

    private static void notFound(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        resp.getWriter().write(errorJson(message));
    }

    private static String normalizePath(String pathInfo) {
        return (pathInfo == null || pathInfo.isBlank()) ? "/" : pathInfo;
    }

    private static String firstNonBlank(String primary, String secondary) {
        return primary != null && !primary.isBlank() ? primary : (secondary == null ? "" : secondary);
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private static int parseInteger(String value, int fallback) {
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
    }

    private static long parseLong(String value, long fallback) {
        return value == null || value.isBlank() ? fallback : Long.parseLong(value);
    }

    private static String extractJsonString(String body, String field) {
        Matcher matcher = JSON_STRING_FIELD.matcher(body == null ? "" : body);
        while (matcher.find()) {
            if (field.equals(matcher.group(1))) {
                return unescape(matcher.group(2));
            }
        }
        return "";
    }

    private static String extractJsonBoolean(String body, String field) {
        Matcher matcher = JSON_BOOLEAN_FIELD.matcher(body == null ? "" : body);
        while (matcher.find()) {
            if (field.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return "";
    }

    private static String extractJsonNumber(String body, String field) {
        Matcher matcher = JSON_NUMBER_FIELD.matcher(body == null ? "" : body);
        while (matcher.find()) {
            if (field.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return "";
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
                .replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private String buildConfigJson() {
        ServerConfiguration.AiPlatform ai = configuration.getServer().getAiPlatform();
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{");
        sb.append("\"enabled\":").append(ai.isEnabled()).append(",");
        sb.append("\"mode\":\"").append(AiGatewayServlet.escapeJson(ai.getMode())).append("\",");
        sb.append("\"contextPath\":\"").append(AiGatewayServlet.escapeJson(ai.getConsole().getContextPath())).append("\",");
        // serving
        sb.append("\"serving\":{");
        sb.append("\"modelRouterEnabled\":").append(ai.getServing().isModelRouterEnabled()).append(",");
        sb.append("\"abTestingEnabled\":").append(ai.getServing().isAbTestingEnabled()).append(",");
        sb.append("\"autoModelSelectionEnabled\":").append(ai.getServing().isAutoModelSelectionEnabled()).append(",");
        sb.append("\"ensembleServingEnabled\":").append(ai.getServing().isEnsembleServingEnabled()).append(",");
        sb.append("\"edgeAiEnabled\":").append(ai.getServing().isEdgeAiEnabled()).append(",");
        sb.append("\"defaultStrategy\":\"").append(AiGatewayServlet.escapeJson(ai.getServing().getDefaultStrategy())).append("\",");
        sb.append("\"targetP99LatencyMs\":").append(ai.getServing().getTargetP99LatencyMs()).append(",");
        sb.append("\"routerTimeoutMillis\":").append(ai.getServing().getRouterTimeoutMillis()).append(",");
        sb.append("\"modelCount\":").append(ai.getServing().getModels().size()).append(",");
        sb.append("\"routePolicyCount\":").append(ai.getServing().getRoutePolicies().size());
        sb.append("},");
        // platform
        sb.append("\"platform\":{");
        sb.append("\"modelRegistrationEnabled\":").append(ai.getPlatform().isModelRegistrationEnabled()).append(",");
        sb.append("\"autoApiGenerationEnabled\":").append(ai.getPlatform().isAutoApiGenerationEnabled()).append(",");
        sb.append("\"versionManagementEnabled\":").append(ai.getPlatform().isVersionManagementEnabled()).append(",");
        sb.append("\"billingEnabled\":").append(ai.getPlatform().isBillingEnabled()).append(",");
        sb.append("\"developerPortalEnabled\":").append(ai.getPlatform().isDeveloperPortalEnabled()).append(",");
        sb.append("\"multiTenantEnabled\":").append(ai.getPlatform().isMultiTenantEnabled());
        sb.append("},");
        // advanced
        sb.append("\"advanced\":{");
        sb.append("\"promptRoutingEnabled\":").append(ai.getAdvanced().isPromptRoutingEnabled()).append(",");
        sb.append("\"contextCacheEnabled\":").append(ai.getAdvanced().isContextCacheEnabled()).append(",");
        sb.append("\"contextCacheTtlSeconds\":").append(ai.getAdvanced().getContextCacheTtlSeconds()).append(",");
        sb.append("\"aiGatewayEnabled\":").append(ai.getAdvanced().isAiGatewayEnabled()).append(",");
        sb.append("\"observabilityEnabled\":").append(ai.getAdvanced().isObservabilityEnabled());
        sb.append("},");
        // differentiation
        sb.append("\"differentiation\":{");
        sb.append("\"aiOptimizedWasEnabled\":").append(ai.getDifferentiation().isAiOptimizedWasEnabled()).append(",");
        sb.append("\"requestRoutingEnabled\":").append(ai.getDifferentiation().isRequestRoutingEnabled()).append(",");
        sb.append("\"streamingResponseEnabled\":").append(ai.getDifferentiation().isStreamingResponseEnabled()).append(",");
        sb.append("\"pluginFrameworkEnabled\":").append(ai.getDifferentiation().isPluginFrameworkEnabled()).append(",");
        sb.append("\"runtimeEngine\":\"").append(AiGatewayServlet.escapeJson(ai.getDifferentiation().getRuntimeEngine())).append("\"");
        sb.append("},");
        // roadmap
        sb.append("\"roadmapStage\":").append(ai.getRoadmap().getCurrentStage());
        sb.append("}");
        return sb.toString();
    }

    // ── Intent routing JSON builders ──

    private String buildIntentKeywordsJson() {
        var keywords = intentPolicyService.listKeywords();
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{\"totalKeywords\":").append(keywords.size()).append(",\"keywords\":[");
        boolean first = true;
        for (IntentKeyword kw : keywords) {
            if (!first) sb.append(",");
            first = false;
            sb.append(buildKeywordJson(kw));
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String buildKeywordJson(IntentKeyword kw) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"keywordId\":\"").append(esc(kw.keywordId())).append("\"");
        sb.append(",\"primaryKeyword\":\"").append(esc(kw.primaryKeyword())).append("\"");
        sb.append(",\"synonyms\":[");
        boolean first = true;
        for (String s : kw.synonyms()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(esc(s)).append("\"");
        }
        sb.append("],\"intent\":\"").append(kw.intent().name()).append("\"");
        sb.append(",\"intentLabel\":\"").append(esc(kw.intent().label())).append("\"");
        sb.append(",\"priority\":").append(kw.priority());
        sb.append(",\"enabled\":").append(kw.enabled());
        sb.append("}");
        return sb.toString();
    }

    private String buildIntentPoliciesJson() {
        var policies = intentPolicyService.listPolicies();
        StringBuilder sb = new StringBuilder(2048);
        sb.append("{\"totalPolicies\":").append(policies.size()).append(",\"policies\":[");
        boolean first = true;
        for (RoutingPolicy pol : policies) {
            if (!first) sb.append(",");
            first = false;
            sb.append(buildPolicyJson(pol));
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String buildPolicyJson(RoutingPolicy pol) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"policyId\":\"").append(esc(pol.policyId())).append("\"");
        sb.append(",\"intent\":\"").append(pol.intent().name()).append("\"");
        sb.append(",\"intentLabel\":\"").append(esc(pol.intent().label())).append("\"");
        sb.append(",\"priority\":").append(pol.priority());
        sb.append(",\"routeTarget\":\"").append(esc(pol.routeTarget())).append("\"");
        sb.append(",\"modelName\":\"").append(esc(pol.modelName())).append("\"");
        sb.append(",\"fallbackModel\":\"").append(esc(pol.fallbackModel())).append("\"");
        sb.append(",\"streamingPreferred\":").append(pol.streamingPreferred());
        sb.append(",\"enabled\":").append(pol.enabled());
        sb.append(",\"tenantOverride\":\"").append(esc(pol.tenantOverride() != null ? pol.tenantOverride() : "")).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private static String buildIntentDecisionJson(IntentRouteDecision d) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"resolvedIntent\":\"").append(d.resolvedIntent().name()).append("\"");
        sb.append(",\"intentLabel\":\"").append(esc(d.resolvedIntent().label())).append("\"");
        sb.append(",\"matchedKeyword\":").append(d.matchedKeyword() != null ? "\"" + esc(d.matchedKeyword()) + "\"" : "null");
        sb.append(",\"policyId\":\"").append(esc(d.policyId())).append("\"");
        sb.append(",\"routeTarget\":\"").append(esc(d.routeTarget())).append("\"");
        sb.append(",\"modelName\":\"").append(esc(d.modelName())).append("\"");
        sb.append(",\"fallbackModel\":\"").append(esc(d.fallbackModel())).append("\"");
        sb.append(",\"streamingPreferred\":").append(d.streamingPreferred());
        sb.append(",\"priority\":").append(d.priority());
        sb.append(",\"reasoning\":\"").append(esc(d.reasoning())).append("\"");
        sb.append(",\"candidateKeywords\":[");
        boolean first = true;
        for (String k : d.candidateKeywords()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(esc(k)).append("\"");
        }
        sb.append("],\"processingTimeMicros\":").append(d.processingTimeNanos() / 1000);
        sb.append("}");
        return sb.toString();
    }

    private static String buildPreviewJson(RouteDecisionEngine.PreviewResult p) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"normalizedText\":\"").append(esc(p.normalizedText())).append("\"");
        sb.append(",\"estimatedTokens\":").append(p.estimatedTokens());
        sb.append(",\"matchedKeywords\":[");
        boolean first = true;
        for (KeywordMatcher.MatchResult mr : p.matchResults()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"matchedText\":\"").append(esc(mr.matchedText())).append("\"");
            sb.append(",\"intent\":\"").append(mr.keyword().intent().name()).append("\"");
            sb.append(",\"priority\":").append(mr.keyword().priority()).append("}");
        }
        sb.append("],\"resolvedIntent\":\"").append(p.resolvedIntent().intent().name()).append("\"");
        sb.append(",\"intentLabel\":\"").append(esc(p.resolvedIntent().intent().label())).append("\"");
        sb.append(",\"reasoning\":\"").append(esc(p.resolvedIntent().reasoning())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private String buildAuditJson(int limit) {
        var entries = auditLogger.recentEntries(limit);
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\"totalEntries\":").append(entries.size()).append(",\"entries\":[");
        boolean first = true;
        for (RouteAuditEntry e : entries) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"requestId\":\"").append(esc(e.requestId())).append("\"");
            sb.append(",\"tenantId\":\"").append(esc(e.tenantId())).append("\"");
            sb.append(",\"prompt\":\"").append(esc(e.prompt())).append("\"");
            sb.append(",\"intent\":\"").append(e.resolvedIntent().name()).append("\"");
            sb.append(",\"matchedKeyword\":").append(e.matchedKeyword() != null ? "\"" + esc(e.matchedKeyword()) + "\"" : "null");
            sb.append(",\"policyId\":\"").append(esc(e.policyId())).append("\"");
            sb.append(",\"routeTarget\":\"").append(esc(e.routeTarget())).append("\"");
            sb.append(",\"modelName\":\"").append(esc(e.modelName())).append("\"");
            sb.append(",\"usedFallback\":").append(e.usedFallback());
            sb.append(",\"processingTimeMicros\":").append(e.processingTimeNanos() / 1000);
            sb.append(",\"timestamp\":").append(e.timestamp());
            sb.append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildIntentStatsJson() {
        RouteAuditLogger.IntentStats stats = auditLogger.stats();
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"totalRoutes\":").append(stats.totalRoutes());
        sb.append(",\"fallbackRoutes\":").append(stats.fallbackRoutes());
        sb.append(",\"avgProcessingMicros\":").append(String.format("%.1f", stats.avgProcessingMicros()));
        sb.append(",\"auditLogSize\":").append(stats.auditLogSize());
        sb.append(",\"keywordCount\":").append(intentPolicyService.keywordCount());
        sb.append(",\"policyCount\":").append(intentPolicyService.policyCount());
        sb.append(",\"intentDistribution\":{");
        boolean first = true;
        for (var entry : stats.intentDistribution().entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey().name()).append("\":").append(entry.getValue());
        }
        sb.append("}}");
        return sb.toString();
    }

    private static String esc(String s) {
        return AiGatewayServlet.escapeJson(s != null ? s : "");
    }

    private static String errorJson(String message) {
        return "{\"error\":\"" + AiGatewayServlet.escapeJson(message) + "\"}";
    }
}