package io.velo.was.aiplatform.servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

public class AiPlatformAuthFilter implements Filter {

    static final String AUTH_ATTR = "velo.aiPlatform.authenticated";
    static final String USER_ATTR = "velo.aiPlatform.username";
    static final String CSRF_ATTR = "velo.aiPlatform.csrf";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getServletPath();
        if (req.getPathInfo() != null) {
            path += req.getPathInfo();
        }

        applyHeaders(resp);
        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = req.getSession(false);
        boolean authenticated = session != null && Boolean.TRUE.equals(session.getAttribute(AUTH_ATTR));
        if (!authenticated) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        chain.doFilter(request, response);
    }

    private static boolean isPublic(String path) {
        return "/login".equals(path)
                || "/logout".equals(path)
                || "/api/status".equals(path)
                || path.startsWith("/gateway")
                || path.startsWith("/api-docs")
                || path.startsWith("/invoke");
    }

    private static void applyHeaders(HttpServletResponse resp) {
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Referrer-Policy", "no-referrer");
    }
}