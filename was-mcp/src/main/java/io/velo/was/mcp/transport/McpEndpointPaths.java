package io.velo.was.mcp.transport;

/** Centralized path constants for the MCP HTTP transport. */
public final class McpEndpointPaths {

    private McpEndpointPaths() {}

    /** Main MCP endpoint — handles both POST (JSON-RPC) and GET (SSE). */
    public static final String MCP_PATH = "/mcp";

    /** Health/liveness endpoint. */
    public static final String HEALTH_PATH = "/mcp/health";

    // ── Admin control-plane paths ────────────────────────────────────────────

    public static final String ADMIN_SERVERS   = "/mcp/admin/servers";
    public static final String ADMIN_TOOLS     = "/mcp/admin/tools";
    public static final String ADMIN_RESOURCES = "/mcp/admin/resources";
    public static final String ADMIN_PROMPTS   = "/mcp/admin/prompts";
    public static final String ADMIN_SESSIONS  = "/mcp/admin/sessions";
    public static final String ADMIN_AUDIT     = "/mcp/admin/audit";
    public static final String ADMIN_POLICIES  = "/mcp/admin/policies";
}
