# WebAdmin 데이터 저장소 상세

## 목적

이 문서는 `webadmin`에서 보이는 데이터가 실제로 어디에 저장되는지, 무엇이 재기동 후에도 남고 무엇이 사라지는지 코드 기준으로 설명한다.

현재 구현의 핵심은 다음 한 줄로 요약할 수 있다.

- `webadmin`은 별도 DB를 사용하지 않는다.
- 데이터는 `JVM 메모리`, `파일 시스템`, `SLF4J 로그 출력`, `브라우저 localStorage`, `브라우저 쿠키`에 나뉘어 존재한다.

## 한눈에 보기

| 데이터 종류 | 실제 저장 위치 | 재기동 후 유지 | 노드 간 공유 | 비고 |
|---|---|---|---|---|
| 로그인 세션 | 서버 프로세스 메모리 | 아니오 | 기본값 기준 아니오 | `JSESSIONID` 쿠키는 브라우저에만 저장 |
| CSRF 토큰 | 서버 세션 메모리 | 아니오 | 기본값 기준 아니오 | 세션 속성 |
| 사용자/비밀번호 | 서버 프로세스 메모리 | 아니오 | 아니오 | 기본 `admin/admin`으로 초기화 |
| 감사 이벤트 | 서버 메모리 + 로그 출력 | 메모리본은 아니오 | 아니오 | 로그 출력은 외부 수집 방식에 따라 남을 수 있음 |
| 설정 Draft | 서버 프로세스 메모리 | 아니오 | 아니오 | `Apply`도 현재는 실제 파일 미반영 |
| Domain/Logger 상태 | 서버 프로세스 메모리 | 아니오 | 아니오 | `LocalAdminClient` 내부 맵 |
| 실제 설정 원본 | 파일 시스템 `conf/server.yaml` | 예 | 파일 공유 방식에 따름 | 읽기 원본 파일 |
| WAR 업로드 파일 | 파일 시스템 `deploy/` 계열 | 일부 예 | 파일 공유 방식에 따름 | 업로드 방식에 따라 지속성 차이 있음 |
| 배포된 앱 런타임 상태 | 서버 프로세스 메모리 | 아니오 | 아니오 | 재기동 시 `deploy/*.war`만 재스캔 |
| 테마/언어/즐겨찾기 등 UI 설정 | 브라우저 `localStorage` | 예 | 브라우저/Origin 별로만 | 서버와 무관 |

## 1. 서버 메모리에만 저장되는 데이터

### 1.1 로그인 세션

기본 부트스트랩 경로에서는 `webadmin` 세션이 서버 메모리에 저장된다.

- 기본 세션 저장소: `InMemoryHttpSessionStore`
- 기본 세션 쿠키 이름: `JSESSIONID`
- 기본 만료: `server.session.timeoutSeconds`
- 만료 정리 주기: `server.session.purgeIntervalSeconds`

근거 코드:

- `was-servlet-core/src/main/java/io/velo/was/servlet/SimpleServletContainer.java`
- `was-servlet-core/src/main/java/io/velo/was/servlet/InMemoryHttpSessionStore.java`
- `was-bootstrap/src/main/java/io/velo/was/bootstrap/VeloWasApplication.java`

세션 안에는 최소한 다음 값들이 들어간다.

| 세션 키 | 의미 |
|---|---|
| `velo.admin.authenticated` | 로그인 성공 여부 |
| `velo.admin.username` | 로그인 사용자명 |
| `velo.admin.loginTime` | 로그인 시각 |
| `velo.admin.lastLogin` | 로그인 후 UI에 보여줄 마지막 로그인 표시 |
| `velo.csrf.token` | CSRF 검증 토큰 |

운영 의미:

- WAS 재기동 시 모두 사라진다.
- 기본 구현은 단일 JVM 메모리라 다른 노드와 공유되지 않는다.
- 브라우저의 `JSESSIONID` 쿠키가 남아 있어도 서버 메모리에서 세션이 사라졌으면 다시 로그인해야 한다.

