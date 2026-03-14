# `was-protocol-http` 모듈 가이드

이 모듈은 최하단 네트워크 프레임과 논리적인 애플리케이션 개념 사이의 다리가 되어줍니다. Netty의 기본 HTTP 코덱들은 물리적인 ByteBuf 구조나 Netty 고유의 파이프라인(Channel) 이벤트 처리 흐름에 강하게 종속되어 있습니다. `was-protocol-http` 모듈은 완충 계층 역할을 하여 보다 상위 층인 서블릿 엔진들이 Netty 구현체에 직접 의존하지 않도록 격리시켜 줍니다.

## 주요 인터페이스 및 클래스

### 1. `HttpExchange` 레코드(Record)
이 모듈의 핵심은 `HttpExchange` 레코드입니다. 단일 HTTP 트래픽의 모든 수명 주기를 캡슐화합니다.
- 헤더, 본문(Payload) 및 URI 경로를 포함하는 `FullHttpRequest`를 저장합니다.
- 물리적 연결 메타데이터인 `remoteAddress`와 `localAddress`를 보관합니다.
- 애플리케이션 수행 후 결과를 다시 파이프라인 밖으로 배출할 때 사용될 `ResponseSink` 의존성을 전달합니다.

### 2. 내장 추상 핸들러
무거운 서블릿 엔진에 진입하기 앞서, 가벼운 내부 처리를 위한 `HttpHandlerRegistry`를 내포하고 있습니다.

#### 2.1 패턴 매칭 라우터
이 레지스트리는 내부 엔진 엔드포인트를 식별하기 위해 가장 빠른 완전 일치(Exact-Match) 혹은 접두사(Prefix-Match) 기반의 라우팅 매커니즘을 제공합니다. 애플리케이션 필터 체인을 완전히 우회하는 초고속 라우터 역할을 갖습니다.

#### 2.2 기본 제공 엔드포인트
기본적으로 `/health` (활성/생존 여부 검사용) 이나 `/info` (시스템 통계 파악용) 와 같은 모듈들이 이곳에 묶여 있습니다. 이 엔드포인트들은 프록시 생성이나 서블릿 컨텍스트를 거치지 않으므로 서버 운영에 거의 오버헤드를 발생시키지 않습니다.

### 3. Netty 연동 (`NettyHttpChannelHandler`)
이 핸들러는 평문 또는 양방향 TLS 파이프라인의 종단부에 위치합니다.
1. Netty가 읽어들인 인바운드 HTTP 데이터 객체 모듈을 인계 받습니다.
2. HTTP 메시지들을 통합하여 하나로 조립합나다 (내부에 위치한 `HttpObjectAggregator` 사용).
3. 이를 포설하여 범용 포맷인 `HttpExchange`를 구축합니다.
4. `HttpHandlerRegistry`에 경로가 매칭되는지 탐색합니다. 있다면 내부 내장 핸들러를 가동시킵니다.
5. 경로가 매치되지 않는다면, 즉 일반 웹 애플리케이션 호출이라면 `was-servlet-core` 모듈에 매핑된 최종 `ServletContainer` 에게 처리를 위임하고 빠집니다.

### 4. WebSocket 통합 추상화
이 모듈은 HTTP 외에도 `WebSocketHandlerRegistry` 와 `NettyWebSocketFrameHandler` 를 갖추고 있습니다.
초기 연결 구조에서 WebSocket 업그레이드 협상이 완료되면 (혹은 라우터에 매칭된 웹소켓 URI라면), 실행 파이프라인 구조를 동적으로 변경합니다. HTTP용 핸들러들이 모두 제거되고 소켓 통신을 위한 Netty `WebSocketFrame` 이 그 자체 이벤트인 `WebSocketSession` API 단으로 전환됩니다.
