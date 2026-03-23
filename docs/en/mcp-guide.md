# Velo MCP Server — User & Administrator Guide

> **Version**: 0.5.11
> **Protocol**: MCP (Model Context Protocol) over Streamable HTTP
> **Transport**: JSON-RPC 2.0 + SSE (Server-Sent Events)

---

## 1. Overview

Velo MCP Server is a **Streamable HTTP MCP server** embedded in Velo WAS.
External MCP clients (Claude Desktop, IDE plugins, custom agents, etc.) can invoke Velo AI platform and WAS administration features through the standard MCP protocol.

### 1.1 Capabilities

| Category | Tools |
|----------|-------|
| **AI** | `infer` (run inference), `route` (routing decision) |
| **Server** | `list_servers`, `server_info`, `restart_server` |
| **Application** | `list_applications`, `application_info`, `deploy_application`, `undeploy_application`, `redeploy_application` |
| **Monitoring** | `system_info`, `jvm_info`, `memory_info`, `thread_info`, `resource_overview` |
| **Logging** | `list_loggers`, `get_log_level`, `set_log_level` |
| **Thread Pool** | `list_thread_pools`, `thread_pool_info` |
| **Datasource** | `list_datasources`, `datasource_info`, `test_datasource` |
| **JDBC** | `list_jdbc_resources`, `jdbc_resource_info`, `flush_connection_pool` |
| **JMS** | `list_jms_servers`, `list_jms_destinations`, `purge_jms_queue` |
| **Domain** | `list_domains`, `domain_info` |
| **Cluster** | `list_clusters`, `cluster_info` |
| **Security** | `list_users`, `list_roles`, `create_user`, `remove_user` |
| **JMX** | `list_mbeans`, `get_mbean_attribute`, `set_mbean_attribute` |
| **Resources** | `mcp://models`, `mcp://platform/status` |
| **Prompts** | `chat` (general-purpose conversation template) |
| **Admin API** | Server registry, tool catalog, sessions, audit log, policy management |

### 1.2 Architecture

```
MCP Client (Claude, IDE, Agent)
    |
    +-- POST /ai-platform/mcp          -> JSON-RPC 2.0 request/response
    +-- GET  /ai-platform/mcp (SSE)    -> Server event stream
    +-- GET  /ai-platform/mcp/health   -> Health check
         |
    +----+----------------------------+
    |   McpServer (JSON-RPC)          |
    |   +-- ToolRegistry (40 tools)   |
    |   +-- ResourceRegistry          |
    |   +-- PromptRegistry            |
    |   +-- SessionManager            |
    |   +-- AuditLog                  |
    |   +-- PolicyEnforcer            |
    +----+----------------------------+
         |
    +----+----------------------------+
    |   AI Platform + Admin CLI       |
    |   +-- AiGatewayService          |
    |   +-- AiModelRegistryService    |
    |   +-- AdminClient (72 commands) |
    +----+----------------------------+
```

---

## 2. Quick Start

### 2.1 Start Server

```bash
cd /path/to/velo
bin/build.sh -p
bin/start.sh
# or
java -jar was-bootstrap/target/was-bootstrap-0.5.11-jar-with-dependencies.jar
```

Look for this log line:
```
MCP server installed at /ai-platform/mcp (admin at /ai-platform/mcp/admin/*) with full admin CLI tools
```

### 2.2 Health Check

```bash
curl http://localhost:8080/ai-platform/mcp/health
```

```json
{
  "status": "UP",
  "server": "velo-mcp",
  "version": "velo-was-node-1",
  "protocol": "2024-11-05",
  "sessions": 0,
  "tools": 40,
  "resources": 2,
  "prompts": 1
}
```

### 2.3 Test Console

Open `http://localhost:8080/mcp-test/` in a browser for an interactive web-based MCP test console that communicates with the MCP server at `/ai-platform/mcp`.

---

## 3. User Guide — MCP Protocol API

### 3.1 Connection Flow

```
1. Client -> POST /ai-platform/mcp  { initialize }            -> Server
2. Server -> 200 OK     { capabilities }           -> Client  (Mcp-Session-Id header)
3. Client -> POST /ai-platform/mcp  { notifications/initialized } -> Server  (204 No Content)
4. Client -> POST /ai-platform/mcp  { tools/list }             -> Server
5. Client -> POST /ai-platform/mcp  { tools/call }             -> Server
6. (optional) Client -> GET /ai-platform/mcp (SSE)             -> Server events
```

### 3.2 initialize

```bash
curl -X POST http://localhost:8080/ai-platform/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": { "name": "my-client", "version": "1.0" }
    }
  }'
```

The response includes `Mcp-Session-Id` header. Include it in all subsequent requests.

### 3.3 notifications/initialized

```bash
curl -X POST http://localhost:8080/ai-platform/mcp \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
```

Response: `204 No Content`

### 3.4 tools/list

```bash
curl -X POST http://localhost:8080/ai-platform/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'
```

Returns all 40 tools with their input schemas.

### 3.5 tools/call — AI Tools