### 1.2 사용자 계정과 비밀번호

`webadmin`의 사용자/비밀번호는 별도 DB나 파일이 아니라 `LocalAdminClient` 내부의 `ConcurrentHashMap`에 저장된다.

근거 코드:

- `was-admin/src/main/java/io/velo/was/admin/client/LocalAdminClient.java`
- `was-webadmin/src/main/java/io/velo/was/webadmin/WebAdminApplication.java`

현재 동작:

- 서버 시작 시 `admin -> admin` 기본 계정이 메모리에 생성된다.
- UI의 사용자 추가/삭제/비밀번호 변경은 이 메모리 맵을 수정한다.
- `WebAdminApplication`이 하나의 `AdminClient` 인스턴스를 로그인 서블릿과 API 서블릿에 공유하므로, 현재 프로세스 안에서는 로그인과 API가 같은 사용자 상태를 본다.

중요한 한계:

- 재기동하면 추가한 사용자와 바꾼 비밀번호는 모두 사라지고 다시 `admin/admin` 상태로 돌아간다.
- 멀티 노드 환경에서도 노드별 메모리가 따로라 공유되지 않는다.

### 1.3 감사 이벤트

감사 이벤트는 두 군데로 간다.

1. `AuditEngine`의 메모리 덱
2. SLF4J 로거 `velo.audit`

근거 코드:

- `was-webadmin/src/main/java/io/velo/was/webadmin/audit/AuditEngine.java`
- `was-webadmin/src/main/java/io/velo/was/webadmin/audit/AuditEvent.java`

메모리 동작:

- 최신 이벤트를 앞에서부터 저장한다.
- 최대 10,000건까지만 유지한다.
- 재기동 시 메모리본은 사라진다.

로그 출력 동작:

- 이벤트마다 `velo.audit` 카테고리로 로그를 남긴다.
- fat jar 기본 런타임은 `slf4j-simple`을 사용하므로, 별도 로깅 백엔드를 붙이지 않으면 보통 콘솔 출력이 기본이다.
- 즉 “감사 로그 파일”이 자동 생성되는 구조는 아니고, 실제 영속 보관은 로그 리다이렉션/수집기 설정에 달려 있다.

### 1.4 설정 Draft

설정 화면에서 저장하는 내용은 지금 당장 `conf/server.yaml`을 덮어쓰지 않는다.
대신 `ConfigChangeEngine`의 메모리 맵에 Draft로 저장한다.

근거 코드:

- `was-webadmin/src/main/java/io/velo/was/webadmin/api/AdminApiServlet.java`
- `was-webadmin/src/main/java/io/velo/was/webadmin/config/ConfigChangeEngine.java`
- `was-webadmin/src/main/java/io/velo/was/webadmin/config/ConfigDraft.java`

현재 흐름:

1. Settings 페이지에서 `/api/config`로 현재 `conf/server.yaml`을 읽어온다.
2. 저장 버튼은 `/api/config/save`를 호출한다.
3. 서버는 변경 내용을 Draft로 메모리에 저장한다.
4. `apply()`를 호출해도 현재는 상태만 `APPLIED`로 바꾸고 실제 파일은 수정하지 않는다.

중요한 한계:

- Draft는 재기동 시 전부 사라진다.
- “적용됨” 상태라도 실제 `conf/server.yaml` 파일 내용은 바뀌지 않는다.

### 1.5 도메인 속성, 로그 레벨, 일부 관리 상태

다음 데이터들도 `LocalAdminClient` 내부 맵에 존재한다.

| 데이터 | 저장 방식 | 비고 |
|---|---|---|
| Domain 목록/속성 | 메모리 맵 | 기본 `default` 도메인 생성 |
| Logger 레벨 | 메모리 맵 | `setLogLevel()`로 변경 |
| 서버/앱/리소스 일부 상태 | 계산값 또는 메모리 | 실데이터 저장소 아님 |

