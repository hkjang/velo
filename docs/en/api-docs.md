# API Documentation (Swagger) Guide

Velo WAS Web Admin provides built-in OpenAPI 3.0-based API documentation.
A fully embedded Swagger UI is included, implemented in pure HTML/CSS/JS with no external dependencies.

## Access URLs

| Item | Path | Description |
|------|------|-------------|
| Swagger UI | `{contextPath}/api-docs/ui` | Interactive API documentation page |
| OpenAPI JSON | `{contextPath}/api-docs` | OpenAPI 3.0 JSON specification |

With default settings:

- Swagger UI: `http://localhost:8080/admin/api-docs/ui`
- OpenAPI JSON: `http://localhost:8080/admin/api-docs`

## Configuration

Enable or disable API documentation in `conf/server.yaml`:

```yaml
server:
  webAdmin:
    enabled: true
    contextPath: /admin
    apiDocsEnabled: true    # Set to false to disable API docs
```

When `apiDocsEnabled: false`, the `/api-docs/*` endpoints are not registered.
This can be disabled in production for security purposes.

## Swagger UI Features

### Endpoint Browsing

- All REST API endpoints are displayed **grouped by tag**
- Click any endpoint to expand its details
- Color-coded HTTP methods:
  - `GET` → green
  - `POST` → yellow
  - `PUT` → blue
  - `DELETE` → red

### Detail View

Expanding an endpoint shows:

- **Description**: Purpose and behavior of the API
- **Parameters**: Query parameters (name, location, type, description)
- **Request Body**: Request body schema (field name, type, required, examples)
- **Responses**: Response codes and descriptions

### Try It

All GET endpoints have a **Try It** button.
Clicking it executes a live API call and displays the formatted JSON response.

> For POST endpoints, use the Console page (`/console`) or curl commands.

### Schema Reference

The **Schemas** section at the bottom of the page lists all data models.
Click any schema to view its properties, types, and format information.

## API Tag Categories

| Tag | Description | Endpoint Count |
|-----|-------------|----------------|
| Status | Server status and health information | 1 |
| Servers | Server instance management | 1 |
| Applications | Application deployment and management | 1 |
| Resources | Resource monitoring (memory, connections) | 1 |
| Monitoring | Metrics and performance monitoring | 1 |
| Diagnostics | Thread dumps, JVM/system info | 4 |
| Security | User and session management | 4 |
| Configuration | Server config, loggers, draft management | 6 |
| Commands | CLI command execution | 2 |
| Audit | Audit trail and history | 1 |
| Clusters | Cluster management | 1 |
| Nodes | Node management | 1 |

## Data Models (Schemas)

### Key Schemas

| Schema | Description |
|--------|-------------|
| `ServerStatus` | Server status (name, nodeId, heap memory, uptime, thread count) |
| `ServerInfo` | Server instance details (host, port, transport, TLS, thread config) |
| `ApplicationInfo` | Application (name, context path, status, servlet/filter counts) |
| `ResourceInfo` | Heap/non-heap memory usage |
| `ThreadInfo` | All threads (ID, name, state, daemon flag, deadlock detection) |
| `CommandInfo` | CLI command (name, description, category, usage) |
| `CommandResult` | Command execution result (success flag, message) |
| `ThreadPoolInfo` | Thread pool (name, active count, pool size, max pool size) |
| `LoggerInfo` | Logger (name, level) |
| `UserInfo` | User (username) |
| `AuditEvent` | Audit event (timestamp, user, action, target, detail, IP, success) |
| `ClusterInfo` | Cluster (name, member count, status) |
| `NodeInfo` | Node (ID, host, OS, CPUs, Java version, server list) |

## Authentication

Accessing the API documentation page requires Web Admin login.
API calls also require session cookie (JSESSIONID) authentication.

```
Security Scheme: sessionAuth
Type: API Key (Cookie)
Name: JSESSIONID
```

## Using with External Tools

### Download OpenAPI Spec with curl

```bash
# Log in to get session cookie
curl -c cookies.txt -X POST http://localhost:8080/admin/login \
  -d "username=admin&password=admin"

# Download OpenAPI JSON
curl -b cookies.txt http://localhost:8080/admin/api-docs > openapi.json
```

### Import into Postman/Insomnia

1. Download the OpenAPI JSON from `http://localhost:8080/admin/api-docs`
2. Postman → Import → File → select `openapi.json`
3. All endpoints are automatically created as a collection

### Use with Swagger Editor

1. Visit [https://editor.swagger.io](https://editor.swagger.io)
2. File → Import URL → enter `http://localhost:8080/admin/api-docs`
3. Edit the spec and generate client code

## Navigation

The API Docs page can be accessed via:

- **Sidebar**: Administration section → "API Docs" menu item
- **Command Palette**: `Ctrl+K` → search "API Docs"
- **Direct URL**: `http://localhost:8080/admin/api-docs/ui`

## UI Features

- Supports the same dark/light theme as Web Admin
- Integrated with the shared layout (sidebar, header, toast notifications)
- Fully embedded UI with no external CDN dependencies
- Direct download link for the OpenAPI JSON specification
