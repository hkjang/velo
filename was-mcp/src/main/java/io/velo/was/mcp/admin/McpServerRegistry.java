package io.velo.was.mcp.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry for MCP server instances managed via the admin control plane.
 *
 * <p>Thread-safe. Supports list, register, update, and remove operations.
 */
public class McpServerRegistry {

    private final ConcurrentMap<String, McpServerDescriptor> servers = new ConcurrentHashMap<>();

    /**
     * Register the local server instance automatically (called at startup).
     */
    public McpServerDescriptor registerLocal(String name, String endpoint, String version) {
        McpServerDescriptor descriptor = new McpServerDescriptor(
                UUID.randomUUID().toString(), name, endpoint, "local", version);
        servers.put(descriptor.id(), descriptor);
        return descriptor;
    }

    /**
     * Register a remote server descriptor (called from admin API).
     */
    public McpServerDescriptor register(String name, String endpoint,
                                        String environment, String version) {
        McpServerDescriptor descriptor = new McpServerDescriptor(
                UUID.randomUUID().toString(), name, endpoint, environment, version);
        servers.put(descriptor.id(), descriptor);
        return descriptor;
    }

    /** Find by ID. */
    public McpServerDescriptor find(String id) {
        return servers.get(id);
    }

    /** List all registered servers. */
    public List<McpServerDescriptor> list() {
        return Collections.unmodifiableList(new ArrayList<>(servers.values()));
    }

    /** Remove a server by ID. Returns the removed descriptor, or null. */
    public McpServerDescriptor remove(String id) {
        return servers.remove(id);
    }

    public int size() { return servers.size(); }
}
