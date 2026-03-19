package io.velo.was.webadmin.servlet;

import io.velo.was.admin.client.AdminClient;
import io.velo.was.admin.client.LocalAdminClient;
import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.audit.AuditEngine;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Login page servlet for Velo Web Admin.
 * Provides form-based authentication with CSRF protection, rate limiting,
 * password strength indicator, session timeout warnings, MFA support structure,
 * initial setup wizard, safe mode diagnostics, and enhanced UI.
 */
public class AdminLoginServlet extends HttpServlet {

    private static final DateTimeFormatter LOGIN_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final String VERSION = "0.1.0";

    private final AdminClient adminClient;
    private final int sessionTimeoutSeconds;

    public AdminLoginServlet(ServerConfiguration configuration) {
        this(configuration, new LocalAdminClient(configuration));
    }

    public AdminLoginServlet(ServerConfiguration configuration, AdminClient adminClient) {
        this.adminClient = adminClient;
        this.sessionTimeoutSeconds = configuration.getServer().getSession().getTimeoutSeconds();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        PrintWriter out = resp.getWriter();
        String contextPath = req.getContextPath();
        String error = req.getParameter("error");
        String locked = req.getParameter("locked");
        String loggedOut = req.getParameter("logout");

        // Ensure CSRF token exists in session
        HttpSession session = req.getSession(true);
        if (session.getAttribute(AdminAuthFilter.CSRF_TOKEN_ATTR) == null) {
            byte[] tokenBytes = new byte[32];
            new java.security.SecureRandom().nextBytes(tokenBytes);
            String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
            session.setAttribute(AdminAuthFilter.CSRF_TOKEN_ATTR, token);
        }
        String csrfToken = (String) session.getAttribute(AdminAuthFilter.CSRF_TOKEN_ATTR);

        // Check if IP is locked out
        String clientIp = req.getRemoteAddr();
        boolean isLocked = AdminAuthFilter.isLockedOut(clientIp);
        long lockoutRemaining = AdminAuthFilter.getLockoutRemainingSeconds(clientIp);

        // Determine which message to show
        String errorDisplay = "none";
        String errorMessage = "";
        String errorClass = "error-msg";
        if (isLocked || "1".equals(locked)) {
            errorDisplay = "block";
            errorMessage = "Account locked due to too many failed attempts. Try again in "
                    + lockoutRemaining + " seconds.";
            errorClass = "error-msg lockout-msg";
        } else if (error != null) {
            errorDisplay = "block";
            errorMessage = "Invalid username or password.";
        } else if ("1".equals(loggedOut)) {
            errorDisplay = "block";
            errorMessage = "You have been logged out successfully.";
            errorClass = "error-msg info-msg";
        }

        // Check for last login info in session
        String lastLoginDisplay = "none";
        String lastLoginInfo = "";
        if (session.getAttribute("velo.admin.lastLogin") != null) {
            lastLoginDisplay = "block";
            lastLoginInfo = (String) session.getAttribute("velo.admin.lastLogin");
            session.removeAttribute("velo.admin.lastLogin");
        }

        // Gather server status for safe mode diagnostics
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadMX = ManagementFactory.getThreadMXBean();
        long heapUsed = memory.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memory.getHeapMemoryUsage().getMax() / (1024 * 1024);
        int threadCount = threadMX.getThreadCount();
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long uptimeSec = uptimeMs / 1000;
        String uptimeStr = "%dd %dh %dm %ds".formatted(
                uptimeSec / 86400, (uptimeSec % 86400) / 3600,
                (uptimeSec % 3600) / 60, uptimeSec % 60);
        int heapPct = heapMax > 0 ? (int) (heapUsed * 100 / heapMax) : 0;

        out.write(renderPage(
                errorDisplay, lastLoginDisplay, errorClass, errorMessage,
                lastLoginInfo, contextPath, csrfToken,
                isLocked ? "disabled" : "",
                isLocked ? lockoutRemaining : 0,
                sessionTimeoutSeconds,
                heapUsed, heapMax, heapPct, threadCount, uptimeStr
        ));
    }

    private String renderPage(
            String errorDisplay, String lastLoginDisplay, String errorClass,
            String errorMessage, String lastLoginInfo, String contextPath,
            String csrfToken, String disabledAttr, long lockoutSec,
            int sessionTimeout, long heapUsed, long heapMax, int heapPct,
            int threadCount, String uptimeStr) {

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Velo Web Admin - Login</title>
                <style>
                :root {
                    --bg: #0f1117; --surface: #1a1d27; --surface2: #22253a; --border: #2e3348;
                    --text: #e4e4e7; --text2: #9ca3af; --text3: #6b7280;
                    --primary: #6366f1; --primary-hover: #818cf8; --primary-dim: rgba(99,102,241,0.15);
                    --danger: #ef4444; --success: #22c55e; --warning: #f59e0b;
                    --info: #3b82f6; --radius: 12px;
                }
                * { margin:0; padding:0; box-sizing:border-box; }
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                       background: var(--bg); color: var(--text);
                       display: flex; flex-direction: column; justify-content: center; align-items: center;
                       min-height: 100vh; }

                /* Login card animation */
                @keyframes cardSlideIn {
                    from { opacity: 0; transform: translateY(20px); }
                    to { opacity: 1; transform: translateY(0); }
                }
                @keyframes fadeIn {
                    from { opacity: 0; }
                    to { opacity: 1; }
                }
                @keyframes pulse {
                    0%%, 100%% { opacity: 1; }
                    50%% { opacity: 0.5; }
                }

