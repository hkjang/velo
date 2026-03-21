package io.velo.was.mcp.builtin;

import io.velo.was.mcp.prompt.McpPrompt;
import io.velo.was.mcp.prompt.McpPromptArgument;
import io.velo.was.mcp.prompt.McpPromptGetResult;
import io.velo.was.mcp.prompt.McpPromptMessage;
import io.velo.was.mcp.prompt.McpPromptProvider;

import java.util.List;
import java.util.Map;

/**
 * MCP prompt: {@code chat} — a general-purpose conversation prompt template.
 *
 * <p>Arguments:
 * <ul>
 *   <li>{@code task} (required) — describes the task the user wants to accomplish</li>
 *   <li>{@code context} (optional) — background context or constraints</li>
 *   <li>{@code language} (optional) — response language preference, default "Korean"</li>
 * </ul>
 */
public final class McpChatPrompt {

    private McpChatPrompt() {}

    public static final String NAME = "chat";

    public static McpPrompt descriptor() {
        return new McpPrompt(NAME,
                "General-purpose conversation prompt template for the Velo AI platform. " +
                "Generates a structured user message suitable for LLM chat inference.",
                List.of(
                        new McpPromptArgument("task", "The task or question to address", true),
                        new McpPromptArgument("context", "Optional background context or constraints", false),
                        new McpPromptArgument("language", "Response language (default: Korean)", false)
                ));
    }

    public static McpPromptProvider provider() {
        return arguments -> {
            String task = require(arguments, "task");
            String context = arguments.getOrDefault("context", "");
            String language = arguments.getOrDefault("language", "Korean");

            StringBuilder systemText = new StringBuilder();
            systemText.append("You are a helpful AI assistant powered by the Velo AI platform. ")
                      .append("Respond in ").append(language).append('.');
            if (!context.isBlank()) {
                systemText.append(" Context: ").append(context);
            }

            String userText = task;

            return new McpPromptGetResult(
                    "General chat prompt for: " + task,
                    List.of(
                            new McpPromptMessage("user", systemText + "\n\n" + userText)
                    )
            );
        };
    }

    private static String require(Map<String, String> args, String key) {
        String val = args.get(key);
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("'" + key + "' is required");
        }
        return val;
    }
}
