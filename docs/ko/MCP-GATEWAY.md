# Velo WAS — MCP Gateway 아키텍처

## 개요

Velo WAS의 MCP(Model Context Protocol) Gateway는 3계층 MCP 통합을 제공합니다:

1. **빌트인 MCP 서버** — WAS에 내장된 AI Platform 도구/리소스/프롬프트
2. **Remote MCP 프록시** — 외부 MCP 서버에 연결하여 도구/리소스를 통합 제공
3. **App MCP 모니터링** — 배포된 WAR 앱의 MCP 트래픽을 중앙에서 감시

클라이언트는 단일 엔드포인트(`/ai-platform/mcp`)를 통해 모든 MCP 기능에 접근합니다.

## 아키텍처

```
                         ┌─────────────────┐
                         │   MCP Client    │
                         │ (Claude Desktop,│
                         │  IDE Plugin 등) │
                         └────────┬────────┘
                                  │
                    POST /ai-platform/mcp
                    GET  /ai-platform/mcp (SSE)
                                  │
                         ┌────────▼────────┐
                         │  McpPostHandler  │
                         │  McpSseHandler   │
                         └────────┬────────┘
                                  │
                         ┌────────▼────────┐
                         │    McpServer     │
                         │  (JSON-RPC 2.0) │
                         └────────┬────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              │                   │                   │
     ┌────────▼────────┐ ┌───────▼────────┐ ┌────────▼────────┐
     │   Local Tools   │ │ Gateway Router │ │  App MCP Monitor │
     │ (McpToolRegistry│ │(McpGatewayRouter│ │(McpAppTraffic   │
     │  infer, route,  │ │  remote proxy) │ │  Interceptor)   │
     │  admin CLI...)  │ └───────┬────────┘ └─────────────────┘
     └─────────────────┘         │
                        ┌────────┼────────┐
                        │        │        │
               ┌────────▼──┐ ┌──▼──────┐ ┌▼──────────┐
               │ Remote A  │ │Remote B │ │ Remote C  │
               │(MCP Server│ │         │ │           │
               │ endpoint) │ │         │ │           │
               └───────────┘ └─────────┘ └───────────┘
```

## 1. 빌트인 MCP 서버

### 엔드포인트
| 경로 | 메서드 | 설명 |
|------|--------|------|
| `/ai-platform/mcp` | POST | JSON-RPC 2.0 요청 |
| `/ai-platform/mcp` | GET (SSE) | Server-Sent Events 스트림 |
| `/ai-platform/mcp/health` | GET | 서버 상태 확인 |

### 지원 메서드
- `initialize` — 클라이언트 핸드셰이크, 세션 생성
- `tools/list` — 로컬 + 원격 도구 통합 목록
- `tools/call` — 도구 호출 (로컬 또는 원격으로 라우팅)
- `resources/list` — 로컬 + 원격 리소스 통합 목록
- `resources/read` — 리소스 읽기
- `prompts/list` — 로컬 + 원격 프롬프트 통합 목록
- `prompts/get` — 프롬프트 렌더링
- `ping` — 헬스체크

### 빌트인 도구
- `infer` — AI 모델 추론 호출
- `route` — 의도 기반 모델 라우팅
- Admin CLI 도구 (서버 관리, 배포, 모니터링)

## 2. Remote MCP 프록시 (Gateway)

### 원격 서버 등록

AI Platform 대시보드 또는 Admin API로 원격 MCP 서버를 등록합니다:

```bash
curl -X POST /ai-platform/mcp/admin/servers \
  -H "Content-Type: application/json" \
  -d '{"name":"prod-tools","endpoint":"http://tools.internal:8080/mcp","environment":"remote","version":"1.0"}'
```

등록 시 자동으로:
1. `initialize` 핸드셰이크 수행
2. `tools/list`, `resources/list`, `prompts/list`로 capabilities 디스커버리
3. 60초 간격 헬스체크 시작

### 네이밍 규칙

원격 도구는 `serverId::toolName` 형태로 네임스페이스됩니다:

| 이름 | Origin | 설명 |
|------|--------|------|
| `infer` | local | 빌트인 추론 도구 |
| `abc-123::search` | prod-tools | 원격 서버의 search 도구 |
| `abc-123::mcp://docs` | prod-tools | 원격 서버의 docs 리소스 |

클라이언트가 `tools/call`을 호출할 때:
- 이름에 `::`가 없으면 → 로컬 도구 실행
- 이름에 `::`가 있으면 → 해당 원격 서버로 프록시

