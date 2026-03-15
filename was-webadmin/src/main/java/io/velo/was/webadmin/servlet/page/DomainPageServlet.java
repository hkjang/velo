package io.velo.was.webadmin.servlet.page;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Domain management page.
 * Global domain settings, environment profiles, variables, and multi-domain management.
 */
public class DomainPageServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public DomainPageServlet(ServerConfiguration configuration) {
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
                    <div class="page-title">Domain</div>
                    <div class="page-subtitle">Domain configuration, environment variables, and profiles</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn btn-sm" onclick="document.getElementById('editDomainModal').classList.add('open')">Edit Configuration</button>
                  </div>
                </div>

                <div class="tabs" id="domTabs">
                  <div class="tab active" data-tab="config">Configuration</div>
                  <div class="tab" data-tab="env">Environment Variables</div>
                  <div class="tab" data-tab="profiles">Profiles</div>
                </div>

                <div class="tab-panel active" id="tab-config">
                  <div class="grid grid-2">
                    <div class="card">
                      <div class="card-title">Domain Information</div>
                      <table class="info-table" style="margin-top:8px;">
                        <tr><td>Domain Name</td><td>%s</td></tr>
                        <tr><td>Node ID</td><td>%s</td></tr>
                        <tr><td>Config File</td><td><code>conf/server.yaml</code></td></tr>
                        <tr><td>Graceful Shutdown</td><td>%dms</td></tr>
                      </table>
                    </div>
                    <div class="card">
                      <div class="card-title">Listener</div>
                      <table class="info-table" style="margin-top:8px;">
                        <tr><td>Host</td><td>%s</td></tr>
                        <tr><td>Port</td><td>%d</td></tr>
                        <tr><td>Backlog</td><td>%d</td></tr>
                        <tr><td>Max Content Length</td><td>%s</td></tr>
                        <tr><td>Max Header Size</td><td>%d bytes</td></tr>
                        <tr><td>Idle Timeout</td><td>%ds</td></tr>
                      </table>
                    </div>
                  </div>
                  <div class="grid grid-2" style="margin-top:16px;">
                    <div class="card">
                      <div class="card-title">Session</div>
                      <table class="info-table" style="margin-top:8px;">
                        <tr><td>Timeout</td><td>%ds</td></tr>
                        <tr><td>Purge Interval</td><td>%ds</td></tr>
                      </table>
                    </div>
                    <div class="card">
                      <div class="card-title">Compression</div>
                      <table class="info-table" style="margin-top:8px;">
                        <tr><td>Enabled</td><td>%s</td></tr>
                        <tr><td>Min Response Size</td><td>%d bytes</td></tr>
                        <tr><td>Level</td><td>%d</td></tr>
                        <tr><td>MIME Types</td><td style="font-size:12px;">%s</td></tr>
                      </table>
                    </div>
                  </div>
                  <div class="grid grid-2" style="margin-top:16px;">
                    <div class="card">
                      <div class="card-title">JSP Engine</div>
                      <table class="info-table" style="margin-top:8px;">
                        <tr><td>Scratch Dir</td><td><code>%s</code></td></tr>
                        <tr><td>Development Mode</td><td>%s</td></tr>
                        <tr><td>Precompile</td><td>%s</td></tr>
                        <tr><td>Reload Interval</td><td>%ds</td></tr>
                        <tr><td>Page Encoding</td><td>%s</td></tr>
                        <tr><td>Buffer Size</td><td>%d bytes</td></tr>
                      </table>
                    </div>
                    <div class="card">
                      <div class="card-title">Deploy</div>
                      <table class="info-table" style="margin-top:8px;">
                        <tr><td>Directory</td><td><code>%s</code></td></tr>
                        <tr><td>Hot Deploy</td><td>%s</td></tr>
                        <tr><td>Scan Interval</td><td>%ds</td></tr>
                      </table>
                    </div>
                  </div>
                  <div class="card" style="margin-top:16px;">
                    <div class="card-header">
                      <div class="card-title">Raw Configuration (YAML)</div>
                      <button class="btn btn-sm" onclick="loadConfig()">Reload from Disk</button>
                    </div>
                    <pre id="yamlView" style="font-family:'JetBrains Mono',monospace;font-size:12px;
                         background:var(--bg);padding:16px;border-radius:8px;max-height:400px;
                         overflow:auto;line-height:1.6;margin-top:8px;white-space:pre-wrap;">%s</pre>
                  </div>
                </div>