                .login-wrapper { display: flex; flex-direction: column; align-items: center; gap: 16px;
                                 animation: fadeIn 0.5s ease-out; }
                .login-card { background: var(--surface); border: 1px solid var(--border);
                              border-radius: var(--radius); padding: 40px; width: 420px;
                              animation: cardSlideIn 0.6s ease-out; }
                .login-title { font-size: 24px; font-weight: 700; text-align: center; margin-bottom: 8px; }
                .login-subtitle { font-size: 14px; color: var(--text2); text-align: center; margin-bottom: 32px; }
                .form-group { margin-bottom: 20px; }
                .form-label { display: block; font-size: 13px; color: var(--text2); margin-bottom: 6px; }
                .form-input { width: 100%%; padding: 10px 14px; background: var(--bg);
                              border: 1px solid var(--border); border-radius: 8px;
                              color: var(--text); font-size: 14px; outline: none;
                              transition: border-color 0.2s; }
                .form-input:focus { border-color: var(--primary); box-shadow: 0 0 0 3px var(--primary-dim); }
                .btn-primary { width: 100%%; padding: 12px; background: var(--primary);
                               color: white; border: none; border-radius: 8px;
                               font-size: 14px; font-weight: 600; cursor: pointer;
                               transition: background 0.2s, transform 0.1s; }
                .btn-primary:hover { background: var(--primary-hover); }
                .btn-primary:active { transform: scale(0.98); }
                .btn-primary:disabled { background: #4b5563; cursor: not-allowed; transform: none; }
                .btn-secondary { width: 100%%; padding: 10px; background: transparent;
                                 color: var(--text2); border: 1px solid var(--border); border-radius: 8px;
                                 font-size: 13px; cursor: pointer; transition: background 0.2s; }
                .btn-secondary:hover { background: var(--surface2); }
                .error-msg { background: rgba(239,68,68,0.1); color: var(--danger);
                             padding: 10px 14px; border-radius: 8px; font-size: 13px;
                             margin-bottom: 20px; display: %s;
                             border: 1px solid rgba(239,68,68,0.2); }
                .lockout-msg { background: rgba(245,158,11,0.1); color: var(--warning);
                               border-color: rgba(245,158,11,0.2); }
                .info-msg { background: rgba(59,130,246,0.1); color: var(--info);
                            border-color: rgba(59,130,246,0.2); }
                .success-msg { background: rgba(34,197,94,0.1); color: var(--success);
                               border-color: rgba(34,197,94,0.2); }

                /* Branding */
                .branding { text-align: center; margin-bottom: 28px; }
                .branding .logo-text { font-size: 32px; font-weight: 800; color: var(--primary);
                                       letter-spacing: -0.5px; }
                .branding .logo-text span { color: var(--text); font-weight: 400; }
                .branding .version-tag { display: inline-block; font-size: 11px; color: var(--text3);
                                         background: var(--surface2); border: 1px solid var(--border);
                                         border-radius: 4px; padding: 2px 8px; margin-top: 6px; }

                .password-strength { height: 4px; border-radius: 2px; margin-top: 6px;
                                     background: var(--border); overflow: hidden; }
                .password-strength-bar { height: 100%%; width: 0; border-radius: 2px;
                                         transition: width 0.3s, background 0.3s; }
                .password-strength-label { font-size: 11px; color: var(--text2); margin-top: 4px; }
                .last-login { background: rgba(34,197,94,0.1); color: var(--success);
                              padding: 10px 14px; border-radius: 8px; font-size: 13px;
                              margin-bottom: 20px; display: %s;
                              border: 1px solid rgba(34,197,94,0.2); }
                .session-warning { display: none; position: fixed; top: 20px; right: 20px;
                                   background: rgba(245,158,11,0.15); color: var(--warning);
                                   padding: 14px 20px; border-radius: 8px; font-size: 13px;
                                   border: 1px solid rgba(245,158,11,0.3); z-index: 9999; }

                /* Remember me & links row */
                .form-options { display: flex; justify-content: space-between; align-items: center;
                                margin-bottom: 20px; font-size: 13px; }
                .form-options label { display: flex; align-items: center; gap: 6px; color: var(--text2);
                                      cursor: pointer; }
                .form-options input[type="checkbox"] { accent-color: var(--primary); width: 14px; height: 14px; }
                .form-options a { color: var(--primary); text-decoration: none; font-size: 12px; }
                .form-options a:hover { color: var(--primary-hover); text-decoration: underline; }

                /* MFA step */
                .mfa-step { display: none; }
                .mfa-step.active { display: block; }
                .mfa-code-inputs { display: flex; gap: 8px; justify-content: center; margin: 20px 0; }
                .mfa-code-inputs input { width: 44px; height: 52px; text-align: center; font-size: 20px;
                                         font-weight: 700; background: var(--bg); border: 1px solid var(--border);
                                         border-radius: 8px; color: var(--text); outline: none;
                                         transition: border-color 0.2s; }
                .mfa-code-inputs input:focus { border-color: var(--primary);
                                                box-shadow: 0 0 0 3px var(--primary-dim); }
                .mfa-info { font-size: 12px; color: var(--text3); text-align: center; margin-top: 12px; }
                .mfa-info a { color: var(--primary); text-decoration: none; }

                /* Forgot password modal */
                .modal-overlay { display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.6);
                                 z-index: 10000; justify-content: center; align-items: center; }
                .modal-overlay.active { display: flex; }
                .modal-card { background: var(--surface); border: 1px solid var(--border);
                              border-radius: var(--radius); padding: 28px; width: 400px;
                              animation: cardSlideIn 0.3s ease-out; }
                .modal-card h3 { font-size: 18px; margin-bottom: 12px; }
                .modal-card p { font-size: 13px; color: var(--text2); line-height: 1.6; margin-bottom: 16px; }
                .modal-card code { background: var(--bg); padding: 2px 8px; border-radius: 4px;
                                   font-size: 12px; color: var(--primary); }
                .modal-close { float: right; background: none; border: none; color: var(--text2);
                               font-size: 18px; cursor: pointer; padding: 4px; }
                .modal-close:hover { color: var(--text); }

