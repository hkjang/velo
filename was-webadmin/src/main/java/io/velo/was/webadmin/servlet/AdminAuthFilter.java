package io.velo.was.webadmin.servlet;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Security filter that protects all Web Admin pages.
 * Unauthenticated requests are redirected to the login page.
 * The login page itself and static resources are excluded.
 */
public class AdminAuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = req.getPathInfo();
        if (path == null) path = req.getServletPath();

        // Allow login page and static resources without authentication
        if (isPublicPath(path)) {
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

        // Add security headers
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "DENY");
        resp.setHeader("X-XSS-Protection", "1; mode=block");
        resp.setHeader("Cache-Control", "no-store");

        chain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return "/login".equals(path)
                || "/logout".equals(path)
                || (path != null && path.startsWith("/static/"));
    }
}
