package io.velo.was.webadmin.servlet;

import io.velo.was.config.ServerConfiguration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;

/**
 * Main dashboard page for Velo Web Admin.
 * Displays server health overview, alert summary, resource overview,
 * deployment status, recent tasks, capacity trends, and quick actions.
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
        ThreadMXBean threadMX = ManagementFactory.getThreadMXBean();

        long uptimeSec = runtime.getUptime() / 1000;
        String uptime = "%dd %dh %dm %ds".formatted(
                uptimeSec / 86400, (uptimeSec % 86400) / 3600,
                (uptimeSec % 3600) / 60, uptimeSec % 60);

        long heapUsed = memory.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memory.getHeapMemoryUsage().getMax() / (1024 * 1024);
        int heapPct = heapMax > 0 ? (int) (heapUsed * 100 / heapMax) : 0;
        int cpus = Runtime.getRuntime().availableProcessors();
        int threadCount = threadMX.getThreadCount();
        int peakThreadCount = threadMX.getPeakThreadCount();
        long[] deadlocked = threadMX.findDeadlockedThreads();
        int deadlockedCount = deadlocked != null ? deadlocked.length : 0;

        // Determine initial health from server-side metrics
        String healthClass = "success";
        String healthLabel = "HEALTHY";
        if (deadlockedCount > 0 || heapPct > 90) {
            healthClass = "danger";
            healthLabel = "CRITICAL";
        } else if (heapPct > 80 || threadCount > 500) {
            healthClass = "warning";
            healthLabel = "WARNING";
        }

        String body = """
                <style>
                  .health-indicator { display:flex; align-items:center; gap:10px; }
                  .health-dot { width:12px; height:12px; border-radius:50%%; animation:pulse 2s infinite; }
                  .health-dot.success { background:var(--success); }
                  .health-dot.warning { background:var(--warning); }
                  .health-dot.danger { background:var(--danger); }
                  .alert-row { display:flex; align-items:center; gap:10px; padding:8px 12px;
                               border-radius:var(--radius-sm); margin-bottom:6px; font-size:13px; }
                  .alert-row.critical { background:var(--danger-bg); color:var(--danger); }
                  .alert-row.warning { background:var(--warning-bg); color:var(--warning); }
                  .alert-row.info { background:var(--info-bg); color:var(--info); }
                  .alert-row .badge { margin-left:auto; flex-shrink:0; }
                  .task-item { display:flex; align-items:center; gap:10px; padding:10px 0;
                               border-bottom:1px solid var(--border); font-size:13px; }
                  .task-item:last-child { border-bottom:none; }
                  .task-icon { width:28px; height:28px; border-radius:6px; display:flex;
                               align-items:center; justify-content:center; font-size:13px; flex-shrink:0; }
                  .task-meta { color:var(--text3); font-size:11px; margin-top:2px; }
                  .deploy-row { display:flex; align-items:center; gap:10px; padding:8px 0;
                                border-bottom:1px solid var(--border); font-size:13px; }
                  .deploy-row:last-child { border-bottom:none; }
                  .deploy-bar { flex:1; height:4px; background:var(--surface2); border-radius:2px; overflow:hidden; }
                  .deploy-bar-fill { height:100%%; border-radius:2px; transition:width 0.5s; }
                  .resource-item { display:flex; justify-content:space-between; align-items:center;
                                   padding:10px 0; border-bottom:1px solid var(--border); }
                  .resource-item:last-child { border-bottom:none; }
                  .resource-label { font-size:13px; color:var(--text2); }
                  .resource-value { font-size:14px; font-weight:600; }
                  .quick-action-btn { display:flex; flex-direction:column; align-items:center; gap:6px;
                                      padding:14px 8px; border-radius:var(--radius-sm); cursor:pointer;
                                      transition:all 0.15s; border:1px solid var(--border);
                                      background:var(--surface2); color:var(--text); font-size:12px;
                                      text-align:center; text-decoration:none; }
                  .quick-action-btn:hover { background:var(--surface3); border-color:var(--primary); color:var(--primary); }
                  .quick-action-btn .qa-icon { font-size:18px; }
                  .legend-item { display:inline-flex; align-items:center; gap:4px; margin-right:12px; font-size:11px; color:var(--text2); }
                  .legend-dot { width:8px; height:8px; border-radius:2px; }
                  .no-alerts { padding:20px; text-align:center; color:var(--text3); font-size:13px; }
                </style>

                <div class="page-header">
                  <div>
                    <div class="page-title" data-i18n="page.dashboard">Dashboard</div>
                    <div class="page-subtitle">System overview and quick actions</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn btn-sm" onclick="triggerGC()" id="btn-gc">Trigger GC</button>
                    <button class="btn btn-sm" onclick="location.reload()">Refresh</button>
                  </div>
                </div>

                <!-- Row 1: Health + Heap + Threads + Apps -->
                <div class="grid grid-4" style="margin-bottom:20px;">
                  <div class="card">
                    <div class="card-title">Server Health</div>
                    <div class="health-indicator" id="dash-health">
                      <span class="health-dot %s"></span>
                      <span class="metric-value %s" style="font-size:26px;" id="dash-health-label">%s</span>
                    </div>
                    <div style="font-size:13px;color:var(--text2);margin-top:4px;" id="dash-uptime">Uptime: %s</div>
                  </div>
                  <div class="card">
                    <div class="card-title">Heap Memory</div>
                    <div class="metric-value sm" id="dash-heap">%d / %d MB</div>
                    <div class="progress-bar" style="margin-top:8px;">
                      <div class="progress-fill" id="dash-heap-bar" style="width:%d%%;background:%s;"></div>
                    </div>
                    <div style="font-size:11px;color:var(--text3);margin-top:4px;" id="dash-heap-pct">%d%% used</div>
                  </div>
                  <div class="card">
                    <div class="card-title">Threads</div>
                    <div class="metric-value sm" id="dash-threads">%d</div>
                    <div style="font-size:13px;color:var(--text2);margin-top:4px;">
                      Peak: <span id="dash-peak-threads">%d</span> &middot; CPUs: %d
                    </div>
                  </div>
                  <div class="card">
                    <div class="card-title">Applications</div>
                    <div class="metric-value sm success" id="dash-apps">-</div>
                    <div style="font-size:13px;color:var(--text2);margin-top:4px;" id="dash-apps-status">Loading...</div>
                  </div>
                </div>

                <!-- Row 2: Alert Summary + Recent Tasks -->
                <div class="grid grid-2" style="margin-bottom:20px;">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Alert Summary</div>
                      <span class="badge badge-neutral" id="alert-count">0 alerts</span>
                    </div>
                    <div id="alert-list">
                      <div class="no-alerts">Checking system health...</div>
                    </div>
                  </div>
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Recent Tasks</div>
                      <a href="%s/history" class="btn btn-sm">View All</a>
                    </div>
                    <div id="recent-tasks">
                      <div class="no-alerts">Loading recent operations...</div>
                    </div>
                  </div>
                </div>

                <!-- Row 3: Resource Overview + Deployment Status -->
                <div class="grid grid-2" style="margin-bottom:20px;">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Resource Overview</div>
                      <a href="%s/resources" class="btn btn-sm">Details</a>
                    </div>
                    <div id="resource-overview">
                      <div class="resource-item">
                        <span class="resource-label">Heap Memory</span>
                        <span class="resource-value" id="res-heap">%d / %d MB</span>
                      </div>
                      <div class="resource-item">
                        <span class="resource-label">Thread Pools</span>
                        <span class="resource-value" id="res-threadpools">Loading...</span>
                      </div>
                      <div class="resource-item">
                        <span class="resource-label">Active Threads</span>
                        <span class="resource-value" id="res-threads">%d</span>
                      </div>
                      <div class="resource-item">
                        <span class="resource-label">Deadlocked Threads</span>
                        <span class="resource-value" id="res-deadlocked" style="color:%s;">%d</span>
                      </div>
                      <div class="resource-item">
                        <span class="resource-label">JDBC Pools</span>
                        <span class="resource-value" id="res-jdbc">Loading...</span>
                      </div>
                    </div>
                  </div>
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Deployment Status</div>
                      <a href="%s/applications" class="btn btn-sm">Manage</a>
                    </div>
                    <div id="deploy-status">
                      <div class="no-alerts">Loading deployment info...</div>
                    </div>
                  </div>
                </div>

                <!-- Row 4: Server Info + Quick Actions -->
                <div class="grid grid-2" style="margin-bottom:20px;">
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
                    <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-top:8px;">
                      <a href="%s/console" class="quick-action-btn"><span class="qa-icon">&gt;_</span>Console</a>
                      <a href="%s/applications" class="quick-action-btn"><span class="qa-icon">&#9678;</span>Apps</a>
                      <a href="%s/servers" class="quick-action-btn"><span class="qa-icon">&#10022;</span>Servers</a>
                      <a href="%s/monitoring" class="quick-action-btn"><span class="qa-icon">&#9672;</span>Monitor</a>
                      <a href="%s/diagnostics" class="quick-action-btn"><span class="qa-icon">&#9874;</span>Diagnostics</a>
                      <a href="%s/resources" class="quick-action-btn"><span class="qa-icon">&#10697;</span>Resources</a>
                      <div class="quick-action-btn" onclick="takeThreadDump()"><span class="qa-icon">&#9776;</span>Thread Dump</div>
                      <div class="quick-action-btn" onclick="triggerGC()"><span class="qa-icon">&#9851;</span>Trigger GC</div>
                      <a href="%s/security" class="quick-action-btn"><span class="qa-icon">&#9919;</span>Security</a>
                      <a href="%s/history" class="quick-action-btn"><span class="qa-icon">&#10710;</span>History</a>
                      <a href="%s/settings" class="quick-action-btn"><span class="qa-icon">&#9881;</span>Settings</a>
                      <div class="quick-action-btn" onclick="exportMetrics()"><span class="qa-icon">&#8615;</span>Export Metrics</div>
                    </div>
                  </div>
                </div>

                <!-- Row 5: Topology Overview -->
                <div class="card" style="margin-bottom:20px;">
                  <div class="card-header">
                    <div class="card-title">Server Topology</div>
                    <div class="btn-group">
                      <button class="btn btn-sm" onclick="refreshTopology()">Refresh</button>
                    </div>
                  </div>
                  <div id="topologyView" style="padding:16px;min-height:120px;">
                    <div style="display:flex;gap:24px;align-items:flex-start;flex-wrap:wrap;">
                      <div style="text-align:center;">
                        <div style="background:var(--surface2);border:2px solid var(--primary);border-radius:12px;padding:16px 24px;min-width:140px;">
                          <div style="font-size:20px;margin-bottom:4px;">&#9733;</div>
                          <div style="font-weight:700;font-size:14px;">Admin</div>
                          <div style="font-size:11px;color:var(--text2);">%s</div>
                          <div class="status-badge" style="margin-top:8px;font-size:11px;">Running</div>
                        </div>
                      </div>
                      <div style="display:flex;align-items:center;color:var(--text3);font-size:20px;">&#8594;</div>
                      <div id="topologyNodes" style="display:flex;gap:16px;flex-wrap:wrap;">
                        <div style="text-align:center;">
                          <div style="background:var(--surface2);border:1px solid var(--border);border-radius:12px;padding:16px 20px;min-width:120px;">
                            <div style="font-size:18px;margin-bottom:4px;">&#9632;</div>
                            <div style="font-weight:600;font-size:13px;">%s</div>
                            <div style="font-size:11px;color:var(--text2);">Node: %s</div>
                            <div class="status-badge" style="margin-top:8px;font-size:11px;">Active</div>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div style="margin-top:12px;font-size:12px;color:var(--text3);">
                      Domain topology shows server instances and their connections. Use Servers and Clusters pages for detailed management.
                    </div>
                  </div>
                </div>

                <!-- Row 6: Capacity Trend Chart -->
                <div class="card" style="margin-bottom:20px;">
                  <div class="card-header">
                    <div class="card-title">Capacity Trend</div>
                    <div>
                      <span class="legend-item"><span class="legend-dot" style="background:#6366f1;"></span>Heap (MB)</span>
                      <span class="legend-item"><span class="legend-dot" style="background:#22c55e;"></span>Threads</span>
                      <span class="legend-item"><span class="legend-dot" style="background:#f59e0b;"></span>CPU Load</span>
                    </div>
                  </div>
                  <canvas id="dashChart" width="800" height="180" style="width:100%%;margin-top:8px;"></canvas>
                </div>

                <script>
                var CTX = '%s';
                var chartData = [];

                // ── SSE / Polling ──────────────────────────────
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

                // ── Update Dashboard Metrics ───────────────────
                function updateDash(d) {
                  var hm = Math.round(d.heapUsedBytes/(1024*1024));
                  var mx = Math.round(d.heapMaxBytes/(1024*1024));
                  var p = mx > 0 ? Math.round(hm*100/mx) : 0;

                  document.getElementById('dash-heap').textContent = hm + ' / ' + mx + ' MB';
                  document.getElementById('dash-heap-pct').textContent = p + '%%' + ' used';
                  document.getElementById('dash-threads').textContent = d.threadCount;
                  document.getElementById('res-heap').textContent = hm + ' / ' + mx + ' MB';
                  document.getElementById('res-threads').textContent = d.threadCount;

                  var bar = document.getElementById('dash-heap-bar');
                  bar.style.width = p + '%%';
                  bar.style.background = p > 80 ? 'var(--danger)' : p > 60 ? 'var(--warning)' : 'var(--primary)';

                  // Update uptime
                  if (d.uptimeMs) {
                    var s = Math.floor(d.uptimeMs / 1000);
                    var ut = Math.floor(s/86400) + 'd ' + Math.floor((s%%86400)/3600) + 'h '
                           + Math.floor((s%%3600)/60) + 'm ' + (s%%60) + 's';
                    var upEl = document.getElementById('dash-uptime');
                    if (upEl) upEl.textContent = 'Uptime: ' + ut;
                  }

                  // Update health indicator
                  updateHealthIndicator(p, d.threadCount);

                  // Update alerts
                  updateAlerts(p, d.threadCount);

                  // Chart data - estimate CPU load from available data
                  var cpuEst = d.availableProcessors ? Math.min(100, Math.round(d.threadCount / (d.availableProcessors * 4) * 100)) : 0;
                  chartData.push({heap: hm, threads: d.threadCount, cpu: cpuEst, time: Date.now()});
                  if (chartData.length > 60) chartData.shift();
                  drawDashChart();
                }

                // ── Health Indicator ───────────────────────────
                function updateHealthIndicator(heapPct, threads) {
                  var dot = document.querySelector('#dash-health .health-dot');
                  var label = document.getElementById('dash-health-label');
                  if (!dot || !label) return;

                  dot.className = 'health-dot';
                  label.className = 'metric-value';
                  label.style.fontSize = '26px';

                  if (heapPct > 90 || window._deadlockedCount > 0) {
                    dot.classList.add('danger'); label.classList.add('danger');
                    label.textContent = 'CRITICAL';
                  } else if (heapPct > 80 || threads > 500) {
                    dot.classList.add('warning'); label.classList.add('warning');
                    label.textContent = 'WARNING';
                  } else {
                    dot.classList.add('success'); label.classList.add('success');
                    label.textContent = 'HEALTHY';
                  }
                }

                // ── Alert Summary ──────────────────────────────
                function updateAlerts(heapPct, threads) {
                  var alerts = [];
                  if (heapPct > 90) {
                    alerts.push({level:'critical', msg:'Heap usage at ' + heapPct + '%%' + ' - critical threshold exceeded', badge:'CRITICAL'});
                  } else if (heapPct > 80) {
                    alerts.push({level:'warning', msg:'Heap usage at ' + heapPct + '%%' + ' - approaching limit', badge:'WARNING'});
                  }
                  if (threads > 500) {
                    alerts.push({level:'warning', msg:'Thread count high: ' + threads + ' active threads', badge:'WARNING'});
                  }
                  if (window._deadlockedCount > 0) {
                    alerts.push({level:'critical', msg:window._deadlockedCount + ' deadlocked thread(s) detected', badge:'CRITICAL'});
                  }
                  if (heapPct > 60) {
                    alerts.push({level:'info', msg:'Heap usage above 60%% - consider monitoring', badge:'INFO'});
                  }

                  var el = document.getElementById('alert-list');
                  var countEl = document.getElementById('alert-count');
                  if (!el) return;

                  if (alerts.length === 0) {
                    el.innerHTML = '<div class="no-alerts" style="color:var(--success);">All systems healthy - no active alerts</div>';
                    countEl.textContent = '0 alerts';
                    countEl.className = 'badge badge-success';
                  } else {
                    var html = '';
                    for (var i = 0; i < alerts.length; i++) {
                      var a = alerts[i];
                      var badgeClass = a.level === 'critical' ? 'badge-danger' : a.level === 'warning' ? 'badge-warning' : 'badge-info';
                      html += '<div class="alert-row ' + a.level + '">'
                            + '<span>' + a.msg + '</span>'
                            + '<span class="badge ' + badgeClass + '">' + a.badge + '</span>'
                            + '</div>';
                    }
                    el.innerHTML = html;
                    var crits = alerts.filter(function(a){return a.level==='critical';}).length;
                    if (crits > 0) {
                      countEl.textContent = crits + ' critical, ' + (alerts.length - crits) + ' other';
                      countEl.className = 'badge badge-danger';
                    } else {
                      countEl.textContent = alerts.length + ' alert' + (alerts.length > 1 ? 's' : '');
                      countEl.className = 'badge badge-warning';
                    }
                  }
                }

                // ── Capacity Trend Chart ───────────────────────
                function drawDashChart() {
                  var c = document.getElementById('dashChart');
                  if (!c) return;
                  var g = c.getContext('2d');
                  var w = c.width, h = c.height;
                  var pad = 30;
                  g.clearRect(0, 0, w, h);
                  if (chartData.length < 2) return;

                  // Draw grid lines
                  g.strokeStyle = 'rgba(255,255,255,0.05)';
                  g.lineWidth = 1;
                  for (var i = 0; i < 4; i++) {
                    var gy = pad + (i / 3) * (h - pad * 2);
                    g.beginPath(); g.moveTo(0, gy); g.lineTo(w, gy); g.stroke();
                  }

                  // Heap line
                  var maxHeap = Math.max.apply(null, chartData.map(function(d){return d.heap;})) * 1.2 || 1;
                  g.strokeStyle = '#6366f1'; g.lineWidth = 2; g.beginPath();
                  chartData.forEach(function(d, i) {
                    var x = (i / (chartData.length - 1)) * w;
                    var y = h - pad - (d.heap / maxHeap) * (h - pad * 2);
                    if (i === 0) g.moveTo(x, y); else g.lineTo(x, y);
                  });
                  g.stroke();
                  // Heap fill
                  g.fillStyle = 'rgba(99,102,241,0.06)';
                  g.lineTo(w, h - pad); g.lineTo(0, h - pad); g.fill();

                  // Thread line
                  var maxT = Math.max.apply(null, chartData.map(function(d){return d.threads;})) * 1.2 || 1;
                  g.strokeStyle = '#22c55e'; g.lineWidth = 1.5; g.beginPath();
                  chartData.forEach(function(d, i) {
                    var x = (i / (chartData.length - 1)) * w;
                    var y = h - pad - (d.threads / maxT) * (h - pad * 2);
                    if (i === 0) g.moveTo(x, y); else g.lineTo(x, y);
                  });
                  g.stroke();

                  // CPU load line
                  g.strokeStyle = '#f59e0b'; g.lineWidth = 1.5; g.setLineDash([4,3]); g.beginPath();
                  chartData.forEach(function(d, i) {
                    var x = (i / (chartData.length - 1)) * w;
                    var y = h - pad - (d.cpu / 100) * (h - pad * 2);
                    if (i === 0) g.moveTo(x, y); else g.lineTo(x, y);
                  });
                  g.stroke();
                  g.setLineDash([]);

                  // Y-axis labels
                  g.fillStyle = '#6b7280'; g.font = '10px sans-serif';
                  g.fillText(Math.round(maxHeap) + ' MB', 4, pad - 4);
                  g.fillText('0', 4, h - pad + 12);

                  // Right axis - CPU
                  g.fillText('100%%', w - 32, pad - 4);
                  g.fillText('0%%', w - 20, h - pad + 12);
                }

                // ── Load Applications (Deployment Status) ──────
                function loadDashApps() {
                  fetch(CTX + '/api/applications').then(function(r){return r.json();}).then(function(d) {
                    var apps = d.applications || [];
                    var el = document.getElementById('dash-apps');
                    var stEl = document.getElementById('dash-apps-status');
                    if (el) el.textContent = apps.length;

                    var running = apps.filter(function(a){return a.status==='RUNNING';}).length;
                    var stopped = apps.filter(function(a){return a.status==='STOPPED';}).length;
                    var failed = apps.filter(function(a){return a.status!=='RUNNING' && a.status!=='STOPPED';}).length;

                    if (stEl) {
                      var parts = [];
                      if (running > 0) parts.push(running + ' running');
                      if (stopped > 0) parts.push(stopped + ' stopped');
                      if (failed > 0) parts.push(failed + ' failed');
                      stEl.textContent = parts.length > 0 ? parts.join(', ') : 'No applications';
                    }

                    // Deployment status widget
                    var dsEl = document.getElementById('deploy-status');
                    if (dsEl && apps.length > 0) {
                      var html = '';
                      var total = apps.length;
                      html += '<div style="display:flex;gap:16px;margin-bottom:12px;font-size:12px;">'
                            + '<span style="color:var(--success);">&#9679; Running: ' + running + '</span>'
                            + '<span style="color:var(--text3);">&#9679; Stopped: ' + stopped + '</span>'
                            + (failed > 0 ? '<span style="color:var(--danger);">&#9679; Failed: ' + failed + '</span>' : '')
                            + '</div>';
                      apps.forEach(function(a) {
                        var color = a.status === 'RUNNING' ? 'var(--success)' : a.status === 'STOPPED' ? 'var(--text3)' : 'var(--danger)';
                        var badgeCls = a.status === 'RUNNING' ? 'badge-success' : a.status === 'STOPPED' ? 'badge-neutral' : 'badge-danger';
                        var pct = a.status === 'RUNNING' ? 100 : a.status === 'STOPPED' ? 0 : 50;
                        html += '<div class="deploy-row">'
                              + '<span style="flex:0 0 140px;overflow:hidden;text-overflow:ellipsis;">' + escHtml(a.name) + '</span>'
                              + '<span class="badge ' + badgeCls + '">' + a.status + '</span>'
                              + '<div class="deploy-bar"><div class="deploy-bar-fill" style="width:' + pct + '%%;background:' + color + ';"></div></div>'
                              + '<span style="font-size:11px;color:var(--text3);width:50px;text-align:right;">' + (a.contextPath || '/') + '</span>'
                              + '</div>';
                      });
                      dsEl.innerHTML = html;
                    } else if (dsEl) {
                      dsEl.innerHTML = '<div class="no-alerts">No applications deployed</div>';
                    }
                  }).catch(function(){
                    var el = document.getElementById('dash-apps');
                    if (el) el.textContent = '-';
                  });
                }

                // ── Load Recent Tasks (Audit Events) ───────────
                function loadRecentTasks() {
                  fetch(CTX + '/api/audit?limit=5').then(function(r){return r.json();}).then(function(d) {
                    var events = d.events || [];
                    var el = document.getElementById('recent-tasks');
                    if (!el) return;
                    if (events.length === 0) {
                      el.innerHTML = '<div class="no-alerts">No recent operations recorded</div>';
                      return;
                    }
                    var html = '';
                    events.forEach(function(e) {
                      var iconBg = e.success ? 'var(--success-bg)' : 'var(--danger-bg)';
                      var iconColor = e.success ? 'var(--success)' : 'var(--danger)';
                      var icon = e.success ? '&#10003;' : '&#10007;';
                      var ago = timeAgo(e.timestamp);
                      html += '<div class="task-item">'
                            + '<div class="task-icon" style="background:' + iconBg + ';color:' + iconColor + ';">' + icon + '</div>'
                            + '<div style="flex:1;min-width:0;">'
                            + '<div style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">' + escHtml(e.action) + ': ' + escHtml(e.target || '') + '</div>'
                            + '<div class="task-meta">' + escHtml(e.user || 'system') + ' &middot; ' + ago + '</div>'
                            + '</div>'
                            + '<span class="badge ' + (e.success ? 'badge-success' : 'badge-danger') + '">' + (e.success ? 'OK' : 'FAIL') + '</span>'
                            + '</div>';
                    });
                    el.innerHTML = html;
                  }).catch(function(){});
                }

                // ── Load Resource Overview ─────────────────────
                function loadResources() {
                  // Thread pools
                  fetch(CTX + '/api/threadpools').then(function(r){return r.json();}).then(function(d) {
                    var pools = d.threadPools || [];
                    var el = document.getElementById('res-threadpools');
                    if (el) {
                      if (pools.length === 0) {
                        el.textContent = 'None configured';
                      } else {
                        var totalActive = 0, totalMax = 0;
                        pools.forEach(function(p) { totalActive += p.activeCount; totalMax += p.maxPoolSize; });
                        el.textContent = pools.length + ' pool(s), ' + totalActive + '/' + totalMax + ' active';
                      }
                    }
                  }).catch(function(){
                    var el = document.getElementById('res-threadpools');
                    if (el) el.textContent = 'N/A';
                  });

                  // JDBC / DataSources - uses thread dump info as proxy or direct endpoint
                  fetch(CTX + '/api/resources').then(function(r){return r.json();}).then(function(d) {
                    var el = document.getElementById('res-jdbc');
                    if (el) {
                      if (d.resources && d.resources.dataSources) {
                        el.textContent = Object.keys(d.resources.dataSources).length + ' data source(s)';
                      } else {
                        el.textContent = 'None configured';
                      }
                    }
                  }).catch(function(){
                    var el = document.getElementById('res-jdbc');
                    if (el) el.textContent = 'None configured';
                  });

                  // Deadlocked threads
                  fetch(CTX + '/api/threads').then(function(r){return r.json();}).then(function(d) {
                    window._deadlockedCount = d.deadlockedCount || 0;
                    var el = document.getElementById('res-deadlocked');
                    if (el) {
                      el.textContent = d.deadlockedCount || 0;
                      el.style.color = d.deadlockedCount > 0 ? 'var(--danger)' : 'var(--success)';
                    }
                    var peakEl = document.getElementById('dash-peak-threads');
                    if (peakEl && d.peakThreadCount) peakEl.textContent = d.peakThreadCount;
                  }).catch(function(){});
                }

                // ── Quick Action: Thread Dump ──────────────────
                function takeThreadDump() {
                  fetch(CTX + '/api/execute', {
                    method:'POST', headers:{'Content-Type':'application/json'},
                    body:JSON.stringify({command:'threaddump'})
                  }).then(function(r){return r.json();}).then(function(d) {
                    if (d.success) {
                      var win = window.open('', '_blank');
                      win.document.write('<pre style="background:#0f1117;color:#e4e4e7;padding:20px;font-size:12px;font-family:monospace;">'
                        + escHtml(d.message) + '</pre>');
                      win.document.title = 'Thread Dump';
                      showToast('Thread dump generated', 'success');
                    } else {
                      showToast('Failed: ' + d.message, 'error');
                    }
                  }).catch(function(){ showToast('Thread dump request failed', 'error'); });
                }

                // ── Quick Action: Trigger GC ───────────────────
                function triggerGC() {
                  fetch(CTX + '/api/execute', {
                    method:'POST', headers:{'Content-Type':'application/json'},
                    body:JSON.stringify({command:'gc'})
                  }).then(function(r){return r.json();}).then(function(d) {
                    showToast(d.success ? 'GC triggered successfully' : 'GC failed: ' + d.message, d.success ? 'success' : 'error');
                  }).catch(function(){ showToast('GC request failed', 'error'); });
                }

                // ── Quick Action: Export Metrics ───────────────
                function exportMetrics() {
                  fetch(CTX + '/api/monitoring').then(function(r){return r.json();}).then(function(d) {
                    var blob = new Blob([JSON.stringify(d, null, 2)], {type:'application/json'});
                    var url = URL.createObjectURL(blob);
                    var a = document.createElement('a');
                    a.href = url; a.download = 'velo-metrics-' + new Date().toISOString().slice(0,19).replace(/:/g,'-') + '.json';
                    document.body.appendChild(a); a.click(); document.body.removeChild(a);
                    URL.revokeObjectURL(url);
                    showToast('Metrics exported', 'success');
                  }).catch(function(){ showToast('Export failed', 'error'); });
                }

                // ── Topology ──────────────────────────────────
                function refreshTopology() {
                  fetch(CTX + '/api/servers').then(function(r){return r.json();}).then(function(d) {
                    var servers = d.servers || [];
                    var container = document.getElementById('topologyNodes');
                    if (servers.length === 0) return;
                    var html = '';
                    servers.forEach(function(s) {
                      var statusColor = s.status === 'RUNNING' ? 'var(--success)' : s.status === 'STOPPED' ? 'var(--danger)' : 'var(--warning)';
                      var statusBg = s.status === 'RUNNING' ? 'var(--success-bg)' : s.status === 'STOPPED' ? 'var(--danger-bg)' : 'var(--warning-bg)';
                      html += '<div style="text-align:center;">'
                        + '<div style="background:var(--surface2);border:1px solid var(--border);border-radius:12px;padding:16px 20px;min-width:120px;">'
                        + '<div style="font-size:18px;margin-bottom:4px;">&#9632;</div>'
                        + '<div style="font-weight:600;font-size:13px;">' + escHtml(s.name || '-') + '</div>'
                        + '<div style="font-size:11px;color:var(--text2);">Port: ' + (s.port || '-') + '</div>'
                        + '<div class="status-badge" style="margin-top:8px;font-size:11px;background:' + statusBg + ';color:' + statusColor + ';">' + (s.status || 'Unknown') + '</div>'
                        + '</div></div>';
                    });
                    container.innerHTML = html;
                  }).catch(function(){});
                }

                // ── Utilities ──────────────────────────────────
                function escHtml(s) {
                  if (!s) return '';
                  var d = document.createElement('div'); d.textContent = s; return d.innerHTML;
                }
                function timeAgo(ts) {
                  if (!ts) return '';
                  var diff = Date.now() - new Date(ts).getTime();
                  if (diff < 60000) return 'just now';
                  if (diff < 3600000) return Math.floor(diff/60000) + 'm ago';
                  if (diff < 86400000) return Math.floor(diff/3600000) + 'h ago';
                  return Math.floor(diff/86400000) + 'd ago';
                }

                // ── Initialize ─────────────────────────────────
                window._deadlockedCount = %d;
                loadDashApps();
                loadRecentTasks();
                loadResources();
                pollDash();

                // Auto-refresh recent tasks and resources every 30s
                setInterval(function() {
                  loadRecentTasks();
                  loadResources();
                }, 30000);

                // Apply saved theme
                var savedTheme = localStorage.getItem('velo-theme');
                if (savedTheme === 'light') {
                  var s = document.createElement('style');
                  s.textContent = ':root { --bg: #f8f9fa; --bg2: #ffffff; --bg3: #e9ecef; --text: #1a1d27; --text2: #495057; --text3: #868e96; --border: #dee2e6; --primary: #6366f1; --primary-hover: #4f46e5; --success: #22c55e; --warning: #f59e0b; --danger: #ef4444; }';
                  document.head.appendChild(s);
                }
                </script>
                """.formatted(
                // Health indicator
                healthClass, healthClass, healthLabel,
                // Uptime
                uptime,
                // Heap
                heapUsed, heapMax, heapPct,
                heapPct > 80 ? "var(--danger)" : heapPct > 60 ? "var(--warning)" : "var(--primary)",
                heapPct,
                // Threads
                threadCount, peakThreadCount, cpus,
                // Alert Summary link
                ctx,
                // Resource Overview link
                ctx,
                // Resource initial values
                heapUsed, heapMax, threadCount,
                deadlockedCount > 0 ? "var(--danger)" : "var(--success)", deadlockedCount,
                // Deployment Status link
                ctx,
                // Server Info
                h(server.getName()), h(server.getNodeId()),
                h(server.getListener().getHost()), server.getListener().getPort(),
                server.getTls().isEnabled() ? "Enabled" : "Disabled",
                System.getProperty("java.version"),
                server.getDeploy().isHotDeploy() ? "Enabled" : "Disabled",
                server.getCompression().isEnabled() ? "Enabled" : "Disabled",
                // Quick Actions links
                ctx, ctx, ctx, ctx, ctx, ctx, ctx, ctx, ctx,
                // Topology
                h(server.getName()),
                h(server.getName()), h(server.getNodeId()),
                // Chart CTX
                ctx,
                // Initial deadlocked count for JS
                deadlockedCount
        );

        resp.getWriter().write(AdminPageLayout.page("Dashboard", server.getName(), server.getNodeId(),
                ctx, "dashboard", body));
    }

    private static String h(String s) { return AdminPageLayout.escapeHtml(s); }
}
