# Velo Web Admin Guide

Built-in web-based administration console for Velo WAS.
Modeled after JEUS WebAdmin, providing 100% CLI command coverage (73 commands) through the browser.

## Quick Start

| Item | Default |
|------|---------|
| URL | `http://localhost:8080/admin` |
| Username | `admin` |
| Password | `admin` |
| Context Path | `/admin` (configurable) |

> If the server port differs from 8080, use `http://localhost:{port}/admin`.

## Configuration

Enable/disable Web Admin and configure the context path in `conf/server.yaml`:

```yaml
server:
  webAdmin:
    enabled: true          # Set to false to disable Web Admin
    contextPath: /admin    # Change to any desired path
```

When `webAdmin.enabled: true`, the console is automatically deployed at the configured context path on server startup.

## Authentication

- Login page: `{contextPath}/login`
- Session-based authentication (Jakarta Servlet HttpSession)
- Unauthenticated requests are automatically redirected to the login page
- Login/logout events are recorded in the audit log

## Pages

### Core Pages

| Page | Path | Description |
|------|------|-------------|
| Dashboard | `/` | Server status summary, real-time heap/thread charts, SSE integration |
| Console | `/console` | CLI command execution (all 73 commands supported) |
| Scripts | `/scripts` | Multi-command batch script execution with templates |

### Management Pages

| Page | Path | Description |
|------|------|-------------|
| Domain | `/domain` | Server configuration viewer, raw YAML, Draft-based config changes |
| Servers | `/servers` | Server instance status, live thread/memory refresh, server control |
| Clusters | `/clusters` | Cluster list, create, rolling restart/stop |
| Nodes | `/nodes` | Node inventory, OS/Java/CPU info, server mapping |
| Applications | `/applications` | Deployed app list, Deploy/Undeploy/Redeploy/Stop, WAR upload |
| Resources | `/resources` | JDBC DataSource, JMS, JNDI, Memory, Thread Pools, TLS Certificates |
| Security | `/security` | User management (create/remove/password change), roles, policies, sessions |
| Monitoring | `/monitoring` | Real-time metrics, heap chart, logger level management, audit events, alerts |
| Diagnostics | `/diagnostics` | Thread dump, heap dump, deadlock check, JVM/system info |
| History | `/history` | Change history, audit log, Draft workflow management |
| Settings | `/settings` | Console preferences (language, auto-refresh, theme), localStorage persistence |

## REST API

All API endpoints are under `{contextPath}/api/*`.

### GET Endpoints

| Endpoint | Description |
|----------|-------------|
| `/api/status` | Server status (heap, threads, uptime, TLS) |
| `/api/servers` | Server instance list |
| `/api/applications` | Deployed applications |
| `/api/clusters` | Cluster list |
| `/api/nodes` | Node information |
| `/api/resources` | Resource information |
| `/api/threads` | Full thread dump |
| `/api/threadpools` | Thread pool status |
| `/api/monitoring` | MetricsCollector snapshot |
| `/api/commands` | Available CLI commands |
| `/api/audit` | Audit log (filters: `?action=`, `?user=`, `?limit=`) |
| `/api/drafts` | Config change drafts (filter: `?status=`) |
| `/api/config` | Raw server.yaml content |
| `/api/jvm` | JVM information |
| `/api/system` | System information |
| `/api/loggers` | Logger list and current levels |
| `/api/users` | User list |

### POST Endpoints

| Endpoint | Description |
|----------|-------------|
| `/api/execute` | Execute CLI command (`{"command": "status"}`) |
| `/api/config/save` | Create config change draft |
| `/api/loggers/set` | Change logger level (`{"logger": "...", "level": "DEBUG"}`) |
| `/api/drafts/create` | Create config change draft |
| `/api/drafts/{id}/action` | Draft workflow (`{"action": "validate\|review\|approve\|apply\|rollback\|reject"}`) |
| `/api/users/create` | Create user (`{"username": "...", "password": "..."}`) |
| `/api/users/remove` | Remove user (`{"username": "..."}`) |
| `/api/users/change-password` | Change password (`{"username": "...", "password": "..."}`) |

### SSE (Server-Sent Events)

| Endpoint | Description |
|----------|-------------|
| `/sse/status` | Server status stream at 2-second intervals (used by Dashboard) |

## Config Change Workflow (Draft)

