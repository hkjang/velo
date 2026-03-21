package io.velo.was.mcp.prompt;

import java.util.Map;

/**
 * Renders a specific MCP prompt template with the given arguments.
 *
 * <p>Implementations must be thread-safe.
 */
@FunctionalInterface
public interface McpPromptProvider {

    /**
     * Render the prompt.
     *
     * @param arguments argument values from the {@code prompts/get} request
     * @return rendered prompt result
     * @throws Exception if rendering fails
     */
    McpPromptGetResult get(Map<String, String> arguments) throws Exception;
}
