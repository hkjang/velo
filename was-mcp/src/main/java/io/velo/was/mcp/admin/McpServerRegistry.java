package io.velo.was.mcp.admin;

import io.velo.was.mcp.gateway.McpGatewayRouter;
import io.velo.was.mcp.protocol.SimpleJsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
 *
 * <p>Supports file-based persistence: remote server registrations are saved
 * to a JSON file and automatically restored on startup.
 */
public class McpServerRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpServerRegistry.class);

    private final ConcurrentMap<String, McpServerDescriptor> servers = new ConcurrentHashMap<>();
    private volatile McpGatewayRouter gatewayRouter;
    private volatile Path persistFile;

    /** Attach the gateway router for auto-connect on remote server registration. */
    public void setGatewayRouter(McpGatewayRouter router) {
        this.gatewayRouter = router;
    }

    /**
     * Set the file path for persisting remote server registrations.
     * If the file exists, servers are loaded immediately.
     */
    public void setPersistFile(Path persistFile) {
        this.persistFile = persistFile;
        if (persistFile != null) {
            loadFromFile();
        }
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
        saveToFile();
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
        saveToFile();
        return removed;
    }

    public int size() { return servers.size(); }

    // ═══════════════════════════════════════════════════════════════════════
    //  Persistence — JSON file
    // ═══════════════════════════════════════════════════════════════════════

    /** Save all remote (non-local) server registrations to the persist file. */
    private void saveToFile() {
        Path file = persistFile;
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder(2048);
            sb.append("[");
            boolean first = true;
            for (McpServerDescriptor desc : servers.values()) {
                if ("local".equals(desc.environment())) continue;
                if (!first) sb.append(',');
                first = false;
                sb.append(serializeDescriptor(desc));
            }
            sb.append("]");
            Files.writeString(file, sb.toString());
            log.debug("Saved {} remote MCP servers to {}", servers.size(), file);
        } catch (IOException e) {
            log.error("Failed to save MCP server registry to {}: {}", file, e.getMessage());
        }
    }

    /** Load remote server registrations from the persist file. */
    @SuppressWarnings("unchecked")
    private void loadFromFile() {
        Path file = persistFile;
        if (file == null || !Files.exists(file)) return;
        try {
            String json = Files.readString(file);
            Object parsed = SimpleJsonParser.parse(json);
            if (!(parsed instanceof List<?> list)) {
                log.warn("MCP server registry file is not a JSON array: {}", file);
                return;
            }
            int loaded = 0;
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) continue;
                Map<String, Object> map = (Map<String, Object>) m;
                String id = strVal(map, "id");
                String name = strVal(map, "name");
                String endpoint = strVal(map, "endpoint");
                String environment = strVal(map, "environment");
                String version = strVal(map, "version");
                if (id == null || endpoint == null) continue;

                Map<String, String> headers = new LinkedHashMap<>();
                if (map.get("headers") instanceof Map<?, ?> hm) {
                    hm.forEach((k, v) -> {
                        if (k != null && v != null) headers.put(k.toString(), v.toString());
                    });
                }
                String basicUser = strVal(map, "basicAuthUser");
                String basicPass = strVal(map, "basicAuthPassword");

                McpServerDescriptor desc = new McpServerDescriptor(
                        id, name != null ? name : "unnamed", endpoint,
                        environment != null ? environment : "remote",
                        version != null ? version : "unknown",
                        headers, basicUser, basicPass);
                servers.put(desc.id(), desc);

                // Auto-connect via gateway
                if (gatewayRouter != null && !"local".equals(environment)) {
                    gatewayRouter.registerRemoteServer(desc);
                }
                loaded++;
            }
            log.info("Loaded {} remote MCP servers from {}", loaded, file);
        } catch (Exception e) {
            log.error("Failed to load MCP server registry from {}: {}", file, e.getMessage());
        }
    }

    private static String serializeDescriptor(McpServerDescriptor desc) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"id\":\"").append(esc(desc.id())).append('"');
        sb.append(",\"name\":\"").append(esc(desc.name())).append('"');
        sb.append(",\"endpoint\":\"").append(esc(desc.endpoint())).append('"');
        sb.append(",\"environment\":\"").append(esc(desc.environment())).append('"');
        sb.append(",\"version\":\"").append(esc(desc.version())).append('"');

        // Headers (full values — not masked, this is internal storage)
        sb.append(",\"headers\":{");
        boolean first = true;
        for (var entry : desc.headers().entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(esc(entry.getKey())).append("\":\"").append(esc(entry.getValue())).append('"');
        }
        sb.append('}');

        if (desc.hasBasicAuth()) {
            sb.append(",\"basicAuthUser\":\"").append(esc(desc.basicAuthUser())).append('"');
            if (desc.basicAuthPassword() != null) {
                sb.append(",\"basicAuthPassword\":\"").append(esc(desc.basicAuthPassword())).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String strVal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String s ? s : (v != null ? v.toString() : null);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
