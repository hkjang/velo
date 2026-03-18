# Velo WAS 제품화 로드맵

이 문서는 현재 저장소 상태를 기준으로 `velo-was`를 "기능 데모 가능한 WAS"에서 "운영 가능한 제품형 WAS"로 끌어올리기 위한 우선순위와 6주 실행 계획을 정리한다.

## 목표

다음 단계의 핵심 목표는 세 가지다.

1. **운영 안정성**: 재배포, 장시간 실행, 느린 클라이언트, 큰 요청 등 경계 조건에서도 서버가 안정적으로 동작해야 한다.
2. **서블릿 호환성 완성도**: README에 제시한 "Tomcat급 서블릿 호환성"을 실제 호환성 항목과 테스트로 증명해야 한다.
3. **개발자/운영자 경험**: CLI, Web Admin, 메트릭, 설정 검증이 "있다" 수준을 넘어 "믿고 쓸 수 있다" 수준이어야 한다.

## 현재 상태 요약

현재 저장소에는 이미 다음 축의 1차 구현이 존재한다.

- `was-servlet-core`: `AsyncContext`, `Filter`, `RequestDispatcher`, 세션 TTL, multipart
- `was-deploy`: exploded WAR, `web.xml` 파싱, 핫 디플로이, 클래스 로더 격리
- `was-transport-netty` / `was-protocol-http`: HTTP/2, TLS ALPN, WebSocket, gzip, idle timeout
- `was-admin`: 73개 CLI 명령 표면
- `was-webadmin`: 로그인, 대시보드, REST API, SSE 기반 관리 UI
- `was-observability`: JSON 구조화 로그, 기본 요청/연결 메트릭

반면 제품성 관점에서 아직 보강이 필요한 영역도 분명하다.

- `web.xml` / 서블릿 스펙의 세부 항목은 아직 부분 구현 상태다.
- 운영 명령은 넓지만 일부는 실제 런타임 데이터보다 로컬 스텁 성격이 강하다.
- 메트릭은 기본 JSON 스냅샷 중심이며 Prometheus/OpenTelemetry 같은 표준 통합이 없다.
- 회귀 테스트는 기능 소개 대비 경계 조건과 장시간 시나리오가 부족하다.

## 재정렬된 우선순위

| 순위 | 에픽 | 왜 지금 먼저 해야 하는가 | 주요 대상 모듈 |
|---|---|---|---|
| 1 | 서블릿 스펙 완성도 강화 | 프로젝트의 핵심 차별화 포인트이며 실제 호환성 이슈가 가장 먼저 드러나는 영역이다. | `was-servlet-core`, `was-deploy` |
| 2 | 운영 기능의 실데이터화 | CLI/Web Admin이 제품 인상을 좌우하므로 런타임 상태를 실제로 보여줘야 한다. | `was-admin`, `was-webadmin`, `was-bootstrap` |
| 3 | 테스트 자산 확대 | 재배포, 경계 조건, 프로토콜 회귀를 막아 주는 품질 게이트가 필요하다. | `was-bootstrap`, `test-war`, `test-app` |
| 4 | 관측성 표준화 | 현장 도입성을 높이는 가장 빠른 방법이며 운영 안정성과도 직접 연결된다. | `was-observability`, `was-webadmin` |
| 5 | 배포/클래스로더 격리 고도화 | 배포 누수와 충돌은 장기 운영에서 가장 비싼 장애가 되므로 조기 차단이 필요하다. | `was-deploy`, `was-classloader` |
| 6 | 설정 체계 고도화 | 운영 환경 분리, 검증, 일부 핫 리로드는 배포 안정성을 크게 높인다. | `was-config`, `was-bootstrap`, `was-webadmin` |
| 7 | HTTP 기능 보강 | 이미 뼈대가 있는 만큼 keep-alive, streaming, range, GOAWAY를 정교화하면 완성도가 오른다. | `was-protocol-http`, `was-transport-netty` |
| 8 | 보안 기능 | secure cookie, SameSite, CLI/Web Admin 인증 강화는 운영 배포 이전 필수 항목이다. | `was-servlet-core`, `was-admin`, `was-webadmin`, `was-config` |
| 9 | 데이터소스/트랜잭션 | 엔터프라이즈 포지셔닝을 위해 필요하지만 앞선 운영/호환성보다 우선도는 낮다. | `was-jndi`, `was-admin` |
| 10 | 문서/포지셔닝 | 지원 범위와 미지원 항목을 명시해야 기대치가 정렬되고 도입 판단이 쉬워진다. | `README.md`, `docs/ko/*` |
| 11 | Web Admin 고도화 | 이미 기반이 있으므로 상위 기능이 실데이터화된 뒤 UX를 확장하는 편이 효율적이다. | `was-webadmin` |
| 12 | JSP 방향성 결정 | 제품 핵심은 아니므로 마지막에 범위를 명확히 정의하는 편이 맞다. | `was-jsp`, `docs/ko/*` |

## 에픽별 구체 항목

### 1. 서블릿 스펙 완성도 강화

- `error-page` 매핑 지원
- `security-constraint` / login-config 파싱과 최소 enforcement
- `HttpSession#changeSessionId()` 기반 session fixation 보호
- `ServletContextAttributeListener`, `HttpSessionListener`, `HttpSessionAttributeListener` 추가
- `Filter` dispatch type 처리의 예외 케이스 정리
- `RequestDispatcher`의 `ERROR_*` 속성과 에러 디스패치 흐름 보강
- welcome-file, directory request, default servlet 동작 검증 확대

### 2. 운영 기능의 실데이터화

