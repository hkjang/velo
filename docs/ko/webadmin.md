# Velo Web Admin 가이드

Velo WAS에 내장된 웹 기반 관리 콘솔이다.
JEUS WebAdmin을 모델로 하여, CLI 73개 명령어를 100% 웹에서 실행할 수 있도록 설계되었다.

## 기본 접속 정보

| 항목 | 기본값 |
|------|--------|
| URL | `http://localhost:8080/admin` |
| 아이디 | `admin` |
| 비밀번호 | `admin` |
| Context Path | `/admin` (설정 변경 가능) |

> 서버 포트가 8080이 아닌 경우 `http://localhost:{포트}/admin`으로 접속한다.

## 설정

`conf/server.yaml`에서 Web Admin 활성화 여부와 Context Path를 설정한다.

```yaml
server:
  webAdmin:
    enabled: true          # false로 변경 시 Web Admin 비활성화
    contextPath: /admin    # 원하는 경로로 변경 가능
    apiDocsEnabled: true   # false로 변경 시 API 문서 비활성화
```

서버 기동 시 `webAdmin.enabled: true`이면 자동으로 해당 Context Path에 배포된다.

## 인증

- 로그인 페이지: `{contextPath}/login`
- 세션 기반 인증 (Jakarta Servlet HttpSession)
- 인증되지 않은 요청은 자동으로 로그인 페이지로 리다이렉트
- 로그인/로그아웃 이벤트는 감사 로그에 기록됨

## 페이지 구성

### 핵심 페이지

| 페이지 | 경로 | 설명 |
|--------|------|------|
| Dashboard | `/` | 서버 상태 요약, 힙 메모리/스레드 실시간 차트, SSE 연동 |
| Console | `/console` | CLI 명령어 웹 실행 (73개 전체 명령어 지원) |
| Scripts | `/scripts` | 다중 명령어 배치 스크립트 실행, 템플릿 제공 |

### 관리 페이지

| 페이지 | 경로 | 설명 |
|--------|------|------|
| Domain | `/domain` | 서버 설정 조회, YAML 원본 보기, Draft 방식 설정 변경 |
| Servers | `/servers` | 서버 인스턴스 상태, 스레드/메모리 실시간 갱신, 서버 제어 |
| Clusters | `/clusters` | 클러스터 목록 조회, 생성, Rolling Restart/Stop |
| Nodes | `/nodes` | 노드 인벤토리, OS/Java/CPU 정보, 서버 매핑 |
| Applications | `/applications` | 배포된 앱 목록, Deploy/Undeploy/Redeploy/Stop, WAR 업로드 |
| Resources | `/resources` | JDBC DataSource, JMS, JNDI, 메모리, 스레드 풀, TLS 인증서 |
| Security | `/security` | 사용자 관리 (생성/삭제/비밀번호 변경), 역할, 정책, 세션 |
| Monitoring | `/monitoring` | 실시간 메트릭, 힙 차트, 로거 레벨 관리, 감사 이벤트, 알림 |
| Diagnostics | `/diagnostics` | 스레드 덤프, 힙 덤프, 데드락 체크, JVM/시스템 정보 |
| History | `/history` | 변경 이력, 감사 로그, Draft 워크플로우 관리 |
| Settings | `/settings` | 콘솔 환경설정 (언어, 자동 새로고침, 테마), localStorage 저장 |
| API Docs | `/api-docs/ui` | OpenAPI 3.0 Swagger UI (조건부: `apiDocsEnabled: true`) |

> API 문서 상세 가이드: [api-docs.md](api-docs.md)

## REST API

모든 API 엔드포인트는 `{contextPath}/api/*` 하위에 위치한다.

### GET 엔드포인트

| 엔드포인트 | 설명 |
|-----------|------|
| `/api/status` | 서버 상태 (힙, 스레드, 업타임, TLS) |
| `/api/servers` | 서버 인스턴스 목록 |
| `/api/applications` | 배포된 애플리케이션 목록 |
| `/api/clusters` | 클러스터 목록 |
| `/api/nodes` | 노드 정보 |
| `/api/resources` | 리소스 정보 |
| `/api/threads` | 전체 스레드 덤프 |
| `/api/threadpools` | 스레드 풀 상태 |
| `/api/monitoring` | MetricsCollector 메트릭 스냅샷 |
| `/api/commands` | 사용 가능한 CLI 명령어 목록 |
| `/api/audit` | 감사 로그 (필터: `?action=`, `?user=`, `?limit=`) |
| `/api/drafts` | 설정 변경 Draft 목록 (필터: `?status=`) |
| `/api/config` | server.yaml 원본 내용 |
| `/api/jvm` | JVM 정보 |
| `/api/system` | 시스템 정보 |
| `/api/loggers` | 로거 목록 및 현재 레벨 |
| `/api/users` | 사용자 목록 |

### POST 엔드포인트

| 엔드포인트 | 설명 |
|-----------|------|
| `/api/execute` | CLI 명령어 실행 (`{"command": "status"}`) |
| `/api/config/save` | 설정 변경 Draft 생성 |
| `/api/loggers/set` | 로거 레벨 변경 (`{"logger": "...", "level": "DEBUG"}`) |
| `/api/drafts/create` | 설정 변경 Draft 생성 |
| `/api/drafts/{id}/action` | Draft 워크플로우 (`{"action": "validate\|review\|approve\|apply\|rollback\|reject"}`) |
| `/api/users/create` | 사용자 생성 (`{"username": "...", "password": "..."}`) |
| `/api/users/remove` | 사용자 삭제 (`{"username": "..."}`) |
| `/api/users/change-password` | 비밀번호 변경 (`{"username": "...", "password": "..."}`) |

