# Velo WAS 상세 아키텍처 가이드

본 문서는 Velo WAS의 내부 기술 설계 및 모듈 간의 상호작용 방식에 대해 심도 있게 다룹니다. 기존 `architecture.md`가 전체적인 시스템의 개요를 나타낸다면, 본 문서는 HTTP 요청이 어떤 경로로 유입되고 번역되어 최종적으로 Jakarta Servlet 애플리케이션에 전달되는지 해부합니다.

## 1. 핵심 모듈 책임 분리

시스템은 네트워크 전송 계층과 서블릿 애플리케이션 로직 간의 엄격한 경계 분리를 위해 다중 Maven 모듈로 나뉘어 구성되어 있습니다.

### 1.1. `was-transport-netty` (네트워크 유입 계층)
클라이언트의 물리적 요청이 최초 당도하는 엔트리포인트 모듈입니다.
- **네이티브 트랜스포트 지원**: `NativeTransportSelector`를 통하여 구동되는 OS 환경에 맞춰 `epoll`(Linux), `kqueue`(macOS), 또는 기본 `NIO` 채널을 할당하고 서버 소켓 포트를 바인딩합니다.
- **TLS 파이프라인**: 동적인 인증서 교체가 가능한 `ReloadingSslContextProvider`를 사용하여, 활성화된 세션을 끊지 않고도 X.509 인증서의 Hot-Reloading을 지원합니다.
- **HTTP 프로토콜 협상 (ALPN/h2c)**: 보안 연결(TLS)시 ALPN(Application-Layer Protocol Negotiation)을 이용해 HTTP/2와 HTTP/1.1을 동적으로 협상합니다. 평문(clear-text) 통신의 경우 `CleartextHttp2ServerUpgradeHandler`를 바탕으로 `h2c` 업그레이드를 지원합니다.

### 1.2. `was-protocol-http` (프로토콜 추상화 계층)
Netty 코덱을 통과한 파편화된 데이터는 이 미들웨어 계층에서 조립됩니다. Netty 의존성이 서블릿 계층까지 전파되는 것을 막기 위해 `HttpExchange`라는 인터페이스 레코드를 사용합니다.
- **`HttpExchange`**: 요청의 Method, URI, Header, 본문 데이터, 클라이언트의 IP 주소 등을 포괄하는 공통 데이터 허브입니다. 특정 Netty Handler 구현체에 결합되지 않은 중립적인 구조를 취합니다.
- **`HttpHandlerRegistry`**: `/health`나 `/info`와 같이 애플리케이션 배포와 관계없이 기본 내장된 관측성 포인트들을 가벼운 패턴 매칭으로 라우팅합니다.
- **`NettyHttpChannelHandler`**: Netty의 `SimpleChannelInboundHandler`를 상속하여 `FullHttpRequest`를 중간 매체물인 `HttpExchange`로 변환합니다. 우선 내장 핸들러가 처리할 수 있는지를 확인한 뒤, 처리가 불가한 일반 애플리케이션 트래픽이면 Fallback으로 서블릿 컨테이너(`ServletContainer`)에 요청의 처리를 이임합니다.

### 1.3. `was-servlet-core` (Jakarta Servlet 스펙 구현 계층)
Velo WAS의 핵심적인 스펙 호환 엔진이자 애플리케이션 구동의 심장부입니다.
- **`SimpleServletContainer`**: 사용자가 배포한 다수의 `ServletApplication`들의 생명주기를 총괄합니다. 유입된 패킷의 Request URI를 분석하여 어느 Context Path가 매칭되는지 식별합니다.
- **프록시 기반 스펙 매핑 (`ServletProxyFactory`)**: Apache Tomcat 등과 같이 비대한 오버헤드의 `HttpServletRequest` 어댑터를 수동 구현하지 않습니다. Velo WAS는 JDK 동적 프록시(`java.lang.reflect.Proxy`)를 사용하여 Jakarta Servlet API 메서드 호출을 가로채고, 대상이 되는 `HttpExchange` 속성에 알맞은 값을 동적으로 반환해줍니다.
- **서블릿 요청 라이프사이클**:
  1. 식별된 `ServletApplication` 내에서 적절한 타겟 `Servlet`을 찾아냅니다.
  2. 세션 정보를 검사하고 메모리 스토어에서 상태값(`SessionState`)을 적재합니다 (`JSESSIONID` 분석).
  3. `SimpleFilterChain` 체인을 초기화로 구성합니다.
  4. `ServletRequestListener.requestInitialized()` 이벤트 사이클을 전파합니다.
  5. Servlet Filter들을 순차적으로 통과시킵니다.
  6. 최종적으로 타겟 애플리케이션의 `Servlet.service()` 메서드를 호출합니다.
  7. `ServletRequestListener.requestDestroyed()` 이벤트를 호출하여 완료를 알립니다.

## 2. 심층 분석: 동적 프록시 처리 메커니즘

Velo WAS는 런타임 성능 저하를 감수하면서 코드 관리의 응집도를 높이기 위해 프록시에 의한 동적 라우팅 방식을 취합니다. 
예를 들어 `request.getHeader("Authorization")`를 애플리케이션에서 호출할 경우 내부의 `ServletProxyFactory` 인터셉터가 발동합니다. 인터셉터는 호출된 메서드의 명칭(`"getHeader"`)을 Switch-Case 구조로 식별하고는 내장된 `HttpExchange`의 Netty Http Header 값을 읽어 반환합니다. 구현되지 않은 불필요 메서드의 경우 안전한 기본값(빈 컬렉션, null 등)을 반환하거나 `UnsupportedOperationException`을 엄격히 송출합니다.

## 3. Request Dispatcher (내부 라우터 브릿지)

사용자 애플리케이션에서 `request.getRequestDispatcher("/other").forward(req, res)` 나 `.include()`를 호출하면, 서버 내부에서 별개의 실행 흐름 조작이 이루어져야 합니다.
이를 위해 `InternalRequestBridge` 및 `InternalResponseBridge` 인터페이스가 기능합니다.
- **forward (포워드)**: 진행 중이던 응답 출력 버퍼의 기록을 전부 초기화/삭제하고, 명시된 대상 경로로 새로운 서블릿 필터 체인을 구동하여 완전히 제어권을 넘깁니다.
- **include (인클루드)**: 현재 작성 중인 출력 흐름의 상태를 영속적으로 보존하면서, 포함시킬 서블릿의 출력을 추가 기록 후 다시 메인 메서드로 복구시킵니다.
- 두 경우 모두 `..` 와 같은 상대 경로 디렉토리 계산 세그먼트를 정규화하여 경로 탈주(Path Traversal) 오류를 자체 예방합니다.

## 4. 세션(Session)의 지속 상태 처리 구조

무상태의 HTTP에 상태를 부여하고자 `InMemoryHttpSessionStore`를 사용합니다.
- `SessionState` 래퍼 클래스는 등록된 속성, 생성 시각 및 마지막 방문 기록 정보를 관리합니다.
- 세션 키 식별자는 내부적으로 `java.util.UUID` 규격을 바탕으로 하이픈(`-`)을 제외한 해시 난수를 발급합니다.
- 발급된 세션 키는 표준 스펙에 의거해 `HttpOnly` 속성을 기본으로 띄는 `JSESSIONID` 쿠키 값으로 클라이언트 브라우저에 하달됩니다.
- 스케줄러가 자체적으로 마지막 접속(`lastAccessedTime`) 기준 `maxInactiveIntervalSeconds` 설정값을 초과한 세션을 메모리 내부에서 점진적으로 무효화(Invalidation)하고 제거합니다.