                /* Server status indicator */
                .server-status { display: flex; align-items: center; gap: 8px; font-size: 12px;
                                 color: var(--text3); margin-top: 16px; }
                .status-dot { width: 8px; height: 8px; border-radius: 50%%; background: var(--success);
                              animation: pulse 2s infinite; }
                .status-dot.offline { background: var(--danger); }

                /* Safe mode panel */
                .safe-mode-link { font-size: 11px; color: var(--text3); text-decoration: none;
                                  margin-top: 8px; cursor: pointer; }
                .safe-mode-link:hover { color: var(--text2); }
                .safe-mode-panel { display: none; background: var(--surface); border: 1px solid var(--border);
                                   border-radius: var(--radius); padding: 24px; width: 420px;
                                   margin-top: 12px; animation: cardSlideIn 0.3s ease-out; }
                .safe-mode-panel.active { display: block; }
                .safe-mode-panel h4 { font-size: 14px; font-weight: 600; margin-bottom: 16px;
                                       color: var(--warning); }
                .diag-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
                .diag-item { background: var(--bg); border: 1px solid var(--border); border-radius: 8px;
                             padding: 12px; }
                .diag-item .diag-label { font-size: 11px; color: var(--text3); text-transform: uppercase;
                                          letter-spacing: 0.5px; margin-bottom: 4px; }
                .diag-item .diag-value { font-size: 16px; font-weight: 600; }
                .diag-item .diag-sub { font-size: 11px; color: var(--text3); margin-top: 2px; }
                .diag-bar { height: 4px; background: var(--border); border-radius: 2px; margin-top: 6px;
                            overflow: hidden; }
                .diag-bar-fill { height: 100%%; border-radius: 2px; transition: width 0.3s; }

                /* Setup wizard overlay */
                .wizard-overlay { display: none; position: fixed; inset: 0; background: rgba(0,0,0,0.7);
                                  z-index: 20000; justify-content: center; align-items: center; }
                .wizard-overlay.active { display: flex; }
                .wizard-card { background: var(--surface); border: 1px solid var(--border);
                               border-radius: 16px; padding: 40px; width: 480px;
                               animation: cardSlideIn 0.4s ease-out; }
                .wizard-steps { display: flex; gap: 8px; margin-bottom: 32px; }
                .wizard-step-dot { width: 32px; height: 4px; border-radius: 2px; background: var(--border);
                                   transition: background 0.3s; }
                .wizard-step-dot.active { background: var(--primary); }
                .wizard-step-dot.done { background: var(--success); }
                .wizard-title { font-size: 22px; font-weight: 700; margin-bottom: 8px; }
                .wizard-desc { font-size: 14px; color: var(--text2); line-height: 1.6; margin-bottom: 28px; }
                .wizard-content { margin-bottom: 28px; }
                .wizard-actions { display: flex; gap: 12px; justify-content: flex-end; }
                .wizard-actions button { padding: 10px 24px; border-radius: 8px; font-size: 13px;
                                          font-weight: 600; cursor: pointer; transition: all 0.2s; }
                .wizard-btn-skip { background: transparent; color: var(--text2); border: 1px solid var(--border); }
                .wizard-btn-skip:hover { background: var(--surface2); }
                .wizard-btn-next { background: var(--primary); color: white; border: none; }
                .wizard-btn-next:hover { background: var(--primary-hover); }

                /* Theme selection cards */
                .theme-cards { display: flex; gap: 16px; }
                .theme-card { flex: 1; padding: 20px; border: 2px solid var(--border); border-radius: 12px;
                              text-align: center; cursor: pointer; transition: all 0.2s; }
                .theme-card:hover { border-color: var(--text3); }
                .theme-card.selected { border-color: var(--primary); background: var(--primary-dim); }
                .theme-card .theme-icon { font-size: 28px; margin-bottom: 8px; }
                .theme-card .theme-name { font-size: 14px; font-weight: 600; }
                .theme-card .theme-desc { font-size: 12px; color: var(--text2); margin-top: 4px; }

                /* Wizard password inputs */
                .wizard-input { width: 100%%; padding: 10px 14px; background: var(--bg);
                                border: 1px solid var(--border); border-radius: 8px;
                                color: var(--text); font-size: 14px; outline: none;
                                margin-bottom: 12px; transition: border-color 0.2s; }
                .wizard-input:focus { border-color: var(--primary); box-shadow: 0 0 0 3px var(--primary-dim); }
                .wizard-input-label { font-size: 12px; color: var(--text2); margin-bottom: 6px; display: block; }

