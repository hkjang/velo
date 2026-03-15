package io.velo.was.webadmin.servlet.page;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Change history and audit log page.
 * Shows configuration changes, approvals, rollbacks, and full audit trail.
 * Fetches real data from /api/audit and /api/drafts endpoints.
 */
public class HistoryPageServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public HistoryPageServlet(ServerConfiguration configuration) {
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
                    <div class="page-title">History</div>
                    <div class="page-subtitle">Change history, audit log, and configuration rollback</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn btn-sm" onclick="exportAuditLog()">Export Audit Log</button>
                    <button class="btn btn-sm" onclick="refreshAll()">Refresh</button>
                  </div>
                </div>

                <div class="tabs" id="histTabs">
                  <div class="tab active" data-tab="changes">Changes</div>
                  <div class="tab" data-tab="audit">Audit Log</div>
                  <div class="tab" data-tab="drafts">Drafts</div>
                </div>

                <div class="tab-panel active" id="tab-changes">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Configuration Change History</div>
                      <div class="btn-group">
                        <input class="form-input" type="text" placeholder="Search changes..." style="width:200px;"
                               id="changeSearch" oninput="filterChanges()">
                        <select class="form-select" style="width:120px;" id="changeFilter" onchange="filterChanges()">
                          <option value="">All Types</option><option>Server</option><option>Application</option>
                          <option>Resource</option><option>Security</option>
                        </select>
                      </div>
                    </div>
                    <table class="data-table">
                      <thead>
                        <tr><th>Time</th><th>User</th><th>Target</th><th>Action</th><th>Status</th><th>Detail</th></tr>
                      </thead>
                      <tbody id="changesTbody">
                        <tr><td colspan="6" style="text-align:center;color:var(--text2);">Loading...</td></tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-audit">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Audit Trail <span id="auditTotal" style="color:var(--text3);font-size:13px;"></span></div>
                      <div class="btn-group">
                        <select class="form-select" style="width:140px;" id="auditFilter" onchange="loadAudit()">
                          <option value="">All Actions</option>
                          <option value="LOGIN">Login</option>
                          <option value="LOGOUT">Logout</option>
                          <option value="EXECUTE_COMMAND">Commands</option>
                          <option value="CREATE_DRAFT">Draft Create</option>
                          <option value="APPLY_DRAFT">Draft Apply</option>
                          <option value="ROLLBACK_DRAFT">Rollback</option>
                        </select>
                      </div>
                    </div>
                    <table class="data-table">
                      <thead>
                        <tr><th>Timestamp</th><th>User</th><th>Action</th><th>Resource</th><th>Detail</th><th>Result</th><th>IP</th></tr>
                      </thead>
                      <tbody id="auditTbody">
                        <tr><td colspan="7" style="text-align:center;color:var(--text2);">Loading...</td></tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-drafts">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Draft Changes</div>
                      <div class="btn-group">
                        <select class="form-select" style="width:140px;" id="draftFilter" onchange="loadDrafts()">
                          <option value="">All</option>
                          <option value="DRAFT">Draft</option>
                          <option value="VALIDATED">Validated</option>
                          <option value="REVIEWED">Reviewed</option>
                          <option value="APPROVED">Approved</option>
                          <option value="APPLIED">Applied</option>
                          <option value="REJECTED">Rejected</option>
                          <option value="ROLLED_BACK">Rolled Back</option>
                        </select>
                      </div>
                    </div>
                    <p style="color:var(--text2);font-size:14px;margin:12px 0 8px;">
                      Workflow: <strong>Draft</strong> &rarr; <strong>Validate</strong> &rarr;
                      <strong>Review</strong> &rarr; <strong>Approve</strong> &rarr; <strong>Apply</strong>
                    </p>
                    <table class="data-table" id="draftsTable" style="display:none;">
                      <thead>
                        <tr><th>ID</th><th>Author</th><th>Target</th><th>Description</th><th>Status</th><th>Actions</th></tr>
                      </thead>
                      <tbody id="draftsTbody"></tbody>
                    </table>
                    <div id="draftsEmpty" class="alert alert-info">No drafts found.</div>
                  </div>
                </div>

                <script>
                var CTX = '%s';
                var allAuditEvents = [];

