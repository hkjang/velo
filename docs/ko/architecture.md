# Architecture Overview

## 프로젝트 목표

velo-was는 세 가지 목표를 지향하는 Netty 기반 엔터프라이즈 WAS 파운데이션이다:

- **Tomcat급 서블릿 호환성**: Jakarta Servlet 6.1 API 기반 컨테이너
- **Jetty급 프로토콜/I/O 성능**: Netty 네이티브 전송, 비동기 I/O
- **JEUS급 운영/관리성**: YAML 설정, 모듈 분리, 확장 가능한 구조

## 모듈 구조

```
velo-was (parent pom)
├── was-config              설정 모델 (순수 POJO)
├── was-protocol-http       HTTP 프로토콜 추상화
├── was-transport-netty     Netty 서버 부트스트랩
├── was-servlet-core        서블릿 컨테이너
├── was-admin               관리 CLI (jeusadmin 호환)
└── was-bootstrap           기동 진입점 + 샘플 앱
```

### 모듈 의존성 그래프

```
was-bootstrap
  ├── was-config
  ├── was-protocol-http
  │     └── netty-codec-http
  ├── was-transport-netty
  │     ├── was-config
  │     ├── was-protocol-http
  │     ├── netty-handler
  │     └── netty-transport-native-*
  └── was-servlet-core
        ├── was-protocol-http
        ├── netty-codec-http
        └── jakarta.servlet-api 6.1

was-admin
  ├── was-config
  ├── jline 3.27.1
  └── snakeyaml
```

## 요청 처리 흐름

```
클라이언트 요청
    │
    ▼
┌─────────────────────────────────────────────┐
│ Netty Channel Pipeline                       │
│  [SSL] → HttpServerCodec → HttpObject-       │
│          Aggregator → ChunkedWriteHandler    │
│          → NettyHttpChannelHandler           │
└─────────────────┬───────────────────────────┘
                  │ HttpExchange 생성
                  ▼
┌─────────────────────────────────────────────┐
│ HttpHandlerRegistry                          │
│  /health → 내장 핸들러                       │
│  /info   → 내장 핸들러                       │
│  (기타)  → fallback: ServletContainer.handle │
└─────────────────┬───────────────────────────┘
                  │ (서블릿 경로인 경우)
                  ▼
┌─────────────────────────────────────────────┐
│ SimpleServletContainer                       │
│  1. context path로 DeployedApplication 해석  │
│  2. servlet path로 ServletRuntime 해석       │
│  3. JSESSIONID 쿠키에서 세션 복원            │
│  4. ServletRequestContext 구성               │
│  5. ServletRequestListener.requestInitialized│
│  6. SimpleFilterChain 실행                   │
│     ├── Filter 1 (before)                    │
│     ├── Filter 2 (before)                    │
│     ├── Servlet.service()                    │
│     ├── Filter 2 (after)                     │
│     └── Filter 1 (after)                     │
│  7. ServletRequestListener.requestDestroyed  │
│  8. ServletResponseContext → FullHttpResponse │
└─────────────────────────────────────────────┘
```

## 설계 결정

### Netty는 컨테이너 계약 아래에 위치

Netty는 전송 및 프로토콜 엔진으로만 사용한다. 애플리케이션 코드는 Netty API에 직접 접근하지 않으며, 서블릿 호환 컨테이너 계층을 통해 실행된다.

### 서블릿 호환은 별도 모듈

서블릿 적응(adaptation)은 `was-servlet-core`에 위치하여 프로토콜/전송 관심사가 애플리케이션 생명주기 및 배포 로직과 격리된다.

### 동적 프록시 기반 서블릿 API

`ServletProxyFactory`는 `java.lang.reflect.Proxy`를 사용하여 `HttpServletRequest`, `HttpServletResponse`, `ServletContext`, `HttpSession` 등의 인터페이스를 동적으로 구현한다.

**장점:**
- Jakarta Servlet API의 많은 메서드를 개별 클래스 없이 구현
- switch 표현식으로 메서드별 동작을 한 곳에서 관리
- 미구현 메서드는 반환 타입에 따른 기본값 자동 반환

**트레이드오프:**
- 컴파일 시점 타입 안전성 없음 (메서드명 문자열 매칭)
- 리플렉션 오버헤드 (성능 크리티컬 경로에서 고려 필요)

### RequestDispatcher 구현

forward/include는 `InternalRequestBridge`/`InternalResponseBridge` 인터페이스를 통해 프록시된 요청/응답 객체에서 내부 컨텍스트를 추출하여 재디스패치한다.

- **forward**: 응답 버퍼 리셋 후 대상 서블릿 실행, `FORWARD_*` 속성 설정
- **include**: 기존 응답 유지하며 대상 서블릿 출력 추가, `INCLUDE_*` 속성 설정
- 상대 경로 해석: `..` 세그먼트를 포함한 경로 정규화 지원

### TLS 리로드는 connection-safe

TLS 자료는 리로드 주기와 파일 수정 시간을 기반으로 새 연결에 대해서만 갱신된다. 기존 연결은 이전에 협상된 TLS 컨텍스트를 계속 사용한다.

### 세션 관리

- `JSESSIONID` 쿠키 기반 in-memory 세션
- `ConcurrentHashMap`을 사용한 스레드 안전 저장소
- 세션 ID는 UUID 기반 (하이픈 제거)
- `HttpOnly` 쿠키로 발급

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

## 현재 구현 범위

- Maven 멀티모듈 프로젝트 레이아웃
- YAML 기반 서버 설정 로딩 및 검증
- Netty HTTP/1.1 런타임 (graceful shutdown 포함)
- 네이티브 전송 자동 선택 (epoll/kqueue/nio)
- TLS 부트스트랩 및 인증서 핫 리로드
- 서블릿 컨테이너: HttpServlet dispatch, Filter chain, Listener lifecycle
- RequestDispatcher forward/include (상대 경로 포함)
- DispatcherType 기반 필터 매칭
- In-memory JSESSIONID 세션
- 내장 health/info 엔드포인트
- 보안 헤더 기본 포함
- **Admin CLI**: 14개 카테고리 73개 명령어, JLine 인터랙티브 셸, JMX 통합 ([상세 문서](admin-cli.md))

## 근시일 백로그

1. **비동기 서블릿**: AsyncContext 지원
2. **WAR 배포**: 애플리케이션 클래스로더 격리
3. **구조화된 로깅**: 액세스 로그, 에러 로그, 감사 로그
4. **관리 API**: Admin REST 엔드포인트 (CLI의 RemoteAdminClient 연동)
5. **HTTP/2**: ALPN + WebSocket 업그레이드
6. **세션 만료**: TTL 기반 세션 정리 스케줄러
7. **JNDI / DataSource**: SPI 기반 리소스 관리