                .wizard-complete-icon { font-size: 48px; text-align: center; margin-bottom: 16px;
                                        color: var(--success); }
                </style>
                </head>
                <body>
                <div class="login-wrapper">
                  <div class="login-card" id="loginCard">
                    <!-- Branding -->
                    <div class="branding">
                      <div class="logo-text">Velo<span> WAS</span></div>
                      <div class="version-tag">v%s</div>
                    </div>

                    <!-- Step 1: Credentials -->
                    <div id="credentialsStep">
                      <div class="login-title">Sign In</div>
                      <div class="login-subtitle">Enter your credentials to access the admin console</div>
                      <div class="%s" id="errorMsg">%s</div>
                      <div class="last-login">Last login: %s</div>
                      <form method="POST" action="%s/login" id="loginForm">
                        <input type="hidden" name="_csrf" value="%s">
                        <div class="form-group">
                          <label class="form-label">Username</label>
                          <input class="form-input" type="text" name="username" id="usernameInput"
                                 autocomplete="username" required autofocus>
                        </div>
                        <div class="form-group">
                          <label class="form-label">Password</label>
                          <input class="form-input" type="password" name="password" id="passwordInput"
                                 autocomplete="current-password" required>
                          <div class="password-strength"><div class="password-strength-bar" id="strengthBar"></div></div>
                          <div class="password-strength-label" id="strengthLabel"></div>
                        </div>
                        <div class="form-options">
                          <label><input type="checkbox" name="remember" id="rememberMe"> Remember me</label>
                          <a href="#" id="forgotPasswordLink">Forgot Password?</a>
                        </div>
                        <button type="submit" class="btn-primary" id="submitBtn" %s>Sign In</button>
                      </form>
                    </div>

                    <!-- Step 2: MFA Verification -->
                    <div id="mfaStep" class="mfa-step">
                      <div class="login-title">Two-Factor Authentication</div>
                      <div class="login-subtitle">Enter the 6-digit code from your authenticator app</div>
                      <div class="mfa-code-inputs" id="mfaCodeInputs">
                        <input type="text" maxlength="1" inputmode="numeric" pattern="[0-9]" data-idx="0">
                        <input type="text" maxlength="1" inputmode="numeric" pattern="[0-9]" data-idx="1">
                        <input type="text" maxlength="1" inputmode="numeric" pattern="[0-9]" data-idx="2">
                        <input type="text" maxlength="1" inputmode="numeric" pattern="[0-9]" data-idx="3">
                        <input type="text" maxlength="1" inputmode="numeric" pattern="[0-9]" data-idx="4">
                        <input type="text" maxlength="1" inputmode="numeric" pattern="[0-9]" data-idx="5">
                      </div>
                      <div id="mfaError" class="error-msg" style="display:none;"></div>
                      <button type="button" class="btn-primary" id="mfaVerifyBtn">Verify</button>
                      <div class="mfa-info">
                        Having trouble? <a href="#" id="mfaBackLink">Sign in with a different account</a>
                      </div>
                    </div>
                  </div>

                  <!-- Server status indicator -->
                  <div class="server-status" id="serverStatus">
                    <div class="status-dot" id="statusDot"></div>
                    <span id="statusText">Server online</span>
                  </div>

                  <!-- Safe mode entry -->
                  <a class="safe-mode-link" id="safeModeLink">Safe Mode</a>

                  <!-- Safe mode diagnostics panel -->
                  <div class="safe-mode-panel" id="safeModePanel">
                    <h4>Safe Mode Diagnostics</h4>
                    <div class="diag-grid">
                      <div class="diag-item">
                        <div class="diag-label">Status</div>
                        <div class="diag-value" id="diagStatus" style="color:var(--success);">Online</div>
                      </div>
                      <div class="diag-item">
                        <div class="diag-label">Uptime</div>
                        <div class="diag-value" id="diagUptime">%s</div>
                      </div>
                      <div class="diag-item">
                        <div class="diag-label">Heap Usage</div>
                        <div class="diag-value" id="diagHeap">%dMB / %dMB</div>
                        <div class="diag-bar">
                          <div class="diag-bar-fill" id="diagHeapBar"
                               style="width:%d%%;background:%s;"></div>
                        </div>
                        <div class="diag-sub" id="diagHeapPct">%d%% used</div>
                      </div>
                      <div class="diag-item">
                        <div class="diag-label">Thread Count</div>
                        <div class="diag-value" id="diagThreads">%d</div>
                        <div class="diag-sub">active threads</div>
                      </div>
                    </div>
                    <div style="margin-top:12px;text-align:center;">
                      <button class="btn-secondary" id="diagRefreshBtn"
                              style="width:auto;padding:6px 16px;font-size:12px;">Refresh</button>
                    </div>
                  </div>
                </div>

                <!-- Forgot password modal -->
                <div class="modal-overlay" id="forgotModal">
                  <div class="modal-card">
                    <button class="modal-close" id="forgotModalClose">&times;</button>
                    <h3>Reset Admin Password</h3>
                    <p>Admin passwords cannot be reset through the web interface for security reasons.
                       To reset the admin password, use the CLI tool:</p>
                    <p><code>velo-admin reset-password --user admin</code></p>
                    <p>This command must be run on the server host with appropriate system privileges.
                       Contact your system administrator if you need assistance.</p>
                    <div style="text-align:right;margin-top:16px;">
                      <button class="btn-secondary" id="forgotModalOk"
                              style="width:auto;padding:8px 20px;">OK</button>
                    </div>
                  </div>
                </div>

