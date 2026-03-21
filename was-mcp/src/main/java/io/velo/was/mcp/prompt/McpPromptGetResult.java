package io.velo.was.mcp.prompt;

import java.util.List;

/**
 * Result of a {@code prompts/get} call — an optional description and a list of messages.
 */
public record McpPromptGetResult(String description, List<McpPromptMessage> messages) {

    public String toJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append('{');
        if (description != null) {
            sb.append("\"description\":\"").append(escape(description)).append("\",");
        }
        sb.append("\"messages\":[");
        boolean first = true;
        for (McpPromptMessage msg : messages) {
            if (!first) sb.append(',');
            first = false;
            sb.append(msg.toJson());
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
