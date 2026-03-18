package io.velo.was.webadmin.servlet.page;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Application management page.
 * Lists deployed applications and provides install/deploy/undeploy/start/stop/rollback controls.
 */
public class ApplicationsPageServlet extends HttpServlet {

    private static final String CSRF_TOKEN_ATTR = "velo.csrf.token";
    private final ServerConfiguration configuration;

    public ApplicationsPageServlet(ServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        ServerConfiguration.Server server = configuration.getServer();
        String ctx = req.getContextPath();
        String csrfToken = "";
        if (req.getSession(false) != null) {
            Object token = req.getSession(false).getAttribute(CSRF_TOKEN_ATTR);
            csrfToken = token != null ? token.toString() : "";
        }

        String body = """
                <style>
                .deploy-progress { display:none; margin:16px 0; }
                .deploy-progress.active { display:block; }
                .deploy-steps { display:flex; gap:4px; align-items:center; margin:12px 0; }
                .deploy-step { padding:6px 14px; border-radius:20px; font-size:12px; font-weight:500;
                               background:var(--surface2); color:var(--text3); transition:all 0.3s; }
                .deploy-step.active { background:var(--primary-bg); color:var(--primary); }
                .deploy-step.done { background:var(--success-bg); color:var(--success); }
                .deploy-step.error { background:var(--danger-bg); color:var(--danger); }
                .deploy-arrow { color:var(--text3); font-size:12px; }
                .spinner { display:inline-block; width:14px; height:14px; border:2px solid var(--border);
                           border-top-color:var(--primary); border-radius:50%%; animation:spin 0.6s linear infinite; vertical-align:middle; }
                @keyframes spin { to { transform:rotate(360deg); } }
                .log-viewer { background:var(--bg); border:1px solid var(--border); border-radius:var(--radius-sm);
                              padding:12px; font-family:monospace; font-size:12px; max-height:300px; overflow:auto;
                              white-space:pre-wrap; color:var(--text2); }
                .history-list { list-style:none; padding:0; margin:0; }
                .history-list li { padding:6px 0; border-bottom:1px solid var(--border); font-size:13px; }
                .history-list li:last-child { border-bottom:none; }
                .confirm-modal .risk-indicator { display:inline-block; padding:2px 10px; border-radius:4px;
                                                 font-size:12px; margin-left:8px; }
                .confirm-modal .risk-indicator.high { background:var(--danger-bg); color:var(--danger); }
                .confirm-modal .risk-indicator.medium { background:var(--warning-bg); color:var(--warning); }
                .confirm-modal .risk-indicator.low { background:var(--success-bg); color:var(--success); }
                </style>

                <div class="page-header">
                  <div>
                    <div class="page-title" data-i18n="page.applications">Applications</div>
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
                        <th>Name</th><th>Context Path</th><th>Version</th><th>Type</th><th>Status</th>
                        <th>Servlets</th><th>Filters</th><th>Actions</th>
                      </tr>
                    </thead>
                    <tbody id="appsTbody">
                      <tr><td colspan="8" style="text-align:center;color:var(--text2);">Loading...</td></tr>
                    </tbody>
                  </table>
                </div>

                <!-- Deployment Progress Card -->
                <div class="card deploy-progress" id="deployProgressCard" style="margin-top:24px;">
                  <div class="card-title">Deployment Progress</div>
                  <div class="deploy-steps" id="deploySteps">
                    <span class="deploy-step" id="step-upload">Uploading</span>
                    <span class="deploy-arrow">&rarr;</span>
                    <span class="deploy-step" id="step-validate">Validating</span>
                    <span class="deploy-arrow">&rarr;</span>
                    <span class="deploy-step" id="step-deploy">Deploying</span>
                    <span class="deploy-arrow">&rarr;</span>
                    <span class="deploy-step" id="step-start">Starting</span>
                    <span class="deploy-arrow">&rarr;</span>
                    <span class="deploy-step" id="step-running">Running</span>
                  </div>
                  <div id="deployStatusMsg" style="font-size:13px;color:var(--text2);"></div>
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
                    <form id="uploadForm" enctype="multipart/form-data">
                      <div class="form-group">
                        <label class="form-label">WAR File</label>
                        <input class="form-input" type="file" name="file" accept=".war" required>
                      </div>
                      <div class="form-group">
                        <label class="form-label">Context Path (optional)</label>
                        <input class="form-input" type="text" name="contextPath" placeholder="Auto-derived from filename">
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

                <!-- Rollback Confirmation Modal -->
                <div class="modal-overlay" id="rollbackModal">
                  <div class="modal confirm-modal">
                    <div class="modal-title">Rollback Application</div>
                    <div style="margin:16px 0;">
                      <span id="rollbackMsg">Rollback to previous version?</span>
                      <span class="risk-indicator medium">Medium Risk</span>
                    </div>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('rollbackModal').classList.remove('open')">Cancel</button>
                      <button class="btn btn-primary" id="rollbackConfirmBtn" onclick="executeRollback()">Rollback</button>
                    </div>
                  </div>
                </div>

                <!-- App Log Viewer Modal -->
                <div class="modal-overlay" id="logViewerModal">
                  <div class="modal" style="max-width:700px;">
                    <div class="modal-title">Application Log — <span id="logAppName"></span></div>
                    <div class="log-viewer" id="logContent">Loading...</div>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('logViewerModal').classList.remove('open')">Close</button>
                      <button class="btn btn-sm" id="logRefreshBtn" onclick="refreshLog()">Refresh</button>
                    </div>
                  </div>
                </div>

                <!-- Deployment History Modal -->
                <div class="modal-overlay" id="historyModal">
                  <div class="modal">
                    <div class="modal-title">Deployment History — <span id="historyAppName"></span></div>
                    <ul class="history-list" id="historyList">
                      <li>Loading...</li>
                    </ul>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('historyModal').classList.remove('open')">Close</button>
                    </div>
                  </div>
                </div>

                <script>
                var CTX = '%s';
                var CSRF_TOKEN = '%s';
                var UPLOAD_ENDPOINT = CTX + '/upload-war';
                var rollbackTarget = null;
                var logTarget = null;
                function esc(s) { if(!s) return ''; var d=document.createElement('div'); d.textContent=s; return d.innerHTML; }
                function readJsonResponse(response) {
                  return response.text().then(function(text) {
                    if (!text || !text.trim()) {
                      return {success: response.ok, message: response.ok ? '' : ('HTTP ' + response.status)};
                    }
                    try {
                      return JSON.parse(text);
                    } catch (e) {
                      return {success: false, message: text.trim() || e.message};
                    }
                  });
                }

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
                      var version = a.version || '-';
                      var actions = '';
                      if (a.name === 'velo-webadmin') {
                        actions = '<span style="color:var(--text3);font-size:12px;">System app</span>';
                      } else {
                        actions = '<div class="btn-group">'
                          + '<button class="btn btn-sm" onclick="appAction(\\'start-application\\',\\'' + esc(a.name) + '\\')">Start</button>'
                          + '<button class="btn btn-sm" onclick="appAction(\\'stop-application\\',\\'' + esc(a.name) + '\\')">Stop</button>'
                          + '<button class="btn btn-sm" onclick="appAction(\\'redeploy\\',\\'' + esc(a.name) + '\\')">Redeploy</button>'
                          + '<button class="btn btn-sm" onclick="appAction(\\'reload-context\\',\\'' + esc(a.name) + '\\')">Reload</button>'
                          + '<button class="btn btn-sm" onclick="showRollback(\\'' + esc(a.name) + '\\',\\'' + esc(version) + '\\')">Rollback</button>'
                          + '<button class="btn btn-sm" onclick="showLog(\\'' + esc(a.name) + '\\')">Log</button>'
                          + '<button class="btn btn-sm" onclick="showHistory(\\'' + esc(a.name) + '\\')">History</button>'
                          + '<button class="btn btn-sm btn-danger" onclick="appAction(\\'undeploy\\',\\'' + esc(a.name) + '\\')">Undeploy</button>'
                          + '</div>';
                      }
                      html += '<tr><td><strong>' + esc(a.name) + '</strong></td>'
                        + '<td><code>' + esc(a.contextPath) + '</code></td>'
                        + '<td>' + esc(version) + '</td>'
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

                // Rollback
                function showRollback(appName, currentVersion) {
                  rollbackTarget = appName;
                  document.getElementById('rollbackMsg').textContent =
                    'Rollback "' + appName + '" from version ' + currentVersion + ' to previous version?';
                  document.getElementById('rollbackModal').classList.add('open');
                }
                function executeRollback() {
                  if (!rollbackTarget) return;
                  document.getElementById('rollbackModal').classList.remove('open');
                  appAction('rollback', rollbackTarget);
                  rollbackTarget = null;
                }

                // Log viewer
                function showLog(appName) {
                  logTarget = appName;
                  document.getElementById('logAppName').textContent = appName;
                  document.getElementById('logContent').textContent = 'Loading...';
                  document.getElementById('logViewerModal').classList.add('open');
                  refreshLog();
                }
                function refreshLog() {
                  if (!logTarget) return;
                  fetch(CTX + '/api/execute', {
                    method:'POST',
                    headers:{'Content-Type':'application/json'},
                    body:JSON.stringify({command:'app-log ' + logTarget})
                  }).then(function(r){return r.json();}).then(function(d){
                    document.getElementById('logContent').textContent = d.message || '(no log output)';
                  }).catch(function(){
                    document.getElementById('logContent').textContent = '(failed to load log)';
                  });
                }

                // Deployment history
                function showHistory(appName) {
                  document.getElementById('historyAppName').textContent = appName;
                  document.getElementById('historyList').innerHTML = '<li>Loading...</li>';
                  document.getElementById('historyModal').classList.add('open');
                  fetch(CTX + '/api/execute', {
                    method:'POST',
                    headers:{'Content-Type':'application/json'},
                    body:JSON.stringify({command:'deploy-history ' + appName})
                  }).then(function(r){return r.json();}).then(function(d){
                    var msg = d.message || '';
                    var lines = msg.split('\\n').filter(function(l){return l.trim();});
                    if (lines.length === 0) {
                      document.getElementById('historyList').innerHTML = '<li style="color:var(--text3);">No deployment history available</li>';
                      return;
                    }
                    var html = '';
                    lines.slice(0,5).forEach(function(l){ html += '<li>' + esc(l) + '</li>'; });
                    document.getElementById('historyList').innerHTML = html;
                  }).catch(function(){
                    document.getElementById('historyList').innerHTML = '<li style="color:var(--danger);">Failed to load history</li>';
                  });
                }

                // Deploy progress helpers
                function showDeployProgress() {
                  var card = document.getElementById('deployProgressCard');
                  card.classList.add('active');
                  resetDeploySteps();
                }
                function resetDeploySteps() {
                  ['upload','validate','deploy','start','running'].forEach(function(s){
                    document.getElementById('step-' + s).className = 'deploy-step';
                  });
                  document.getElementById('deployStatusMsg').textContent = '';
                }
                function setDeployStep(stepId, state) {
                  document.getElementById('step-' + stepId).className = 'deploy-step ' + state;
                }
                function advanceDeploySteps(steps, index, callback) {
                  if (index >= steps.length) { if(callback) callback(); return; }
                  setDeployStep(steps[index], 'active');
                  document.getElementById('deployStatusMsg').innerHTML = '<span class="spinner"></span> ' + steps[index].charAt(0).toUpperCase() + steps[index].slice(1) + '...';
                  setTimeout(function(){
                    setDeployStep(steps[index], 'done');
                    advanceDeploySteps(steps, index + 1, callback);
                  }, 600);
                }

                document.getElementById('directDeployForm').addEventListener('submit', function(e) {
                  e.preventDefault();
                  var inputs = this.querySelectorAll('.form-input');
                  var path = inputs[0].value;
                  var ctxPath = inputs[1].value;
                  var cmd = 'deploy ' + path + (ctxPath ? ' ' + ctxPath : '');
                  showDeployProgress();
                  advanceDeploySteps(['validate','deploy','start'], 0, function(){
                    fetch(CTX + '/api/execute', {
                      method: 'POST',
                      headers: {'Content-Type':'application/json'},
                      body: JSON.stringify({command: cmd})
                    }).then(function(r){return r.json();}).then(function(d){
                      if (d.success) {
                        setDeployStep('running','done');
                        document.getElementById('deployStatusMsg').textContent = 'Deployment complete';
                      } else {
                        setDeployStep('running','error');
                        document.getElementById('deployStatusMsg').textContent = d.message || 'Deployment failed';
                      }
                      showToast(d.message, d.success ? 'success' : 'error');
                      document.getElementById('directDeployModal').classList.remove('open');
                      if(d.success) loadApps();
                    });
                  });
                });

                // WAR Upload form handler — posts multipart to the applications page doPost handler
                document.getElementById('uploadForm').addEventListener('submit', function(e) {
                  e.preventDefault();
                  var fileInput = this.querySelector('input[type="file"]');
                  var ctxInput = this.querySelector('input[name="contextPath"]');
                  if (!fileInput.files.length) { showToast('Please select a WAR file', 'warning'); return; }
                  var formData = new FormData();
                  formData.append('file', fileInput.files[0]);
                  if (ctxInput && ctxInput.value) formData.append('contextPath', ctxInput.value);
                  showDeployProgress();
                  setDeployStep('upload','active');
                  document.getElementById('deployStatusMsg').innerHTML = '<span class="spinner"></span> Uploading WAR file...';
                  fetch(UPLOAD_ENDPOINT, {
                    method: 'POST',
                    headers: {'X-CSRF-Token': CSRF_TOKEN},
                    body: formData
                  }).then(readJsonResponse).then(function(d) {
                    setDeployStep('upload','done');
                    if (d.success) {
                      advanceDeploySteps(['validate','deploy','start','running'], 0, function(){
                        document.getElementById('deployStatusMsg').textContent = 'Deployment complete';
                      });
                    } else {
                      setDeployStep('validate','error');
                      document.getElementById('deployStatusMsg').textContent = d.message || 'Deployment failed';
                    }
                    showToast(d.message || (d.success ? 'Deployed successfully' : 'Deploy failed'), d.success ? 'success' : 'error');
                    document.getElementById('deployModal').classList.remove('open');
                    if (d.success) loadApps();
                  }).catch(function(err) {
                    setDeployStep('upload','error');
                    document.getElementById('deployStatusMsg').textContent = 'Upload failed: ' + err.message;
                    showToast('Upload failed. Check server logs.', 'error');
                  });
                });

                loadApps();
                </script>
                """.formatted(
                h(server.getDeploy().getDirectory()),
                server.getDeploy().isHotDeploy() ? "<span class='badge badge-success'>Enabled</span>" : "<span class='badge badge-neutral'>Disabled</span>",
                server.getDeploy().getScanIntervalSeconds(),
                h(server.getName()),
                ctx,
                escapeJson(csrfToken)
        );

        resp.getWriter().write(AdminPageLayout.page("Applications", server.getName(), server.getNodeId(),
                ctx, "applications", body));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json; charset=UTF-8");

        String contentType = req.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("multipart/")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"success\":false,\"message\":\"Expected multipart/form-data\"}");
            return;
        }

        try {
            Part filePart = req.getPart("file");
            if (filePart == null || filePart.getSize() == 0) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"success\":false,\"message\":\"No WAR file provided\"}");
                return;
            }

            String fileName = filePart.getSubmittedFileName();
            if (fileName == null || !fileName.toLowerCase().endsWith(".war")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"success\":false,\"message\":\"Only .war files are accepted\"}");
                return;
            }

            // Sanitize filename
            fileName = Path.of(fileName).getFileName().toString();

            // Resolve deploy directory and ensure it exists
            String deployDir = configuration.getServer().getDeploy().getDirectory();
            Path deployPath = Path.of(deployDir).toAbsolutePath();
            if (!Files.exists(deployPath)) {
                Files.createDirectories(deployPath);
            }

            Path targetFile = deployPath.resolve(fileName);

            // Write the uploaded WAR to the deploy directory
            try (InputStream in = filePart.getInputStream();
                 OutputStream out = Files.newOutputStream(targetFile)) {
                in.transferTo(out);
            }

            resp.getWriter().write("{\"success\":true,\"message\":\"WAR file '%s' uploaded to deploy directory (%d bytes)\"}".formatted(
                    escapeJson(fileName), filePart.getSize()));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"success\":false,\"message\":\"Upload failed: %s\"}".formatted(
                    escapeJson(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
        }
    }

    private static String h(String s) { return AdminPageLayout.escapeHtml(s); }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
