package io.velo.was.webadmin.servlet.page;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Cluster management page.
 * Cluster overview, membership, rolling operations, distributed settings, HA and scaling control.
 */
public class ClustersPageServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public ClustersPageServlet(ServerConfiguration configuration) {
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
                    <div class="page-title" data-i18n="page.clusters">Clusters</div>
                    <div class="page-subtitle">HA clustering, membership management, and distributed operations</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn btn-primary" onclick="document.getElementById('createClusterModal').classList.add('open')">Create Cluster</button>
                  </div>
                </div>

                <div class="grid grid-3" style="margin-bottom:24px;">
                  <div class="card">
                    <div class="card-title">Clusters</div>
                    <div class="metric-value" style="font-size:32px;" id="cl-total">0</div>
                  </div>
                  <div class="card">
                    <div class="card-title">Total Members</div>
                    <div class="metric-value" style="font-size:32px;" id="cl-members">0</div>
                  </div>
                  <div class="card">
                    <div class="card-title">Session Replication</div>
                    <div class="metric-value" style="font-size:28px;color:var(--text3);">N/A</div>
                  </div>
                </div>

                <div class="card">
                  <div class="card-header">
                    <div class="card-title">Cluster Instances</div>
                    <button class="btn btn-sm" onclick="loadClusters()">Refresh</button>
                  </div>
                  <div class="alert alert-info" id="cl-empty">
                    No clusters have been created yet. Create a cluster to enable high availability,
                    load balancing, and rolling deployments across multiple server instances.
                  </div>
                  <table class="data-table" id="cl-table" style="display:none;">
                    <thead>
                      <tr><th>Cluster Name</th><th>Members</th><th>Status</th><th>Actions</th></tr>
                    </thead>
                    <tbody id="clTbody"></tbody>
                  </table>
                </div>

