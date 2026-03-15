package io.velo.was.webadmin.servlet;

import io.velo.was.config.ServerConfiguration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;

/**
 * Main dashboard page for Velo Web Admin.
 * Displays server status overview, resource summary, and quick-action links.
 */
public class AdminDashboardServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public AdminDashboardServlet(ServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        ServerConfiguration.Server server = configuration.getServer();
        String ctx = req.getContextPath();
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

        long uptimeSec = runtime.getUptime() / 1000;
        String uptime = "%dd %dh %dm %ds".formatted(
                uptimeSec / 86400, (uptimeSec % 86400) / 3600,
                (uptimeSec % 3600) / 60, uptimeSec % 60);

        long heapUsed = memory.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memory.getHeapMemoryUsage().getMax() / (1024 * 1024);
        int heapPct = heapMax > 0 ? (int) (heapUsed * 100 / heapMax) : 0;
        int cpus = Runtime.getRuntime().availableProcessors();
        int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();

        String body = """
                <div class="page-header">
                  <div>
                    <div class="page-title">Dashboard</div>
                    <div class="page-subtitle">System overview and quick actions</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn btn-sm" onclick="location.reload()">Refresh</button>
                  </div>
                </div>

                <div class="grid grid-4" style="margin-bottom:24px;">
                  <div class="card">
                    <div class="card-title">Server Status</div>
                    <div class="metric-value success" style="font-size:28px;">RUNNING</div>
                    <div style="font-size:13px;color:var(--text2);margin-top:4px;" id="dash-uptime">Uptime: %s</div>
                  </div>
                  <div class="card">
                    <div class="card-title">Heap Memory</div>
                    <div class="metric-value sm" id="dash-heap">%d / %d MB</div>
                    <div class="progress-bar" style="margin-top:8px;">
                      <div class="progress-fill" id="dash-heap-bar" style="width:%d%%;background:%s;"></div>
                    </div>
                  </div>
                  <div class="card">
                    <div class="card-title">Threads</div>
                    <div class="metric-value sm" id="dash-threads">%d</div>
                    <div style="font-size:13px;color:var(--text2);margin-top:4px;">CPUs: %d</div>
                  </div>
                  <div class="card">
                    <div class="card-title">Applications</div>
                    <div class="metric-value sm success" id="dash-apps">-</div>
                    <div style="font-size:13px;color:var(--text2);margin-top:4px;" id="dash-apps-status">Loading...</div>
                  </div>
                </div>

                <div class="grid grid-2">
                  <div class="card">
                    <div class="card-title">Server Information</div>
                    <table class="info-table">
                      <tr><td>Server Name</td><td>%s</td></tr>
                      <tr><td>Node ID</td><td>%s</td></tr>
                      <tr><td>Listen</td><td>%s:%d</td></tr>
                      <tr><td>TLS</td><td>%s</td></tr>
                      <tr><td>Java</td><td>%s</td></tr>
                      <tr><td>Hot Deploy</td><td>%s</td></tr>
                      <tr><td>Compression</td><td>%s</td></tr>
                    </table>
                  </div>
                  <div class="card">
                    <div class="card-title">Quick Actions</div>
                    <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:8px;">
                      <a href="%s/console" class="btn" style="justify-content:center;">Console</a>
                      <a href="%s/applications" class="btn" style="justify-content:center;">Apps</a>
                      <a href="%s/servers" class="btn" style="justify-content:center;">Servers</a>
                      <a href="%s/monitoring" class="btn" style="justify-content:center;">Monitor</a>
                      <a href="%s/diagnostics" class="btn" style="justify-content:center;">Diagnostics</a>
                      <a href="%s/resources" class="btn" style="justify-content:center;">Resources</a>
                      <a href="%s/security" class="btn" style="justify-content:center;">Security</a>
                      <a href="%s/history" class="btn" style="justify-content:center;">History</a>
                      <a href="%s/settings" class="btn" style="justify-content:center;">Settings</a>
                    </div>
                  </div>
                </div>

                <div class="card" style="margin-top:16px;">
                  <div class="card-title">Live Metrics</div>
                  <canvas id="dashChart" width="800" height="150" style="width:100%%;margin-top:12px;"></canvas>
                </div>

                <script>
                var CTX = '%s';
                var chartData = [];

                // Try SSE first, fallback to polling
                var evtSource = null;
                try {
                  evtSource = new EventSource(CTX + '/sse/status');
                  evtSource.addEventListener('status', function(e) {
                    try { updateDash(JSON.parse(e.data)); } catch(ex) {}
                  });
                  evtSource.onerror = function() {
                    evtSource.close();
                    evtSource = null;
                    setInterval(pollDash, 3000);
                  };
                } catch(ex) {
                  setInterval(pollDash, 3000);
                }

                function pollDash() {
                  fetch(CTX + '/api/status').then(function(r){return r.json();}).then(updateDash).catch(function(){});
                }

                function updateDash(d) {
                  var hm = Math.round(d.heapUsedBytes/(1024*1024));
                  var mx = Math.round(d.heapMaxBytes/(1024*1024));
                  var p = mx > 0 ? Math.round(hm*100/mx) : 0;
                  document.getElementById('dash-heap').textContent = hm + ' / ' + mx + ' MB';
                  document.getElementById('dash-threads').textContent = d.threadCount;
                  var bar = document.getElementById('dash-heap-bar');
                  bar.style.width = p + '%%';
                  bar.style.background = p > 80 ? 'var(--danger)' : p > 60 ? 'var(--warning)' : 'var(--primary)';
                  // Update uptime if available
                  if (d.uptimeMs) {
                    var s = Math.floor(d.uptimeMs / 1000);
                    var ut = Math.floor(s/86400) + 'd ' + Math.floor((s%%86400)/3600) + 'h '
                           + Math.floor((s%%3600)/60) + 'm ' + (s%%60) + 's';
                    var upEl = document.getElementById('dash-uptime');
                    if (upEl) upEl.textContent = 'Uptime: ' + ut;
                  }
                  chartData.push({heap: hm, threads: d.threadCount});
                  if (chartData.length > 60) chartData.shift();
                  drawDashChart();
                }

                function drawDashChart() {
                  var c = document.getElementById('dashChart');
                  if (!c) return;
                  var g = c.getContext('2d');
                  var w = c.width, h = c.height;
                  g.clearRect(0, 0, w, h);
                  if (chartData.length < 2) return;
                  var max = Math.max.apply(null, chartData.map(function(d){return d.heap;})) * 1.2 || 1;
                  // Draw heap line
                  g.strokeStyle = '#6366f1'; g.lineWidth = 2; g.beginPath();
                  chartData.forEach(function(d, i) {
                    var x = (i / (chartData.length - 1)) * w;
                    var y = h - (d.heap / max) * (h - 10) - 5;
                    if (i === 0) g.moveTo(x, y); else g.lineTo(x, y);
                  });
                  g.stroke();
                  g.fillStyle = 'rgba(99,102,241,0.08)';
                  g.lineTo(w, h); g.lineTo(0, h); g.fill();
                  // Draw thread count line
                  var maxT = Math.max.apply(null, chartData.map(function(d){return d.threads;})) * 1.2 || 1;
                  g.strokeStyle = '#22c55e'; g.lineWidth = 1.5; g.beginPath();
                  chartData.forEach(function(d, i) {
                    var x = (i / (chartData.length - 1)) * w;
                    var y = h - (d.threads / maxT) * (h - 10) - 5;
                    if (i === 0) g.moveTo(x, y); else g.lineTo(x, y);
                  });
                  g.stroke();
                  // Legend
                  g.fillStyle = '#6366f1'; g.fillRect(w-120, 8, 10, 10);
                  g.fillStyle = '#e4e4e7'; g.font = '10px sans-serif'; g.fillText('Heap', w-106, 17);
                  g.fillStyle = '#22c55e'; g.fillRect(w-60, 8, 10, 10);
                  g.fillStyle = '#e4e4e7'; g.fillText('Threads', w-46, 17);
                }
                // Load application count dynamically
                function loadDashApps() {
                  fetch(CTX + '/api/applications').then(function(r){return r.json();}).then(function(d) {
                    var apps = d.applications || [];
                    var el = document.getElementById('dash-apps');
                    var stEl = document.getElementById('dash-apps-status');
                    if (el) el.textContent = apps.length;
                    var running = apps.filter(function(a){return a.status==='RUNNING';}).length;
                    var stopped = apps.filter(function(a){return a.status==='STOPPED';}).length;
                    if (stEl) {
                      if (stopped > 0) stEl.textContent = running + ' running, ' + stopped + ' stopped';
                      else stEl.textContent = 'All running';
                    }
                  }).catch(function(){
                    var el = document.getElementById('dash-apps');
                    if (el) el.textContent = '-';
                  });
                }
                loadDashApps();

                // Apply saved theme
                var savedTheme = localStorage.getItem('velo-theme');
                if (savedTheme === 'light') {
                  var s = document.createElement('style');
                  s.textContent = ':root { --bg: #f8f9fa; --bg2: #ffffff; --bg3: #e9ecef; --text: #1a1d27; --text2: #495057; --text3: #868e96; --border: #dee2e6; --primary: #6366f1; --primary-hover: #4f46e5; --success: #22c55e; --warning: #f59e0b; --danger: #ef4444; }';
                  document.head.appendChild(s);
                }

                pollDash();
                </script>
                """.formatted(
                uptime,
                heapUsed, heapMax, heapPct,
                heapPct > 80 ? "var(--danger)" : heapPct > 60 ? "var(--warning)" : "var(--primary)",
                threadCount, cpus,
                h(server.getName()), h(server.getNodeId()),
                h(server.getListener().getHost()), server.getListener().getPort(),
                server.getTls().isEnabled() ? "Enabled" : "Disabled",
                System.getProperty("java.version"),
                server.getDeploy().isHotDeploy() ? "Enabled" : "Disabled",
                server.getCompression().isEnabled() ? "Enabled" : "Disabled",
                ctx, ctx, ctx, ctx, ctx, ctx, ctx, ctx, ctx,
                ctx
        );

        resp.getWriter().write(AdminPageLayout.page("Dashboard", server.getName(), server.getNodeId(),
                ctx, "dashboard", body));
    }

    private static String h(String s) { return AdminPageLayout.escapeHtml(s); }
}
