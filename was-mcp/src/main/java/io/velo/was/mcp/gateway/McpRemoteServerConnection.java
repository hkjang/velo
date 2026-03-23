package io.velo.was.mcp.gateway;

import io.velo.was.mcp.admin.McpServerDescriptor;
import io.velo.was.mcp.prompt.McpPrompt;
import io.velo.was.mcp.resource.McpResource;
import io.velo.was.mcp.tool.McpTool;
import io.velo.was.mcp.tool.McpToolInputSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Wraps a {@link McpRemoteClient} with connection lifecycle management,
 * capability caching, and health-check support.
 */
public class McpRemoteServerConnection implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpRemoteServerConnection.class);

    public enum ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    private final McpServerDescriptor descriptor;
    private final McpRemoteClient client;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private volatile Instant lastHealthCheck;
    private volatile String errorMessage;

    // Cached capabilities (immutable lists, volatile references for thread safety)
    private volatile List<McpTool> remoteTools = List.of();
    private volatile List<McpResource> remoteResources = List.of();
    private volatile List<McpPrompt> remotePrompts = List.of();

    public McpRemoteServerConnection(McpServerDescriptor descriptor) {
        this.descriptor = descriptor;
        this.client = new McpRemoteClient(descriptor.id(), descriptor.endpoint(),
                descriptor.effectiveHeaders());
        this.lastHealthCheck = Instant.now();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    /** Connect to the remote server: initialize handshake + discover capabilities. */
    public void connect() {
        state = ConnectionState.CONNECTING;
        errorMessage = null;
        try {
            client.initialize();
            refreshCapabilities();
            state = ConnectionState.CONNECTED;
            descriptor.setStatus("UP");
            descriptor.touchHealthCheck();
            log.info("Connected to remote MCP server: {} ({})", descriptor.name(), descriptor.endpoint());
        } catch (Exception e) {
            state = ConnectionState.ERROR;
            errorMessage = e.getMessage();
            descriptor.setStatus("DOWN");
            log.warn("Failed to connect to remote MCP server {} ({}): {}",
                    descriptor.name(), descriptor.endpoint(), e.getMessage());
        }
    }

    /** Disconnect from the remote server. */
    public void disconnect() {
        client.close();
        state = ConnectionState.DISCONNECTED;
        remoteTools = List.of();
        remoteResources = List.of();
        remotePrompts = List.of();
        descriptor.setStatus("DOWN");
        log.info("Disconnected from remote MCP server: {}", descriptor.name());
    }

    /** Refresh cached capabilities from the remote server. */
    @SuppressWarnings("unchecked")
    public void refreshCapabilities() {
        try {
            // Tools
            List<Map<String, Object>> rawTools = client.listTools();
            List<McpTool> tools = new ArrayList<>();
            for (Map<String, Object> raw : rawTools) {
                String name = strVal(raw, "name");
                String desc = strVal(raw, "description");
                McpToolInputSchema schema = parseInputSchema(raw.get("inputSchema"));
                if (name != null) {
                    tools.add(new McpTool(name, desc != null ? desc : "", schema));
                }
            }
            remoteTools = List.copyOf(tools);

            // Resources
            List<Map<String, Object>> rawResources = client.listResources();
            List<McpResource> resources = new ArrayList<>();
            for (Map<String, Object> raw : rawResources) {
                String uri = strVal(raw, "uri");
                String name = strVal(raw, "name");
                String desc = strVal(raw, "description");
                String mime = strVal(raw, "mimeType");
                if (uri != null) {
                    resources.add(new McpResource(uri, name != null ? name : uri,
                            desc != null ? desc : "", mime != null ? mime : "text/plain"));
                }
            }
            remoteResources = List.copyOf(resources);

            // Prompts
            List<Map<String, Object>> rawPrompts = client.listPrompts();
            List<McpPrompt> prompts = new ArrayList<>();
            for (Map<String, Object> raw : rawPrompts) {
                String name = strVal(raw, "name");
                String desc = strVal(raw, "description");
                if (name != null) {
                    prompts.add(new McpPrompt(name, desc != null ? desc : "", List.of()));
                }
            }
            remotePrompts = List.copyOf(prompts);

            log.debug("Capabilities refreshed for {}: {} tools, {} resources, {} prompts",
                    descriptor.name(), remoteTools.size(), remoteResources.size(), remotePrompts.size());
        } catch (Exception e) {
            log.warn("Failed to refresh capabilities for {}: {}", descriptor.name(), e.getMessage());
        }
    }

    /** Health check: ping the remote server. */
    public boolean healthCheck() {
        lastHealthCheck = Instant.now();
        descriptor.touchHealthCheck();
        boolean ok = client.ping();
        if (ok) {
            if (state == ConnectionState.ERROR) {
                // Recovered — reconnect
                log.info("Remote MCP server {} recovered, reconnecting", descriptor.name());
                connect();
            }
            descriptor.setStatus("UP");
        } else {
            state = ConnectionState.ERROR;
            descriptor.setStatus("DOWN");
        }
        return ok;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Proxy calls
    // ═══════════════════════════════════════════════════════════════════════

    /** Call a tool on the remote server. Returns raw result JSON. */
    public String callTool(String name, Map<String, Object> arguments) throws Exception {
        return client.callTool(name, arguments);
    }

    /** Read a resource from the remote server. Returns raw result JSON. */
    public String readResource(String uri) throws Exception {
        return client.readResource(uri);
    }

    /** Get a prompt from the remote server. Returns raw result JSON. */
    public String getPrompt(String name, Map<String, String> arguments) throws Exception {
        return client.getPrompt(name, arguments);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Accessors
    // ═══════════════════════════════════════════════════════════════════════

    public McpServerDescriptor descriptor() { return descriptor; }
    public ConnectionState state() { return state; }
    public Instant lastHealthCheck() { return lastHealthCheck; }
    public String errorMessage() { return errorMessage; }
    public List<McpTool> tools() { return remoteTools; }
    public List<McpResource> resources() { return remoteResources; }
    public List<McpPrompt> prompts() { return remotePrompts; }

    @Override
    public void close() {
        disconnect();
    }

    /** JSON serialization for admin API. */
    public String toJson() {
        return "{\"serverId\":\"" + escape(descriptor.id()) + "\""
                + ",\"serverName\":\"" + escape(descriptor.name()) + "\""
                + ",\"endpoint\":\"" + escape(descriptor.endpoint()) + "\""
                + ",\"state\":\"" + state.name() + "\""
                + ",\"tools\":" + remoteTools.size()
                + ",\"resources\":" + remoteResources.size()
                + ",\"prompts\":" + remotePrompts.size()
                + ",\"lastHealthCheck\":\"" + lastHealthCheck + "\""
                + (errorMessage != null ? ",\"error\":\"" + escape(errorMessage) + "\"" : "")
                + "}";
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static McpToolInputSchema parseInputSchema(Object raw) {
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> schemaMap = (Map<String, Object>) m;
            String type = schemaMap.getOrDefault("type", "object").toString();
            Map<String, Object> properties = schemaMap.get("properties") instanceof Map<?, ?> p
                    ? (Map<String, Object>) p : Map.of();
            List<String> required = List.of();
            if (schemaMap.get("required") instanceof List<?> reqList) {
                required = reqList.stream().map(Object::toString).toList();
            }
            return new McpToolInputSchema(type, properties, required);
        }
        return new McpToolInputSchema(Map.of());
    }

    private static String strVal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private static String mapToJson(Object value) {
        if (value instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var entry : ((Map<String, Object>) m).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(entry.getKey())).append("\":");
                sb.append(mapToJson(entry.getValue()));
            }
            sb.append('}');
            return sb.toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                sb.append(mapToJson(item));
            }
            sb.append(']');
            return sb.toString();
        }
        if (value instanceof String s) return "\"" + escape(s) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value == null) return "null";
        return "\"" + escape(value.toString()) + "\"";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
