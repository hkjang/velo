package io.velo.was.webadmin.servlet.page;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Application management page.
 * Lists deployed applications and provides install/deploy/undeploy/start/stop/rollback controls.
 */
public class ApplicationsPageServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public ApplicationsPageServlet(ServerConfiguration configuration) {
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
                    <div class="page-title">Applications</div>
                    <div class="page-subtitle">Deploy, manage, and monitor applications</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn" onclick="document.getElementById('deployModal').classList.add('open')">Upload WAR</button>
                    <button class="btn btn-primary" onclick="document.getElementById('directDeployModal').classList.add('open')">Deploy</button>
                  </div>
                </div>

                <div class="grid grid-4" style="margin-bottom:24px;">
                  <div class="card">
                    <div class="card-title">Total Apps</div>
                    <div class="metric-value" style="font-size:32px;" id="app-total">-</div>
                  </div>
                  <div class="card">
                    <div class="card-title">Running</div>
                    <div class="metric-value success" style="font-size:32px;" id="app-running">-</div>
                  </div>
                  <div class="card">
                    <div class="card-title">Stopped</div>
                    <div class="metric-value" style="font-size:32px;color:var(--text3);" id="app-stopped">0</div>
                  </div>
                  <div class="card">
                    <div class="card-title">Failed</div>
                    <div class="metric-value danger" style="font-size:32px;" id="app-failed">0</div>
                  </div>
                </div>

                <div class="card">
                  <div class="card-header">
                    <div class="card-title">Deployed Applications</div>
                    <div class="btn-group">
                      <button class="btn btn-sm" onclick="loadApps()">Refresh</button>
                    </div>
                  </div>
                  <table class="data-table">
                    <thead>
                      <tr>
                        <th>Name</th><th>Context Path</th><th>Type</th><th>Status</th>
                        <th>Servlets</th><th>Filters</th><th>Actions</th>
                      </tr>
                    </thead>
                    <tbody id="appsTbody">
                      <tr><td colspan="7" style="text-align:center;color:var(--text2);">Loading...</td></tr>
                    </tbody>
                  </table>
                </div>

                <div style="margin-top:24px;" class="grid grid-2">
                  <div class="card">
                    <div class="card-title">Deployment Configuration</div>
                    <table class="info-table">
                      <tr><td>Deploy Directory</td><td><code>%s</code></td></tr>
                      <tr><td>Hot Deploy</td><td>%s</td></tr>
                      <tr><td>Scan Interval</td><td>%ds</td></tr>
                    </table>
                  </div>
                  <div class="card">
                    <div class="card-title">Deployment Strategy</div>
                    <table class="info-table">
                      <tr><td>Rolling Deploy</td><td><span class="badge badge-neutral">Planned</span></td></tr>
                      <tr><td>Blue-Green</td><td><span class="badge badge-neutral">Planned</span></td></tr>
                      <tr><td>Canary</td><td><span class="badge badge-neutral">Planned</span></td></tr>
                    </table>
                  </div>
                </div>

                <!-- Deploy Modal -->
                <div class="modal-overlay" id="deployModal">
                  <div class="modal">
                    <div class="modal-title">Upload WAR File</div>
                    <form id="uploadForm">
                      <div class="form-group">
                        <label class="form-label">WAR File</label>
                        <input class="form-input" type="file" accept=".war" required>
                      </div>
                      <div class="form-group">
                        <label class="form-label">Context Path (optional)</label>
                        <input class="form-input" type="text" placeholder="Auto-derived from filename">
                        <div class="form-hint">Leave empty to use filename as context path</div>
                      </div>
                      <div class="form-group">
                        <label class="form-label">Target Server</label>
                        <select class="form-select"><option>%s (current)</option></select>
                      </div>
                      <div class="modal-footer">
                        <button class="btn" type="button" onclick="document.getElementById('deployModal').classList.remove('open')">Cancel</button>
                        <button class="btn btn-primary" type="submit">Deploy</button>
                      </div>
                    </form>
                  </div>
                </div>

                <!-- Direct Deploy Modal -->
                <div class="modal-overlay" id="directDeployModal">
                  <div class="modal">
                    <div class="modal-title">Direct Deploy</div>
                    <form id="directDeployForm">
                      <div class="form-group">
                        <label class="form-label">Application Path</label>
                        <input class="form-input" type="text" placeholder="/path/to/app.war or /path/to/exploded-dir" required>
                        <div class="form-hint">Server file path or repository path</div>
                      </div>
                      <div class="form-group">
                        <label class="form-label">Context Path (optional)</label>
                        <input class="form-input" type="text" placeholder="Auto-derived from filename">
                      </div>
                      <div class="modal-footer">
                        <button class="btn" type="button" onclick="document.getElementById('directDeployModal').classList.remove('open')">Cancel</button>
                        <button class="btn btn-primary" type="submit">Deploy</button>
                      </div>
                    </form>
                  </div>
                </div>

                <script>
                var CTX = '%s';
                function esc(s) { if(!s) return ''; var d=document.createElement('div'); d.textContent=s; return d.innerHTML; }

