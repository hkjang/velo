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

```
velo-was
├── was-config              서버 설정 모델 (순수 POJO, 외부 의존성 없음)
├── was-protocol-http       HTTP 요청/응답 추상화, 핸들러 라우팅
├── was-transport-netty     Netty 서버 부트스트랩, TLS, 네이티브 전송
├── was-servlet-core        서블릿 컨테이너 (Filter, Listener, Session, Dispatcher)
└── was-bootstrap           서버 진입점, YAML 로딩, 샘플 앱
```

각 모듈의 상세 문서는 모듈 디렉토리의 `README.md`를 참고한다. 아키텍처 상세는 [`docs/architecture.md`](docs/architecture.md)를 참고한다.

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
java -jar was-bootstrap/target/was-bootstrap-0.1.0-SNAPSHOT-jar-with-dependencies.jar [config-path]
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

  threading:
    bossThreads: 1
    workerThreads: 0                # 0 = Netty 기본값
    businessThreads: 32

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
| `/info` | GET, HEAD | 서버 정보 조회 |
| `/app/hello` | GET | 샘플 서블릿 (세션 기반 방문 횟수 추적) |

응답 예시:

```bash
# Health check
curl http://localhost:8080/health
# {"status":"UP","name":"velo-was","nodeId":"node-1"}

# Server info
curl http://localhost:8080/info
# {"product":"velo-was","phase":"servlet-foundation","transport":"netty","servletCompatibility":"minimal"}

# Sample servlet
curl http://localhost:8080/app/hello
# {"message":"Hello from Velo Servlet","contextPath":"/app","servletPath":"/hello","visits":1,...}
```

## 현재 구현 범위

- Maven 멀티모듈 프로젝트 레이아웃
- YAML 기반 서버 설정 로딩 및 검증
- Netty HTTP/1.1 런타임 (graceful shutdown)
- 네이티브 전송 자동 선택 (epoll/kqueue/nio)
- TLS 부트스트랩 및 인증서 핫 리로드 (connection-safe)
- 서블릿 컨테이너
  - `HttpServlet` dispatch (context path + servlet path longest-match)
  - `Filter` chain 실행 (DispatcherType 기반 매칭)
  - `ServletContextListener`, `ServletRequestListener` lifecycle
  - `RequestDispatcher` forward/include (상대 경로 및 `..` 해석 포함)
  - In-memory `JSESSIONID` 쿠키 세션
  - 동적 프록시 기반 `HttpServletRequest`/`HttpServletResponse`/`ServletContext`/`HttpSession`
- 내장 health/info 엔드포인트
- 보안 헤더 기본 포함 (nosniff, DENY, no-store, no-referrer)

## 프로젝트 구조

```
velo-was/
├── conf/
│   └── server.yaml                           서버 설정 파일
├── docs/
│   └── architecture.md                       아키텍처 상세 문서
├── scripts/
│   └── use-local-toolchain.ps1               로컬 JDK/Maven 환경 설정
├── was-config/
│   └── src/main/java/.../config/
│       ├── ServerConfiguration.java          설정 모델 (Server, Listener, Threading, Tls)
│       └── TlsMode.java                     TLS 키 형식 enum (PEM, PKCS12)
├── was-protocol-http/
│   └── src/main/java/.../http/
│       ├── HttpExchange.java                 요청/응답 사이클 레코드
│       ├── HttpHandler.java                  핸들러 함수형 인터페이스
│       ├── HttpHandlerRegistry.java          경로 기반 라우팅 레지스트리
│       ├── HttpResponses.java                응답 팩토리 (보안 헤더 포함)
│       └── NettyHttpChannelHandler.java      Netty 채널 핸들러
├── was-transport-netty/
│   └── src/main/java/.../transport/netty/
│       ├── NativeTransportSelector.java      OS별 전송 자동 선택
│       ├── NettyServer.java                  서버 생명주기 관리
│       └── ReloadingSslContextProvider.java  TLS 인증서 핫 리로드
├── was-servlet-core/
│   └── src/main/java/.../servlet/
│       ├── ServletContainer.java             컨테이너 인터페이스
│       ├── SimpleServletContainer.java       컨테이너 기본 구현
│       ├── ServletApplication.java           애플리케이션 정의 인터페이스
│       ├── SimpleServletApplication.java     빌더 패턴 애플리케이션 구현
│       ├── ServletProxyFactory.java          동적 프록시 팩토리
│       ├── ServletRequestContext.java        요청 컨텍스트 관리
│       ├── ServletResponseContext.java       응답 버퍼 관리
│       ├── SimpleFilterChain.java            필터 체인 실행
│       ├── FilterRegistrationSpec.java       필터 등록 정보 레코드
│       ├── InMemoryHttpSessionStore.java     세션 저장소
│       ├── SessionState.java                 세션 상태
│       ├── ServletBodyInputStream.java       서블릿 입력 스트림
│       ├── ServletBodyOutputStream.java      서블릿 출력 스트림
│       ├── InternalRequestBridge.java        내부 요청 브리지
│       └── InternalResponseBridge.java       내부 응답 브리지
└── was-bootstrap/
    └── src/main/java/.../bootstrap/
        ├── VeloWasApplication.java           메인 클래스 (진입점)
        ├── ServerConfigurationLoader.java    YAML 설정 로더
        ├── SampleHelloServlet.java           샘플 서블릿
        ├── SampleTraceFilter.java            샘플 필터
        └── SampleLifecycleListener.java      샘플 리스너
```

## 향후 계획

- 비동기 서블릿 (AsyncContext)
- WAR 배포 및 애플리케이션 클래스로더 격리
- 구조화된 액세스/에러/감사 로깅
- Admin REST API, 메트릭스, JMX
- HTTP/2 ALPN + WebSocket 업그레이드
- TTL 기반 세션 만료 스케줄러
- JNDI / DataSource SPI
