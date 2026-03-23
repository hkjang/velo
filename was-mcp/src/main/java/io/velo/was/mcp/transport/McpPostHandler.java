package io.velo.was.mcp.transport;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.velo.was.http.HttpExchange;
import io.velo.was.http.HttpHandler;
import io.velo.was.http.HttpResponses;
import io.velo.was.mcp.McpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * HTTP POST handler for the MCP endpoint ({@code /ai-platform/mcp}).
 *
 * <p>Accepts {@code application/json} bodies containing JSON-RPC 2.0 messages.
 * Returns the JSON-RPC response as {@code application/json}.
 *
 * <p>Session management:
 * <ul>
 *   <li>On {@code initialize}: creates a new session, returns its ID in
 *       {@code Mcp-Session-Id} response header.</li>
 *   <li>On subsequent requests: reads {@code Mcp-Session-Id} request header
 *       to correlate with an existing session.</li>
 * </ul>
 */
public class McpPostHandler implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(McpPostHandler.class);

    static final String SESSION_HEADER = "Mcp-Session-Id";
    static final String CONTENT_TYPE_JSON = "application/json";

    private final McpServer server;

    public McpPostHandler(McpServer server) {
        this.server = server;
    }

    @Override
    public FullHttpResponse handle(HttpExchange exchange) {
        FullHttpRequest request = exchange.request();

        if (request.method() != HttpMethod.POST) {
            // GET with Accept: text/event-stream is handled by SseHandlerRegistry, not this handler.
            // Any other method (GET without SSE, PUT, DELETE, etc.) is rejected here.
            String hint = request.method() == HttpMethod.GET
                    ? "Use POST for JSON-RPC, or GET with Accept: text/event-stream for SSE"
                    : "Only POST is supported for JSON-RPC on this endpoint";
            return HttpResponses.methodNotAllowed(hint);
        }

        String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null || !contentType.startsWith(CONTENT_TYPE_JSON)) {
            return HttpResponses.badRequest("Content-Type must be application/json");
        }

        String body = request.content().toString(StandardCharsets.UTF_8).trim();
        if (body.isEmpty()) {
            return HttpResponses.badRequest("Request body is empty");
        }

        String sessionId = request.headers().get(SESSION_HEADER);
        String[] outSessionId = {null};
        String remoteAddr = exchange.remoteAddress() != null ? exchange.remoteAddress().toString() : null;

        String responseJson;
        try {
            responseJson = server.handle(body, sessionId, outSessionId, remoteAddr);
        } catch (Exception e) {
            log.error("MCP dispatch error", e);
            return HttpResponses.serverError("MCP internal error: " + e.getMessage());
        }

        // Notification: no response body
        if (responseJson == null) {
            return McpTransportResponses.noContent(outSessionId[0]);
        }

        return McpTransportResponses.jsonRpc(responseJson, outSessionId[0]);
    }
}
