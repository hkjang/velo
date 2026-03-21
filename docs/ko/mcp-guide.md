# Velo MCP Server — 사용자 / 관리자 가이드

> **버전**: 0.5.10
> **프로토콜**: MCP (Model Context Protocol) over Streamable HTTP
> **전송 규격**: JSON-RPC 2.0 + SSE (Server-Sent Events)

---

## 1. 개요

Velo MCP Server는 Velo WAS에 내장된 **Streamable HTTP MCP 서버**입니다.
외부 MCP 클라이언트 (Claude Desktop, IDE 플러그인, 커스텀 에이전트 등)가 Velo AI 플랫폼의 기능을 표준 MCP 프로토콜로 호출할 수 있게 합니다.

### 1.1 제공 기능

| 영역 | 기능 |
|------|------|
| **AI Tools** | `infer` (AI 추론 실행), `route` (라우팅 결정 조회) |
| **Server Tools** | `list_servers`, `server_info`, `restart_server` |
| **App Tools** | `list_applications`, `application_info`, `deploy_application`, `undeploy_application`, `redeploy_application` |
| **Monitoring Tools** | `system_info`, `jvm_info`, `memory_info`, `thread_info`, `resource_overview` |
| **Logging Tools** | `list_loggers`, `get_log_level`, `set_log_level` |
| **Thread Pool Tools** | `list_thread_pools`, `thread_pool_info` |
| **Datasource Tools** | `list_datasources`, `datasource_info`, `test_datasource` |
| **JDBC Tools** | `list_jdbc_resources`, `jdbc_resource_info`, `flush_connection_pool` |
| **JMS Tools** | `list_jms_servers`, `list_jms_destinations`, `purge_jms_queue` |
| **Domain Tools** | `list_domains`, `domain_info` |
| **Cluster Tools** | `list_clusters`, `cluster_info` |
| **Security Tools** | `list_users`, `list_roles`, `create_user`, `remove_user` |
| **JMX Tools** | `list_mbeans`, `get_mbean_attribute`, `set_mbean_attribute` |
| **Resources** | `mcp://models` (모델 목록), `mcp://platform/status` (플랫폼 상태) |
| **Prompts** | `chat` (범용 대화 프롬프트 템플릿) |
| **Admin API** | 서버 관리, 툴/리소스/프롬프트 카탈로그, 세션 조회, 감사 로그, 정책 관리 |

### 1.2 아키텍처

```
MCP Client (Claude, IDE, Agent)
    │
    ├── POST /mcp          → JSON-RPC 2.0 요청/응답
    ├── GET  /mcp (SSE)    → 서버 이벤트 스트림
    └── GET  /mcp/health   → 상태 확인
         │
    ┌────┴────────────────────────┐
    │   McpServer (JSON-RPC)     │
    │   ├── ToolRegistry          │
    │   ├── ResourceRegistry      │
    │   ├── PromptRegistry        │
    │   ├── SessionManager        │
    │   ├── AuditLog              │
    │   └── PolicyEnforcer        │
    └────┬────────────────────────┘
         │
    ┌────┴────────────────────────┐
    │   AI Platform               │
    │   ├── AiGatewayService      │
    │   ├── AiModelRegistryService│
    │   └── (모델 라우팅/추론)     │
    └─────────────────────────────┘
```

---

## 2. 빠른 시작

### 2.1 서버 시작

```bash
# 빌드
cd D:/project/velo
bin/build.sh -p

# 서버 시작
bin/start.sh
# 또는
java -jar was-bootstrap/target/was-bootstrap-0.5.10-jar-with-dependencies.jar
```

서버 시작 후 로그에서 확인:
```
MCP server installed at /mcp (admin at /mcp/admin/*)
```

### 2.2 Health 확인

```bash
curl http://localhost:8080/mcp/health
```

응답 예시:
```json
{
  "status": "UP",
  "server": "velo-mcp",
  "version": "velo-was-node-1",
  "protocol": "2024-11-05",
  "sessions": 0,
  "tools": 2,
  "resources": 2,
  "prompts": 1
}
```

### 2.3 테스트 콘솔

브라우저에서 `http://localhost:8080/mcp-test/` 접속 시 웹 기반 MCP 테스트 콘솔을 사용할 수 있습니다.

---

## 3. 사용자 가이드 — MCP 프로토콜 API

