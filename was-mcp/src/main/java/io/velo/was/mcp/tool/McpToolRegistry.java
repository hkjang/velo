package io.velo.was.mcp.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that stores {@link McpTool} descriptors and their {@link McpToolExecutor}s.
 *
 * <p>Register all tools before the server starts; tools should not be added or removed
 * at runtime in Phase 1.
 */
public class McpToolRegistry {

    private final Map<String, McpTool> tools = new ConcurrentHashMap<>();
    private final Map<String, McpToolExecutor> executors = new ConcurrentHashMap<>();

    /**
     * Register a tool with its executor.
     *
     * @return {@code this} for chaining
     */
    public McpToolRegistry register(McpTool tool, McpToolExecutor executor) {
        tools.put(tool.name(), tool);
        executors.put(tool.name(), executor);
        return this;
    }

    /** List all registered tools in registration order (backed by insertion-ordered copy). */
    public List<McpTool> list() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }

    /** Find the executor for the given tool name, or {@code null} if not found. */
    public McpToolExecutor executor(String name) {
        return executors.get(name);
    }

    /** Find the descriptor for the given tool name, or {@code null} if not found. */
    public McpTool find(String name) {
        return tools.get(name);
    }
}
