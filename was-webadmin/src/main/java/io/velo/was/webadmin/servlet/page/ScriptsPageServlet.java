package io.velo.was.webadmin.servlet.page;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Script automation center.
 * Upload, edit, validate, schedule, and execute automation scripts.
 */
public class ScriptsPageServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public ScriptsPageServlet(ServerConfiguration configuration) {
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
                    <div class="page-title">Scripts</div>
                    <div class="page-subtitle">Automation scripts, templates, scheduled tasks, and batch execution</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn" onclick="document.getElementById('uploadModal').classList.add('open')">Upload Script</button>
                    <button class="btn btn-primary" onclick="document.getElementById('newScriptModal').classList.add('open')">New Script</button>
                  </div>
                </div>

                <div class="tabs" id="scrTabs">
                  <div class="tab active" data-tab="scripts">Scripts</div>
                  <div class="tab" data-tab="editor">Editor</div>
                  <div class="tab" data-tab="scheduled">Scheduled Tasks</div>
                  <div class="tab" data-tab="templates">Templates</div>
                </div>

                <div class="tab-panel active" id="tab-scripts">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Saved Scripts</div>
                    </div>
                    <div class="alert alert-info">No scripts saved yet. Create a new script or upload one.</div>
                    <table class="data-table" style="display:none;">
                      <thead>
                        <tr><th>Name</th><th>Description</th><th>Commands</th><th>Last Run</th><th>Schedule</th><th>Actions</th></tr>
                      </thead>
                      <tbody></tbody>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-editor">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Script Editor</div>
                      <div class="btn-group">
                        <button class="btn btn-sm">Validate</button>
                        <button class="btn btn-sm btn-primary" onclick="runScript()">Run</button>
                        <button class="btn btn-sm">Save</button>
                      </div>
                    </div>
                    <textarea id="scriptEditor" style="width:100%%;min-height:300px;background:var(--bg);
                              color:var(--text);border:1px solid var(--border);border-radius:8px;
                              padding:16px;font-family:'JetBrains Mono',monospace;font-size:13px;
                              line-height:1.8;outline:none;resize:vertical;"
                              placeholder="# Enter commands one per line\n# Lines starting with # are comments\n\nstatus\nlistservers\nlistapplications\nmemoryinfo"></textarea>
                    <div id="scriptOutput" style="margin-top:12px;"></div>
                  </div>
                </div>

