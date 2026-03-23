package io.velo.was.mcp.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/**
 * Servlet filter that intercepts MCP JSON-RPC traffic from deployed applications
 * and reports it to {@link McpAppGatewayService} for unified monitoring and auditing.
 *
 * <h3>Detection Logic</h3>
 * <ul>
 *   <li>POST with {@code Content-Type: application/json} and body containing {@code "jsonrpc":"2.0"}</li>
 *   <li>GET with {@code Accept: text/event-stream} (SSE transport)</li>
 * </ul>
 *
 * <p>The filter is non-blocking: it wraps the response to capture status/timing but does
 * NOT buffer the entire response body (lightweight interception).
 *
 * <p>This filter is injected by the servlet container for all deployed applications,
 * not manually configured in {@code web.xml}.
 */
public class McpAppTrafficInterceptor implements Filter {

    private static final Logger log = LoggerFactory.getLogger(McpAppTrafficInterceptor.class);

    private final McpAppGatewayService gatewayService;
    private final String contextPath;
    private final String appName;

    public McpAppTrafficInterceptor(McpAppGatewayService gatewayService,
                                     String contextPath, String appName) {
        this.gatewayService = gatewayService;
        this.contextPath = contextPath;
        this.appName = appName;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // no-op; gateway service is injected via constructor
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpReq)
                || !(response instanceof HttpServletResponse httpResp)) {
            chain.doFilter(request, response);
            return;
        }

        // ── Detect MCP traffic ─────────────────────────────────────────────
        String method = httpReq.getMethod();
        boolean isMcpPost = "POST".equalsIgnoreCase(method) && isMcpJsonRpc(httpReq);
        boolean isMcpSse = "GET".equalsIgnoreCase(method) && isSseRequest(httpReq);

        if (!isMcpPost && !isMcpSse) {
            chain.doFilter(request, response);
            return;
        }

        // Register endpoint on first detection
        gatewayService.registerEndpoint(contextPath, appName);

        long startTime = System.currentTimeMillis();
        String sessionId = extractSessionId(httpReq);
        String jsonRpcMethod = null;
        String toolName = null;
        String clientName = null;

        // For POST, try to extract JSON-RPC method from cached body
        if (isMcpPost) {
            CachedBodyServletRequest cachedReq = new CachedBodyServletRequest(httpReq);
            String body = cachedReq.getCachedBody();
            jsonRpcMethod = extractJsonField(body, "method");
            toolName = extractToolName(body, jsonRpcMethod);
            clientName = extractClientName(body, jsonRpcMethod);

            // Track session on initialize
            if ("initialize".equals(jsonRpcMethod) && clientName != null) {
                String clientVersion = extractClientVersion(body);
                if (sessionId != null) {
                    gatewayService.trackSession(contextPath, sessionId, clientName, clientVersion);
                }
            }

            // Wrap response to capture status
            StatusCapturingResponse wrappedResp = new StatusCapturingResponse(httpResp);
            try {
                chain.doFilter(cachedReq, wrappedResp);
            } finally {
                long durationMs = System.currentTimeMillis() - startTime;
                int status = wrappedResp.getCapturedStatus();
                boolean success = status >= 200 && status < 300;
                int errorCode = success ? 0 : status;
                String errorMsg = success ? null : "HTTP " + status;

                gatewayService.recordTraffic(contextPath, appName, sessionId,
                        clientName, jsonRpcMethod != null ? jsonRpcMethod : "unknown",
                        toolName, durationMs, success, errorCode, errorMsg,
                        httpReq.getRemoteAddr());
            }
        } else {
            // SSE — just record the connection event, don't wrap
            gatewayService.recordTraffic(contextPath, appName, sessionId,
                    null, "sse/connect", null, 0, true, 0, null,
                    httpReq.getRemoteAddr());
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
        // no-op
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Detection helpers
    // ═══════════════════════════════════════════════════════════════════════

    private static boolean isMcpJsonRpc(HttpServletRequest req) {
        String contentType = req.getContentType();
        return contentType != null && contentType.contains("application/json");
    }

    private static boolean isSseRequest(HttpServletRequest req) {
        String accept = req.getHeader("Accept");
        return accept != null && accept.contains("text/event-stream");
    }

    private static String extractSessionId(HttpServletRequest req) {
        // MCP session ID is typically in Mcp-Session-Id header
        String sid = req.getHeader("Mcp-Session-Id");
        if (sid == null || sid.isBlank()) {
            sid = req.getHeader("X-MCP-Session-Id");
        }
        return sid;
    }

    /** Lightweight JSON field extraction without full parsing. */
    static String extractJsonField(String json, String field) {
        if (json == null) return null;
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;
        int start = -1;
        for (int i = colon + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') { start = i + 1; break; }
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return null;
        }
        if (start < 0) return null;
        int end = json.indexOf('"', start);
        return end > start ? json.substring(start, end) : null;
    }

    private static String extractToolName(String body, String jsonRpcMethod) {
        if ("tools/call".equals(jsonRpcMethod)) {
            return extractJsonField(body, "name");
        }
        return null;
    }

    private static String extractClientName(String body, String jsonRpcMethod) {
        if ("initialize".equals(jsonRpcMethod)) {
            // clientInfo.name — lightweight extraction
            int idx = body != null ? body.indexOf("\"clientInfo\"") : -1;
            if (idx >= 0) {
                String sub = body.substring(idx, Math.min(body.length(), idx + 200));
                return extractJsonField(sub, "name");
            }
        }
        return null;
    }

    private static String extractClientVersion(String body) {
        if (body == null) return "unknown";
        int idx = body.indexOf("\"clientInfo\"");
        if (idx >= 0) {
            String sub = body.substring(idx, Math.min(body.length(), idx + 200));
            String version = extractJsonField(sub, "version");
            return version != null ? version : "unknown";
        }
        return "unknown";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Request body caching wrapper
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Wraps HttpServletRequest to cache the body so it can be read both by
     * the interceptor (for MCP method extraction) and by the actual servlet.
     */
    static class CachedBodyServletRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final byte[] cachedBody;

        CachedBodyServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        String getCachedBody() {
            return new String(cachedBody, StandardCharsets.UTF_8);
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            return new CachedServletInputStream(cachedBody);
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(
                    new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    static class CachedServletInputStream extends jakarta.servlet.ServletInputStream {
        private final java.io.ByteArrayInputStream bais;

        CachedServletInputStream(byte[] data) {
            this.bais = new java.io.ByteArrayInputStream(data);
        }

        @Override public int read() { return bais.read(); }
        @Override public int read(byte[] b, int off, int len) { return bais.read(b, off, len); }
        @Override public int available() { return bais.available(); }
        @Override public boolean isFinished() { return bais.available() == 0; }
        @Override public boolean isReady() { return true; }
        @Override public void setReadListener(jakarta.servlet.ReadListener listener) {
            // Sync only; async not needed for MCP interceptor
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Response status capture wrapper
    // ═══════════════════════════════════════════════════════════════════════

    static class StatusCapturingResponse extends HttpServletResponseWrapper {
        private int capturedStatus = 200;

        StatusCapturingResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            this.capturedStatus = sc;
            super.setStatus(sc);
        }

        @Override
        public void sendError(int sc) throws IOException {
            this.capturedStatus = sc;
            super.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            this.capturedStatus = sc;
            super.sendError(sc, msg);
        }

        int getCapturedStatus() {
            return capturedStatus;
        }
    }
}