                <div class="tab-panel" id="tab-env">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Environment Variables</div>
                      <button class="btn btn-sm btn-primary" onclick="document.getElementById('addVarModal').classList.add('open')">Add Variable</button>
                    </div>
                    <table class="data-table">
                      <thead><tr><th>Key</th><th>Value</th><th>Scope</th><th>Actions</th></tr></thead>
                      <tbody>
                        <tr><td>JAVA_HOME</td><td><code>%s</code></td><td><span class="badge badge-neutral">System</span></td><td>-</td></tr>
                        <tr><td>server.name</td><td><code>%s</code></td><td><span class="badge badge-info">Domain</span></td><td><button class="btn btn-sm">Edit</button></td></tr>
                        <tr><td>server.nodeId</td><td><code>%s</code></td><td><span class="badge badge-info">Domain</span></td><td><button class="btn btn-sm">Edit</button></td></tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-profiles">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Configuration Profiles</div>
                      <button class="btn btn-sm btn-primary" onclick="document.getElementById('addProfileModal').classList.add('open')">Create Profile</button>
                    </div>
                    <table class="data-table">
                      <thead><tr><th>Profile</th><th>Description</th><th>Status</th><th>Actions</th></tr></thead>
                      <tbody>
                        <tr>
                          <td><strong>default</strong></td>
                          <td>Default server configuration</td>
                          <td><span class="badge badge-success">Active</span></td>
                          <td><button class="btn btn-sm">View</button></td>
                        </tr>
                      </tbody>
                    </table>
                    <div class="alert alert-info" style="margin-top:16px;">
                      Profiles allow maintaining multiple configurations (development, staging, production)
                      with environment-specific overrides.
                    </div>
                  </div>
                </div>

                <!-- Edit Domain Modal -->
                <div class="modal-overlay" id="editDomainModal">
                  <div class="modal" style="min-width:600px;">
                    <div class="modal-title">Edit Domain Configuration</div>
                    <div class="alert alert-warning">
                      Configuration changes follow the Draft &rarr; Validate &rarr; Review &rarr; Apply workflow.
                      Changes will be staged as a draft first.
                    </div>
                    <div class="form-group">
                      <label class="form-label">Server Name</label>
                      <input class="form-input" type="text" value="%s">
                    </div>
                    <div class="form-group">
                      <label class="form-label">Node ID</label>
                      <input class="form-input" type="text" value="%s">
                    </div>
                    <div class="form-group">
                      <label class="form-label">Graceful Shutdown (ms)</label>
                      <input class="form-input" type="number" value="%d">
                    </div>
                    <div class="form-group">
                      <label class="form-label">Change Description</label>
                      <textarea class="form-input" rows="3" placeholder="Describe the reason for this change..."></textarea>
                    </div>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('editDomainModal').classList.remove('open')">Cancel</button>
                      <button class="btn" onclick="validateDomainConfig()">Validate</button>
                      <button class="btn btn-primary">Save as Draft</button>
                    </div>
                  </div>
                </div>

                <script>
                var CTX = '%s';
                document.querySelectorAll('#domTabs .tab').forEach(function(tab){
                  tab.addEventListener('click', function(){
                    document.querySelectorAll('#domTabs .tab').forEach(function(t){t.classList.remove('active');});
                    document.querySelectorAll('.tab-panel').forEach(function(p){p.classList.remove('active');});
                    tab.classList.add('active');
                    document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
                  });
                });

                function loadConfig() {
                  fetch(CTX + '/api/config').then(function(r){return r.json();}).then(function(d) {
                    if (d.content) {
                      document.getElementById('yamlView').textContent = d.content;
                    } else if (d.error) {
                      document.getElementById('yamlView').textContent = 'Error: ' + d.error;
                    }
                  }).catch(function(e) {
                    document.getElementById('yamlView').textContent = 'Failed to load config';
                  });
                }