```bash
# infer
curl -X POST http://localhost:8080/ai-platform/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call",
       "params":{"name":"infer","arguments":{"prompt":"Explain quantum computing","requestType":"CHAT"}}}'

# route
curl -X POST http://localhost:8080/ai-platform/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":4,"method":"tools/call",
       "params":{"name":"route","arguments":{"prompt":"Recommend a movie","requestType":"AUTO"}}}'
```

### 3.6 tools/call — WAS Admin CLI Tools

```bash
# Server status
curl -X POST http://localhost:8080/ai-platform/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"server_info","arguments":{"serverName":"velo-was"}}}'

# List applications
curl -X POST http://localhost:8080/ai-platform/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"list_applications","arguments":{}}}'

# Memory info
curl -X POST http://localhost:8080/ai-platform/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"memory_info","arguments":{}}}'

# Set log level
curl -X POST http://localhost:8080/ai-platform/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"set_log_level","arguments":{"loggerName":"io.velo.was","level":"DEBUG"}}}'

# Deploy WAR
curl -X POST http://localhost:8080/ai-platform/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"deploy_application","arguments":{"warPath":"/path/to/app.war"}}}'
```

### 3.7 Complete Admin CLI Tool Reference

| Category | Tool | Parameters | Description |
|----------|------|-----------|-------------|
| **Server** | `list_servers` | — | List server instances |
| | `server_info` | serverName? | Server status detail |
| | `restart_server` | serverName | Restart server |
| **Application** | `list_applications` | — | List deployed apps |
| | `application_info` | appName | App detail (servlets, filters) |
| | `deploy_application` | warPath, contextPath? | Deploy WAR |
| | `undeploy_application` | appName | Undeploy app |
| | `redeploy_application` | appName | Redeploy app |
| **Monitoring** | `system_info` | — | OS, CPU, architecture |
| | `jvm_info` | — | JVM name, version, uptime |
| | `memory_info` | — | Heap / non-heap memory |
| | `thread_info` | — | Thread count, deadlock detection |
| | `resource_overview` | — | Full resource summary |
| **Logging** | `list_loggers` | — | Loggers with levels |
| | `get_log_level` | loggerName | Current log level |
| | `set_log_level` | loggerName, level | Change log level |
| **Thread Pool** | `list_thread_pools` | — | Thread pool status |
| | `thread_pool_info` | poolName | Pool detail |
| **Datasource** | `list_datasources` | — | Datasource list |
| | `datasource_info` | datasourceName | Datasource detail |
| | `test_datasource` | datasourceName | Connectivity test |
| **JDBC** | `list_jdbc_resources` | — | JDBC resource list |
| | `jdbc_resource_info` | resourceName | JDBC detail |
| | `flush_connection_pool` | poolName | Flush pool |
| **JMS** | `list_jms_servers` | — | JMS server list |
| | `list_jms_destinations` | — | Queue/topic list |
| | `purge_jms_queue` | queueName | Purge queue messages |
| **Domain** | `list_domains` | — | Domain list |
| | `domain_info` | domainName? | Domain detail |
| **Cluster** | `list_clusters` | — | Cluster list |
| | `cluster_info` | clusterName | Cluster detail |
| **Security** | `list_users` | — | User list |
| | `list_roles` | — | Role list |
| | `create_user` | username, password | Create user |
| | `remove_user` | username | Remove user |
| **JMX** | `list_mbeans` | — | MBean list |
| | `get_mbean_attribute` | mbeanName, attribute | Get attribute |
| | `set_mbean_attribute` | mbeanName, attribute, value | Set attribute |

### 3.8 resources/list & resources/read

```bash
curl -X POST http://localhost:8080/ai-platform/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":10,"method":"resources/list"}'

curl -X POST http://localhost:8080/ai-platform/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":11,"method":"resources/read","params":{"uri":"mcp://models"}}'

curl -X POST http://localhost:8080/ai-platform/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":12,"method":"resources/read","params":{"uri":"mcp://platform/status"}}'
```

### 3.9 prompts/list & prompts/get

```bash
curl -X POST http://localhost:8080/ai-platform/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":13,"method":"prompts/list"}'

curl -X POST http://localhost:8080/ai-platform/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":14,"method":"prompts/get","params":{"name":"chat","arguments":{"task":"Explain microservices","language":"English"}}}'
```

### 3.10 SSE Stream

```bash
curl -N -H "Accept: text/event-stream" http://localhost:8080/ai-platform/mcp
```

The server sends an `endpoint` event immediately, then `: ping` comments every 25 seconds. Add `Mcp-Session-Id` header to link the stream to a session.

---

## 4. Administrator Guide — Admin Control Plane API

All admin APIs are under `/ai-platform/mcp/admin/*`.

| Endpoint | Methods | Description |
|----------|---------|-------------|
| `/ai-platform/mcp/admin/servers` | GET, POST | MCP server instance management |
| `/ai-platform/mcp/admin/tools` | GET, PUT | Tool catalog (PUT to block/unblock) |
| `/ai-platform/mcp/admin/resources` | GET | Resource catalog |
| `/ai-platform/mcp/admin/prompts` | GET | Prompt catalog |
| `/ai-platform/mcp/admin/sessions` | GET | Active session listing |
| `/ai-platform/mcp/admin/audit` | GET | Audit log query (?limit=N&method=X) |
| `/ai-platform/mcp/admin/policies` | GET, PUT | Auth/rate-limit/block policy CRUD |