                document.querySelectorAll('#histTabs .tab').forEach(function(tab){
                  tab.addEventListener('click', function(){
                    document.querySelectorAll('#histTabs .tab').forEach(function(t){t.classList.remove('active');});
                    document.querySelectorAll('.tab-panel').forEach(function(p){p.classList.remove('active');});
                    tab.classList.add('active');
                    document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
                  });
                });

                function refreshAll() { loadAudit(); loadDrafts(); }

                function loadAudit() {
                  var filter = document.getElementById('auditFilter').value;
                  var url = CTX + '/api/audit?limit=200';
                  if (filter) url += '&action=' + filter;
                  fetch(url).then(function(r){return r.json();}).then(function(d) {
                    allAuditEvents = d.events || [];
                    document.getElementById('auditTotal').textContent = '(' + d.total + ' total)';
                    renderAudit(allAuditEvents);
                    renderChanges(allAuditEvents);
                  }).catch(function(){
                    document.getElementById('auditTbody').innerHTML =
                      '<tr><td colspan="7" style="text-align:center;color:var(--text3);">Failed to load</td></tr>';
                  });
                }

                function renderAudit(events) {
                  var tb = document.getElementById('auditTbody');
                  if (!events.length) {
                    tb.innerHTML = '<tr><td colspan="7" style="text-align:center;color:var(--text3);">No audit events recorded yet.</td></tr>';
                    return;
                  }
                  var html = '';
                  events.forEach(function(e) {
                    var badge = e.success ? 'badge-success' : 'badge-danger';
                    var result = e.success ? 'Success' : 'Failed';
                    html += '<tr><td style="white-space:nowrap;">' + fmtTime(e.timestamp) + '</td>'
                      + '<td>' + esc(e.user) + '</td>'
                      + '<td><span class="badge badge-info">' + esc(e.action) + '</span></td>'
                      + '<td>' + esc(e.resource) + '</td>'
                      + '<td style="max-width:300px;overflow:hidden;text-overflow:ellipsis;">' + esc(e.detail) + '</td>'
                      + '<td><span class="badge ' + badge + '">' + result + '</span></td>'
                      + '<td>' + esc(e.sourceIp || '-') + '</td></tr>';
                  });
                  tb.innerHTML = html;
                }