                <!-- Setup wizard overlay -->
                <div class="wizard-overlay" id="wizardOverlay">
                  <div class="wizard-card">
                    <div class="wizard-steps" id="wizardSteps">
                      <div class="wizard-step-dot active" data-step="0"></div>
                      <div class="wizard-step-dot" data-step="1"></div>
                      <div class="wizard-step-dot" data-step="2"></div>
                      <div class="wizard-step-dot" data-step="3"></div>
                    </div>

                    <!-- Wizard Step 1: Welcome -->
                    <div class="wizard-page" id="wizardPage0">
                      <div class="wizard-title">Welcome to Velo Web Admin</div>
                      <div class="wizard-desc">
                        Let's configure your admin environment. This setup wizard will guide you through
                        the essential configuration steps to get started. You can always change these
                        settings later from the admin console.
                      </div>
                      <div class="wizard-actions">
                        <button class="wizard-btn-skip" onclick="wizardSkip()">Skip Setup</button>
                        <button class="wizard-btn-next" onclick="wizardNext()">Get Started</button>
                      </div>
                    </div>

                    <!-- Wizard Step 2: Password change -->
                    <div class="wizard-page" id="wizardPage1" style="display:none;">
                      <div class="wizard-title">Change Default Password</div>
                      <div class="wizard-desc">
                        For security, change the default admin password. You can skip this step and
                        change it later from Settings.
                      </div>
                      <div class="wizard-content">
                        <label class="wizard-input-label">New Password</label>
                        <input type="password" class="wizard-input" id="wizardNewPw"
                               placeholder="Enter new password">
                        <label class="wizard-input-label">Confirm Password</label>
                        <input type="password" class="wizard-input" id="wizardConfirmPw"
                               placeholder="Confirm new password">
                        <div id="wizardPwError" style="font-size:12px;color:var(--danger);display:none;"></div>
                      </div>
                      <div class="wizard-actions">
                        <button class="wizard-btn-skip" onclick="wizardNext()">Skip</button>
                        <button class="wizard-btn-next" onclick="wizardSavePassword()">Change Password</button>
                      </div>
                    </div>

                    <!-- Wizard Step 3: Theme selection -->
                    <div class="wizard-page" id="wizardPage2" style="display:none;">
                      <div class="wizard-title">Choose Your Theme</div>
                      <div class="wizard-desc">
                        Select the visual theme for the admin console.
                      </div>
                      <div class="wizard-content">
                        <div class="theme-cards">
                          <div class="theme-card selected" id="themeDark" onclick="selectTheme('dark')">
                            <div class="theme-icon">&#9790;</div>
                            <div class="theme-name">Dark</div>
                            <div class="theme-desc">Easy on the eyes for extended sessions</div>
                          </div>
                          <div class="theme-card" id="themeLight" onclick="selectTheme('light')">
                            <div class="theme-icon">&#9788;</div>
                            <div class="theme-name">Light</div>
                            <div class="theme-desc">Clean and bright for daytime use</div>
                          </div>
                        </div>
                      </div>
                      <div class="wizard-actions">
                        <button class="wizard-btn-skip" onclick="wizardNext()">Skip</button>
                        <button class="wizard-btn-next" onclick="wizardNext()">Next</button>
                      </div>
                    </div>

                    <!-- Wizard Step 4: Complete -->
                    <div class="wizard-page" id="wizardPage3" style="display:none;">
                      <div class="wizard-complete-icon">&#10003;</div>
                      <div class="wizard-title" style="text-align:center;">Setup Complete</div>
                      <div class="wizard-desc" style="text-align:center;">
                        Your admin environment is configured. You can always change settings later
                        from the admin console Settings page.
                      </div>
                      <div class="wizard-actions" style="justify-content:center;">
                        <button class="wizard-btn-next" onclick="wizardFinish()">Start Using Velo Admin</button>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="session-warning" id="sessionWarning">
                  Your session will expire soon. Please save your work.
                </div>

                <script>
                // ---- Password strength indicator ----
                var pwInput = document.getElementById('passwordInput');
                var strengthBar = document.getElementById('strengthBar');
                var strengthLabel = document.getElementById('strengthLabel');
                pwInput.addEventListener('input', function() {
                  var pw = pwInput.value;
                  var score = 0;
                  if (pw.length >= 8) score++;
                  if (pw.length >= 12) score++;
                  if (/[A-Z]/.test(pw)) score++;
                  if (/[0-9]/.test(pw)) score++;
                  if (/[^A-Za-z0-9]/.test(pw)) score++;
                  var pct = Math.min(100, score * 20);
                  var colors = ['#ef4444','#ef4444','#f59e0b','#f59e0b','#22c55e','#22c55e'];
                  var labels = ['','Weak','Weak','Fair','Good','Strong'];
                  strengthBar.style.width = pct + '%%';
                  strengthBar.style.background = colors[score] || '#ef4444';
                  strengthLabel.textContent = labels[score] || '';
                });

                // ---- Lockout countdown ----
                var lockoutSec = %d;
                if (lockoutSec > 0) {
                  var submitBtn = document.getElementById('submitBtn');
                  var interval = setInterval(function() {
                    lockoutSec--;
                    if (lockoutSec <= 0) {
                      clearInterval(interval);
                      submitBtn.disabled = false;
                      submitBtn.textContent = 'Sign In';
                      var errDiv = document.querySelector('.lockout-msg');
                      if (errDiv) errDiv.style.display = 'none';
                    } else {
                      submitBtn.textContent = 'Locked (' + lockoutSec + 's)';
                    }
                  }, 1000);
                }

