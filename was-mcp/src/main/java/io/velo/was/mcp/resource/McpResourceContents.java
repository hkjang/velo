package io.velo.was.mcp.resource;

/**
 * Content returned by {@code resources/read}.
 *
 * <p>Each content block has a URI (same as the requested resource URI),
 * a MIME type, and either {@code text} or {@code blob} (base64) data.
 * Phase 1 supports text only.
 */
public record McpResourceContents(
        String uri,
        String mimeType,
        /** Text content; mutually exclusive with blob. */
        String text
) {

    /** Convenience factory for text resources. */
    public static McpResourceContents text(String uri, String mimeType, String text) {
        return new McpResourceContents(uri, mimeType, text);
    }

    /** Serialize to the JSON content block used in resources/read result. */
    public String toJson() {
        return "{\"uri\":\"" + escape(uri) + "\""
                + ",\"mimeType\":\"" + escape(mimeType) + "\""
                + ",\"text\":\"" + escape(text) + "\""
                + "}";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
