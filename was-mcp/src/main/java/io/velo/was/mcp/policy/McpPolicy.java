package io.velo.was.mcp.policy;

import java.util.List;

/**
 * Mutable policy settings governing MCP server behaviour.
 *
 * <p>Policies are managed via the admin API ({@code /mcp/admin/policies}).
 */
public final class McpPolicy {

    // ── Authentication ──────────────────────────────────────────────────────

    /** Whether API key authentication is required for MCP requests. */
    private volatile boolean authRequired = false;

    /** HTTP header name for the API key (default "X-Api-Key"). */
    private volatile String apiKeyHeader = "X-Api-Key";

    // ── Rate limiting ───────────────────────────────────────────────────────

    /** Maximum requests per minute per client (0 = unlimited). */
    private volatile int rateLimitPerMinute = 0;

    /** Maximum concurrent tool executions per session (0 = unlimited). */
    private volatile int maxConcurrentToolCalls = 0;

    // ── Blocking ────────────────────────────────────────────────────────────

    /** Blocked tool names — calls to these tools are rejected. */
    private volatile List<String> blockedTools = List.of();

    /** Blocked prompt patterns — prompts matching any regex pattern are rejected. */
    private volatile List<String> blockedPromptPatterns = List.of();

    /** Blocked client names — clients matching any pattern are refused at initialize. */
    private volatile List<String> blockedClients = List.of();

    // ── Data masking ────────────────────────────────────────────────────────

    /** Regex patterns for sensitive data that should be masked in audit logs. */
    private volatile List<String> dataMaskingPatterns = List.of();

    // ── Getters ─────────────────────────────────────────────────────────────

    public boolean isAuthRequired() { return authRequired; }
    public String getApiKeyHeader() { return apiKeyHeader; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public int getMaxConcurrentToolCalls() { return maxConcurrentToolCalls; }
    public List<String> getBlockedTools() { return blockedTools; }
    public List<String> getBlockedPromptPatterns() { return blockedPromptPatterns; }
    public List<String> getBlockedClients() { return blockedClients; }
    public List<String> getDataMaskingPatterns() { return dataMaskingPatterns; }

    // ── Setters ─────────────────────────────────────────────────────────────

    public void setAuthRequired(boolean authRequired) { this.authRequired = authRequired; }
    public void setApiKeyHeader(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }
    public void setMaxConcurrentToolCalls(int maxConcurrentToolCalls) { this.maxConcurrentToolCalls = maxConcurrentToolCalls; }
    public void setBlockedTools(List<String> blockedTools) { this.blockedTools = List.copyOf(blockedTools); }
    public void setBlockedPromptPatterns(List<String> blockedPromptPatterns) { this.blockedPromptPatterns = List.copyOf(blockedPromptPatterns); }
    public void setBlockedClients(List<String> blockedClients) { this.blockedClients = List.copyOf(blockedClients); }
    public void setDataMaskingPatterns(List<String> dataMaskingPatterns) { this.dataMaskingPatterns = List.copyOf(dataMaskingPatterns); }

    /** Serialize current policy state to JSON. */
    public String toJson() {
        return "{\"authRequired\":" + authRequired
                + ",\"apiKeyHeader\":\"" + escape(apiKeyHeader) + "\""
                + ",\"rateLimitPerMinute\":" + rateLimitPerMinute
                + ",\"maxConcurrentToolCalls\":" + maxConcurrentToolCalls
                + ",\"blockedTools\":" + stringListJson(blockedTools)
                + ",\"blockedPromptPatterns\":" + stringListJson(blockedPromptPatterns)
                + ",\"blockedClients\":" + stringListJson(blockedClients)
                + ",\"dataMaskingPatterns\":" + stringListJson(dataMaskingPatterns)
                + "}";
    }

    private static String stringListJson(List<String> list) {
        if (list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String s : list) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(s)).append('"');
        }
        return sb.append(']').toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
