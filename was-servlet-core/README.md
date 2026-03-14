# was-servlet-core

Jakarta Servlet 6.1 호환 컨테이너 모듈. Netty HTTP 요청을 서블릿 스펙에 맞게 변환하여 `HttpServlet`, `Filter`, `Listener`를 실행한다.

## 핵심 인터페이스

### `ServletContainer`

컨테이너의 최상위 인터페이스.

```java
public interface ServletContainer {
    void deploy(ServletApplication application) throws Exception;
    void undeploy(String applicationName) throws Exception;
    FullHttpResponse handle(HttpExchange exchange);
}
```

### `ServletApplication`

배포 가능한 서블릿 애플리케이션 정의.

```java
public interface ServletApplication {
    String name();
    String contextPath();
    ClassLoader classLoader();
    Map<String, Servlet> servlets();
    List<FilterRegistrationSpec> filters();
    List<ServletContextListener> servletContextListeners();
    List<ServletRequestListener> servletRequestListeners();
    Map<String, String> initParameters();
}
```

## 구현 클래스

### `SimpleServletContainer`

`ServletContainer`의 기본 구현. 주요 기능:

- **애플리케이션 배포**: context path 기반 다중 앱 배포, 서블릿/필터 `init()` 호출, 리스너 `contextInitialized()` 실행
- **요청 라우팅**: context path -> 서블릿 매핑 순으로 longest-match 해석
- **필터 체인**: `DispatcherType`과 URL 패턴에 따라 필터를 선별하여 체인 실행
- **세션 관리**: `JSESSIONID` 쿠키 기반 in-memory 세션
- **RequestDispatcher**: `forward()`와 `include()` 지원, 상대 경로(`..` 포함) 해석
- **언디플로이**: 서블릿 `destroy()`, 필터 `destroy()`, 리스너 `contextDestroyed()` 순서대로 호출

### `SimpleServletApplication`

`ServletApplication`의 빌더 패턴 구현.

```java
SimpleServletApplication.builder("app-name", "/context")
    .servlet("/path", new MyServlet())
    .filter(new MyFilter())
    .filter("/specific", new PathFilter(), DispatcherType.FORWARD)
    .servletContextListener(new MyListener())
    .servletRequestListener(new MyRequestListener())
    .initParameter("key", "value")
    .classLoader(customClassLoader)
    .build();
```

### `FilterRegistrationSpec`

필터 등록 정보를 담는 레코드.

| 필드 | 설명 |
|---|---|
| `pathPattern` | URL 매칭 패턴 (`/*`, `/path`, `/path/*`) |
| `filter` | `jakarta.servlet.Filter` 인스턴스 |
| `dispatcherTypes` | 필터가 적용될 `DispatcherType` 집합 |

**패턴 매칭 규칙:**
- `/*`: 모든 경로에 매칭
- `/path/*`: prefix 매칭
- `/path`: exact 매칭

### `ServletProxyFactory`

`java.lang.reflect.Proxy`를 사용하여 Jakarta Servlet API 인터페이스의 동적 프록시를 생성하는 팩토리.

| 생성 대상 | 프록시 인터페이스 |
|---|---|
| `createServletContext(...)` | `ServletContext` |
| `createServletConfig(...)` | `ServletConfig` |
| `createFilterConfig(...)` | `FilterConfig` |
| `createRequest(...)` | `HttpServletRequest`, `InternalRequestBridge` |
| `createResponse(...)` | `HttpServletResponse`, `InternalResponseBridge` |
| `createSessionProxy(...)` | `HttpSession` |
| `createRequestDispatcher(...)` | `RequestDispatcher` |

각 프록시는 switch 표현식으로 메서드별 동작을 정의하며, 미구현 메서드는 반환 타입에 맞는 기본값을 반환한다.

### `ServletRequestContext`

HTTP 요청의 서블릿 컨텍스트 정보를 관리하는 내부 클래스.

