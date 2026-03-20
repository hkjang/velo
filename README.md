# velo-was

Netty 기반 엔터프라이즈 WAS 파운데이션.

- **Tomcat급 서블릿 호환성** - Jakarta Servlet 6.1 API 기반 컨테이너
- **Jetty급 프로토콜/I/O 성능** - Netty 네이티브 전송 (epoll/kqueue/nio)
- **JEUS급 운영/관리성** - YAML 설정, 모듈 분리, 확장 가능한 구조

## 기술 스택

| 항목 | 버전 |
|---|---|
| Java | 21 |
| Netty | 4.1.131.Final |
| Jakarta Servlet API | 6.1.0 |
| SnakeYAML | 2.5 |
| SLF4J | 2.0.17 |
| JUnit 5 | 5.12.1 |
| Maven | 3.9.13 |

## 모듈 구조

```text
velo-was (13 modules)
├── was-config              서버 설정 모델 (순수 POJO, 외부 의존성 없음)
├── was-observability       구조화된 로깅 (액세스/에러/감사)
├── was-protocol-http       HTTP 프로토콜 추상화 + WebSocket
├── was-transport-netty     Netty 서버 부트스트랩 (HTTP/2, TLS ALPN)
├── was-servlet-core        서블릿 컨테이너 (AsyncContext, 세션 TTL)
├── was-classloader         웹 애플리케이션 클래스로더 격리
├── was-deploy              WAR 배포 파이프라인
├── was-jndi                JNDI 네이밍 + DataSource 커넥션 풀
├── was-admin               관리 CLI (jeusadmin 호환, 73개 명령어)
├── was-jsp                 JSP 지원 (실험적)
├── was-tcp-listener        TCP 리스너
└── was-bootstrap           기동 진입점 + 통합 테스트
```

각 모듈의 상세 문서는 모듈 디렉토리의 `README.md`를 참고한다. 아키텍처 상세는 [`docs/ko/architecture.md`](docs/ko/architecture.md)를 참고한다.

## 빌드

```bash
# 로컬 툴체인 설정 (Windows PowerShell)
.\scripts\use-local-toolchain.ps1

# 전체 빌드
mvn clean package

# 테스트만 실행
mvn test
```

## 실행

```bash
java -jar was-bootstrap/target/was-bootstrap-0.5.10-jar-with-dependencies.jar [config-path]
```

- `config-path` 미지정 시 `conf/server.yaml` 사용
- 메인 클래스: `io.velo.was.bootstrap.VeloWasApplication`

## 설정

설정 파일: [`conf/server.yaml`](conf/server.yaml)

```yaml
server:
  name: velo-was
  nodeId: node-1
  gracefulShutdownMillis: 30000

  listener:
    host: 0.0.0.0
    port: 8080
    soBacklog: 2048
    reuseAddress: true
    tcpNoDelay: true
    keepAlive: true
    maxContentLength: 10485760      # 10 MB
    idleTimeoutSeconds: 60          # 유휴 연결 타임아웃
    maxHeaderSize: 8192             # HTTP 헤더 최대 크기
    maxInitialLineLength: 4096      # HTTP 요청 라인 최대 길이

  threading:
    bossThreads: 1
    workerThreads: 0                # 0 = Netty 기본값
    businessThreads: 32

  compression:
    enabled: false                  # gzip 압축 활성화
    minResponseSizeBytes: 1024      # 최소 압축 대상 크기
    compressionLevel: 6             # zlib 압축 레벨 (1-9)

  session:
    timeoutSeconds: 1800            # 세션 타임아웃 (30분)
    purgeIntervalSeconds: 60        # 만료 세션 정리 주기

  deploy:
    directory: deploy               # WAR 배포 디렉토리
    hotDeploy: false                # 핫 디플로이 활성화
    scanIntervalSeconds: 5          # 디렉토리 감시 디바운스

  tls:
    enabled: false
    mode: PEM                       # PEM 또는 PKCS12
    certChainFile: ""
    privateKeyFile: ""
    protocols: [TLSv1.3, TLSv1.2]
    reloadIntervalSeconds: 30
```

## API 엔드포인트

| 경로 | 메서드 | 설명 |
|---|---|---|
| `/health` | GET, HEAD | 서버 상태 확인 |
| `/metrics` | GET | 서버 메트릭 (요청 수, 활성 연결, 응답 시간) |
| `/info` | GET, HEAD | 서버 정보 조회 |
| `/app/hello` | GET | 샘플 서블릿 (세션 기반 방문 횟수 추적) |

응답 예시:

```bash
# Health check
curl http://localhost:8080/health
# {"status":"UP","name":"velo-was","nodeId":"node-1"}

# Metrics
curl http://localhost:8080/metrics
# {"totalRequests":150,"activeRequests":2,"activeConnections":5,"averageResponseTimeMs":12.34,"status":{"1xx":0,"2xx":140,"3xx":3,"4xx":5,"5xx":2}}

# Server info
curl http://localhost:8080/info
# {"product":"velo-was","phase":"servlet-foundation","transport":"netty","servletCompatibility":"minimal"}

# Sample servlet
curl http://localhost:8080/app/hello
# {"message":"Hello from Velo Servlet","contextPath":"/app","servletPath":"/hello","visits":1,...}
```

## 현재 구현 범위

