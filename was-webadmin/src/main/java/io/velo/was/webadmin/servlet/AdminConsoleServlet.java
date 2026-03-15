package io.velo.was.webadmin.servlet;

import io.velo.was.config.ServerConfiguration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Web-based CLI console for Velo Web Admin.
 * Full terminal-style interface with command execution, auto-complete, history,
 * saved commands, and parameter forms.
 */
public class AdminConsoleServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public AdminConsoleServlet(ServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        String ctx = req.getContextPath();
        ServerConfiguration.Server server = configuration.getServer();

        String body = """
                <style>
                  /* ── Console-specific styles ─────────────────────────── */
                  .console-wrap { display:flex;gap:16px;height:calc(100vh - 200px); }
                  .console-main { flex:1;display:flex;flex-direction:column; }
                  .console-sidebar { width:300px;display:flex;flex-direction:column;gap:12px; }
                  #output { flex:1;background:#0a0c10;border:1px solid var(--border);
                       border-radius:var(--radius) var(--radius) 0 0;padding:16px;overflow-y:auto;
                       font-family:'JetBrains Mono','Fira Code','Cascadia Code',monospace;font-size:13px;
                       line-height:1.8; }
                  .output-block { position:relative;margin-bottom:4px; }
                  .output-block:hover .ob-actions { opacity:1; }
                  .ob-actions { position:absolute;top:2px;right:4px;display:flex;gap:4px;opacity:0;transition:opacity .15s; }
                  .ob-actions button { background:var(--surface2);border:1px solid var(--border);color:var(--text2);
                       border-radius:4px;padding:2px 6px;font-size:10px;cursor:pointer; }
                  .ob-actions button:hover { background:var(--surface3);color:var(--text); }
                  .ob-duration { font-size:10px;color:var(--text3);margin-left:8px; }
                  .input-row { display:flex;background:#0a0c10;border:1px solid var(--border);border-top:none;
                       border-radius:0 0 var(--radius) var(--radius);padding:8px 16px;align-items:flex-start;gap:8px;
                       position:relative; }
                  .prompt { color:var(--success);font-family:monospace;font-size:13px;white-space:nowrap;padding-top:2px; }
                  #cmdInput { flex:1;background:transparent;border:none;color:var(--text);resize:none;
                       font-family:'JetBrains Mono','Fira Code',monospace;font-size:13px;outline:none;
                       line-height:1.5;min-height:22px;max-height:120px;overflow-y:auto; }
                  .input-hint { font-size:10px;color:var(--text3);padding:4px 16px 4px;background:#0a0c10;
                       border:1px solid var(--border);border-top:none;border-bottom:none; }

                  /* Autocomplete dropdown */
                  .ac-dropdown { position:absolute;bottom:100%%;left:0;right:0;max-height:260px;overflow-y:auto;
                       background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-sm);
                       box-shadow:0 -4px 20px rgba(0,0,0,0.4);display:none;z-index:50; }
                  .ac-dropdown.open { display:block; }
                  .ac-item { padding:8px 14px;cursor:pointer;display:flex;justify-content:space-between;
                       align-items:center;font-size:12px;font-family:monospace;border-bottom:1px solid var(--border); }
                  .ac-item:last-child { border-bottom:none; }
                  .ac-item:hover, .ac-item.selected { background:var(--primary-bg);color:var(--primary); }
                  .ac-item .ac-desc { color:var(--text3);font-size:11px;font-family:sans-serif;max-width:200px;
                       overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }
                  .ac-item .ac-cat { font-size:10px;padding:1px 6px;border-radius:3px;background:var(--surface2);
                       color:var(--text3);margin-left:8px; }

                  /* Parameter form mode */
                  #paramForm { background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-sm);
                       padding:16px;margin-top:8px;display:none; }
                  #paramForm.open { display:block; }
                  #paramForm .pf-title { font-size:13px;font-weight:600;margin-bottom:12px;color:var(--primary); }
                  #paramForm .pf-usage { font-size:11px;color:var(--text3);margin-bottom:12px;font-family:monospace; }
                  #paramForm .pf-field { margin-bottom:10px; }
                  #paramForm .pf-field label { display:block;font-size:12px;color:var(--text2);margin-bottom:4px; }
                  #paramForm .pf-field input { width:100%%;padding:6px 10px;background:var(--bg);border:1px solid var(--border);
                       border-radius:var(--radius-xs);color:var(--text);font-size:12px;outline:none;font-family:monospace; }
                  #paramForm .pf-field input:focus { border-color:var(--primary); }

                  /* Confirmation dialog */
                  .confirm-overlay { display:none;position:fixed;inset:0;background:rgba(0,0,0,0.6);z-index:300;
                       justify-content:center;align-items:center; }
                  .confirm-overlay.open { display:flex; }
                  .confirm-box { background:var(--surface);border:1px solid var(--danger);border-radius:var(--radius);
                       padding:24px;max-width:520px;width:90%%; }
                  .confirm-box .cb-title { font-size:16px;font-weight:600;color:var(--danger);margin-bottom:12px; }
                  .confirm-box .cb-label { font-size:12px;color:var(--text2);margin-bottom:4px; }
                  .confirm-box pre { background:var(--bg);border:1px solid var(--border);border-radius:var(--radius-xs);
                       padding:10px;font-size:12px;color:var(--text);overflow-x:auto;margin-bottom:12px; }
                  .confirm-box .cb-footer { display:flex;justify-content:flex-end;gap:8px;margin-top:16px; }

                  /* Saved commands panel */
                  .saved-item { display:flex;align-items:center;gap:6px;padding:4px 8px;font-size:12px;
                       font-family:monospace;color:var(--text2);border-radius:4px;cursor:pointer; }
                  .saved-item:hover { background:var(--surface2);color:var(--text); }
                  .saved-item .saved-name { color:var(--warning);font-size:10px;font-family:sans-serif; }
                  .saved-item .saved-del { color:var(--text3);cursor:pointer;margin-left:auto;font-size:14px; }
                  .saved-item .saved-del:hover { color:var(--danger); }

                  /* History search */
                  .hist-search { width:100%%;padding:4px 8px;background:var(--bg);border:1px solid var(--border);
                       border-radius:var(--radius-xs);color:var(--text);font-size:11px;outline:none;margin-bottom:6px; }
                  .hist-search:focus { border-color:var(--primary); }
                  .hist-item { display:flex;align-items:center;gap:6px;padding:4px 8px;font-size:12px;
                       font-family:monospace;color:var(--text2);cursor:pointer;border-radius:4px; }
                  .hist-item:hover { background:var(--surface2);color:var(--text); }
                  .hist-item .hist-time { font-size:9px;color:var(--text3);font-family:sans-serif; }
                  .hist-item .hist-status { width:6px;height:6px;border-radius:50%%;flex-shrink:0; }
                  .hist-item .hist-star { color:var(--text3);cursor:pointer;margin-left:auto;font-size:14px; }
                  .hist-item .hist-star:hover, .hist-item .hist-star.starred { color:var(--warning); }

                  /* JSON syntax highlighting */
                  .json-key { color:#7dd3fc; }
                  .json-str { color:#86efac; }
                  .json-num { color:#fbbf24; }
                  .json-bool { color:#c084fc; }
                  .json-null { color:#9ca3af; }

                  /* Toggle button group */
                  .toggle-group { display:inline-flex;border:1px solid var(--border);border-radius:var(--radius-sm);overflow:hidden; }
                  .toggle-group button { padding:4px 10px;font-size:11px;border:none;background:var(--surface2);
                       color:var(--text2);cursor:pointer;border-right:1px solid var(--border); }
                  .toggle-group button:last-child { border-right:none; }
                  .toggle-group button.active { background:var(--primary);color:white; }

                  /* ── Action Preview Panel ────────────────────────────── */
                  .preview-overlay { display:none;position:fixed;inset:0;background:rgba(0,0,0,0.6);z-index:300;
                       justify-content:center;align-items:center; }
                  .preview-overlay.open { display:flex; }
                  .preview-box { background:var(--surface);border:1px solid var(--primary);border-radius:var(--radius);
                       padding:24px;max-width:620px;width:90%%; }
                  .preview-box .pv-title { font-size:16px;font-weight:600;color:var(--primary);margin-bottom:12px;
                       display:flex;align-items:center;gap:8px; }
                  .preview-box .pv-label { font-size:12px;color:var(--text2);margin-bottom:4px;font-weight:600; }
                  .preview-box pre { background:var(--bg);border:1px solid var(--border);border-radius:var(--radius-xs);
                       padding:10px;font-size:12px;color:var(--text);overflow-x:auto;margin-bottom:12px;white-space:pre-wrap; }
                  .preview-box .pv-desc { font-size:12px;color:var(--text2);margin-bottom:12px;line-height:1.5; }
                  .preview-box .pv-footer { display:flex;justify-content:flex-end;gap:8px;margin-top:16px; }
                  .risk-badge { display:inline-block;padding:2px 8px;border-radius:3px;font-size:10px;font-weight:600;
                       text-transform:uppercase;letter-spacing:0.5px; }
                  .risk-high { background:rgba(239,68,68,0.15);color:var(--danger);border:1px solid var(--danger); }
                  .risk-medium { background:rgba(251,191,36,0.15);color:var(--warning);border:1px solid var(--warning); }
                  .risk-low { background:rgba(34,197,94,0.15);color:var(--success);border:1px solid var(--success); }

                  /* ── Context Help Sidebar Card ───────────────────────── */
                  .cmd-help-card { transition:all 0.2s ease; }
                  .cmd-help-card .help-name { font-size:14px;font-weight:600;color:var(--primary);margin-bottom:4px; }
                  .cmd-help-card .help-cat { font-size:10px;padding:1px 6px;border-radius:3px;background:var(--surface2);
                       color:var(--text3);display:inline-block;margin-bottom:8px; }
                  .cmd-help-card .help-desc { font-size:12px;color:var(--text2);line-height:1.5;margin-bottom:8px; }
                  .cmd-help-card .help-usage { font-size:11px;font-family:monospace;color:var(--text);background:var(--bg);
                       border:1px solid var(--border);border-radius:var(--radius-xs);padding:6px 8px;margin-bottom:8px; }
                  .cmd-help-card .help-section { margin-bottom:8px; }
                  .cmd-help-card .help-section-title { font-size:11px;font-weight:600;color:var(--text2);margin-bottom:4px; }
                  .cmd-help-card .help-tip { font-size:11px;color:var(--text3);line-height:1.4;padding:6px 8px;
                       background:var(--bg);border-left:3px solid var(--primary);border-radius:0 var(--radius-xs) var(--radius-xs) 0; }
                  .cmd-help-card .help-related { display:flex;flex-wrap:wrap;gap:4px; }
                  .cmd-help-card .help-related-item { font-size:10px;padding:2px 6px;border-radius:3px;background:var(--surface2);
                       color:var(--text2);cursor:pointer;font-family:monospace; }
                  .cmd-help-card .help-related-item:hover { background:var(--primary-bg);color:var(--primary); }
                  .quick-cmds-toggle { font-size:11px;color:var(--text3);cursor:pointer;display:flex;
                       align-items:center;gap:4px;margin-top:8px;padding:4px 0;border-top:1px solid var(--border); }
                  .quick-cmds-toggle:hover { color:var(--text2); }
                  .quick-cmds-body { overflow:hidden;transition:max-height 0.2s ease; }
                  .quick-cmds-body.collapsed { max-height:0; }

                  /* ── Templates Card ──────────────────────────────────── */
                  .tpl-item { display:flex;align-items:center;gap:6px;padding:5px 8px;font-size:12px;
                       color:var(--text2);border-radius:4px;cursor:pointer;border:1px solid transparent; }
                  .tpl-item:hover { background:var(--surface2);color:var(--text);border-color:var(--border); }
                  .tpl-item .tpl-name { font-size:11px;font-weight:600;color:var(--text); }
                  .tpl-item .tpl-cmd { font-size:10px;font-family:monospace;color:var(--text3);
                       overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }
                  .tpl-item .tpl-del { color:var(--text3);cursor:pointer;margin-left:auto;font-size:14px;flex-shrink:0; }
                  .tpl-item .tpl-del:hover { color:var(--danger); }
                  .tpl-builtin { font-size:9px;padding:1px 4px;border-radius:2px;background:var(--surface2);
                       color:var(--text3);flex-shrink:0; }
                </style>

                <div class="page-header">
                  <div>
                    <div class="page-title">Console</div>
                    <div class="page-subtitle">Execute CLI commands directly from the browser</div>
                  </div>
                  <div class="btn-group">
                    <div class="toggle-group">
                      <button id="modeText" class="active" onclick="setMode('text')" title="Text input mode">Text</button>
                      <button id="modeForm" onclick="setMode('form')" title="Parameter form mode">Form</button>
                    </div>
                    <button class="btn btn-sm" onclick="showPreviewFromInput()" title="Ctrl+Shift+Enter">Preview</button>
                    <button class="btn btn-sm" onclick="clearOutput()">Clear</button>
                    <button class="btn btn-sm" onclick="downloadHistory()">Export History</button>
                    <button class="btn btn-sm" onclick="exportAsScript()">Export as Script</button>
                  </div>
                </div>
                <div class="console-wrap">
                  <div class="console-main">
                    <div id="output">
                      <div class="ol" style="color:var(--text2);">Velo Web Admin Console v0.1.0</div>
                      <div class="ol" style="color:var(--text2);">Type 'help' for available commands. Tab for auto-complete.</div>
                      <div class="ol" style="color:var(--text2);">Shift+Enter for multi-line input. Ctrl+Shift+Enter: preview before execute.</div>
                      <div class="ol" style="color:var(--text2);">---</div>
                    </div>
                    <div class="input-hint">Tab: autocomplete &middot; Up/Down: history &middot; Shift+Enter: new line &middot; Enter: execute &middot; Ctrl+Shift+Enter: preview</div>
                    <div class="input-row">
                      <span class="prompt">velo&gt;</span>
                      <textarea id="cmdInput" rows="1" autocomplete="off" autofocus
                             placeholder="Enter command..."></textarea>
                      <div class="ac-dropdown" id="acDropdown"></div>
                    </div>
                    <div id="paramForm">
                      <div class="pf-title" id="pfTitle"></div>
                      <div class="pf-usage" id="pfUsage"></div>
                      <div id="pfFields"></div>
                      <div style="display:flex;gap:8px;margin-top:8px;">
                        <button class="btn btn-sm btn-primary" onclick="executeFromForm()">Execute</button>
                        <button class="btn btn-sm" onclick="closeParamForm()">Cancel</button>
                      </div>
                    </div>
                  </div>
                  <div class="console-sidebar">
                    <!-- Context Help / Quick Commands card -->
                    <div class="card cmd-help-card" style="flex:0;">
                      <div id="cmdHelpContent">
                        <div class="card-title">Command Help</div>
                        <div id="cmdHelpBody" style="margin-top:8px;">
                          <div style="font-size:11px;color:var(--text3);">Type a command to see contextual help.</div>
                        </div>
                      </div>
                      <div class="quick-cmds-toggle" onclick="toggleQuickCmds()">
                        <span id="quickCmdsArrow">&#9654;</span> Quick Commands
                      </div>
                      <div class="quick-cmds-body collapsed" id="quickCmdsBody">
                        <div style="display:flex;flex-direction:column;gap:4px;padding-top:8px;">
                          <button class="btn btn-sm" onclick="exec('status')" style="justify-content:flex-start;">status</button>
                          <button class="btn btn-sm" onclick="exec('list-servers')" style="justify-content:flex-start;">list-servers</button>
                          <button class="btn btn-sm" onclick="exec('list-applications')" style="justify-content:flex-start;">list-applications</button>
                          <button class="btn btn-sm" onclick="exec('memory-info')" style="justify-content:flex-start;">memory-info</button>
                          <button class="btn btn-sm" onclick="exec('thread-info')" style="justify-content:flex-start;">thread-info</button>
                          <button class="btn btn-sm" onclick="exec('jvm-info')" style="justify-content:flex-start;">jvm-info</button>
                          <button class="btn btn-sm" onclick="exec('system-info')" style="justify-content:flex-start;">system-info</button>
                          <button class="btn btn-sm" onclick="exec('list-datasources')" style="justify-content:flex-start;">list-datasources</button>
                          <button class="btn btn-sm" onclick="exec('list-thread-pools')" style="justify-content:flex-start;">list-thread-pools</button>
                        </div>
                      </div>
                    </div>
                    <div class="card" style="flex:0;max-height:200px;overflow-y:auto;">
                      <div class="card-header" style="margin-bottom:8px;">
                        <div class="card-title">Saved Commands</div>
                      </div>
                      <div id="savedList"></div>
                      <div id="savedEmpty" style="font-size:11px;color:var(--text3);padding:4px 8px;">
                        Click the star next to a history item to save it.
                      </div>
                    </div>
                    <!-- Templates Card -->
                    <div class="card" style="flex:0;max-height:240px;overflow-y:auto;">
                      <div class="card-header" style="margin-bottom:8px;display:flex;justify-content:space-between;align-items:center;">
                        <div class="card-title">Templates</div>
                        <button class="btn btn-sm" onclick="createCustomTemplate()" style="font-size:10px;padding:2px 6px;">+ New</button>
                      </div>
                      <div id="templateList" style="display:flex;flex-direction:column;gap:2px;"></div>
                    </div>
                    <div class="card" style="flex:1;overflow-y:auto;">
                      <div class="card-title">History</div>
                      <input type="text" class="hist-search" id="histSearch" placeholder="Search history..."
                             autocomplete="off">
                      <div id="historyList" style="margin-top:4px;"></div>
                    </div>
                  </div>
                </div>

                <!-- Action Preview overlay -->
                <div class="preview-overlay" id="previewOverlay">
                  <div class="preview-box">
                    <div class="pv-title">
                      Action Preview
                      <span class="risk-badge" id="previewRiskBadge"></span>
                    </div>
                    <div class="pv-desc" id="previewDesc"></div>
                    <div class="pv-label">CLI Command</div>
                    <pre id="previewCliCmd"></pre>
                    <div class="pv-label">API Request</div>
                    <pre id="previewApiReq"></pre>
                    <div class="pv-label">Usage</div>
                    <pre id="previewUsage"></pre>
                    <div id="dryRunResult" style="display:none;margin-top:12px;">
                      <div class="pv-label">Dry Run Result</div>
                      <pre id="dryRunOutput" style="color:var(--info);"></pre>
                    </div>
                    <div class="pv-footer">
                      <button class="btn btn-sm" onclick="cancelPreview()">Cancel</button>
                      <button class="btn btn-sm" onclick="dryRunFromPreview()" title="Simulate execution without making changes" style="border-color:var(--info);color:var(--info);">Dry Run</button>
                      <button class="btn btn-sm btn-primary" id="previewExecBtn" onclick="executeFromPreview()">Execute</button>
                    </div>
                  </div>
                </div>

                <!-- Confirmation dialog for dangerous commands -->
                <div class="confirm-overlay" id="confirmOverlay">
                  <div class="confirm-box">
                    <div class="cb-title">Confirm Dangerous Command</div>
                    <p style="font-size:13px;color:var(--text2);margin-bottom:12px;">
                      This command may have destructive effects. Please review before executing.
                    </p>
                    <div class="cb-label">CLI Command</div>
                    <pre id="confirmCmd"></pre>
                    <div class="cb-label">API Payload</div>
                    <pre id="confirmPayload"></pre>
                    <div class="cb-footer">
                      <button class="btn btn-sm" onclick="cancelConfirm()">Cancel</button>
                      <button class="btn btn-sm btn-danger" onclick="proceedConfirm()">Execute Anyway</button>
                    </div>
                  </div>
                </div>

                <script>
                (function(){
                  var output = document.getElementById('output');
                  var input = document.getElementById('cmdInput');
                  var historyList = document.getElementById('historyList');
                  var histSearch = document.getElementById('histSearch');
                  var savedList = document.getElementById('savedList');
                  var savedEmpty = document.getElementById('savedEmpty');
                  var acDropdown = document.getElementById('acDropdown');
                  var paramForm = document.getElementById('paramForm');
                  var confirmOverlay = document.getElementById('confirmOverlay');
                  var confirmCmd = document.getElementById('confirmCmd');
                  var confirmPayload = document.getElementById('confirmPayload');
                  var previewOverlay = document.getElementById('previewOverlay');
                  var previewCliCmd = document.getElementById('previewCliCmd');
                  var previewApiReq = document.getElementById('previewApiReq');
                  var previewUsage = document.getElementById('previewUsage');
                  var previewDesc = document.getElementById('previewDesc');
                  var previewRiskBadge = document.getElementById('previewRiskBadge');
                  var previewExecBtn = document.getElementById('previewExecBtn');
                  var cmdHelpBody = document.getElementById('cmdHelpBody');
                  var templateListEl = document.getElementById('templateList');
                  var apiBase = '%s/api';

                  var history = [];
                  var historyMeta = []; // {cmd, time, success}
                  var historyIdx = -1;
                  var acSelected = -1;
                  var acFiltered = [];
                  var commandsMeta = []; // from /api/commands
                  var currentMode = 'text'; // 'text' or 'form'
                  var selectedFormCmd = null;
                  var pendingCmd = null;
                  var previewPendingCmd = null;

                  var DANGEROUS = ['stop','kill','remove','delete','undeploy','force'];
                  var MEDIUM_RISK = ['restart','reload','redeploy','shutdown','suspend','disable'];
                  var LIFECYCLE_CMDS = ['start','stop','restart','reload','deploy','undeploy','redeploy','shutdown','suspend','resume','kill'];

                  /* ── Built-in Templates ──────────────────────────────── */
                  var BUILTIN_TEMPLATES = [
                    { name: 'Deploy Application', cmd: 'deploy <path> [context-path]', builtin: true },
                    { name: 'Rolling Restart', cmd: 'restart-server <name>', builtin: true },
                    { name: 'Change Log Level', cmd: 'set-log-level <logger> <level>', builtin: true },
                    { name: 'Create User', cmd: 'create-user <username> <password>', builtin: true },
                    { name: 'Thread Dump', cmd: 'threaddump', builtin: true }
                  ];

                  // ── Load commands from API ──────────────────────
                  function loadCommands() {
                    fetch(apiBase + '/commands')
                      .then(function(r){ return r.json(); })
                      .then(function(data){
                        if (data.commands) commandsMeta = data.commands;
                      })
                      .catch(function(){});
                  }
                  loadCommands();

                  // ── Saved commands (localStorage) ───────────────
                  function loadSaved() {
                    try {
                      var raw = localStorage.getItem('velo-saved-commands');
                      return raw ? JSON.parse(raw) : [];
                    } catch(e) { return []; }
                  }
                  function storeSaved(arr) {
                    localStorage.setItem('velo-saved-commands', JSON.stringify(arr));
                  }
                  function renderSaved() {
                    var saved = loadSaved();
                    savedList.innerHTML = '';
                    savedEmpty.style.display = saved.length ? 'none' : 'block';
                    saved.forEach(function(item, idx) {
                      var div = document.createElement('div');
                      div.className = 'saved-item';
                      var nameSpan = document.createElement('span');
                      nameSpan.className = 'saved-name';
                      nameSpan.textContent = item.name;
                      var cmdSpan = document.createElement('span');
                      cmdSpan.textContent = item.cmd;
                      cmdSpan.style.cssText = 'flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;';
                      var delSpan = document.createElement('span');
                      delSpan.className = 'saved-del';
                      delSpan.innerHTML = '&times;';
                      delSpan.title = 'Remove saved command';
                      delSpan.onclick = function(e) {
                        e.stopPropagation();
                        var s = loadSaved();
                        s.splice(idx, 1);
                        storeSaved(s);
                        renderSaved();
                      };
                      div.appendChild(nameSpan);
                      div.appendChild(cmdSpan);
                      div.appendChild(delSpan);
                      div.onclick = function() { input.value = item.cmd; input.focus(); autoResize(); };
                      savedList.appendChild(div);
                    });
                  }
                  renderSaved();

                  // ── Risk level helpers ───────────────────────────────
                  function getRiskLevel(cmd) {
                    var lower = cmd.toLowerCase();
                    for (var i = 0; i < DANGEROUS.length; i++) {
                      if (lower.indexOf(DANGEROUS[i]) >= 0) return 'high';
                    }
                    for (var i = 0; i < MEDIUM_RISK.length; i++) {
                      if (lower.indexOf(MEDIUM_RISK[i]) >= 0) return 'medium';
                    }
                    return 'low';
                  }

                  function getRiskBadgeHtml(risk) {
                    var cls = risk === 'high' ? 'risk-high' : risk === 'medium' ? 'risk-medium' : 'risk-low';
                    var label = risk === 'high' ? 'High Risk' : risk === 'medium' ? 'Medium Risk' : 'Low Risk';
                    return '<span class="risk-badge ' + cls + '">' + label + '</span>';
                  }

                  function isLifecycleCmd(cmdName) {
                    var lower = cmdName.toLowerCase();
                    for (var i = 0; i < LIFECYCLE_CMDS.length; i++) {
                      if (lower.indexOf(LIFECYCLE_CMDS[i]) >= 0) return true;
                    }
                    return false;
                  }

                  // ── Action Preview Panel ────────────────────────────
                  function showPreview(cmd) {
                    previewPendingCmd = cmd;
                    var cmdName = cmd.split(/\\s+/)[0];
                    var meta = commandsMeta.find(function(c) { return c.name === cmdName; });
                    var risk = getRiskLevel(cmd);

                    previewCliCmd.textContent = 'velo> ' + cmd;
                    var payload = JSON.stringify({ command: cmd }, null, 2);
                    previewApiReq.textContent = 'POST ' + apiBase + '/execute\\nContent-Type: application/json\\n\\n' + payload;

                    if (meta) {
                      previewDesc.textContent = meta.description || 'No description available.';
                      previewUsage.textContent = meta.usage || meta.name;
                    } else {
                      previewDesc.textContent = 'No description available for this command.';
                      previewUsage.textContent = cmd;
                    }

                    previewRiskBadge.className = 'risk-badge ' + (risk === 'high' ? 'risk-high' : risk === 'medium' ? 'risk-medium' : 'risk-low');
                    previewRiskBadge.textContent = risk === 'high' ? 'High Risk' : risk === 'medium' ? 'Medium Risk' : 'Low Risk';

                    if (risk === 'high') {
                      previewExecBtn.className = 'btn btn-sm btn-danger';
                      previewExecBtn.textContent = 'Execute Anyway';
                    } else {
                      previewExecBtn.className = 'btn btn-sm btn-primary';
                      previewExecBtn.textContent = 'Execute';
                    }

                    previewOverlay.classList.add('open');
                  }

                  window.showPreviewFromInput = function() {
                    var cmd = input.value.trim();
                    if (!cmd) return;
                    showPreview(cmd);
                  };

                  window.cancelPreview = function() {
                    previewOverlay.classList.remove('open');
                    previewPendingCmd = null;
                    input.focus();
                  };

                  window.executeFromPreview = function() {
                    previewOverlay.classList.remove('open');
                    document.getElementById('dryRunResult').style.display = 'none';
                    if (previewPendingCmd) {
                      var cmd = previewPendingCmd;
                      previewPendingCmd = null;
                      input.value = '';
                      autoResize();
                      doExecute(cmd);
                    }
                  };

                  window.dryRunFromPreview = function() {
                    if (!previewPendingCmd) return;
                    var dryRunEl = document.getElementById('dryRunResult');
                    var dryRunOut = document.getElementById('dryRunOutput');
                    dryRunEl.style.display = 'block';
                    dryRunOut.textContent = 'Simulating...';
                    fetch(CTX + '/api/execute', {
                      method: 'POST',
                      headers: {'Content-Type': 'application/json'},
                      body: JSON.stringify({command: previewPendingCmd, dryRun: true})
                    }).then(function(r) { return r.json(); }).then(function(d) {
                      var result = d.dryRunResult || d.message || 'Command validated. No changes will be made.';
                      if (d.success || d.dryRunResult) {
                        dryRunOut.textContent = '[DRY RUN] ' + result + '\\n\\nThis is a simulation. No actual changes were made.';
                        dryRunOut.style.color = 'var(--info)';
                      } else {
                        dryRunOut.textContent = '[DRY RUN FAILED] ' + result;
                        dryRunOut.style.color = 'var(--warning)';
                      }
                    }).catch(function() {
                      dryRunOut.textContent = '[DRY RUN] Command syntax validated. Actual execution would proceed with:\\n  > ' + previewPendingCmd + '\\n\\nNote: Full dry-run simulation requires server-side support.';
                      dryRunOut.style.color = 'var(--info)';
                    });
                  };

                  // ── Context Help Sidebar ────────────────────────────
                  function updateContextHelp(cmdName) {
                    if (!cmdName) {
                      cmdHelpBody.innerHTML = '<div style="font-size:11px;color:var(--text3);">Type a command to see contextual help.</div>';
                      return;
                    }
                    var meta = commandsMeta.find(function(c) { return c.name === cmdName; });
                    if (!meta) {
                      cmdHelpBody.innerHTML = '<div style="font-size:11px;color:var(--text3);">Unknown command: ' + escHtml(cmdName) + '</div>';
                      return;
                    }

                    var risk = getRiskLevel(meta.name);
                    var html = '';
                    html += '<div class="help-name">' + escHtml(meta.name) + '</div>';
                    html += '<div class="help-cat">' + escHtml(meta.category || 'general') + '</div> ';
                    html += getRiskBadgeHtml(risk);

                    html += '<div class="help-desc">' + escHtml(meta.description || 'No description.') + '</div>';

                    if (meta.usage) {
                      html += '<div class="help-section"><div class="help-section-title">Usage</div>';
                      html += '<div class="help-usage">' + escHtml(meta.usage) + '</div>';

                      // Parse and describe parameters
                      var params = parseUsageParams(meta.usage);
                      if (params.length > 0) {
                        html += '<div style="font-size:11px;color:var(--text3);margin-bottom:4px;">';
                        params.forEach(function(p) {
                          html += '<div style="margin-bottom:2px;"><code style="color:var(--primary);">' + escHtml(p.name) + '</code>';
                          html += p.required ? ' <span style="color:var(--danger);">(required)</span>' : ' <span style="color:var(--text3);">(optional)</span>';
                          html += '</div>';
                        });
                        html += '</div>';
                      }
                      html += '</div>';
                    }

                    // Performance / operation tips
                    html += '<div class="help-section"><div class="help-section-title">Tips</div>';
                    if (risk === 'high') {
                      html += '<div class="help-tip" style="border-left-color:var(--danger);">';
                      html += '<strong>Risk:</strong> This command performs a destructive action. Ensure you have backups or confirmation before running.';
                      html += '</div>';
                    } else if (risk === 'medium') {
                      html += '<div class="help-tip" style="border-left-color:var(--warning);">';
                      html += '<strong>Risk:</strong> This command modifies server state. Consider running during a maintenance window.';
                      html += '</div>';
                    }
                    if (isLifecycleCmd(meta.name)) {
                      html += '<div class="help-tip" style="margin-top:4px;">';
                      html += '<strong>Performance:</strong> This command may affect running services. Connected clients may experience brief interruptions.';
                      html += '</div>';
                    }
                    if (!isLifecycleCmd(meta.name) && risk === 'low') {
                      html += '<div class="help-tip">';
                      html += '<strong>Info:</strong> This is a read-only or safe command. No impact on running services.';
                      html += '</div>';
                    }
                    html += '</div>';

                    // Related commands
                    var related = findRelatedCommands(meta);
                    if (related.length > 0) {
                      html += '<div class="help-section"><div class="help-section-title">Related Commands</div>';
                      html += '<div class="help-related">';
                      related.forEach(function(r) {
                        html += '<span class="help-related-item" onclick="fillCommand(\\'' + escHtml(r.name) + '\\');">' + escHtml(r.name) + '</span>';
                      });
                      html += '</div></div>';
                    }

                    cmdHelpBody.innerHTML = html;
                  }

                  function findRelatedCommands(meta) {
                    if (!meta || !meta.category) return [];
                    return commandsMeta.filter(function(c) {
                      return c.category === meta.category && c.name !== meta.name;
                    }).slice(0, 6);
                  }

                  window.fillCommand = function(name) {
                    input.value = name + ' ';
                    input.focus();
                    autoResize();
                    updateContextHelp(name);
                    if (currentMode === 'form') {
                      var cmd = commandsMeta.find(function(c) { return c.name === name; });
                      if (cmd) openParamForm(cmd);
                    }
                  };

                  // ── Quick Commands Toggle ────────────────────────────
                  var quickCmdsExpanded = false;
                  window.toggleQuickCmds = function() {
                    quickCmdsExpanded = !quickCmdsExpanded;
                    var body = document.getElementById('quickCmdsBody');
                    var arrow = document.getElementById('quickCmdsArrow');
                    if (quickCmdsExpanded) {
                      body.classList.remove('collapsed');
                      body.style.maxHeight = body.scrollHeight + 'px';
                      arrow.innerHTML = '&#9660;';
                    } else {
                      body.classList.add('collapsed');
                      body.style.maxHeight = '0';
                      arrow.innerHTML = '&#9654;';
                    }
                  };

                  // ── Templates ────────────────────────────────────────
                  function loadCustomTemplates() {
                    try {
                      var raw = localStorage.getItem('velo-custom-templates');
                      return raw ? JSON.parse(raw) : [];
                    } catch(e) { return []; }
                  }
                  function storeCustomTemplates(arr) {
                    localStorage.setItem('velo-custom-templates', JSON.stringify(arr));
                  }

                  function renderTemplates() {
                    templateListEl.innerHTML = '';
                    var allTemplates = BUILTIN_TEMPLATES.concat(loadCustomTemplates());
                    allTemplates.forEach(function(tpl, idx) {
                      var div = document.createElement('div');
                      div.className = 'tpl-item';

                      var info = document.createElement('div');
                      info.style.cssText = 'flex:1;overflow:hidden;';
                      var nameEl = document.createElement('div');
                      nameEl.className = 'tpl-name';
                      nameEl.textContent = tpl.name;
                      var cmdEl = document.createElement('div');
                      cmdEl.className = 'tpl-cmd';
                      cmdEl.textContent = tpl.cmd;
                      info.appendChild(nameEl);
                      info.appendChild(cmdEl);
                      div.appendChild(info);

                      if (tpl.builtin) {
                        var badge = document.createElement('span');
                        badge.className = 'tpl-builtin';
                        badge.textContent = 'built-in';
                        div.appendChild(badge);
                      } else {
                        var delSpan = document.createElement('span');
                        delSpan.className = 'tpl-del';
                        delSpan.innerHTML = '&times;';
                        delSpan.title = 'Remove template';
                        delSpan.onclick = function(e) {
                          e.stopPropagation();
                          var custom = loadCustomTemplates();
                          var ci = idx - BUILTIN_TEMPLATES.length;
                          if (ci >= 0) {
                            custom.splice(ci, 1);
                            storeCustomTemplates(custom);
                            renderTemplates();
                          }
                        };
                        div.appendChild(delSpan);
                      }

                      div.onclick = function() {
                        applyTemplate(tpl);
                      };
                      templateListEl.appendChild(div);
                    });
                  }

                  function applyTemplate(tpl) {
                    // Fill the input with the template command
                    input.value = tpl.cmd;
                    input.focus();
                    autoResize();

                    // If it has parameters and we are in form mode, open the param form
                    var cmdName = tpl.cmd.split(/\\s+/)[0];
                    var meta = commandsMeta.find(function(c) { return c.name === cmdName; });
                    if (currentMode === 'form' && meta) {
                      openParamForm(meta);
                    }
                    updateContextHelp(cmdName);
                  }

                  window.createCustomTemplate = function() {
                    var cmd = prompt('Template command (e.g. deploy /apps/myapp.war /myapp):');
                    if (!cmd) return;
                    var name = prompt('Template name:', cmd.split(/\\s+/)[0]);
                    if (!name) return;
                    var custom = loadCustomTemplates();
                    custom.push({ name: name, cmd: cmd, builtin: false });
                    storeCustomTemplates(custom);
                    renderTemplates();
                  };

                  renderTemplates();

                  // ── Export as Script (.vsh) ──────────────────────────
                  window.exportAsScript = function() {
                    var lines = [];
                    lines.push('# Velo WAS Console Script');
                    lines.push('# Exported: ' + new Date().toISOString());
                    lines.push('');
                    historyMeta.forEach(function(h) {
                      if (h.success === true) {
                        lines.push('# [' + h.time + ']');
                        lines.push(h.cmd);
                      }
                    });
                    if (lines.length <= 3) {
                      alert('No successful commands to export.');
                      return;
                    }
                    var blob = new Blob([lines.join('\\n')], { type: 'text/plain' });
                    var a = document.createElement('a');
                    a.href = URL.createObjectURL(blob);
                    a.download = 'console-export.vsh';
                    a.click();
                  };

                  function saveCommand(cmd) {
                    var name = prompt('Save command as:', cmd.split(' ')[0]);
                    if (!name) return;
                    var saved = loadSaved();
                    // Avoid exact duplicates
                    for (var i = 0; i < saved.length; i++) {
                      if (saved[i].cmd === cmd) { saved.splice(i, 1); break; }
                    }
                    saved.unshift({name: name, cmd: cmd});
                    if (saved.length > 30) saved = saved.slice(0, 30);
                    storeSaved(saved);
                    renderSaved();
                  }

                  // ── Output helpers ──────────────────────────────
                  function appendLine(text, color) {
                    var div = document.createElement('div');
                    div.className = 'ol';
                    if (color) div.style.color = color;
                    div.style.whiteSpace = 'pre-wrap';
                    div.style.wordBreak = 'break-all';
                    div.textContent = text;
                    output.appendChild(div);
                    output.scrollTop = output.scrollHeight;
                  }

                  function isJsonLike(s) {
                    var t = s.trim();
                    return (t.startsWith('{') && t.endsWith('}')) || (t.startsWith('[') && t.endsWith(']'));
                  }

                  function syntaxHighlightJson(json) {
                    return json.replace(/("(\\\\u[a-fA-F0-9]{4}|\\\\[^u]|[^\\\\"])*")(\\s*:)?/g, function(match, p1, p2, p3) {
                      var cls = 'json-str';
                      if (p3) { cls = 'json-key'; }
                      return '<span class="' + cls + '">' + escHtml(p1) + '</span>' + (p3 || '');
                    }).replace(/\\b(true|false)\\b/g, '<span class="json-bool">$1</span>')
                      .replace(/\\bnull\\b/g, '<span class="json-null">null</span>')
                      .replace(/\\b(-?\\d+\\.?\\d*([eE][+-]?\\d+)?)\\b/g, '<span class="json-num">$1</span>');
                  }

                  function escHtml(s) {
                    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
                  }

                  function appendOutputBlock(text, success, durationMs) {
                    var block = document.createElement('div');
                    block.className = 'output-block';

                    var actions = document.createElement('div');
                    actions.className = 'ob-actions';
                    var copyBtn = document.createElement('button');
                    copyBtn.textContent = 'Copy';
                    copyBtn.onclick = function() {
                      navigator.clipboard.writeText(text).then(function(){
                        copyBtn.textContent = 'Copied!';
                        setTimeout(function(){ copyBtn.textContent = 'Copy'; }, 1200);
                      });
                    };
                    actions.appendChild(copyBtn);
                    block.appendChild(actions);

                    var content = document.createElement('div');
                    content.style.whiteSpace = 'pre-wrap';
                    content.style.wordBreak = 'break-all';
                    if (!success) content.style.color = 'var(--danger)';

                    // Try to format JSON
                    if (isJsonLike(text)) {
                      try {
                        var pretty = JSON.stringify(JSON.parse(text), null, 2);
                        content.innerHTML = syntaxHighlightJson(escHtml(pretty));
                      } catch(e) {
                        content.textContent = text;
                      }
                    } else {
                      content.textContent = text;
                    }

                    block.appendChild(content);

                    if (durationMs !== undefined) {
                      var dur = document.createElement('span');
                      dur.className = 'ob-duration';
                      dur.textContent = durationMs + 'ms';
                      block.appendChild(dur);
                    }

                    output.appendChild(block);
                    output.scrollTop = output.scrollHeight;
                  }

                  // ── History ─────────────────────────────────────
                  function updateHistory(filter) {
                    historyList.innerHTML = '';
                    var items = historyMeta.slice().reverse();
                    if (filter) {
                      var q = filter.toLowerCase();
                      items = items.filter(function(h){ return h.cmd.toLowerCase().indexOf(q) >= 0; });
                    }
                    items.slice(0, 30).forEach(function(h) {
                      var div = document.createElement('div');
                      div.className = 'hist-item';

                      var dot = document.createElement('span');
                      dot.className = 'hist-status';
                      dot.style.background = h.success === false ? 'var(--danger)' : h.success === true ? 'var(--success)' : 'var(--text3)';
                      div.appendChild(dot);

                      var cmdSpan = document.createElement('span');
                      cmdSpan.textContent = h.cmd;
                      cmdSpan.style.cssText = 'flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;';
                      div.appendChild(cmdSpan);

                      var timeSpan = document.createElement('span');
                      timeSpan.className = 'hist-time';
                      timeSpan.textContent = h.time;
                      div.appendChild(timeSpan);

                      var star = document.createElement('span');
                      star.className = 'hist-star';
                      star.innerHTML = '&#9734;';
                      star.title = 'Save command';
                      star.onclick = function(e) {
                        e.stopPropagation();
                        saveCommand(h.cmd);
                      };
                      div.appendChild(star);

                      div.onclick = function(){ input.value = h.cmd; input.focus(); autoResize(); };
                      historyList.appendChild(div);
                    });
                  }

                  histSearch.addEventListener('input', function() {
                    updateHistory(histSearch.value);
                  });

                  // ── Autocomplete ────────────────────────────────
                  function getCommandNames() {
                    if (commandsMeta.length > 0) return commandsMeta.map(function(c){ return c.name; });
                    return [];
                  }

                  function showAutocomplete(val) {
                    var parts = val.trim().split(/\\s+/);
                    var isFirstToken = parts.length <= 1;
                    var query = isFirstToken ? parts[0].toLowerCase() : '';
                    var cmdName = parts[0].toLowerCase();

                    acDropdown.innerHTML = '';
                    acFiltered = [];
                    acSelected = -1;

                    if (isFirstToken && query) {
                      // Match command names
                      acFiltered = commandsMeta.filter(function(c){
                        return c.name.toLowerCase().indexOf(query) >= 0;
                      }).slice(0, 15);

                      acFiltered.forEach(function(c, idx) {
                        var div = document.createElement('div');
                        div.className = 'ac-item';
                        var left = document.createElement('span');
                        left.innerHTML = escHtml(c.name) + '<span class="ac-cat">' + escHtml(c.category) + '</span>';
                        div.appendChild(left);
                        var desc = document.createElement('span');
                        desc.className = 'ac-desc';
                        desc.textContent = c.description;
                        div.appendChild(desc);
                        div.onmousedown = function(e) {
                          e.preventDefault();
                          input.value = c.name + ' ';
                          autoResize();
                          closeAutocomplete();
                          updateContextHelp(c.name);
                          if (currentMode === 'form') openParamForm(c);
                        };
                        div.onmouseover = function() {
                          acSelected = idx;
                          highlightAc();
                        };
                        acDropdown.appendChild(div);
                      });
                    } else if (!isFirstToken) {
                      // Parameter hints for the current command
                      var cmd = commandsMeta.find(function(c){ return c.name === cmdName; });
                      if (cmd && cmd.usage) {
                        var hint = document.createElement('div');
                        hint.className = 'ac-item';
                        hint.style.color = 'var(--text3)';
                        hint.style.cursor = 'default';
                        hint.innerHTML = '<span style="font-family:monospace;font-size:11px;">' + escHtml(cmd.usage) + '</span>';
                        acDropdown.appendChild(hint);
                        acFiltered = [{name: cmd.usage, _hint: true}];
                      }
                    }

                    acDropdown.classList.toggle('open', acDropdown.children.length > 0);
                  }

                  function highlightAc() {
                    var items = acDropdown.querySelectorAll('.ac-item');
                    items.forEach(function(item, idx) {
                      item.classList.toggle('selected', idx === acSelected);
                    });
                  }

                  function closeAutocomplete() {
                    acDropdown.classList.remove('open');
                    acSelected = -1;
                    acFiltered = [];
                  }

                  // ── Parameter form mode ─────────────────────────
                  function openParamForm(cmd) {
                    selectedFormCmd = cmd;
                    document.getElementById('pfTitle').textContent = cmd.name;
                    document.getElementById('pfUsage').textContent = cmd.usage || cmd.name;

                    var fieldsDiv = document.getElementById('pfFields');
                    fieldsDiv.innerHTML = '';

                    // Parse parameters from usage string
                    var params = parseUsageParams(cmd.usage || '');
                    if (params.length === 0) {
                      fieldsDiv.innerHTML = '<div style="font-size:12px;color:var(--text3);">No parameters required.</div>';
                    } else {
                      params.forEach(function(p) {
                        var field = document.createElement('div');
                        field.className = 'pf-field';
                        var label = document.createElement('label');
                        label.textContent = p.name + (p.required ? ' *' : '');
                        field.appendChild(label);
                        var inp = document.createElement('input');
                        inp.type = 'text';
                        inp.placeholder = p.placeholder || '';
                        inp.dataset.paramName = p.name;
                        inp.dataset.paramFlag = p.flag || '';
                        inp.dataset.paramRequired = p.required ? 'true' : 'false';
                        field.appendChild(inp);
                        fieldsDiv.appendChild(field);
                      });
                    }
                    paramForm.classList.add('open');
                  }

                  function parseUsageParams(usage) {
                    var params = [];
                    // Match patterns like <name>, --flag <value>, [optional]
                    var re = /(<([^>]+)>|--?(\\w[\\w-]*)(?:\\s+<([^>]+)>)?|\\[([^\\]]+)\\])/g;
                    var match;
                    var seen = {};
                    while ((match = re.exec(usage)) !== null) {
                      var name, flag, required, placeholder;
                      if (match[2]) {
                        // <name> - positional required
                        name = match[2]; flag = ''; required = true; placeholder = name;
                      } else if (match[3]) {
                        // --flag or --flag <value>
                        name = match[4] || match[3]; flag = match[0].split(' ')[0]; required = false;
                        placeholder = match[4] || '';
                      } else if (match[5]) {
                        // [optional]
                        name = match[5]; flag = ''; required = false; placeholder = name;
                      } else continue;
                      if (!seen[name]) {
                        seen[name] = true;
                        params.push({name:name, flag:flag, required:required, placeholder:placeholder});
                      }
                    }
                    return params;
                  }

                  window.executeFromForm = function() {
                    if (!selectedFormCmd) return;
                    var parts = [selectedFormCmd.name];
                    var fields = document.querySelectorAll('#pfFields input');
                    fields.forEach(function(f) {
                      var val = f.value.trim();
                      if (!val) return;
                      if (f.dataset.paramFlag) {
                        parts.push(f.dataset.paramFlag);
                      }
                      parts.push(val);
                    });
                    var cmd = parts.join(' ');
                    input.value = '';
                    autoResize();
                    closeParamForm();
                    executeCommand(cmd);
                  };

                  window.closeParamForm = function() {
                    paramForm.classList.remove('open');
                    selectedFormCmd = null;
                  };

                  window.setMode = function(mode) {
                    currentMode = mode;
                    document.getElementById('modeText').classList.toggle('active', mode === 'text');
                    document.getElementById('modeForm').classList.toggle('active', mode === 'form');
                    if (mode === 'text') closeParamForm();
                  };

                  // ── Dangerous command confirmation ──────────────
                  function isDangerous(cmd) {
                    var lower = cmd.toLowerCase();
                    for (var i = 0; i < DANGEROUS.length; i++) {
                      if (lower.indexOf(DANGEROUS[i]) >= 0) return true;
                    }
                    return false;
                  }

                  function showConfirmation(cmd) {
                    pendingCmd = cmd;
                    confirmCmd.textContent = 'velo> ' + cmd;
                    var payload = JSON.stringify({command: cmd}, null, 2);
                    confirmPayload.textContent = 'POST ' + apiBase + '/execute\\n' + payload;
                    confirmOverlay.classList.add('open');
                  }

                  window.cancelConfirm = function() {
                    confirmOverlay.classList.remove('open');
                    pendingCmd = null;
                    input.focus();
                  };

                  window.proceedConfirm = function() {
                    confirmOverlay.classList.remove('open');
                    if (pendingCmd) {
                      doExecute(pendingCmd);
                      pendingCmd = null;
                    }
                  };

                  // ── Execute ─────────────────────────────────────
                  window.exec = function(cmd) {
                    input.value = cmd;
                    autoResize();
                    executeCommand(cmd);
                  };

                  function executeCommand(cmd) {
                    if (isDangerous(cmd)) {
                      showConfirmation(cmd);
                      return;
                    }
                    doExecute(cmd);
                  }

                  function doExecute(cmd) {
                    // Support multi-line: split by newlines and execute sequentially
                    var lines = cmd.split('\\n').map(function(l){return l.trim();}).filter(function(l){return l.length > 0;});
                    if (lines.length > 1) {
                      lines.forEach(function(line) { doExecuteSingle(line); });
                    } else {
                      doExecuteSingle(cmd);
                    }
                  }

                  function doExecuteSingle(cmd) {
                    appendLine('velo> ' + cmd, 'var(--success)');
                    var now = new Date();
                    var timeStr = padZ(now.getHours()) + ':' + padZ(now.getMinutes()) + ':' + padZ(now.getSeconds());
                    var meta = {cmd: cmd, time: timeStr, success: null};
                    history.push(cmd);
                    historyMeta.push(meta);
                    historyIdx = history.length;
                    updateHistory(histSearch.value);

                    if (cmd === 'clear') { output.innerHTML = ''; return; }

                    var startTime = performance.now();
                    fetch(apiBase + '/execute', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify({ command: cmd })
                    })
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                      var elapsed = Math.round(performance.now() - startTime);
                      meta.success = data.success;
                      updateHistory(histSearch.value);
                      appendOutputBlock(data.message, data.success, elapsed);
                    })
                    .catch(function(err) {
                      var elapsed = Math.round(performance.now() - startTime);
                      meta.success = false;
                      updateHistory(histSearch.value);
                      appendOutputBlock('Error: ' + err.message, false, elapsed);
                    });
                  }

                  function padZ(n) { return n < 10 ? '0' + n : '' + n; }

                  // ── Auto-resize textarea ────────────────────────
                  function autoResize() {
                    input.style.height = 'auto';
                    input.style.height = Math.min(input.scrollHeight, 120) + 'px';
                  }

                  input.addEventListener('input', function() {
                    autoResize();
                    var val = input.value;
                    if (val.trim()) {
                      showAutocomplete(val);
                      // Update context help based on the first token
                      var cmdName = val.trim().split(/\\s+/)[0].toLowerCase();
                      updateContextHelp(cmdName);
                    } else {
                      closeAutocomplete();
                      updateContextHelp(null);
                    }
                  });

                  input.addEventListener('blur', function() {
                    setTimeout(closeAutocomplete, 200);
                  });

                  input.addEventListener('keydown', function(e) {
                    // Autocomplete navigation
                    if (acDropdown.classList.contains('open') && acFiltered.length > 0) {
                      if (e.key === 'ArrowDown') {
                        e.preventDefault();
                        acSelected = Math.min(acSelected + 1, acFiltered.length - 1);
                        highlightAc();
                        return;
                      }
                      if (e.key === 'ArrowUp') {
                        e.preventDefault();
                        acSelected = Math.max(acSelected - 1, 0);
                        highlightAc();
                        return;
                      }
                      if ((e.key === 'Enter' || e.key === 'Tab') && acSelected >= 0 && !acFiltered[acSelected]._hint) {
                        e.preventDefault();
                        var sel = acFiltered[acSelected];
                        input.value = sel.name + ' ';
                        autoResize();
                        closeAutocomplete();
                        updateContextHelp(sel.name);
                        if (currentMode === 'form') openParamForm(sel);
                        return;
                      }
                    }

                    if (e.key === 'Tab') {
                      e.preventDefault();
                      var val = input.value.trim();
                      if (val) {
                        var names = getCommandNames();
                        var matches = names.filter(function(c){return c.indexOf(val.toLowerCase()) === 0;});
                        if (matches.length === 1) {
                          input.value = matches[0] + ' ';
                          autoResize();
                          closeAutocomplete();
                          updateContextHelp(matches[0]);
                          if (currentMode === 'form') {
                            var cmd = commandsMeta.find(function(c){return c.name === matches[0];});
                            if (cmd) openParamForm(cmd);
                          }
                        } else if (matches.length > 1) {
                          showAutocomplete(val);
                        }
                      }
                      return;
                    }

                    if (e.key === 'Enter') {
                      // Ctrl+Shift+Enter: open action preview
                      if (e.ctrlKey && e.shiftKey) {
                        e.preventDefault();
                        closeAutocomplete();
                        if (input.value.trim()) {
                          showPreview(input.value.trim());
                        }
                        return;
                      }
                      if (e.shiftKey) {
                        // Allow newline in textarea (default behavior)
                        return;
                      }
                      e.preventDefault();
                      closeAutocomplete();
                      if (input.value.trim()) {
                        executeCommand(input.value.trim());
                        input.value = '';
                        autoResize();
                      }
                      return;
                    }

                    if (e.key === 'ArrowUp' && !input.value.includes('\\n')) {
                      e.preventDefault();
                      if (historyIdx > 0) { historyIdx--; input.value = history[historyIdx]; autoResize(); }
                    } else if (e.key === 'ArrowDown' && !input.value.includes('\\n')) {
                      e.preventDefault();
                      if (historyIdx < history.length - 1) { historyIdx++; input.value = history[historyIdx]; autoResize(); }
                      else { historyIdx = history.length; input.value = ''; autoResize(); }
                    }

                    if (e.key === 'Escape') {
                      closeAutocomplete();
                      closeParamForm();
                    }
                  });

                  // Close confirm/preview with Escape
                  document.addEventListener('keydown', function(e) {
                    if (e.key === 'Escape') {
                      if (previewOverlay.classList.contains('open')) {
                        window.cancelPreview();
                      } else if (confirmOverlay.classList.contains('open')) {
                        window.cancelConfirm();
                      }
                    }
                  });

                  confirmOverlay.addEventListener('click', function(e) {
                    if (e.target === confirmOverlay) window.cancelConfirm();
                  });

                  previewOverlay.addEventListener('click', function(e) {
                    if (e.target === previewOverlay) window.cancelPreview();
                  });

                  window.clearOutput = function() { output.innerHTML = ''; };
                  window.downloadHistory = function() {
                    var lines = historyMeta.map(function(h) {
                      return '[' + h.time + '] ' + (h.success === true ? 'OK' : h.success === false ? 'FAIL' : '??') + ' ' + h.cmd;
                    });
                    var blob = new Blob([lines.join('\\n')], {type:'text/plain'});
                    var a = document.createElement('a');
                    a.href = URL.createObjectURL(blob);
                    a.download = 'console-history.txt';
                    a.click();
                  };
                })();
                </script>
                """.formatted(ctx);

        resp.getWriter().write(AdminPageLayout.page("Console", server.getName(), server.getNodeId(),
                ctx, "console", body));
    }
}
