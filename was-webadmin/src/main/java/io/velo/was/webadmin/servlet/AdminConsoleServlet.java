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
                <div class="page-header">
                  <div>
                    <div class="page-title">Console</div>
                    <div class="page-subtitle">Execute CLI commands directly from the browser</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn btn-sm" onclick="clearOutput()">Clear</button>
                    <button class="btn btn-sm" onclick="downloadHistory()">Export History</button>
                  </div>
                </div>
                <div style="display:flex;gap:16px;height:calc(100vh - 200px);">
                  <div style="flex:1;display:flex;flex-direction:column;">
                    <div id="output" style="flex:1;background:#0a0c10;border:1px solid var(--border);
                         border-radius:var(--radius) var(--radius) 0 0;padding:16px;overflow-y:auto;
                         font-family:'JetBrains Mono','Fira Code','Cascadia Code',monospace;font-size:13px;
                         line-height:1.8;">
                      <div class="ol" style="color:var(--text2);">Velo Web Admin Console v0.1.0</div>
                      <div class="ol" style="color:var(--text2);">Type 'help' for available commands. Tab for auto-complete.</div>
                      <div class="ol" style="color:var(--text2);">---</div>
                    </div>
                    <div style="display:flex;background:#0a0c10;border:1px solid var(--border);border-top:none;
                         border-radius:0 0 var(--radius) var(--radius);padding:8px 16px;align-items:center;gap:8px;">
                      <span style="color:var(--success);font-family:monospace;font-size:13px;white-space:nowrap;">velo&gt;</span>
                      <input id="cmdInput" type="text" autocomplete="off" autofocus
                             placeholder="Enter command..."
                             style="flex:1;background:transparent;border:none;color:var(--text);
                                    font-family:'JetBrains Mono','Fira Code',monospace;font-size:13px;outline:none;">
                    </div>
                  </div>
                  <div style="width:280px;display:flex;flex-direction:column;gap:12px;">
                    <div class="card" style="flex:0;">
                      <div class="card-title">Quick Commands</div>
                      <div style="display:flex;flex-direction:column;gap:4px;margin-top:8px;">
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
                    <div class="card" style="flex:1;overflow-y:auto;">
                      <div class="card-title">History</div>
                      <div id="historyList" style="margin-top:8px;"></div>
                    </div>
                  </div>
                </div>

                <script>
                (function(){
                  var output = document.getElementById('output');
                  var input = document.getElementById('cmdInput');
                  var historyList = document.getElementById('historyList');
                  var history = [];
                  var historyIdx = -1;
                  var apiBase = '%s/api';

                  var COMMANDS = ['help','version','status','clear',
                    'server-info','system-info','memory-info','thread-info','jvm-info','transaction-info',
                    'list-servers','start-server','stop-server','restart-server','suspend-server','resume-server','kill-server',
                    'list-applications','application-info','deploy','undeploy','redeploy','start-application','stop-application',
                    'list-clusters','cluster-info','start-cluster','stop-cluster','restart-cluster','add-server-to-cluster','remove-server-from-cluster',
                    'list-datasources','datasource-info','enable-datasource','disable-datasource','test-datasource',
                    'list-jdbc-resources','jdbc-resource-info','reset-connection-pool','flush-connection-pool',
                    'list-jms-servers','jms-server-info','list-jms-destinations','jms-destination-info','purge-jms-queue',
                    'list-thread-pools','thread-pool-info','reset-thread-pool','resource-info',
                    'list-loggers','logger-info','get-log-level','set-log-level',
                    'list-mbeans','get-mbean-attribute','set-mbean-attribute','invoke-mbean-operation',
                    'list-users','create-user','remove-user','change-password','list-roles',
                    'domain-info','list-domains','create-domain','remove-domain','set-domain-property','get-domain-property',
                    'run-script','record-script','stop-record'];

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

                  function updateHistory() {
                    historyList.innerHTML = '';
                    history.slice(-15).reverse().forEach(function(cmd) {
                      var div = document.createElement('div');
                      div.style.cssText = 'padding:4px 8px;font-size:12px;font-family:monospace;color:var(--text2);cursor:pointer;border-radius:4px;';
                      div.textContent = cmd;
                      div.onmouseover = function(){this.style.background='var(--surface2)';this.style.color='var(--text)';};
                      div.onmouseout = function(){this.style.background='';this.style.color='var(--text2)';};
                      div.onclick = function(){ input.value = cmd; input.focus(); };
                      historyList.appendChild(div);
                    });
                  }

                  window.exec = function(cmd) {
                    input.value = cmd;
                    executeCommand(cmd);
                  };

                  function executeCommand(cmd) {
                    appendLine('velo> ' + cmd, 'var(--success)');
                    history.push(cmd);
                    historyIdx = history.length;
                    updateHistory();

                    if (cmd === 'clear') { output.innerHTML = ''; return; }

                    fetch(apiBase + '/execute', {
                      method: 'POST',
                      headers: { 'Content-Type': 'application/json' },
                      body: JSON.stringify({ command: cmd })
                    })
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                      appendLine(data.message, data.success ? '' : 'var(--danger)');
                    })
                    .catch(function(err) {
                      appendLine('Error: ' + err.message, 'var(--danger)');
                    });
                  }

                  input.addEventListener('keydown', function(e) {
                    if (e.key === 'Enter' && input.value.trim()) {
                      executeCommand(input.value.trim());
                      input.value = '';
                    } else if (e.key === 'ArrowUp') {
                      e.preventDefault();
                      if (historyIdx > 0) { historyIdx--; input.value = history[historyIdx]; }
                    } else if (e.key === 'ArrowDown') {
                      e.preventDefault();
                      if (historyIdx < history.length - 1) { historyIdx++; input.value = history[historyIdx]; }
                      else { historyIdx = history.length; input.value = ''; }
                    } else if (e.key === 'Tab') {
                      e.preventDefault();
                      var val = input.value.trim().toLowerCase();
                      if (val) {
                        var matches = COMMANDS.filter(function(c){return c.startsWith(val);});
                        if (matches.length === 1) input.value = matches[0];
                        else if (matches.length > 1) appendLine('Completions: ' + matches.join(', '), 'var(--text2)');
                      }
                    }
                  });

                  window.clearOutput = function() { output.innerHTML = ''; };
                  window.downloadHistory = function() {
                    var blob = new Blob([history.join('\\n')], {type:'text/plain'});
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
