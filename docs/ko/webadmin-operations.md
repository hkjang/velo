# Velo Web Admin 운영 가이드

## 개요

이 문서는 Velo Web Admin의 고급 운영 기능에 대한 가이드이다.
기본 사용법은 [webadmin.md](webadmin.md)를 참고한다.

## 서버 라이프사이클 관리

### 서버 상태 모델

```
STARTING → RUNNING → SUSPENDED → SHUTDOWN
                  ↘      ↗
                   FAILED
```

| 상태 | 설명 |
|------|------|
| STARTING | 서버 기동 중 |
| RUNNING | 정상 운영 중 |
| SUSPENDED | 일시 중지 (트래픽 수신 중단, Drain 완료) |
| SHUTDOWN | 종료 완료 |
| FAILED | 비정상 종료 |

### 서버 제어 버튼

Servers 페이지에서 각 서버 인스턴스에 대해 다음 작업을 수행할 수 있다:

| 버튼 | 명령 | 위험도 | 설명 |
|------|------|--------|------|
| ▶ Start | `start-server` | Low | 서버 기동 |
| ⏸ Suspend | `suspend-server` | Low | 트래픽 drain 후 일시 중지 |
| ▶︎ Resume | `resume-server` | Low | 일시 중지된 서버 재개 |
| ↻ Restart | `restart-server` | Medium | 서버 재시작 (graceful) |
| ⏹ Stop | `stop-server` | **High** | 서버 정상 종료 |
| ■ Force Stop | `force-stop-server` | **High** | 서버 강제 종료 |

> **위험 작업 확인**: High Risk 작업은 확인 모달이 표시되며, 위험도 라벨과 함께 실행 여부를 재확인한다.

### 벌크 작업

1. 서버 목록에서 체크박스로 여러 서버를 선택한다
2. 상단에 벌크 작업 바가 나타난다
3. "Bulk Restart" 또는 "Bulk Stop"을 클릭한다
4. 선택된 모든 서버에 순차적으로 명령이 전송된다

### 서버 상세 정보

서버 이름 옆의 ▼ 아이콘을 클릭하면 확장 패널이 열리며 다음 정보를 확인할 수 있다:

- JVM 인수
- OS/Java 환경
- 리스너 설정 (Host, Port, TLS, SO Backlog, Idle Timeout)
- 배포된 애플리케이션 목록

## 클러스터 운영

### 클러스터 생성

1. Clusters 페이지에서 "Create Cluster" 버튼을 클릭한다
2. 클러스터 이름, 세션 복제 모드, 로드 밸런싱 정책을 선택한다
3. "Create" 버튼으로 생성한다

### 멤버 관리

클러스터를 선택하면 하단 상세 패널에서 멤버 관리가 가능하다:

- **Add Member**: 서버 인스턴스를 클러스터에 추가
- **Remove**: 클러스터에서 서버 제거
- **Drain**: 특정 멤버의 트래픽을 중단 (유지보수 전)
- **Resume**: 트래픽 재개

### 롤링 작업

롤링 작업은 클러스터의 각 멤버에 순차적으로 작업을 수행한다:

| 작업 | 설명 |
|------|------|
| Rolling Restart | 멤버를 하나씩 재시작하며 헬스체크 통과 후 다음 멤버 진행 |
| Rolling Deploy | 트래픽 drain 후 배포, 시작, 확인 순서로 진행 |
| Drain & Maintain | 특정 멤버의 트래픽을 제거하고 유지보수 수행 |
| Scale Out | 새 서버 인스턴스를 클러스터에 동적 추가 |

## 노드 관리

### 노드 인벤토리

Nodes 페이지는 물리/논리 노드의 전체 인벤토리를 제공한다:

- Node ID, Host, OS, CPUs, 상태, Agent 연결 상태
- 각 노드에 배포된 서버 인스턴스 수

### 노드 상세 탭

| 탭 | 내용 |
|----|------|
| Node Detail | 기본 정보 (OS, Java, CPU, 서버 목록) |
| Node Agent | 에이전트 상태, 하트비트, Ping/Restart 작업 |
| Processes | 관리 대상 JVM 프로세스 목록 (PID, 상태, 메모리, 시작 시각) |
| Resources | CPU 사용률, 로드 평균, 디스크 사용량 |

### 노드 등록

1. "Register Node" 버튼을 클릭한다
2. Node ID, Host Address, Agent Port를 입력한다
3. "Register"를 클릭하면 `register-node` 명령이 실행된다

