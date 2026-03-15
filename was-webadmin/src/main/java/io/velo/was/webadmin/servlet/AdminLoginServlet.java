package io.velo.was.webadmin.servlet;

import io.velo.was.admin.client.AdminClient;
import io.velo.was.admin.client.LocalAdminClient;
import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.audit.AuditEngine;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Login page servlet for Velo Web Admin.
 * Phase 1 provides a basic form-based authentication UI.
 */
public class AdminLoginServlet extends HttpServlet {

    private final AdminClient adminClient;

    public AdminLoginServlet(ServerConfiguration configuration) {
        this.adminClient = new LocalAdminClient(configuration);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        PrintWriter out = resp.getWriter();
        String contextPath = req.getContextPath();
        String error = req.getParameter("error");

        out.write("""
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Velo Web Admin - Login</title>
                <style>
                :root {
                    --bg: #0f1117; --surface: #1a1d27; --border: #2e3348;
                    --text: #e4e4e7; --text2: #9ca3af;
                    --primary: #6366f1; --primary-hover: #818cf8;
                    --danger: #ef4444; --radius: 12px;
                }
                * { margin:0; padding:0; box-sizing:border-box; }
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                       background: var(--bg); color: var(--text);
                       display: flex; justify-content: center; align-items: center;
                       min-height: 100vh; }
                .login-card { background: var(--surface); border: 1px solid var(--border);
                              border-radius: var(--radius); padding: 40px; width: 400px; }
                .login-title { font-size: 24px; font-weight: 700; text-align: center; margin-bottom: 8px; }
                .login-subtitle { font-size: 14px; color: var(--text2); text-align: center; margin-bottom: 32px; }
                .form-group { margin-bottom: 20px; }
                .form-label { display: block; font-size: 13px; color: var(--text2); margin-bottom: 6px; }
                .form-input { width: 100%%; padding: 10px 14px; background: var(--bg);
                              border: 1px solid var(--border); border-radius: 8px;
                              color: var(--text); font-size: 14px; outline: none; }
                .form-input:focus { border-color: var(--primary); }
                .btn-primary { width: 100%%; padding: 12px; background: var(--primary);
                               color: white; border: none; border-radius: 8px;
                               font-size: 14px; font-weight: 600; cursor: pointer; }
                .btn-primary:hover { background: var(--primary-hover); }
                .error-msg { background: rgba(239,68,68,0.1); color: var(--danger);
                             padding: 10px 14px; border-radius: 8px; font-size: 13px;
                             margin-bottom: 20px; display: %s; }
                .logo { color: var(--primary); font-size: 28px; font-weight: 700;
                        text-align: center; margin-bottom: 24px; }
                .logo span { color: var(--text); font-weight: 400; }
                </style>
                </head>
                <body>
                <div class="login-card">
                  <div class="logo">Velo<span> Admin</span></div>
                  <div class="login-title">Sign In</div>
                  <div class="login-subtitle">Enter your credentials to access the admin console</div>
                  <div class="error-msg">Invalid username or password.</div>
                  <form method="POST" action="%s/login">
                    <div class="form-group">
                      <label class="form-label">Username</label>
                      <input class="form-input" type="text" name="username" autocomplete="username" required autofocus>
                    </div>
                    <div class="form-group">
                      <label class="form-label">Password</label>
                      <input class="form-input" type="password" name="password" autocomplete="current-password" required>
                    </div>
                    <button type="submit" class="btn-primary">Sign In</button>
                  </form>
                </div>
                </body>
                </html>
                """.formatted(
                error != null ? "block" : "none",
                contextPath
        ));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        String contextPath = req.getContextPath();

        // Phase 1: simple built-in admin/admin check
        // Future phases will integrate LDAP, SAML, OIDC, MFA
        String clientIp = req.getRemoteAddr();
        if (adminClient.authenticate(username, password)) {
            req.getSession().setAttribute("velo.admin.authenticated", Boolean.TRUE);
            req.getSession().setAttribute("velo.admin.username", username);
            AuditEngine.instance().record(username, "LOGIN", "webadmin",
                    "Login successful", clientIp, true);
            resp.sendRedirect(contextPath + "/");
        } else {
            AuditEngine.instance().record(username != null ? username : "unknown",
                    "LOGIN", "webadmin", "Login failed", clientIp, false);
            resp.sendRedirect(contextPath + "/login?error=1");
        }
    }
}
