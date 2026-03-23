package io.velo.was.mcp.admin;

import io.velo.was.mcp.gateway.McpGatewayRouter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry for MCP server instances managed via the admin control plane.
 *
 * <p>Thread-safe. Supports list, register, update, and remove operations.
 * When a gateway router is attached, remote server registration automatically
 * triggers connection and capability discovery.
 */
public class McpServerRegistry {

    private final ConcurrentMap<String, McpServerDescriptor> servers = new ConcurrentHashMap<>();
    private volatile McpGatewayRouter gatewayRouter;

    /** Attach the gateway router for auto-connect on remote server registration. */
    public void setGatewayRouter(McpGatewayRouter router) {
        this.gatewayRouter = router;
    }

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
        return register(name, endpoint, environment, version, Map.of(), null, null);
    }

    /**
     * Register a remote server with custom headers and optional Basic Auth.
     */
    public McpServerDescriptor register(String name, String endpoint,
                                        String environment, String version,
                                        Map<String, String> headers,
                                        String basicAuthUser, String basicAuthPassword) {
        McpServerDescriptor descriptor = new McpServerDescriptor(
                UUID.randomUUID().toString(), name, endpoint, environment, version,
                headers, basicAuthUser, basicAuthPassword);
        servers.put(descriptor.id(), descriptor);
        // Auto-connect remote servers via gateway
        if (gatewayRouter != null && !"local".equals(environment)) {
            gatewayRouter.registerRemoteServer(descriptor);
        }
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
        McpServerDescriptor removed = servers.remove(id);
        if (removed != null && gatewayRouter != null) {
            gatewayRouter.unregisterRemoteServer(id);
        }
        return removed;
    }

    public int size() { return servers.size(); }
}