### 3.1 연결 흐름

```
1. Client → POST /mcp  { initialize }      → Server
2. Server → 200 OK     { capabilities }     → Client  (Mcp-Session-Id 헤더 포함)
3. Client → POST /mcp  { notifications/initialized }  → Server  (204 No Content)
4. Client → POST /mcp  { tools/list }       → Server
5. Client → POST /mcp  { tools/call }       → Server
6. (선택) Client → GET /mcp (SSE)           → 서버 이벤트 수신
```

### 3.2 initialize — 연결 초기화

**요청:**
```bash
curl -X POST http://localhost:8080/mcp \
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

**응답:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": { "listChanged": false },
      "resources": { "subscribe": false, "listChanged": false },
      "prompts": { "listChanged": false }
    },
    "serverInfo": { "name": "velo-mcp", "version": "velo-was-node-1" }
  }
}
```

응답 헤더에 `Mcp-Session-Id`가 포함됩니다. 이후 모든 요청에 이 헤더를 포함해야 합니다.

### 3.3 notifications/initialized — 초기화 완료 알림

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
```

응답: `204 No Content` (알림이므로 본문 없음)

### 3.4 ping — 서버 상태 확인

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"ping"}'
```

응답: `{"jsonrpc":"2.0","id":2,"result":{}}`

### 3.5 tools/list — 사용 가능 도구 조회

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/list"}'
```

**응답 예시:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "tools": [
      {
        "name": "infer",
        "description": "Run AI inference through the Velo gateway...",
        "inputSchema": {
          "type": "object",
          "properties": {
            "prompt": { "type": "string", "description": "Input text prompt for inference" },
            "requestType": { "type": "string", "enum": ["CHAT","VISION","RECOMMENDATION","AUTO"] },
            "sessionId": { "type": "string", "description": "Optional session ID for context cache" }
          },
          "required": ["prompt"]
        }
      },
      {
        "name": "route",
        "description": "Resolve the AI gateway routing decision...",
        "inputSchema": { ... }
      }
    ]
  }
}
```

### 3.6 tools/call — 도구 실행

#### infer 도구 호출

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Mcp-Session-Id: <session-id>" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
      "name": "infer",
      "arguments": {
        "prompt": "양자 컴퓨팅을 한 문단으로 설명해줘",
        "requestType": "CHAT"
      }
    }
  }'
```

**응답:**
```json
{
  "jsonrpc": "2.0",
  "id": 4,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Conversation route selected. Model llm-general will handle..."
      }
    ],
    "isError": false
  }
}
```

#### route 도구 호출

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 5,
    "method": "tools/call",
    "params": {
      "name": "route",
      "arguments": {
        "prompt": "이 사진에 무엇이 있어?",
        "requestType": "AUTO"
      }
    }
  }'
```

### 3.7 resources/list — 리소스 목록 조회

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":6,"method":"resources/list"}'
```

### 3.8 resources/read — 리소스 읽기

```bash
# 모델 목록 조회
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 7,
    "method": "resources/read",
    "params": { "uri": "mcp://models" }
  }'

# 플랫폼 상태 조회
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 8,
    "method": "resources/read",
    "params": { "uri": "mcp://platform/status" }
  }'
```

### 3.9 prompts/list — 프롬프트 템플릿 목록

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":9,"method":"prompts/list"}'
```

### 3.10 prompts/get — 프롬프트 렌더링

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 10,
    "method": "prompts/get",
    "params": {
      "name": "chat",
      "arguments": {
        "task": "마이크로서비스 아키텍처의 장단점을 정리해줘",
        "language": "Korean"
      }
    }
  }'
```

### 3.11 SSE 스트림 연결

```bash
curl -N -H "Accept: text/event-stream" http://localhost:8080/mcp
```

서버는 즉시 `endpoint` 이벤트를 보내고, 25초 간격으로 `: ping` 주석을 전송합니다.
세션과 연결하려면 `Mcp-Session-Id` 헤더를 추가합니다.

---

## 3.12 Admin CLI 도구 — WAS 운영 관리

MCP 클라이언트에서 Velo WAS의 전체 운영 기능을 도구로 호출할 수 있습니다.
총 **37개 Admin CLI 도구**가 12개 카테고리로 등록됩니다.

### 서버 관리

```bash
# 서버 목록
curl -X POST http://localhost:8080/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"list_servers","arguments":{}}}'

