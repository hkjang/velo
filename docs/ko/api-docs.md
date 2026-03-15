# API Documentation (Swagger) 가이드

Velo WAS Web Admin은 내장 OpenAPI 3.0 기반 API 문서를 제공한다.
외부 라이브러리 없이 순수 HTML/CSS/JS로 구현된 Swagger UI를 내장하고 있다.

## 접속 정보

| 항목 | 경로 | 설명 |
|------|------|------|
| Swagger UI | `{contextPath}/api-docs/ui` | 인터랙티브 API 문서 페이지 |
| OpenAPI JSON | `{contextPath}/api-docs` | OpenAPI 3.0 JSON 스펙 |

기본 설정 기준:

- Swagger UI: `http://localhost:8080/admin/api-docs/ui`
- OpenAPI JSON: `http://localhost:8080/admin/api-docs`

## 설정

`conf/server.yaml`에서 API 문서 기능을 활성화/비활성화할 수 있다.

```yaml
server:
  webAdmin:
    enabled: true
    contextPath: /admin
    apiDocsEnabled: true    # false로 설정하면 API 문서 비활성화
```

`apiDocsEnabled: false`로 설정하면 `/api-docs/*` 엔드포인트가 등록되지 않는다.
운영 환경에서 보안을 위해 비활성화할 수 있다.

## Swagger UI 기능

### 엔드포인트 탐색

- 모든 REST API 엔드포인트를 **태그별로 그룹화**하여 표시
- 각 엔드포인트를 클릭하면 상세 정보 확인 가능
- HTTP 메서드별 색상 구분:
  - `GET` → 초록색
  - `POST` → 노란색
  - `PUT` → 파란색
  - `DELETE` → 빨간색

### 상세 정보

각 엔드포인트 확장 시 다음 정보가 표시된다:

- **설명**: API의 목적과 동작 설명
- **Parameters**: 쿼리 파라미터 (이름, 위치, 타입, 설명)
- **Request Body**: 요청 본문 스키마 (필드명, 타입, 필수 여부, 예시값)
- **Responses**: 응답 코드 및 설명

### Try It 기능

모든 GET 엔드포인트에 **Try It** 버튼이 제공된다.
버튼 클릭 시 해당 API를 실제 호출하고 JSON 응답을 포맷팅하여 표시한다.

> POST 엔드포인트의 경우 Console 페이지 (`/console`) 또는 curl 명령을 사용한다.

### 스키마 참조

페이지 하단의 **Schemas** 섹션에서 모든 데이터 모델을 확인할 수 있다.
각 스키마를 클릭하면 속성(프로퍼티), 타입, 포맷 정보가 표시된다.

## API 태그 분류

| 태그 | 설명 | 엔드포인트 수 |
|------|------|--------------|
| Status | 서버 상태 및 헬스 정보 | 1 |
| Servers | 서버 인스턴스 관리 | 1 |
| Applications | 애플리케이션 배포 및 관리 | 1 |
| Resources | 리소스 모니터링 (메모리, 커넥션) | 1 |
| Monitoring | 메트릭 및 성능 모니터링 | 1 |
| Diagnostics | 스레드 덤프, JVM/시스템 정보 | 4 |
| Security | 사용자 및 세션 관리 | 4 |
| Configuration | 서버 설정, 로거, Draft 관리 | 6 |
| Commands | CLI 명령어 실행 | 2 |
| Audit | 감사 로그 및 이력 | 1 |
| Clusters | 클러스터 관리 | 1 |
| Nodes | 노드 관리 | 1 |

## 데이터 모델 (Schemas)

### 주요 스키마

| 스키마 | 설명 |
|--------|------|
| `ServerStatus` | 서버 상태 (이름, 노드ID, 힙 메모리, 업타임, 스레드 수) |
| `ServerInfo` | 서버 인스턴스 상세 (호스트, 포트, 트랜스포트, TLS, 스레드 설정) |
| `ApplicationInfo` | 애플리케이션 (이름, 컨텍스트 경로, 상태, 서블릿/필터 수) |
| `ResourceInfo` | 힙/비힙 메모리 사용량 |
| `ThreadInfo` | 전체 스레드 정보 (ID, 이름, 상태, 데몬 여부, 데드락) |
| `CommandInfo` | CLI 명령어 (이름, 설명, 카테고리, 사용법) |
| `CommandResult` | 명령어 실행 결과 (성공 여부, 메시지) |
| `ThreadPoolInfo` | 스레드 풀 (이름, 활성 수, 풀 크기, 최대 크기) |
| `LoggerInfo` | 로거 (이름, 레벨) |
| `UserInfo` | 사용자 (사용자명) |
| `AuditEvent` | 감사 이벤트 (타임스탬프, 사용자, 액션, 대상, 상세, IP, 성공) |
| `ClusterInfo` | 클러스터 (이름, 멤버 수, 상태) |
| `NodeInfo` | 노드 (ID, 호스트, OS, CPU, Java 버전, 서버 목록) |

## 인증

API 문서 페이지에 접근하려면 Web Admin 로그인이 필요하다.
API 호출 시에도 세션 쿠키(JSESSIONID) 기반 인증이 적용된다.

```
Security Scheme: sessionAuth
Type: API Key (Cookie)
Name: JSESSIONID
```

## 외부 도구에서 활용

### curl로 OpenAPI 스펙 다운로드

```bash
# 로그인하여 세션 쿠키 획득
curl -c cookies.txt -X POST http://localhost:8080/admin/login \
  -d "username=admin&password=admin"

# OpenAPI JSON 다운로드
curl -b cookies.txt http://localhost:8080/admin/api-docs > openapi.json
```

### Postman/Insomnia에서 사용

1. `http://localhost:8080/admin/api-docs`에서 OpenAPI JSON 다운로드
2. Postman → Import → File → `openapi.json` 선택
3. 자동으로 모든 엔드포인트가 컬렉션으로 생성됨

### Swagger Editor에서 사용

1. [https://editor.swagger.io](https://editor.swagger.io) 접속
2. File → Import URL → `http://localhost:8080/admin/api-docs` 입력
3. 스펙 편집 및 코드 생성 가능

## 네비게이션

API Docs 페이지는 다음 방법으로 접근할 수 있다:

- **사이드바**: Administration 섹션 → "API Docs" 메뉴
- **명령 팔레트**: `Ctrl+K` → "API Docs" 검색
- **직접 URL**: `http://localhost:8080/admin/api-docs/ui`

## UI 특징

- Web Admin과 동일한 다크/라이트 테마 지원
- 사이드바, 헤더, 토스트 알림 등 공통 레이아웃 적용
- 외부 CDN 의존성 없이 완전히 내장된 UI
- OpenAPI JSON 스펙 파일 직접 다운로드 링크 제공