Configuration changes follow a safe, staged workflow:

```
Draft -> Validate -> Review -> Approve -> Apply
                                           |
                                        Rollback
```

1. **Draft**: Create a change proposal
2. **Validate**: Syntax/format validation
3. **Review**: Reviewer confirmation
4. **Approve**: Approver authorization
5. **Apply**: Apply the change
6. **Rollback**: Revert an applied change
7. **Reject**: Reject at review stage

All workflow steps are recorded in the audit log.

## User Management & Password Change

### Default Account

A default admin account is created on first server startup.

| Item | Default |
|------|---------|
| Username | `admin` |
| Password | `admin` |

> **Security recommendation**: Change the default password immediately after first login.

### Change Password via Web Admin UI

1. Log in to Web Admin (`http://localhost:8080/admin`)
2. Navigate to the **Security** page from the sidebar
3. Click the **Reset Password** button next to the target user
4. Enter and confirm the new password

### Change Password via CLI Console

Run the following command in the Console page (`/console`) or CLI tool:

```
changepassword admin newpassword123
```

### Change Password via REST API

```bash
curl -X POST http://localhost:8080/admin/api/users/change-password \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "newpassword123"}'
```

### Create User

**Web Admin UI**: Click **Add User** on the Security page, then enter username and password.

**CLI Console**:
```
createuser newuser password123
```

**REST API**:
```bash
curl -X POST http://localhost:8080/admin/api/users/create \
  -H "Content-Type: application/json" \
  -d '{"username": "newuser", "password": "password123"}'
```

### Remove User

**CLI Console**:
```
removeuser username
```

**REST API**:
```bash
curl -X POST http://localhost:8080/admin/api/users/remove \
  -H "Content-Type: application/json" \
  -d '{"username": "username"}'
```

> All user management operations (create, remove, password change) are automatically recorded in the audit log.

## Audit Log

The following actions are automatically audited:

- Login success/failure
- Logout
- CLI command execution
- Logger level changes
- User create/remove/password change
- All Draft workflow steps (create, validate, review, approve, apply, rollback, reject)

Up to 10,000 audit events are retained in memory. View via the History page or `/api/audit`.

## Toast Notifications

All operation results are displayed as toast notifications in the top-right corner:
- Success: green
- Error: red
- Warning: yellow
- Info: blue

Notifications auto-dismiss after 4 seconds or can be clicked to dismiss immediately.

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+K` | Open command palette (page navigation, command execution) |
| `ESC` | Close modal/palette |

## Module Structure

```
was-webadmin/
├── WebAdminApplication.java          # Servlet app factory
├── api/
│   ├── AdminApiServlet.java          # REST API (GET/POST)
│   └── AdminSseServlet.java          # SSE real-time stream
├── audit/
│   ├── AuditEngine.java              # Audit log engine (singleton)
│   └── AuditEvent.java               # Audit event record
├── config/
│   ├── ConfigChangeEngine.java       # Draft workflow engine (singleton)
│   └── ConfigDraft.java              # Draft record
└── servlet/
    ├── AdminAuthFilter.java          # Authentication filter
    ├── AdminDashboardServlet.java    # Dashboard
    ├── AdminLoginServlet.java        # Login
    ├── AdminLogoutServlet.java       # Logout
    ├── AdminConsoleServlet.java      # CLI Console
    ├── AdminPageLayout.java          # Shared layout (CSS/JS/sidebar)
    ├── AdminStaticResourceServlet.java
    └── page/                         # Management page servlets (14)
        ├── ApplicationsPageServlet.java
        ├── ClustersPageServlet.java
        ├── DiagnosticsPageServlet.java
        ├── DomainPageServlet.java
        ├── HistoryPageServlet.java
        ├── MonitoringPageServlet.java
        ├── NodesPageServlet.java
        ├── ResourcesPageServlet.java
        ├── ScriptsPageServlet.java
        ├── SecurityPageServlet.java
        ├── ServersPageServlet.java
        └── SettingsPageServlet.java
```

## UI Features

- Dark theme (JEUS WebAdmin style)
- SPA-like tab switching (no full page reloads)
- Zero external frontend dependencies (pure embedded HTML/CSS/JS)
- Responsive sidebar navigation
- Real-time data refresh (SSE + Polling fallback)
- Canvas-based live charts (heap memory, threads)
