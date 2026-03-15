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
                    <div class="page-title">Clusters</div>
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

                <div class="card" style="margin-top:16px;">
                  <div class="card-title">Cluster Operations Reference</div>
                  <div style="margin-top:12px;">
                    <table class="data-table">
                      <thead><tr><th>Operation</th><th>CLI Command</th><th>Description</th></tr></thead>
                      <tbody>
                        <tr><td>Create Cluster</td><td><code>createcluster &lt;name&gt;</code></td><td>Create a new cluster</td></tr>
                        <tr><td>Add Member</td><td><code>addservertocluster &lt;server&gt; &lt;cluster&gt;</code></td><td>Add server to cluster</td></tr>
                        <tr><td>Remove Member</td><td><code>removeserverfromcluster &lt;server&gt; &lt;cluster&gt;</code></td><td>Remove server from cluster</td></tr>
                        <tr><td>Rolling Restart</td><td><code>restartcluster &lt;name&gt;</code></td><td>Rolling restart all members</td></tr>
                        <tr><td>Stop Cluster</td><td><code>stopcluster &lt;name&gt;</code></td><td>Stop all cluster members</td></tr>
                        <tr><td>Drain</td><td><code>suspendserver &lt;name&gt;</code></td><td>Drain traffic before maintenance</td></tr>
                      </tbody>
                    </table>
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
                    } else {
                      document.getElementById('cl-empty').style.display = 'none';
                      document.getElementById('cl-table').style.display = '';
                      var tb = document.getElementById('clTbody');
                      var html = '';
                      clusters.forEach(function(c) {
                        var badge = c.status === 'RUNNING' ? 'badge-success' : 'badge-neutral';
                        html += '<tr><td><strong>' + esc(c.name) + '</strong></td>'
                          + '<td>' + c.memberCount + '</td>'
                          + '<td><span class="badge ' + badge + '">' + esc(c.status) + '</span></td>'
                          + '<td><div class="btn-group">'
                          + '<button class="btn btn-sm" onclick="clusterAction(\\'restartcluster\\',\\'' + esc(c.name) + '\\')">Rolling Restart</button>'
                          + '<button class="btn btn-sm btn-danger" onclick="clusterAction(\\'stopcluster\\',\\'' + esc(c.name) + '\\')">Stop</button>'
                          + '</div></td></tr>';
                      });
                      tb.innerHTML = html;
                    }
                  }).catch(function(){});
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