- Maven 멀티모듈 프로젝트 레이아웃 (13개 모듈, 170+ 테스트)
- YAML 기반 서버 설정 로딩 및 검증
- Netty HTTP/1.1 + **HTTP/2** 런타임 (graceful shutdown)
- 네이티브 전송 자동 선택 (epoll/kqueue/nio)
- TLS 부트스트랩 및 인증서 핫 리로드 (ALPN h2/h1.1 협상 포함)
- **HTTP/2**: TLS ALPN + 클리어텍스트 h2c + 스트림 멀티플렉싱
- **WebSocket**: 경로 기반 핸들러 레지스트리, 텍스트/바이너리 프레임
- **Gzip 압축**: `HttpContentCompressor` 기반 응답 압축 (설정 가능)
- **유휴 연결 타임아웃**: `IdleStateHandler` 기반 자동 연결 종료
- **HTTP 코덱 제한**: 최대 헤더 크기, 최대 요청 라인 길이 설정
- 서블릿 컨테이너
  - `HttpServlet` dispatch (context path + servlet path longest-match)
  - `Filter` chain 실행 (DispatcherType 기반 매칭)
  - `ServletContextListener`, `ServletContextAttributeListener`, `ServletRequestListener` lifecycle
  - `HttpSessionListener`, `HttpSessionAttributeListener`, `HttpSessionIdListener`
  - `RequestDispatcher` forward/include (상대 경로 및 `..` 해석 포함)
  - `error-page` 매핑 (`error-code`, `exception-type`) + `DispatcherType.ERROR`
  - In-memory `JSESSIONID` 쿠키 세션 + **TTL 만료 스케줄러** (타임아웃 설정 가능)
  - `changeSessionId()` 기반 session fixation 완화
  - **AsyncContext**: dispatch, complete, timeout, listener
  - **Multipart**: `multipart/form-data` 파싱, `Part` API 지원
  - 동적 프록시 기반 `HttpServletRequest`/`HttpServletResponse`/`ServletContext`/`HttpSession`
  - TLS 감지 (`isSecure()`, `getScheme()`)
- **WAR 배포**: web.xml 파싱, 클래스로더 격리 (parent-first/child-first)
- **배포 디렉토리**: `deploy/` 디렉토리 기반 자동 WAR 배포
- **핫 디플로이**: `WatchService` 기반 WAR 파일 변경 감지 및 자동 재배포
- **메트릭 수집**: `LongAdder` 기반 요청 수, 활성 연결, 응답 시간, HTTP 상태 코드 분포
- **구조화된 로깅**: 액세스/에러/감사 로그 (JSON 형식)
- **JNDI / DataSource**: In-Memory 네이밍 컨텍스트, JDBC 커넥션 풀
- **Admin CLI**: 14개 카테고리 73개 명령어, JLine 인터랙티브 셸, JMX 통합
- 내장 health/info/metrics 엔드포인트
- 보안 헤더 기본 포함 (nosniff, DENY, no-store, no-referrer)

## 문서

| 문서 | 설명 |
|---|---|
| [아키텍처 개요](docs/ko/architecture.md) | 모듈 구조, 요청 처리 흐름, 설계 결정 |
| [아키텍처 상세](docs/ko/architecture-detail.md) | 내부 동작 심층 분석 |
| [Tomcat 10 대비 속도 해석](docs/ko/tomcat10-vs-velo-speed.md) | Java 21, 경량 컨테이너 구조, 기능 범위 차이를 기준으로 체감 성능 차이 해석 |
| [AsyncContext](docs/ko/async-context.md) | 비동기 서블릿 지원 |
| [WAR 배포](docs/ko/war-deployment.md) | WAR 배포 + 클래스로더 격리 + 핫 디플로이 |
| [구조화된 로깅](docs/ko/structured-logging.md) | 액세스/에러/감사 로그 + 메트릭 (JSON) |
| [HTTP/2 + WebSocket](docs/ko/http2-websocket.md) | ALPN, h2c, WebSocket 업그레이드 |
| [세션 관리](docs/ko/session-management.md) | TTL 만료 + 이중 제거 전략 + 설정 연동 |
| [클러스터 세션 가이드](docs/ko/cluster-session-guide.md) | 저장소 SPI, sticky session, 비동기 복제, TTL/충돌 일관성 |
| [JNDI / DataSource](docs/ko/jndi-datasource.md) | JNDI 네이밍 + 커넥션 풀 |
| [Admin CLI](docs/ko/admin-cli.md) | 73개 관리 명령어 레퍼런스 |
| [Browser WAR 샘플](docs/ko/browser-war-samples.md) | JavaScript, TypeScript, jQuery, React, Vue, Angular 테스트 링크 |
| [라이프사이클](docs/ko/lifecycle.md) | 서버/앱 생명주기 |
| [제품화 로드맵](docs/ko/roadmap.md) | 우선순위 재정렬 + 6주 실행 계획 |

## 향후 계획

상세 우선순위와 단계별 실행 계획은 [`docs/ko/roadmap.md`](docs/ko/roadmap.md)를 참고한다.

1. **서블릿 스펙 완성도 강화**: `web.xml`, 에러 디스패치, 리스너, 세션 보안 보강
2. **운영 기능 실데이터화**: CLI/Web Admin이 실제 런타임 상태를 직접 노출
3. **테스트 자산 확대**: 재배포, 느린 클라이언트, HTTP/2, WebSocket 회귀 테스트
4. **관측성 표준화**: Prometheus, request id, slow request log, 앱별 메트릭 태깅