                <!-- Cluster Detail Panel -->
                <div class="card" style="margin-top:16px;" id="clusterDetailPanel" style="display:none;">
                  <div class="card-header">
                    <div class="card-title">Cluster Detail: <span id="clDetailName">-</span></div>
                    <div class="btn-group">
                      <button class="btn btn-sm btn-primary" onclick="document.getElementById('addMemberModal').classList.add('open')">Add Member</button>
                    </div>
                  </div>
                  <div class="tabs" id="clDetailTabs" style="margin-bottom:0;">
                    <div class="tab active" data-tab="clMembers">Members</div>
                    <div class="tab" data-tab="clSession">Session Replication</div>
                    <div class="tab" data-tab="clOps">Rolling Operations</div>
                    <div class="tab" data-tab="clRef">CLI Reference</div>
                  </div>
                  <div class="tab-panel active" id="tab-clMembers">
                    <table class="data-table" style="margin-top:12px;">
                      <thead><tr><th>Server</th><th>Node</th><th>Status</th><th>Heap</th><th>Threads</th><th>Actions</th></tr></thead>
                      <tbody id="clMembersTbody">
                        <tr><td colspan="6" style="text-align:center;color:var(--text2);">Select a cluster</td></tr>
                      </tbody>
                    </table>
                  </div>
                  <div class="tab-panel" id="tab-clSession">
                    <div style="margin-top:12px;">
                      <div class="grid grid-3" style="margin-bottom:16px;">
                        <div class="card" style="padding:12px;">
                          <div class="card-title" style="font-size:11px;">Replication Mode</div>
                          <div style="font-size:16px;font-weight:600;" id="clSessMode">N/A</div>
                        </div>
                        <div class="card" style="padding:12px;">
                          <div class="card-title" style="font-size:11px;">Active Sessions</div>
                          <div class="metric-value sm" id="clSessCount">0</div>
                        </div>
                        <div class="card" style="padding:12px;">
                          <div class="card-title" style="font-size:11px;">Replication Lag</div>
                          <div style="font-size:16px;font-weight:600;color:var(--success);" id="clSessLag">&lt; 1ms</div>
                        </div>
                      </div>
                      <div class="alert alert-info">Session replication status will be available when a cluster with session replication is configured.</div>
                    </div>
                  </div>
                  <div class="tab-panel" id="tab-clOps">
                    <div style="margin-top:12px;">
                      <div class="card-title">Rolling Operation Controls</div>
                      <p style="color:var(--text2);font-size:13px;margin:8px 0 16px;">
                        Perform operations on all cluster members sequentially, ensuring service availability throughout.
                      </p>
                      <div class="grid grid-2" style="gap:12px;">
                        <div style="padding:16px;background:var(--surface2);border-radius:8px;">
                          <strong>Rolling Restart</strong>
                          <p style="font-size:12px;color:var(--text2);margin:4px 0 8px;">Restart each member one by one, waiting for health check before proceeding.</p>
                          <div class="btn-group">
                            <button class="btn btn-sm btn-primary" onclick="rollingOp('restart')">Start Rolling Restart</button>
                          </div>
                        </div>
                        <div style="padding:16px;background:var(--surface2);border-radius:8px;">
                          <strong>Rolling Deploy</strong>
                          <p style="font-size:12px;color:var(--text2);margin:4px 0 8px;">Deploy application to each member sequentially with traffic drain.</p>
                          <div class="btn-group">
                            <button class="btn btn-sm" onclick="rollingOp('deploy')">Start Rolling Deploy</button>
                          </div>
                        </div>
                        <div style="padding:16px;background:var(--surface2);border-radius:8px;">
                          <strong>Drain &amp; Maintain</strong>
                          <p style="font-size:12px;color:var(--text2);margin:4px 0 8px;">Drain traffic from a member, perform maintenance, then re-enable.</p>
                          <div class="btn-group">
                            <button class="btn btn-sm" onclick="rollingOp('drain')">Select Member to Drain</button>
                          </div>
                        </div>
                        <div style="padding:16px;background:var(--surface2);border-radius:8px;">
                          <strong>Scale Out</strong>
                          <p style="font-size:12px;color:var(--text2);margin:4px 0 8px;">Add new server instance to the cluster dynamically.</p>
                          <div class="btn-group">
                            <button class="btn btn-sm" onclick="document.getElementById('addMemberModal').classList.add('open')">Add Member</button>
                          </div>
                        </div>
                      </div>
                      <div id="rollingProgress" style="display:none;margin-top:16px;">
                        <div class="alert alert-info">
                          <span style="display:inline-block;width:14px;height:14px;border:2px solid var(--border);border-top-color:var(--primary);border-radius:50%%;animation:spin 0.6s linear infinite;vertical-align:middle;margin-right:8px;"></span>
                          <span id="rollingProgressText">Operation in progress...</span>
                        </div>
                      </div>
                    </div>
                  </div>
                  <div class="tab-panel" id="tab-clRef">
                    <table class="data-table" style="margin-top:12px;">
                      <thead><tr><th>Operation</th><th>CLI Command</th><th>Description</th></tr></thead>
                      <tbody>
                        <tr><td>Create Cluster</td><td><code>createcluster &lt;name&gt;</code></td><td>Create a new cluster</td></tr>
                        <tr><td>Add Member</td><td><code>addservertocluster &lt;server&gt; &lt;cluster&gt;</code></td><td>Add server to cluster</td></tr>
                        <tr><td>Remove Member</td><td><code>removeserverfromcluster &lt;server&gt; &lt;cluster&gt;</code></td><td>Remove server from cluster</td></tr>
                        <tr><td>Rolling Restart</td><td><code>restartcluster &lt;name&gt;</code></td><td>Rolling restart all members</td></tr>
                        <tr><td>Stop Cluster</td><td><code>stopcluster &lt;name&gt;</code></td><td>Stop all cluster members</td></tr>
                        <tr><td>Start Cluster</td><td><code>startcluster &lt;name&gt;</code></td><td>Start all cluster members</td></tr>
                        <tr><td>Suspend (Drain)</td><td><code>suspendserver &lt;name&gt;</code></td><td>Drain traffic before maintenance</td></tr>
                        <tr><td>Resume</td><td><code>resumeserver &lt;name&gt;</code></td><td>Resume traffic after maintenance</td></tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <!-- Add Member Modal -->
                <div class="modal-overlay" id="addMemberModal">
                  <div class="modal">
                    <div class="modal-title">Add Member to Cluster</div>
                    <div class="form-group">
                      <label class="form-label">Server Name</label>
                      <select class="form-select" id="memberServerSelect">
                        <option>Loading servers...</option>
                      </select>
                    </div>
                    <div class="form-group">
                      <label class="form-label">Target Cluster</label>
                      <input class="form-input" type="text" id="memberClusterName" readonly>
                    </div>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('addMemberModal').classList.remove('open')">Cancel</button>
                      <button class="btn btn-primary" onclick="addMemberToCluster()">Add Member</button>
                    </div>
                  </div>
                </div>

