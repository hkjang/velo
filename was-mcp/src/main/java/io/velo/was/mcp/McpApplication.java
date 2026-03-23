package io.velo.was.mcp;

import io.velo.was.admin.client.AdminClient;
import io.velo.was.aiplatform.gateway.AiGatewayService;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.http.HttpHandlerRegistry;
import io.velo.was.http.SseHandlerRegistry;
import io.velo.was.mcp.admin.McpAdminHandler;
import io.velo.was.mcp.admin.McpServerRegistry;
import io.velo.was.mcp.audit.McpAuditLog;
import io.velo.was.mcp.builtin.McpAdminCliTools;
import io.velo.was.mcp.builtin.McpChatPrompt;
import io.velo.was.mcp.builtin.McpInferTool;
import io.velo.was.mcp.builtin.McpModelListResource;
import io.velo.was.mcp.builtin.McpPlatformStatusResource;
import io.velo.was.mcp.builtin.McpRouteTool;
import io.velo.was.mcp.policy.McpPolicy;
import io.velo.was.mcp.policy.McpPolicyEnforcer;
import io.velo.was.mcp.prompt.McpPromptRegistry;
import io.velo.was.mcp.resource.McpResourceRegistry;
import io.velo.was.mcp.session.McpSessionManager;
import io.velo.was.mcp.tool.McpToolRegistry;
import io.velo.was.mcp.transport.McpEndpointPaths;
import io.velo.was.mcp.transport.McpHealthHandler;
import io.velo.was.mcp.transport.McpPostHandler;
import io.velo.was.mcp.transport.McpSseHandler;

/**
 * Factory that wires the MCP module and registers its HTTP handlers.
 *
 * <p>Usage in bootstrap:
 * <pre>
 *   McpApplication.install(httpRegistry, sseRegistry, registryService, gatewayService,
 *                          "velo-mcp", "0.5.10");
 * </pre>
 *
 * <h3>Registered endpoints</h3>
 * <table>
 *   <tr><th>Path</th><th>Method</th><th>Description</th></tr>
 *   <tr><td>/ai-platform/mcp</td><td>POST</td><td>JSON-RPC 2.0 over HTTP</td></tr>
 *   <tr><td>/ai-platform/mcp</td><td>GET (SSE)</td><td>Server-Sent Events stream</td></tr>
 *   <tr><td>/ai-platform/mcp/health</td><td>GET</td><td>Liveness/readiness</td></tr>
 *   <tr><td>/ai-platform/mcp/admin/servers</td><td>GET, POST</td><td>MCP server management</td></tr>
 *   <tr><td>/ai-platform/mcp/admin/tools</td><td>GET, POST, PUT</td><td>Tool catalog management</td></tr>
 *   <tr><td>/ai-platform/mcp/admin/resources</td><td>GET, POST, PUT</td><td>Resource management</td></tr>
 *   <tr><td>/ai-platform/mcp/admin/prompts</td><td>GET, POST, PUT</td><td>Prompt management</td></tr>
 *   <tr><td>/ai-platform/mcp/admin/sessions</td><td>GET</td><td>Session/connection listing</td></tr>
 *   <tr><td>/ai-platform/mcp/admin/audit</td><td>GET</td><td>Audit log query</td></tr>
 *   <tr><td>/ai-platform/mcp/admin/policies</td><td>GET, PUT</td><td>Policy management</td></tr>
 * </table>
 */
public final class McpApplication {

    private McpApplication() {}

    /**
     * Construct the MCP server, admin control plane, and register all handlers.
     *
     * @param httpRegistry    HTTP handler registry for POST, health, and admin endpoints
     * @param sseRegistry     SSE handler registry for GET streaming endpoint
     * @param registryService AI model registry (for resource and tool backing)
     * @param gatewayService  AI gateway (for infer and route tools)
     * @param serverName      server name reported in {@code initialize} response
     * @param serverVersion   server version reported in {@code initialize} response
     * @return the created {@link McpServer} (useful for tests and admin integrations)
     */
    /**
     * Convenience overload without AdminClient (admin CLI tools not registered).
     */
    public static McpServer install(HttpHandlerRegistry httpRegistry,
                                    SseHandlerRegistry sseRegistry,
                                    AiModelRegistryService registryService,
                                    AiGatewayService gatewayService,
                                    String serverName,
                                    String serverVersion) {
        return install(httpRegistry, sseRegistry, registryService, gatewayService, null, serverName, serverVersion);
    }