- `app list`, `app info`, `thread dump`, `connection 현황`, `TLS 상태`를 실제 런타임 데이터와 연결
- graceful shutdown 외에 `graceful drain` 상태 노출
- runtime config view와 read-only config API 제공
- 세션 조회/카운트, 최근 에러, 최근 배포 이력 노출
- CLI와 Web Admin이 동일한 관리 서비스 계층을 사용하도록 정리

### 3. 테스트 자산 확대

- 배포/재배포/undeploy 반복 테스트
- slow client / slow upload / idle timeout 테스트
- large header / large body / malformed request 테스트
- HTTP/2 conformance, websocket soak test
- classloader leak 및 hot deploy 회귀 테스트
- 지원 기능 매트릭스와 연결된 호환성 테스트 세트 구축

### 4. 관측성 표준화

- `/metrics`에 Prometheus 포맷 추가
- request id 생성 및 전파
- slow request log 기준값과 샘플링 설정
- 앱별, 경로별, 상태 코드별 태깅
- JVM GC / thread pool / deployment lifecycle 메트릭 추가
- OpenTelemetry 또는 Micrometer bridge 설계

### 5. 배포/클래스로더 격리 고도화

- undeploy 시 classloader leak 탐지
- 라이브러리 충돌 탐지 및 진단 메시지
- annotation 스캔 전략 최적화
- 병렬 배포 및 배포 실패 rollback 개선

### 6. 설정 체계 고도화

- `dev` / `prod` 프로파일 분리
- 환경변수 치환과 기본값 문법
- startup validation report
- config schema validation
- 일부 설정의 safe hot reload

## 6주 실행 계획

### 1-2주차: 서블릿 호환성 1차 마감

목표는 `web.xml`과 요청 디스패치의 핵심 누락 항목을 채워 "호환성 기반"을 닫는 것이다.

- `error-page`, `security-constraint`, listener 계열 파싱 구조 확장
- session fixation 보호 추가
- 에러 디스패치와 필터 dispatch type 회귀 테스트 작성
- 지원/미지원 항목 표 초안 작성

완료 기준:

- `web.xml`에 선언된 `error-page`와 `welcome-file`이 통합 테스트로 검증된다.
- 주요 listener와 session id 변경 흐름이 테스트로 보장된다.
- README와 문서에서 "minimal" 수준 표현을 보다 구체적인 지원 매트릭스로 대체할 수 있다.

### 3-4주차: 운영 기능 실데이터화 + 관측성 표준화

목표는 CLI/Web Admin을 운영 도구답게 만들고, `/metrics`를 외부 시스템과 연결 가능한 수준으로 올리는 것이다.

- 관리 서비스 계층을 정리해 CLI와 Web Admin이 같은 런타임 데이터를 보게 한다.
- thread dump, app list, connection 현황, TLS 상태, runtime config view 구현
- Prometheus 포맷, request id, slow request log, 앱별 메트릭 태깅 추가
- Web Admin 대시보드와 API에서 실데이터 노출

완료 기준:

- CLI와 Web Admin의 주요 관리 화면이 스텁 없이 실제 런타임 값으로 동작한다.
- `/metrics`는 JSON 외에 Prometheus 형식도 제공한다.
- 운영자가 최근 장애 징후를 메트릭과 로그만으로 파악할 수 있다.

### 5주차: 배포 안정성 + 회귀 테스트 강화

목표는 재배포와 장시간 운영에서 발생할 수 있는 누수와 경계 조건 장애를 조기에 막는 것이다.

- redeploy/undeploy 반복 테스트와 classloader leak 검출 추가
- large request / slow client / websocket soak / HTTP/2 회귀 테스트 추가
- hot deploy 실패 시 진단 로그와 rollback 동작 정리

완료 기준:

- 배포 관련 회귀 테스트가 CI에서 반복 실행 가능하다.
- classloader close 누락이나 잔존 스레드 같은 대표 누수 패턴을 탐지할 수 있다.

### 6주차: 문서화 + 포지셔닝 정리

목표는 지금까지의 구현 범위를 사용자와 운영자가 오해 없이 이해하게 만드는 것이다.

- 지원 기능 매트릭스
- 미지원 / 부분 지원 항목 문서화
- 운영 가이드, 튜닝 가이드 초안
- Tomcat/Jetty와의 비교 포인트 정리
- JSP는 범위를 줄일지 계속 투자할지 명시

완료 기준:

- 신규 사용자가 README와 문서만 읽고 현재 가능한 것과 아직 아닌 것을 구분할 수 있다.
- "제품 방향"과 "다음 릴리스 목표"가 한 문서에서 연결된다.

## 릴리스 게이트

다음 릴리스는 아래 기준을 만족할 때 제품형 마일스톤으로 간주한다.

- 서블릿 지원 범위가 문서와 테스트로 함께 제시된다.
- 운영 CLI/Web Admin의 핵심 조회 명령이 실제 런타임 데이터를 사용한다.
- `/metrics`가 외부 수집기와 바로 연결 가능한 형태를 제공한다.
- redeploy/undeploy와 slow client 시나리오가 자동화 테스트에 포함된다.
- 지원/미지원 항목이 README와 문서에 명확히 기록된다.

## 후순위 항목

다음 항목은 중요하지만 상위 에픽 이후에 진행하는 편이 효율적이다.

- JSP 엔진 완성도 확대
- XA 수준 트랜잭션
- 분산 세션/클러스터 고도화
- Web Admin UI 연출 개선

이유는 현재 병목이 "기능 수"가 아니라 "호환성, 운영 신뢰성, 검증 가능성"에 있기 때문이다.
