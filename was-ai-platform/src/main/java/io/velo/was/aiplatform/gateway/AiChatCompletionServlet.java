package io.velo.was.aiplatform.gateway;

import io.velo.was.aiplatform.audit.AiGatewayAuditLog;
import io.velo.was.aiplatform.observability.AiPlatformUsageService;
import io.velo.was.aiplatform.tenant.AiTenantAccessGrant;
import io.velo.was.aiplatform.tenant.AiTenantService;
import io.velo.was.config.ServerConfiguration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * OpenAI-compatible /v1/chat/completions proxy that routes requests
 * through the built-in AI gateway with automatic failover support.
 */
public class AiChatCompletionServlet extends HttpServlet {

    private static final Pattern JSON_STRING_FIELD = Pattern.compile(
            "\"([A-Za-z0-9_]+)\"\\s*:\\s*\"((?:\\\\.|[^\\\"])*)\"", Pattern.DOTALL);
    private static final Pattern JSON_BOOLEAN_FIELD = Pattern.compile(
            "\"([A-Za-z0-9_]+)\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_NUMBER_FIELD = Pattern.compile(
            "\"([A-Za-z0-9_]+)\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MESSAGES_CONTENT = Pattern.compile(
            "\"content\"\\s*:\\s*\"((?:\\\\.|[^\\\"])*)\"", Pattern.DOTALL);

    private final ServerConfiguration configuration;
    private final AiGatewayService gatewayService;
    private final AiPlatformUsageService usageService;
    private final AiTenantService tenantService;
    private final AiGatewayAuditLog auditLog;

    public AiChatCompletionServlet(ServerConfiguration configuration,
                                   AiGatewayService gatewayService,
                                   AiPlatformUsageService usageService,
                                   AiTenantService tenantService) {
        this(configuration, gatewayService, usageService, tenantService, null);
    }

    public AiChatCompletionServlet(ServerConfiguration configuration,
                                   AiGatewayService gatewayService,
                                   AiPlatformUsageService usageService,
                                   AiTenantService tenantService,
                                   AiGatewayAuditLog auditLog) {
        this.configuration = configuration;
        this.gatewayService = gatewayService;
        this.usageService = usageService;
        this.tenantService = tenantService;
        this.auditLog = auditLog;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        AiTenantAccessGrant tenantAccess = authorize(req, resp);
        if (tenantAccess == null) {
            return;
        }

        long auditStart = System.nanoTime();
        String remoteAddr = req.getRemoteAddr();
        String body = req.getReader().lines().collect(Collectors.joining("\n"));
        String model = extractJsonString(body, "model");
        boolean stream = "true".equalsIgnoreCase(extractJsonString(body, "stream"))
                || extractJsonBoolean(body, "stream");
        String prompt = extractMessagesContent(body);

        if (prompt.isBlank()) {
            returnJson(resp);
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":{\"message\":\"messages array is required\",\"type\":\"invalid_request_error\"}}");
            resp.getWriter().flush();
            return;
        }

        // 의도 기반 라우팅: 모델 미지정 시 의도 분석으로 최적 모델 결정
        String resolvedModel = model;
        String intentName = null;
        try {
            io.velo.was.aiplatform.intent.IntentRouteDecision intentDecision =
                    gatewayService.intentRoute(prompt, tenantAccess.tenantId());
            resp.setHeader("X-Intent", intentDecision.resolvedIntent().name());
            resp.setHeader("X-Intent-Model", intentDecision.modelName());
            resp.setHeader("X-Intent-Policy", intentDecision.policyId());
            intentName = intentDecision.resolvedIntent().name();
            if (model.isBlank()) {
                resolvedModel = intentDecision.modelName();
            }
        } catch (Exception ignored) {
        }

        String requestType = resolvedModel.isBlank() ? "AUTO" : "CHAT";
        AiGatewayRequest gatewayRequest = new AiGatewayRequest(requestType, prompt, "openai-compat-" + UUID.randomUUID().toString().substring(0, 8), stream);