## 애플리케이션 배포

### 배포 방법

| 방법 | 설명 |
|------|------|
| WAR 업로드 | 브라우저에서 WAR 파일을 선택하여 업로드 |
| 직접 배포 | 서버 경로를 지정하여 배포 (`deploy /path/to/app.war`) |
| Hot Deploy | deploy 디렉토리에 WAR 파일 복사 시 자동 배포 |

### 배포 진행 상태

배포 시 5단계 진행 표시기가 나타난다:

```
Uploading → Validating → Deploying → Starting → Running
```

각 단계는 색상으로 상태를 표시한다:
- 회색: 대기 중
- 파란색: 진행 중 (스피너)
- 초록색: 완료
- 빨간색: 오류

### 애플리케이션 제어

배포된 각 애플리케이션에 대해 다음 작업이 가능하다:

| 버튼 | 설명 |
|------|------|
| Start | 중지된 앱 시작 |
| Stop | 실행 중인 앱 중지 |
| Redeploy | 재배포 (기존 컨텍스트 유지) |
| Reload | 컨텍스트 리로드 |
| Rollback | 이전 버전으로 복귀 |
| Log | 애플리케이션 로그 조회 |
| History | 배포 이력 조회 |
| Undeploy | 배포 해제 (확인 필요) |

## 리소스 관리

### JDBC DataSource

- "Add DataSource" 버튼으로 새 DataSource 생성
- 필수 항목: 이름, JDBC URL
- 선택 항목: JNDI 이름, 드라이버, 사용자, 비밀번호, 풀 크기
- "Test" 버튼으로 연결 테스트 (`test-datasource` 명령 실행)
- "Remove" 버튼으로 삭제

### Connection Pool

Connection Pool 탭에서 풀 상태를 모니터링한다:

- Active/Idle/Max 커넥션 수
- Wait Count, 평균 대기 시간
- "Reset Pool" / "Flush Pool" 액션 버튼
- Active > Max × 0.9 시 누수 경고 표시

### JMS

JMS 탭에서 메시징 리소스를 관리한다:

- JMS 서버 목록 (`list-jms-servers` 명령)
- Destination 목록 (`list-jms-destinations` 명령)
- 큐 퍼지 (`purge-jms-queue` 명령)

### JNDI

JNDI 탭에서 바인딩 목록을 확인하고 참조를 검증한다.

### Thread Pool

Thread Pool 탭에서 스레드 풀 상태를 실시간 모니터링한다:

- 풀 이름, Active, Pool Size, Max Size, Queue Size
- "Refresh" 버튼으로 갱신

## 모니터링

### 실시간 메트릭

Overview 탭에서 다음 메트릭을 실시간으로 확인한다:

- Uptime, Heap Memory, Threads, CPUs
- Response Time Percentiles (p50, p95, p99)
- 이상 탐지 (Anomaly Detection): 힙 사용량이 최근 평균에서 2σ 이상 벗어나면 경고

### 라이브 차트

Canvas 기반 실시간 차트로 3가지 메트릭을 동시에 모니터링한다:

- **Heap (MB)**: 보라색 선
- **Threads**: 초록색 선
- **CPU (%)**: 노란색 선

체크박스로 각 메트릭의 표시/숨김을 전환할 수 있다.

### 알림 규칙

Alert Rules 탭에서 커스텀 알림 규칙을 설정한다:

1. "Add Rule" 버튼을 클릭한다
2. 메트릭(Heap %, Thread Count, CPU %), 조건(>, <, >=, <=), 임계값을 설정한다
3. 심각도(Info/Warning/Critical)와 액션(Toast/Log/Email)을 선택한다
4. 규칙은 localStorage에 저장되어 브라우저에 유지된다

### 로그 뷰어

Logs 탭에서 로그를 검색하고 필터링한다:

- 날짜 범위, 레벨, 텍스트 검색 필터
- 페이지네이션 (20건씩)
- 로거 레벨 실시간 변경 가능

### 데이터 내보내기

| 형식 | 버튼 | 설명 |
|------|------|------|
| CSV | Export CSV | 차트 데이터를 CSV 파일로 다운로드 |
| PDF | Export PDF Report | 새 탭에서 인쇄 가능한 리포트 생성 |

## 진단

### Thread Dump

- "Capture New Dump" 버튼으로 최신 스레드 덤프 수집
- 스레드 상태 요약: Runnable / Waiting / Timed Waiting / Blocked
- Download / Copy 버튼으로 덤프 내용 저장
- **덤프 비교**: 여러 시점의 덤프를 저장하고 side-by-side로 비교

