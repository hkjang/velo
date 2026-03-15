package io.velo.was.webadmin.servlet.page;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Node management page.
 * Physical or logical node inventory, agent status, and node lifecycle.
 */
public class NodesPageServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public NodesPageServlet(ServerConfiguration configuration) {
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
                    <div class="page-title">Nodes</div>
                    <div class="page-subtitle">Physical and logical node management, agent status</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn btn-primary" onclick="document.getElementById('addNodeModal').classList.add('open')">Register Node</button>
                  </div>
                </div>

                <div class="grid grid-3" style="margin-bottom:24px;">
                  <div class="card">
                    <div class="card-title">Total Nodes</div>
                    <div class="metric-value" style="font-size:32px;" id="nd-total">-</div>
                  </div>
                  <div class="card">
                    <div class="card-title">Online</div>
                    <div class="metric-value success" style="font-size:32px;" id="nd-online">-</div>
                  </div>
                  <div class="card">
                    <div class="card-title">Offline</div>
                    <div class="metric-value" style="font-size:32px;color:var(--text3);" id="nd-offline">0</div>
                  </div>
                </div>

                <div class="card">
                  <div class="card-header">
                    <div class="card-title">Node Inventory</div>
                    <button class="btn btn-sm" onclick="loadNodes()">Refresh</button>
                  </div>
                  <table class="data-table">
                    <thead>
                      <tr><th>Node ID</th><th>Host</th><th>OS</th><th>CPUs</th><th>Status</th><th>Agent</th><th>Servers</th><th>Actions</th></tr>
                    </thead>
                    <tbody id="nodesTbody">
                      <tr><td colspan="8" style="text-align:center;color:var(--text2);">Loading...</td></tr>
                    </tbody>
                  </table>
                </div>

                <div class="card" style="margin-top:16px;">
                  <div class="card-title" id="nd-detail-title">Node Detail</div>
                  <div class="grid grid-2" style="margin-top:12px;">
                    <div>
                      <table class="info-table" id="nd-detail-left">
                        <tr><td colspan="2" style="color:var(--text2);">Select a node to view details</td></tr>
                      </table>
                    </div>
                    <div>
                      <table class="info-table" id="nd-detail-right"></table>
                    </div>
                  </div>
                </div>

                <!-- Add Node Modal -->
                <div class="modal-overlay" id="addNodeModal">
                  <div class="modal">
                    <div class="modal-title">Register Node</div>
                    <div class="form-group">
                      <label class="form-label">Node ID</label>
                      <input class="form-input" type="text" placeholder="e.g. node-2">
                    </div>
                    <div class="form-group">
                      <label class="form-label">Host Address</label>
                      <input class="form-input" type="text" placeholder="e.g. 192.168.1.100">
                    </div>
                    <div class="form-group">
                      <label class="form-label">Agent Port</label>
                      <input class="form-input" type="number" value="9736" placeholder="9736">
                      <div class="form-hint">Port where the node agent is listening</div>
                    </div>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('addNodeModal').classList.remove('open')">Cancel</button>
                      <button class="btn btn-primary">Register</button>
                    </div>
                  </div>
                </div>

                <script>
                var CTX = '%s';
                function esc(s) { if(!s) return ''; var d=document.createElement('div'); d.textContent=s; return d.innerHTML; }

                function loadNodes() {
                  fetch(CTX + '/api/nodes').then(function(r){return r.json();}).then(function(d) {
                    var nodes = d.nodes || [];
                    document.getElementById('nd-total').textContent = nodes.length;
                    var online = nodes.filter(function(n){return n.status==='ONLINE';}).length;
                    document.getElementById('nd-online').textContent = online;
                    document.getElementById('nd-offline').textContent = nodes.length - online;
                    var tb = document.getElementById('nodesTbody');
                    var html = '';
                    nodes.forEach(function(n) {
                      var srvNames = (n.servers || []).map(function(s){return s.name;}).join(', ') || '-';
                      html += '<tr><td><strong>' + esc(n.nodeId) + '</strong></td>'
                        + '<td>' + esc(n.host) + '</td>'
                        + '<td>' + esc(n.os) + '</td>'
                        + '<td>' + n.cpus + '</td>'
                        + '<td><span class="badge badge-success">' + esc(n.status) + '</span></td>'
                        + '<td><span class="badge badge-success">Connected</span></td>'
                        + '<td>' + (n.servers ? n.servers.length : 0) + '</td>'
                        + '<td><button class="btn btn-sm" onclick="showDetail(' + JSON.stringify(JSON.stringify(n)).slice(1,-1) + ')">Detail</button></td></tr>';
                    });
                    if (!html) html = '<tr><td colspan="8" style="text-align:center;color:var(--text2);">No nodes found</td></tr>';
                    tb.innerHTML = html;
                    if (nodes.length > 0) showDetail(JSON.stringify(nodes[0]));
                  }).catch(function(){});
                }

                function showDetail(jsonStr) {
                  var n = JSON.parse(jsonStr);
                  document.getElementById('nd-detail-title').textContent = 'Node Detail: ' + n.nodeId;
                  document.getElementById('nd-detail-left').innerHTML =
                    '<tr><td>Node ID</td><td>' + esc(n.nodeId) + '</td></tr>'
                    + '<tr><td>Hostname</td><td>' + esc(n.host) + '</td></tr>'
                    + '<tr><td>OS</td><td>' + esc(n.os) + ' (' + esc(n.arch) + ')</td></tr>'
                    + '<tr><td>Java</td><td>' + esc(n.java) + '</td></tr>'
                    + '<tr><td>CPUs</td><td>' + n.cpus + '</td></tr>';
                  var srvs = (n.servers || []).map(function(s){return s.name + ' on port ' + s.port;}).join(', ') || '-';
                  document.getElementById('nd-detail-right').innerHTML =
                    '<tr><td>Server Instances</td><td>' + esc(srvs) + '</td></tr>'
                    + '<tr><td>TCP Listeners</td><td>' + (n.tcpListeners || 0) + '</td></tr>'
                    + '<tr><td>Agent Status</td><td><span class="badge badge-success">Connected</span></td></tr>';
                }

                loadNodes();
                </script>
                """.formatted(ctx);

        resp.getWriter().write(AdminPageLayout.page("Nodes", server.getName(), server.getNodeId(),
                ctx, "nodes", body));
    }
}
