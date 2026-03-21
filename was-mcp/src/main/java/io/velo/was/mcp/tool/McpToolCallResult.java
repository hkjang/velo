package io.velo.was.mcp.tool;

import java.util.List;

/**
 * Result of a {@link McpToolExecutor#execute} call.
 *
 * <p>Content items follow the MCP content block model:
 * <ul>
 *   <li>text — {@code {"type":"text","text":"..."}}</li>
 *   <li>image — not supported in Phase 1</li>
 *   <li>resource — not supported in Phase 1</li>
 * </ul>
 */
public record McpToolCallResult(
        List<ContentItem> content,
        boolean isError
) {

    /** Single text content item. */
    public record ContentItem(String type, String text) {
        public String toJson() {
            String escapedText = text == null ? "" : text
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
            return "{\"type\":\"" + type + "\",\"text\":\"" + escapedText + "\"}";
        }
    }

    /** Create a successful text result. */
    public static McpToolCallResult text(String text) {
        return new McpToolCallResult(List.of(new ContentItem("text", text)), false);
    }

    /** Create an error result with a text message. */
    public static McpToolCallResult error(String message) {
        return new McpToolCallResult(List.of(new ContentItem("text", message)), true);
    }

    /** Serialize to the JSON result object for a tools/call response. */
    public String toJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"content\":[");
        boolean first = true;
        for (ContentItem item : content) {
            if (!first) sb.append(',');
            first = false;
            sb.append(item.toJson());
        }
        sb.append("],\"isError\":").append(isError).append('}');
        return sb.toString();
    }
}
