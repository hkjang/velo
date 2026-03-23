package io.velo.was.mcp.prompt;

import java.util.List;

/**
 * Descriptor for an MCP prompt template (returned in {@code prompts/list}).
 *
 * @param name        unique prompt name
 * @param description human-readable description
 * @param arguments   declared arguments (may be empty)
 */
public record McpPrompt(String name, String description, List<McpPromptArgument> arguments) {

    /** Serialize to the JSON object format used in prompts/list. */
    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"name\":\"").append(escape(name)).append('"')
          .append(",\"description\":\"").append(escape(description)).append('"');
        if (arguments != null && !arguments.isEmpty()) {
            sb.append(",\"arguments\":[");
            boolean first = true;
            for (McpPromptArgument arg : arguments) {
                if (!first) sb.append(',');
                first = false;
                sb.append(arg.toJson());
            }
            sb.append(']');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
