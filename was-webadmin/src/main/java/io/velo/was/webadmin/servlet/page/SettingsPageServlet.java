package io.velo.was.webadmin.servlet.page;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * System settings page.
 * Web Admin self-management: theme, session policy, license, and general configuration.
 */
public class SettingsPageServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public SettingsPageServlet(ServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        ServerConfiguration.Server server = configuration.getServer();
        String ctx = req.getContextPath();

        String body = """
                <div class="page-header">
                  <div>
                    <div class="page-title">Settings</div>
                    <div class="page-subtitle">Web Admin console configuration and preferences</div>
                  </div>
                </div>

                <div class="tabs" id="setTabs">
                  <div class="tab active" data-tab="general">General</div>
                  <div class="tab" data-tab="appearance">Appearance</div>
                  <div class="tab" data-tab="about">About</div>
                </div>

                <div class="tab-panel active" id="tab-general">
                  <div class="card">
                    <div class="card-title">Web Admin Configuration</div>
                    <table class="info-table" style="margin-top:12px;">
                      <tr><td>Context Path</td><td><code>%s</code></td></tr>
                      <tr><td>Status</td><td><span class="badge badge-success">Enabled</span></td></tr>
                      <tr><td>Session Timeout</td><td>%d seconds</td></tr>
                    </table>
                  </div>
                  <div class="card" style="margin-top:16px;">
                    <div class="card-title">Preferences</div>
                    <div class="form-group" style="margin-top:12px;">
                      <label class="form-label">Language</label>
                      <select class="form-select" style="width:200px;">
                        <option selected>Korean (한국어)</option>
                        <option>English</option>
                      </select>
                    </div>
                    <div class="form-group">
                      <label class="form-label">Auto-refresh Interval</label>
                      <select class="form-select" style="width:200px;">
                        <option>3 seconds</option>
                        <option selected>5 seconds</option>
                        <option>10 seconds</option>
                        <option>30 seconds</option>
                        <option>Disabled</option>
                      </select>
                    </div>
                    <div class="form-group">
                      <label class="form-label">Default Page</label>
                      <select class="form-select" style="width:200px;">
                        <option selected>Dashboard</option>
                        <option>Servers</option>
                        <option>Monitoring</option>
                        <option>Console</option>
                      </select>
                    </div>
                  </div>
                </div>

                <div class="tab-panel" id="tab-appearance">
                  <div class="card">
                    <div class="card-title">Theme</div>
                    <div style="display:flex;gap:16px;margin-top:16px;">
                      <div style="padding:16px 24px;border-radius:8px;border:2px solid var(--primary);
                           background:#0f1117;color:#e4e4e7;cursor:pointer;text-align:center;">
                        <div style="font-weight:600;">Dark</div>
                        <div style="font-size:12px;color:#9ca3af;">Current</div>
                      </div>
                      <div style="padding:16px 24px;border-radius:8px;border:1px solid var(--border);
                           background:#f8f9fa;color:#1a1d27;cursor:pointer;text-align:center;opacity:0.6;">
                        <div style="font-weight:600;">Light</div>
                        <div style="font-size:12px;">Planned</div>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="tab-panel" id="tab-about">
                  <div class="card">
                    <div class="card-title">About Velo Web Admin</div>
                    <table class="info-table" style="margin-top:12px;">
                      <tr><td>Product</td><td>Velo WAS</td></tr>
                      <tr><td>Version</td><td>0.1.0-SNAPSHOT</td></tr>
                      <tr><td>Web Admin Module</td><td>was-webadmin</td></tr>
                      <tr><td>Java</td><td>%s</td></tr>
                      <tr><td>OS</td><td>%s %s (%s)</td></tr>
                      <tr><td>Servlet Compatibility</td><td>Jakarta Servlet 6.1</td></tr>
                      <tr><td>Transport</td><td>Netty</td></tr>
                    </table>
                  </div>
                </div>

                <script>
                document.querySelectorAll('#setTabs .tab').forEach(function(tab){
                  tab.addEventListener('click', function(){
                    document.querySelectorAll('#setTabs .tab').forEach(function(t){t.classList.remove('active');});
                    document.querySelectorAll('.tab-panel').forEach(function(p){p.classList.remove('active');});
                    tab.classList.add('active');
                    document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
                  });
                });

                // localStorage persistence for preferences
                var PREF_KEY = 'velo-admin-prefs';
                function loadPrefs() {
                  try {
                    var prefs = JSON.parse(localStorage.getItem(PREF_KEY) || '{}');
                    var selects = document.querySelectorAll('#tab-general .form-select');
                    if (prefs.language && selects[0]) selects[0].value = prefs.language;
                    if (prefs.refreshInterval && selects[1]) selects[1].value = prefs.refreshInterval;
                    if (prefs.defaultPage && selects[2]) selects[2].value = prefs.defaultPage;
                  } catch(e) {}
                }
                function savePrefs() {
                  var selects = document.querySelectorAll('#tab-general .form-select');
                  var prefs = {
                    language: selects[0] ? selects[0].value : '',
                    refreshInterval: selects[1] ? selects[1].value : '',
                    defaultPage: selects[2] ? selects[2].value : ''
                  };
                  localStorage.setItem(PREF_KEY, JSON.stringify(prefs));
                  showToast('Preferences saved', 'success');
                }
                document.querySelectorAll('#tab-general .form-select').forEach(function(sel){
                  sel.addEventListener('change', savePrefs);
                });
                loadPrefs();
                </script>
                """.formatted(
                AdminPageLayout.escapeHtml(server.getWebAdmin().getContextPath()),
                server.getSession().getTimeoutSeconds(),
                System.getProperty("java.version"),
                System.getProperty("os.name"),
                System.getProperty("os.version"),
                System.getProperty("os.arch")
        );

        resp.getWriter().write(AdminPageLayout.page("Settings", server.getName(), server.getNodeId(),
                ctx, "settings", body));
    }
}
