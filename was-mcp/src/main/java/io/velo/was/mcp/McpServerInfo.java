package io.velo.was.mcp;

/**
 * Identifies this MCP server to connecting clients (returned in {@code initialize} response).
 *
 * @param name    server name, e.g. "velo-mcp"
 * @param version server version, e.g. "0.5.10"
 */
public record McpServerInfo(String name, String version) {}