                function loadApps() {
                  fetch(CTX + '/api/applications').then(function(r){return r.json();}).then(function(d) {
                    var apps = d.applications || [];
                    var tb = document.getElementById('appsTbody');
                    var totalEl = document.getElementById('app-total');
                    var runEl = document.getElementById('app-running');
                    if (totalEl) totalEl.textContent = apps.length;
                    if (runEl) runEl.textContent = apps.filter(function(a){return a.status==='RUNNING';}).length;
                    var stoppedEl = document.getElementById('app-stopped');
                    var failedEl = document.getElementById('app-failed');
                    if (stoppedEl) stoppedEl.textContent = apps.filter(function(a){return a.status==='STOPPED';}).length;
                    if (failedEl) failedEl.textContent = apps.filter(function(a){return a.status==='FAILED';}).length;
                    var html = '';
                    apps.forEach(function(a) {
                      var statusBadge = a.status === 'RUNNING' ? 'badge-success' : a.status === 'STOPPED' ? 'badge-neutral' : 'badge-danger';
                      var typeBadge = a.type === 'INTERNAL' ? 'badge-info' : 'badge-warning';
                      var actions = '';
                      if (a.name === 'velo-webadmin') {
                        actions = '<span style="color:var(--text3);font-size:12px;">System app</span>';
                      } else {
                        actions = '<div class="btn-group">'
                          + '<button class="btn btn-sm" onclick="appAction(\\'stop-application\\',\\'' + a.name + '\\')">Stop</button>'
                          + '<button class="btn btn-sm" onclick="appAction(\\'redeploy\\',\\'' + a.name + '\\')">Redeploy</button>'
                          + '<button class="btn btn-sm btn-danger" onclick="appAction(\\'undeploy\\',\\'' + a.name + '\\')">Undeploy</button>'
                          + '</div>';
                      }
                      html += '<tr><td><strong>' + esc(a.name) + '</strong></td>'
                        + '<td><code>' + esc(a.contextPath) + '</code></td>'
                        + '<td><span class="badge ' + typeBadge + '">' + esc(a.type) + '</span></td>'
                        + '<td><span class="badge ' + statusBadge + '">' + esc(a.status) + '</span></td>'
                        + '<td>' + (a.servletCount != null ? a.servletCount : '-') + '</td>'
                        + '<td>' + (a.filterCount != null ? a.filterCount : '-') + '</td>'
                        + '<td>' + actions + '</td></tr>';
                    });
                    tb.innerHTML = html;
                  }).catch(function(){});
                }

                function appAction(cmd, appName) {
                  if (cmd === 'undeploy' && !confirm('Undeploy ' + appName + '?')) return;
                  fetch(CTX + '/api/execute', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({command: cmd + ' ' + appName})
                  }).then(function(r){return r.json();}).then(function(d){
                    showToast(d.message, d.success ? 'success' : 'error');
                    if(d.success) loadApps();
                  });
                }

                document.getElementById('directDeployForm').addEventListener('submit', function(e) {
                  e.preventDefault();
                  var inputs = this.querySelectorAll('.form-input');
                  var path = inputs[0].value;
                  var ctxPath = inputs[1].value;
                  var cmd = 'deploy ' + path + (ctxPath ? ' ' + ctxPath : '');
                  fetch(CTX + '/api/execute', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({command: cmd})
                  }).then(function(r){return r.json();}).then(function(d){
                    showToast(d.message, d.success ? 'success' : 'error');
                    document.getElementById('directDeployModal').classList.remove('open');
                    if(d.success) loadApps();
                  });
                });

                // WAR Upload form handler
                document.getElementById('uploadForm').addEventListener('submit', function(e) {
                  e.preventDefault();
                  var fileInput = this.querySelector('input[type="file"]');
                  var ctxInput = this.querySelectorAll('.form-input')[1];
                  if (!fileInput.files.length) { showToast('Please select a WAR file', 'warning'); return; }
                  var formData = new FormData();
                  formData.append('file', fileInput.files[0]);
                  if (ctxInput && ctxInput.value) formData.append('contextPath', ctxInput.value);
                  showToast('Uploading WAR file...', 'info');
                  fetch(CTX + '/api/deploy/upload', {
                    method: 'POST',
                    body: formData
                  }).then(function(r){return r.json();}).then(function(d) {
                    showToast(d.message || (d.success ? 'Deployed successfully' : 'Deploy failed'), d.success ? 'success' : 'error');
                    document.getElementById('deployModal').classList.remove('open');
                    if (d.success) loadApps();
                  }).catch(function(err) {
                    // Fallback: deploy via copy to deploy dir
                    var fileName = fileInput.files[0].name;
                    var ctxPath = ctxInput && ctxInput.value ? ctxInput.value : '/' + fileName.replace('.war', '');
                    showToast('Upload endpoint not available. Use Direct Deploy with server file path instead.', 'warning');
                  });
                });

                loadApps();
                </script>
                """.formatted(
                h(server.getDeploy().getDirectory()),
                server.getDeploy().isHotDeploy() ? "<span class='badge badge-success'>Enabled</span>" : "<span class='badge badge-neutral'>Disabled</span>",
                server.getDeploy().getScanIntervalSeconds(),
                h(server.getName()),
                ctx
        );

        resp.getWriter().write(AdminPageLayout.page("Applications", server.getName(), server.getNodeId(),
                ctx, "applications", body));
    }

    private static String h(String s) { return AdminPageLayout.escapeHtml(s); }
}