### Heap Dump

- "Generate Heap Dump" 버튼으로 힙 덤프 트리거
- 서버 로컬 파일로 저장됨

> **주의**: 힙 덤프 생성 중 서버가 잠시 멈출 수 있다. 프로덕션에서 주의하여 사용한다.

### Deadlock 탐지

- 서버 기동 시 자동 체크
- 데드락 발견 시 관련 스레드 체인 정보 표시
- 주기적 자동 체크 토글 지원

## 설정 변경 워크플로우

### 단계별 프로세스

```
Read → Draft → Validate → Review → Approve → Apply → Verify
                                                  ↓
                                              Rollback
```

### History 페이지 기능

| 탭 | 내용 |
|----|------|
| Changes | 설정 변경 이력 (검색, 필터링) |
| Audit | 전체 감사 추적 (날짜, 사용자, 액션 필터) |
| Drafts | Draft 워크플로우 관리 (상태별 필터) |

### Diff 뷰

Draft를 클릭하면 side-by-side Diff 뷰가 표시된다:
- 왼쪽: 기존 설정
- 오른쪽: 변경된 설정
- 변경된 라인은 빨간색(삭제)/초록색(추가)으로 강조

### 감사 로그 내보내기

- "Export Audit Log": 감사 이벤트만 CSV로 내보내기
- "Export All": 감사 이벤트 + Draft 이력 전체 CSV 내보내기

## 콘솔 (CLI)

### 기본 사용

Console 페이지에서 모든 CLI 명령어를 실행할 수 있다:

```
velo> status
velo> list-servers
velo> deploy /path/to/app.war
```

### 자동완성

- Tab 키: 명령어 자동완성
- 입력 중 드롭다운으로 매칭 명령어 표시
- 카테고리와 설명이 함께 표시됨

### 실행 모드

| 모드 | 설명 |
|------|------|
| Text | 텍스트 입력 방식 (기본) |
| Form | 파라미터를 폼 필드로 입력하는 방식 |

### 위험 명령 확인

`stop`, `kill`, `remove`, `delete`, `undeploy`, `force` 키워드가 포함된 명령은 자동으로 확인 대화상자가 표시된다:

- 실행될 CLI 명령어 미리보기
- API payload 미리보기
- "Execute Anyway" 버튼으로 실행

### 저장된 명령

- History에서 ★ 아이콘을 클릭하여 명령 저장
- 저장된 명령은 사이드바에서 클릭하여 재사용
- 최대 30개까지 localStorage에 저장

### 히스토리 내보내기

"Export History" 버튼으로 실행 이력을 텍스트 파일로 다운로드한다.

## 보안

### 사용자 관리

Security 페이지의 Users 탭에서 관리한다:

- 사용자 생성/삭제
- 비밀번호 재설정
- admin 계정은 삭제할 수 없음

### 역할 관리

Roles 탭에서 역할을 관리한다:

- 기본 역할: ADMIN (전체 권한), OPERATOR (운영), VIEWER (읽기 전용)
- 커스텀 역할 생성: 이름, 설명, 권한(server.view, server.control, app.deploy 등) 지정
- 커스텀 역할은 삭제 가능, 기본 역할은 삭제 불가

### 보안 정책

Policies 탭에서 보안 정책을 인라인 편집한다:

| 정책 | 기본값 | 설명 |
|------|--------|------|
| Session Timeout | 1800초 | 세션 만료 시간 |
| Max Failed Attempts | 5 | 계정 잠금 전 최대 실패 횟수 |
| Account Lock Duration | 900초 | 잠금 해제 대기 시간 |

### 비밀번호 정책

| 정책 | 기본값 |
|------|--------|
| 최소 길이 | 8자 |
| 대문자 포함 | 필수 |
| 숫자 포함 | 필수 |
| 특수문자 포함 | 필수 |

### 세션 관리

Sessions 탭에서 활성 세션을 관리한다:

- 세션 목록 조회 (사용자, IP, 시작 시각, 마지막 활동)
- 개별 세션 종료 ("Terminate" 버튼)
- 전체 세션 종료 ("Terminate All" — 본인 포함 로그아웃)
- 30초마다 자동 갱신

### 인증서 관리

Certificates 탭에서 TLS 인증서를 관리한다:

- Alias, Subject, Issuer, 만료일, 상태 표시
- 만료 임박 경고 (30일 이내: 빨간색, 90일 이내: 노란색)

