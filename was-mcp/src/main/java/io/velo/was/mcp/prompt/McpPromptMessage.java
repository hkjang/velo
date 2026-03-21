package io.velo.was.mcp.prompt;

/**
 * A single message in the rendered prompt returned by {@code prompts/get}.
 *
 * @param role    "user" or "assistant"
 * @param content text content of the message
 */
public record McpPromptMessage(String role, String content) {

    public String toJson() {
        String escaped = content == null ? "" : content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "{\"role\":\"" + role + "\",\"content\":{\"type\":\"text\",\"text\":\"" + escaped + "\"}}";
    }
}
