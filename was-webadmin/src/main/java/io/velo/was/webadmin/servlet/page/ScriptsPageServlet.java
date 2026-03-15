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
                        <button class="btn btn-sm" onclick="validateScript()">Validate</button>
                        <button class="btn btn-sm btn-primary" onclick="runScript()">Run</button>
                        <button class="btn btn-sm" onclick="saveScript()">Save</button>
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
                      <button class="btn btn-sm btn-primary" onclick="document.getElementById('scheduleModal').classList.add('open')">Create Schedule</button>
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
                      <button class="btn btn-primary" onclick="uploadScript()">Upload</button>
                    </div>
                  </div>
                </div>

                <!-- Schedule Modal -->
                <div class="modal-overlay" id="scheduleModal">
                  <div class="modal">
                    <div class="modal-title">Create Scheduled Task</div>
                    <div class="form-group">
                      <label class="form-label">Task Name</label>
                      <input class="form-input" type="text" id="schedName" placeholder="e.g. daily-health-check">
                    </div>
                    <div class="form-group">
                      <label class="form-label">Script</label>
                      <select class="form-select" id="schedScript"></select>
                    </div>
                    <div class="form-group">
                      <label class="form-label">Schedule Type</label>
                      <select class="form-select" id="schedType">
                        <option value="interval">Interval (minutes)</option>
                        <option value="cron">Cron Expression</option>
                      </select>
                    </div>
                    <div class="form-group">
                      <label class="form-label">Value</label>
                      <input class="form-input" type="text" id="schedValue" placeholder="e.g. 60 (minutes) or 0 0 * * * (cron)">
                    </div>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('scheduleModal').classList.remove('open')">Cancel</button>
                      <button class="btn btn-primary" onclick="createSchedule()">Create</button>
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
                      <button class="btn btn-primary" onclick="createNewScript()">Create &amp; Edit</button>
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
                var savedScripts = JSON.parse(localStorage.getItem('velo-scripts') || '[]');
                var CTX = '%s';

                function renderScriptsList() {
                  var container = document.querySelector('#tab-scripts .card');
                  var alert = container.querySelector('.alert');
                  var table = container.querySelector('.data-table');
                  var tbody = table.querySelector('tbody');
                  if (savedScripts.length === 0) {
                    alert.style.display = '';
                    table.style.display = 'none';
                    return;
                  }
                  alert.style.display = 'none';
                  table.style.display = '';
                  var html = '';
                  savedScripts.forEach(function(s, i) {
                    var cmds = (s.content || '').split('\\n').filter(function(l){return l.trim() && !l.trim().startsWith('#');}).length;
                    html += '<tr><td><strong>' + esc(s.name) + '</strong></td>'
                      + '<td>' + esc(s.description || '-') + '</td>'
                      + '<td>' + cmds + '</td>'
                      + '<td>' + (s.lastRun || 'Never') + '</td>'
                      + '<td>' + (s.schedule || '-') + '</td>'
                      + '<td><div class="btn-group">'
                      + '<button class="btn btn-sm" onclick="editScript(' + i + ')">Edit</button>'
                      + '<button class="btn btn-sm" onclick="runSaved(' + i + ')">Run</button>'
                      + '<button class="btn btn-sm btn-danger" onclick="deleteScript(' + i + ')">Delete</button>'
                      + '</div></td></tr>';
                  });
                  tbody.innerHTML = html;
                }

                function esc(s) { if(!s) return ''; var d=document.createElement('div'); d.textContent=s; return d.innerHTML; }

                function saveScriptsToStorage() {
                  localStorage.setItem('velo-scripts', JSON.stringify(savedScripts));
                  renderScriptsList();
                }

                function editScript(i) {
                  document.getElementById('scriptEditor').value = savedScripts[i].content;
                  document.getElementById('scriptEditor').dataset.editIndex = i;
                  document.querySelectorAll('#scrTabs .tab').forEach(function(t){t.classList.remove('active');});
                  document.querySelectorAll('.tab-panel').forEach(function(p){p.classList.remove('active');});
                  document.querySelector('[data-tab="editor"]').classList.add('active');
                  document.getElementById('tab-editor').classList.add('active');
                }

                function runSaved(i) {
                  document.getElementById('scriptEditor').value = savedScripts[i].content;
                  document.getElementById('scriptEditor').dataset.editIndex = i;
                  document.querySelectorAll('#scrTabs .tab').forEach(function(t){t.classList.remove('active');});
                  document.querySelectorAll('.tab-panel').forEach(function(p){p.classList.remove('active');});
                  document.querySelector('[data-tab="editor"]').classList.add('active');
                  document.getElementById('tab-editor').classList.add('active');
                  runScript();
                  savedScripts[i].lastRun = new Date().toLocaleString();
                  saveScriptsToStorage();
                }

                function deleteScript(i) {
                  if (!confirm('Delete script "' + savedScripts[i].name + '"?')) return;
                  savedScripts.splice(i, 1);
                  saveScriptsToStorage();
                  showToast('Script deleted', 'success');
                }

                function validateScript() {
                  var editor = document.getElementById('scriptEditor');
                  var lines = editor.value.split('\\n');
                  var errors = [];
                  var cmdCount = 0;
                  lines.forEach(function(l, i) {
                    var trimmed = l.trim();
                    if (!trimmed || trimmed.startsWith('#')) return;
                    cmdCount++;
                  });
                  if (cmdCount === 0) {
                    showToast('Script is empty or contains only comments', 'warning');
                    return;
                  }
                  showToast('Script validated: ' + cmdCount + ' command(s) found. Ready to run.', 'success');
                }

                function saveScript() {
                  var editor = document.getElementById('scriptEditor');
                  var content = editor.value;
                  if (!content.trim()) { showToast('Script is empty', 'warning'); return; }
                  var idx = editor.dataset.editIndex;
                  if (idx !== undefined && idx !== '' && savedScripts[parseInt(idx)]) {
                    savedScripts[parseInt(idx)].content = content;
                    saveScriptsToStorage();
                    showToast('Script updated', 'success');
                  } else {
                    var name = prompt('Enter script name:');
                    if (!name) return;
                    savedScripts.push({name: name, description: '', content: content, lastRun: null, schedule: null});
                    saveScriptsToStorage();
                    showToast('Script saved as "' + name + '"', 'success');
                  }
                }

                function uploadScript() {
                  var fileInput = document.querySelector('#uploadModal input[type="file"]');
                  var nameInput = document.querySelector('#uploadModal input[type="text"]');
                  if (!fileInput.files.length) { showToast('Please select a file', 'warning'); return; }
                  var file = fileInput.files[0];
                  var reader = new FileReader();
                  reader.onload = function(e) {
                    var name = nameInput.value || file.name.replace(/\\.[^.]+$/, '');
                    savedScripts.push({name: name, description: 'Uploaded from ' + file.name, content: e.target.result, lastRun: null, schedule: null});
                    saveScriptsToStorage();
                    document.getElementById('uploadModal').classList.remove('open');
                    fileInput.value = '';
                    nameInput.value = '';
                    showToast('Script "' + name + '" uploaded successfully', 'success');
                  };
                  reader.readAsText(file);
                }

                function createNewScript() {
                  var nameInput = document.querySelector('#newScriptModal input[type="text"]');
                  var descInput = document.querySelectorAll('#newScriptModal input[type="text"]')[1];
                  var name = nameInput.value;
                  if (!name) { showToast('Script name is required', 'warning'); return; }
                  savedScripts.push({name: name, description: descInput ? descInput.value : '', content: '# ' + name + '\\n', lastRun: null, schedule: null});
                  saveScriptsToStorage();
                  document.getElementById('newScriptModal').classList.remove('open');
                  var idx = savedScripts.length - 1;
                  document.getElementById('scriptEditor').value = savedScripts[idx].content;
                  document.getElementById('scriptEditor').dataset.editIndex = idx;
                  nameInput.value = '';
                  if (descInput) descInput.value = '';
                  document.querySelectorAll('#scrTabs .tab').forEach(function(t){t.classList.remove('active');});
                  document.querySelectorAll('.tab-panel').forEach(function(p){p.classList.remove('active');});
                  document.querySelector('[data-tab="editor"]').classList.add('active');
                  document.getElementById('tab-editor').classList.add('active');
                  showToast('Script "' + name + '" created. Edit below.', 'success');
                }

                function createSchedule() {
                  var name = document.getElementById('schedName').value;
                  if (!name) { showToast('Task name is required', 'warning'); return; }
                  var scriptSel = document.getElementById('schedScript');
                  if (!scriptSel.value) { showToast('Please select a script', 'warning'); return; }
                  var type = document.getElementById('schedType').value;
                  var value = document.getElementById('schedValue').value;
                  if (!value) { showToast('Schedule value is required', 'warning'); return; }
                  var idx = parseInt(scriptSel.value);
                  savedScripts[idx].schedule = type + ': ' + value;
                  saveScriptsToStorage();
                  document.getElementById('scheduleModal').classList.remove('open');
                  showToast('Schedule created for "' + savedScripts[idx].name + '"', 'success');
                  renderScheduledTasks();
                }

                function renderScheduledTasks() {
                  var scheduled = savedScripts.filter(function(s){return s.schedule;});
                  var tbody = document.querySelector('#tab-scheduled .data-table tbody');
                  var alert = document.querySelector('#tab-scheduled .alert');
                  if (scheduled.length === 0) {
                    if (alert) alert.style.display = '';
                    return;
                  }
                  if (alert) alert.style.display = 'none';
                  var html = '';
                  scheduled.forEach(function(s) {
                    html += '<tr><td>' + esc(s.name) + '-task</td><td>' + esc(s.name) + '</td>'
                      + '<td>' + esc(s.schedule) + '</td><td>-</td><td>-</td>'
                      + '<td><button class="btn btn-sm btn-danger" onclick="removeSchedule(\\'' + esc(s.name) + '\\')">Remove</button></td></tr>';
                  });
                  tbody.innerHTML = html;
                }

                function removeSchedule(scriptName) {
                  savedScripts.forEach(function(s){ if(s.name === scriptName) s.schedule = null; });
                  saveScriptsToStorage();
                  renderScheduledTasks();
                  showToast('Schedule removed', 'success');
                }

                function populateScheduleScripts() {
                  var sel = document.getElementById('schedScript');
                  sel.innerHTML = '';
                  savedScripts.forEach(function(s, i) {
                    var opt = document.createElement('option');
                    opt.value = i;
                    opt.textContent = s.name;
                    sel.appendChild(opt);
                  });
                }

                document.getElementById('scheduleModal').addEventListener('transitionstart', populateScheduleScripts);

                renderScriptsList();
                renderScheduledTasks();

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
                """.formatted(ctx, ctx);

        resp.getWriter().write(AdminPageLayout.page("Scripts", server.getName(), server.getNodeId(),
                ctx, "scripts", body));
    }
}