- Netty `HttpExchange`에서 추출한 요청 메타데이터 보유
- 속성(attributes), 파라미터, 헤더, 쿠키, 세션 상태 관리
- `forDispatch()`: RequestDispatcher에 의한 forward/include 시 새 컨텍스트 생성

### `ServletResponseContext`

HTTP 응답 버퍼를 관리하는 내부 클래스.

- `ServletBodyOutputStream` 기반 응답 본문 버퍼링
- 헤더, 쿠키, 상태 코드, content type 관리
- `toNettyResponse()`: 버퍼된 응답을 Netty `FullHttpResponse`로 변환
- `reset()`: forward 시 응답 버퍼 초기화 (미커밋 상태에서만)

### `SimpleFilterChain`

필터 목록을 순차 실행하고, 마지막에 서블릿의 `service()`를 호출하는 `FilterChain` 구현.

### `InMemoryHttpSessionStore`

`ConcurrentHashMap` 기반 in-memory 세션 저장소.

- `create()`: UUID 기반 세션 ID 생성
- `find(sessionId)`: 세션 조회
- `invalidate(sessionId)`: 세션 제거

### `SessionState`

개별 세션의 상태를 보유하는 클래스.

- 세션 ID, 생성 시간, 마지막 접근 시간
- `ConcurrentHashMap` 기반 속성 저장소
- `invalidate()`: 세션 무효화 및 속성 초기화

### I/O 스트림

| 클래스 | 설명 |
|---|---|
| `ServletBodyInputStream` | `ByteArrayInputStream` 기반 `ServletInputStream` 구현 |
| `ServletBodyOutputStream` | `ByteArrayOutputStream` 기반 `ServletOutputStream` 구현 |

두 스트림 모두 비동기 I/O(`ReadListener`/`WriteListener`)는 미구현 상태이다.

### 브리지 인터페이스

| 인터페이스 | 설명 |
|---|---|
| `InternalRequestBridge` | 프록시된 `HttpServletRequest`에서 내부 `ServletRequestContext`를 추출 |
| `InternalResponseBridge` | 프록시된 `HttpServletResponse`에서 내부 `ServletResponseContext`를 추출 |

RequestDispatcher의 forward/include 시 내부 컨텍스트 객체에 직접 접근하기 위해 사용된다.

## 서블릿 스펙 지원 현황

| 기능 | 상태 |
|---|---|
| `HttpServlet.service()` dispatch | 지원 |
| `Filter` chain 실행 | 지원 |
| `DispatcherType` 기반 필터 매칭 | 지원 |
| `ServletContextListener` | 지원 |
| `ServletRequestListener` | 지원 |
| `RequestDispatcher.forward()` | 지원 |
| `RequestDispatcher.include()` | 지원 |
| 상대 경로 dispatch (`..` 포함) | 지원 |
| `HttpSession` (JSESSIONID 쿠키) | 지원 |
| `ServletContext` 속성 | 지원 |
| 비동기 서블릿 (AsyncContext) | 미구현 |
| 멀티파트 파일 업로드 | 미구현 |
| WAR 배포 / 클래스로더 격리 | 미구현 |
| JNDI / DataSource | 미구현 |
| JSP / 표현 언어 | 미구현 |

## 테스트

`SimpleServletContainerTest`에서 다음을 검증한다:

1. **서블릿 디스패치 + 세션 지속**: 필터/리스너 실행 순서, context path/servlet path, 세션 쿠키 발급, 방문 횟수 누적
2. **RequestDispatcher forward/include**: 응답 버퍼 리셋(forward), 응답 합성(include), dispatcher 속성 설정, DispatcherType별 필터 매칭, 상대 경로 해석

## 의존성

- `was-protocol-http` (내부)
- `io.netty:netty-codec-http`
- `jakarta.servlet:jakarta.servlet-api:6.1.0`
- `org.junit.jupiter:junit-jupiter` (test)
