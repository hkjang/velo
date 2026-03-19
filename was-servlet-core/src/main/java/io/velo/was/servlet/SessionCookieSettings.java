package io.velo.was.servlet;

import jakarta.servlet.http.Cookie;

import java.util.Locale;

/**
 * Runtime policy for session cookies emitted by the servlet container.
 */
public final class SessionCookieSettings {

    public enum SecureMode {
        AUTO,
        ALWAYS,
        NEVER;

        public static SecureMode from(String value) {
            if (value == null || value.isBlank()) {
                return AUTO;
            }
            return SecureMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    private final String name;
    private final String path;
    private final boolean httpOnly;
    private final SecureMode secureMode;
    private final String sameSite;
    private final int maxAgeSeconds;
    private final String domain;

    public SessionCookieSettings(String name,
                                 String path,
                                 boolean httpOnly,
                                 SecureMode secureMode,
                                 String sameSite,
                                 int maxAgeSeconds,
                                 String domain) {
        this.name = normalizeName(name);
        this.path = normalizeNullable(path);
        this.httpOnly = httpOnly;
        this.secureMode = secureMode == null ? SecureMode.AUTO : secureMode;
        this.sameSite = normalizeNullable(sameSite);
        this.maxAgeSeconds = maxAgeSeconds;
        this.domain = normalizeNullable(domain);
    }

    public static SessionCookieSettings defaults() {
        return new SessionCookieSettings("JSESSIONID", null, true, SecureMode.AUTO, null, -1, null);
    }

    public String name() {
        return name;
    }

    public String resolvePath(String contextPath) {
        if (path != null) {
            return path;
        }
        if (contextPath == null || contextPath.isBlank()) {
            return "/";
        }
        return contextPath;
    }

    public boolean shouldUseSecureFlag(boolean requestSecure) {
        return switch (secureMode) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> requestSecure;
        };
    }

    public Cookie createSessionCookie(String sessionId, String contextPath, boolean requestSecure) {
        Cookie cookie = new Cookie(name, sessionId);
        cookie.setPath(resolvePath(contextPath));
        cookie.setHttpOnly(httpOnly);
        cookie.setSecure(shouldUseSecureFlag(requestSecure));
        if (maxAgeSeconds >= 0) {
            cookie.setMaxAge(maxAgeSeconds);
        }
        if (domain != null) {
            cookie.setDomain(domain);
        }
        if (sameSite != null) {
            cookie.setAttribute("SameSite", sameSite);
        }
        return cookie;
    }

    private static String normalizeName(String value) {
        return value == null || value.isBlank() ? "JSESSIONID" : value.trim();
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
