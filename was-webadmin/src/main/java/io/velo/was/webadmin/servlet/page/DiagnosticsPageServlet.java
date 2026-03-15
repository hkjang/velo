package io.velo.was.webadmin.servlet.page;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * Diagnostics page.
 * Thread dump, heap dump trigger, deadlock detection, and slow request analysis.
 */
public class DiagnosticsPageServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public DiagnosticsPageServlet(ServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        ServerConfiguration.Server server = configuration.getServer();
        String ctx = req.getContextPath();
        ThreadMXBean threadMx = ManagementFactory.getThreadMXBean();
        long[] deadlocked = threadMx.findDeadlockedThreads();
        int deadlockedCount = deadlocked != null ? deadlocked.length : 0;

        // Build thread dump summary
        ThreadInfo[] threadInfos = threadMx.getThreadInfo(threadMx.getAllThreadIds(), 8);
        StringBuilder dumpBuilder = new StringBuilder();
        for (ThreadInfo info : threadInfos) {
            if (info == null) continue;
            dumpBuilder.append("\"").append(info.getThreadName()).append("\"")
                    .append(" #").append(info.getThreadId())
                    .append(" ").append(info.isDaemon() ? "daemon" : "")
                    .append(" state=").append(info.getThreadState())
                    .append("\n");
            for (StackTraceElement ste : info.getStackTrace()) {
                dumpBuilder.append("    at ").append(ste).append("\n");
            }
            dumpBuilder.append("\n");
        }

        // Thread state summary
        int runnable = 0, waiting = 0, timedWaiting = 0, blocked = 0;
        for (ThreadInfo info : threadInfos) {
            if (info == null) continue;
            switch (info.getThreadState()) {
                case RUNNABLE -> runnable++;
                case WAITING -> waiting++;
                case TIMED_WAITING -> timedWaiting++;
                case BLOCKED -> blocked++;
                default -> {}
            }
        }

        String body = """
                <div class="page-header">
                  <div>
                    <div class="page-title">Diagnostics</div>
                    <div class="page-subtitle">Thread dumps, heap analysis, deadlock detection, and profiling</div>
                  </div>
                </div>

                <div class="tabs" id="diagTabs">
                  <div class="tab active" data-tab="threadDump">Thread Dump</div>
                  <div class="tab" data-tab="heapDump">Heap Dump</div>
                  <div class="tab" data-tab="deadlock">Deadlock</div>
                  <div class="tab" data-tab="slow">Slow Requests</div>
                </div>

                <div class="tab-panel active" id="tab-threadDump">
                  <div class="grid grid-4" style="margin-bottom:20px;">
                    <div class="card">
                      <div class="card-title">Runnable</div>
                      <div class="metric-value success" style="font-size:28px;">%d</div>
                    </div>
                    <div class="card">
                      <div class="card-title">Waiting</div>
                      <div class="metric-value" style="font-size:28px;color:var(--info);">%d</div>
                    </div>
                    <div class="card">
                      <div class="card-title">Timed Waiting</div>
                      <div class="metric-value" style="font-size:28px;color:var(--warning);">%d</div>
                    </div>
                    <div class="card">
                      <div class="card-title">Blocked</div>
                      <div class="metric-value %s" style="font-size:28px;">%d</div>
                    </div>
                  </div>

                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Thread Dump (%d threads)</div>
                      <div class="btn-group">
                        <button class="btn btn-sm btn-primary" onclick="refreshThreadDump()">Capture New Dump</button>
                        <button class="btn btn-sm" onclick="downloadDump()">Download</button>
                        <button class="btn btn-sm" onclick="copyDump()">Copy</button>
                      </div>
                    </div>
                    <pre id="threadDumpOutput" style="font-family:'JetBrains Mono',monospace;font-size:11px;
                         background:var(--bg);padding:16px;border-radius:8px;max-height:500px;
                         overflow:auto;line-height:1.6;white-space:pre-wrap;">%s</pre>
                  </div>
                </div>

                <div class="tab-panel" id="tab-heapDump">
                  <div class="card">
                    <div class="card-title">Heap Dump</div>
                    <p style="color:var(--text2);font-size:14px;margin:16px 0;">
                      Generate a heap dump for offline analysis. The dump file will be saved on the server.
                    </p>
                    <div class="alert alert-warning">Heap dump generation may cause a brief pause. Use with caution in production.</div>
                    <div class="btn-group" style="margin-top:16px;">
                      <button class="btn btn-danger" onclick="if(confirm('Generate heap dump? This may cause a brief pause.'))triggerHeapDump()">
                        Generate Heap Dump
                      </button>
                    </div>
                    <div id="heapDumpResult" style="margin-top:16px;"></div>
                  </div>
                </div>

                <div class="tab-panel" id="tab-deadlock">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Deadlock Detection</div>
                      <button class="btn btn-sm" onclick="location.reload()">Check Now</button>
                    </div>
                    %s
                  </div>
                </div>

                <div class="tab-panel" id="tab-slow">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Slow Requests</div>
                      <div style="display:flex;align-items:center;gap:8px;">
                        <span style="font-size:13px;color:var(--text2);">Threshold:</span>
                        <input class="form-input" type="number" value="3000" style="width:80px;" id="slowThreshold">
                        <span style="font-size:13px;color:var(--text2);">ms</span>
                      </div>
                    </div>
                    <div class="alert alert-info">No slow requests detected. Requests exceeding the threshold will appear here.</div>
                  </div>
                </div>

                <script>
                var CTX = '%s';
                document.querySelectorAll('#diagTabs .tab').forEach(function(tab){
                  tab.addEventListener('click', function(){
                    document.querySelectorAll('#diagTabs .tab').forEach(function(t){t.classList.remove('active');});
                    document.querySelectorAll('.tab-panel').forEach(function(p){p.classList.remove('active');});
                    tab.classList.add('active');
                    document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
                  });
                });
                function refreshThreadDump() {
                  fetch(CTX + '/api/threads').then(function(r){return r.json();}).then(function(d) {
                    var dump = '';
                    (d.threads || []).forEach(function(t) {
                      dump += '"' + t.name + '" #' + t.id + (t.daemon ? ' daemon' : '') + ' state=' + t.state + '\\n\\n';
                    });
                    document.getElementById('threadDumpOutput').textContent = dump || 'No thread data available.';
                  }).catch(function() {
                    document.getElementById('threadDumpOutput').textContent = 'Failed to fetch thread data.';
                  });
                }
                function downloadDump() {
                  var el = document.getElementById('threadDumpOutput');
                  var blob = new Blob([el.textContent], {type:'text/plain'});
                  var a = document.createElement('a');
                  a.href = URL.createObjectURL(blob);
                  a.download = 'thread-dump-' + new Date().toISOString().slice(0,19) + '.txt';
                  a.click();
                }
                function copyDump() {
                  navigator.clipboard.writeText(document.getElementById('threadDumpOutput').textContent);
                }
                function triggerHeapDump() {
                  document.getElementById('heapDumpResult').innerHTML = '<div class="alert alert-info">Heap dump requested...</div>';
                  fetch(CTX + '/api/execute', {
                    method:'POST', headers:{'Content-Type':'application/json'},
                    body: JSON.stringify({command:'heapdump'})
                  }).then(function(r){return r.json();}).then(function(d) {
                    document.getElementById('heapDumpResult').innerHTML =
                      '<div class="alert ' + (d.success ? 'alert-success' : 'alert-danger') + '">' + d.message + '</div>';
                  });
                }
                </script>
                """.formatted(
                runnable, waiting, timedWaiting,
                blocked > 0 ? "danger" : "", blocked,
                threadInfos.length,
                AdminPageLayout.escapeHtml(dumpBuilder.toString()),
                deadlockedCount == 0
                        ? "<div class=\"alert alert-success\">No deadlocks detected.</div>"
                        : "<div class=\"alert alert-danger\">%d deadlocked threads detected!</div>".formatted(deadlockedCount),
                ctx
        );

        resp.getWriter().write(AdminPageLayout.page("Diagnostics", server.getName(), server.getNodeId(),
                ctx, "diagnostics", body));
    }
}
