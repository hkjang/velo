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
        int cpus = Runtime.getRuntime().availableProcessors();

        String body = """
                <div class="page-header">
                  <div>
                    <div class="page-title">Servers</div>
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
                  <table class="data-table">
                    <thead>
                      <tr>
                        <th>Name</th><th>Node</th><th>Status</th><th>Listen</th>
                        <th>Uptime</th><th>Heap</th><th>Threads</th><th>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td><a href="%s/servers?detail=%s"><strong>%s</strong></a></td>
                        <td>%s</td>
                        <td><span class="badge badge-success">RUNNING</span></td>
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
                            <button class="btn btn-sm" title="Suspend" onclick="serverAction('suspend')">&#9208;</button>
                            <button class="btn btn-sm btn-danger" title="Stop" onclick="serverAction('stop')">&#9209;</button>
                            <button class="btn btn-sm" title="Restart" onclick="serverAction('restart')">&#8635;</button>
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
                <script>
                var CTX = '%s';
                var SERVER_NAME = '%s';
                function serverAction(action) {
                  if (action === 'stop' && !confirm('Are you sure you want to stop the server?')) return;
                  fetch(CTX + '/api/execute', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({command: action + 'server ' + SERVER_NAME})
                  }).then(function(r){return r.json();}).then(function(d){showToast(d.message, d.success ? 'success' : 'error');});
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
                ctx, h(server.getName()), h(server.getName()),
                h(server.getNodeId()),
                h(server.getListener().getHost()), server.getListener().getPort(),
                uptime,
                heapPct, heapPct > 80 ? "var(--danger)" : heapPct > 60 ? "var(--warning)" : "var(--primary)", heapPct,
                threads,
                // Details
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
