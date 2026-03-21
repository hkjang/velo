package io.velo.was.mcp.prompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for MCP prompt templates and their providers.
 */
public class McpPromptRegistry {

    private final Map<String, McpPrompt> prompts = new ConcurrentHashMap<>();
    private final Map<String, McpPromptProvider> providers = new ConcurrentHashMap<>();

    /**
     * Register a prompt with its provider.
     *
     * @return {@code this} for chaining
     */
    public McpPromptRegistry register(McpPrompt prompt, McpPromptProvider provider) {
        prompts.put(prompt.name(), prompt);
        providers.put(prompt.name(), provider);
        return this;
    }

    /** List all registered prompts. */
    public List<McpPrompt> list() {
        return Collections.unmodifiableList(new ArrayList<>(prompts.values()));
    }

    /** Find the provider for the given prompt name, or {@code null} if not found. */
    public McpPromptProvider provider(String name) {
        return providers.get(name);
    }

    /** Find the descriptor for the given prompt name, or {@code null} if not found. */
    public McpPrompt find(String name) {
        return prompts.get(name);
    }
}
