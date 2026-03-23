package io.velo.was.mcp.gateway;

import io.velo.was.mcp.admin.McpServerDescriptor;
import io.velo.was.mcp.admin.McpServerRegistry;
import io.velo.was.mcp.audit.McpAuditLog;
import io.velo.was.mcp.prompt.McpPrompt;
import io.velo.was.mcp.prompt.McpPromptGetResult;
import io.velo.was.mcp.prompt.McpPromptProvider;
import io.velo.was.mcp.prompt.McpPromptRegistry;
import io.velo.was.mcp.resource.McpResource;
import io.velo.was.mcp.resource.McpResourceContents;
import io.velo.was.mcp.resource.McpResourceProvider;
import io.velo.was.mcp.resource.McpResourceRegistry;
import io.velo.was.mcp.tool.McpTool;
import io.velo.was.mcp.tool.McpToolCallResult;
import io.velo.was.mcp.tool.McpToolExecutor;
import io.velo.was.mcp.tool.McpToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Central MCP Gateway Router that aggregates tools/resources/prompts from
 * local registries and remote MCP servers, and routes requests accordingly.
 *
 * <h3>Naming Convention</h3>
 * <ul>
 *   <li>Local tools: original name (e.g. {@code infer})</li>
 *   <li>Remote tools: {@code serverId::originalName} (e.g. {@code abc-123::search})</li>
 * </ul>
 *
 * <p>The separator {@code ::} was chosen to avoid conflicts with tool names that
 * may contain hyphens, underscores, or slashes.
 */
