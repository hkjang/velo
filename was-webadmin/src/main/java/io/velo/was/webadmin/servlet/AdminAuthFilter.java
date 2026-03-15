package io.velo.was.webadmin.servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Security filter that protects all Web Admin pages.
 * Unauthenticated requests are redirected to the login page.
 * The login page itself and static resources are excluded.
 * <p>
 * Provides CSRF protection, security headers, rate limiting,
 * and secure cookie flags for enterprise environments.
 */
public class AdminAuthFilter implements Filter {

    static final String CSRF_TOKEN_ATTR = "velo.csrf.token";
    static final String CSRF_FORM_FIELD = "_csrf";
    static final String CSRF_HEADER = "X-CSRF-Token";

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long FAILURE_WINDOW_MS = 5 * 60 * 1000L;
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000L;
    private static final long CLEANUP_INTERVAL_MS = 60 * 1000L;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Tracks failed login attempts per IP for rate limiting.
     */
    static final ConcurrentHashMap<String, FailedAttemptRecord> failedAttempts = new ConcurrentHashMap<>();

    private volatile long lastCleanupTime = System.currentTimeMillis();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        // Build the full application-relative path.
        // For path-prefix mappings like /api/*, getServletPath()="/api" and getPathInfo()="/execute"
        // so we must combine them to correctly identify API paths.
        String path = req.getServletPath();
        if (req.getPathInfo() != null) {
            path = path + req.getPathInfo();
        }

        // Periodic cleanup of stale rate-limit entries
        cleanupIfNeeded();

        // Allow login page and static resources without authentication
        if (isPublicPath(path)) {
            addSecurityHeaders(resp);
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = req.getSession(false);
        boolean authenticated = session != null
                && Boolean.TRUE.equals(session.getAttribute("velo.admin.authenticated"));

        if (!authenticated) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        // Ensure CSRF token exists in session
        ensureCsrfToken(session);

        // Validate CSRF token on POST requests (except JSON API endpoints)
        if ("POST".equalsIgnoreCase(req.getMethod()) && !isJsonApiPath(path)) {
            String sessionToken = (String) session.getAttribute(CSRF_TOKEN_ATTR);
            String requestToken = req.getParameter(CSRF_FORM_FIELD);
            if (requestToken == null) {
                requestToken = req.getHeader(CSRF_HEADER);
            }
            if (sessionToken == null || !sessionToken.equals(requestToken)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                resp.setContentType("text/plain; charset=UTF-8");
                resp.getWriter().write("CSRF token validation failed");
                return;
            }
        }

        // Add security headers
        addSecurityHeaders(resp);

        // Set secure cookie flags on session cookie
        setSecureCookieFlags(resp, req);

        chain.doFilter(request, response);
    }

    private void addSecurityHeaders(HttpServletResponse resp) {
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("X-XSS-Protection", "1; mode=block");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self' 'unsafe-inline'; "
                        + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                        + "font-src 'self'; frame-ancestors 'none'");
        resp.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    }

    private void setSecureCookieFlags(HttpServletResponse resp, HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            String sessionId = session.getId();
            String cookieValue = "JSESSIONID=" + sessionId + "; Path=" + req.getContextPath()
                    + "; HttpOnly; SameSite=Strict";
            resp.setHeader("Set-Cookie", cookieValue);
        }
    }

    /**
     * Generates a CSRF token and stores it in the session if not already present.
     */
    void ensureCsrfToken(HttpSession session) {
        if (session.getAttribute(CSRF_TOKEN_ATTR) == null) {
            byte[] tokenBytes = new byte[32];
            secureRandom.nextBytes(tokenBytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
            session.setAttribute(CSRF_TOKEN_ATTR, token);
        }
    }

    /**
     * Records a failed login attempt for the given IP address.
     */
    public static void recordFailedAttempt(String ip) {
        failedAttempts.compute(ip, (key, record) -> {
            long now = System.currentTimeMillis();
            if (record == null) {
                return new FailedAttemptRecord(1, now, now);
            }
            // Reset if outside failure window
            if (now - record.firstAttemptTime > FAILURE_WINDOW_MS && !record.isLockedOut(now)) {
                return new FailedAttemptRecord(1, now, now);
            }
            return new FailedAttemptRecord(record.count + 1, record.firstAttemptTime, now);
        });
    }

    /**
     * Clears failed attempts for the given IP on successful login.
     */
    public static void clearFailedAttempts(String ip) {
        failedAttempts.remove(ip);
    }

    /**
     * Checks if the given IP is currently locked out due to excessive failures.
     */
    public static boolean isLockedOut(String ip) {
        FailedAttemptRecord record = failedAttempts.get(ip);
        if (record == null) return false;
        return record.isLockedOut(System.currentTimeMillis());
    }

    /**
     * Returns the number of seconds remaining in the lockout, or 0 if not locked.
     */
    public static long getLockoutRemainingSeconds(String ip) {
        FailedAttemptRecord record = failedAttempts.get(ip);
        if (record == null) return 0;
        long now = System.currentTimeMillis();
        if (!record.isLockedOut(now)) return 0;
        long elapsed = now - record.lastAttemptTime;
        long remaining = LOCKOUT_DURATION_MS - elapsed;
        return remaining > 0 ? (remaining / 1000) + 1 : 0;
    }

    private boolean isPublicPath(String path) {
        return "/login".equals(path)
                || "/logout".equals(path)
                || (path != null && path.startsWith("/static/"));
    }

    private boolean isJsonApiPath(String path) {
        return path != null && path.startsWith("/api/");
    }

    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) return;
        lastCleanupTime = now;

        Iterator<Map.Entry<String, FailedAttemptRecord>> it = failedAttempts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, FailedAttemptRecord> entry = it.next();
            FailedAttemptRecord record = entry.getValue();
            // Remove entries that are past both the failure window and any lockout
            if (!record.isLockedOut(now)
                    && now - record.lastAttemptTime > FAILURE_WINDOW_MS) {
                it.remove();
            }
        }
    }

    /**
     * Immutable record of failed login attempts for a single IP.
     */
    static final class FailedAttemptRecord {
        final int count;
        final long firstAttemptTime;
        final long lastAttemptTime;

        FailedAttemptRecord(int count, long firstAttemptTime, long lastAttemptTime) {
            this.count = count;
            this.firstAttemptTime = firstAttemptTime;
            this.lastAttemptTime = lastAttemptTime;
        }

        boolean isLockedOut(long now) {
            return count >= MAX_FAILED_ATTEMPTS
                    && (now - lastAttemptTime) < LOCKOUT_DURATION_MS;
        }
    }
}
