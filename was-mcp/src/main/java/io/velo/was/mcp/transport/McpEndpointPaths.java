package io.velo.was.mcp.transport;

/** Centralized path constants for the MCP HTTP transport. */
public final class McpEndpointPaths {

    private McpEndpointPaths() {}

    /** Main MCP endpoint — handles both POST (JSON-RPC) and GET (SSE). */
    public static final String MCP_PATH = "/ai-platform/mcp";

    /** Health/liveness endpoint. */
    public static final String HEALTH_PATH = "/ai-platform/mcp/health";

    // ── Admin control-plane paths ────────────────────────────────────────────

    public static final String ADMIN_SERVERS   = "/ai-platform/mcp/admin/servers";
    public static final String ADMIN_TOOLS     = "/ai-platform/mcp/admin/tools";
    public static final String ADMIN_RESOURCES = "/ai-platform/mcp/admin/resources";
    public static final String ADMIN_PROMPTS   = "/ai-platform/mcp/admin/prompts";
    public static final String ADMIN_SESSIONS  = "/ai-platform/mcp/admin/sessions";
    public static final String ADMIN_AUDIT     = "/ai-platform/mcp/admin/audit";
    public static final String ADMIN_POLICIES  = "/ai-platform/mcp/admin/policies";
}
