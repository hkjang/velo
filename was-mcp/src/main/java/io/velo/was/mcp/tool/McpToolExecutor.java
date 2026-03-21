package io.velo.was.mcp.tool;

import java.util.Map;

/**
 * Executes a specific MCP tool invocation.
 *
 * <p>Each registered tool has exactly one executor. The executor is called
 * synchronously on the Netty worker thread; implementations that need heavy
 * I/O or GPU work should offload to an internal async queue and block the
 * caller with a reasonable timeout, or return a progress-style result.
 *
 * <p>Implementations must be thread-safe.
 */
@FunctionalInterface
public interface McpToolExecutor {

    /**
     * Execute the tool.
     *
     * @param arguments the {@code arguments} map from the {@code tools/call} params
     * @return tool call result (text content blocks)
     * @throws Exception if the tool execution fails unrecoverably
     */
    McpToolCallResult execute(Map<String, Object> arguments) throws Exception;
}