                <!-- Create Cluster Modal -->
                <div class="modal-overlay" id="createClusterModal">
                  <div class="modal">
                    <div class="modal-title">Create Cluster</div>
                    <div class="form-group">
                      <label class="form-label">Cluster Name</label>
                      <input class="form-input" type="text" placeholder="e.g. production-cluster">
                    </div>
                    <div class="form-group">
                      <label class="form-label">Session Replication</label>
                      <select class="form-select">
                        <option>None</option>
                        <option>In-Memory</option>
                        <option>Database-backed</option>
                      </select>
                    </div>
                    <div class="form-group">
                      <label class="form-label">Load Balancing Policy</label>
                      <select class="form-select">
                        <option>Round Robin</option>
                        <option>Least Connections</option>
                        <option>Sticky Session</option>
                      </select>
                    </div>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('createClusterModal').classList.remove('open')">Cancel</button>
                      <button class="btn btn-primary" id="createClusterBtn">Create</button>
                    </div>
                  </div>
                </div>

                <script>
                var CTX = '%s';
                function esc(s) { if(!s) return ''; var d=document.createElement('div'); d.textContent=s; return d.innerHTML; }

                var selectedCluster = null;

                // Cluster detail tabs
                document.querySelectorAll('#clDetailTabs .tab').forEach(function(tab){
                  tab.addEventListener('click', function(){
                    document.querySelectorAll('#clDetailTabs .tab').forEach(function(t){t.classList.remove('active');});
                    document.querySelectorAll('#clusterDetailPanel .tab-panel').forEach(function(p){p.classList.remove('active');});
                    tab.classList.add('active');
                    document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
                  });
                });

                function loadClusters() {
                  fetch(CTX + '/api/clusters').then(function(r){return r.json();}).then(function(d) {
                    var clusters = d.clusters || [];
                    document.getElementById('cl-total').textContent = clusters.length;
                    var totalMembers = 0;
                    clusters.forEach(function(c){ totalMembers += c.memberCount || 0; });
                    document.getElementById('cl-members').textContent = totalMembers;
                    if (clusters.length === 0) {
                      document.getElementById('cl-empty').style.display = '';
                      document.getElementById('cl-table').style.display = 'none';
                      document.getElementById('clusterDetailPanel').style.display = 'none';
                    } else {
                      document.getElementById('cl-empty').style.display = 'none';
                      document.getElementById('cl-table').style.display = '';
                      document.getElementById('clusterDetailPanel').style.display = '';
                      var tb = document.getElementById('clTbody');
                      var html = '';
                      clusters.forEach(function(c) {
                        var badge = c.status === 'RUNNING' ? 'badge-success' : 'badge-neutral';
                        html += '<tr style="cursor:pointer;" onclick="selectCluster(\\'' + esc(c.name) + '\\')">'
                          + '<td><strong>' + esc(c.name) + '</strong></td>'
                          + '<td>' + c.memberCount + '</td>'
                          + '<td><span class="badge ' + badge + '">' + esc(c.status) + '</span></td>'
                          + '<td><div class="btn-group">'
                          + '<button class="btn btn-sm" onclick="event.stopPropagation();clusterAction(\\'startcluster\\',\\'' + esc(c.name) + '\\')">Start</button>'
                          + '<button class="btn btn-sm" onclick="event.stopPropagation();clusterAction(\\'restartcluster\\',\\'' + esc(c.name) + '\\')">Rolling Restart</button>'
                          + '<button class="btn btn-sm btn-danger" onclick="event.stopPropagation();clusterAction(\\'stopcluster\\',\\'' + esc(c.name) + '\\')">Stop</button>'
                          + '</div></td></tr>';
                      });
                      tb.innerHTML = html;
                      if (!selectedCluster && clusters.length > 0) selectCluster(clusters[0].name);
                    }
                  }).catch(function(){});
                }

                function selectCluster(name) {
                  selectedCluster = name;
                  document.getElementById('clDetailName').textContent = name;
                  document.getElementById('memberClusterName').value = name;
                  loadClusterMembers(name);
                  loadAvailableServers();
                }