                // Add Variable Modal & Profile Modal (dynamically created)
                document.body.insertAdjacentHTML('beforeend',
                  '<div class="modal-overlay" id="addVarModal">' +
                  '<div class="modal">' +
                  '<div class="modal-title">Add Environment Variable</div>' +
                  '<div class="form-group"><label class="form-label">Key</label>' +
                  '<input class="form-input" type="text" id="varKey" placeholder="e.g. app.timeout"></div>' +
                  '<div class="form-group"><label class="form-label">Value</label>' +
                  '<input class="form-input" type="text" id="varValue" placeholder="e.g. 30"></div>' +
                  '<div class="form-group"><label class="form-label">Scope</label>' +
                  '<select class="form-select" id="varScope"><option>Domain</option><option>Server</option></select></div>' +
                  '<div class="modal-footer">' +
                  '<button class="btn" onclick="document.getElementById(\\'addVarModal\\').classList.remove(\\'open\\')">Cancel</button>' +
                  '<button class="btn btn-primary" onclick="addVariable()">Add</button>' +
                  '</div></div></div>' +
                  '<div class="modal-overlay" id="addProfileModal">' +
                  '<div class="modal">' +
                  '<div class="modal-title">Create Configuration Profile</div>' +
                  '<div class="form-group"><label class="form-label">Profile Name</label>' +
                  '<input class="form-input" type="text" id="profileName" placeholder="e.g. production"></div>' +
                  '<div class="form-group"><label class="form-label">Description</label>' +
                  '<input class="form-input" type="text" id="profileDesc" placeholder="Profile description"></div>' +
                  '<div class="form-group"><label class="form-label">Base Profile</label>' +
                  '<select class="form-select"><option>default</option></select></div>' +
                  '<div class="modal-footer">' +
                  '<button class="btn" onclick="document.getElementById(\\'addProfileModal\\').classList.remove(\\'open\\')">Cancel</button>' +
                  '<button class="btn btn-primary" onclick="createProfile()">Create</button>' +
                  '</div></div></div>'
                );

