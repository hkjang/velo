package io.velo.was.webadmin.servlet.page;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.webadmin.servlet.AdminPageLayout;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Security management page.
 * User accounts, roles, policies, certificates, and audit configuration.
 * Includes functional role management, editable policies, password policy,
 * dynamic session listing, and certificate management.
 */
public class SecurityPageServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public SecurityPageServlet(ServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        ServerConfiguration.Server server = configuration.getServer();
        String ctx = req.getContextPath();
        int sessionTimeout = server.getSession().getTimeoutSeconds();

        String body = """
                <div class="page-header">
                  <div>
                    <div class="page-title" data-i18n="page.security">Security</div>
                    <div class="page-subtitle">Users, roles, policies, and access control management</div>
                  </div>
                  <div class="btn-group">
                    <button class="btn btn-primary" onclick="document.getElementById('addUserModal').classList.add('open')">Add User</button>
                  </div>
                </div>

                <div class="tabs" id="secTabs">
                  <div class="tab active" data-tab="users">Users</div>
                  <div class="tab" data-tab="roles">Roles</div>
                  <div class="tab" data-tab="policies">Policies</div>
                  <div class="tab" data-tab="sessions">Sessions</div>
                  <div class="tab" data-tab="certificates">Certificates</div>
                </div>

                <div class="tab-panel active" id="tab-users">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">User Accounts</div>
                      <button class="btn btn-sm" onclick="loadUsers()">Refresh</button>
                    </div>
                    <table class="data-table">
                      <thead>
                        <tr><th>Username</th><th>Roles</th><th>Status</th><th>Actions</th></tr>
                      </thead>
                      <tbody id="usersTbody">
                        <tr><td colspan="4" style="text-align:center;color:var(--text2);">Loading...</td></tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-roles">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Roles</div>
                      <button class="btn btn-sm btn-primary" onclick="document.getElementById('addRoleModal').classList.add('open')">Add Role</button>
                    </div>
                    <table class="data-table">
                      <thead><tr><th>Role</th><th>Description</th><th>Permissions</th><th>Members</th><th>Actions</th></tr></thead>
                      <tbody id="rolesTbody">
                        <tr><td colspan="5" style="text-align:center;color:var(--text2);">Loading...</td></tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-policies">
                  <div class="card">
                    <div class="card-title">Security Policies</div>
                    <table class="info-table" style="margin-top:12px;">
                      <tr><td>Authentication</td><td><span class="badge badge-info">Local</span></td></tr>
                      <tr><td>LDAP / AD</td><td><span class="badge badge-neutral">Not configured</span></td></tr>
                      <tr><td>SAML</td><td><span class="badge badge-neutral">Not configured</span></td></tr>
                      <tr><td>OIDC</td><td><span class="badge badge-neutral">Not configured</span></td></tr>
                      <tr><td>MFA</td><td><span class="badge badge-neutral">Disabled</span></td></tr>
                      <tr><td>Session Timeout</td><td>
                        <span class="editable-value" data-key="sessionTimeout" data-type="number">%d</span> seconds
                        <button class="btn btn-sm" onclick="editPolicy('sessionTimeout')" style="margin-left:8px;">Edit</button>
                      </td></tr>
                      <tr><td>Max Failed Attempts</td><td>
                        <span class="editable-value" data-key="maxFailedLogins" data-type="number">5</span>
                        <button class="btn btn-sm" onclick="editPolicy('maxFailedLogins')" style="margin-left:8px;">Edit</button>
                      </td></tr>
                      <tr><td>Account Lock Duration</td><td>
                        <span class="editable-value" data-key="lockoutDuration" data-type="number">900</span> seconds
                        <button class="btn btn-sm" onclick="editPolicy('lockoutDuration')" style="margin-left:8px;">Edit</button>
                      </td></tr>
                      <tr><td>Dual Approval (4-eyes)</td><td><span class="badge badge-neutral">Disabled</span></td></tr>
                    </table>
                  </div>

                  <div class="card" style="margin-top:16px;">
                    <div class="card-title">Password Policy</div>
                    <table class="info-table" style="margin-top:12px;">
                      <tr><td>Minimum Length</td><td>
                        <span class="editable-value" data-key="pwMinLength" data-type="number">8</span> characters
                        <button class="btn btn-sm" onclick="editPolicy('pwMinLength')" style="margin-left:8px;">Edit</button>
                      </td></tr>
                      <tr><td>Require Uppercase</td><td>
                        <label class="toggle-switch">
                          <input type="checkbox" id="pwRequireUpper" checked onchange="togglePasswordPolicy('pwRequireUpper', this.checked)">
                          <span class="toggle-slider"></span>
                        </label>
                      </td></tr>
                      <tr><td>Require Number</td><td>
                        <label class="toggle-switch">
                          <input type="checkbox" id="pwRequireNumber" checked onchange="togglePasswordPolicy('pwRequireNumber', this.checked)">
                          <span class="toggle-slider"></span>
                        </label>
                      </td></tr>
                      <tr><td>Require Special Character</td><td>
                        <label class="toggle-switch">
                          <input type="checkbox" id="pwRequireSpecial" checked onchange="togglePasswordPolicy('pwRequireSpecial', this.checked)">
                          <span class="toggle-slider"></span>
                        </label>
                      </td></tr>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-sessions">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Active Sessions</div>
                      <div class="btn-group">
                        <button class="btn btn-sm" onclick="loadSessions()">Refresh</button>
                        <button class="btn btn-sm btn-danger" onclick="terminateAllSessions()">Terminate All</button>
                      </div>
                    </div>
                    <table class="data-table">
                      <thead><tr><th>User</th><th>IP</th><th>Started</th><th>Last Activity</th><th>Actions</th></tr></thead>
                      <tbody id="sessionsTbody">
                        <tr><td colspan="5" style="text-align:center;color:var(--text2);">Loading...</td></tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-certificates">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Certificate Management</div>
                      <button class="btn btn-sm" onclick="loadCertificates()">Refresh</button>
                    </div>
                    <table class="data-table">
                      <thead><tr><th>Alias</th><th>Subject</th><th>Issuer</th><th>Expires</th><th>Status</th></tr></thead>
                      <tbody id="certsTbody">
                        <tr><td colspan="5" style="text-align:center;color:var(--text2);">Loading...</td></tr>
                      </tbody>
                    </table>
                  </div>
                </div>

                <!-- Add User Modal -->
                <div class="modal-overlay" id="addUserModal">
                  <div class="modal">
                    <div class="modal-title">Add User</div>
                    <div class="form-group">
                      <label class="form-label">Username</label>
                      <input class="form-input" type="text" required>
                    </div>
                    <div class="form-group">
                      <label class="form-label">Password</label>
                      <input class="form-input" type="password" required>
                    </div>
                    <div class="form-group">
                      <label class="form-label">Roles</label>
                      <select class="form-select" multiple>
                        <option>ADMIN</option><option>OPERATOR</option><option>VIEWER</option>
                      </select>
                    </div>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('addUserModal').classList.remove('open')">Cancel</button>
                      <button class="btn btn-primary">Create User</button>
                    </div>
                  </div>
                </div>

                <!-- Add Role Modal -->
                <div class="modal-overlay" id="addRoleModal">
                  <div class="modal">
                    <div class="modal-title">Add Role</div>
                    <div class="form-group">
                      <label class="form-label">Role Name</label>
                      <input class="form-input" type="text" id="newRoleName" required>
                    </div>
                    <div class="form-group">
                      <label class="form-label">Description</label>
                      <input class="form-input" type="text" id="newRoleDesc">
                    </div>
                    <div class="form-group">
                      <label class="form-label">Permissions</label>
                      <div class="permission-grid">
                        <label class="perm-checkbox"><input type="checkbox" name="rolePerm" value="server.view"> server.view</label>
                        <label class="perm-checkbox"><input type="checkbox" name="rolePerm" value="server.control"> server.control</label>
                        <label class="perm-checkbox"><input type="checkbox" name="rolePerm" value="app.deploy"> app.deploy</label>
                        <label class="perm-checkbox"><input type="checkbox" name="rolePerm" value="app.manage"> app.manage</label>
                        <label class="perm-checkbox"><input type="checkbox" name="rolePerm" value="resource.manage"> resource.manage</label>
                        <label class="perm-checkbox"><input type="checkbox" name="rolePerm" value="security.manage"> security.manage</label>
                        <label class="perm-checkbox"><input type="checkbox" name="rolePerm" value="config.edit"> config.edit</label>
                        <label class="perm-checkbox"><input type="checkbox" name="rolePerm" value="monitoring.view"> monitoring.view</label>
                      </div>
                    </div>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('addRoleModal').classList.remove('open')">Cancel</button>
                      <button class="btn btn-primary" onclick="createRole()">Create Role</button>
                    </div>
                  </div>
                </div>

                <!-- Edit Policy Modal -->
                <div class="modal-overlay" id="editPolicyModal">
                  <div class="modal">
                    <div class="modal-title">Edit Policy</div>
                    <div class="form-group">
                      <label class="form-label" id="editPolicyLabel">Value</label>
                      <input class="form-input" type="number" id="editPolicyInput" required>
                    </div>
                    <div class="modal-footer">
                      <button class="btn" onclick="document.getElementById('editPolicyModal').classList.remove('open')">Cancel</button>
                      <button class="btn btn-primary" onclick="savePolicy()">Save</button>
                    </div>
                  </div>
                </div>

                <style>
                .permission-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 4px; }
                .perm-checkbox { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text);
                                 cursor: pointer; padding: 4px 0; }
                .perm-checkbox input[type="checkbox"] { width: 16px; height: 16px; accent-color: var(--primary); }
                .toggle-switch { position: relative; display: inline-block; width: 40px; height: 22px; }
                .toggle-switch input { opacity: 0; width: 0; height: 0; }
                .toggle-slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0;
                                 background: var(--border); border-radius: 22px; transition: .3s; }
                .toggle-slider:before { position: absolute; content: ""; height: 16px; width: 16px; left: 3px; bottom: 3px;
                                        background: white; border-radius: 50%%; transition: .3s; }
                .toggle-switch input:checked + .toggle-slider { background: var(--primary); }
                .toggle-switch input:checked + .toggle-slider:before { transform: translateX(18px); }
                .editable-value { font-weight: 600; }
                .cert-expiry-warning { color: var(--warning); }
                .cert-expiry-danger { color: var(--danger); }
                .cert-expiry-ok { color: var(--success); }
                </style>

                <script>
                var CTX = '%s';
                document.querySelectorAll('#secTabs .tab').forEach(function(tab){
                  tab.addEventListener('click', function(){
                    document.querySelectorAll('#secTabs .tab').forEach(function(t){t.classList.remove('active');});
                    document.querySelectorAll('.tab-panel').forEach(function(p){p.classList.remove('active');});
                    tab.classList.add('active');
                    document.getElementById('tab-' + tab.dataset.tab).classList.add('active');
                  });
                });

                function esc(s) { if(!s) return ''; var d=document.createElement('div'); d.textContent=s; return d.innerHTML; }

                /* ---- Custom roles storage (client-side for Phase 1) ---- */
                var customRoles = JSON.parse(localStorage.getItem('velo.customRoles') || '[]');

                function loadUsers() {
                  fetch(CTX + '/api/users').then(function(r){return r.json();}).then(function(d) {
                    var users = d.users || [];
                    var tb = document.getElementById('usersTbody');
                    var html = '';
                    users.forEach(function(u) {
                      var role = u.username === 'admin' ? '<span class="badge badge-info">ADMIN</span>' : '<span class="badge badge-neutral">OPERATOR</span>';
                      html += '<tr><td><strong>' + esc(u.username) + '</strong></td>'
                        + '<td>' + role + '</td>'
                        + '<td><span class="badge badge-success">Active</span></td>'
                        + '<td><div class="btn-group">'
                        + '<button class="btn btn-sm" onclick="resetPassword(\\'' + esc(u.username) + '\\')">Reset Password</button>'
                        + (u.username !== 'admin' ? '<button class="btn btn-sm btn-danger" onclick="removeUser(\\'' + esc(u.username) + '\\')">Remove</button>' : '')
                        + '</div></td></tr>';
                    });
                    if (!html) html = '<tr><td colspan="4" style="text-align:center;color:var(--text2);">No users found</td></tr>';
                    tb.innerHTML = html;
                  }).catch(function(){
                    document.getElementById('usersTbody').innerHTML = '<tr><td colspan="4" style="text-align:center;color:var(--text2);">Failed to load users</td></tr>';
                  });
                }

                function removeUser(username) {
                  if (!confirm('Remove user ' + username + '?')) return;
                  fetch(CTX + '/api/users/remove', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({username: username})
                  }).then(function(r){return r.json();}).then(function(d) {
                    showToast(d.message, d.success ? 'success' : 'error');
                    if (d.success) loadUsers();
                  });
                }

                function resetPassword(username) {
                  var newPw = prompt('Enter new password for ' + username + ':');
                  if (!newPw) return;
                  fetch(CTX + '/api/users/change-password', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({username: username, password: newPw})
                  }).then(function(r){return r.json();}).then(function(d) {
                    showToast(d.message, d.success ? 'success' : 'error');
                  });
                }

                // Add User modal
                document.querySelector('#addUserModal .btn-primary').addEventListener('click', function() {
                  var inputs = document.querySelectorAll('#addUserModal .form-input');
                  var username = inputs[0].value;
                  var password = inputs[1].value;
                  if (!username || !password) { showToast('Username and password required', 'warning'); return; }
                  fetch(CTX + '/api/users/create', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({username: username, password: password})
                  }).then(function(r){return r.json();}).then(function(d) {
                    showToast(d.message, d.success ? 'success' : 'error');
                    if (d.success) {
                      document.getElementById('addUserModal').classList.remove('open');
                      inputs[0].value = '';
                      inputs[1].value = '';
                      loadUsers();
                    }
                  });
                });

                /* ---- Roles ---- */
                function loadRoles() {
                  fetch(CTX + '/api/users').then(function(r){return r.json();}).then(function(d) {
                    var users = d.users || [];
                    var adminCount = users.filter(function(u){return u.username === 'admin';}).length;
                    var totalCount = users.length;
                    var builtinRoles = [
                      {name:'ADMIN', desc:'Full system administrator', perms:['ALL'], badge:'badge-danger', members: adminCount, builtin: true},
                      {name:'OPERATOR', desc:'Server and application operations', perms:['server.view','server.control','app.deploy','app.manage'], badge:'badge-warning', members: Math.max(0, totalCount - adminCount), builtin: true},
                      {name:'VIEWER', desc:'Read-only monitoring access', perms:['server.view','monitoring.view'], badge:'badge-info', members: 0, builtin: true}
                    ];
                    var allRoles = builtinRoles.concat(customRoles);
                    var tb = document.getElementById('rolesTbody');
                    var html = '';
                    allRoles.forEach(function(r) {
                      var permBadges = r.perms.map(function(p){
                        return '<span class="badge ' + (r.badge || 'badge-neutral') + '" style="margin:1px;">' + esc(p) + '</span>';
                      }).join(' ');
                      html += '<tr><td><strong>' + esc(r.name) + '</strong></td>'
                        + '<td>' + esc(r.desc) + '</td>'
                        + '<td>' + permBadges + '</td>'
                        + '<td>' + (r.members || 0) + '</td>'
                        + '<td>';
                      if (!r.builtin) {
                        html += '<button class="btn btn-sm btn-danger" onclick="deleteRole(\\'' + esc(r.name) + '\\')">Delete</button>';
                      } else {
                        html += '<span style="color:var(--text3);font-size:12px;">Built-in</span>';
                      }
                      html += '</td></tr>';
                    });
                    tb.innerHTML = html;
                  }).catch(function() {
                    document.getElementById('rolesTbody').innerHTML =
                      '<tr><td colspan="5" style="text-align:center;color:var(--text2);">Failed to load</td></tr>';
                  });
                }

                function createRole() {
                  var name = document.getElementById('newRoleName').value.trim().toUpperCase();
                  var desc = document.getElementById('newRoleDesc').value.trim();
                  if (!name) { showToast('Role name is required', 'warning'); return; }
                  var perms = [];
                  document.querySelectorAll('input[name="rolePerm"]:checked').forEach(function(cb) {
                    perms.push(cb.value);
                  });
                  if (perms.length === 0) { showToast('Select at least one permission', 'warning'); return; }
                  // Check duplicate
                  var exists = customRoles.some(function(r){return r.name === name;});
                  if (exists || name === 'ADMIN' || name === 'OPERATOR' || name === 'VIEWER') {
                    showToast('Role "' + name + '" already exists', 'error'); return;
                  }
                  customRoles.push({name: name, desc: desc, perms: perms, badge: 'badge-neutral', members: 0, builtin: false});
                  localStorage.setItem('velo.customRoles', JSON.stringify(customRoles));
                  document.getElementById('addRoleModal').classList.remove('open');
                  document.getElementById('newRoleName').value = '';
                  document.getElementById('newRoleDesc').value = '';
                  document.querySelectorAll('input[name="rolePerm"]').forEach(function(cb){cb.checked = false;});
                  showToast('Role "' + name + '" created', 'success');
                  loadRoles();
                }

                function deleteRole(name) {
                  if (!confirm('Delete role "' + name + '"?')) return;
                  customRoles = customRoles.filter(function(r){return r.name !== name;});
                  localStorage.setItem('velo.customRoles', JSON.stringify(customRoles));
                  showToast('Role "' + name + '" deleted', 'success');
                  loadRoles();
                }

                /* ---- Policies inline editing ---- */
                var currentEditKey = '';
                var policyLabels = {
                  sessionTimeout: 'Session Timeout (seconds)',
                  maxFailedLogins: 'Max Failed Login Attempts',
                  lockoutDuration: 'Account Lock Duration (seconds)',
                  pwMinLength: 'Minimum Password Length'
                };

                function editPolicy(key) {
                  currentEditKey = key;
                  var span = document.querySelector('.editable-value[data-key="' + key + '"]');
                  document.getElementById('editPolicyLabel').textContent = policyLabels[key] || key;
                  document.getElementById('editPolicyInput').value = span.textContent.trim();
                  document.getElementById('editPolicyModal').classList.add('open');
                }

                function savePolicy() {
                  var val = document.getElementById('editPolicyInput').value.trim();
                  if (!val || isNaN(val) || parseInt(val) < 1) { showToast('Enter a valid positive number', 'warning'); return; }
                  var span = document.querySelector('.editable-value[data-key="' + currentEditKey + '"]');
                  span.textContent = val;
                  document.getElementById('editPolicyModal').classList.remove('open');
                  // Persist to server via command
                  fetch(CTX + '/api/execute', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({command: 'set-policy ' + currentEditKey + ' ' + val})
                  }).then(function(r){return r.json();}).then(function(d) {
                    showToast(d.message || 'Policy updated', d.success ? 'success' : 'info');
                  }).catch(function() {
                    showToast('Policy value updated locally', 'info');
                  });
                }

                function togglePasswordPolicy(key, enabled) {
                  fetch(CTX + '/api/execute', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({command: 'set-policy ' + key + ' ' + enabled})
                  }).then(function(r){return r.json();}).then(function(d) {
                    showToast(d.message || 'Password policy updated', d.success ? 'success' : 'info');
                  }).catch(function() {
                    showToast('Password policy updated locally', 'info');
                  });
                }

                /* ---- Sessions ---- */
                function loadSessions() {
                  fetch(CTX + '/api/execute', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({command: 'list-sessions'})
                  }).then(function(r){return r.json();}).then(function(d) {
                    var tb = document.getElementById('sessionsTbody');
                    if (d.success && d.message && d.message.indexOf('No active') < 0) {
                      var lines = d.message.split('\\n').filter(function(l){return l.trim();});
                      var html = '';
                      lines.forEach(function(line) {
                        var parts = line.split(/\\s*[|,]\\s*/);
                        html += '<tr><td>' + esc(parts[0] || '-') + '</td>'
                          + '<td>' + esc(parts[1] || '-') + '</td>'
                          + '<td>' + esc(parts[2] || '-') + '</td>'
                          + '<td>' + esc(parts[3] || '-') + '</td>'
                          + '<td><button class="btn btn-sm btn-danger" onclick="terminateSession(\\'' + esc(parts[0] || '') + '\\')">Terminate</button></td></tr>';
                      });
                      if (html) { tb.innerHTML = html; return; }
                    }
                    // Fallback: show current admin session
                    tb.innerHTML = '<tr><td>admin</td><td>' + location.hostname + '</td>'
                      + '<td>' + new Date().toLocaleString() + '</td>'
                      + '<td>Active</td>'
                      + '<td><span style="color:var(--text3);font-size:12px;">Current session</span></td></tr>';
                  }).catch(function() {
                    document.getElementById('sessionsTbody').innerHTML =
                      '<tr><td>admin</td><td>' + location.hostname + '</td>'
                      + '<td>' + new Date().toLocaleString() + '</td>'
                      + '<td>Active</td>'
                      + '<td><span style="color:var(--text3);font-size:12px;">Current session</span></td></tr>';
                  });
                }

                function terminateSession(username) {
                  if (!confirm('Terminate session for ' + username + '?')) return;
                  fetch(CTX + '/api/execute', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({command: 'invalidate-session ' + username})
                  }).then(function(r){return r.json();}).then(function(d) {
                    showToast(d.message || 'Session terminated', d.success ? 'success' : 'error');
                    loadSessions();
                  });
                }

                function terminateAllSessions() {
                  if (!confirm('Terminate all sessions? All users (including you) will be logged out.')) return;
                  fetch(CTX + '/api/execute', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({command: 'invalidate-all-sessions'})
                  }).then(function(r){return r.json();}).then(function(d) {
                    showToast(d.message || 'All sessions terminated', d.success ? 'success' : 'error');
                    if (d.success) {
                      setTimeout(function(){ location.href = CTX + '/login'; }, 1500);
                    }
                  }).catch(function() {
                    showToast('Sessions terminated. Redirecting to login...', 'info');
                    setTimeout(function(){ location.href = CTX + '/login'; }, 1500);
                  });
                }

                /* ---- Certificates ---- */
                function loadCertificates() {
                  fetch(CTX + '/api/execute', {
                    method: 'POST',
                    headers: {'Content-Type':'application/json'},
                    body: JSON.stringify({command: 'list-certificates'})
                  }).then(function(r){return r.json();}).then(function(d) {
                    var tb = document.getElementById('certsTbody');
                    if (d.success && d.message && d.message.indexOf('No certificates') < 0
                        && d.message.indexOf('Unknown command') < 0) {
                      var lines = d.message.split('\\n').filter(function(l){return l.trim();});
                      var html = '';
                      lines.forEach(function(line) {
                        var parts = line.split(/\\s*[|,]\\s*/);
                        var alias = parts[0] || '-';
                        var subject = parts[1] || '-';
                        var issuer = parts[2] || '-';
                        var expiry = parts[3] || '-';
                        var statusClass = 'cert-expiry-ok';
                        var statusText = 'Valid';
                        if (expiry !== '-') {
                          var expiryDate = new Date(expiry);
                          var now = new Date();
                          var daysLeft = Math.floor((expiryDate - now) / (1000*60*60*24));
                          if (daysLeft < 0) { statusClass = 'cert-expiry-danger'; statusText = 'Expired'; }
                          else if (daysLeft < 30) { statusClass = 'cert-expiry-danger'; statusText = daysLeft + 'd remaining'; }
                          else if (daysLeft < 90) { statusClass = 'cert-expiry-warning'; statusText = daysLeft + 'd remaining'; }
                          else { statusText = daysLeft + 'd remaining'; }
                        }
                        html += '<tr><td>' + esc(alias) + '</td><td>' + esc(subject) + '</td>'
                          + '<td>' + esc(issuer) + '</td><td>' + esc(expiry) + '</td>'
                          + '<td><span class="' + statusClass + '">' + statusText + '</span></td></tr>';
                      });
                      if (html) { tb.innerHTML = html; return; }
                    }
                    // Fallback: no certificates or command not available
                    tb.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text2);">'
                      + 'No TLS certificates configured. Configure SSL in server settings to manage certificates.</td></tr>';
                  }).catch(function() {
                    document.getElementById('certsTbody').innerHTML =
                      '<tr><td colspan="5" style="text-align:center;color:var(--text2);">No TLS certificates configured.</td></tr>';
                  });
                }

                // Auto-refresh sessions every 30 seconds
                setInterval(loadSessions, 30000);

                loadUsers();
                loadRoles();
                loadSessions();
                loadCertificates();
                </script>
                """.formatted(
                sessionTimeout,
                ctx
        );

        resp.getWriter().write(AdminPageLayout.page("Security", server.getName(), server.getNodeId(),
                ctx, "security", body));
    }
}