근거 코드:

- `was-admin/src/main/java/io/velo/was/admin/client/LocalAdminClient.java`

운영 의미:

- 재기동 시 초기값으로 돌아간다.
- 별도 영속 저장소가 없다.

## 2. 파일 시스템에 저장되는 데이터

### 2.1 실제 서버 설정 파일

서버는 기본적으로 `conf/server.yaml`을 읽어서 기동한다.

근거 코드:

- `was-bootstrap/src/main/java/io/velo/was/bootstrap/VeloWasApplication.java`
- `was-bootstrap/src/main/java/io/velo/was/bootstrap/ServerConfigurationLoader.java`
- `was-webadmin/src/main/java/io/velo/was/webadmin/api/AdminApiServlet.java`

의미:

- 이것이 현재 서버 설정의 “실제 원본 파일”이다.
- WebAdmin 설정 편집기는 이 파일을 읽어와서 보여준다.
- 하지만 현재 WebAdmin에서 저장해도 Draft만 만들어지고, 이 파일 자체는 수정되지 않는다.

### 2.2 WAR 업로드와 배포 파일

WebAdmin의 앱 업로드는 최종적으로 `deploy/` 아래 파일을 만든다.

핵심 경로:

| 경로 | 의미 |
|---|---|
| `deploy/` | 기본 WAR 배포 디렉터리 |
| `deploy/.work/` | WAR 전개 작업 디렉터리 |
| `deploy/.upload-staging/` | 업로드 수신 중 임시 staging 파일 |
| `deploy/.uploads/` | 수동 context path 지정 업로드 WAR 저장 위치 |

근거 코드:

- `was-bootstrap/src/main/java/io/velo/was/bootstrap/VeloWasApplication.java`
- `was-bootstrap/src/main/java/io/velo/was/bootstrap/AdminWarUploadChannelHandler.java`
- `was-bootstrap/src/main/java/io/velo/was/bootstrap/AdminWarUploadService.java`

업로드 방식별 차이:

1. 일반 업로드

- 파일이 `deploy/<파일명>.war`로 이동된다.
- `hotDeploy=true`면 watcher가 나중에 반영한다.
- `hotDeploy=false`면 바로 배포한다.
- 이 경우 재기동 후에도 `deploy/*.war` 재스캔 대상이라 다시 올라온다.

2. 수동 context path 지정 업로드

- 파일이 `deploy/.uploads/<uuid>-원본명.war`로 저장된다.
- 즉시 배포는 되지만, 서버 시작 시 재스캔은 `deploy/` 최상위의 `*.war`만 보기 때문에 `.uploads/` 하위 WAR는 자동 재배포되지 않는다.

운영상 매우 중요:

- “수동 context path 배포”는 현재 구현 기준으로 재기동 내구성이 약하다.
- 재기동 후에도 유지하려면 최상위 `deploy/`에 재배치하거나 별도 배포 절차가 필요하다.

### 2.3 실제 로그 보관 위치

코드 자체는 `velo.access`, `velo.audit`, `velo.error` 로거 카테고리에 구조화 로그를 남긴다.

근거 코드:

- `was-observability/src/main/java/io/velo/was/observability/AccessLog.java`
- `was-observability/src/main/java/io/velo/was/observability/ErrorLog.java`
- `was-webadmin/src/main/java/io/velo/was/webadmin/audit/AuditEngine.java`
- `was-bootstrap/pom.xml`

현재 fat jar 기본값:

- 런타임 의존성은 `slf4j-simple`
- 별도 파일 appender 설정이 없으면 콘솔 출력이 기본

즉 실제 “파일 경로”는 애플리케이션 코드가 고정하지 않는다.
다음 중 하나에 의해 결정된다.

- 운영 쉘의 stdout/stderr 리다이렉션
- systemd/journald
- Docker logging driver
- 외부 SLF4J 백엔드 교체

## 3. 브라우저 localStorage에 저장되는 데이터

