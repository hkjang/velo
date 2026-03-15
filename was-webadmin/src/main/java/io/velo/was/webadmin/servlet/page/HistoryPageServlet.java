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
                    <div class="page-title" data-i18n="page.history">History</div>
                    <div class="page-subtitle">Change history, audit log, and configuration rollback</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn btn-sm" onclick="exportAuditLog()">Export Audit Log</button>
                    <button class="btn btn-sm" onclick="exportAllAuditTrail()">Export All</button>
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
                      <div class="btn-group" style="flex-wrap:wrap;gap:6px;">
                        <label style="font-size:12px;color:var(--text2);display:flex;align-items:center;gap:4px;">From
                          <input class="form-input" type="date" id="auditDateFrom" style="width:140px;" onchange="filterAuditDisplay()">
                        </label>
                        <label style="font-size:12px;color:var(--text2);display:flex;align-items:center;gap:4px;">To
                          <input class="form-input" type="date" id="auditDateTo" style="width:140px;" onchange="filterAuditDisplay()">
                        </label>
                        <select class="form-select" style="width:130px;" id="auditUserFilter" onchange="filterAuditDisplay()">
                          <option value="">All Users</option>
                        </select>
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
                  <div id="diffViewPanel" style="display:none;margin-top:16px;">
                    <div class="card">
                      <div class="card-header">
                        <div class="card-title">Diff View: <span id="diffDraftId"></span></div>
                        <button class="btn btn-sm" onclick="document.getElementById('diffViewPanel').style.display='none'">Close</button>
                      </div>
                      <div style="display:grid;grid-template-columns:1fr 1fr;gap:0;border:1px solid var(--border);border-radius:8px;overflow:hidden;margin-top:8px;">
                        <div style="border-right:1px solid var(--border);">
                          <div style="background:var(--bg2);padding:8px 12px;font-weight:600;font-size:13px;border-bottom:1px solid var(--border);">Old Configuration</div>
                          <pre id="diffOld" style="font-family:'JetBrains Mono',monospace;font-size:11px;padding:12px;margin:0;max-height:400px;overflow:auto;line-height:1.7;white-space:pre-wrap;"></pre>
                        </div>
                        <div>
                          <div style="background:var(--bg2);padding:8px 12px;font-weight:600;font-size:13px;border-bottom:1px solid var(--border);">New Configuration</div>
                          <pre id="diffNew" style="font-family:'JetBrains Mono',monospace;font-size:11px;padding:12px;margin:0;max-height:400px;overflow:auto;line-height:1.7;white-space:pre-wrap;"></pre>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Rollback Confirmation Modal -->
                <div class="modal-overlay" id="rollbackModal">
                  <div class="modal">
                    <div class="modal-title">Confirm Rollback</div>
                    <div class="alert alert-warning">You are about to rollback this draft. This action will revert the applied configuration changes.</div>
                    <div class="form-group">
                      <label class="form-label">Draft ID</label>
                      <input class="form-input" type="text" id="rollbackDraftId" readonly>
                    </div>
                    <div class="form-group">
                      <label class="form-label">Reason for Rollback</label>
                      <textarea class="form-input" rows="3" id="rollbackReason" placeholder="Describe the reason for this rollback..."></textarea>
                    </div>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('rollbackModal').classList.remove('open')">Cancel</button>
                      <button class="btn btn-danger" onclick="confirmRollback()">Confirm Rollback</button>
                    </div>
                  </div>
                </div>

                <script>
                var CTX = '%s';
                var allAuditEvents = [];
                var allDrafts = [];

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
                    populateUserFilter(allAuditEvents);
                    filterAuditDisplay();
                    renderChanges(allAuditEvents);
                  }).catch(function(){
                    document.getElementById('auditTbody').innerHTML =
                      '<tr><td colspan="7" style="text-align:center;color:var(--text3);">Failed to load</td></tr>';
                  });
                }

                function populateUserFilter(events) {
                  var users = {};
                  events.forEach(function(e) { if (e.user) users[e.user] = true; });
                  var sel = document.getElementById('auditUserFilter');
                  var current = sel.value;
                  sel.innerHTML = '<option value="">All Users</option>';
                  Object.keys(users).sort().forEach(function(u) {
                    var opt = document.createElement('option');
                    opt.value = u; opt.textContent = u;
                    sel.appendChild(opt);
                  });
                  sel.value = current;
                }

                function filterAuditDisplay() {
                  var fromVal = document.getElementById('auditDateFrom').value;
                  var toVal = document.getElementById('auditDateTo').value;
                  var userVal = document.getElementById('auditUserFilter').value;
                  var fromDate = fromVal ? new Date(fromVal + 'T00:00:00') : null;
                  var toDate = toVal ? new Date(toVal + 'T23:59:59') : null;
                  var filtered = allAuditEvents.filter(function(e) {
                    if (fromDate && new Date(e.timestamp) < fromDate) return false;
                    if (toDate && new Date(e.timestamp) > toDate) return false;
                    if (userVal && e.user !== userVal) return false;
                    return true;
                  });
                  renderAudit(filtered);
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
                    allDrafts = d.drafts || [];
                    var tb = document.getElementById('draftsTbody');
                    var tbl = document.getElementById('draftsTable');
                    var empty = document.getElementById('draftsEmpty');
                    if (!allDrafts.length) {
                      tbl.style.display = 'none'; empty.style.display = '';
                      return;
                    }
                    tbl.style.display = ''; empty.style.display = 'none';
                    var html = '';
                    allDrafts.forEach(function(d) {
                      html += '<tr><td><code style="cursor:pointer;text-decoration:underline;" onclick="showDiffView(\\'' + esc(d.id) + '\\')">' + esc(d.id) + '</code></td>'
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
                    case 'APPLIED': btns = '<button class="btn btn-sm btn-danger" onclick="showRollbackModal(\\''+d.id+'\\')">Rollback</button>'; break;
                  }
                  if (d.status !== 'APPLIED' && d.status !== 'REJECTED' && d.status !== 'ROLLED_BACK') {
                    btns += ' <button class="btn btn-sm" onclick="draftAction(\\''+d.id+'\\',\\'reject\\')">Reject</button>';
                  }
                  btns += ' <button class="btn btn-sm" onclick="showDiffView(\\''+d.id+'\\')">Diff</button>';
                  return '<div class="btn-group">' + btns + '</div>';
                }

                function showRollbackModal(draftId) {
                  document.getElementById('rollbackDraftId').value = draftId;
                  document.getElementById('rollbackReason').value = '';
                  document.getElementById('rollbackModal').classList.add('open');
                }

                function confirmRollback() {
                  var draftId = document.getElementById('rollbackDraftId').value;
                  var reason = document.getElementById('rollbackReason').value;
                  if (!reason.trim()) { showToast('Please provide a reason for rollback', 'warning'); return; }
                  fetch(CTX + '/api/drafts/' + draftId + '/action', {
                    method: 'POST', headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({action: 'rollback', reason: reason})
                  }).then(function(r){return r.json();}).then(function(d) {
                    document.getElementById('rollbackModal').classList.remove('open');
                    showToast(d.message || 'Rollback completed', d.success !== false ? 'success' : 'error');
                    loadDrafts();
                    loadAudit();
                  });
                }

                function showDiffView(draftId) {
                  document.getElementById('diffDraftId').textContent = draftId;
                  document.getElementById('diffViewPanel').style.display = '';
                  var draft = allDrafts.find(function(d){ return d.id === draftId; });
                  var oldContent = (draft && draft.oldContent) || 'server:\\n  name: velo-server\\n  nodeId: node-1\\n  gracefulShutdownMillis: 30000';
                  var newContent = (draft && draft.newContent) || (draft && draft.changes) || 'No new content available';
                  renderDiff(oldContent, newContent);
                }

                function renderDiff(oldText, newText) {
                  var oldLines = oldText.split('\\n');
                  var newLines = newText.split('\\n');
                  var oldHtml = '';
                  var newHtml = '';
                  var maxLen = Math.max(oldLines.length, newLines.length);
                  for (var i = 0; i < maxLen; i++) {
                    var ol = i < oldLines.length ? oldLines[i] : '';
                    var nl = i < newLines.length ? newLines[i] : '';
                    if (ol !== nl) {
                      oldHtml += '<div style="background:rgba(255,80,80,0.15);padding:0 4px;">' + esc(ol || '(empty)') + '</div>';
                      newHtml += '<div style="background:rgba(80,200,80,0.15);padding:0 4px;">' + esc(nl || '(empty)') + '</div>';
                    } else {
                      oldHtml += '<div style="padding:0 4px;">' + esc(ol) + '</div>';
                      newHtml += '<div style="padding:0 4px;">' + esc(nl) + '</div>';
                    }
                  }
                  document.getElementById('diffOld').innerHTML = oldHtml;
                  document.getElementById('diffNew').innerHTML = newHtml;
                }

                function draftAction(id, action) {
                  if (action === 'rollback') { showRollbackModal(id); return; }
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

                function exportAllAuditTrail() {
                  fetch(CTX + '/api/audit?limit=100000').then(function(r){return r.json();}).then(function(d) {
                    var events = d.events || [];
                    var csv = 'Timestamp,User,Action,Resource,Detail,Success,IP\\n';
                    events.forEach(function(e) {
                      csv += '"' + (e.timestamp||'') + '","' + (e.user||'') + '","' + (e.action||'') + '","'
                        + (e.resource||'') + '","' + (e.detail||'').replace(/"/g,'""') + '",'
                        + e.success + ',"' + (e.sourceIp||'') + '"\\n';
                    });
                    fetch(CTX + '/api/drafts').then(function(r2){return r2.json();}).then(function(d2) {
                      csv += '\\n\\nDRAFTS\\nID,Author,Target,Description,Status\\n';
                      (d2.drafts || []).forEach(function(dr) {
                        csv += '"' + (dr.id||'') + '","' + (dr.author||'') + '","' + (dr.target||'') + '","'
                          + (dr.description||'').replace(/"/g,'""') + '","' + (dr.status||'') + '"\\n';
                      });
                      var blob = new Blob([csv], {type:'text/csv'});
                      var a = document.createElement('a');
                      a.href = URL.createObjectURL(blob);
                      a.download = 'full-audit-trail-' + new Date().toISOString().slice(0,10) + '.csv';
                      a.click();
                      showToast('Complete audit trail exported (' + events.length + ' events)', 'success');
                    });
                  }).catch(function() {
                    showToast('Failed to export audit trail', 'error');
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