public class McpGatewayRouter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpGatewayRouter.class);

    /** Namespace separator between serverId and tool/resource/prompt name. */
    public static final String NS_SEPARATOR = "::";

    private final ConcurrentMap<String, McpRemoteServerConnection> connections = new ConcurrentHashMap<>();
    private final McpAuditLog auditLog;
    private final ScheduledExecutorService healthScheduler;
    private volatile ScheduledFuture<?> healthTask;

    public McpGatewayRouter(McpAuditLog auditLog) {
        this.auditLog = auditLog;
        this.healthScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-gateway-health");
            t.setDaemon(true);
            return t;
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Remote server lifecycle
    // ═══════════════════════════════════════════════════════════════════════

    /** Register and connect to a remote MCP server. Runs connect in a virtual thread. */
    public void registerRemoteServer(McpServerDescriptor descriptor) {
        McpRemoteServerConnection conn = new McpRemoteServerConnection(descriptor);
        connections.put(descriptor.id(), conn);
        // Connect asynchronously to avoid blocking the caller
        Thread.startVirtualThread(() -> {
            try {
                conn.connect();
            } catch (Exception e) {
                log.warn("Async connect failed for {}: {}", descriptor.name(), e.getMessage());
            }
        });
    }

    /** Disconnect and remove a remote server. */
    public void unregisterRemoteServer(String serverId) {
        McpRemoteServerConnection conn = connections.remove(serverId);
        if (conn != null) {
            conn.close();
        }
    }

    /** Force (re)connect to a specific remote server. */
    public boolean connect(String serverId) {
        McpRemoteServerConnection conn = connections.get(serverId);
        if (conn == null) return false;
        conn.connect();
        return conn.state() == McpRemoteServerConnection.ConnectionState.CONNECTED;
    }

    /** Disconnect a specific remote server. */
    public boolean disconnect(String serverId) {
        McpRemoteServerConnection conn = connections.get(serverId);
        if (conn == null) return false;
        conn.disconnect();
        return true;
    }

    /** Refresh capabilities for a specific remote server. */
    public boolean refresh(String serverId) {
        McpRemoteServerConnection conn = connections.get(serverId);
        if (conn == null) return false;
        conn.refreshCapabilities();
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Aggregated list methods
    // ═══════════════════════════════════════════════════════════════════════

    /** Return all tools: local + namespaced remote. */
    public List<McpTool> allTools(McpToolRegistry localTools) {
        List<McpTool> merged = new ArrayList<>(localTools.list());
        for (var entry : connections.entrySet()) {
            McpRemoteServerConnection conn = entry.getValue();
            if (conn.state() != McpRemoteServerConnection.ConnectionState.CONNECTED) continue;
            String prefix = entry.getKey() + NS_SEPARATOR;
            String serverLabel = "[" + conn.descriptor().name() + "] ";
            for (McpTool tool : conn.tools()) {
                merged.add(new McpTool(
                        prefix + tool.name(),
                        serverLabel + tool.description(),
                        tool.inputSchema()));
            }
        }
        return merged;
    }

    /** Return all resources: local + namespaced remote. */
    public List<McpResource> allResources(McpResourceRegistry localResources) {
        List<McpResource> merged = new ArrayList<>(localResources.list());
        for (var entry : connections.entrySet()) {
            McpRemoteServerConnection conn = entry.getValue();
            if (conn.state() != McpRemoteServerConnection.ConnectionState.CONNECTED) continue;
            String prefix = entry.getKey() + NS_SEPARATOR;
            String serverLabel = "[" + conn.descriptor().name() + "] ";
            for (McpResource res : conn.resources()) {
                merged.add(new McpResource(
                        prefix + res.uri(),
                        serverLabel + res.name(),
                        serverLabel + res.description(),
                        res.mimeType()));
            }
        }
        return merged;
    }

    /** Return all prompts: local + namespaced remote. */
    public List<McpPrompt> allPrompts(McpPromptRegistry localPrompts) {
        List<McpPrompt> merged = new ArrayList<>(localPrompts.list());
        for (var entry : connections.entrySet()) {
            McpRemoteServerConnection conn = entry.getValue();
            if (conn.state() != McpRemoteServerConnection.ConnectionState.CONNECTED) continue;
            String prefix = entry.getKey() + NS_SEPARATOR;
            String serverLabel = "[" + conn.descriptor().name() + "] ";
            for (McpPrompt prompt : conn.prompts()) {
                merged.add(new McpPrompt(
                        prefix + prompt.name(),
                        serverLabel + prompt.description(),
                        prompt.arguments()));
            }
        }
        return merged;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Routing methods
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Route a tools/call request. If name contains "::", route to remote server.
     * Otherwise delegate to local registry. Returns the raw result JSON.
     */
    @SuppressWarnings("unchecked")
    public String routeToolCall(String name, Map<String, Object> arguments,
                                McpToolRegistry localTools) throws Exception {
        int sepIdx = name.indexOf(NS_SEPARATOR);
        if (sepIdx > 0) {
            String serverId = name.substring(0, sepIdx);
            String originalName = name.substring(sepIdx + NS_SEPARATOR.length());
            McpRemoteServerConnection conn = connections.get(serverId);
            if (conn == null || conn.state() != McpRemoteServerConnection.ConnectionState.CONNECTED) {
                return McpToolCallResult.error("Remote server not connected: " + serverId).toJson();
            }
            long start = System.currentTimeMillis();
            try {
                String result = conn.callTool(originalName, arguments);
                long duration = System.currentTimeMillis() - start;
                if (auditLog != null) {
                    auditLog.recordSuccess(null, "gateway", "[gateway:" + serverId + "] tools/call",
                            originalName, duration, "gateway");
                }
                return result;
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - start;
                if (auditLog != null) {
                    auditLog.recordFailure(null, "gateway", "[gateway:" + serverId + "] tools/call",
                            originalName, duration, -1, e.getMessage(), "gateway");
                }
                return McpToolCallResult.error("Remote call failed: " + e.getMessage()).toJson();
            }
        }
        // Local fallback
        McpToolExecutor executor = localTools.executor(name);
        if (executor == null) return null; // signal "not found" to caller
        McpToolCallResult result = executor.execute(arguments);
        return result.toJson();
    }

    /** Route a resources/read request. */
    public String routeResourceRead(String uri, McpResourceRegistry localResources) throws Exception {
        int sepIdx = uri.indexOf(NS_SEPARATOR);
        if (sepIdx > 0) {
            String serverId = uri.substring(0, sepIdx);
            String originalUri = uri.substring(sepIdx + NS_SEPARATOR.length());
            McpRemoteServerConnection conn = connections.get(serverId);
            if (conn == null || conn.state() != McpRemoteServerConnection.ConnectionState.CONNECTED) {
                return null;
            }
            long start = System.currentTimeMillis();
            try {
                String result = conn.readResource(originalUri);
                long duration = System.currentTimeMillis() - start;
                if (auditLog != null) {
                    auditLog.recordSuccess(null, "gateway", "[gateway:" + serverId + "] resources/read",
                            originalUri, duration, "gateway");
                }
                return result;
            } catch (Exception e) {
                return null;
            }
        }
        return null; // signal "not found / use local" to caller
    }

    /** Route a prompts/get request. */
    public String routePromptGet(String name, Map<String, String> arguments,
                                  McpPromptRegistry localPrompts) throws Exception {
        int sepIdx = name.indexOf(NS_SEPARATOR);
        if (sepIdx > 0) {
            String serverId = name.substring(0, sepIdx);
            String originalName = name.substring(sepIdx + NS_SEPARATOR.length());
            McpRemoteServerConnection conn = connections.get(serverId);
            if (conn == null || conn.state() != McpRemoteServerConnection.ConnectionState.CONNECTED) {
                return null;
            }
            long start = System.currentTimeMillis();
            try {
                String result = conn.getPrompt(originalName, arguments);
                long duration = System.currentTimeMillis() - start;
                if (auditLog != null) {
                    auditLog.recordSuccess(null, "gateway", "[gateway:" + serverId + "] prompts/get",
                            originalName, duration, "gateway");
                }
                return result;
            } catch (Exception e) {
                return null;
            }
        }
        return null; // use local
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Health checks
    // ═══════════════════════════════════════════════════════════════════════

    /** Start periodic health checks for all remote servers. */
    public void startHealthChecks(long intervalSeconds) {
        if (healthTask != null) {
            healthTask.cancel(false);
        }
        healthTask = healthScheduler.scheduleAtFixedRate(() -> {
            for (McpRemoteServerConnection conn : connections.values()) {
                try {
                    conn.healthCheck();
                } catch (Exception e) {
                    log.debug("Health check error for {}: {}", conn.descriptor().name(), e.getMessage());
                }
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    public void stopHealthChecks() {
        if (healthTask != null) {
            healthTask.cancel(false);
            healthTask = null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Status / Admin
    // ═══════════════════════════════════════════════════════════════════════

    public McpRemoteServerConnection getConnection(String serverId) {
        return connections.get(serverId);
    }

    public List<McpRemoteServerConnection> listConnections() {
        return List.copyOf(connections.values());
    }

    /** Build a routing table showing all tools/resources/prompts with their origin. */
    public List<RoutingEntry> routingTable(McpToolRegistry localTools,
                                           McpResourceRegistry localResources,
                                           McpPromptRegistry localPrompts) {
        List<RoutingEntry> routes = new ArrayList<>();
        // Local
        for (McpTool t : localTools.list()) {
            routes.add(new RoutingEntry(t.name(), "local", "tool", t.name()));
        }
        for (McpResource r : localResources.list()) {
            routes.add(new RoutingEntry(r.uri(), "local", "resource", r.uri()));
        }
        for (McpPrompt p : localPrompts.list()) {
            routes.add(new RoutingEntry(p.name(), "local", "prompt", p.name()));
        }
        // Remote
        for (var entry : connections.entrySet()) {
            McpRemoteServerConnection conn = entry.getValue();
            if (conn.state() != McpRemoteServerConnection.ConnectionState.CONNECTED) continue;
            String prefix = entry.getKey() + NS_SEPARATOR;
            String origin = conn.descriptor().name();
            for (McpTool t : conn.tools()) {
                routes.add(new RoutingEntry(prefix + t.name(), origin, "tool", t.name()));
            }
            for (McpResource r : conn.resources()) {
                routes.add(new RoutingEntry(prefix + r.uri(), origin, "resource", r.uri()));
            }
            for (McpPrompt p : conn.prompts()) {
                routes.add(new RoutingEntry(prefix + p.name(), origin, "prompt", p.name()));
            }
        }
        return routes;
    }

    /** A single entry in the gateway routing table. */
    public record RoutingEntry(String name, String origin, String type, String originalName) {
        public String toJson() {
            return "{\"name\":\"" + escape(name) + "\""
                    + ",\"origin\":\"" + escape(origin) + "\""
                    + ",\"type\":\"" + escape(type) + "\""
                    + ",\"originalName\":\"" + escape(originalName) + "\"}";
        }

        private static String escape(String s) {
            return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }
    }

    @Override
    public void close() {
        stopHealthChecks();
        healthScheduler.shutdownNow();
        connections.values().forEach(McpRemoteServerConnection::close);
        connections.clear();
    }
}
