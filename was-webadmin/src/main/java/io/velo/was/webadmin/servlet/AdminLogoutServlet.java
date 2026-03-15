package io.velo.was.webadmin.servlet;

import io.velo.was.webadmin.audit.AuditEngine;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Handles user logout by invalidating the HTTP session
 * and redirecting to the login page.
 */
public class AdminLogoutServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            Object user = session.getAttribute("velo.admin.username");
            AuditEngine.instance().record(
                    user != null ? user.toString() : "unknown",
                    "LOGOUT", "webadmin", "User logged out",
                    req.getRemoteAddr(), true);
            session.invalidate();
        }
        resp.sendRedirect(req.getContextPath() + "/login");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doGet(req, resp);
    }
}