                function renderChanges(events) {
                  var changes = events.filter(function(e) {
                    return e.action && (e.action.indexOf('DRAFT') >= 0 || e.action === 'EXECUTE_COMMAND');
                  });
                  var tb = document.getElementById('changesTbody');
                  if (!changes.length) {
                    tb.innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--text3);">No configuration changes recorded yet.</td></tr>';
                    return;
                  }
                  var html = '';
                  changes.forEach(function(e) {
                    var badge = e.success ? 'badge-success' : 'badge-danger';
                    html += '<tr><td style="white-space:nowrap;">' + fmtTime(e.timestamp) + '</td>'
                      + '<td>' + esc(e.user) + '</td>'
                      + '<td>' + esc(e.resource) + '</td>'
                      + '<td>' + esc(e.action) + '</td>'
                      + '<td><span class="badge ' + badge + '">' + (e.success ? 'Success' : 'Failed') + '</span></td>'
                      + '<td style="max-width:200px;overflow:hidden;text-overflow:ellipsis;">' + esc(e.detail) + '</td></tr>';
                  });
                  tb.innerHTML = html;
                }

                function filterChanges() {
                  var q = (document.getElementById('changeSearch').value || '').toLowerCase();
                  var cat = (document.getElementById('changeFilter').value || '').toLowerCase();
                  var rows = document.querySelectorAll('#changesTbody tr');
                  rows.forEach(function(row) {
                    var text = row.textContent.toLowerCase();
                    row.style.display = (text.indexOf(q) >= 0 && (!cat || text.indexOf(cat) >= 0)) ? '' : 'none';
                  });
                }

                function loadDrafts() {
                  var filter = document.getElementById('draftFilter').value;
                  var url = CTX + '/api/drafts';
                  if (filter) url += '?status=' + filter;
                  fetch(url).then(function(r){return r.json();}).then(function(d) {
                    var drafts = d.drafts || [];
                    var tb = document.getElementById('draftsTbody');
                    var tbl = document.getElementById('draftsTable');
                    var empty = document.getElementById('draftsEmpty');
                    if (!drafts.length) {
                      tbl.style.display = 'none'; empty.style.display = '';
                      return;
                    }
                    tbl.style.display = ''; empty.style.display = 'none';
                    var html = '';
                    drafts.forEach(function(d) {
                      html += '<tr><td><code>' + esc(d.id) + '</code></td>'
                        + '<td>' + esc(d.author) + '</td>'
                        + '<td>' + esc(d.target) + '</td>'
                        + '<td>' + esc(d.description) + '</td>'
                        + '<td><span class="badge ' + draftBadge(d.status) + '">' + esc(d.status) + '</span></td>'
                        + '<td>' + draftActions(d) + '</td></tr>';
                    });
                    tb.innerHTML = html;
                  }).catch(function(){});
                }

                function draftBadge(s) {
                  switch(s) {
                    case 'DRAFT': return 'badge-neutral';
                    case 'VALIDATED': return 'badge-info';
                    case 'REVIEWED': return 'badge-warning';
                    case 'APPROVED': return 'badge-success';
                    case 'APPLIED': return 'badge-success';
                    case 'REJECTED': return 'badge-danger';
                    case 'ROLLED_BACK': return 'badge-danger';
                    default: return '';
                  }
                }

                function draftActions(d) {
                  var btns = '';
                  switch(d.status) {
                    case 'DRAFT': btns = '<button class="btn btn-sm" onclick="draftAction(\\''+d.id+'\\',\\'validate\\')">Validate</button>'; break;
                    case 'VALIDATED': btns = '<button class="btn btn-sm" onclick="draftAction(\\''+d.id+'\\',\\'review\\')">Review</button>'; break;
                    case 'REVIEWED': btns = '<button class="btn btn-sm btn-primary" onclick="draftAction(\\''+d.id+'\\',\\'approve\\')">Approve</button>'; break;
                    case 'APPROVED': btns = '<button class="btn btn-sm btn-primary" onclick="draftAction(\\''+d.id+'\\',\\'apply\\')">Apply</button>'; break;
                    case 'APPLIED': btns = '<button class="btn btn-sm btn-danger" onclick="draftAction(\\''+d.id+'\\',\\'rollback\\')">Rollback</button>'; break;
                  }
                  if (d.status !== 'APPLIED' && d.status !== 'REJECTED' && d.status !== 'ROLLED_BACK') {
                    btns += ' <button class="btn btn-sm" onclick="draftAction(\\''+d.id+'\\',\\'reject\\')">Reject</button>';
                  }
                  return '<div class="btn-group">' + btns + '</div>';
                }

                function draftAction(id, action) {
                  fetch(CTX + '/api/drafts/' + id + '/action', {
                    method: 'POST', headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({action: action})
                  }).then(function(r){return r.json();}).then(function(d) {
                    loadDrafts();
                    loadAudit();
                  });
                }

                function exportAuditLog() {
                  fetch(CTX + '/api/audit?limit=10000').then(function(r){return r.json();}).then(function(d) {
                    var csv = 'Timestamp,User,Action,Resource,Detail,Success,IP\\n';
                    (d.events || []).forEach(function(e) {
                      csv += '"' + e.timestamp + '","' + e.user + '","' + e.action + '","'
                        + e.resource + '","' + (e.detail||'').replace(/"/g,'""') + '",'
                        + e.success + ',"' + (e.sourceIp||'') + '"\\n';
                    });
                    var blob = new Blob([csv], {type:'text/csv'});
                    var a = document.createElement('a');
                    a.href = URL.createObjectURL(blob);
                    a.download = 'audit-log-' + new Date().toISOString().slice(0,10) + '.csv';
                    a.click();
                  });
                }

                function fmtTime(t) {
                  if (!t) return '-';
                  try { return new Date(t).toLocaleString(); } catch(e) { return t; }
                }
                function esc(s) { if(!s) return ''; var d=document.createElement('div'); d.textContent=s; return d.innerHTML; }

                loadAudit();
                loadDrafts();
                </script>
                """.formatted(ctx);

        resp.getWriter().write(AdminPageLayout.page("History", server.getName(), server.getNodeId(),
                ctx, "history", body));
    }
}
