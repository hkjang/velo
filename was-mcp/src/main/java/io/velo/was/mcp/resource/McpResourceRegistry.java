package io.velo.was.mcp.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for MCP resources and their read providers.
 */
public class McpResourceRegistry {

    private final Map<String, McpResource> resources = new ConcurrentHashMap<>();
    private final Map<String, McpResourceProvider> providers = new ConcurrentHashMap<>();

    /**
     * Register a resource with its provider.
     *
     * @return {@code this} for chaining
     */
    public McpResourceRegistry register(McpResource resource, McpResourceProvider provider) {
        resources.put(resource.uri(), resource);
        providers.put(resource.uri(), provider);
        return this;
    }

    /** List all registered resources. */
    public List<McpResource> list() {
        return Collections.unmodifiableList(new ArrayList<>(resources.values()));
    }

    /** Find the provider for the given URI, or {@code null} if not found. */
    public McpResourceProvider provider(String uri) {
        return providers.get(uri);
    }

    /** Find the descriptor for the given URI, or {@code null} if not found. */
    public McpResource find(String uri) {
        return resources.get(uri);
    }
}