# 서버 상태 상세
curl -X POST http://localhost:8080/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"server_info","arguments":{"serverName":"velo-was"}}}'
```

### 애플리케이션 관리

```bash
# 배포된 앱 목록
curl -X POST http://localhost:8080/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_applications","arguments":{}}}'

# WAR 배포
curl -X POST http://localhost:8080/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"deploy_application","arguments":{"warPath":"/path/to/app.war","contextPath":"/myapp"}}}'

# 언디플로이
curl -X POST http://localhost:8080/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"undeploy_application","arguments":{"appName":"myapp"}}}'
```

### 시스템 모니터링

```bash
# OS 정보
curl -X POST http://localhost:8080/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"system_info","arguments":{}}}'

# JVM 정보
curl -X POST http://localhost:8080/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"jvm_info","arguments":{}}}'

# 메모리 사용량
curl -X POST http://localhost:8080/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"memory_info","arguments":{}}}'

# 스레드 상태
curl -X POST http://localhost:8080/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"thread_info","arguments":{}}}'
```

### 로그 관리

```bash
# 로거 목록
curl -X POST http://localhost:8080/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"list_loggers","arguments":{}}}'

# 로그 레벨 변경
curl -X POST http://localhost:8080/mcp -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":11,"method":"tools/call","params":{"name":"set_log_level","arguments":{"loggerName":"io.velo.was.mcp","level":"DEBUG"}}}'
```

### 전체 Admin CLI 도구 목록

| 카테고리 | 도구명 | 파라미터 | 설명 |
|---------|--------|---------|------|
| **Server** | `list_servers` | (없음) | 서버 인스턴스 목록 |
| | `server_info` | serverName? | 서버 상태 상세 |
| | `restart_server` | serverName | 서버 재시작 |
| **Application** | `list_applications` | (없음) | 배포된 앱 목록 |
| | `application_info` | appName | 앱 상세 (서블릿/필터 수) |
| | `deploy_application` | warPath, contextPath? | WAR 배포 |
| | `undeploy_application` | appName | 앱 제거 |
| | `redeploy_application` | appName | 앱 재배포 |
| **Monitoring** | `system_info` | (없음) | OS, CPU, 아키텍처 |
| | `jvm_info` | (없음) | JVM 이름/버전/가동시간 |
| | `memory_info` | (없음) | 힙/논힙 메모리 사용량 |
| | `thread_info` | (없음) | 스레드 수/데드락 감지 |
| | `resource_overview` | (없음) | 전체 리소스 현황 |
| **Logging** | `list_loggers` | (없음) | 로거 목록 + 레벨 |
| | `get_log_level` | loggerName | 로거 현재 레벨 |
| | `set_log_level` | loggerName, level | 로그 레벨 변경 |
| **Thread Pool** | `list_thread_pools` | (없음) | 스레드 풀 현황 |
| | `thread_pool_info` | poolName | 스레드 풀 상세 |
| **Datasource** | `list_datasources` | (없음) | 데이터소스 목록 |
| | `datasource_info` | datasourceName | 데이터소스 상세 |
| | `test_datasource` | datasourceName | 연결 테스트 |
| **JDBC** | `list_jdbc_resources` | (없음) | JDBC 리소스 목록 |
| | `jdbc_resource_info` | resourceName | JDBC 상세 |
| | `flush_connection_pool` | poolName | 커넥션 풀 초기화 |
| **JMS** | `list_jms_servers` | (없음) | JMS 서버 목록 |
| | `list_jms_destinations` | (없음) | JMS 큐/토픽 목록 |
| | `purge_jms_queue` | queueName | 큐 메시지 전체 삭제 |
| **Domain** | `list_domains` | (없음) | 도메인 목록 |
| | `domain_info` | domainName? | 도메인 상세 |
| **Cluster** | `list_clusters` | (없음) | 클러스터 목록 |
| | `cluster_info` | clusterName | 클러스터 상세 |
| **Security** | `list_users` | (없음) | 사용자 목록 |
| | `list_roles` | (없음) | 역할 목록 |
| | `create_user` | username, password | 사용자 생성 |
| | `remove_user` | username | 사용자 삭제 |
| **JMX** | `list_mbeans` | (없음) | MBean 목록 |
| | `get_mbean_attribute` | mbeanName, attribute | MBean 속성 조회 |
| | `set_mbean_attribute` | mbeanName, attribute, value | MBean 속성 설정 |

---

## 4. 관리자 가이드 — Admin API

모든 Admin API는 `/mcp/admin/*` 경로에 있습니다.

### 4.1 서버 관리

#### 서버 목록 조회
```bash
curl http://localhost:8080/mcp/admin/servers
```

#### 원격 서버 등록
```bash
curl -X POST http://localhost:8080/mcp/admin/servers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "mcp-prod-1",
    "endpoint": "https://prod-1.example.com/mcp",
    "environment": "production",
    "version": "0.5.10"
  }'
```

### 4.2 툴 카탈로그 관리

#### 등록된 툴 조회
```bash
curl http://localhost:8080/mcp/admin/tools
```

#### 툴 차단 (block)
```bash
curl -X PUT http://localhost:8080/mcp/admin/tools \
  -H "Content-Type: application/json" \
  -d '{"name": "infer", "action": "block"}'
```

차단된 툴은 `tools/call` 시 정책 거부 오류를 반환합니다.

#### 툴 차단 해제 (unblock)
```bash
curl -X PUT http://localhost:8080/mcp/admin/tools \
  -H "Content-Type: application/json" \
  -d '{"name": "infer", "action": "unblock"}'
```

### 4.3 리소스 관리

```bash
curl http://localhost:8080/mcp/admin/resources
```

### 4.4 프롬프트 관리

```bash
curl http://localhost:8080/mcp/admin/prompts
```

### 4.5 세션 조회

```bash
curl http://localhost:8080/mcp/admin/sessions
```

**응답 예시:**
```json
{
  "activeSessions": 1,
  "sessions": [
    {
      "id": "a1b2c3...",
      "clientName": "my-client",
      "clientVersion": "1.0",
      "protocolVersion": "2024-11-05",
      "initialized": true,
      "hasSseConnection": false,
      "createdAt": "2026-03-21T03:50:00Z",
      "lastActivityAt": "2026-03-21T03:50:05Z"
    }
  ]
}
```

### 4.6 감사 로그 조회

```bash
# 최근 50건 조회
curl http://localhost:8080/mcp/admin/audit

# 필터링: 최근 20건, tools/call 메서드만
curl "http://localhost:8080/mcp/admin/audit?limit=20&method=tools/call"
```

**응답 예시:**
```json
{
  "total": 42,
  "returned": 5,
  "entries": [
    {
      "timestamp": "2026-03-21T03:50:05Z",
      "sessionId": "a1b2c3...",
      "clientName": "my-client",
      "method": "tools/call",
      "toolName": "infer",
      "durationMs": 12,
      "success": true,
      "errorCode": 0,
      "errorMsg": null,
      "remoteAddr": "/127.0.0.1:52341"
    }
  ]
}
```

### 4.7 정책 관리

#### 현재 정책 조회
```bash
curl http://localhost:8080/mcp/admin/policies
```

**응답:**
```json
{
  "authRequired": false,
  "apiKeyHeader": "X-Api-Key",
  "rateLimitPerMinute": 0,
  "maxConcurrentToolCalls": 0,
  "blockedTools": [],
  "blockedPromptPatterns": [],
  "blockedClients": [],
  "dataMaskingPatterns": []
}
```

#### 정책 업데이트

```bash
# Rate limit 설정 (분당 60회)
curl -X PUT http://localhost:8080/mcp/admin/policies \
  -H "Content-Type: application/json" \
  -d '{"rateLimitPerMinute": 60}'

# 특정 프롬프트 패턴 차단
curl -X PUT http://localhost:8080/mcp/admin/policies \
  -H "Content-Type: application/json" \
  -d '{"blockedPromptPatterns": ["(?i)password|secret|credential"]}'

# 클라이언트 차단
curl -X PUT http://localhost:8080/mcp/admin/policies \
  -H "Content-Type: application/json" \
  -d '{"blockedClients": [".*malicious.*"]}'

# 인증 활성화
curl -X PUT http://localhost:8080/mcp/admin/policies \
  -H "Content-Type: application/json" \
  -d '{"authRequired": true, "apiKeyHeader": "X-Api-Key"}'
```

---

## 5. 오류 코드 참조

| 코드 | 이름 | 설명 |
|------|------|------|
| -32700 | Parse error | JSON 파싱 오류 |
| -32600 | Invalid Request | 잘못된 JSON-RPC 요청 |
| -32601 | Method not found | 지원되지 않는 메서드 |
| -32602 | Invalid params | 파라미터 오류 |
| -32603 | Internal error | 서버 내부 오류 |
| -32001 | Tool not found | 존재하지 않는 툴 |
| -32002 | Resource not found | 존재하지 않는 리소스 |
| -32003 | Prompt not found | 존재하지 않는 프롬프트 |
| -32010 | Quota exceeded | Rate limit 초과 |
| -32011 | Model unavailable | AI 모델 사용 불가 |
| -32012 | Unsafe prompt blocked | 차단 정책에 의해 프롬프트 거부 |
| -32013 | Client blocked | 클라이언트 차단됨 |

---

## 6. 엔드포인트 전체 목록

| 경로 | 메서드 | 설명 |
|------|--------|------|
| `/mcp` | POST | JSON-RPC 2.0 요청 수신 |
| `/mcp` | GET (SSE) | 서버 이벤트 스트림 (Accept: text/event-stream) |
| `/mcp/health` | GET | 서버 상태 (liveness/readiness) |
| `/mcp/admin/servers` | GET, POST | MCP 서버 인스턴스 관리 |
| `/mcp/admin/tools` | GET, PUT | 툴 카탈로그 및 차단 관리 |
| `/mcp/admin/resources` | GET | 리소스 카탈로그 조회 |
| `/mcp/admin/prompts` | GET | 프롬프트 카탈로그 조회 |
| `/mcp/admin/sessions` | GET | 활성 세션 목록 |
| `/mcp/admin/audit` | GET | 감사 로그 (쿼리 파라미터: limit, method) |
| `/mcp/admin/policies` | GET, PUT | 인증/인가/차단 정책 관리 |

---

## 7. 설정 (server.yaml)

MCP 서버는 `aiPlatform.enabled: true`일 때 자동으로 활성화됩니다.

```yaml
server:
  aiPlatform:
    enabled: true        # true이면 MCP 서버 자동 활성화
    serving:
      models:            # MCP tools/call에서 라우팅되는 모델 목록
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

## 8. MCP 클라이언트 연동 예시

### Claude Desktop (claude_desktop_config.json)

```json
{
  "mcpServers": {
    "velo-ai": {
      "url": "http://localhost:8080/mcp",
      "transport": "streamable-http"
    }
  }
}
```

### Python 클라이언트 (httpx)

```python
import httpx

base = "http://localhost:8080/mcp"
headers = {"Content-Type": "application/json"}

# Initialize
resp = httpx.post(base, headers=headers, json={
    "jsonrpc": "2.0", "id": 1, "method": "initialize",
    "params": {"protocolVersion": "2024-11-05",
               "clientInfo": {"name": "python-client", "version": "1.0"}}
})
session_id = resp.headers.get("Mcp-Session-Id")
headers["Mcp-Session-Id"] = session_id

# Call tool
resp = httpx.post(base, headers=headers, json={
    "jsonrpc": "2.0", "id": 2, "method": "tools/call",
    "params": {"name": "infer", "arguments": {"prompt": "Hello!"}}
})
print(resp.json())
```

---

## 9. 테스트 체크리스트

- [ ] `/mcp/health` 200 OK 확인
- [ ] `initialize` → `Mcp-Session-Id` 헤더 수신 확인
- [ ] `notifications/initialized` → 204 No Content 확인
- [ ] `tools/list` → 2개 툴 (infer, route) 반환 확인
- [ ] `tools/call infer` → text content 반환 확인
- [ ] `tools/call route` → 라우팅 결정 반환 확인
- [ ] `resources/list` → 2개 리소스 반환 확인
- [ ] `resources/read mcp://models` → 모델 JSON 반환 확인
- [ ] `prompts/list` → 1개 프롬프트 반환 확인
- [ ] `prompts/get chat` → 메시지 배열 반환 확인
- [ ] SSE 연결 → endpoint 이벤트 수신 확인
- [ ] Admin servers → 로컬 서버 1개 등록 확인
- [ ] Admin tools → PUT block/unblock 동작 확인
- [ ] Admin sessions → 세션 상세 정보 확인
- [ ] Admin audit → 호출 이력 기록 확인
- [ ] Admin policies → PUT 정책 변경 후 반영 확인
