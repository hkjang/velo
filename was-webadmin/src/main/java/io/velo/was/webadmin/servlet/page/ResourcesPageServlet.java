package io.velo.was.webadmin.servlet.page;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * Resource management page.
 * Manages JDBC DataSources, Connection Pools, JMS, JNDI, Cache, File Store, and Certificates.
 */
public class ResourcesPageServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public ResourcesPageServlet(ServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        ServerConfiguration.Server server = configuration.getServer();
        String ctx = req.getContextPath();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();

        long heapUsedMb = heap.getUsed() / (1024 * 1024);
        long heapMaxMb = heap.getMax() / (1024 * 1024);
        long heapCommMb = heap.getCommitted() / (1024 * 1024);
        long nonHeapUsedMb = nonHeap.getUsed() / (1024 * 1024);
        long nonHeapCommMb = nonHeap.getCommitted() / (1024 * 1024);
        int heapPct = heapMaxMb > 0 ? (int)(heapUsedMb * 100 / heapMaxMb) : 0;

        String body = """
                <div class="page-header">
                  <div>
                    <div class="page-title">Resources</div>
                    <div class="page-subtitle">JDBC, JMS, JNDI, Cache, and system resource management</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn btn-primary" onclick="document.getElementById('addDsModal').classList.add('open')">Add DataSource</button>
                  </div>
                </div>

                <div class="tabs" id="resTabs">
                  <div class="tab active" data-tab="jdbc">JDBC / DataSource</div>
                  <div class="tab" data-tab="jms">JMS</div>
                  <div class="tab" data-tab="jndi">JNDI</div>
                  <div class="tab" data-tab="memory">Memory</div>
                  <div class="tab" data-tab="threadpools">Thread Pools</div>
                  <div class="tab" data-tab="certs">Certificates</div>
                </div>

                <div class="tab-panel active" id="tab-jdbc">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">JDBC DataSources</div>
                    </div>
                    <div class="alert alert-info">No DataSources configured. Click "Add DataSource" to create one.</div>
                    <table class="data-table" style="display:none;">
                      <thead>
                        <tr><th>Name</th><th>JNDI</th><th>Driver</th><th>Pool (active/idle/max)</th><th>Status</th><th>Actions</th></tr>
                      </thead>
                      <tbody></tbody>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-jms">
                  <div class="card">
                    <div class="card-header"><div class="card-title">JMS Resources</div></div>
                    <div class="alert alert-info">No JMS resources configured.</div>
                  </div>
                </div>

                <div class="tab-panel" id="tab-jndi">
                  <div class="card">
                    <div class="card-header"><div class="card-title">JNDI Bindings</div></div>
                    <table class="data-table">
                      <thead><tr><th>Name</th><th>Type</th><th>Bound Object</th></tr></thead>
                      <tbody>
                        <tr><td colspan="3" style="color:var(--text3);text-align:center;">No JNDI bindings registered</td></tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-memory">
                  <div class="grid grid-2">
                    <div class="card">
                      <div class="card-title">Heap Memory</div>
                      <div class="metric">
                        <span class="metric-label">Used / Max</span>
                        <span class="metric-value sm">%d / %d MB</span>
                      </div>
                      <div class="progress-bar">
                        <div class="progress-fill" style="width:%d%%;background:%s;"></div>
                      </div>
                      <table class="info-table" style="margin-top:16px;">
                        <tr><td>Used</td><td>%d MB</td></tr>
                        <tr><td>Committed</td><td>%d MB</td></tr>
                        <tr><td>Max</td><td>%d MB</td></tr>
                        <tr><td>Usage</td><td>%d%%</td></tr>
                      </table>
                    </div>
                    <div class="card">
                      <div class="card-title">Non-Heap Memory</div>
                      <table class="info-table">
                        <tr><td>Used</td><td>%d MB</td></tr>
                        <tr><td>Committed</td><td>%d MB</td></tr>
                      </table>
                      <div style="margin-top:20px;">
                        <div class="card-title">Actions</div>
                        <div class="btn-group" style="margin-top:8px;">
                          <button class="btn btn-sm" onclick="refreshMemory()">Refresh Memory Info</button>
                          <button class="btn btn-sm btn-danger" onclick="requestGC()">Request GC</button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="tab-panel" id="tab-threadpools">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Thread Pools</div>
                      <button class="btn btn-sm" onclick="loadThreadPools()">Refresh</button>
                    </div>
                    <table class="data-table">
                      <thead><tr><th>Name</th><th>Active</th><th>Pool Size</th><th>Max Size</th><th>Queue Size</th></tr></thead>
                      <tbody id="tpTbody">
                        <tr><td colspan="5" style="text-align:center;color:var(--text2);">Loading...</td></tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-certs">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">TLS Certificates</div>
                    </div>
                    <table class="info-table">
                      <tr><td>TLS Status</td><td>%s</td></tr>
                      <tr><td>Mode</td><td>%s</td></tr>
                      <tr><td>Protocols</td><td>%s</td></tr>
                      <tr><td>Cert Reload Interval</td><td>%ds</td></tr>
                    </table>
                  </div>
                </div>

                <!-- Add DataSource Modal -->
                <div class="modal-overlay" id="addDsModal">
                  <div class="modal">
                    <div class="modal-title">Add DataSource</div>
                    <div class="form-group">
                      <label class="form-label">DataSource Name</label>
                      <input class="form-input" type="text" placeholder="e.g. myAppDS">
                    </div>
                    <div class="form-group">
                      <label class="form-label">JNDI Name</label>
                      <input class="form-input" type="text" placeholder="e.g. jdbc/myAppDS">
                    </div>
                    <div class="form-group">
                      <label class="form-label">JDBC URL</label>
                      <input class="form-input" type="text" placeholder="jdbc:mysql://localhost:3306/mydb">
                    </div>
                    <div class="form-group">
                      <label class="form-label">Driver Class</label>
                      <input class="form-input" type="text" placeholder="com.mysql.cj.jdbc.Driver">
                    </div>
                    <div class="form-group">
                      <label class="form-label">Username</label>
                      <input class="form-input" type="text">
                    </div>
                    <div class="form-group">
                      <label class="form-label">Password</label>
                      <input class="form-input" type="password">
                    </div>
                    <div class="form-group">
                      <label class="form-label">Pool Size (min / max)</label>
                      <div style="display:flex;gap:8px;">
                        <input class="form-input" type="number" value="5" style="width:80px;">
                        <input class="form-input" type="number" value="20" style="width:80px;">
                      </div>
                    </div>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('addDsModal').classList.remove('open')">Cancel</button>
                      <button class="btn btn-primary">Create</button>
                    </div>
                  </div>
                </div>

                <script>
                var CTX = '%s';
                document.querySelectorAll('#resTabs .tab').forEach(function(tab){
                  tab.addEventListener('click', function(){
                    document.querySelectorAll('#resTabs .tab').forEach(function(t){t.classList.remove('active');});
                    document.querySelectorAll('.tab-panel').forEach(function(p){p.classList.remove('active');});
                    tab.classList.add('active');
                    document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
                  });
                });

                function refreshMemory() {
                  fetch(CTX + '/api/execute', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({command: 'memoryinfo'})
                  }).then(function(r){return r.json();}).then(function(d) {
                    showToast(d.message, d.success ? 'success' : 'error');
                    if (d.success) setTimeout(function(){ location.reload(); }, 1000);
                  });
                }

                function requestGC() {
                  if (!confirm('Request garbage collection?')) return;
                  fetch(CTX + '/api/execute', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({command: 'gc'})
                  }).then(function(r){return r.json();}).then(function(d) {
                    showToast(d.message, d.success ? 'success' : 'error');
                    if (d.success) setTimeout(function(){ location.reload(); }, 1000);
                  });
                }

                function loadThreadPools() {
                  fetch(CTX + '/api/threadpools').then(function(r){return r.json();}).then(function(d) {
                    var pools = d.threadPools || [];
                    var tb = document.getElementById('tpTbody');
                    var html = '';
                    pools.forEach(function(p) {
                      html += '<tr><td><strong>' + (p.name || '-') + '</strong></td>'
                        + '<td>' + (p.activeCount != null ? p.activeCount : '-') + '</td>'
                        + '<td>' + (p.poolSize != null ? p.poolSize : '-') + '</td>'
                        + '<td>' + (p.maxPoolSize != null ? p.maxPoolSize : '-') + '</td>'
                        + '<td>' + (p.queueSize != null ? p.queueSize : '-') + '</td></tr>';
                    });
                    if (!html) html = '<tr><td colspan="5" style="text-align:center;color:var(--text2);">No thread pools found</td></tr>';
                    tb.innerHTML = html;
                  }).catch(function(){
                    document.getElementById('tpTbody').innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text2);">Failed to load thread pools</td></tr>';
                  });
                }

                loadThreadPools();
                </script>
                """.formatted(
                heapUsedMb, heapMaxMb, heapPct,
                heapPct > 80 ? "var(--danger)" : heapPct > 60 ? "var(--warning)" : "var(--primary)",
                heapUsedMb, heapCommMb, heapMaxMb, heapPct,
                nonHeapUsedMb, nonHeapCommMb,
                server.getTls().isEnabled() ? "<span class='badge badge-success'>Enabled</span>" : "<span class='badge badge-neutral'>Disabled</span>",
                server.getTls().getMode().name(),
                String.join(", ", server.getTls().getProtocols()),
                server.getTls().getReloadIntervalSeconds(),
                ctx
        );

        resp.getWriter().write(AdminPageLayout.page("Resources", server.getName(), server.getNodeId(),
                ctx, "resources", body));
    }
}