                function loadClusterMembers(name) {
                  fetch(CTX + '/api/servers').then(function(r){return r.json();}).then(function(d) {
                    var servers = d.servers || [];
                    var tb = document.getElementById('clMembersTbody');
                    if (servers.length === 0) {
                      tb.innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--text2);">No members in cluster</td></tr>';
                      return;
                    }
                    var html = '';
                    servers.forEach(function(s) {
                      var badge = s.status === 'RUNNING' ? 'badge-success' : s.status === 'STOPPED' ? 'badge-neutral' : 'badge-danger';
                      var heapPct = s.heapPct || '-';
                      html += '<tr><td><strong>' + esc(s.name) + '</strong></td>'
                        + '<td>' + esc(s.nodeId || '-') + '</td>'
                        + '<td><span class="badge ' + badge + '">' + esc(s.status) + '</span></td>'
                        + '<td>' + heapPct + '</td>'
                        + '<td>' + (s.workerThreads || '-') + '</td>'
                        + '<td><div class="btn-group">'
                        + '<button class="btn btn-sm" onclick="clusterAction(\\'suspendserver\\',\\'' + esc(s.name) + '\\')">Drain</button>'
                        + '<button class="btn btn-sm" onclick="clusterAction(\\'resumeserver\\',\\'' + esc(s.name) + '\\')">Resume</button>'
                        + '<button class="btn btn-sm btn-danger" onclick="removeMember(\\'' + esc(s.name) + '\\')">Remove</button>'
                        + '</div></td></tr>';
                    });
                    tb.innerHTML = html;
                  }).catch(function(){
                    document.getElementById('clMembersTbody').innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--text2);">Failed to load members</td></tr>';
                  });
                }

                function loadAvailableServers() {
                  fetch(CTX + '/api/servers').then(function(r){return r.json();}).then(function(d) {
                    var servers = d.servers || [];
                    var sel = document.getElementById('memberServerSelect');
                    sel.innerHTML = '';
                    servers.forEach(function(s) {
                      var opt = document.createElement('option');
                      opt.value = s.name; opt.textContent = s.name + ' (' + s.status + ')';
                      sel.appendChild(opt);
                    });
                    if (servers.length === 0) sel.innerHTML = '<option>No servers available</option>';
                  }).catch(function(){});
                }

                function addMemberToCluster() {
                  var server = document.getElementById('memberServerSelect').value;
                  var cluster = document.getElementById('memberClusterName').value;
                  if (!server || !cluster) { showToast('Server and cluster required', 'warning'); return; }
                  fetch(CTX + '/api/execute', {
                    method:'POST', headers:{'Content-Type':'application/json'},
                    body:JSON.stringify({command:'add-server-to-cluster ' + server + ' ' + cluster})
                  }).then(function(r){return r.json();}).then(function(d){
                    showToast(d.message, d.success ? 'success' : 'error');
                    document.getElementById('addMemberModal').classList.remove('open');
                    if (d.success) loadClusters();
                  });
                }

                function removeMember(server) {
                  if (!selectedCluster || !confirm('Remove ' + server + ' from cluster ' + selectedCluster + '?')) return;
                  fetch(CTX + '/api/execute', {
                    method:'POST', headers:{'Content-Type':'application/json'},
                    body:JSON.stringify({command:'remove-server-from-cluster ' + server + ' ' + selectedCluster})
                  }).then(function(r){return r.json();}).then(function(d){
                    showToast(d.message, d.success ? 'success' : 'error');
                    if (d.success) loadClusterMembers(selectedCluster);
                  });
                }

                function rollingOp(type) {
                  if (!selectedCluster) { showToast('Select a cluster first', 'warning'); return; }
                  var cmds = {restart:'restartcluster', deploy:'restartcluster', drain:'suspendserver'};
                  var labels = {restart:'Rolling Restart', deploy:'Rolling Deploy', drain:'Drain'};
                  if (!confirm('Start ' + labels[type] + ' on cluster ' + selectedCluster + '?')) return;
                  document.getElementById('rollingProgress').style.display = '';
                  document.getElementById('rollingProgressText').textContent = labels[type] + ' in progress for ' + selectedCluster + '...';
                  fetch(CTX + '/api/execute', {
                    method:'POST', headers:{'Content-Type':'application/json'},
                    body:JSON.stringify({command:cmds[type] + ' ' + selectedCluster})
                  }).then(function(r){return r.json();}).then(function(d){
                    document.getElementById('rollingProgress').style.display = 'none';
                    showToast(d.message, d.success ? 'success' : 'error');
                    loadClusters();
                  }).catch(function(){
                    document.getElementById('rollingProgress').style.display = 'none';
                  });
                }

                function clusterAction(cmd, name) {
                  fetch(CTX + '/api/execute', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({command: cmd + ' ' + name})
                  }).then(function(r){return r.json();}).then(function(d) {
                    showToast(d.message, d.success ? 'success' : 'error');
                    if (d.success) loadClusters();
                  });
                }

                document.getElementById('createClusterBtn').addEventListener('click', function() {
                  var name = document.querySelector('#createClusterModal .form-input').value;
                  if (!name) { showToast('Cluster name is required', 'warning'); return; }
                  fetch(CTX + '/api/execute', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({command: 'createcluster ' + name})
                  }).then(function(r){return r.json();}).then(function(d) {
                    showToast(d.message, d.success ? 'success' : 'error');
                    if (d.success) {
                      document.getElementById('createClusterModal').classList.remove('open');
                      loadClusters();
                    }
                  });
                });

                loadClusters();
                </script>
                """.formatted(ctx);

        resp.getWriter().write(AdminPageLayout.page("Clusters", server.getName(), server.getNodeId(),
                ctx, "clusters", body));
    }
}
