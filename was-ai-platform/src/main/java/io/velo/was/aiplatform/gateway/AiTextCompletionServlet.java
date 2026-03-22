package io.velo.was.aiplatform.gateway;

import io.velo.was.aiplatform.observability.AiPlatformUsageService;
import io.velo.was.aiplatform.tenant.AiTenantAccessGrant;
import io.velo.was.aiplatform.tenant.AiTenantService;
import io.velo.was.config.ServerConfiguration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * OpenAI-compatible /v1/completions text completion proxy.
 * Accepts a prompt string and returns a completion response
 * through the built-in AI gateway.
 */
public class AiTextCompletionServlet extends HttpServlet {

    private static final Pattern JSON_STRING_FIELD = Pattern.compile(
            "\"([A-Za-z0-9_]+)\"\\s*:\\s*\"((?:\\\\.|[^\\\"])*)\"", Pattern.DOTALL);

    private final AiGatewayService gatewayService;
    private final AiPlatformUsageService usageService;
    private final AiTenantService tenantService;

    public AiTextCompletionServlet(ServerConfiguration configuration,
                                   AiGatewayService gatewayService,
                                   AiPlatformUsageService usageService,
                                   AiTenantService tenantService) {
        this.gatewayService = gatewayService;
        this.usageService = usageService;
        this.tenantService = tenantService;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        AiTenantAccessGrant tenantAccess = authorize(req, resp);
        if (tenantAccess == null) {
            return;
        }

        String body = req.getReader().lines().collect(Collectors.joining("\n"));
        String model = extractJsonString(body, "model");
        String prompt = extractJsonString(body, "prompt");

        if (prompt.isBlank()) {
            returnJson(resp);
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":{\"message\":\"prompt is required\",\"type\":\"invalid_request_error\"}}");
            resp.getWriter().flush();
            return;
        }

        // 의도 기반 라우팅 헤더 추가
        try {
            io.velo.was.aiplatform.intent.IntentRouteDecision intentDecision =
                    gatewayService.intentRoute(prompt, tenantAccess.tenantId());
            resp.setHeader("X-Intent", intentDecision.resolvedIntent().name());
            resp.setHeader("X-Intent-Model", intentDecision.modelName());
            resp.setHeader("X-Intent-Policy", intentDecision.policyId());
        } catch (Exception ignored) {
        }

        AiGatewayRequest gatewayRequest = new AiGatewayRequest(
                "CHAT", prompt,
                "completions-" + UUID.randomUUID().toString().substring(0, 8), false);

        try {
            AiGatewayInferenceResult result = gatewayService.infer(gatewayRequest);
            usageService.recordInference(result, false);
            tenantService.recordUsage(tenantAccess, result.estimatedTokens());
            returnJson(resp);
            applyTenantHeaders(resp, tenantAccess, result.estimatedTokens());
            resp.getWriter().write(toCompletionResponse(result));
        } catch (Exception e) {
            // Failover
            try {
                AiGatewayRequest fallback = new AiGatewayRequest("CHAT", prompt,
                        "completions-failover-" + UUID.randomUUID().toString().substring(0, 8), false);
                AiGatewayInferenceResult result = gatewayService.infer(fallback);
                usageService.recordInference(result, false);
                tenantService.recordUsage(tenantAccess, result.estimatedTokens());
                returnJson(resp);
                applyTenantHeaders(resp, tenantAccess, result.estimatedTokens());
                resp.getWriter().write(toCompletionResponse(result));
            } catch (Exception fallbackErr) {
                returnJson(resp);
                resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                resp.getWriter().write("{\"error\":{\"message\":\"" + AiGatewayServlet.escapeJson(e.getMessage())
                        + "\",\"type\":\"server_error\"}}");
            }
        }
        resp.getWriter().flush();
    }

    private static String toCompletionResponse(AiGatewayInferenceResult result) {
        String cmplId = "cmpl-" + UUID.randomUUID().toString().substring(0, 12);
        long created = System.currentTimeMillis() / 1000L;
        int promptTokens = Math.max(8, result.estimatedTokens() / 3);
        int completionTokens = result.estimatedTokens() - promptTokens;
        return "{" +
                "\"id\":\"" + cmplId + "\"," +
                "\"object\":\"text_completion\"," +
                "\"created\":" + created + "," +
                "\"model\":\"" + AiGatewayServlet.escapeJson(result.decision().modelName()) + "\"," +
                "\"choices\":[{" +
                "\"index\":0," +
                "\"text\":\"" + AiGatewayServlet.escapeJson(result.outputText()) + "\"," +
                "\"finish_reason\":\"stop\"" +
                "}]," +
                "\"usage\":{" +
                "\"prompt_tokens\":" + promptTokens + "," +
                "\"completion_tokens\":" + completionTokens + "," +
                "\"total_tokens\":" + result.estimatedTokens() +
                "}}";
    }

    private AiTenantAccessGrant authorize(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            return tenantService.authorize(resolveApiKey(req));
        } catch (SecurityException e) {
            returnJson(resp);
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":{\"message\":\"" + AiGatewayServlet.escapeJson(e.getMessage()) + "\",\"type\":\"authentication_error\"}}");
            resp.getWriter().flush();
            return null;
        } catch (IllegalStateException e) {
            returnJson(resp);
            int status = e.getMessage() != null && e.getMessage().contains("Rate limit") ? 429 : HttpServletResponse.SC_FORBIDDEN;
            resp.setStatus(status);
            resp.getWriter().write("{\"error\":{\"message\":\"" + AiGatewayServlet.escapeJson(e.getMessage()) + "\",\"type\":\"rate_limit_error\"}}");
            resp.getWriter().flush();
            return null;
        }
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

    private void applyTenantHeaders(HttpServletResponse resp, AiTenantAccessGrant tenantAccess, int estimatedTokens) {
        if (tenantAccess == null || !tenantAccess.tracked()) {
            return;
        }
        resp.setHeader("X-AI-Tenant", tenantAccess.tenantId());
        resp.setHeader("X-RateLimit-Limit", String.valueOf(tenantAccess.rateLimitPerMinute()));
        resp.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, tenantAccess.remainingWindowRequests())));
    }

    private static void returnJson(HttpServletResponse resp) {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
    }

    private static String extractJsonString(String body, String field) {
        Matcher matcher = JSON_STRING_FIELD.matcher(body == null ? "" : body);
        while (matcher.find()) {
            if (field.equals(matcher.group(1))) {
                return matcher.group(2).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
            }
        }
        return "";
    }
}