### Gateway Admin API

| 경로 | 메서드 | 설명 |
|------|--------|------|
| `/admin/gateway/status` | GET | 전체 연결 상태 |
| `/admin/gateway/connect` | POST | 원격 서버 수동 연결 |
| `/admin/gateway/disconnect` | POST | 원격 서버 연결 해제 |
| `/admin/gateway/refresh` | POST | capabilities 갱신 |
| `/admin/gateway/routing-table` | GET | 통합 라우팅 테이블 |

### 컴포넌트

- **McpRemoteClient** — `java.net.http.HttpClient` 기반 원격 MCP 통신
- **McpRemoteServerConnection** — 연결 상태 관리 + capabilities 캐시
- **McpGatewayRouter** — 중앙 라우팅 + 통합 + 헬스체크

## 3. App MCP 모니터링

배포된 WAR 애플리케이션이 MCP 엔드포인트를 노출하면, WAS가 자동으로 감지합니다.

### 동작 원리

1. **McpAppTrafficInterceptor** — 서블릿 필터가 모든 배포 앱에 자동 주입
2. POST + `application/json` + `jsonrpc: 2.0` → MCP 트래픽 감지
3. GET + `Accept: text/event-stream` → SSE 연결 추적
4. **McpAppGatewayService** — 중앙 게이트웨이에서 통합 모니터링

### App MCP Admin API

| 경로 | 메서드 | 설명 |
|------|--------|------|
| `/admin/app-endpoints` | GET | 발견된 앱 MCP 엔드포인트 |
| `/admin/app-sessions` | GET | 앱 MCP 세션 목록 |
| `/admin/app-traffic` | GET | 앱 MCP 감사 로그 |

### `/mcp` 컨텍스트 패스

`/mcp`는 WAS 빌트인에서 사용하지 않으므로, 앱이 WAR로 자유롭게 배포 가능합니다.
배포된 앱의 MCP 트래픽은 자동으로 중앙 게이트웨이에서 감시됩니다.

## 4. 감사 로그

모든 MCP 트래픽은 통합 감사 로그에 기록됩니다:

| 소스 | 메서드 형식 | 예시 |
|------|-----------|------|
| 로컬 | `tools/call` | 빌트인 도구 호출 |
| 게이트웨이 | `[gateway:serverId] tools/call` | 원격 프록시 |
| 앱 | `[/contextPath] tools/call` | 앱 MCP 트래픽 |

조회: `GET /ai-platform/mcp/admin/audit?limit=50`

## 5. 대시보드

AI Platform 대시보드(`/ai-platform`)에서 MCP 관리:

### MCP 서버 탭
- Health 상태 (세션/도구/리소스 수)
- 서버 목록 + 원격 서버 등록
- 도구 카탈로그 (block/unblock)
- 세션 모니터링
- 감사 로그 뷰어
- 정책 관리 (인증, Rate Limit, 차단)
- **게이트웨이 상태** (원격 서버 연결, capabilities)
- **라우팅 테이블** (로컬 + 원격 통합 뷰)

### 앱 MCP 모니터링 탭
- 발견된 앱 MCP 엔드포인트
- 앱 MCP 세션
- 앱 MCP 트래픽 로그

## 6. 설정 예시

```yaml
server:
  aiPlatform:
    enabled: true
    console:
      contextPath: /ai-platform
    # MCP Gateway는 aiPlatform 활성화 시 자동 구성
    # 원격 서버는 런타임에 Admin API로 등록
```

## 7. 주요 파일

| 파일 | 설명 |
|------|------|
| `was-mcp/.../McpServer.java` | 코어 JSON-RPC 디스패처 |
| `was-mcp/.../gateway/McpGatewayRouter.java` | 게이트웨이 라우터 |
| `was-mcp/.../gateway/McpRemoteClient.java` | 원격 MCP HTTP 클라이언트 |
| `was-mcp/.../gateway/McpRemoteServerConnection.java` | 원격 연결 관리 |
| `was-mcp/.../gateway/McpAppGatewayService.java` | 앱 MCP 모니터링 |
| `was-mcp/.../gateway/McpAppTrafficInterceptor.java` | 앱 MCP 트래픽 필터 |
| `was-mcp/.../admin/McpAdminHandler.java` | Admin API 핸들러 |
| `was-mcp/.../McpApplication.java` | 모듈 와이어링 팩토리 |