다음 항목들은 서버가 아니라 사용자의 브라우저 `localStorage`에 저장된다.
같은 Origin에서만 보이며, 다른 브라우저/다른 PC/다른 노드와 자동 공유되지 않는다.

### 3.1 전역 UI/로그인 관련 키

| 키 | 사용 위치 | 의미 |
|---|---|---|
| `velo-theme` | 로그인, 대시보드, 설정, 공통 레이아웃 | 다크/라이트 테마 |
| `velo-lang` | 공통 레이아웃, 설정 | UI 언어 |
| `velo-remember-user` | 로그인 | 마지막 로그인 사용자명 |
| `velo-mfa-enabled` | 로그인 | 클라이언트 측 MFA 화면 분기 플래그 |
| `velo-setup-complete` | 로그인 | 초기 welcome/setup wizard 완료 여부 |
| `velo-favorites` | 공통 레이아웃 | 사이드바 즐겨찾기 |
| `velo-admin-prefs` | 설정 | 기본 페이지, 자동 새로고침, 언어 등 일반 환경설정 |
| `velo-notif-prefs` | 설정 | 브라우저 알림/사운드/자동 새로고침 선호도 |
| `velo-notif-history` | 설정 | 알림 이력용 키로 가정되지만 현재 코드는 clear만 있고 기록 로직은 없음 |

### 3.2 Console 페이지

| 키 | 의미 |
|---|---|
| `velo-saved-commands` | 저장한 명령어 목록 |
| `velo-custom-templates` | 사용자 정의 명령 템플릿 |

### 3.3 Diagnostics 페이지

| 키 | 의미 |
|---|---|
| `velo-thread-dumps` | 사용자가 저장한 thread dump 비교본 |
| `velo-heap-dumps` | heap dump 실행 이력 메타데이터 |

주의:

- `velo-heap-dumps`에는 heap dump “파일 내용”이 아니라 이력 메타데이터만 저장된다.
- 실제 heap dump 파일은 서버 측 명령 구현이 저장하는 별도 파일에 있고, 여기엔 파일명/시각/옵션 정도만 남는다.

### 3.4 Monitoring 페이지

| 키 | 의미 |
|---|---|
| `velo-alert-rules` | 브라우저 로컬 alert rule 정의 |

이 규칙은 서버 공통 정책이 아니다.
현재는 해당 브라우저에서만 보이는 클라이언트 사이드 규칙이다.

### 3.5 Resources 페이지

| 키 | 의미 |
|---|---|
| `velo-datasources` | UI에서 추가한 DataSource 목록 |

중요:

- 이것은 현재 실제 서버 JNDI/DataSource 영속 저장소가 아니다.
- 브라우저 UI에만 저장되는 목록이다.

### 3.6 Scripts 페이지

| 키 | 의미 |
|---|---|
| `velo-scripts` | 저장 스크립트, 마지막 실행 시각, 스케줄 문자열 |

중요:

- 서버 스케줄러에 등록되는 것이 아니라 브라우저 localStorage에 저장되는 메타데이터다.
- 다른 브라우저/다른 운영자 화면에는 자동으로 나타나지 않는다.

### 3.7 Security 페이지

| 키 | 의미 |
|---|---|
| `velo.customRoles` | 사용자 정의 역할 목록 |

중요:

- 현재 custom role은 서버 RBAC 저장소가 아니라 브라우저 로컬 데이터다.
- 내 브라우저에서 만든 role이 다른 운영자 브라우저에서는 보이지 않는다.

## 4. 브라우저 쿠키에 저장되는 데이터

### 4.1 `JSESSIONID`

브라우저에는 세션 식별자만 쿠키로 저장된다.
실제 세션 본문은 서버 메모리에 있다.

근거 코드:

- `was-webadmin/src/main/java/io/velo/was/webadmin/servlet/AdminAuthFilter.java`
- `was-servlet-core/src/main/java/io/velo/was/servlet/SimpleServletContainer.java`