                function addVariable() {
                  var key = document.getElementById('varKey').value;
                  var value = document.getElementById('varValue').value;
                  var scope = document.getElementById('varScope').value;
                  if (!key) { showToast('Key is required', 'warning'); return; }
                  fetch(CTX + '/api/execute', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({command: 'set-domain-property default ' + key + ' ' + value})
                  }).then(function(r){return r.json();}).then(function(d) {
                    showToast(d.message, d.success ? 'success' : 'error');
                    if (d.success) {
                      document.getElementById('addVarModal').classList.remove('open');
                      var tbody = document.querySelector('#tab-env .data-table tbody');
                      var badgeCls = scope === 'Domain' ? 'badge-info' : 'badge-warning';
                      tbody.insertAdjacentHTML('beforeend',
                        '<tr><td>' + key + '</td><td><code>' + value + '</code></td>' +
                        '<td><span class="badge ' + badgeCls + '">' + scope + '</span></td>' +
                        '<td><button class="btn btn-sm">Edit</button></td></tr>');
                    }
                  });
                }

                function createProfile() {
                  var name = document.getElementById('profileName').value;
                  var desc = document.getElementById('profileDesc').value;
                  if (!name) { showToast('Profile name is required', 'warning'); return; }
                  document.getElementById('addProfileModal').classList.remove('open');
                  var tbody = document.querySelector('#tab-profiles .data-table tbody');
                  tbody.insertAdjacentHTML('beforeend',
                    '<tr><td><strong>' + name + '</strong></td>' +
                    '<td>' + (desc || '-') + '</td>' +
                    '<td><span class="badge badge-neutral">Inactive</span></td>' +
                    '<td><div class="btn-group">' +
                    '<button class="btn btn-sm">View</button>' +
                    '<button class="btn btn-sm">Activate</button>' +
                    '</div></td></tr>');
                  showToast('Profile "' + name + '" created', 'success');
                }

                function validateDomainConfig() {
                  var inputs = document.querySelectorAll('#editDomainModal .form-input');
                  var serverName = inputs[0].value;
                  var nodeId = inputs[1].value;
                  var shutdown = inputs[2].value;
                  var errors = [];
                  if (!serverName) errors.push('Server name is required');
                  if (!nodeId) errors.push('Node ID is required');
                  if (isNaN(shutdown) || parseInt(shutdown) < 0) errors.push('Graceful shutdown must be >= 0');
                  if (errors.length > 0) {
                    showToast('Validation failed: ' + errors.join('; '), 'error');
                  } else {
                    showToast('Validation passed. Configuration is valid.', 'success');
                  }
                }

                // Save as Draft button
                document.querySelector('#editDomainModal .btn-primary').addEventListener('click', function() {
                  var inputs = document.querySelectorAll('#editDomainModal .form-input');
                  var desc = document.querySelector('#editDomainModal textarea').value || 'Domain configuration update';
                  fetch(CTX + '/api/drafts/create', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({
                      target: 'server.yaml',
                      description: desc,
                      changes: 'server.name=' + inputs[0].value + ', server.nodeId=' + inputs[1].value
                               + ', gracefulShutdownMillis=' + inputs[2].value
                    })
                  }).then(function(r){return r.json();}).then(function(d) {
                    if (d.success) {
                      showToast('Draft created: ' + d.draft.id + '. Go to History > Drafts to review and apply.', 'success');
                      document.getElementById('editDomainModal').classList.remove('open');
                    } else {
                      showToast('Error: ' + d.message, 'error');
                    }
                  });
                });
                </script>
                """.formatted(
                h(server.getName()), h(server.getNodeId()), server.getGracefulShutdownMillis(),
                h(server.getListener().getHost()), server.getListener().getPort(),
                server.getListener().getSoBacklog(),
                formatBytes(server.getListener().getMaxContentLength()),
                server.getListener().getMaxHeaderSize(),
                server.getListener().getIdleTimeoutSeconds(),
                server.getSession().getTimeoutSeconds(), server.getSession().getPurgeIntervalSeconds(),
                server.getCompression().isEnabled() ? "true" : "false",
                server.getCompression().getMinResponseSizeBytes(),
                server.getCompression().getCompressionLevel(),
                String.join(", ", server.getCompression().getMimeTypes()),
                h(server.getJsp().getScratchDir()),
                server.getJsp().isDevelopmentMode() ? "true" : "false",
                server.getJsp().isPrecompile() ? "true" : "false",
                server.getJsp().getReloadIntervalSeconds(),
                h(server.getJsp().getPageEncoding()),
                server.getJsp().getBufferSize(),
                h(server.getDeploy().getDirectory()),
                server.getDeploy().isHotDeploy() ? "true" : "false",
                server.getDeploy().getScanIntervalSeconds(),
                buildYamlPreview(server),
                h(System.getProperty("java.home", "")),
                h(server.getName()), h(server.getNodeId()),
                h(server.getName()), h(server.getNodeId()), server.getGracefulShutdownMillis(),
                ctx
        );

        resp.getWriter().write(AdminPageLayout.page("Domain", server.getName(), server.getNodeId(),
                ctx, "domain", body));
    }

    private static String h(String s) { return AdminPageLayout.escapeHtml(s); }

    private static String formatBytes(int bytes) {
        if (bytes >= 1024 * 1024) return (bytes / (1024 * 1024)) + " MB";
        if (bytes >= 1024) return (bytes / 1024) + " KB";
        return bytes + " B";
    }

    private static String buildYamlPreview(ServerConfiguration.Server s) {
        return h("""
                server:
                  name: %s
                  nodeId: %s
                  gracefulShutdownMillis: %d
                  listener:
                    host: %s
                    port: %d
                  threading:
                    bossThreads: %d
                    workerThreads: %d
                    businessThreads: %d
                  tls:
                    enabled: %s
                  webAdmin:
                    enabled: %s
                    contextPath: %s
                  compression:
                    enabled: %s
                  session:
                    timeoutSeconds: %d
                  deploy:
                    directory: %s
                    hotDeploy: %s
                    scanIntervalSeconds: %d
                """.formatted(
                s.getName(), s.getNodeId(), s.getGracefulShutdownMillis(),
                s.getListener().getHost(), s.getListener().getPort(),
                s.getThreading().getBossThreads(), s.getThreading().getWorkerThreads(),
                s.getThreading().getBusinessThreads(),
                s.getTls().isEnabled(),
                s.getWebAdmin().isEnabled(), s.getWebAdmin().getContextPath(),
                s.getCompression().isEnabled(),
                s.getSession().getTimeoutSeconds(),
                s.getDeploy().getDirectory(), s.getDeploy().isHotDeploy(),
                s.getDeploy().getScanIntervalSeconds()
        ));
    }
}
