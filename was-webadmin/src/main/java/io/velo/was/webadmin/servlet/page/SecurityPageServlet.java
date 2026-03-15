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

        String body = """
                <div class="page-header">
                  <div>
                    <div class="page-title">Security</div>
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
                      <button class="btn btn-sm btn-primary">Add Role</button>
                    </div>
                    <table class="data-table">
                      <thead><tr><th>Role</th><th>Description</th><th>Permissions</th><th>Members</th></tr></thead>
                      <tbody>
                        <tr>
                          <td><strong>ADMIN</strong></td>
                          <td>Full system administrator</td>
                          <td><span class="badge badge-danger">ALL</span></td>
                          <td>1</td>
                        </tr>
                        <tr>
                          <td><strong>OPERATOR</strong></td>
                          <td>Server and application operations</td>
                          <td><span class="badge badge-warning">READ, EXECUTE</span></td>
                          <td>0</td>
                        </tr>
                        <tr>
                          <td><strong>VIEWER</strong></td>
                          <td>Read-only monitoring access</td>
                          <td><span class="badge badge-info">READ</span></td>
                          <td>0</td>
                        </tr>
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
                      <tr><td>Session Timeout</td><td>%d seconds</td></tr>
                      <tr><td>Max Failed Attempts</td><td>5</td></tr>
                      <tr><td>Account Lock Duration</td><td>300 seconds</td></tr>
                      <tr><td>Dual Approval (4-eyes)</td><td><span class="badge badge-neutral">Disabled</span></td></tr>
                    </table>
                  </div>
                </div>

                <div class="tab-panel" id="tab-sessions">
                  <div class="card">
                    <div class="card-header">
                      <div class="card-title">Active Sessions</div>
                      <button class="btn btn-sm btn-danger">Terminate All</button>
                    </div>
                    <table class="data-table">
                      <thead><tr><th>User</th><th>IP</th><th>Started</th><th>Last Activity</th><th>Actions</th></tr></thead>
                      <tbody>
                        <tr>
                          <td>admin</td>
                          <td>127.0.0.1</td>
                          <td>Current</td>
                          <td>Active</td>
                          <td><span style="color:var(--text3);font-size:12px;">Current session</span></td>
                        </tr>
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

                loadUsers();
                </script>
                """.formatted(
                server.getSession().getTimeoutSeconds(),
                ctx
        );

        resp.getWriter().write(AdminPageLayout.page("Security", server.getName(), server.getNodeId(),
                ctx, "security", body));
    }
}