    /**
     * Construct the MCP server, admin control plane, and register all handlers.
     *
     * @param adminClient     Admin CLI client (for server/app/monitoring tools); null to skip
     */
    public static McpServer install(HttpHandlerRegistry httpRegistry,
                                    SseHandlerRegistry sseRegistry,
                                    AiModelRegistryService registryService,
                                    AiGatewayService gatewayService,
                                    AdminClient adminClient,
                                    String serverName,
                                    String serverVersion) {
        McpServerInfo serverInfo = new McpServerInfo(serverName, serverVersion);

        // ── Tool registry ────────────────────────────────────────────────────
        McpToolRegistry toolRegistry = new McpToolRegistry()
                .register(McpInferTool.descriptor(), McpInferTool.executor(gatewayService))
                .register(McpRouteTool.descriptor(), McpRouteTool.executor(gatewayService));

        // ── Admin CLI tools (server, app, monitoring, logging, etc.) ─────────
        if (adminClient != null) {
            McpAdminCliTools.registerAll(toolRegistry, adminClient);
        }

        // ── Resource registry ────────────────────────────────────────────────
        McpResourceRegistry resourceRegistry = new McpResourceRegistry()
                .register(McpModelListResource.descriptor(), McpModelListResource.provider(registryService))
                .register(McpPlatformStatusResource.descriptor(),
                        McpPlatformStatusResource.provider(registryService, gatewayService));

        // ── Prompt registry ──────────────────────────────────────────────────
        McpPromptRegistry promptRegistry = new McpPromptRegistry()
                .register(McpChatPrompt.descriptor(), McpChatPrompt.provider());

        // ── Session manager ──────────────────────────────────────────────────
        McpSessionManager sessionManager = new McpSessionManager();

        // ── Core server ──────────────────────────────────────────────────────
        McpServer mcpServer = new McpServer(serverInfo, toolRegistry, resourceRegistry,
                promptRegistry, sessionManager);

        // ── Audit log ────────────────────────────────────────────────────────
        McpAuditLog auditLog = new McpAuditLog();
        mcpServer.setAuditLog(auditLog);

        // ── Policy enforcer ──────────────────────────────────────────────────
        McpPolicy policy = new McpPolicy();
        McpPolicyEnforcer policyEnforcer = new McpPolicyEnforcer(policy);
        mcpServer.setPolicyEnforcer(policyEnforcer);

        // ── Server registry (control plane) ──────────────────────────────────
        McpServerRegistry serverRegistry = new McpServerRegistry();
        serverRegistry.registerLocal(serverName, McpEndpointPaths.MCP_PATH, serverVersion);

        // ── MCP protocol handlers ────────────────────────────────────────────
        McpPostHandler postHandler = new McpPostHandler(mcpServer);
        McpSseHandler sseHandler = new McpSseHandler(mcpServer);
        McpHealthHandler healthHandler = new McpHealthHandler(mcpServer, serverName, serverVersion);

        httpRegistry.register(McpEndpointPaths.MCP_PATH, postHandler);
        httpRegistry.register(McpEndpointPaths.HEALTH_PATH, healthHandler);
        sseRegistry.register(McpEndpointPaths.MCP_PATH, sseHandler);

        // ── Admin control-plane handler ──────────────────────────────────────
        McpAdminHandler adminHandler = new McpAdminHandler(mcpServer, serverRegistry, auditLog, policyEnforcer);
        httpRegistry.register(McpEndpointPaths.ADMIN_SERVERS, adminHandler);
        httpRegistry.register(McpEndpointPaths.ADMIN_TOOLS, adminHandler);
        httpRegistry.register(McpEndpointPaths.ADMIN_RESOURCES, adminHandler);
        httpRegistry.register(McpEndpointPaths.ADMIN_PROMPTS, adminHandler);
        httpRegistry.register(McpEndpointPaths.ADMIN_SESSIONS, adminHandler);
        httpRegistry.register(McpEndpointPaths.ADMIN_AUDIT, adminHandler);
        httpRegistry.register(McpEndpointPaths.ADMIN_POLICIES, adminHandler);

        return mcpServer;
    }
}