## 테마

### 다크 모드 (기본)

운영자를 위한 기본 테마이다. 장시간 모니터링에 최적화되어 있다.

### 라이트 모드

Settings > Appearance 탭에서 "Light" 테마를 선택한다.
모든 CSS 변수가 라이트 모드용으로 오버라이드된다:

- 배경: `#f5f6f8` (어두운 `#0f1117` 대신)
- 표면: `#ffffff` / `#f0f1f3` / `#e4e6ea`
- 텍스트: `#1a1d27` / `#495057` / `#868e96`
- 테두리: `#d1d5db`

테마 설정은 `localStorage`에 저장되어 브라우저에 유지된다.

## 키보드 단축키

| 단축키 | 기능 |
|--------|------|
| `Ctrl+K` | 명령 팔레트 열기 |
| `Tab` | 콘솔 자동완성 |
| `↑` / `↓` | 콘솔 히스토리 탐색 |
| `Shift+Enter` | 콘솔 멀티라인 입력 |
| `Enter` | 콘솔 명령 실행 |
| `ESC` | 모달/팔레트 닫기 |

## 캐시 관리

Resources 페이지의 **Cache** 탭에서 캐시를 관리한다.

| 기능 | 설명 |
|------|------|
| 캐시 목록 | 이름, 타입, 크기, 히트율, 적중/미스, 제거 수, TTL 표시 |
| 캐시 통계 | 전체 캐시 수, 전체 엔트리, 전체 히트율, 메모리 사용량 |
| Clear | 특정 캐시의 모든 엔트리 삭제 |
| Clear All | 모든 캐시 초기화 |
| Stats | 특정 캐시의 상세 통계 확인 |

## Dry Run (시험 실행)

Console의 **Action Preview** (Ctrl+Shift+Enter)에서 명령 실행 전에 **Dry Run** 버튼으로 시험 실행할 수 있다.

- 실제 변경 없이 명령의 유효성과 예상 결과를 미리 확인
- Dry Run 결과는 Preview 패널 내에 표시
- 위험도가 높은 명령(서버 중지, 배포 해제 등)에 특히 유용

## 알림 설정

Settings 페이지의 **Notifications** 탭에서 알림 환경을 설정한다.

| 설정 | 설명 |
|------|------|
| Browser Notifications | 데스크탑 알림 허용 (Critical 알림 시) |
| Sound Alerts | 알림 시 사운드 재생 |
| Auto-refresh Dashboard | 대시보드 자동 갱신 (10초 간격) |
| Alert Severity Filter | Critical, Warning, Info 레벨별 알림 수신 선택 |
| Notification History Retention | 알림 보존 기간 (1시간 ~ 7일) |

## 서버 토폴로지

Dashboard의 **Server Topology** 섹션에서 서버 인스턴스 간 관계를 시각적으로 확인할 수 있다.

- Admin 서버와 관리 대상 서버 인스턴스의 연결 관계 표시
- 각 서버의 상태(Running/Stopped/Warning) 실시간 표시
- Refresh 버튼으로 최신 상태 갱신

## 접근성 (Accessibility)

| 기능 | 설명 |
|------|------|
| Skip to content | Tab으로 본문 바로 이동 |
| Focus visible | 모든 인터랙티브 요소에 포커스 링 표시 |
| ARIA attributes | `role`, `aria-label`, `aria-current`, `aria-expanded`, `aria-haspopup` 적용 |
| Reduced motion | `prefers-reduced-motion` 미디어 쿼리 지원 |
| Keyboard navigation | 모든 주요 기능 키보드로 접근 가능 |

## Heap Dump 진단

Diagnostics 페이지의 **Heap Dump** 탭에서 힙 덤프를 생성하고 관리한다.

| 기능 | 설명 |
|------|------|
| Heap 사용량 | 현재 힙 사용량, 최대값, 사용률 게이지 표시 |
| Non-Heap 사용량 | Non-Heap 메모리 사용 현황 |
| Live objects only | 살아있는 객체만 포함 옵션 (권장) |
| Request GC | 힙 덤프 전 GC 요청 |
| Heap Dump History | 생성 이력 (타임스탬프, 파일, 모드, 소요시간) |

## 관련 문서

- [Velo Web Admin 기본 가이드](webadmin.md)
- [API Documentation (Swagger) 가이드](api-docs.md)
- [빌드 / 기동 / 종료 스크립트 가이드](build-scripts.md)
- [Admin CLI 가이드](admin-cli.md)
