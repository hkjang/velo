package io.velo.was.webadmin.servlet.page;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * Diagnostics page.
 * Thread dump (collect, compare, analyze), heap dump trigger, deadlock detection,
 * slow request tracking, and JVM profiling.
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
        MemoryMXBean memoryMx = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryMx.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMx.getNonHeapMemoryUsage();
        long heapUsedMb = heap.getUsed() / (1024 * 1024);
        long heapMaxMb = heap.getMax() > 0 ? heap.getMax() / (1024 * 1024) : heap.getCommitted() / (1024 * 1024);
        int heapPct = heapMaxMb > 0 ? (int)(heapUsedMb * 100 / heapMaxMb) : 0;
        long nonHeapUsedMb = nonHeap.getUsed() / (1024 * 1024);
        long nonHeapCommMb = nonHeap.getCommitted() / (1024 * 1024);
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

        // Build deadlock detail info
        StringBuilder deadlockDetail = new StringBuilder();
        if (deadlockedCount > 0) {
            ThreadInfo[] deadlockedInfos = threadMx.getThreadInfo(deadlocked, true, true);
            for (ThreadInfo di : deadlockedInfos) {
                if (di == null) continue;
                deadlockDetail.append("\"").append(di.getThreadName()).append("\"")
                        .append(" #").append(di.getThreadId())
                        .append(" state=").append(di.getThreadState());
                if (di.getLockName() != null) {
                    deadlockDetail.append(" waiting on ").append(di.getLockName());
                }
                if (di.getLockOwnerName() != null) {
                    deadlockDetail.append(" held by \"").append(di.getLockOwnerName())
                            .append("\" #").append(di.getLockOwnerId());
                }
                deadlockDetail.append("\n");
                for (StackTraceElement ste : di.getStackTrace()) {
                    deadlockDetail.append("    at ").append(ste).append("\n");
                }
                deadlockDetail.append("\n");
            }
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

        String deadlockSection;
        if (deadlockedCount == 0) {
            deadlockSection = """
                    <div class="alert alert-success">No deadlocks detected.</div>
                    """;
        } else {
            deadlockSection = """
                    <div class="alert alert-danger" style="margin-bottom:16px;">
                      <strong>%d deadlocked threads detected!</strong>
                    </div>
                    <div style="margin-bottom:16px;">
                      <div class="card-title" style="margin-bottom:8px;">Thread Chain Details</div>
                      <pre id="deadlockChainPre" style="font-family:'JetBrains Mono',monospace;font-size:11px;
                           background:var(--bg);padding:16px;border-radius:8px;max-height:400px;
                           overflow:auto;line-height:1.6;white-space:pre-wrap;color:var(--danger);">%s</pre>
                    </div>
                    """.formatted(deadlockedCount, AdminPageLayout.escapeHtml(deadlockDetail.toString()));
        }

        String body = """
                <div class="page-header">
                  <div>
                    <div class="page-title" data-i18n="page.diagnostics">Diagnostics</div>
                    <div class="page-subtitle">Thread dumps, heap analysis, deadlock detection, and profiling</div>
                  </div>
                </div>

                <div class="tabs" id="diagTabs">
                  <div class="tab active" data-tab="threadDump">Thread Dump</div>
                  <div class="tab" data-tab="heapDump">Heap Dump</div>
                  <div class="tab" data-tab="deadlock">Deadlock</div>
                  <div class="tab" data-tab="slow">Slow Requests <span id="slowBadge" class="status-badge" style="display:none;background:var(--danger-bg);color:var(--danger);font-size:11px;padding:1px 6px;margin-left:4px;">0</span></div>
                  <div class="tab" data-tab="profiling">Profiling</div>
                </div>

                <!-- Thread Dump Tab -->
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
                        <button class="btn btn-sm" onclick="saveDumpToStorage()">Save Current Dump</button>
                        <button class="btn btn-sm" onclick="showCompareView()">Compare Dumps</button>
                        <button class="btn btn-sm" onclick="downloadDump()">Download</button>
                        <button class="btn btn-sm" onclick="copyDump()">Copy</button>
                      </div>
                    </div>
                    <pre id="threadDumpOutput" style="font-family:'JetBrains Mono',monospace;font-size:11px;
                         background:var(--bg);padding:16px;border-radius:8px;max-height:500px;
                         overflow:auto;line-height:1.6;white-space:pre-wrap;">%s</pre>
                  </div>

                  <!-- Saved Dumps List -->
                  <div class="card" style="margin-top:16px;">
                    <div class="card-header">
                      <div class="card-title">Saved Dumps</div>
                      <button class="btn btn-sm btn-danger" onclick="clearSavedDumps()">Clear All</button>
                    </div>
                    <div id="savedDumpsList" style="padding:8px 0;">
                      <div class="alert alert-info">No saved dumps yet. Click "Save Current Dump" to save one.</div>
                    </div>
                  </div>

                  <!-- Comparison View (hidden by default) -->
                  <div id="compareView" style="display:none;margin-top:16px;">
                    <div class="card">
                      <div class="card-header">
                        <div class="card-title">Dump Comparison</div>
                        <button class="btn btn-sm" onclick="document.getElementById('compareView').style.display='none';">Close</button>
                      </div>
                      <div style="display:flex;gap:12px;margin-bottom:12px;padding:12px;">
                        <div style="flex:1;">
                          <label style="font-size:13px;color:var(--text2);display:block;margin-bottom:4px;">Dump A</label>
                          <select id="compareDumpA" class="form-input" style="width:100%%;"></select>
                        </div>
                        <div style="flex:1;">
                          <label style="font-size:13px;color:var(--text2);display:block;margin-bottom:4px;">Dump B</label>
                          <select id="compareDumpB" class="form-input" style="width:100%%;"></select>
                        </div>
                        <div style="display:flex;align-items:flex-end;">
                          <button class="btn btn-sm btn-primary" onclick="runComparison()">Compare</button>
                        </div>
                      </div>
                      <div id="comparisonResult" style="padding:0 12px 12px;"></div>
                    </div>
                  </div>
                </div>

                <!-- Heap Dump Tab -->
                <div class="tab-panel" id="tab-heapDump">
                  <div class="grid grid-3" style="margin-bottom:20px;">
                    <div class="card">
                      <div class="card-title">Heap Used</div>
                      <div class="metric-value" style="font-size:28px;color:var(--primary);">%d MB</div>
                      <div style="font-size:12px;color:var(--text2);margin-top:4px;">of %d MB max</div>
                    </div>
                    <div class="card">
                      <div class="card-title">Heap Utilization</div>
                      <div class="metric-value %s" style="font-size:28px;">%d%%%%</div>
                      <div style="background:var(--surface2);border-radius:4px;height:8px;margin-top:8px;">
                        <div style="background:%s;border-radius:4px;height:8px;width:%d%%%%;transition:width 0.3s;"></div>
                      </div>
                    </div>
                    <div class="card">
                      <div class="card-title">Non-Heap Used</div>
                      <div class="metric-value" style="font-size:28px;color:var(--info);">%d MB</div>
                      <div style="font-size:12px;color:var(--text2);margin-top:4px;">Committed: %d MB</div>
                    </div>
                  </div>
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Heap Dump Generation</div>
                    </div>
                    <p style="color:var(--text2);font-size:14px;margin:12px 0;">
                      Generate a heap dump (HPROF format) for offline analysis with tools like Eclipse MAT, VisualVM, or JProfiler.
                    </p>
                    <div class="alert alert-warning">Heap dump may cause a brief GC pause and takes disk space proportional to heap size. Use with caution in production.</div>
                    <div style="display:flex;align-items:center;gap:12px;margin-top:16px;">
                      <label style="font-size:13px;color:var(--text2);">
                        <input type="checkbox" id="heapDumpLive" checked> Live objects only (recommended)
                      </label>
                    </div>
                    <div class="btn-group" style="margin-top:16px;">
                      <button class="btn btn-danger" onclick="if(confirm('Generate heap dump? This may cause a brief pause.'))triggerHeapDump()">
                        Generate Heap Dump
                      </button>
                      <button class="btn" onclick="requestGC()">Request GC First</button>
                    </div>
                    <div id="heapDumpResult" style="margin-top:16px;"></div>
                  </div>
                  <div class="card" style="margin-top:16px;">
                    <div class="card-header">
                      <div class="card-title">Heap Dump History</div>
                    </div>
                    <div id="heapDumpHistory">
                      <div class="alert alert-info">No heap dumps have been generated in this session.</div>
                    </div>
                  </div>
                </div>

                <!-- Deadlock Tab -->
                <div class="tab-panel" id="tab-deadlock">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Deadlock Detection</div>
                      <div class="btn-group">
                        <button class="btn btn-sm" onclick="checkDeadlocks()">Check Now</button>
                        <button class="btn btn-sm" id="deadlockAutoBtn" onclick="toggleDeadlockAutoCheck()">Auto-check: OFF</button>
                      </div>
                    </div>
                    %s
                    <div id="deadlockLiveResult"></div>
                  </div>
                </div>

                <!-- Slow Requests Tab -->
                <div class="tab-panel" id="tab-slow">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Slow Requests</div>
                      <div style="display:flex;align-items:center;gap:8px;">
                        <span style="font-size:13px;color:var(--text2);">Threshold:</span>
                        <input class="form-input" type="number" value="3000" style="width:80px;" id="slowThreshold" onchange="filterSlowRequests()">
                        <span style="font-size:13px;color:var(--text2);">ms</span>
                        <button class="btn btn-sm" onclick="clearSlowRequests()">Clear</button>
                      </div>
                    </div>
                    <div id="slowRequestsContent">
                      <div class="alert alert-info">Loading slow request data...</div>
                    </div>
                  </div>
                </div>

                <!-- Profiling Tab -->
                <div class="tab-panel" id="tab-profiling">
                  <div class="grid grid-2" style="margin-bottom:20px;">
                    <div class="card">
                      <div class="card-header">
                        <div class="card-title">GC Statistics</div>
                        <button class="btn btn-sm" onclick="refreshProfiling()">Refresh</button>
                      </div>
                      <div id="gcStatsContent">
                        <div class="alert alert-info">Loading GC statistics...</div>
                      </div>
                    </div>
                    <div class="card">
                      <div class="card-header">
                        <div class="card-title">Memory Pools</div>
                      </div>
                      <div id="memoryPoolsContent">
                        <div class="alert alert-info">Loading memory pool data...</div>
                      </div>
                    </div>
                  </div>
                  <div class="grid grid-2">
                    <div class="card">
                      <div class="card-header">
                        <div class="card-title">Class Loading</div>
                      </div>
                      <div id="classLoadingContent">
                        <div class="alert alert-info">Loading class info...</div>
                      </div>
                    </div>
                    <div class="card">
                      <div class="card-header">
                        <div class="card-title">JIT Compilation</div>
                      </div>
                      <div id="compilationContent">
                        <div class="alert alert-info">Loading compilation info...</div>
                      </div>
                    </div>
                  </div>
                </div>

                <script>
                var CTX = '%s';
                var slowRequests = [];
                var slowRefreshTimer = null;
                var deadlockAutoTimer = null;

                /* ========== Tab Switching ========== */
                document.querySelectorAll('#diagTabs .tab').forEach(function(tab){
                  tab.addEventListener('click', function(){
                    document.querySelectorAll('#diagTabs .tab').forEach(function(t){t.classList.remove('active');});
                    document.querySelectorAll('.tab-panel').forEach(function(p){p.classList.remove('active');});
                    tab.classList.add('active');
                    document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
                    if (tab.dataset.tab === 'slow') startSlowRefresh();
                    else stopSlowRefresh();
                    if (tab.dataset.tab === 'profiling') refreshProfiling();
                  });
                });

                /* ========== Thread Dump ========== */
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

                /* ========== Thread Dump Comparison ========== */
                function getSavedDumps() {
                  try {
                    var data = localStorage.getItem('velo-thread-dumps');
                    return data ? JSON.parse(data) : [];
                  } catch(e) { return []; }
                }
                function setSavedDumps(dumps) {
                  localStorage.setItem('velo-thread-dumps', JSON.stringify(dumps));
                }
                function parseDumpToThreads(text) {
                  var threads = {};
                  var parts = text.split('\\n\\n');
                  parts.forEach(function(part) {
                    var trimmed = part.trim();
                    if (!trimmed) return;
                    var match = trimmed.match(/^"([^"]+)".*state=([A-Z_]+)/);
                    if (match) {
                      threads[match[1]] = { name: match[1], state: match[2], text: trimmed };
                    }
                  });
                  return threads;
                }
                function saveDumpToStorage() {
                  var dumpText = document.getElementById('threadDumpOutput').textContent;
                  if (!dumpText || dumpText === 'No thread data available.' || dumpText === 'Failed to fetch thread data.') {
                    alert('No valid dump to save.');
                    return;
                  }
                  var dumps = getSavedDumps();
                  var threads = parseDumpToThreads(dumpText);
                  var threadCount = Object.keys(threads).length;
                  dumps.unshift({
                    timestamp: new Date().toISOString(),
                    threadCount: threadCount,
                    text: dumpText
                  });
                  if (dumps.length > 5) dumps = dumps.slice(0, 5);
                  setSavedDumps(dumps);
                  renderSavedDumps();
                }
                function renderSavedDumps() {
                  var dumps = getSavedDumps();
                  var container = document.getElementById('savedDumpsList');
                  if (dumps.length === 0) {
                    container.innerHTML = '<div class="alert alert-info">No saved dumps yet. Click "Save Current Dump" to save one.</div>';
                    return;
                  }
                  var html = '<table style="width:100%%;border-collapse:collapse;">';
                  html += '<thead><tr style="border-bottom:1px solid var(--border);">';
                  html += '<th style="text-align:left;padding:8px 12px;font-size:13px;color:var(--text2);">#</th>';
                  html += '<th style="text-align:left;padding:8px 12px;font-size:13px;color:var(--text2);">Timestamp</th>';
                  html += '<th style="text-align:left;padding:8px 12px;font-size:13px;color:var(--text2);">Threads</th>';
                  html += '<th style="text-align:right;padding:8px 12px;font-size:13px;color:var(--text2);">Actions</th>';
                  html += '</tr></thead><tbody>';
                  dumps.forEach(function(d, i) {
                    html += '<tr style="border-bottom:1px solid var(--border);">';
                    html += '<td style="padding:8px 12px;font-size:13px;">' + (i + 1) + '</td>';
                    html += '<td style="padding:8px 12px;font-size:13px;">' + new Date(d.timestamp).toLocaleString() + '</td>';
                    html += '<td style="padding:8px 12px;font-size:13px;">' + d.threadCount + '</td>';
                    html += '<td style="padding:8px 12px;text-align:right;">';
                    html += '<button class="btn btn-sm" onclick="viewSavedDump(' + i + ')">View</button> ';
                    html += '<button class="btn btn-sm btn-danger" onclick="deleteSavedDump(' + i + ')">Delete</button>';
                    html += '</td></tr>';
                  });
                  html += '</tbody></table>';
                  container.innerHTML = html;
                }
                function viewSavedDump(idx) {
                  var dumps = getSavedDumps();
                  if (dumps[idx]) {
                    document.getElementById('threadDumpOutput').textContent = dumps[idx].text;
                  }
                }
                function deleteSavedDump(idx) {
                  var dumps = getSavedDumps();
                  dumps.splice(idx, 1);
                  setSavedDumps(dumps);
                  renderSavedDumps();
                }
                function clearSavedDumps() {
                  if (confirm('Delete all saved dumps?')) {
                    setSavedDumps([]);
                    renderSavedDumps();
                  }
                }
                function showCompareView() {
                  var dumps = getSavedDumps();
                  if (dumps.length < 2) {
                    alert('Need at least 2 saved dumps to compare. Save more dumps first.');
                    return;
                  }
                  var selA = document.getElementById('compareDumpA');
                  var selB = document.getElementById('compareDumpB');
                  selA.innerHTML = '';
                  selB.innerHTML = '';
                  dumps.forEach(function(d, i) {
                    var label = '#' + (i + 1) + ' - ' + new Date(d.timestamp).toLocaleString() + ' (' + d.threadCount + ' threads)';
                    selA.innerHTML += '<option value="' + i + '">' + label + '</option>';
                    selB.innerHTML += '<option value="' + i + '">' + label + '</option>';
                  });
                  if (dumps.length >= 2) selB.value = '1';
                  document.getElementById('compareView').style.display = 'block';
                  document.getElementById('comparisonResult').innerHTML = '';
                }
                function runComparison() {
                  var dumps = getSavedDumps();
                  var idxA = parseInt(document.getElementById('compareDumpA').value);
                  var idxB = parseInt(document.getElementById('compareDumpB').value);
                  if (idxA === idxB) { alert('Select two different dumps.'); return; }
                  var threadsA = parseDumpToThreads(dumps[idxA].text);
                  var threadsB = parseDumpToThreads(dumps[idxB].text);
                  var allNames = {};
                  Object.keys(threadsA).forEach(function(n){ allNames[n] = true; });
                  Object.keys(threadsB).forEach(function(n){ allNames[n] = true; });
                  var appeared = [], disappeared = [], changed = [], unchanged = 0;
                  Object.keys(allNames).sort().forEach(function(name) {
                    var inA = threadsA[name];
                    var inB = threadsB[name];
                    if (!inA && inB) {
                      appeared.push({ name: name, state: inB.state });
                    } else if (inA && !inB) {
                      disappeared.push({ name: name, state: inA.state });
                    } else if (inA.state !== inB.state) {
                      changed.push({ name: name, fromState: inA.state, toState: inB.state });
                    } else {
                      unchanged++;
                    }
                  });
                  var html = '<div style="display:flex;gap:16px;margin-bottom:16px;flex-wrap:wrap;">';
                  html += '<div class="status-badge" style="background:var(--success-bg);color:var(--success);">+ ' + appeared.length + ' appeared</div>';
                  html += '<div class="status-badge" style="background:var(--danger-bg);color:var(--danger);">- ' + disappeared.length + ' disappeared</div>';
                  html += '<div class="status-badge" style="background:var(--warning-bg);color:var(--warning);">~ ' + changed.length + ' changed</div>';
                  html += '<div class="status-badge" style="background:var(--info-bg);color:var(--info);">= ' + unchanged + ' unchanged</div>';
                  html += '</div>';
                  if (appeared.length + disappeared.length + changed.length === 0) {
                    html += '<div class="alert alert-success">No differences found between the two dumps.</div>';
                  } else {
                    html += '<table style="width:100%%;border-collapse:collapse;">';
                    html += '<thead><tr style="border-bottom:1px solid var(--border);">';
                    html += '<th style="text-align:left;padding:8px 12px;font-size:13px;color:var(--text2);">Change</th>';
                    html += '<th style="text-align:left;padding:8px 12px;font-size:13px;color:var(--text2);">Thread Name</th>';
                    html += '<th style="text-align:left;padding:8px 12px;font-size:13px;color:var(--text2);">State (Dump A)</th>';
                    html += '<th style="text-align:left;padding:8px 12px;font-size:13px;color:var(--text2);">State (Dump B)</th>';
                    html += '</tr></thead><tbody>';
                    function stateStyle(state) {
                      if (state === 'BLOCKED') return 'color:var(--danger);font-weight:600;';
                      if (state === 'RUNNABLE') return 'color:var(--success);';
                      if (state === 'WAITING') return 'color:var(--info);';
                      if (state === 'TIMED_WAITING') return 'color:var(--warning);';
                      return '';
                    }
                    appeared.forEach(function(t) {
                      html += '<tr style="border-bottom:1px solid var(--border);background:rgba(34,197,94,0.05);">';
                      html += '<td style="padding:8px 12px;font-size:13px;color:var(--success);font-weight:600;">+ Appeared</td>';
                      html += '<td style="padding:8px 12px;font-size:13px;">' + t.name + '</td>';
                      html += '<td style="padding:8px 12px;font-size:13px;color:var(--text3);">-</td>';
                      html += '<td style="padding:8px 12px;font-size:13px;' + stateStyle(t.state) + '">' + t.state + '</td>';
                      html += '</tr>';
                    });
                    disappeared.forEach(function(t) {
                      html += '<tr style="border-bottom:1px solid var(--border);background:rgba(239,68,68,0.05);">';
                      html += '<td style="padding:8px 12px;font-size:13px;color:var(--danger);font-weight:600;">- Disappeared</td>';
                      html += '<td style="padding:8px 12px;font-size:13px;">' + t.name + '</td>';
                      html += '<td style="padding:8px 12px;font-size:13px;' + stateStyle(t.state) + '">' + t.state + '</td>';
                      html += '<td style="padding:8px 12px;font-size:13px;color:var(--text3);">-</td>';
                      html += '</tr>';
                    });
                    changed.forEach(function(t) {
                      html += '<tr style="border-bottom:1px solid var(--border);background:rgba(245,158,11,0.05);">';
                      html += '<td style="padding:8px 12px;font-size:13px;color:var(--warning);font-weight:600;">~ Changed</td>';
                      html += '<td style="padding:8px 12px;font-size:13px;">' + t.name + '</td>';
                      html += '<td style="padding:8px 12px;font-size:13px;' + stateStyle(t.fromState) + '">' + t.fromState + '</td>';
                      html += '<td style="padding:8px 12px;font-size:13px;' + stateStyle(t.toState) + '">' + t.toState + '</td>';
                      html += '</tr>';
                    });
                    html += '</tbody></table>';
                  }
                  document.getElementById('comparisonResult').innerHTML = html;
                }
                renderSavedDumps();

                /* ========== Heap Dump ========== */
                var heapDumpHistory = JSON.parse(localStorage.getItem('velo-heap-dumps') || '[]');
                function triggerHeapDump() {
                  var liveOnly = document.getElementById('heapDumpLive').checked;
                  var cmd = liveOnly ? 'heapdump --live' : 'heapdump';
                  document.getElementById('heapDumpResult').innerHTML = '<div class="alert alert-info">Generating heap dump... This may take a moment.</div>';
                  var startTime = Date.now();
                  fetch(CTX + '/api/execute', {
                    method:'POST', headers:{'Content-Type':'application/json'},
                    body: JSON.stringify({command: cmd})
                  }).then(function(r){return r.json();}).then(function(d) {
                    var elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
                    document.getElementById('heapDumpResult').innerHTML =
                      '<div class="alert ' + (d.success ? 'alert-success' : 'alert-danger') + '">' + d.message + ' (took ' + elapsed + 's)</div>';
                    if (d.success) {
                      heapDumpHistory.unshift({
                        timestamp: new Date().toISOString(),
                        file: d.file || d.path || 'heap-dump-' + Date.now() + '.hprof',
                        liveOnly: liveOnly,
                        duration: elapsed + 's'
                      });
                      if (heapDumpHistory.length > 10) heapDumpHistory = heapDumpHistory.slice(0, 10);
                      localStorage.setItem('velo-heap-dumps', JSON.stringify(heapDumpHistory));
                      renderHeapDumpHistory();
                    }
                  }).catch(function() {
                    document.getElementById('heapDumpResult').innerHTML = '<div class="alert alert-danger">Failed to generate heap dump.</div>';
                  });
                }
                function requestGC() {
                  if (!confirm('Request garbage collection before heap dump?')) return;
                  fetch(CTX + '/api/execute', {
                    method:'POST', headers:{'Content-Type':'application/json'},
                    body: JSON.stringify({command:'gc'})
                  }).then(function(r){return r.json();}).then(function(d) {
                    showToast(d.message || 'GC requested', d.success ? 'success' : 'error');
                  });
                }
                function renderHeapDumpHistory() {
                  var container = document.getElementById('heapDumpHistory');
                  if (heapDumpHistory.length === 0) {
                    container.innerHTML = '<div class="alert alert-info">No heap dumps have been generated in this session.</div>';
                    return;
                  }
                  var html = '<table style="width:100%%;border-collapse:collapse;">';
                  html += '<thead><tr style="border-bottom:1px solid var(--border);">';
                  html += '<th style="text-align:left;padding:6px 12px;font-size:12px;color:var(--text2);">Timestamp</th>';
                  html += '<th style="text-align:left;padding:6px 12px;font-size:12px;color:var(--text2);">File</th>';
                  html += '<th style="text-align:left;padding:6px 12px;font-size:12px;color:var(--text2);">Mode</th>';
                  html += '<th style="text-align:right;padding:6px 12px;font-size:12px;color:var(--text2);">Duration</th>';
                  html += '</tr></thead><tbody>';
                  heapDumpHistory.forEach(function(h) {
                    html += '<tr style="border-bottom:1px solid var(--border);">';
                    html += '<td style="padding:6px 12px;font-size:13px;">' + new Date(h.timestamp).toLocaleString() + '</td>';
                    html += '<td style="padding:6px 12px;font-size:13px;font-family:monospace;">' + h.file + '</td>';
                    html += '<td style="padding:6px 12px;font-size:13px;">' + (h.liveOnly ? 'Live objects' : 'Full heap') + '</td>';
                    html += '<td style="padding:6px 12px;font-size:13px;text-align:right;">' + h.duration + '</td>';
                    html += '</tr>';
                  });
                  html += '</tbody></table>';
                  container.innerHTML = html;
                }
                renderHeapDumpHistory();

                /* ========== Deadlock Detection ========== */
                function checkDeadlocks() {
                  var container = document.getElementById('deadlockLiveResult');
                  container.innerHTML = '<div class="alert alert-info">Checking for deadlocks...</div>';
                  fetch(CTX + '/api/execute', {
                    method:'POST', headers:{'Content-Type':'application/json'},
                    body: JSON.stringify({command:'deadlock-check'})
                  }).then(function(r){return r.json();}).then(function(d) {
                    if (d.success && d.message) {
                      if (d.message.toLowerCase().indexOf('no deadlock') >= 0 || d.message.indexOf('0 deadlock') >= 0) {
                        container.innerHTML = '<div class="alert alert-success" style="margin-top:12px;">' + d.message + '</div>';
                      } else {
                        container.innerHTML = '<div class="alert alert-danger" style="margin-top:12px;">' + d.message + '</div>';
                        if (d.details) {
                          container.innerHTML += '<pre style="font-family:monospace;font-size:11px;background:var(--bg);' +
                            'padding:16px;border-radius:8px;max-height:300px;overflow:auto;line-height:1.6;' +
                            'white-space:pre-wrap;color:var(--danger);margin-top:8px;">' + d.details + '</pre>';
                        }
                      }
                    } else {
                      container.innerHTML = '<div class="alert alert-success" style="margin-top:12px;">No deadlocks detected (live check).</div>';
                    }
                  }).catch(function() {
                    container.innerHTML = '<div class="alert alert-warning" style="margin-top:12px;">Failed to check deadlocks via API. Server-side result is shown above.</div>';
                  });
                }
                function toggleDeadlockAutoCheck() {
                  var btn = document.getElementById('deadlockAutoBtn');
                  if (deadlockAutoTimer) {
                    clearInterval(deadlockAutoTimer);
                    deadlockAutoTimer = null;
                    btn.textContent = 'Auto-check: OFF';
                  } else {
                    deadlockAutoTimer = setInterval(checkDeadlocks, 10000);
                    btn.textContent = 'Auto-check: ON';
                    checkDeadlocks();
                  }
                }

                /* ========== Slow Requests ========== */
                function startSlowRefresh() {
                  fetchSlowRequests();
                  if (slowRefreshTimer) clearInterval(slowRefreshTimer);
                  slowRefreshTimer = setInterval(fetchSlowRequests, 5000);
                }
                function stopSlowRefresh() {
                  if (slowRefreshTimer) { clearInterval(slowRefreshTimer); slowRefreshTimer = null; }
                }
                function fetchSlowRequests() {
                  fetch(CTX + '/api/monitoring').then(function(r){return r.json();}).then(function(d) {
                    var threshold = parseInt(document.getElementById('slowThreshold').value) || 3000;
                    var requests = d.requests || d.recentRequests || [];
                    var now = Date.now();
                    requests.forEach(function(req) {
                      var duration = req.duration || req.responseTime || 0;
                      if (duration >= threshold) {
                        var exists = slowRequests.some(function(s) {
                          return s.url === (req.url || req.path || '-') && s.startedAt === (req.startedAt || req.timestamp || '-');
                        });
                        if (!exists) {
                          slowRequests.push({
                            url: req.url || req.path || '-',
                            method: req.method || 'GET',
                            duration: duration,
                            startedAt: req.startedAt || req.timestamp || new Date().toISOString(),
                            status: req.status || req.statusCode || '-'
                          });
                        }
                      }
                    });
                    renderSlowRequests();
                  }).catch(function() {
                    /* silently retry on next interval */
                  });
                }
                function filterSlowRequests() {
                  var threshold = parseInt(document.getElementById('slowThreshold').value) || 3000;
                  slowRequests = slowRequests.filter(function(r) { return r.duration >= threshold; });
                  renderSlowRequests();
                }
                function clearSlowRequests() {
                  slowRequests = [];
                  renderSlowRequests();
                }
                function renderSlowRequests() {
                  var badge = document.getElementById('slowBadge');
                  var container = document.getElementById('slowRequestsContent');
                  if (slowRequests.length === 0) {
                    badge.style.display = 'none';
                    container.innerHTML = '<div class="alert alert-info">No slow requests detected. Requests exceeding the threshold will appear here. Auto-refreshes every 5 seconds.</div>';
                    return;
                  }
                  badge.style.display = 'inline-flex';
                  badge.textContent = slowRequests.length;
                  var html = '<table style="width:100%%;border-collapse:collapse;">';
                  html += '<thead><tr style="border-bottom:1px solid var(--border);">';
                  html += '<th style="text-align:left;padding:8px 12px;font-size:13px;color:var(--text2);">URL</th>';
                  html += '<th style="text-align:left;padding:8px 12px;font-size:13px;color:var(--text2);">Method</th>';
                  html += '<th style="text-align:right;padding:8px 12px;font-size:13px;color:var(--text2);">Duration (ms)</th>';
                  html += '<th style="text-align:left;padding:8px 12px;font-size:13px;color:var(--text2);">Started At</th>';
                  html += '<th style="text-align:left;padding:8px 12px;font-size:13px;color:var(--text2);">Status</th>';
                  html += '</tr></thead><tbody>';
                  slowRequests.sort(function(a,b){ return b.duration - a.duration; }).forEach(function(r) {
                    var durationColor = r.duration >= 10000 ? 'var(--danger)' : r.duration >= 5000 ? 'var(--warning)' : 'var(--text)';
                    html += '<tr style="border-bottom:1px solid var(--border);">';
                    html += '<td style="padding:8px 12px;font-size:13px;font-family:monospace;">' + r.url + '</td>';
                    html += '<td style="padding:8px 12px;font-size:13px;">' + r.method + '</td>';
                    html += '<td style="padding:8px 12px;font-size:13px;text-align:right;color:' + durationColor + ';font-weight:600;">' + r.duration + '</td>';
                    html += '<td style="padding:8px 12px;font-size:13px;">' + r.startedAt + '</td>';
                    html += '<td style="padding:8px 12px;font-size:13px;">' + r.status + '</td>';
                    html += '</tr>';
                  });
                  html += '</tbody></table>';
                  container.innerHTML = html;
                }

                /* ========== Profiling ========== */
                function refreshProfiling() {
                  fetch(CTX + '/api/jvm').then(function(r){return r.json();}).then(function(d) {
                    renderGcStats(d);
                    renderMemoryPools(d);
                    renderClassLoading(d);
                    renderCompilation(d);
                  }).catch(function() {
                    document.getElementById('gcStatsContent').innerHTML = '<div class="alert alert-warning">Failed to fetch JVM data.</div>';
                    document.getElementById('memoryPoolsContent').innerHTML = '<div class="alert alert-warning">Failed to fetch JVM data.</div>';
                    document.getElementById('classLoadingContent').innerHTML = '<div class="alert alert-warning">Failed to fetch JVM data.</div>';
                    document.getElementById('compilationContent').innerHTML = '<div class="alert alert-warning">Failed to fetch JVM data.</div>';
                  });
                }
                function formatBytes(bytes) {
                  if (bytes == null) return '-';
                  var mb = bytes / (1024 * 1024);
                  return mb.toFixed(1) + ' MB';
                }
                function renderGcStats(d) {
                  var gc = d.gc || d.garbageCollectors || {};
                  var collectors = gc.collectors || gc.list || [];
                  var html = '<div class="grid grid-3" style="margin:12px 0;">';
                  html += '<div style="text-align:center;"><div style="font-size:24px;font-weight:600;color:var(--primary);">' + (gc.totalCount || gc.count || '-') + '</div><div style="font-size:12px;color:var(--text2);">Total GC Count</div></div>';
                  html += '<div style="text-align:center;"><div style="font-size:24px;font-weight:600;color:var(--warning);">' + (gc.totalTime || gc.time || '-') + ' ms</div><div style="font-size:12px;color:var(--text2);">Total GC Time</div></div>';
                  html += '<div style="text-align:center;"><div style="font-size:24px;font-weight:600;color:var(--info);">' + (collectors.length || '-') + '</div><div style="font-size:12px;color:var(--text2);">Collectors</div></div>';
                  html += '</div>';
                  if (collectors.length > 0) {
                    html += '<table style="width:100%%;border-collapse:collapse;margin-top:8px;">';
                    html += '<thead><tr style="border-bottom:1px solid var(--border);">';
                    html += '<th style="text-align:left;padding:6px 12px;font-size:12px;color:var(--text2);">Collector</th>';
                    html += '<th style="text-align:right;padding:6px 12px;font-size:12px;color:var(--text2);">Count</th>';
                    html += '<th style="text-align:right;padding:6px 12px;font-size:12px;color:var(--text2);">Time (ms)</th>';
                    html += '</tr></thead><tbody>';
                    collectors.forEach(function(c) {
                      html += '<tr style="border-bottom:1px solid var(--border);">';
                      html += '<td style="padding:6px 12px;font-size:13px;">' + (c.name || '-') + '</td>';
                      html += '<td style="padding:6px 12px;font-size:13px;text-align:right;">' + (c.count != null ? c.count : '-') + '</td>';
                      html += '<td style="padding:6px 12px;font-size:13px;text-align:right;">' + (c.time != null ? c.time : '-') + '</td>';
                      html += '</tr>';
                    });
                    html += '</tbody></table>';
                  }
                  document.getElementById('gcStatsContent').innerHTML = html;
                }
                function renderMemoryPools(d) {
                  var pools = d.memoryPools || d.pools || [];
                  if (!Array.isArray(pools) || pools.length === 0) {
                    document.getElementById('memoryPoolsContent').innerHTML = '<div class="alert alert-info">No memory pool data available.</div>';
                    return;
                  }
                  var html = '<table style="width:100%%;border-collapse:collapse;">';
                  html += '<thead><tr style="border-bottom:1px solid var(--border);">';
                  html += '<th style="text-align:left;padding:6px 12px;font-size:12px;color:var(--text2);">Pool</th>';
                  html += '<th style="text-align:right;padding:6px 12px;font-size:12px;color:var(--text2);">Used</th>';
                  html += '<th style="text-align:right;padding:6px 12px;font-size:12px;color:var(--text2);">Max</th>';
                  html += '<th style="text-align:left;padding:6px 12px;font-size:12px;color:var(--text2);">Usage</th>';
                  html += '</tr></thead><tbody>';
                  pools.forEach(function(p) {
                    var used = p.used || 0;
                    var max = p.max || p.committed || 1;
                    var pct = max > 0 ? Math.round(used * 100 / max) : 0;
                    var barColor = pct > 90 ? 'var(--danger)' : pct > 70 ? 'var(--warning)' : 'var(--success)';
                    html += '<tr style="border-bottom:1px solid var(--border);">';
                    html += '<td style="padding:6px 12px;font-size:13px;">' + (p.name || '-') + '</td>';
                    html += '<td style="padding:6px 12px;font-size:13px;text-align:right;">' + formatBytes(used) + '</td>';
                    html += '<td style="padding:6px 12px;font-size:13px;text-align:right;">' + formatBytes(max) + '</td>';
                    html += '<td style="padding:6px 12px;font-size:13px;width:120px;">';
                    html += '<div style="background:var(--surface2);border-radius:4px;height:8px;width:100px;display:inline-block;vertical-align:middle;">';
                    html += '<div style="background:' + barColor + ';border-radius:4px;height:8px;width:' + pct + 'px;"></div></div>';
                    html += ' <span style="font-size:11px;color:var(--text2);">' + pct + '%%</span>';
                    html += '</td></tr>';
                  });
                  html += '</tbody></table>';
                  document.getElementById('memoryPoolsContent').innerHTML = html;
                }
                function renderClassLoading(d) {
                  var cl = d.classLoading || {};
                  var html = '<div class="grid grid-3" style="margin:12px 0;">';
                  html += '<div style="text-align:center;"><div style="font-size:24px;font-weight:600;color:var(--primary);">' + (cl.loaded || cl.loadedCount || '-') + '</div><div style="font-size:12px;color:var(--text2);">Loaded Classes</div></div>';
                  html += '<div style="text-align:center;"><div style="font-size:24px;font-weight:600;color:var(--danger);">' + (cl.unloaded || cl.unloadedCount || '-') + '</div><div style="font-size:12px;color:var(--text2);">Unloaded Classes</div></div>';
                  html += '<div style="text-align:center;"><div style="font-size:24px;font-weight:600;color:var(--info);">' + (cl.totalLoaded || cl.totalLoadedCount || '-') + '</div><div style="font-size:12px;color:var(--text2);">Total Loaded</div></div>';
                  html += '</div>';
                  document.getElementById('classLoadingContent').innerHTML = html;
                }
                function renderCompilation(d) {
                  var comp = d.compilation || {};
                  var html = '<div style="margin:12px 0;text-align:center;">';
                  html += '<div style="font-size:24px;font-weight:600;color:var(--primary);">' + (comp.totalCompileTime || comp.time || '-') + ' ms</div>';
                  html += '<div style="font-size:12px;color:var(--text2);">Total Compilation Time</div>';
                  if (comp.name) {
                    html += '<div style="margin-top:8px;font-size:13px;color:var(--text2);">Compiler: ' + comp.name + '</div>';
                  }
                  html += '</div>';
                  document.getElementById('compilationContent').innerHTML = html;
                }
                </script>
                """.formatted(
                runnable, waiting, timedWaiting,
                blocked > 0 ? "danger" : "", blocked,
                threadInfos.length,
                AdminPageLayout.escapeHtml(dumpBuilder.toString()),
                // Heap dump tab params
                heapUsedMb, heapMaxMb,
                heapPct > 80 ? "danger" : "", heapPct,
                heapPct > 80 ? "var(--danger)" : heapPct > 60 ? "var(--warning)" : "var(--success)",
                heapPct,
                nonHeapUsedMb, nonHeapCommMb,
                deadlockSection,
                ctx
        );

        resp.getWriter().write(AdminPageLayout.page("Diagnostics", server.getName(), server.getNodeId(),
                ctx, "diagnostics", body));
    }
}
