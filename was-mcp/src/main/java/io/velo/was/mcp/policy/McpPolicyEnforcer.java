package io.velo.was.mcp.policy;

import io.velo.was.mcp.protocol.JsonRpcError;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Evaluates MCP policies at request time.
 *
 * <p>Called by {@code McpServer} before dispatching a request.
 * Returns {@code null} if the request is allowed, or a {@link JsonRpcError}
 * describing the rejection reason.
 */
public class McpPolicyEnforcer {

    private final McpPolicy policy;

    /** Per-session call counts for rate limiting, keyed by sessionId. */
    private final ConcurrentHashMap<String, RateBucket> rateBuckets = new ConcurrentHashMap<>();

    public McpPolicyEnforcer(McpPolicy policy) {
        this.policy = policy;
    }

    /**
     * Check whether a {@code tools/call} is allowed.
     *
     * @param toolName    the tool being called
     * @param sessionId   caller session ID (may be null)
     * @return rejection error, or {@code null} if allowed
     */
    public JsonRpcError checkToolCall(String toolName, String sessionId) {
        // Blocked tools
        List<String> blocked = policy.getBlockedTools();
        if (!blocked.isEmpty() && blocked.contains(toolName)) {
            return new JsonRpcError(-32012, "Tool '" + toolName + "' is blocked by server policy");
        }

        // Rate limit
        if (sessionId != null && policy.getRateLimitPerMinute() > 0) {
            JsonRpcError rateError = checkRateLimit(sessionId);
            if (rateError != null) return rateError;
        }

        return null;
    }

    /**
     * Check whether a client name is blocked (at initialize time).
     */
    public JsonRpcError checkClientAllowed(String clientName) {
        List<String> blocked = policy.getBlockedClients();
        for (String pattern : blocked) {
            if (clientName != null && clientName.matches(pattern)) {
                return new JsonRpcError(-32013, "Client '" + clientName + "' is blocked by server policy");
            }
        }
        return null;
    }

    /**
     * Check whether a prompt text matches any blocked prompt pattern.
     */
    public JsonRpcError checkPromptBlocked(String promptText) {
        List<String> patterns = policy.getBlockedPromptPatterns();
        if (patterns.isEmpty() || promptText == null) return null;
        for (String regex : patterns) {
            try {
                if (Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(promptText).find()) {
                    return new JsonRpcError(JsonRpcError.UNSAFE_PROMPT_BLOCKED,
                            "Prompt blocked by server content policy");
                }
            } catch (Exception ignored) {
                // Bad regex in policy — skip
            }
        }
        return null;
    }

    /** Access the underlying policy for reads. */
    public McpPolicy policy() { return policy; }

    // ── Rate limiting ───────────────────────────────────────────────────────

    private JsonRpcError checkRateLimit(String sessionId) {
        long now = System.currentTimeMillis();
        RateBucket bucket = rateBuckets.compute(sessionId, (k, v) -> {
            if (v == null || (now - v.windowStart) > 60_000) {
                return new RateBucket(now, new AtomicInteger(1));
            }
            v.count.incrementAndGet();
            return v;
        });
        if (bucket.count.get() > policy.getRateLimitPerMinute()) {
            return new JsonRpcError(JsonRpcError.QUOTA_EXCEEDED,
                    "Rate limit exceeded: max " + policy.getRateLimitPerMinute() + " requests/minute");
        }
        return null;
    }

    /** Periodically clear old rate buckets (call from session evictor or timer). */
    public void evictStaleRateBuckets() {
        long cutoff = System.currentTimeMillis() - 120_000; // 2-minute staleness
        rateBuckets.entrySet().removeIf(e -> e.getValue().windowStart < cutoff);
    }

    private static class RateBucket {
        final long windowStart;
        final AtomicInteger count;

        RateBucket(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
