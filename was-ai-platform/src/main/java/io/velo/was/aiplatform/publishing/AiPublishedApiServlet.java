package io.velo.was.aiplatform.publishing;

import io.velo.was.aiplatform.api.AiPlatformExtendedJson;
import io.velo.was.aiplatform.gateway.AiGatewayServlet;
import io.velo.was.aiplatform.observability.AiPlatformUsageService;
import io.velo.was.aiplatform.tenant.AiTenantAccessGrant;
import io.velo.was.aiplatform.tenant.AiTenantService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.stream.Collectors;

public class AiPublishedApiServlet extends HttpServlet {

    private final AiPublishedApiService publishedApiService;
    private final AiPlatformUsageService usageService;
    private final AiTenantService tenantService;

    public AiPublishedApiServlet(AiPublishedApiService publishedApiService,
                                 AiPlatformUsageService usageService,
                                 AiTenantService tenantService) {
        this.publishedApiService = publishedApiService;
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
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        AiTenantAccessGrant tenantAccess = authorize(req, resp);
        if (tenantAccess == null) {
            return;
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo)) {
            applyTenantHeaders(resp, tenantAccess, 0);
            resp.getWriter().write(AiPlatformExtendedJson.publishedEndpoints(publishedApiService.listEndpoints(req.getContextPath())));
            resp.getWriter().flush();
            tenantService.recordUsage(tenantAccess, 0);
            return;
        }
        String modelName = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        String prompt = req.getParameter("prompt");
        if ((prompt == null || prompt.isBlank()) && "POST".equalsIgnoreCase(req.getMethod())) {
            prompt = req.getReader().lines().collect(Collectors.joining("\n"));
        }
        try {
            AiPublishedInvocationResult result = publishedApiService.invoke(req.getContextPath(), modelName, prompt, req.getParameter("sessionId"));
            usageService.recordPublishedInvocation(result.endpoint().modelName(), result.estimatedTokens());
            tenantService.recordUsage(tenantAccess, result.estimatedTokens());
            applyTenantHeaders(resp, tenantAccess, result.estimatedTokens());
            resp.getWriter().write(AiPlatformExtendedJson.publishedInvocation(result));
        } catch (IllegalStateException e) {
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            resp.getWriter().write("{\"error\":\"" + AiGatewayServlet.escapeJson(e.getMessage()) + "\"}");
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("{\"error\":\"" + AiGatewayServlet.escapeJson(e.getMessage()) + "\"}");
        }
        resp.getWriter().flush();
    }

    private AiTenantAccessGrant authorize(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            return tenantService.authorize(resolveApiKey(req));
        } catch (SecurityException e) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"error\":\"" + AiGatewayServlet.escapeJson(e.getMessage()) + "\"}");
            resp.getWriter().flush();
            return null;
        } catch (IllegalStateException e) {
            int status = e.getMessage() != null && e.getMessage().contains("Rate limit")
                    ? 429 /* Too Many Requests */
                    : HttpServletResponse.SC_FORBIDDEN;
            resp.setStatus(status);
            resp.getWriter().write("{\"error\":\"" + AiGatewayServlet.escapeJson(e.getMessage()) + "\"}");
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
        resp.setHeader("X-Token-Quota-Remaining", String.valueOf(Math.max(0L, tenantAccess.remainingTokensBeforeRequest() - Math.max(0, estimatedTokens))));
    }
}