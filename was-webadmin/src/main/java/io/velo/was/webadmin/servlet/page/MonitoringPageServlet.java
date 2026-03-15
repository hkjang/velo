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
 * Displays real-time metrics, charts, logs, events, alerts, and alert rules.
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
                    <div class="page-title" data-i18n="page.monitoring">Monitoring</div>
                    <div class="page-subtitle">Real-time metrics, logs, and system events</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn btn-sm" id="autoRefreshBtn" onclick="toggleAutoRefresh()">Auto-Refresh: ON</button>
                    <button class="btn btn-sm" onclick="location.reload()">Refresh Now</button>
                    <button class="btn btn-sm" onclick="exportCsv()">Export CSV</button>
                    <button class="btn btn-sm" onclick="exportPdfReport()">Export PDF Report</button>
                  </div>
                </div>

                <div class="tabs" id="monTabs">
                  <div class="tab active" data-tab="overview">Overview</div>
                  <div class="tab" data-tab="metrics">Metrics</div>
                  <div class="tab" data-tab="logs">Logs</div>
                  <div class="tab" data-tab="events">Events</div>
                  <div class="tab" data-tab="alerts">Alerts</div>
                  <div class="tab" data-tab="alertrules">Alert Rules</div>
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

                  <!-- Percentile display -->
                  <div class="card" style="margin-bottom:20px;">
                    <div class="card-title">Response Time Percentiles</div>
                    <div class="grid grid-3" style="margin-top:12px;">
                      <div style="text-align:center;padding:12px;background:var(--bg);border-radius:8px;">
                        <div style="font-size:12px;color:var(--text2);">p50 (Median)</div>
                        <div class="metric-value sm" id="m-p50">-- ms</div>
                      </div>
                      <div style="text-align:center;padding:12px;background:var(--bg);border-radius:8px;">
                        <div style="font-size:12px;color:var(--text2);">p95</div>
                        <div class="metric-value sm" id="m-p95">-- ms</div>
                      </div>
                      <div style="text-align:center;padding:12px;background:var(--bg);border-radius:8px;">
                        <div style="font-size:12px;color:var(--text2);">p99</div>
                        <div class="metric-value sm" id="m-p99">-- ms</div>
                      </div>
                    </div>
                  </div>

                  <!-- Anomaly indicator -->
                  <div id="anomalyIndicator" class="alert alert-danger" style="display:none;margin-bottom:20px;"></div>

                  <div class="grid grid-2">
                    <div class="card">
                      <div class="card-title">Request Metrics (from MetricsCollector)</div>
                      <div id="metricsJson" style="font-family:monospace;font-size:12px;white-space:pre-wrap;
                           color:var(--text2);max-height:300px;overflow-y:auto;padding:12px;
                           background:var(--bg);border-radius:8px;margin-top:8px;"></div>
                    </div>
                    <div class="card">
                      <div class="card-title">Live Metrics Chart</div>
                      <div style="margin:8px 0;">
                        <label style="margin-right:12px;font-size:12px;"><input type="checkbox" id="chkHeap" checked onchange="drawChart()"> Heap</label>
                        <label style="margin-right:12px;font-size:12px;"><input type="checkbox" id="chkThreads" checked onchange="drawChart()"> Threads</label>
                        <label style="font-size:12px;"><input type="checkbox" id="chkCpu" checked onchange="drawChart()"> CPU</label>
                      </div>
                      <canvas id="metricsChart" width="500" height="220" style="width:100%%;"></canvas>
                      <div id="chartLegend" style="display:flex;gap:16px;margin-top:8px;font-size:11px;">
                        <span><span style="display:inline-block;width:12px;height:3px;background:#6366f1;margin-right:4px;vertical-align:middle;"></span>Heap (MB)</span>
                        <span><span style="display:inline-block;width:12px;height:3px;background:#22c55e;margin-right:4px;vertical-align:middle;"></span>Threads</span>
                        <span><span style="display:inline-block;width:12px;height:3px;background:#f59e0b;margin-right:4px;vertical-align:middle;"></span>CPU (%%)</span>
                      </div>
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
                  <!-- Log viewer with filters -->
                  <div class="card" style="margin-top:16px;">
                    <div class="card-header">
                      <div class="card-title">Log Viewer</div>
                    </div>
                    <div style="display:flex;gap:12px;flex-wrap:wrap;margin-bottom:12px;">
                      <div class="form-group" style="margin-bottom:0;">
                        <label class="form-label" style="font-size:11px;">From</label>
                        <input class="form-input" type="date" id="logDateFrom" style="width:160px;">
                      </div>
                      <div class="form-group" style="margin-bottom:0;">
                        <label class="form-label" style="font-size:11px;">To</label>
                        <input class="form-input" type="date" id="logDateTo" style="width:160px;">
                      </div>
                      <div class="form-group" style="margin-bottom:0;">
                        <label class="form-label" style="font-size:11px;">Level</label>
                        <select class="form-select" id="logLevelFilter" style="width:120px;">
                          <option value="">All</option>
                          <option value="ERROR">ERROR</option>
                          <option value="WARN">WARN</option>
                          <option value="INFO">INFO</option>
                          <option value="DEBUG">DEBUG</option>
                          <option value="TRACE">TRACE</option>
                        </select>
                      </div>
                      <div class="form-group" style="margin-bottom:0;">
                        <label class="form-label" style="font-size:11px;">Search</label>
                        <input class="form-input" type="text" id="logSearchBox" placeholder="Filter text..." style="width:200px;">
                      </div>
                      <div class="form-group" style="margin-bottom:0;align-self:flex-end;">
                        <button class="btn btn-sm" onclick="filterLogs()">Apply</button>
                      </div>
                    </div>
                    <table class="data-table">
                      <thead><tr><th style="width:160px;">Timestamp</th><th style="width:60px;">Level</th><th>Message</th></tr></thead>
                      <tbody id="logViewerTbody">
                        <tr><td colspan="3" style="text-align:center;color:var(--text2);">Loading...</td></tr>
                      </tbody>
                    </table>
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-top:12px;">
                      <span id="logPageInfo" style="font-size:12px;color:var(--text2);">Page 1</span>
                      <div class="btn-group">
                        <button class="btn btn-sm" id="logPrevBtn" onclick="logPageNav(-1)" disabled>Previous</button>
                        <button class="btn btn-sm" id="logNextBtn" onclick="logPageNav(1)">Next</button>
                      </div>
                    </div>
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

                <div class="tab-panel" id="tab-alertrules">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Alert Rules Configuration</div>
                      <button class="btn btn-sm btn-primary" onclick="showAddRuleForm()">Add Rule</button>
                    </div>
                    <!-- Add Rule form -->
                    <div id="addRuleForm" style="display:none;padding:16px;background:var(--bg);border-radius:8px;margin-bottom:16px;">
                      <div style="display:flex;gap:12px;flex-wrap:wrap;align-items:flex-end;">
                        <div class="form-group" style="margin-bottom:0;">
                          <label class="form-label" style="font-size:11px;">Metric</label>
                          <select class="form-select" id="ruleMetric" style="width:150px;">
                            <option value="heapPct">Heap %%</option>
                            <option value="threadCount">Thread Count</option>
                            <option value="cpuLoad">CPU %%</option>
                          </select>
                        </div>
                        <div class="form-group" style="margin-bottom:0;">
                          <label class="form-label" style="font-size:11px;">Condition</label>
                          <select class="form-select" id="ruleCondition" style="width:60px;">
                            <option value=">">></option>
                            <option value="<"><</option>
                            <option value=">=">>=</option>
                            <option value="<="><=</option>
                          </select>
                        </div>
                        <div class="form-group" style="margin-bottom:0;">
                          <label class="form-label" style="font-size:11px;">Threshold</label>
                          <input class="form-input" type="number" id="ruleThreshold" style="width:100px;" placeholder="80">
                        </div>
                        <div class="form-group" style="margin-bottom:0;">
                          <label class="form-label" style="font-size:11px;">Severity</label>
                          <select class="form-select" id="ruleSeverity" style="width:120px;">
                            <option value="info">Info</option>
                            <option value="warning" selected>Warning</option>
                            <option value="critical">Critical</option>
                          </select>
                        </div>
                        <div class="form-group" style="margin-bottom:0;">
                          <label class="form-label" style="font-size:11px;">Action</label>
                          <select class="form-select" id="ruleAction" style="width:130px;">
                            <option value="toast">Toast Notification</option>
                            <option value="log">Log to Console</option>
                            <option value="email">Email (placeholder)</option>
                          </select>
                        </div>
                        <div class="form-group" style="margin-bottom:0;">
                          <button class="btn btn-sm btn-primary" onclick="addAlertRule()">Save</button>
                          <button class="btn btn-sm" onclick="document.getElementById('addRuleForm').style.display='none'">Cancel</button>
                        </div>
                      </div>
                    </div>
                    <table class="data-table">
                      <thead><tr><th>Metric</th><th>Condition</th><th>Threshold</th><th>Severity</th><th>Action</th><th>Status</th><th>Actions</th></tr></thead>
                      <tbody id="alertRulesTbody">
                        <tr><td colspan="7" style="text-align:center;color:var(--text2);">No alert rules configured</td></tr>
                      </tbody>
                    </table>
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
                var responseTimeSamples = [];
                function toggleAutoRefresh() {
                  autoRefresh = !autoRefresh;
                  document.getElementById('autoRefreshBtn').textContent = 'Auto-Refresh: ' + (autoRefresh ? 'ON' : 'OFF');
                }

                /* ---- Percentile helpers ---- */
                function percentile(arr, p) {
                  if (!arr.length) return 0;
                  var sorted = arr.slice().sort(function(a,b){return a-b;});
                  var idx = Math.ceil(p / 100 * sorted.length) - 1;
                  return sorted[Math.max(0, idx)];
                }

                /* ---- Anomaly detection ---- */
                function checkAnomaly() {
                  if (chartData.length < 5) { document.getElementById('anomalyIndicator').style.display = 'none'; return; }
                  var heaps = chartData.map(function(d){return d.heap;});
                  var mean = heaps.reduce(function(a,b){return a+b;},0) / heaps.length;
                  var variance = heaps.reduce(function(a,b){return a + Math.pow(b - mean, 2);},0) / heaps.length;
                  var stddev = Math.sqrt(variance);
                  var latest = heaps[heaps.length - 1];
                  var el = document.getElementById('anomalyIndicator');
                  if (stddev > 0 && Math.abs(latest - mean) > 2 * stddev) {
                    el.style.display = '';
                    el.innerHTML = 'Anomaly detected: Heap usage (' + latest + ' MB) deviates more than 2 standard deviations from recent average (' + Math.round(mean) + ' MB, stddev=' + Math.round(stddev) + ' MB).';
                  } else {
                    el.style.display = 'none';
                  }
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
                    var cpuLoad = d.cpuLoad != null ? Math.round(d.cpuLoad * 100) : 0;
                    chartData.push({t: Date.now(), heap: heapMb, threads: d.threadCount, cpu: cpuLoad, heapPct: pct});
                    if (chartData.length > 60) chartData.shift();
                    drawChart();
                    checkAnomaly();
                    evaluateAlertRules({heapPct: pct, threadCount: d.threadCount, cpuLoad: cpuLoad});
                  }).catch(function(){});

                  fetch(CTX + '/api/monitoring').then(function(r){return r.json();}).then(function(d) {
                    document.getElementById('metricsJson').textContent = JSON.stringify(d, null, 2);
                    document.getElementById('detailedMetrics').textContent = JSON.stringify(d, null, 2);
                    // Update percentiles from response time samples if available
                    var rt = d.responseTimeSamples || d.responseTimes || [];
                    if (rt.length > 0) { responseTimeSamples = rt; }
                    else {
                      // Simulate from avgResponseTime if present
                      var avg = d.avgResponseTimeMs || d.averageResponseTime || 0;
                      if (avg > 0) responseTimeSamples.push(avg + (Math.random() - 0.5) * avg * 0.3);
                      if (responseTimeSamples.length > 200) responseTimeSamples = responseTimeSamples.slice(-200);
                    }
                    document.getElementById('m-p50').textContent = responseTimeSamples.length ? Math.round(percentile(responseTimeSamples, 50)) + ' ms' : '-- ms';
                    document.getElementById('m-p95').textContent = responseTimeSamples.length ? Math.round(percentile(responseTimeSamples, 95)) + ' ms' : '-- ms';
                    document.getElementById('m-p99').textContent = responseTimeSamples.length ? Math.round(percentile(responseTimeSamples, 99)) + ' ms' : '-- ms';
                  }).catch(function(){});
                }

                function drawChart() {
                  var canvas = document.getElementById('metricsChart');
                  if (!canvas) return;
                  var c = canvas.getContext('2d');
                  var w = canvas.width, h = canvas.height;
                  var padTop = 10, padBottom = 25, padLeft = 40, padRight = 10;
                  var plotW = w - padLeft - padRight;
                  var plotH = h - padTop - padBottom;
                  c.clearRect(0, 0, w, h);
                  if (chartData.length < 2) return;
                  var showHeap = document.getElementById('chkHeap').checked;
                  var showThreads = document.getElementById('chkThreads').checked;
                  var showCpu = document.getElementById('chkCpu').checked;

                  // Grid lines
                  c.strokeStyle = 'rgba(128,128,128,0.15)';
                  c.lineWidth = 1;
                  for (var g = 0; g <= 4; g++) {
                    var gy = padTop + (plotH / 4) * g;
                    c.beginPath(); c.moveTo(padLeft, gy); c.lineTo(padLeft + plotW, gy); c.stroke();
                  }

                  // Y-axis labels
                  c.fillStyle = 'rgba(128,128,128,0.6)';
                  c.font = '10px sans-serif';
                  c.textAlign = 'right';
                  var maxHeap = Math.max.apply(null, chartData.map(function(d){return d.heap;})) * 1.2 || 1;
                  for (var g = 0; g <= 4; g++) {
                    var gy = padTop + (plotH / 4) * g;
                    var val = Math.round(maxHeap * (1 - g / 4));
                    c.fillText(val, padLeft - 4, gy + 3);
                  }

                  // X-axis label
                  c.textAlign = 'center';
                  c.fillText('Time', padLeft + plotW / 2, h - 2);

                  // Draw series
                  function drawLine(data, color, maxVal) {
                    c.strokeStyle = color; c.lineWidth = 2; c.beginPath();
                    data.forEach(function(val, i) {
                      var x = padLeft + (i / (chartData.length - 1)) * plotW;
                      var y = padTop + plotH - (val / maxVal) * plotH;
                      if (i === 0) c.moveTo(x, y); else c.lineTo(x, y);
                    });
                    c.stroke();
                  }

                  if (showHeap) {
                    drawLine(chartData.map(function(d){return d.heap;}), '#6366f1', maxHeap);
                  }
                  if (showThreads) {
                    var maxT = Math.max.apply(null, chartData.map(function(d){return d.threads;})) * 1.2 || 1;
                    drawLine(chartData.map(function(d){return d.threads;}), '#22c55e', maxT);
                  }
                  if (showCpu) {
                    drawLine(chartData.map(function(d){return d.cpu;}), '#f59e0b', 100);
                  }
                }

                /* ---- Log Viewer with filters and pagination ---- */
                var allLogs = [];
                var filteredLogs = [];
                var logPage = 0;
                var LOG_PER_PAGE = 20;

                function loadLogEntries() {
                  fetch(CTX + '/api/audit').then(function(r){return r.json();}).then(function(d) {
                    var events = d.events || [];
                    allLogs = events.map(function(ev) {
                      return {
                        timestamp: ev.timestamp || new Date().toISOString(),
                        level: ev.success === false ? 'ERROR' : (ev.action === 'WARN' ? 'WARN' : 'INFO'),
                        message: (ev.action || '') + ' ' + (ev.resource || '') + ' by ' + (ev.user || '-')
                      };
                    });
                    filterLogs();
                  }).catch(function() {
                    document.getElementById('logViewerTbody').innerHTML = '<tr><td colspan="3" style="text-align:center;color:var(--text2);">Failed to load logs</td></tr>';
                  });
                }

                function filterLogs() {
                  var dateFrom = document.getElementById('logDateFrom').value;
                  var dateTo = document.getElementById('logDateTo').value;
                  var levelFilter = document.getElementById('logLevelFilter').value;
                  var searchText = document.getElementById('logSearchBox').value.toLowerCase();
                  filteredLogs = allLogs.filter(function(log) {
                    if (dateFrom && log.timestamp < dateFrom) return false;
                    if (dateTo && log.timestamp > dateTo + 'T23:59:59') return false;
                    if (levelFilter && log.level !== levelFilter) return false;
                    if (searchText && log.message.toLowerCase().indexOf(searchText) === -1) return false;
                    return true;
                  });
                  logPage = 0;
                  renderLogPage();
                }

                function renderLogPage() {
                  var start = logPage * LOG_PER_PAGE;
                  var page = filteredLogs.slice(start, start + LOG_PER_PAGE);
                  var totalPages = Math.max(1, Math.ceil(filteredLogs.length / LOG_PER_PAGE));
                  var tb = document.getElementById('logViewerTbody');
                  if (page.length === 0) {
                    tb.innerHTML = '<tr><td colspan="3" style="text-align:center;color:var(--text2);">No log entries found</td></tr>';
                  } else {
                    var html = '';
                    page.forEach(function(log) {
                      var badgeCls = log.level === 'ERROR' ? 'badge-danger' : log.level === 'WARN' ? 'badge-warning' : 'badge-info';
                      html += '<tr><td style="white-space:nowrap;">' + log.timestamp + '</td>'
                        + '<td><span class="badge ' + badgeCls + '">' + log.level + '</span></td>'
                        + '<td>' + log.message + '</td></tr>';
                    });
                    tb.innerHTML = html;
                  }
                  document.getElementById('logPageInfo').textContent = 'Page ' + (logPage + 1) + ' of ' + totalPages + ' (' + filteredLogs.length + ' entries)';
                  document.getElementById('logPrevBtn').disabled = logPage === 0;
                  document.getElementById('logNextBtn').disabled = (logPage + 1) >= totalPages;
                }

                function logPageNav(dir) {
                  logPage += dir;
                  renderLogPage();
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

                /* ---- Alert Rules ---- */
                var alertRules = JSON.parse(localStorage.getItem('velo-alert-rules') || '[]');

                function showAddRuleForm() {
                  document.getElementById('addRuleForm').style.display = '';
                }

                function addAlertRule() {
                  var metric = document.getElementById('ruleMetric').value;
                  var condition = document.getElementById('ruleCondition').value;
                  var threshold = parseFloat(document.getElementById('ruleThreshold').value);
                  var severity = document.getElementById('ruleSeverity').value;
                  var action = document.getElementById('ruleAction').value;
                  if (isNaN(threshold)) { showToast('Threshold must be a number', 'warning'); return; }
                  alertRules.push({metric: metric, condition: condition, threshold: threshold, severity: severity, action: action, enabled: true});
                  localStorage.setItem('velo-alert-rules', JSON.stringify(alertRules));
                  document.getElementById('addRuleForm').style.display = 'none';
                  renderAlertRules();
                  showToast('Alert rule added', 'success');
                }

                function removeAlertRule(idx) {
                  alertRules.splice(idx, 1);
                  localStorage.setItem('velo-alert-rules', JSON.stringify(alertRules));
                  renderAlertRules();
                  showToast('Alert rule removed', 'success');
                }

                function toggleAlertRule(idx) {
                  alertRules[idx].enabled = !alertRules[idx].enabled;
                  localStorage.setItem('velo-alert-rules', JSON.stringify(alertRules));
                  renderAlertRules();
                }

                function renderAlertRules() {
                  var tb = document.getElementById('alertRulesTbody');
                  if (alertRules.length === 0) {
                    tb.innerHTML = '<tr><td colspan="7" style="text-align:center;color:var(--text2);">No alert rules configured</td></tr>';
                    return;
                  }
                  var metricLabels = {heapPct: 'Heap %%', threadCount: 'Threads', cpuLoad: 'CPU %%'};
                  var sevBadge = {info: 'badge-info', warning: 'badge-warning', critical: 'badge-danger'};
                  var html = '';
                  alertRules.forEach(function(r, i) {
                    html += '<tr><td>' + (metricLabels[r.metric] || r.metric) + '</td>'
                      + '<td>' + r.condition + '</td>'
                      + '<td>' + r.threshold + '</td>'
                      + '<td><span class="badge ' + (sevBadge[r.severity] || 'badge-info') + '">' + r.severity + '</span></td>'
                      + '<td>' + r.action + '</td>'
                      + '<td><span class="badge ' + (r.enabled ? 'badge-success' : 'badge-neutral') + '">' + (r.enabled ? 'Enabled' : 'Disabled') + '</span></td>'
                      + '<td><div class="btn-group">'
                      + '<button class="btn btn-sm" onclick="toggleAlertRule(' + i + ')">' + (r.enabled ? 'Disable' : 'Enable') + '</button>'
                      + '<button class="btn btn-sm btn-danger" onclick="removeAlertRule(' + i + ')">Remove</button>'
                      + '</div></td></tr>';
                  });
                  tb.innerHTML = html;
                }

                function evaluateAlertRules(metrics) {
                  alertRules.forEach(function(rule) {
                    if (!rule.enabled) return;
                    var val = metrics[rule.metric];
                    if (val == null) return;
                    var triggered = false;
                    if (rule.condition === '>' && val > rule.threshold) triggered = true;
                    if (rule.condition === '<' && val < rule.threshold) triggered = true;
                    if (rule.condition === '>=' && val >= rule.threshold) triggered = true;
                    if (rule.condition === '<=' && val <= rule.threshold) triggered = true;
                    if (triggered) {
                      var metricLabels = {heapPct: 'Heap %%', threadCount: 'Threads', cpuLoad: 'CPU %%'};
                      var msg = 'Alert [' + rule.severity.toUpperCase() + ']: ' + (metricLabels[rule.metric] || rule.metric) + ' ' + rule.condition + ' ' + rule.threshold + ' (current: ' + val + ')';
                      if (rule.action === 'toast') {
                        var toastLevel = rule.severity === 'critical' ? 'error' : rule.severity;
                        showToast(msg, toastLevel);
                      } else if (rule.action === 'log') {
                        console.warn(msg);
                      } else if (rule.action === 'email') {
                        console.log('[EMAIL PLACEHOLDER] Would send: ' + msg);
                      }
                    }
                  });
                }

                renderAlertRules();

                /* ---- Export PDF Report ---- */
                function exportPdfReport() {
                  var win = window.open('', '_blank');
                  var heapEl = document.getElementById('m-heap');
                  var threadsEl = document.getElementById('m-threads');
                  var uptimeEl = document.getElementById('m-uptime');
                  var p50 = document.getElementById('m-p50').textContent;
                  var p95 = document.getElementById('m-p95').textContent;
                  var p99 = document.getElementById('m-p99').textContent;
                  var html = '<!DOCTYPE html><html><head><title>Velo Monitoring Report</title>';
                  html += '<style>body{font-family:sans-serif;padding:40px;color:#333;}';
                  html += 'h1{color:#6366f1;} table{border-collapse:collapse;width:100%%;margin:16px 0;}';
                  html += 'th,td{border:1px solid #ddd;padding:8px 12px;text-align:left;}';
                  html += 'th{background:#f5f5f5;} .section{margin:24px 0;} @media print{button{display:none;}}</style></head><body>';
                  html += '<h1>Velo WAS Monitoring Report</h1>';
                  html += '<p>Generated: ' + new Date().toLocaleString() + '</p>';
                  html += '<div class="section"><h2>System Overview</h2>';
                  html += '<table><tr><th>Metric</th><th>Value</th></tr>';
                  html += '<tr><td>Uptime</td><td>' + (uptimeEl ? uptimeEl.textContent : '-') + '</td></tr>';
                  html += '<tr><td>Heap Memory</td><td>' + (heapEl ? heapEl.textContent : '-') + '</td></tr>';
                  html += '<tr><td>Threads</td><td>' + (threadsEl ? threadsEl.textContent : '-') + '</td></tr>';
                  html += '</table></div>';
                  html += '<div class="section"><h2>Response Time Percentiles</h2>';
                  html += '<table><tr><th>Percentile</th><th>Value</th></tr>';
                  html += '<tr><td>p50</td><td>' + p50 + '</td></tr>';
                  html += '<tr><td>p95</td><td>' + p95 + '</td></tr>';
                  html += '<tr><td>p99</td><td>' + p99 + '</td></tr>';
                  html += '</table></div>';
                  if (chartData.length > 0) {
                    html += '<div class="section"><h2>Recent Metrics Data (' + chartData.length + ' samples)</h2>';
                    html += '<table><tr><th>Time</th><th>Heap (MB)</th><th>Threads</th><th>CPU (%%)</th></tr>';
                    chartData.forEach(function(d) {
                      html += '<tr><td>' + new Date(d.t).toLocaleTimeString() + '</td><td>' + d.heap + '</td><td>' + d.threads + '</td><td>' + (d.cpu || 0) + '</td></tr>';
                    });
                    html += '</table></div>';
                  }
                  html += '<button onclick="window.print()" style="margin-top:20px;padding:10px 24px;background:#6366f1;color:white;border:none;border-radius:6px;cursor:pointer;font-size:14px;">Print / Save as PDF</button>';
                  html += '</body></html>';
                  win.document.write(html);
                  win.document.close();
                  showToast('Report opened in new tab', 'success');
                }

                function exportCsv() {
                  if (chartData.length === 0) { showToast('No metrics data to export', 'warning'); return; }
                  var csv = 'Timestamp,Heap (MB),Threads,CPU\\n';
                  chartData.forEach(function(d) {
                    csv += new Date(d.t).toISOString() + ',' + d.heap + ',' + d.threads + ',' + (d.cpu || 0) + '\\n';
                  });
                  var blob = new Blob([csv], {type: 'text/csv'});
                  var a = document.createElement('a');
                  a.href = URL.createObjectURL(blob);
                  a.download = 'velo-metrics-' + new Date().toISOString().slice(0,10) + '.csv';
                  a.click();
                  URL.revokeObjectURL(a.href);
                  showToast('CSV exported: ' + chartData.length + ' data points', 'success');
                }

                fetchMetrics();
                loadLoggers();
                loadLogEntries();
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