### SSE (Server-Sent Events)

| 엔드포인트 | 설명 |
|-----------|------|
| `/sse/status` | 2초 간격 서버 상태 스트림 (Dashboard에서 사용) |

## 설정 변경 워크플로우 (Draft)

설정 변경은 안전한 단계적 워크플로우를 따른다:

```
Draft → Validate → Review → Approve → Apply
                                        ↓
                                    Rollback
```

1. **Draft**: 변경 사항 초안 생성
2. **Validate**: 문법/형식 검증
3. **Review**: 검토자 확인
4. **Approve**: 승인권자 승인
5. **Apply**: 실제 적용
6. **Rollback**: 적용된 변경 되돌리기
7. **Reject**: 검토 단계에서 거부

모든 워크플로우 단계는 감사 로그에 기록된다.

## 사용자 관리 및 비밀번호 변경

### 기본 계정

서버 최초 기동 시 기본 관리자 계정이 생성된다.

| 항목 | 기본값 |
|------|--------|
| 아이디 | `admin` |
| 비밀번호 | `admin` |

> **보안 권장**: 서버 기동 후 즉시 기본 비밀번호를 변경할 것을 권장한다.

### Web Admin UI에서 비밀번호 변경

1. Web Admin에 로그인한다 (`http://localhost:8080/admin`)
2. 좌측 사이드바에서 **Security** 페이지로 이동한다
3. 사용자 목록에서 비밀번호를 변경할 사용자의 **Reset Password** 버튼을 클릭한다
4. 새 비밀번호를 입력하고 확인한다

### CLI Console에서 비밀번호 변경

Web Admin의 Console 페이지 (`/console`) 또는 CLI 도구에서 다음 명령어를 실행한다:

```
changepassword admin newpassword123
```

### REST API로 비밀번호 변경

```bash
curl -X POST http://localhost:8080/admin/api/users/change-password \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "newpassword123"}'
```

### 사용자 생성

**Web Admin UI**: Security 페이지에서 **Add User** 버튼 클릭 후 아이디와 비밀번호 입력

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

### 사용자 삭제

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

> 모든 사용자 관리 작업(생성, 삭제, 비밀번호 변경)은 감사 로그에 자동 기록된다.

## 감사 로그 (Audit)

다음 작업이 자동으로 감사 로그에 기록된다:

- 로그인 성공/실패
- 로그아웃
- CLI 명령어 실행
- 로거 레벨 변경
- 사용자 생성/삭제/비밀번호 변경
- Draft 워크플로우 전 단계 (생성, 검증, 검토, 승인, 적용, 롤백, 거부)

감사 로그는 최대 10,000건까지 메모리에 보관되며, History 페이지 또는 `/api/audit`에서 조회할 수 있다.

## 토스트 알림

모든 작업 결과는 화면 우상단 토스트 알림으로 표시된다.
- 성공: 초록색
- 실패: 빨간색
- 경고: 노란색
- 정보: 파란색

4초 후 자동으로 사라지며, 클릭하여 즉시 닫을 수 있다.

## 키보드 단축키

| 단축키 | 기능 |
|--------|------|
| `Ctrl+K` | 명령 팔레트 열기 (페이지 이동, 명령어 실행) |
| `ESC` | 모달/팔레트 닫기 |

## 모듈 구조

```
was-webadmin/
├── WebAdminApplication.java          # 서블릿 앱 팩토리
├── api/
│   ├── AdminApiDocsServlet.java     # OpenAPI 3.0 스펙 + Swagger UI
│   ├── AdminApiServlet.java          # REST API (GET/POST)
│   └── AdminSseServlet.java          # SSE 실시간 스트림
├── audit/
│   ├── AuditEngine.java              # 감사 로그 엔진 (싱글턴)
│   └── AuditEvent.java               # 감사 이벤트 레코드
├── config/
│   ├── ConfigChangeEngine.java       # Draft 워크플로우 엔진 (싱글턴)
│   └── ConfigDraft.java              # Draft 레코드
└── servlet/
    ├── AdminAuthFilter.java          # 인증 필터
    ├── AdminDashboardServlet.java    # 대시보드
    ├── AdminLoginServlet.java        # 로그인
    ├── AdminLogoutServlet.java       # 로그아웃
    ├── AdminConsoleServlet.java      # CLI 콘솔
    ├── AdminPageLayout.java          # 공통 레이아웃 (CSS/JS/사이드바)
    ├── AdminStaticResourceServlet.java
    └── page/                         # 관리 페이지 서블릿 (14개)
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

## UI 특징

- 다크 테마 (JEUS WebAdmin 스타일)
- SPA 방식 탭 전환 (전체 페이지 리로드 없음)
- 외부 프론트엔드 의존성 없음 (순수 HTML/CSS/JS 내장)
- 반응형 사이드바 네비게이션
- 실시간 데이터 갱신 (SSE + Polling 폴백)
- Canvas 기반 실시간 차트 (힙 메모리, 스레드)
- 라이트/다크 테마 전환 (localStorage 저장)
- 내장 OpenAPI 3.0 Swagger UI (Try It 기능 포함)

## 관련 문서

- [API Documentation (Swagger) 가이드](api-docs.md)
- [빌드 / 기동 / 종료 스크립트 가이드](build-scripts.md)
- [Admin CLI 가이드](admin-cli.md)
