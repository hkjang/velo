package io.velo.was.webadmin.servlet.page;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;

/**
 * Server management page.
 * Lists servers, shows status, provides lifecycle control (start/stop/restart/suspend/resume).
 */
public class ServersPageServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public ServersPageServlet(ServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
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
        int heapPct = heapMax > 0 ? (int)(heapUsed * 100 / heapMax) : 0;
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        int peakThreads = ManagementFactory.getThreadMXBean().getPeakThreadCount();
        int cpus = Runtime.getRuntime().availableProcessors();

        // Health score: 0-100 based on heap usage and thread pressure
        int heapScore = heapPct < 60 ? 100 : heapPct < 80 ? 70 : heapPct < 90 ? 40 : 10;
        int threadScore = threads < cpus * 50 ? 100 : threads < cpus * 100 ? 70 : 40;
        int healthScore = (heapScore * 60 + threadScore * 40) / 100;
        String healthColor = healthScore >= 80 ? "var(--success)" : healthScore >= 50 ? "var(--warning)" : "var(--danger)";
        String healthLabel = healthScore >= 80 ? "Healthy" : healthScore >= 50 ? "Warning" : "Critical";

        // JVM args
        String jvmArgs = String.join(" ", runtime.getInputArguments());

        String body = """
                <style>
                .confirm-modal .risk-high { color: var(--danger); font-weight: 600; }
                .confirm-modal .risk-medium { color: var(--warning); font-weight: 600; }
                .confirm-modal .risk-low { color: var(--success); font-weight: 600; }
                .confirm-modal .risk-indicator { display:inline-block; padding:2px 10px; border-radius:4px; font-size:12px; margin-left:8px; }
                .confirm-modal .risk-indicator.high { background:var(--danger-bg); color:var(--danger); }
                .confirm-modal .risk-indicator.medium { background:var(--warning-bg); color:var(--warning); }
                .confirm-modal .risk-indicator.low { background:var(--success-bg); color:var(--success); }
                .spinner { display:inline-block; width:16px; height:16px; border:2px solid var(--border);
                           border-top-color:var(--primary); border-radius:50%%; animation:spin 0.6s linear infinite; }
                @keyframes spin { to { transform:rotate(360deg); } }
                .health-badge { display:inline-flex; align-items:center; gap:6px; padding:4px 12px;
                                border-radius:20px; font-size:13px; font-weight:600; }
                .detail-toggle { cursor:pointer; user-select:none; }
                .detail-toggle:hover { color:var(--primary); }
                .detail-section { display:none; background:var(--surface2); border-radius:var(--radius-sm);
                                  padding:16px; margin:8px 0; }
                .detail-section.open { display:block; }
                .bulk-bar { display:none; align-items:center; gap:12px; padding:12px 16px;
                            background:var(--primary-bg); border-radius:var(--radius-sm); margin-bottom:12px; }
                .bulk-bar.visible { display:flex; }
                </style>

                <div class="page-header">
                  <div>
                    <div class="page-title" data-i18n="page.servers">Servers</div>
                    <div class="page-subtitle">Server instances lifecycle management and status monitoring</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn btn-primary" onclick="document.getElementById('addServerModal').classList.add('open')">Add Server</button>
                  </div>
                </div>

                <div class="grid grid-4" style="margin-bottom:24px;">
                  <div class="card">
                    <div class="card-title">Total Servers</div>
                    <div class="metric-value" style="font-size:32px;" id="srv-total">-</div>
                  </div>
                  <div class="card">
                    <div class="card-title">Running</div>
                    <div class="metric-value success" style="font-size:32px;" id="srv-running">-</div>
                  </div>
                  <div class="card">
                    <div class="card-title">Stopped</div>
                    <div class="metric-value" style="font-size:32px;color:var(--text3);" id="srv-stopped">0</div>
                  </div>
                  <div class="card">
                    <div class="card-title">Failed</div>
                    <div class="metric-value danger" style="font-size:32px;" id="srv-failed">0</div>
                  </div>
                </div>

                <div class="card">
                  <div class="card-header">
                    <div class="card-title">Server Instances</div>
                    <div class="btn-group">
                      <button class="btn btn-sm" onclick="refreshServerStatus()">Refresh</button>
                    </div>
                  </div>

                  <div class="bulk-bar" id="bulkBar">
                    <span id="bulkCount">0</span> server(s) selected
                    <button class="btn btn-sm" onclick="bulkAction('restart')">Bulk Restart</button>
                    <button class="btn btn-sm btn-danger" onclick="bulkAction('stop')">Bulk Stop</button>
                    <button class="btn btn-sm" onclick="clearBulkSelection()">Clear</button>
                  </div>

                  <table class="data-table">
                    <thead>
                      <tr>
                        <th><input type="checkbox" id="selectAll" onchange="toggleSelectAll(this)"></th>
                        <th>Name</th><th>Node</th><th>Status</th><th>Health</th><th>Listen</th>
                        <th>Uptime</th><th>Heap</th><th>Threads</th><th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td><input type="checkbox" class="srv-check" value="%s" onchange="updateBulkBar()"></td>
                        <td>
                          <a href="%s/servers?detail=%s"><strong>%s</strong></a>
                          <span class="detail-toggle" onclick="toggleDetail('detail-%s')" title="Toggle details"> &#9660;</span>
                        </td>
                        <td>%s</td>
                        <td><span class="badge badge-success">RUNNING</span></td>
                        <td>
                          <span class="health-badge" style="background:%s22;color:%s;">
                            %d%% &middot; %s
                          </span>
                        </td>
                        <td>%s:%d</td>
                        <td>%s</td>
                        <td id="srv-heap">
                          <div style="display:flex;align-items:center;gap:8px;">
                            <div class="progress-bar" style="width:80px;">
                              <div class="progress-fill" style="width:%d%%;background:%s;"></div>
                            </div>
                            <span style="font-size:12px;color:var(--text2);">%d%%</span>
                          </div>
                        </td>
                        <td id="srv-threads">%d</td>
                        <td>
                          <div class="btn-group">
                            <button class="btn btn-sm" title="Start" onclick="confirmServerAction('start-server','Start Server','low')">&#9654;</button>
                            <button class="btn btn-sm" title="Suspend" onclick="confirmServerAction('suspend-server','Suspend Server','low')">&#9208;</button>
                            <button class="btn btn-sm" title="Resume" onclick="confirmServerAction('resume-server','Resume Server','low')">&#9654;&#xFE0E;</button>
                            <button class="btn btn-sm" title="Restart" onclick="confirmServerAction('restart-server','Restart Server','medium')">&#8635;</button>
                            <button class="btn btn-sm btn-danger" title="Stop" onclick="confirmServerAction('stop-server','Stop Server','high')">&#9209;</button>
                            <button class="btn btn-sm btn-danger" title="Force Stop" onclick="confirmServerAction('force-stop-server','Force Stop Server','high')">&#9632;</button>
                          </div>
                        </td>
                      </tr>
                      <tr>
                        <td colspan="10" style="padding:0;">
                          <div class="detail-section" id="detail-%s">
                            <div class="grid grid-2" style="gap:16px;">
                              <div>
                                <strong>JVM Arguments</strong>
                                <pre style="background:var(--surface);padding:8px;border-radius:6px;font-size:12px;margin-top:4px;white-space:pre-wrap;word-break:break-all;max-height:120px;overflow:auto;">%s</pre>
                              </div>
                              <div>
                                <strong>Environment</strong>
                                <table class="info-table" style="margin-top:4px;">
                                  <tr><td>Java Version</td><td>%s</td></tr>
                                  <tr><td>OS</td><td>%s %s</td></tr>
                                  <tr><td>CPUs</td><td>%d</td></tr>
                                  <tr><td>Peak Threads</td><td>%d</td></tr>
                                </table>
                              </div>
                              <div>
                                <strong>Listeners</strong>
                                <table class="info-table" style="margin-top:4px;">
                                  <tr><td>Host:Port</td><td>%s:%d</td></tr>
                                  <tr><td>TLS</td><td>%s</td></tr>
                                  <tr><td>SO Backlog</td><td>%d</td></tr>
                                  <tr><td>Idle Timeout</td><td>%ds</td></tr>
                                </table>
                              </div>
                              <div>
                                <strong>Deployed Apps</strong>
                                <div id="detail-apps-%s" style="margin-top:4px;font-size:13px;color:var(--text2);">Loading...</div>
                              </div>
                            </div>
                          </div>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                <div style="margin-top:24px;" class="grid grid-2">
                  <div class="card">
                    <div class="card-title">Server Details</div>
                    <table class="info-table">
                      <tr><td>Server Name</td><td>%s</td></tr>
                      <tr><td>Node ID</td><td>%s</td></tr>
                      <tr><td>Host</td><td>%s</td></tr>
                      <tr><td>Port</td><td>%d</td></tr>
                      <tr><td>TLS</td><td>%s</td></tr>
                      <tr><td>Graceful Shutdown</td><td>%dms</td></tr>
                      <tr><td>Java Version</td><td>%s</td></tr>
                      <tr><td>OS</td><td>%s %s</td></tr>
                      <tr><td>CPUs</td><td>%d</td></tr>
                    </table>
                  </div>
                  <div class="card">
                    <div class="card-title">Threading Configuration</div>
                    <table class="info-table">
                      <tr><td>Boss Threads</td><td>%d</td></tr>
                      <tr><td>Worker Threads</td><td>%s</td></tr>
                      <tr><td>Business Threads</td><td>%d</td></tr>
                      <tr><td>Active Threads</td><td>%d</td></tr>
                      <tr><td>Peak Threads</td><td>%d</td></tr>
                    </table>
                    <div style="margin-top:16px;">
                      <div class="card-title">Listener Configuration</div>
                      <table class="info-table" style="margin-top:8px;">
                        <tr><td>SO Backlog</td><td>%d</td></tr>
                        <tr><td>TCP No Delay</td><td>%s</td></tr>
                        <tr><td>Keep Alive</td><td>%s</td></tr>
                        <tr><td>Max Content Length</td><td>%s</td></tr>
                        <tr><td>Max Header Size</td><td>%d</td></tr>
                        <tr><td>Idle Timeout</td><td>%ds</td></tr>
                      </table>
                    </div>
                  </div>
                </div>

                <div class="modal-overlay" id="addServerModal">
                  <div class="modal">
                    <div class="modal-title">Add Server</div>
                    <div class="alert alert-info">Multi-server support will be available in a future release.</div>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('addServerModal').classList.remove('open')">Close</button>
                    </div>
                  </div>
                </div>

                <!-- Lifecycle Confirmation Modal -->
                <div class="modal-overlay" id="confirmModal">
                  <div class="modal confirm-modal">
                    <div class="modal-title" id="confirmTitle">Confirm Action</div>
                    <div style="margin:16px 0;">
                      <span id="confirmMsg">Are you sure?</span>
                      <span class="risk-indicator" id="confirmRisk"></span>
                    </div>
                    <div id="confirmProgress" style="display:none;margin:12px 0;">
                      <span class="spinner"></span> <span id="confirmProgressText">Executing...</span>
                    </div>
                    <div id="confirmResult" style="display:none;margin:12px 0;"></div>
                    <div class="modal-footer">
                      <button class="btn" id="confirmCancel" onclick="document.getElementById('confirmModal').classList.remove('open')">Cancel</button>
                      <button class="btn btn-primary" id="confirmOk" onclick="executeConfirmedAction()">Confirm</button>
                    </div>
                  </div>
                </div>

                <script>
                var CTX = '%s';
                var SERVER_NAME = '%s';
                var pendingAction = null;

                function confirmServerAction(command, title, risk) {
                  pendingAction = command;
                  document.getElementById('confirmTitle').textContent = title;
                  var riskLabels = {high:'High Risk',medium:'Medium Risk',low:'Low Risk'};
                  var riskEl = document.getElementById('confirmRisk');
                  riskEl.textContent = riskLabels[risk] || risk;
                  riskEl.className = 'risk-indicator ' + risk;
                  document.getElementById('confirmMsg').textContent = 'Are you sure you want to ' + title.toLowerCase() + ' "' + SERVER_NAME + '"?';
                  document.getElementById('confirmProgress').style.display = 'none';
                  document.getElementById('confirmResult').style.display = 'none';
                  document.getElementById('confirmOk').style.display = '';
                  document.getElementById('confirmCancel').textContent = 'Cancel';
                  document.getElementById('confirmModal').classList.add('open');
                }

                function executeConfirmedAction() {
                  if (!pendingAction) return;
                  document.getElementById('confirmProgress').style.display = '';
                  document.getElementById('confirmProgressText').textContent = 'Executing ' + pendingAction + '...';
                  document.getElementById('confirmOk').style.display = 'none';
                  fetch(CTX + '/api/execute', {
                    method:'POST',
                    headers:{'Content-Type':'application/json'},
                    body:JSON.stringify({command: pendingAction + ' ' + SERVER_NAME})
                  }).then(function(r){return r.json();}).then(function(d){
                    document.getElementById('confirmProgress').style.display = 'none';
                    var resultEl = document.getElementById('confirmResult');
                    resultEl.style.display = '';
                    resultEl.innerHTML = '<div class="alert ' + (d.success ? 'alert-success' : 'alert-danger') + '">' + (d.message || (d.success ? 'Success' : 'Failed')) + '</div>';
                    document.getElementById('confirmCancel').textContent = 'Close';
                    showToast(d.message, d.success ? 'success' : 'error');
                    pendingAction = null;
                  }).catch(function(err){
                    document.getElementById('confirmProgress').style.display = 'none';
                    document.getElementById('confirmResult').style.display = '';
                    document.getElementById('confirmResult').innerHTML = '<div class="alert alert-danger">Request failed</div>';
                    document.getElementById('confirmCancel').textContent = 'Close';
                    pendingAction = null;
                  });
                }

                function toggleDetail(id) {
                  var el = document.getElementById(id);
                  if (el) el.classList.toggle('open');
                  // Load deployed apps into the detail section
                  var srvName = id.replace('detail-','');
                  var appsEl = document.getElementById('detail-apps-' + srvName);
                  if (appsEl && el.classList.contains('open')) {
                    fetch(CTX + '/api/applications').then(function(r){return r.json();}).then(function(d){
                      var apps = d.applications || [];
                      if (apps.length === 0) { appsEl.textContent = 'No applications deployed'; return; }
                      var html = '<ul style="list-style:none;padding:0;margin:0;">';
                      apps.forEach(function(a){
                        var badge = a.status === 'RUNNING' ? 'badge-success' : 'badge-neutral';
                        html += '<li style="padding:2px 0;"><span class="badge ' + badge + '">' + a.status + '</span> ' + a.name + ' <code>' + a.contextPath + '</code></li>';
                      });
                      html += '</ul>';
                      appsEl.innerHTML = html;
                    }).catch(function(){ appsEl.textContent = 'Failed to load'; });
                  }
                }

                // Bulk selection
                function toggleSelectAll(el) {
                  var checks = document.querySelectorAll('.srv-check');
                  checks.forEach(function(c){ c.checked = el.checked; });
                  updateBulkBar();
                }
                function updateBulkBar() {
                  var checks = document.querySelectorAll('.srv-check:checked');
                  var bar = document.getElementById('bulkBar');
                  document.getElementById('bulkCount').textContent = checks.length;
                  if (checks.length > 0) bar.classList.add('visible'); else bar.classList.remove('visible');
                }
                function clearBulkSelection() {
                  document.querySelectorAll('.srv-check').forEach(function(c){ c.checked = false; });
                  document.getElementById('selectAll').checked = false;
                  updateBulkBar();
                }
                function bulkAction(action) {
                  var checks = document.querySelectorAll('.srv-check:checked');
                  if (checks.length === 0) return;
                  if (!confirm('Are you sure you want to ' + action + ' ' + checks.length + ' server(s)?')) return;
                  checks.forEach(function(c) {
                    fetch(CTX + '/api/execute', {
                      method:'POST',
                      headers:{'Content-Type':'application/json'},
                      body:JSON.stringify({command: action + '-server ' + c.value})
                    }).then(function(r){return r.json();}).then(function(d){
                      showToast(c.value + ': ' + d.message, d.success ? 'success' : 'error');
                    });
                  });
                  clearBulkSelection();
                }

                function refreshServerStatus() {
                  fetch(CTX + '/api/status').then(function(r){return r.json();}).then(function(d) {
                    var hm = Math.round(d.heapUsedBytes/(1024*1024));
                    var mx = Math.round(d.heapMaxBytes/(1024*1024));
                    var pct = mx > 0 ? Math.round(hm*100/mx) : 0;
                    var heapCell = document.getElementById('srv-heap');
                    if (heapCell) {
                      heapCell.querySelector('.progress-fill').style.width = pct + '%%';
                      heapCell.querySelector('.progress-fill').style.background =
                        pct > 80 ? 'var(--danger)' : pct > 60 ? 'var(--warning)' : 'var(--primary)';
                      heapCell.querySelector('span').textContent = pct + '%%';
                    }
                    var thrCell = document.getElementById('srv-threads');
                    if (thrCell) thrCell.textContent = d.threadCount;
                  }).catch(function(){});
                }
                function loadServerCounts() {
                  fetch(CTX + '/api/servers').then(function(r){return r.json();}).then(function(d) {
                    var servers = d.servers || [];
                    document.getElementById('srv-total').textContent = servers.length;
                    document.getElementById('srv-running').textContent = servers.filter(function(s){return s.status==='RUNNING';}).length;
                    document.getElementById('srv-stopped').textContent = servers.filter(function(s){return s.status==='STOPPED';}).length;
                    document.getElementById('srv-failed').textContent = servers.filter(function(s){return s.status==='FAILED';}).length;
                  }).catch(function(){});
                }
                loadServerCounts();
                setInterval(refreshServerStatus, 5000);
                </script>
                """.formatted(
                // Checkbox value
                h(server.getName()),
                // Name cell
                ctx, h(server.getName()), h(server.getName()),
                // detail toggle id
                h(server.getName()),
                // Node
                h(server.getNodeId()),
                // Health badge
                healthColor, healthColor, healthScore, healthLabel,
                // Listen
                h(server.getListener().getHost()), server.getListener().getPort(),
                // Uptime
                uptime,
                // Heap progress
                heapPct, heapPct > 80 ? "var(--danger)" : heapPct > 60 ? "var(--warning)" : "var(--primary)", heapPct,
                // Threads
                threads,
                // Detail section id
                h(server.getName()),
                // JVM args
                h(jvmArgs.isEmpty() ? "(none)" : jvmArgs),
                // Environment in detail
                System.getProperty("java.version"),
                System.getProperty("os.name"), System.getProperty("os.version"),
                cpus, peakThreads,
                // Listeners in detail
                h(server.getListener().getHost()), server.getListener().getPort(),
                server.getTls().isEnabled() ? "Enabled" : "Disabled",
                server.getListener().getSoBacklog(),
                server.getListener().getIdleTimeoutSeconds(),
                // Deployed apps placeholder id
                h(server.getName()),
                // Server Details card
                h(server.getName()), h(server.getNodeId()),
                h(server.getListener().getHost()), server.getListener().getPort(),
                server.getTls().isEnabled() ? "Enabled" : "Disabled",
                server.getGracefulShutdownMillis(),
                System.getProperty("java.version"),
                System.getProperty("os.name"), System.getProperty("os.version"),
                cpus,
                // Threading
                server.getThreading().getBossThreads(),
                server.getThreading().getWorkerThreads() == 0 ? "auto" : String.valueOf(server.getThreading().getWorkerThreads()),
                server.getThreading().getBusinessThreads(),
                threads,
                ManagementFactory.getThreadMXBean().getPeakThreadCount(),
                // Listener
                server.getListener().getSoBacklog(),
                server.getListener().isTcpNoDelay() ? "true" : "false",
                server.getListener().isKeepAlive() ? "true" : "false",
                formatBytes(server.getListener().getMaxContentLength()),
                server.getListener().getMaxHeaderSize(),
                server.getListener().getIdleTimeoutSeconds(),
                // Script
                ctx, h(server.getName())
        );

        resp.getWriter().write(AdminPageLayout.page("Servers", server.getName(), server.getNodeId(),
                ctx, "servers", body));
    }

    private static String h(String s) { return AdminPageLayout.escapeHtml(s); }

    private static String formatBytes(int bytes) {
        if (bytes >= 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        if (bytes >= 1024) return (bytes / 1024) + " KB";
        return bytes + " B";
    }
}
