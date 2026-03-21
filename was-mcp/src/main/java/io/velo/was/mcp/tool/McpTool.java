package io.velo.was.mcp.tool;

/**
 * Descriptor for an MCP tool (returned in {@code tools/list}).
 *
 * @param name        unique tool name, e.g. {@code "infer"}
 * @param description human-readable description for LLM and user
 * @param inputSchema JSON Schema defining the tool's input parameters
 */
public record McpTool(
        String name,
        String description,
        McpToolInputSchema inputSchema
) {

    /** Serialize to the JSON object format used in tools/list result. */
    public String toJson() {
        return "{\"name\":\"" + escape(name) + "\""
                + ",\"description\":\"" + escape(description) + "\""
                + ",\"inputSchema\":" + inputSchema.toJson()
                + "}";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
