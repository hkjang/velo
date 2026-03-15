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
                    <div class="page-title" data-i18n="page.resources">Resources</div>
                    <div class="page-subtitle">JDBC, JMS, JNDI, Cache, and system resource management</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn btn-primary" onclick="document.getElementById('addDsModal').classList.add('open')">Add DataSource</button>
                  </div>
                </div>

                <div class="tabs" id="resTabs">
                  <div class="tab active" data-tab="jdbc">JDBC / DataSource</div>
                  <div class="tab" data-tab="connpool">Connection Pools</div>
                  <div class="tab" data-tab="jms">JMS</div>
                  <div class="tab" data-tab="jndi">JNDI</div>
                  <div class="tab" data-tab="cache">Cache</div>
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

                <div class="tab-panel" id="tab-connpool">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Connection Pools</div>
                      <button class="btn btn-sm" onclick="loadConnPools()">Refresh</button>
                    </div>
                    <div id="connpoolAlert" class="alert alert-info" style="display:none;">No connection pools found.</div>
                    <table class="data-table" id="connpoolTable" style="display:none;">
                      <thead>
                        <tr><th>Pool Name</th><th>Active</th><th>Idle</th><th>Max</th><th>Wait Count</th><th>Avg Wait Time</th><th>Status</th><th>Actions</th></tr>
                      </thead>
                      <tbody id="connpoolTbody"></tbody>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-jms">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">JMS Servers</div>
                      <div class="btn-group">
                        <button class="btn btn-sm" onclick="loadJmsServers()">Refresh Servers</button>
                        <button class="btn btn-sm" onclick="loadJmsDestinations()">Load Destinations</button>
                      </div>
                    </div>
                    <div id="jmsServerAlert" class="alert alert-info" style="display:none;">No JMS resources configured.</div>
                    <table class="data-table" id="jmsServerTable" style="display:none;">
                      <thead>
                        <tr><th>Name</th><th>Type</th><th>Status</th><th>Messages</th><th>Consumers</th></tr>
                      </thead>
                      <tbody id="jmsServerTbody"></tbody>
                    </table>
                  </div>
                  <div class="card" style="margin-top:16px;">
                    <div class="card-header">
                      <div class="card-title">JMS Destinations</div>
                    </div>
                    <div id="jmsDestAlert" class="alert alert-info" style="display:none;">No JMS destinations loaded. Click "Load Destinations" above.</div>
                    <table class="data-table" id="jmsDestTable" style="display:none;">
                      <thead>
                        <tr><th>Name</th><th>Type</th><th>Messages Pending</th><th>Consumers</th><th>Actions</th></tr>
                      </thead>
                      <tbody id="jmsDestTbody"></tbody>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-jndi">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">JNDI Bindings</div>
                      <button class="btn btn-sm" onclick="loadJndiBindings()">Refresh</button>
                    </div>
                    <div id="jndiAlert" class="alert alert-info" style="display:none;">No JNDI bindings registered.</div>
                    <table class="data-table" id="jndiTable" style="display:none;">
                      <thead><tr><th>Name</th><th>Type</th><th>Bound Object</th><th>Actions</th></tr></thead>
                      <tbody id="jndiTbody"></tbody>
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

                <div class="tab-panel" id="tab-cache">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Cache Management</div>
                      <div class="btn-group">
                        <button class="btn btn-sm" onclick="loadCaches()">Refresh</button>
                        <button class="btn btn-sm btn-danger" onclick="clearAllCaches()">Clear All</button>
                      </div>
                    </div>
                    <div class="alert alert-info" id="cacheAlert">Loading cache information...</div>
                    <table class="data-table" id="cacheTable" style="display:none;">
                      <thead>
                        <tr><th>Name</th><th>Type</th><th>Size</th><th>Hit Rate</th><th>Hits / Misses</th><th>Evictions</th><th>TTL</th><th>Actions</th></tr>
                      </thead>
                      <tbody id="cacheTbody"></tbody>
                    </table>
                  </div>
                  <div class="card" style="margin-top:16px;">
                    <div class="card-header">
                      <div class="card-title">Cache Statistics</div>
                    </div>
                    <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:16px;padding:16px;" id="cacheStats">
                      <div class="stat-card"><div class="stat-label">Total Caches</div><div class="stat-value" id="cacheTotalCount">-</div></div>
                      <div class="stat-card"><div class="stat-label">Total Entries</div><div class="stat-value" id="cacheTotalEntries">-</div></div>
                      <div class="stat-card"><div class="stat-label">Overall Hit Rate</div><div class="stat-value" id="cacheOverallHitRate">-</div></div>
                      <div class="stat-card"><div class="stat-label">Memory Used</div><div class="stat-value" id="cacheMemUsed">-</div></div>
                    </div>
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
                      <button class="btn btn-primary" onclick="createDataSource()">Create</button>
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
                    var tabId = tab.dataset.tab;
                    document.getElementById('tab-' + tabId).classList.add('active');
                    if (tabId === 'jms') loadJmsServers();
                    if (tabId === 'jndi') loadJndiBindings();
                    if (tabId === 'connpool') loadConnPools();
                    if (tabId === 'cache') loadCaches();
                  });
                });

                function execCmd(command) {
                  return fetch(CTX + '/api/execute', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({command: command})
                  }).then(function(r){ return r.json(); });
                }

                function refreshMemory() {
                  execCmd('memoryinfo').then(function(d) {
                    showToast(d.message, d.success ? 'success' : 'error');
                    if (d.success) setTimeout(function(){ location.reload(); }, 1000);
                  });
                }

                function requestGC() {
                  if (!confirm('Request garbage collection?')) return;
                  execCmd('gc').then(function(d) {
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

                /* --- JMS --- */
                function loadJmsServers() {
                  var alert = document.getElementById('jmsServerAlert');
                  var table = document.getElementById('jmsServerTable');
                  var tbody = document.getElementById('jmsServerTbody');
                  alert.style.display = 'none';
                  table.style.display = 'none';
                  tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text2);">Loading...</td></tr>';
                  table.style.display = '';
                  execCmd('list-jms-servers').then(function(d) {
                    var servers = d.data || d.result || [];
                    if (!Array.isArray(servers) || servers.length === 0) {
                      table.style.display = 'none';
                      alert.style.display = '';
                      return;
                    }
                    var html = '';
                    servers.forEach(function(s) {
                      html += '<tr><td><strong>' + (s.name || '-') + '</strong></td>'
                        + '<td>' + (s.type || '-') + '</td>'
                        + '<td><span class="badge ' + (s.status === 'Running' ? 'badge-success' : 'badge-neutral') + '">' + (s.status || '-') + '</span></td>'
                        + '<td>' + (s.messages != null ? s.messages : '-') + '</td>'
                        + '<td>' + (s.consumers != null ? s.consumers : '-') + '</td></tr>';
                    });
                    tbody.innerHTML = html;
                  }).catch(function() {
                    table.style.display = 'none';
                    alert.textContent = 'Failed to load JMS servers.';
                    alert.style.display = '';
                  });
                }

                function loadJmsDestinations() {
                  var alert = document.getElementById('jmsDestAlert');
                  var table = document.getElementById('jmsDestTable');
                  var tbody = document.getElementById('jmsDestTbody');
                  alert.style.display = 'none';
                  table.style.display = 'none';
                  tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text2);">Loading...</td></tr>';
                  table.style.display = '';
                  execCmd('list-jms-destinations').then(function(d) {
                    var dests = d.data || d.result || [];
                    if (!Array.isArray(dests) || dests.length === 0) {
                      table.style.display = 'none';
                      alert.textContent = 'No JMS destinations found.';
                      alert.style.display = '';
                      return;
                    }
                    var html = '';
                    dests.forEach(function(dest) {
                      html += '<tr><td><strong>' + (dest.name || '-') + '</strong></td>'
                        + '<td>' + (dest.type || '-') + '</td>'
                        + '<td>' + (dest.messagesPending != null ? dest.messagesPending : '-') + '</td>'
                        + '<td>' + (dest.consumers != null ? dest.consumers : '-') + '</td>'
                        + '<td><div class="btn-group">';
                      if (dest.type === 'Queue' || dest.type === 'queue') {
                        html += '<button class="btn btn-sm btn-danger" onclick="purgeJmsQueue(\\'' + (dest.name || '').replace(/'/g, "\\\\'") + '\\')">Purge</button>';
                      }
                      html += '</div></td></tr>';
                    });
                    tbody.innerHTML = html;
                  }).catch(function() {
                    table.style.display = 'none';
                    alert.textContent = 'Failed to load JMS destinations.';
                    alert.style.display = '';
                  });
                }

                function purgeJmsQueue(name) {
                  if (!confirm('Purge all messages from queue "' + name + '"?')) return;
                  execCmd('purge-jms-queue ' + name).then(function(d) {
                    showToast(d.message || 'Queue purged', d.success ? 'success' : 'error');
                    loadJmsDestinations();
                  }).catch(function() {
                    showToast('Failed to purge queue', 'error');
                  });
                }

                /* --- JNDI --- */
                function loadJndiBindings() {
                  var alert = document.getElementById('jndiAlert');
                  var table = document.getElementById('jndiTable');
                  var tbody = document.getElementById('jndiTbody');
                  alert.style.display = 'none';
                  table.style.display = 'none';
                  tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;color:var(--text2);">Loading...</td></tr>';
                  table.style.display = '';
                  execCmd('list-jndi-entries').then(function(d) {
                    var bindings = d.data || d.result || [];
                    if (!Array.isArray(bindings) || bindings.length === 0) {
                      table.style.display = 'none';
                      alert.style.display = '';
                      return;
                    }
                    var html = '';
                    bindings.forEach(function(b) {
                      var safeName = (b.name || '').replace(/'/g, "\\\\'");
                      html += '<tr><td><strong>' + (b.name || '-') + '</strong></td>'
                        + '<td><code>' + (b.type || '-') + '</code></td>'
                        + '<td>' + (b.boundObject || b.value || '-') + '</td>'
                        + '<td><button class="btn btn-sm" onclick="verifyJndiRef(\\'' + safeName + '\\')">Verify Reference</button></td></tr>';
                    });
                    tbody.innerHTML = html;
                  }).catch(function() {
                    table.style.display = 'none';
                    alert.textContent = 'Failed to load JNDI bindings.';
                    alert.style.display = '';
                  });
                }

                function verifyJndiRef(name) {
                  execCmd('lookup-jndi ' + name).then(function(d) {
                    showToast(d.message || ('JNDI lookup for "' + name + '": ' + (d.success ? 'OK' : 'Failed')), d.success ? 'success' : 'error');
                  }).catch(function() {
                    showToast('Failed to verify JNDI reference "' + name + '"', 'error');
                  });
                }

                /* --- Connection Pools --- */
                function loadConnPools() {
                  var alert = document.getElementById('connpoolAlert');
                  var table = document.getElementById('connpoolTable');
                  var tbody = document.getElementById('connpoolTbody');
                  alert.style.display = 'none';
                  table.style.display = 'none';
                  tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--text2);">Loading...</td></tr>';
                  table.style.display = '';
                  execCmd('list-jdbc-resources').then(function(d) {
                    var pools = d.data || d.result || [];
                    if (!Array.isArray(pools) || pools.length === 0) {
                      table.style.display = 'none';
                      alert.style.display = '';
                      return;
                    }
                    var html = '';
                    pools.forEach(function(p) {
                      var active = p.active != null ? p.active : 0;
                      var max = p.max != null ? p.max : 0;
                      var leakWarning = (max > 0 && active >= max * 0.9)
                        ? ' <span class="badge badge-warning" title="Possible connection leak: active connections near maximum">Leak Warning</span>'
                        : '';
                      var safeName = (p.name || '').replace(/'/g, "\\\\'");
                      html += '<tr><td><strong>' + (p.name || '-') + '</strong></td>'
                        + '<td>' + active + '</td>'
                        + '<td>' + (p.idle != null ? p.idle : '-') + '</td>'
                        + '<td>' + max + '</td>'
                        + '<td>' + (p.waitCount != null ? p.waitCount : '-') + '</td>'
                        + '<td>' + (p.avgWaitTime != null ? p.avgWaitTime + ' ms' : '-') + '</td>'
                        + '<td>' + leakWarning + '</td>'
                        + '<td><div class="btn-group">'
                        + '<button class="btn btn-sm" onclick="resetConnPool(\\'' + safeName + '\\')">Reset</button>'
                        + '<button class="btn btn-sm btn-danger" onclick="flushConnPool(\\'' + safeName + '\\')">Flush</button>'
                        + '</div></td></tr>';
                    });
                    tbody.innerHTML = html;
                  }).catch(function() {
                    table.style.display = 'none';
                    alert.textContent = 'Failed to load connection pools.';
                    alert.style.display = '';
                  });
                }

                function resetConnPool(name) {
                  if (!confirm('Reset connection pool "' + name + '"?')) return;
                  execCmd('reset-connection-pool ' + name).then(function(d) {
                    showToast(d.message || 'Pool reset', d.success ? 'success' : 'error');
                    loadConnPools();
                  }).catch(function() {
                    showToast('Failed to reset pool', 'error');
                  });
                }

                function flushConnPool(name) {
                  if (!confirm('Flush all connections in pool "' + name + '"? Active connections will be closed.')) return;
                  execCmd('flush-connection-pool ' + name).then(function(d) {
                    showToast(d.message || 'Pool flushed', d.success ? 'success' : 'error');
                    loadConnPools();
                  }).catch(function() {
                    showToast('Failed to flush pool', 'error');
                  });
                }

                /* --- DataSources --- */
                var dataSources = JSON.parse(localStorage.getItem('velo-datasources') || '[]');

                function renderDataSources() {
                  var alert = document.querySelector('#tab-jdbc .alert');
                  var table = document.querySelector('#tab-jdbc .data-table');
                  var tbody = table.querySelector('tbody');
                  if (dataSources.length === 0) {
                    alert.style.display = '';
                    table.style.display = 'none';
                    return;
                  }
                  alert.style.display = 'none';
                  table.style.display = '';
                  var html = '';
                  dataSources.forEach(function(ds, i) {
                    html += '<tr><td><strong>' + ds.name + '</strong></td>'
                      + '<td><code>' + ds.jndi + '</code></td>'
                      + '<td>' + ds.driver + '</td>'
                      + '<td>0 / 0 / ' + ds.maxPool + '</td>'
                      + '<td><span class="badge badge-success">Active</span></td>'
                      + '<td><div class="btn-group">'
                      + '<button class="btn btn-sm" onclick="testDs(' + i + ')">Test</button>'
                      + '<button class="btn btn-sm btn-danger" onclick="removeDs(' + i + ')">Remove</button>'
                      + '</div></td></tr>';
                  });
                  tbody.innerHTML = html;
                }

                function createDataSource() {
                  var inputs = document.querySelectorAll('#addDsModal .form-input');
                  var name = inputs[0].value;
                  var jndi = inputs[1].value;
                  var url = inputs[2].value;
                  var driver = inputs[3].value;
                  if (!name || !url) { showToast('Name and JDBC URL are required', 'warning'); return; }
                  dataSources.push({
                    name: name, jndi: jndi || 'jdbc/' + name, url: url,
                    driver: driver || '-', user: inputs[4].value,
                    minPool: inputs[6] ? inputs[6].value : 5,
                    maxPool: inputs[7] ? inputs[7].value : 20
                  });
                  localStorage.setItem('velo-datasources', JSON.stringify(dataSources));
                  document.getElementById('addDsModal').classList.remove('open');
                  renderDataSources();
                  showToast('DataSource "' + name + '" created', 'success');
                }

                function testDs(i) {
                  var dsName = dataSources[i].name;
                  showToast('Testing connection to ' + dsName + '...', 'info');
                  execCmd('test-datasource ' + dsName).then(function(d) {
                    showToast(d.message || ('Connection test for "' + dsName + '": ' + (d.success ? 'Success' : 'Failed')), d.success ? 'success' : 'error');
                  }).catch(function() {
                    showToast('Failed to test DataSource "' + dsName + '"', 'error');
                  });
                }

                function removeDs(i) {
                  if (!confirm('Remove DataSource "' + dataSources[i].name + '"?')) return;
                  dataSources.splice(i, 1);
                  localStorage.setItem('velo-datasources', JSON.stringify(dataSources));
                  renderDataSources();
                  showToast('DataSource removed', 'success');
                }

                /* --- Cache --- */
                function loadCaches() {
                  var alert = document.getElementById('cacheAlert');
                  var table = document.getElementById('cacheTable');
                  var tbody = document.getElementById('cacheTbody');
                  alert.style.display = 'none';
                  table.style.display = 'none';
                  tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;color:var(--text2);">Loading...</td></tr>';
                  table.style.display = '';
                  execCmd('list-caches').then(function(d) {
                    var caches = d.data || d.result || [];
                    if (!Array.isArray(caches) || caches.length === 0) {
                      table.style.display = 'none';
                      alert.textContent = 'No caches configured. Cache will appear when applications use caching.';
                      alert.style.display = '';
                      document.getElementById('cacheTotalCount').textContent = '0';
                      document.getElementById('cacheTotalEntries').textContent = '0';
                      document.getElementById('cacheOverallHitRate').textContent = '-';
                      document.getElementById('cacheMemUsed').textContent = '-';
                      return;
                    }
                    var html = '';
                    var totalEntries = 0, totalHits = 0, totalMisses = 0;
                    caches.forEach(function(c) {
                      var hits = c.hits || 0;
                      var misses = c.misses || 0;
                      var size = c.size || 0;
                      var hitRate = (hits + misses) > 0 ? ((hits / (hits + misses)) * 100).toFixed(1) : '-';
                      totalEntries += size;
                      totalHits += hits;
                      totalMisses += misses;
                      var safeName = (c.name || '').replace(/'/g, "\\\\'");
                      html += '<tr><td><strong>' + (c.name || '-') + '</strong></td>'
                        + '<td>' + (c.type || 'Local') + '</td>'
                        + '<td>' + size + (c.maxSize ? ' / ' + c.maxSize : '') + '</td>'
                        + '<td>' + (hitRate !== '-' ? hitRate + '%%' : '-') + '</td>'
                        + '<td>' + hits + ' / ' + misses + '</td>'
                        + '<td>' + (c.evictions || 0) + '</td>'
                        + '<td>' + (c.ttl ? c.ttl + 's' : 'N/A') + '</td>'
                        + '<td><div class="btn-group">'
                        + '<button class="btn btn-sm" onclick="cacheStats(\\'' + safeName + '\\')">Stats</button>'
                        + '<button class="btn btn-sm btn-danger" onclick="clearCache(\\'' + safeName + '\\')">Clear</button>'
                        + '</div></td></tr>';
                    });
                    tbody.innerHTML = html;
                    document.getElementById('cacheTotalCount').textContent = caches.length;
                    document.getElementById('cacheTotalEntries').textContent = totalEntries.toLocaleString();
                    var overallRate = (totalHits + totalMisses) > 0 ? ((totalHits / (totalHits + totalMisses)) * 100).toFixed(1) + '%%' : '-';
                    document.getElementById('cacheOverallHitRate').textContent = overallRate;
                    document.getElementById('cacheMemUsed').textContent = d.memoryUsed || '-';
                  }).catch(function() {
                    table.style.display = 'none';
                    alert.textContent = 'Failed to load cache information.';
                    alert.style.display = '';
                  });
                }

                function clearCache(name) {
                  if (!confirm('Clear all entries from cache "' + name + '"?')) return;
                  execCmd('clear-cache ' + name).then(function(d) {
                    showToast(d.message || 'Cache cleared', d.success ? 'success' : 'error');
                    loadCaches();
                  }).catch(function() {
                    showToast('Failed to clear cache', 'error');
                  });
                }

                function clearAllCaches() {
                  if (!confirm('Clear ALL caches? This will remove all cached data.')) return;
                  execCmd('clear-all-caches').then(function(d) {
                    showToast(d.message || 'All caches cleared', d.success ? 'success' : 'error');
                    loadCaches();
                  }).catch(function() {
                    showToast('Failed to clear caches', 'error');
                  });
                }

                function cacheStats(name) {
                  execCmd('cache-stats ' + name).then(function(d) {
                    var msg = d.message || JSON.stringify(d.data || d.result || {}, null, 2);
                    showToast('Stats for "' + name + '": ' + msg, 'info');
                  }).catch(function() {
                    showToast('Failed to get cache stats', 'error');
                  });
                }

                renderDataSources();
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