현재 의미:

- 쿠키는 세션 ID 포인터 역할만 한다.
- 서버 재기동 후에는 해당 ID가 가리키는 세션이 없어지므로 다시 로그인해야 한다.

## 5. “화면상 저장”과 “실제 영속 저장”이 다른 항목

운영자가 가장 헷갈리기 쉬운 부분만 따로 정리하면 다음과 같다.

| 화면 기능 | 겉보기 인상 | 실제 저장 위치 | 진짜 영속 여부 |
|---|---|---|---|
| 비밀번호 변경 | 서버에 저장될 것 같음 | `LocalAdminClient.users` 메모리 | 재기동 후 소실 |
| Add User | 계정 DB에 저장될 것 같음 | 메모리 | 재기동 후 소실 |
| Settings 저장 | `server.yaml`이 바뀔 것 같음 | Draft 메모리 | 실제 파일 미반영 |
| Alert Rule 저장 | 서버 경보 정책 같음 | 브라우저 localStorage | 브라우저에만 유지 |
| DataSource 생성 | 서버 리소스 생성 같음 | 브라우저 localStorage | 서버 재기동과 무관, 서버에도 미반영 |
| Script 저장/스케줄 | 서버 작업 스케줄 같음 | 브라우저 localStorage | 브라우저에만 유지 |
| Custom Role 생성 | 서버 RBAC 저장 같음 | 브라우저 localStorage | 브라우저에만 유지 |
| WAR 업로드 | 파일 배포 | `deploy/` 또는 `deploy/.uploads/` | 업로드 방식에 따라 다름 |

## 6. 재기동 후 유지 여부 체크리스트

### 유지되는 것

- `conf/server.yaml` 파일 자체
- `deploy/` 최상위에 놓인 WAR 파일
- 브라우저의 `localStorage`
- 브라우저의 쿠키
- 운영 환경이 별도로 보관하는 콘솔 로그

### 사라지는 것

- 로그인 세션
- CSRF 토큰
- 사용자 추가/삭제/비밀번호 변경 결과
- 감사 이벤트 메모리본
- 설정 Draft
- Domain 속성/Logger level 메모리 변경분
- 런타임 배포 레지스트리

### 조건부 유지

- `deploy/.uploads/` 아래 수동 context path 업로드 WAR
  - 파일은 남더라도 기본 부트스트랩 재스캔 대상이 아니어서 자동 재배포되지 않음

## 7. 운영 권장사항

현재 구현 기준으로 `webadmin`을 운영 콘솔로 쓸 때는 다음을 권장한다.

1. 사용자/비밀번호를 영구 관리 대상으로 생각하지 말고, 현재는 “프로세스 메모리 기반 임시 관리 기능”으로 취급한다.
2. 실제 서버 설정 변경은 `webadmin` Draft만 믿지 말고 `conf/server.yaml` 변경 절차와 분리해서 운영한다.
3. 감사 로그를 보존하려면 콘솔 로그를 파일이나 수집기로 반드시 외부 적재한다.
4. 앱 배포를 재기동 후에도 유지하려면 최상위 `deploy/`에 WAR가 남도록 운영한다.
5. Scripts, Alert Rules, DataSources, Custom Roles는 브라우저별 데이터이므로 운영 표준 데이터 저장소로 간주하지 않는다.

## 8. 향후 개선 포인트

현재 구조에서 실제 영속 저장소가 필요하다면 다음 순서가 자연스럽다.

1. 사용자/비밀번호를 파일 또는 외부 인증 저장소로 분리
2. Draft를 파일/DB로 영속화하고 `apply()`가 실제 `server.yaml` 반영까지 수행
3. 감사 로그를 메모리 + 파일/DB 이중화
4. localStorage 기반 UI 데이터 중 운영적으로 중요한 항목을 서버 API 뒤로 이동
5. 수동 context path 업로드 WAR의 재기동 복원 전략 추가
