package io.velo.was.mcp.admin;

import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a registered MCP server instance in the admin control plane.
 *
 * <p>Used for multi-server orchestration where one admin console manages
 * multiple MCP endpoints (e.g. across environments, regions, or tenants).
 *
 * <p>Supports custom request headers (e.g. API keys) and Basic Auth
 * for authenticating with remote MCP servers.
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

    /** Custom headers sent with every request to the remote server (e.g. API keys). */
    private final Map<String, String> headers;

    /** Basic Auth username (null if not using basic auth). */
    private String basicAuthUser;

    /** Basic Auth password (null if not using basic auth). */
    private String basicAuthPassword;

    public McpServerDescriptor(String id, String name, String endpoint,
                               String environment, String version) {
        this(id, name, endpoint, environment, version, Map.of(), null, null);
    }

    public McpServerDescriptor(String id, String name, String endpoint,
                               String environment, String version,
                               Map<String, String> headers,
                               String basicAuthUser, String basicAuthPassword) {
        this.id = id;
        this.name = name;
        this.endpoint = endpoint;
        this.environment = environment;
        this.version = version;
        this.status = "UP";
        this.registeredAt = Instant.now();
        this.lastHealthCheck = registeredAt;
        this.headers = headers != null ? new LinkedHashMap<>(headers) : new LinkedHashMap<>();
        this.basicAuthUser = basicAuthUser;
        this.basicAuthPassword = basicAuthPassword;
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
    public Map<String, String> headers() { return Collections.unmodifiableMap(headers); }
    public String basicAuthUser() { return basicAuthUser; }
    public String basicAuthPassword() { return basicAuthPassword; }

    /** Returns true if basic auth credentials are configured. */
    public boolean hasBasicAuth() {
        return basicAuthUser != null && !basicAuthUser.isBlank();
    }

    /**
     * Build the complete set of extra headers to send with requests.
     * Includes custom headers + Basic Auth Authorization header if configured.
     */
    public Map<String, String> effectiveHeaders() {
        Map<String, String> all = new LinkedHashMap<>(headers);
        if (hasBasicAuth()) {
            String credentials = basicAuthUser + ":" + (basicAuthPassword != null ? basicAuthPassword : "");
            all.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes()));
        }
        return all;
    }

    // ── Setters (admin API) ─────────────────────────────────────────────────

    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public void setVersion(String version) { this.version = version; }
    public void setStatus(String status) { this.status = status; }
    public void touchHealthCheck() { this.lastHealthCheck = Instant.now(); }
    public void setBasicAuth(String user, String password) {
        this.basicAuthUser = user;
        this.basicAuthPassword = password;
    }
    public void setHeader(String key, String value) {
        if (value == null || value.isBlank()) {
            headers.remove(key);
        } else {
            headers.put(key, value);
        }
    }

    /** Serialize for admin API response. */
    public String toJson() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"id\":\"").append(escape(id)).append('"')
          .append(",\"name\":\"").append(escape(name)).append('"')
          .append(",\"endpoint\":\"").append(escape(endpoint)).append('"')
          .append(",\"environment\":\"").append(escape(environment)).append('"')
          .append(",\"version\":\"").append(escape(version)).append('"')
          .append(",\"status\":\"").append(escape(status)).append('"')
          .append(",\"registeredAt\":\"").append(registeredAt).append('"')
          .append(",\"lastHealthCheck\":\"").append(lastHealthCheck).append('"');

        // Headers (mask values for security — show key names only)
        sb.append(",\"headers\":{");
        boolean first = true;
        for (var entry : headers.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(entry.getKey())).append("\":\"***\"");
        }
        sb.append('}');

        sb.append(",\"hasBasicAuth\":").append(hasBasicAuth());
        if (hasBasicAuth()) {
            sb.append(",\"basicAuthUser\":\"").append(escape(basicAuthUser)).append('"');
        }
        sb.append(",\"headerCount\":").append(headers.size());
        sb.append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