### 4.1 Server Registry

```bash
curl http://localhost:8080/ai-platform/mcp/admin/servers

curl -X POST http://localhost:8080/ai-platform/mcp/admin/servers \
  -H "Content-Type: application/json" \
  -d '{"name":"mcp-prod-1","endpoint":"https://prod.example.com/mcp","environment":"production","version":"0.5.11"}'
```

### 4.2 Tool Block/Unblock

```bash
curl -X PUT http://localhost:8080/ai-platform/mcp/admin/tools \
  -H "Content-Type: application/json" \
  -d '{"name":"infer","action":"block"}'

curl -X PUT http://localhost:8080/ai-platform/mcp/admin/tools \
  -H "Content-Type: application/json" \
  -d '{"name":"infer","action":"unblock"}'
```

### 4.3 Sessions

```bash
curl http://localhost:8080/ai-platform/mcp/admin/sessions
```

### 4.4 Audit Log

```bash
curl "http://localhost:8080/ai-platform/mcp/admin/audit?limit=20&method=tools/call"
```

### 4.5 Policy Management

```bash
# View current policies
curl http://localhost:8080/ai-platform/mcp/admin/policies

# Set rate limit (60 req/min)
curl -X PUT http://localhost:8080/ai-platform/mcp/admin/policies \
  -H "Content-Type: application/json" \
  -d '{"rateLimitPerMinute":60}'

# Block prompt patterns
curl -X PUT http://localhost:8080/ai-platform/mcp/admin/policies \
  -H "Content-Type: application/json" \
  -d '{"blockedPromptPatterns":["(?i)password|secret|credential"]}'

# Enable authentication
curl -X PUT http://localhost:8080/ai-platform/mcp/admin/policies \
  -H "Content-Type: application/json" \
  -d '{"authRequired":true,"apiKeyHeader":"X-Api-Key"}'
```

---

## 5. Error Code Reference

| Code | Name | Description |
|------|------|-------------|
| -32700 | Parse error | Malformed JSON |
| -32600 | Invalid Request | Bad JSON-RPC message |
| -32601 | Method not found | Unsupported method |
| -32602 | Invalid params | Missing/bad parameters |
| -32603 | Internal error | Server-side error |
| -32001 | Tool not found | Unknown tool name |
| -32002 | Resource not found | Unknown resource URI |
| -32003 | Prompt not found | Unknown prompt name |
| -32010 | Quota exceeded | Rate limit hit |
| -32011 | Model unavailable | AI model not available |
| -32012 | Unsafe prompt blocked | Content policy violation |
| -32013 | Client blocked | Client name blocked |

---

## 6. Configuration (server.yaml)

MCP server activates automatically when `aiPlatform.enabled: true`.

```yaml
server:
  aiPlatform:
    enabled: true
    serving:
      models:
        - name: llm-general
          category: LLM
          provider: builtin
          version: v1
          latencyMs: 650
          accuracyScore: 86
          defaultSelected: true
          enabled: true
```

---

## 7. Client Integration Examples

### Claude Desktop (claude_desktop_config.json)

```json
{
  "mcpServers": {
    "velo-ai": {
      "url": "http://localhost:8080/ai-platform/mcp",
      "transport": "streamable-http"
    }
  }
}
```

### Python (httpx)

```python
import httpx

base = "http://localhost:8080/ai-platform/mcp"
headers = {"Content-Type": "application/json"}

resp = httpx.post(base, headers=headers, json={
    "jsonrpc": "2.0", "id": 1, "method": "initialize",
    "params": {"protocolVersion": "2024-11-05",
               "clientInfo": {"name": "python-client", "version": "1.0"}}
})
session_id = resp.headers.get("Mcp-Session-Id")
headers["Mcp-Session-Id"] = session_id

resp = httpx.post(base, headers=headers, json={
    "jsonrpc": "2.0", "id": 2, "method": "tools/call",
    "params": {"name": "memory_info", "arguments": {}}
})
print(resp.json())
```

---

## 8. Endpoint Summary

| Path | Method | Description |
|------|--------|-------------|
| `/ai-platform/mcp` | POST | JSON-RPC 2.0 requests |
| `/ai-platform/mcp` | GET (SSE) | Server-Sent Events (Accept: text/event-stream) |
| `/ai-platform/mcp/health` | GET | Liveness / readiness |
| `/ai-platform/mcp/admin/servers` | GET, POST | MCP server registry |
| `/ai-platform/mcp/admin/tools` | GET, PUT | Tool catalog & block control |
| `/ai-platform/mcp/admin/resources` | GET | Resource catalog |
| `/ai-platform/mcp/admin/prompts` | GET | Prompt catalog |
| `/ai-platform/mcp/admin/sessions` | GET | Active sessions |
| `/ai-platform/mcp/admin/audit` | GET | Audit log |
| `/ai-platform/mcp/admin/policies` | GET, PUT | Policy management |
