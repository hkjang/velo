package io.velo.was.mcp.prompt;

/**
 * Declares a single argument that a {@link McpPrompt} accepts.
 *
 * @param name        argument name
 * @param description human-readable description
 * @param required    whether the argument must be provided
 */
public record McpPromptArgument(String name, String description, boolean required) {

    public String toJson() {
        return "{\"name\":\"" + escape(name) + "\""
                + ",\"description\":\"" + escape(description) + "\""
                + ",\"required\":" + required
                + "}";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
