package io.velo.was.aiplatform.api;

import io.velo.was.aiplatform.billing.AiBillingService;
import io.velo.was.aiplatform.billing.AiBillingSnapshot;
import io.velo.was.aiplatform.finetuning.AiFineTuningJobRequest;
import io.velo.was.aiplatform.finetuning.AiFineTuningService;
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
    private final AiFineTuningService fineTuningService;
    private final AiTenantService tenantService;

    public AiPlatformApiServlet(ServerConfiguration configuration,
                                AiModelRegistryService registryService,
                                AiGatewayService gatewayService,
                                AiPlatformUsageService usageService,
                                AiPublishedApiService publishedApiService,
                                AiBillingService billingService,
                                AiFineTuningService fineTuningService,
                                AiTenantService tenantService) {
        this.configuration = configuration;
        this.registryService = registryService;
        this.gatewayService = gatewayService;
        this.usageService = usageService;
        this.publishedApiService = publishedApiService;
        this.billingService = billingService;
        this.fineTuningService = fineTuningService;
        this.tenantService = tenantService;
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
        if ("/fine-tuning/jobs".equals(path)) {
            if (!configuration.getServer().getAiPlatform().getAdvanced().isFineTuningApiEnabled()) {
                unavailable(resp, "Fine-tuning API is disabled in configuration");
                return;
            }
            usageService.recordControlPlaneAccess("/api/fine-tuning/jobs");
            resp.getWriter().write(AiPlatformExtendedJson.fineTuningJobs(fineTuningService.listJobs()));
            return;
        }
        if (path.startsWith("/fine-tuning/jobs/")) {
            if (!configuration.getServer().getAiPlatform().getAdvanced().isFineTuningApiEnabled()) {
                unavailable(resp, "Fine-tuning API is disabled in configuration");
                return;
            }
            usageService.recordControlPlaneAccess("/api/fine-tuning/jobs/{id}");
            String jobId = path.substring("/fine-tuning/jobs/".length());
            resp.getWriter().write(AiPlatformExtendedJson.fineTuningJob(fineTuningService.getJob(jobId)));
            return;
        }
        notFound(resp, "Not Found");
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
            if ("/fine-tuning/jobs".equals(path)) {
                if (!configuration.getServer().getAiPlatform().getAdvanced().isFineTuningApiEnabled()) {
                    unavailable(resp, "Fine-tuning API is disabled in configuration");
                    return;
                }
                usageService.recordControlPlaneAccess("/api/fine-tuning/jobs");
                resp.setStatus(HttpServletResponse.SC_CREATED);
                resp.getWriter().write(AiPlatformExtendedJson.fineTuningJob(fineTuningService.createJob(readFineTuningRequest(req, body))));
                return;
            }
            if (path.startsWith("/fine-tuning/jobs/") && path.endsWith("/cancel")) {
                if (!configuration.getServer().getAiPlatform().getAdvanced().isFineTuningApiEnabled()) {
                    unavailable(resp, "Fine-tuning API is disabled in configuration");
                    return;
                }
                usageService.recordControlPlaneAccess("/api/fine-tuning/jobs/{id}/cancel");
                String jobId = path.substring("/fine-tuning/jobs/".length(), path.length() - "/cancel".length());
                resp.getWriter().write(AiPlatformExtendedJson.fineTuningJob(fineTuningService.cancelJob(jobId)));
                return;
            }
            notFound(resp, "Not Found");
        } catch (IllegalArgumentException e) {
            badRequest(resp, e.getMessage());
        } catch (NoSuchElementException e) {
            notFound(resp, e.getMessage());
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

    private AiFineTuningJobRequest readFineTuningRequest(HttpServletRequest req, String body) {
        return new AiFineTuningJobRequest(
                firstNonBlank(req.getParameter("baseModel"), extractJsonString(body, "baseModel")),
                firstNonBlank(req.getParameter("datasetUri"), extractJsonString(body, "datasetUri")),
                firstNonBlank(req.getParameter("tenant"), extractJsonString(body, "tenant")),
                firstNonBlank(req.getParameter("objective"), extractJsonString(body, "objective")),
                parseInteger(firstNonBlank(req.getParameter("epochs"), extractJsonNumber(body, "epochs")), 3)
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

    private static String errorJson(String message) {
        return "{\"error\":\"" + AiGatewayServlet.escapeJson(message) + "\"}";
    }
}