                // ---- Session timeout warning ----
                var sessionTimeout = %d;
                if (sessionTimeout > 0 && window.location.pathname.indexOf('/login') < 0) {
                  var warningDiv = document.getElementById('sessionWarning');
                  setTimeout(function() {
                    warningDiv.style.display = 'block';
                  }, Math.max(0, (sessionTimeout - 60)) * 1000);
                }

                // ---- Remember me (save username) ----
                var rememberMe = document.getElementById('rememberMe');
                var usernameInput = document.getElementById('usernameInput');
                var savedUser = localStorage.getItem('velo-remember-user');
                if (savedUser) {
                  usernameInput.value = savedUser;
                  rememberMe.checked = true;
                }

                // ---- MFA support ----
                var mfaEnabled = localStorage.getItem('velo-mfa-enabled') === 'true';
                var loginForm = document.getElementById('loginForm');
                var credentialsStep = document.getElementById('credentialsStep');
                var mfaStep = document.getElementById('mfaStep');

                loginForm.addEventListener('submit', function(e) {
                  // Save remember me preference
                  if (rememberMe.checked) {
                    localStorage.setItem('velo-remember-user', usernameInput.value);
                  } else {
                    localStorage.removeItem('velo-remember-user');
                  }

                  if (!mfaEnabled) {
                    // MFA not enabled - submit form normally
                    return;
                  }

                  // MFA enabled - intercept form, show MFA step
                  e.preventDefault();

                  // First validate credentials via fetch
                  var formData = new FormData(loginForm);
                  formData.append('mfa_check', '1');
                  fetch(loginForm.action, { method: 'POST', body: formData, redirect: 'manual' })
                    .then(function(response) {
                      // If credentials are valid, the server would redirect (302)
                      // If invalid, we reload with error
                      if (response.type === 'opaqueredirect' || response.status === 0 || response.redirected) {
                        // Credentials accepted - show MFA step
                        credentialsStep.style.display = 'none';
                        mfaStep.classList.add('active');
                        var firstMfaInput = document.querySelector('#mfaCodeInputs input');
                        if (firstMfaInput) firstMfaInput.focus();
                      } else {
                        // Credentials failed - submit normally to show error
                        loginForm.submit();
                      }
                    })
                    .catch(function() {
                      // Network error - submit normally
                      loginForm.submit();
                    });
                });

                // MFA code input handling
                var mfaInputs = document.querySelectorAll('#mfaCodeInputs input');
                mfaInputs.forEach(function(input, idx) {
                  input.addEventListener('input', function(e) {
                    var val = e.target.value.replace(/[^0-9]/g, '');
                    e.target.value = val;
                    if (val && idx < mfaInputs.length - 1) {
                      mfaInputs[idx + 1].focus();
                    }
                  });
                  input.addEventListener('keydown', function(e) {
                    if (e.key === 'Backspace' && !e.target.value && idx > 0) {
                      mfaInputs[idx - 1].focus();
                    }
                  });
                  input.addEventListener('paste', function(e) {
                    e.preventDefault();
                    var pasted = (e.clipboardData || window.clipboardData).getData('text').replace(/[^0-9]/g, '');
                    for (var i = 0; i < Math.min(pasted.length, 6); i++) {
                      mfaInputs[i].value = pasted[i];
                    }
                    var focusIdx = Math.min(pasted.length, 5);
                    mfaInputs[focusIdx].focus();
                  });
                });

                // MFA verify button
                document.getElementById('mfaVerifyBtn').addEventListener('click', function() {
                  var code = '';
                  mfaInputs.forEach(function(inp) { code += inp.value; });
                  if (code.length !== 6) {
                    var errDiv = document.getElementById('mfaError');
                    errDiv.textContent = 'Please enter all 6 digits.';
                    errDiv.style.display = 'block';
                    return;
                  }
                  // Phase 1: Accept any 6-digit code as valid
                  document.getElementById('mfaError').style.display = 'none';
                  loginForm.submit();
                });

                // MFA back link
                document.getElementById('mfaBackLink').addEventListener('click', function(e) {
                  e.preventDefault();
                  mfaStep.classList.remove('active');
                  credentialsStep.style.display = 'block';
                  mfaInputs.forEach(function(inp) { inp.value = ''; });
                });

                // Show MFA disabled info if applicable
                if (!mfaEnabled) {
                  var errorMsgDiv = document.getElementById('errorMsg');
                  if (errorMsgDiv.style.display === 'none' || errorMsgDiv.textContent.trim() === '') {
                    // Only show MFA info if no other message is showing
                    // We don't show it by default - only on first sign-in after feature awareness
                  }
                }

                // ---- Forgot password modal ----
                document.getElementById('forgotPasswordLink').addEventListener('click', function(e) {
                  e.preventDefault();
                  document.getElementById('forgotModal').classList.add('active');
                });
                document.getElementById('forgotModalClose').addEventListener('click', function() {
                  document.getElementById('forgotModal').classList.remove('active');
                });
                document.getElementById('forgotModalOk').addEventListener('click', function() {
                  document.getElementById('forgotModal').classList.remove('active');
                });
                document.getElementById('forgotModal').addEventListener('click', function(e) {
                  if (e.target === this) this.classList.remove('active');
                });

