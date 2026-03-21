package io.velo.was.aiplatform.gateway;

import io.velo.was.aiplatform.observability.AiPlatformUsageService;
import io.velo.was.aiplatform.tenant.AiTenantAccessGrant;
import io.velo.was.aiplatform.tenant.AiTenantService;
import io.velo.was.config.ServerConfiguration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AiGatewayServlet extends HttpServlet {

    private static final Pattern JSON_STRING_FIELD = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"((?:\\\\.|[^\\\"])*)\"", Pattern.DOTALL);
    private static final Pattern JSON_BOOLEAN_FIELD = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);

    private final ServerConfiguration configuration;
    private final AiGatewayService gatewayService;
    private final AiPlatformUsageService usageService;
    private final AiTenantService tenantService;

    public AiGatewayServlet(ServerConfiguration configuration,
                            AiGatewayService gatewayService,
                            AiPlatformUsageService usageService,
                            AiTenantService tenantService) {
        this.configuration = configuration;
        this.gatewayService = gatewayService;
        this.usageService = usageService;
        this.tenantService = tenantService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp);
    }

    private void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        AiTenantAccessGrant tenantAccess = authorize(req, resp);
        if (tenantAccess == null) {
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            writeDiscovery(resp, req.getContextPath(), tenantAccess);
            tenantService.recordUsage(tenantAccess, 0);
            return;
        }

        returnJson(resp);
        AiGatewayRequest gatewayRequest = readGatewayRequest(req);
        switch (pathInfo) {
            case "/route" -> {
                AiGatewayRouteDecision decision = gatewayService.route(gatewayRequest);
                usageService.recordRoute(decision);
                tenantService.recordUsage(tenantAccess, 0);
                applyTenantHeaders(resp, tenantAccess, 0);
                resp.getWriter().write(routeDecisionToJson(decision));
            }
            case "/infer" -> {
                AiGatewayInferenceResult result = gatewayService.infer(gatewayRequest);
                usageService.recordInference(result, false);
                tenantService.recordUsage(tenantAccess, result.estimatedTokens());
                applyTenantHeaders(resp, tenantAccess, result.estimatedTokens());
                resp.getWriter().write(inferenceResultToJson(result));
            }
            case "/stream" -> streamInference(resp, gatewayRequest, tenantAccess);
            default -> {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                applyTenantHeaders(resp, tenantAccess, 0);
                resp.getWriter().write("{\"error\":\"Unknown gateway path\"}");
            }
        }
    }

    private AiTenantAccessGrant authorize(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            return tenantService.authorize(resolveApiKey(req));
        } catch (SecurityException e) {
            return tenantError(resp, HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        } catch (IllegalStateException e) {
            int status = e.getMessage() != null && e.getMessage().contains("Rate limit")
                    ? 429 /* Too Many Requests */
                    : HttpServletResponse.SC_FORBIDDEN;
            return tenantError(resp, status, e.getMessage());
        }
    }

    private AiTenantAccessGrant tenantError(HttpServletResponse resp, int status, String message) throws IOException {
        returnJson(resp);
        resp.setStatus(status);
        resp.getWriter().write("{\"error\":\"" + escapeJson(message) + "\"}");
        return null;
    }

    private void writeDiscovery(HttpServletResponse resp, String contextPath, AiTenantAccessGrant tenantAccess) throws IOException {
        returnJson(resp);
        applyTenantHeaders(resp, tenantAccess, 0);
        resp.getWriter().write("""
                {
                  "service": "velo-ai-gateway",
                  "contextPath": "%s",
                  "tenantEnforced": %s,
                  "apiKeyHeader": "%s",
                  "endpoints": [
                    "%s/gateway/route",
                    "%s/gateway/infer",
                    "%s/gateway/stream"
                  ]
                }
                """.formatted(
                contextPath,
                tenantAccess.tracked(),
                tenantService.apiKeyHeader(),
                contextPath,
                contextPath,
                contextPath
        ).trim());
    }

    private void streamInference(HttpServletResponse resp, AiGatewayRequest gatewayRequest, AiTenantAccessGrant tenantAccess) throws IOException {
        if (!configuration.getServer().getAiPlatform().getDifferentiation().isStreamingResponseEnabled()) {
            returnJson(resp);
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            applyTenantHeaders(resp, tenantAccess, 0);
            resp.getWriter().write("{\"error\":\"Streaming response is disabled in configuration\"}");
            return;
        }

        AiGatewayInferenceResult result = gatewayService.infer(gatewayRequest);
        usageService.recordInference(result, true);
        tenantService.recordUsage(tenantAccess, result.estimatedTokens());
        resp.setContentType("text/event-stream; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        applyTenantHeaders(resp, tenantAccess, result.estimatedTokens());

        PrintWriter out = resp.getWriter();
        out.write("event: meta\n");
        out.write("data: " + routeDecisionToJson(result.decision()) + "\n\n");

        String[] tokens = result.outputText().split(" ");
        for (int i = 0; i < tokens.length; i++) {
            out.write("event: token\n");
            out.write("data: {\"index\":" + i + ",\"token\":\"" + escapeJson(tokens[i]) + "\"}\n\n");
        }

        out.write("event: done\n");
        out.write("data: " + inferenceResultToJson(result) + "\n\n");
        out.flush();
    }

    private void applyTenantHeaders(HttpServletResponse resp, AiTenantAccessGrant tenantAccess, int estimatedTokens) {
        if (tenantAccess == null || !tenantAccess.tracked()) {
            return;
        }
        resp.setHeader("X-AI-Tenant", tenantAccess.tenantId());
        resp.setHeader("X-AI-Plan", tenantAccess.plan());
        resp.setHeader("X-RateLimit-Limit", String.valueOf(tenantAccess.rateLimitPerMinute()));
        resp.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, tenantAccess.remainingWindowRequests())));
        resp.setHeader("X-Token-Quota-Remaining", String.valueOf(Math.max(0L, tenantAccess.remainingTokensBeforeRequest() - Math.max(0, estimatedTokens))));
    }

    private String resolveApiKey(HttpServletRequest req) {
        String headerValue = req.getHeader(tenantService.apiKeyHeader());
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue;
        }
        String bearer = req.getHeader("Authorization");
        if (bearer != null && bearer.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return bearer.substring(7).trim();
        }
        return req.getParameter("apiKey");
    }

    private static void returnJson(HttpServletResponse resp) {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
    }

    private AiGatewayRequest readGatewayRequest(HttpServletRequest req) throws IOException {
        String body = req.getReader().lines().collect(Collectors.joining("\n"));
        String requestType = firstNonBlank(req.getParameter("requestType"), extractJsonString(body, "requestType"));
        String prompt = firstNonBlank(req.getParameter("prompt"), extractJsonString(body, "prompt"));
        String sessionId = firstNonBlank(req.getParameter("sessionId"), extractJsonString(body, "sessionId"));
        boolean stream = parseBoolean(req.getParameter("stream"), extractJsonBoolean(body, "stream"));
        return new AiGatewayRequest(requestType, prompt, sessionId, stream);
    }

    private static String firstNonBlank(String primary, String secondary) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return secondary == null ? "" : secondary;
    }

    private static boolean parseBoolean(String primary, Boolean secondary) {
        if (primary != null && !primary.isBlank()) {
            return Boolean.parseBoolean(primary);
        }
        return secondary != null && secondary;
    }

    private static String extractJsonString(String body, String field) {
        Matcher matcher = JSON_STRING_FIELD.matcher(body == null ? "" : body);
        while (matcher.find()) {
            if (field.equals(matcher.group(1))) {
                return unescapeJsonString(matcher.group(2));
            }
        }
        return "";
    }

    private static Boolean extractJsonBoolean(String body, String field) {
        Matcher matcher = JSON_BOOLEAN_FIELD.matcher(body == null ? "" : body);
        while (matcher.find()) {
            if (field.equals(matcher.group(1))) {
                return Boolean.parseBoolean(matcher.group(2));
            }
        }
        return null;
    }

    private static String unescapeJsonString(String value) {
        return value.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public static String routeDecisionToJson(AiGatewayRouteDecision decision) {
        return "{" +
                "\"requestedType\":\"" + escapeJson(decision.requestedType()) + "\"," +
                "\"resolvedType\":\"" + escapeJson(decision.resolvedType()) + "\"," +
                "\"model\":{" +
                "\"name\":\"" + escapeJson(decision.modelName()) + "\"," +
                "\"category\":\"" + escapeJson(decision.modelCategory()) + "\"," +
                "\"provider\":\"" + escapeJson(decision.provider()) + "\"," +
                "\"version\":\"" + escapeJson(decision.version()) + "\"," +
                "\"expectedLatencyMs\":" + decision.expectedLatencyMs() + "," +
                "\"accuracyScore\":" + decision.accuracyScore() +
                "}," +
                "\"routePolicy\":\"" + escapeJson(decision.routePolicy()) + "\"," +
                "\"strategyApplied\":\"" + escapeJson(decision.strategyApplied()) + "\"," +
                "\"cache\":{" +
                "\"enabled\":" + decision.contextCacheEnabled() + "," +
                "\"hit\":" + decision.cacheHit() + "," +
                "\"key\":\"" + escapeJson(decision.cacheKey()) + "\"}," +
                "\"gateway\":{" +
                "\"streamingSupported\":" + decision.streamingSupported() + "," +
                "\"promptRouted\":" + decision.promptRouted() + "}," +
                "\"observability\":{" +
                "\"totalRequests\":" + decision.totalRequests() + "," +
                "\"modelRequestCount\":" + decision.modelRequestCount() + "}," +
                "\"reasoning\":\"" + escapeJson(decision.reasoning()) + "\"}";
    }

    public static String inferenceResultToJson(AiGatewayInferenceResult result) {
        return "{" +
                "\"decision\":" + routeDecisionToJson(result.decision()) + "," +
                "\"response\":{" +
                "\"outputText\":\"" + escapeJson(result.outputText()) + "\"," +
                "\"estimatedTokens\":" + result.estimatedTokens() + "," +
                "\"confidence\":" + formatDouble(result.confidence()) +
                "}}";
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    public static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static String buildStreamUrl(String contextPath, String requestType, String sessionId, String prompt) {
        return contextPath + "/gateway/stream?requestType=" + encode(requestType)
                + "&sessionId=" + encode(sessionId)
                + "&prompt=" + encode(prompt);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}