                <div class="tab-panel" id="tab-scheduled">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Scheduled Tasks</div>
                      <button class="btn btn-sm btn-primary">Create Schedule</button>
                    </div>
                    <div class="alert alert-info">No scheduled tasks configured.</div>
                    <table class="data-table" style="margin-top:8px;">
                      <thead><tr><th>Name</th><th>Script</th><th>Schedule</th><th>Next Run</th><th>Last Result</th><th>Actions</th></tr></thead>
                      <tbody>
                        <tr><td colspan="6" style="color:var(--text3);text-align:center;">
                          Scheduled tasks allow running scripts at specified intervals (immediate, cron, repeating, conditional).
                        </td></tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-templates">
                  <div class="card">
                    <div class="card-title">Script Templates</div>
                    <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(250px,1fr));gap:12px;margin-top:12px;">
                      <div class="card" style="cursor:pointer;" onclick="useTemplate('health')">
                        <div style="font-weight:600;font-size:14px;">Health Check</div>
                        <div style="font-size:12px;color:var(--text2);margin-top:4px;">
                          Check server status, memory, threads
                        </div>
                        <code style="font-size:11px;color:var(--text3);display:block;margin-top:8px;">status, memoryinfo, threadinfo</code>
                      </div>
                      <div class="card" style="cursor:pointer;" onclick="useTemplate('deployment')">
                        <div style="font-weight:600;font-size:14px;">Deployment Status</div>
                        <div style="font-size:12px;color:var(--text2);margin-top:4px;">
                          List servers and deployed applications
                        </div>
                        <code style="font-size:11px;color:var(--text3);display:block;margin-top:8px;">listservers, listapplications</code>
                      </div>
                      <div class="card" style="cursor:pointer;" onclick="useTemplate('diagnostics')">
                        <div style="font-weight:600;font-size:14px;">Diagnostics</div>
                        <div style="font-size:12px;color:var(--text2);margin-top:4px;">
                          JVM info, memory, threads, deadlocks
                        </div>
                        <code style="font-size:11px;color:var(--text3);display:block;margin-top:8px;">jvminfo, memoryinfo, threadinfo, deadlockcheck</code>
                      </div>
                      <div class="card" style="cursor:pointer;" onclick="useTemplate('resources')">
                        <div style="font-weight:600;font-size:14px;">Resource Check</div>
                        <div style="font-size:12px;color:var(--text2);margin-top:4px;">
                          DataSources, thread pools, system info
                        </div>
                        <code style="font-size:11px;color:var(--text3);display:block;margin-top:8px;">listdatasources, listthreadpools, systeminfo</code>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Upload Script Modal -->
                <div class="modal-overlay" id="uploadModal">
                  <div class="modal">
                    <div class="modal-title">Upload Script</div>
                    <div class="form-group">
                      <label class="form-label">Script File</label>
                      <input class="form-input" type="file" accept=".vsh,.txt,.sh">
                    </div>
                    <div class="form-group">
                      <label class="form-label">Name</label>
                      <input class="form-input" type="text" placeholder="Script name">
                    </div>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('uploadModal').classList.remove('open')">Cancel</button>
                      <button class="btn btn-primary">Upload</button>
                    </div>
                  </div>
                </div>

                <!-- New Script Modal -->
                <div class="modal-overlay" id="newScriptModal">
                  <div class="modal">
                    <div class="modal-title">New Script</div>
                    <div class="form-group">
                      <label class="form-label">Script Name</label>
                      <input class="form-input" type="text" placeholder="e.g. daily-health-check">
                    </div>
                    <div class="form-group">
                      <label class="form-label">Description</label>
                      <input class="form-input" type="text" placeholder="Brief description">
                    </div>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('newScriptModal').classList.remove('open')">Cancel</button>
                      <button class="btn btn-primary">Create &amp; Edit</button>
                    </div>
                  </div>
                </div>

                <script>
                document.querySelectorAll('#scrTabs .tab').forEach(function(tab){
                  tab.addEventListener('click', function(){
                    document.querySelectorAll('#scrTabs .tab').forEach(function(t){t.classList.remove('active');});
                    document.querySelectorAll('.tab-panel').forEach(function(p){p.classList.remove('active');});
                    tab.classList.add('active');
                    document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
                  });
                });
                var templates = {
                  health: '# Health Check Script\\nstatus\\nmemoryinfo\\nthreadinfo',
                  deployment: '# Deployment Status Script\\nlistservers\\nlistapplications',
                  diagnostics: '# Diagnostics Script\\njvminfo\\nmemoryinfo\\nthreadinfo\\ndeadlockcheck',
                  resources: '# Resource Check Script\\nlistdatasources\\nlistthreadpools\\nsysteminfo'
                };
                function useTemplate(name) {
                  document.getElementById('scriptEditor').value = templates[name];
                  document.querySelectorAll('#scrTabs .tab').forEach(function(t){t.classList.remove('active');});
                  document.querySelectorAll('.tab-panel').forEach(function(p){p.classList.remove('active');});
                  document.querySelector('[data-tab="editor"]').classList.add('active');
                  document.getElementById('tab-editor').classList.add('active');
                }
                function runScript() {
                  var editor = document.getElementById('scriptEditor');
                  var lines = editor.value.split('\\n').filter(function(l){return l.trim() && !l.trim().startsWith('#');});
                  var outputDiv = document.getElementById('scriptOutput');
                  outputDiv.innerHTML = '<div class="alert alert-info">Running ' + lines.length + ' commands...</div>';
                  var results = [];
                  function runNext(i) {
                    if (i >= lines.length) {
                      outputDiv.innerHTML = '<div class="card" style="margin-top:8px;"><div class="card-title">Script Output</div>' +
                        '<pre style="font-family:monospace;font-size:12px;background:var(--bg);padding:12px;border-radius:8px;' +
                        'max-height:300px;overflow:auto;margin-top:8px;">' + results.join('\\n') + '</pre></div>';
                      return;
                    }
                    fetch('%s/api/execute', {
                      method:'POST', headers:{'Content-Type':'application/json'},
                      body: JSON.stringify({command: lines[i]})
                    }).then(function(r){return r.json();}).then(function(d) {
                      results.push('> ' + lines[i] + '\\n' + d.message + '\\n');
                      runNext(i + 1);
                    }).catch(function(e){
                      results.push('> ' + lines[i] + '\\nError: ' + e.message + '\\n');
                      runNext(i + 1);
                    });
                  }
                  runNext(0);
                }
                </script>
                """.formatted(ctx);

        resp.getWriter().write(AdminPageLayout.page("Scripts", server.getName(), server.getNodeId(),
                ctx, "scripts", body));
    }
}