        try {
            AiGatewayInferenceResult result = gatewayService.infer(gatewayRequest);
            usageService.recordInference(result, stream);
            tenantService.recordUsage(tenantAccess, result.estimatedTokens());

            if (stream) {
                streamResponse(resp, result, tenantAccess);
            } else {
                returnJson(resp);
                applyTenantHeaders(resp, tenantAccess, result.estimatedTokens());
                resp.getWriter().write(toOpenAiResponse(result));
            }
            auditChatSuccess(tenantAccess, result, prompt, stream, auditStart, remoteAddr, intentName);
        } catch (IllegalStateException e) {
            // Failover: if first model fails, try with fallback
            auditChatFailure(tenantAccess, prompt, stream, auditStart, e.getMessage(), remoteAddr, intentName);
            try {
                long failoverStart = System.nanoTime();
                AiGatewayRequest fallbackRequest = new AiGatewayRequest("CHAT", prompt,
                        "openai-compat-failover-" + UUID.randomUUID().toString().substring(0, 8), stream);
                AiGatewayInferenceResult result = gatewayService.infer(fallbackRequest);
                usageService.recordInference(result, stream);
                tenantService.recordUsage(tenantAccess, result.estimatedTokens());
                returnJson(resp);
                applyTenantHeaders(resp, tenantAccess, result.estimatedTokens());
                resp.getWriter().write(toOpenAiResponse(result));
                auditChatSuccess(tenantAccess, result, prompt, stream, failoverStart, remoteAddr, intentName);
            } catch (Exception fallbackError) {
                returnJson(resp);
                resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                resp.getWriter().write("{\"error\":{\"message\":\"" + AiGatewayServlet.escapeJson(e.getMessage())
                        + "\",\"type\":\"server_error\"}}");
                auditChatFailure(tenantAccess, prompt, stream, auditStart, "failover: " + fallbackError.getMessage(), remoteAddr, intentName);
            }
        }
        resp.getWriter().flush();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        returnJson(resp);
        resp.getWriter().write("{\"object\":\"list\",\"data\":[" +
                "{\"id\":\"velo-ai-gateway\",\"object\":\"model\",\"owned_by\":\"velo\"}" +
                "]}");
        resp.getWriter().flush();
    }

    private void streamResponse(HttpServletResponse resp, AiGatewayInferenceResult result,
                                AiTenantAccessGrant tenantAccess) throws IOException {
        resp.setContentType("text/event-stream; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        applyTenantHeaders(resp, tenantAccess, result.estimatedTokens());

        String chatId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 12);
        long created = System.currentTimeMillis() / 1000L;
        PrintWriter out = resp.getWriter();

        String[] tokens = result.outputText().split(" ");
        for (int i = 0; i < tokens.length; i++) {
            out.write("data: {\"id\":\"" + chatId + "\",\"object\":\"chat.completion.chunk\","
                    + "\"created\":" + created + ",\"model\":\"" + AiGatewayServlet.escapeJson(result.decision().modelName()) + "\","
                    + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"" + AiGatewayServlet.escapeJson(tokens[i] + (i < tokens.length - 1 ? " " : "")) + "\"},"
                    + "\"finish_reason\":null}]}\n\n");
        }

        out.write("data: {\"id\":\"" + chatId + "\",\"object\":\"chat.completion.chunk\","
                + "\"created\":" + created + ",\"model\":\"" + AiGatewayServlet.escapeJson(result.decision().modelName()) + "\","
                + "\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n");
        out.write("data: [DONE]\n\n");
        out.flush();
    }

    private static String toOpenAiResponse(AiGatewayInferenceResult result) {
        String chatId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 12);
        long created = System.currentTimeMillis() / 1000L;
        int promptTokens = Math.max(8, result.estimatedTokens() / 3);
        int completionTokens = result.estimatedTokens() - promptTokens;
        return "{" +
                "\"id\":\"" + chatId + "\"," +
                "\"object\":\"chat.completion\"," +
                "\"created\":" + created + "," +
                "\"model\":\"" + AiGatewayServlet.escapeJson(result.decision().modelName()) + "\"," +
                "\"choices\":[{" +
                "\"index\":0," +
                "\"message\":{\"role\":\"assistant\",\"content\":\"" + AiGatewayServlet.escapeJson(result.outputText()) + "\"}," +
                "\"finish_reason\":\"stop\"" +
                "}]," +
                "\"usage\":{" +
                "\"prompt_tokens\":" + promptTokens + "," +
                "\"completion_tokens\":" + completionTokens + "," +
                "\"total_tokens\":" + result.estimatedTokens() +
                "}," +
                "\"velo_routing\":{" +
                "\"resolvedType\":\"" + AiGatewayServlet.escapeJson(result.decision().resolvedType()) + "\"," +
                "\"routePolicy\":\"" + AiGatewayServlet.escapeJson(result.decision().routePolicy()) + "\"," +
                "\"strategy\":\"" + AiGatewayServlet.escapeJson(result.decision().strategyApplied()) + "\"," +
                "\"cacheHit\":" + result.decision().cacheHit() + "," +
                "\"confidence\":" + String.format(Locale.US, "%.2f", result.confidence()) +
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
        resp.setHeader("X-AI-Plan", tenantAccess.plan());
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

    private static boolean extractJsonBoolean(String body, String field) {
        Matcher matcher = JSON_BOOLEAN_FIELD.matcher(body == null ? "" : body);
        while (matcher.find()) {
            if (field.equals(matcher.group(1))) {
                return Boolean.parseBoolean(matcher.group(2));
            }
        }
        return false;
    }

    private static String extractMessagesContent(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        StringBuilder combined = new StringBuilder();
        int messagesIdx = body.indexOf("\"messages\"");
        if (messagesIdx < 0) {
            return "";
        }
        String messagesSection = body.substring(messagesIdx);
        Matcher matcher = MESSAGES_CONTENT.matcher(messagesSection);
        while (matcher.find()) {
            if (!combined.isEmpty()) {
                combined.append('\n');
            }
            combined.append(matcher.group(1).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return combined.toString().trim();
    }

    // ── 감사 기록 헬퍼 ──

    private void auditChatSuccess(AiTenantAccessGrant tenant, AiGatewayInferenceResult result,
                                   String prompt, boolean stream, long startNanos,
                                   String remoteAddr, String intentType) {
        if (auditLog == null) return;
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        // Determine modality from resolved type
        String modality = AiGatewayService.modalityForResolvedType(result.decision().resolvedType());
        auditLog.recordSuccess(
                tenant != null ? tenant.tenantId() : null,
                "v1/chat/completions",
                result.decision().modelName(),
                result.decision().provider(),
                "CHAT", prompt,
                durationMs, result.estimatedTokens(), stream,
                remoteAddr, result.decision().routePolicy(), intentType,
                modality);
    }

    private void auditChatFailure(AiTenantAccessGrant tenant, String prompt, boolean stream,
                                   long startNanos, String errorMsg, String remoteAddr,
                                   String intentType) {
        if (auditLog == null) return;
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        auditLog.recordFailure(
                tenant != null ? tenant.tenantId() : null,
                "v1/chat/completions",
                null, null,
                "CHAT", prompt,
                durationMs, 0, stream,
                errorMsg, remoteAddr, null, intentType,
                "text");
    }
}
