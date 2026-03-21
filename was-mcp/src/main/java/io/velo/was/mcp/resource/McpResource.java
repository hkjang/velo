package io.velo.was.mcp.resource;

/**
 * Descriptor for an MCP resource (returned in {@code resources/list}).
 *
 * @param uri         unique resource URI, e.g. {@code "mcp://models"}
 * @param name        human-readable name
 * @param description optional description
 * @param mimeType    MIME type of the content, e.g. {@code "application/json"}
 */
public record McpResource(
        String uri,
        String name,
        String description,
        String mimeType
) {

    /** Serialize to the JSON object used in resources/list. */
    public String toJson() {
        return "{\"uri\":\"" + escape(uri) + "\""
                + ",\"name\":\"" + escape(name) + "\""
                + (description != null ? ",\"description\":\"" + escape(description) + "\"" : "")
                + (mimeType != null ? ",\"mimeType\":\"" + escape(mimeType) + "\"" : "")
                + "}";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
