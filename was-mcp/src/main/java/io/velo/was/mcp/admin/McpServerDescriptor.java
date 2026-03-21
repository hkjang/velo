package io.velo.was.mcp.admin;

import java.time.Instant;

/**
 * Represents a registered MCP server instance in the admin control plane.
 *
 * <p>Used for multi-server orchestration where one admin console manages
 * multiple MCP endpoints (e.g. across environments, regions, or tenants).
 */
public final class McpServerDescriptor {

    private final String id;
    private final String name;
    private String endpoint;
    private String environment;
    private String version;
    private String status; // UP, DOWN, DRAINING
    private final Instant registeredAt;
    private volatile Instant lastHealthCheck;

    public McpServerDescriptor(String id, String name, String endpoint,
                               String environment, String version) {
        this.id = id;
        this.name = name;
        this.endpoint = endpoint;
        this.environment = environment;
        this.version = version;
        this.status = "UP";
        this.registeredAt = Instant.now();
        this.lastHealthCheck = registeredAt;
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    public String id() { return id; }
    public String name() { return name; }
    public String endpoint() { return endpoint; }
    public String environment() { return environment; }
    public String version() { return version; }
    public String status() { return status; }
    public Instant registeredAt() { return registeredAt; }
    public Instant lastHealthCheck() { return lastHealthCheck; }

    // ── Setters (admin API) ─────────────────────────────────────────────────

    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public void setVersion(String version) { this.version = version; }
    public void setStatus(String status) { this.status = status; }
    public void touchHealthCheck() { this.lastHealthCheck = Instant.now(); }

    /** Serialize for admin API response. */
    public String toJson() {
        return "{\"id\":\"" + escape(id) + "\""
                + ",\"name\":\"" + escape(name) + "\""
                + ",\"endpoint\":\"" + escape(endpoint) + "\""
                + ",\"environment\":\"" + escape(environment) + "\""
                + ",\"version\":\"" + escape(version) + "\""
                + ",\"status\":\"" + escape(status) + "\""
                + ",\"registeredAt\":\"" + registeredAt + "\""
                + ",\"lastHealthCheck\":\"" + lastHealthCheck + "\""
                + "}";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