                // ---- Safe mode ----
                var safeModePanel = document.getElementById('safeModePanel');
                document.getElementById('safeModeLink').addEventListener('click', function() {
                  safeModePanel.classList.toggle('active');
                });
                document.getElementById('diagRefreshBtn').addEventListener('click', function() {
                  fetch('%s/api/status')
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                      if (data.heapUsed) document.getElementById('diagHeap').textContent =
                        data.heapUsed + 'MB / ' + data.heapMax + 'MB';
                      if (data.threadCount) document.getElementById('diagThreads').textContent =
                        data.threadCount;
                      if (data.uptime) document.getElementById('diagUptime').textContent = data.uptime;
                      document.getElementById('diagStatus').textContent = 'Online';
                      document.getElementById('diagStatus').style.color = 'var(--success)';
                    })
                    .catch(function() {
                      document.getElementById('diagStatus').textContent = 'Error';
                      document.getElementById('diagStatus').style.color = 'var(--danger)';
                    });
                });

                // ---- Server status check ----
                function checkServerStatus() {
                  var dot = document.getElementById('statusDot');
                  var text = document.getElementById('statusText');
                  fetch('%s/api/status', { method: 'GET' })
                    .then(function(r) {
                      if (r.ok) {
                        dot.classList.remove('offline');
                        text.textContent = 'Server online';
                      } else {
                        dot.classList.add('offline');
                        text.textContent = 'Server error';
                      }
                    })
                    .catch(function() {
                      dot.classList.add('offline');
                      text.textContent = 'Server unreachable';
                    });
                }
                checkServerStatus();
                setInterval(checkServerStatus, 30000);

                // ---- Setup wizard ----
                var currentWizardStep = 0;
                var totalWizardSteps = 4;
                var selectedTheme = 'dark';

                function showSetupWizard() {
                  if (localStorage.getItem('velo-setup-complete') !== 'true') {
                    document.getElementById('wizardOverlay').classList.add('active');
                  }
                }

                function updateWizardDots() {
                  var dots = document.querySelectorAll('.wizard-step-dot');
                  dots.forEach(function(dot, i) {
                    dot.classList.remove('active', 'done');
                    if (i < currentWizardStep) dot.classList.add('done');
                    else if (i === currentWizardStep) dot.classList.add('active');
                  });
                }

                function wizardNext() {
                  document.getElementById('wizardPage' + currentWizardStep).style.display = 'none';
                  currentWizardStep++;
                  if (currentWizardStep >= totalWizardSteps) {
                    wizardFinish();
                    return;
                  }
                  document.getElementById('wizardPage' + currentWizardStep).style.display = 'block';
                  updateWizardDots();
                }

                function wizardSkip() {
                  localStorage.setItem('velo-setup-complete', 'true');
                  document.getElementById('wizardOverlay').classList.remove('active');
                }

                function wizardFinish() {
                  localStorage.setItem('velo-setup-complete', 'true');
                  if (selectedTheme) {
                    localStorage.setItem('velo-theme', selectedTheme);
                  }
                  document.getElementById('wizardOverlay').classList.remove('active');
                }

                function wizardSavePassword() {
                  var newPw = document.getElementById('wizardNewPw').value;
                  var confirmPw = document.getElementById('wizardConfirmPw').value;
                  var errDiv = document.getElementById('wizardPwError');
                  var username = document.getElementById('usernameInput').value || 'admin';
                  var currentPassword = document.getElementById('passwordInput').value || 'admin';
                  var csrfToken = document.querySelector('input[name="_csrf"]').value;

                  if (!newPw || newPw.length < 8) {
                    errDiv.textContent = 'Password must be at least 8 characters.';
                    errDiv.style.display = 'block';
                    return;
                  }
                  if (newPw !== confirmPw) {
                    errDiv.textContent = 'Passwords do not match.';
                    errDiv.style.display = 'block';
                    return;
                  }
                  fetch('%s/login?action=change-default-password', {
                    method: 'POST',
                    headers: {
                      'Content-Type': 'application/json',
                      'X-CSRF-Token': csrfToken
                    },
                    body: JSON.stringify({
                      username: username,
                      currentPassword: currentPassword,
                      password: newPw
                    })
                  }).then(function(r) {
                    return r.json();
                  }).then(function(d) {
                    if (!d.success) {
                      errDiv.textContent = d.message || 'Failed to change password.';
                      errDiv.style.display = 'block';
                      return;
                    }
                    errDiv.style.display = 'none';
                    document.getElementById('usernameInput').value = username;
                    document.getElementById('passwordInput').value = newPw;
                    wizardNext();
                  }).catch(function() {
                    errDiv.textContent = 'Failed to change password.';
                    errDiv.style.display = 'block';
                  });
                }

                function selectTheme(theme) {
                  selectedTheme = theme;
                  document.getElementById('themeDark').classList.toggle('selected', theme === 'dark');
                  document.getElementById('themeLight').classList.toggle('selected', theme === 'light');
                }

                // Show wizard on first visit
                showSetupWizard();
                </script>
                </body>
                </html>
                """.formatted(
                // CSS error-msg display
                errorDisplay,
                // CSS last-login display
                lastLoginDisplay,
                // Branding version
                VERSION,
                // Error class and message
                errorClass,
                errorMessage,
                // Last login
                lastLoginInfo,
                // Form action contextPath
                contextPath,
                // CSRF token
                csrfToken,
                // Submit button disabled
                disabledAttr,
                // Safe mode diagnostics
                uptimeStr,
                heapUsed,
                heapMax,
                heapPct,
                heapPct > 80 ? "var(--danger)" : heapPct > 60 ? "var(--warning)" : "var(--success)",
                heapPct,
                threadCount,
                // Lockout countdown
                lockoutSec,
                // Session timeout
                sessionTimeout,
                // Safe mode refresh fetch URL
                contextPath,
                // Wizard password change URL contextPath
                contextPath,
                // Server status fetch URL
                contextPath
        );
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String action = req.getParameter("action");
        if ("change-default-password".equals(action)) {
            handleChangeDefaultPassword(req, resp);
            return;
        }

        String username = req.getParameter("username");
        String password = req.getParameter("password");
        String contextPath = req.getContextPath();
        String clientIp = req.getRemoteAddr();

        // Check rate limiting / lockout
        if (AdminAuthFilter.isLockedOut(clientIp)) {
            resp.sendRedirect(contextPath + "/login?locked=1");
            return;
        }

        // Validate CSRF token
        HttpSession session = req.getSession(true);
        String sessionToken = (String) session.getAttribute(AdminAuthFilter.CSRF_TOKEN_ATTR);
        String requestToken = req.getParameter(AdminAuthFilter.CSRF_FORM_FIELD);
        if (requestToken == null) {
            requestToken = req.getHeader(AdminAuthFilter.CSRF_HEADER);
        }
        if (sessionToken == null || !sessionToken.equals(requestToken)) {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.setContentType("text/plain; charset=UTF-8");
            resp.getWriter().write("CSRF token validation failed");
            return;
        }

        // Phase 1: simple built-in admin/admin check
        // Future phases will integrate LDAP, SAML, OIDC, MFA
        if (adminClient.authenticate(username, password)) {
            AdminAuthFilter.clearFailedAttempts(clientIp);
            session.setAttribute("velo.admin.authenticated", Boolean.TRUE);
            session.setAttribute("velo.admin.username", username);
            session.setAttribute("velo.admin.loginTime", Instant.now().toString());

            // Record last login for display
            session.setAttribute("velo.admin.lastLogin",
                    LOGIN_TIME_FMT.format(Instant.now()) + " from " + clientIp);

            // Generate fresh CSRF token after login
            session.removeAttribute(AdminAuthFilter.CSRF_TOKEN_ATTR);

            AuditEngine.instance().record(username, "LOGIN", "webadmin",
                    "Login successful", clientIp, true);
            resp.sendRedirect(contextPath + "/");
        } else {
            AdminAuthFilter.recordFailedAttempt(clientIp);
            AuditEngine.instance().record(username != null ? username : "unknown",
                    "LOGIN", "webadmin", "Login failed", clientIp, false);

            if (AdminAuthFilter.isLockedOut(clientIp)) {
                resp.sendRedirect(contextPath + "/login?locked=1");
            } else {
                resp.sendRedirect(contextPath + "/login?error=1");
            }
        }
    }

    private void handleChangeDefaultPassword(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String clientIp = req.getRemoteAddr();
        if (AdminAuthFilter.isLockedOut(clientIp)) {
            writeJson(resp, 429,
                    "{\"success\":false,\"message\":\"Account locked due to too many failed attempts.\"}");
            return;
        }

        HttpSession session = req.getSession(true);
        String sessionToken = (String) session.getAttribute(AdminAuthFilter.CSRF_TOKEN_ATTR);
        String requestToken = req.getHeader(AdminAuthFilter.CSRF_HEADER);
        if (requestToken == null) {
            requestToken = req.getParameter(AdminAuthFilter.CSRF_FORM_FIELD);
        }
        if (sessionToken == null || !sessionToken.equals(requestToken)) {
            writeJson(resp, HttpServletResponse.SC_FORBIDDEN,
                    "{\"success\":false,\"message\":\"CSRF token validation failed\"}");
            return;
        }

        String body = readBody(req);
        String username = extractJsonValue(body, "username");
        String currentPassword = extractJsonValue(body, "currentPassword");
        String newPassword = extractJsonValue(body, "password");
        if (username == null || username.isBlank() || currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            writeJson(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"message\":\"Username, current password, and new password are required\"}");
            return;
        }
        if (newPassword.length() < 8) {
            writeJson(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "{\"success\":false,\"message\":\"Password must be at least 8 characters.\"}");
            return;
        }
        if (!adminClient.authenticate(username, currentPassword)) {
            AdminAuthFilter.recordFailedAttempt(clientIp);
            AuditEngine.instance().record(username, "CHANGE_PASSWORD", "login-wizard",
                    "Password change rejected due to invalid current credentials", clientIp, false);
            writeJson(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "{\"success\":false,\"message\":\"Current credentials are invalid.\"}");
            return;
        }

        adminClient.changePassword(username, newPassword);
        AdminAuthFilter.clearFailedAttempts(clientIp);
        AuditEngine.instance().record(username, "CHANGE_PASSWORD", "login-wizard",
                "Password changed from login wizard", clientIp, true);
        writeJson(resp, HttpServletResponse.SC_OK,
                "{\"success\":true,\"message\":\"Password changed successfully.\"}");
    }

    private static void writeJson(HttpServletResponse resp, int status, String body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(body);
    }

    private static String readBody(HttpServletRequest req) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }

    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int qStart = json.indexOf('"', colon + 1);
        if (qStart < 0) return null;
        int qEnd = json.indexOf('"', qStart + 1);
        if (qEnd < 0) return null;
        return json.substring(qStart + 1, qEnd);
    }
}
