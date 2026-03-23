package io.velo.was.mcp.transport;

import io.netty.handler.codec.http.FullHttpResponse;
import io.velo.was.http.HttpExchange;
import io.velo.was.http.HttpHandler;
import io.velo.was.http.HttpResponses;
import io.velo.was.mcp.McpServer;

/**
 * Simple liveness/readiness endpoint for the MCP server.
 *
 * <p>{@code GET /ai-platform/mcp/health} returns a JSON object indicating server status,
 * active session count, and registered capability counts.
 */
public class McpHealthHandler implements HttpHandler {

    private final McpServer server;
    private final String serverName;
    private final String serverVersion;

    public McpHealthHandler(McpServer server, String serverName, String serverVersion) {
        this.server = server;
        this.serverName = serverName;
        this.serverVersion = serverVersion;
    }

    @Override
    public FullHttpResponse handle(HttpExchange exchange) {
        int sessions  = server.sessionManager().size();
        int tools     = server.toolRegistry().list().size();
        int resources = server.resourceRegistry().list().size();
        int prompts   = server.promptRegistry().list().size();

        String json = "{"
                + "\"status\":\"UP\","
                + "\"server\":\"" + escape(serverName) + "\","
                + "\"version\":\"" + escape(serverVersion) + "\","
                + "\"protocol\":\"" + McpServer.PROTOCOL_VERSION + "\","
                + "\"sessions\":" + sessions + ","
                + "\"tools\":" + tools + ","
                + "\"resources\":" + resources + ","
                + "\"prompts\":" + prompts
                + "}";
        return HttpResponses.jsonOk(json);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
