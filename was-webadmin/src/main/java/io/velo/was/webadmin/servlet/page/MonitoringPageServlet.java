package io.velo.was.webadmin.servlet.page;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * Monitoring page.
 * Displays real-time metrics, charts, logs, events, and alerts.
 * Auto-refreshes via SSE / polling.
 */
public class MonitoringPageServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public MonitoringPageServlet(ServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        ServerConfiguration.Server server = configuration.getServer();
        String ctx = req.getContextPath();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        long heapUsed = memory.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memory.getHeapMemoryUsage().getMax() / (1024 * 1024);
        int heapPct = heapMax > 0 ? (int)(heapUsed * 100 / heapMax) : 0;
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        long uptimeSec = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        String body = """
                <div class="page-header">
                  <div>
                    <div class="page-title">Monitoring</div>
                    <div class="page-subtitle">Real-time metrics, logs, and system events</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn btn-sm" id="autoRefreshBtn" onclick="toggleAutoRefresh()">Auto-Refresh: ON</button>
                    <button class="btn btn-sm" onclick="location.reload()">Refresh Now</button>
                    <button class="btn btn-sm">Export CSV</button>
                  </div>
                </div>

                <div class="tabs" id="monTabs">
                  <div class="tab active" data-tab="overview">Overview</div>
                  <div class="tab" data-tab="metrics">Metrics</div>
                  <div class="tab" data-tab="logs">Logs</div>
                  <div class="tab" data-tab="events">Events</div>
                  <div class="tab" data-tab="alerts">Alerts</div>
                </div>

                <div class="tab-panel active" id="tab-overview">
                  <div class="grid grid-4" style="margin-bottom:20px;">
                    <div class="card">
                      <div class="card-title">Uptime</div>
                      <div class="metric-value sm" id="m-uptime">%s</div>
                    </div>
                    <div class="card">
                      <div class="card-title">Heap Memory</div>
                      <div class="metric-value sm" id="m-heap">%d / %d MB</div>
                      <div class="progress-bar" style="margin-top:8px;">
                        <div class="progress-fill" id="m-heap-bar" style="width:%d%%;background:%s;"></div>
                      </div>
                    </div>
                    <div class="card">
                      <div class="card-title">Threads</div>
                      <div class="metric-value sm" id="m-threads">%d</div>
                    </div>
                    <div class="card">
                      <div class="card-title">CPUs</div>
                      <div class="metric-value sm">%d</div>
                    </div>
                  </div>

                  <div class="grid grid-2">
                    <div class="card">
                      <div class="card-title">Request Metrics (from MetricsCollector)</div>
                      <div id="metricsJson" style="font-family:monospace;font-size:12px;white-space:pre-wrap;
                           color:var(--text2);max-height:300px;overflow-y:auto;padding:12px;
                           background:var(--bg);border-radius:8px;margin-top:8px;"></div>
                    </div>
                    <div class="card">
                      <div class="card-title">Live Metrics Chart</div>
                      <canvas id="metricsChart" width="500" height="200" style="width:100%%;margin-top:8px;"></canvas>
                    </div>
                  </div>
                </div>

                <div class="tab-panel" id="tab-metrics">
                  <div class="card">
                    <div class="card-title">Detailed Metrics Snapshot</div>
                    <div id="detailedMetrics" style="font-family:monospace;font-size:12px;white-space:pre-wrap;
                         padding:16px;background:var(--bg);border-radius:8px;margin-top:8px;
                         max-height:500px;overflow-y:auto;"></div>
                  </div>
                </div>

                <div class="tab-panel" id="tab-logs">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Log Level Management</div>
                    </div>
                    <table class="data-table" id="loggersTable">
                      <thead><tr><th>Logger</th><th>Level</th><th>Actions</th></tr></thead>
                      <tbody id="loggersTbody">
                        <tr><td colspan="3" style="text-align:center;color:var(--text2);">Loading...</td></tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-events">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">System Events</div>
                      <button class="btn btn-sm" onclick="loadEvents()">Refresh</button>
                    </div>
                    <table class="data-table">
                      <thead><tr><th>Time</th><th>User</th><th>Action</th><th>Resource</th><th>Result</th></tr></thead>
                      <tbody id="eventsTbody">
                        <tr><td colspan="5" style="text-align:center;color:var(--text2);">Loading...</td></tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-alerts">
                  <div class="card">
                    <div class="card-title">Active Alerts</div>
                    <div id="alertsContent">
                      <div class="alert alert-success">No active alerts. All systems normal.</div>
                    </div>
                  </div>
                </div>

                <script>
                var CTX = '%s';
                document.querySelectorAll('#monTabs .tab').forEach(function(tab){
                  tab.addEventListener('click', function(){
                    document.querySelectorAll('#monTabs .tab').forEach(function(t){t.classList.remove('active');});
                    document.querySelectorAll('.tab-panel').forEach(function(p){p.classList.remove('active');});
                    tab.classList.add('active');
                    document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
                  });
                });

                var autoRefresh = true;
                var chartData = [];
                function toggleAutoRefresh() {
                  autoRefresh = !autoRefresh;
                  document.getElementById('autoRefreshBtn').textContent = 'Auto-Refresh: ' + (autoRefresh ? 'ON' : 'OFF');
                }

                function fetchMetrics() {
                  if (!autoRefresh) return;
                  fetch(CTX + '/api/status').then(function(r){return r.json();}).then(function(d) {
                    var heapMb = Math.round(d.heapUsedBytes / (1024*1024));
                    var maxMb = Math.round(d.heapMaxBytes / (1024*1024));
                    var pct = maxMb > 0 ? Math.round(heapMb * 100 / maxMb) : 0;
                    document.getElementById('m-heap').textContent = heapMb + ' / ' + maxMb + ' MB';
                    document.getElementById('m-threads').textContent = d.threadCount;
                    var bar = document.getElementById('m-heap-bar');
                    bar.style.width = pct + '%%';
                    bar.style.background = pct > 80 ? 'var(--danger)' : pct > 60 ? 'var(--warning)' : 'var(--primary)';
                    chartData.push({t: Date.now(), heap: heapMb, threads: d.threadCount});
                    if (chartData.length > 60) chartData.shift();
                    drawChart();
                  }).catch(function(){});

                  fetch(CTX + '/api/monitoring').then(function(r){return r.json();}).then(function(d) {
                    document.getElementById('metricsJson').textContent = JSON.stringify(d, null, 2);
                    document.getElementById('detailedMetrics').textContent = JSON.stringify(d, null, 2);
                  }).catch(function(){});
                }

                function drawChart() {
                  var canvas = document.getElementById('metricsChart');
                  if (!canvas) return;
                  var c = canvas.getContext('2d');
                  var w = canvas.width, h = canvas.height;
                  c.clearRect(0, 0, w, h);
                  if (chartData.length < 2) return;
                  var maxHeap = Math.max.apply(null, chartData.map(function(d){return d.heap;})) * 1.2 || 1;
                  c.strokeStyle = '#6366f1'; c.lineWidth = 2; c.beginPath();
                  chartData.forEach(function(d, i) {
                    var x = (i / (chartData.length - 1)) * w;
                    var y = h - (d.heap / maxHeap) * h;
                    if (i === 0) c.moveTo(x, y); else c.lineTo(x, y);
                  });
                  c.stroke();
                  c.fillStyle = 'rgba(99,102,241,0.1)';
                  c.lineTo(w, h); c.lineTo(0, h); c.fill();
                }

                function loadLoggers() {
                  fetch(CTX + '/api/loggers').then(function(r){return r.json();}).then(function(d) {
                    var loggers = d.loggers || [];
                    var tb = document.getElementById('loggersTbody');
                    var html = '';
                    loggers.forEach(function(lg) {
                      var levels = ['TRACE','DEBUG','INFO','WARN','ERROR'];
                      var btns = levels.map(function(lv) {
                        var cls = lv === lg.level ? 'btn btn-sm btn-primary' : 'btn btn-sm';
                        return '<button class="' + cls + '" onclick="setLogLevel(\\'' + lg.name + '\\',\\'' + lv + '\\')">' + lv + '</button>';
                      }).join(' ');
                      html += '<tr><td><code>' + lg.name + '</code></td><td><span class="badge badge-info">' + lg.level + '</span></td><td><div class="btn-group">' + btns + '</div></td></tr>';
                    });
                    if (!html) html = '<tr><td colspan="3" style="text-align:center;color:var(--text2);">No loggers found</td></tr>';
                    tb.innerHTML = html;
                  }).catch(function(){
                    document.getElementById('loggersTbody').innerHTML = '<tr><td colspan="3" style="text-align:center;color:var(--text2);">Failed to load loggers</td></tr>';
                  });
                }

                function setLogLevel(name, level) {
                  fetch(CTX + '/api/loggers/set', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({logger: name, level: level})
                  }).then(function(r){return r.json();}).then(function(d) {
                    if (d.success) loadLoggers();
                  }).catch(function(){});
                }

                function loadEvents() {
                  fetch(CTX + '/api/audit').then(function(r){return r.json();}).then(function(d) {
                    var events = d.events || [];
                    var tb = document.getElementById('eventsTbody');
                    var html = '';
                    events.slice(0, 100).forEach(function(ev) {
                      var badge = ev.success ? 'badge-success' : 'badge-danger';
                      html += '<tr><td style="white-space:nowrap;">' + ev.timestamp + '</td>'
                        + '<td>' + (ev.user || '-') + '</td>'
                        + '<td><span class="badge badge-info">' + (ev.action || '-') + '</span></td>'
                        + '<td>' + (ev.resource || '-') + '</td>'
                        + '<td><span class="badge ' + badge + '">' + (ev.success ? 'Success' : 'Failed') + '</span></td></tr>';
                    });
                    if (!html) html = '<tr><td colspan="5" style="text-align:center;color:var(--text2);">No events recorded</td></tr>';
                    tb.innerHTML = html;
                  }).catch(function(){
                    document.getElementById('eventsTbody').innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text2);">Failed to load events</td></tr>';
                  });
                }

                function checkAlerts() {
                  fetch(CTX + '/api/status').then(function(r){return r.json();}).then(function(d) {
                    var alerts = [];
                    var heapMb = Math.round(d.heapUsedBytes / (1024*1024));
                    var maxMb = Math.round(d.heapMaxBytes / (1024*1024));
                    var pct = maxMb > 0 ? Math.round(heapMb * 100 / maxMb) : 0;
                    if (pct > 90) alerts.push({level:'danger', msg:'Heap memory usage is critically high: ' + pct + '%%'});
                    else if (pct > 75) alerts.push({level:'warning', msg:'Heap memory usage is elevated: ' + pct + '%%'});
                    if (d.threadCount > 500) alerts.push({level:'warning', msg:'High thread count: ' + d.threadCount});
                    var el = document.getElementById('alertsContent');
                    if (alerts.length === 0) {
                      el.innerHTML = '<div class="alert alert-success">No active alerts. All systems normal.</div>';
                    } else {
                      el.innerHTML = alerts.map(function(a){return '<div class="alert alert-' + a.level + '">' + a.msg + '</div>';}).join('');
                    }
                  }).catch(function(){});
                }

                fetchMetrics();
                loadLoggers();
                loadEvents();
                checkAlerts();
                setInterval(fetchMetrics, 3000);
                setInterval(checkAlerts, 10000);
                </script>
                """.formatted(
                formatUptime(uptimeSec),
                heapUsed, heapMax, heapPct,
                heapPct > 80 ? "var(--danger)" : heapPct > 60 ? "var(--warning)" : "var(--primary)",
                threads,
                Runtime.getRuntime().availableProcessors(),
                ctx
        );

        resp.getWriter().write(AdminPageLayout.page("Monitoring", server.getName(), server.getNodeId(),
                ctx, "monitoring", body));
    }

    private static String h(String s) { return AdminPageLayout.escapeHtml(s); }

    private static String formatUptime(long sec) {
        return "%dd %dh %dm %ds".formatted(sec / 86400, (sec % 86400) / 3600, (sec % 3600) / 60, sec % 60);
    }
}
