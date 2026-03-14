# HTTP/2 + WebSocket

Netty 기반 HTTP/2 (TLS ALPN + 클리어텍스트 h2c) 및 WebSocket 프로토콜을 지원한다.

## 모듈

- `was-transport-netty` — HTTP/2 파이프라인 구성
- `was-protocol-http` — WebSocket 핸들러 인터페이스 및 구현

## HTTP/2 아키텍처

### TLS (ALPN 협상)

```
클라이언트 ─── TLS Handshake + ALPN ──→ NettyServer
                                          │
                                 ┌────────┴────────┐
                                 │ SslHandler       │
                                 │ AlpnNegotiation  │
                                 │   Handler        │
                                 └────┬────────┬────┘
                                      │        │
                              h2 협상  │        │ http/1.1 협상
                                      ▼        ▼
                               ┌──────────┐ ┌──────────────┐
                               │ HTTP/2   │ │ HTTP/1.1     │
                               │ Pipeline │ │ Pipeline     │
                               └──────────┘ └──────────────┘
```

**AlpnNegotiationHandler:**

TLS 핸드셰이크 완료 후 협상된 프로토콜에 따라 파이프라인을 구성한다.

| 협상 결과 | 파이프라인 구성 |
|---|---|
| `h2` | `Http2FrameCodec` → `Http2MultiplexHandler` |
| `http/1.1` | `HttpServerCodec` → `HttpObjectAggregator` → `ChunkedWriteHandler` → `NettyHttpChannelHandler` |

**SslContext ALPN 설정:**

- 광고 프로토콜: `h2`, `http/1.1` (순서대로 선호)
- SSL Provider: OpenSSL ALPN 지원 시 `OPENSSL`, 아니면 `JDK`
- 실패 동작: `NO_ADVERTISE` / `ACCEPT`

### 클리어텍스트 (h2c)

TLS 없이 HTTP/2를 사용하는 세 가지 시나리오를 모두 지원한다.

```
┌─────────────────────────────────────────────────────────┐
│ CleartextHttp2ServerUpgradeHandler                       │
│                                                         │
│  1. PRI * HTTP/2.0 preface → 직접 HTTP/2 연결           │
│  2. HTTP/1.1 Upgrade: h2c → HTTP/2 업그레이드           │
│  3. 일반 HTTP/1.1 → fallback HTTP/1.1 파이프라인        │
└─────────────────────────────────────────────────────────┘
```

| 시나리오 | 감지 방법 | 결과 |
|---|---|---|
| Direct HTTP/2 | `PRI *` preface 감지 | `Http2FrameCodec` + `Http2MultiplexHandler` |
| HTTP/1.1 → h2c 업그레이드 | `Upgrade: h2c` 헤더 | `Http2ServerUpgradeCodec`로 업그레이드 |
| Plain HTTP/1.1 | 위 두 경우에 해당 안 함 | 기존 HTTP/1.1 파이프라인 |

### HTTP/2 스트림 멀티플렉싱

`Http2MultiplexHandler`가 HTTP/2 프레임을 가상 채널(stream)으로 분리한다.

```
TCP 연결 (1개)
  │
  ├── Stream 1 → Http2StreamFrameToHttpObjectCodec → HttpObjectAggregator → NettyHttpChannelHandler
  ├── Stream 3 → Http2StreamFrameToHttpObjectCodec → HttpObjectAggregator → NettyHttpChannelHandler
  └── Stream 5 → Http2StreamFrameToHttpObjectCodec → HttpObjectAggregator → NettyHttpChannelHandler
```

**핵심 설계:** `Http2StreamFrameToHttpObjectCodec`이 HTTP/2 프레임을 HTTP/1.1 객체(`FullHttpRequest`)로 변환하므로, 기존 `NettyHttpChannelHandler`를 수정 없이 HTTP/1.1과 HTTP/2 모두에서 사용한다.

**Http2StreamChannelInitializer** (per-stream 파이프라인):

```
Http2StreamFrameToHttpObjectCodec → HttpObjectAggregator → NettyHttpChannelHandler
```

## WebSocket 아키텍처

### 인터페이스

**WebSocketHandler** — 콜백 인터페이스:

```java
public interface WebSocketHandler {
    default void onOpen(WebSocketSession session) {}
    default void onText(WebSocketSession session, String message) {}
    default void onBinary(WebSocketSession session, byte[] data) {}
    default void onClose(WebSocketSession session, int statusCode, String reason) {}
    default void onError(WebSocketSession session, Throwable cause) {}
}
```

**WebSocketSession** — 세션 인터페이스:

| 메서드 | 설명 |
|---|---|
| `id()` | UUID 기반 세션 식별자 |
| `path()` | WebSocket 연결 경로 |
| `remoteAddress()` | 원격 주소 |
| `sendText(String)` | 텍스트 메시지 전송 |
| `sendBinary(byte[])` | 바이너리 메시지 전송 |
| `close()` | 정상 종료 (1000) |
| `close(int, String)` | 지정 코드로 종료 |
| `isOpen()` | 연결 활성 여부 |

**WebSocketHandlerRegistry** — 경로 기반 핸들러 레지스트리:

```java
WebSocketHandlerRegistry registry = new WebSocketHandlerRegistry();
registry.register("/ws/echo", new EchoHandler());
registry.register("/ws/chat", new ChatHandler());
```

### WebSocket 업그레이드 흐름

```
HTTP 요청 수신
    │
    ▼
NettyHttpChannelHandler.channelRead0()
    │
    ├── Connection: Upgrade + Upgrade: websocket 감지
    │
    ├── WebSocketHandlerRegistry에서 경로 매칭
    │     ├── 매칭 실패 → 404 응답
    │     └── 매칭 성공 ↓
    │
    ▼
installWebSocketPipeline()
    │
    ├── WebSocketServerProtocolHandler 추가 (핸드셰이크 처리)
    ├── NettyWebSocketFrameHandler 추가 (프레임 처리)
    ├── NettyHttpChannelHandler 제거
    └── 원본 요청 재전파 (핸드셰이크 트리거)
```

### NettyWebSocketFrameHandler

`SimpleChannelInboundHandler<WebSocketFrame>`을 확장한 프레임 처리기.

| 이벤트 | 처리 |
|---|---|
| `HandshakeComplete` 사용자 이벤트 | `NettyWebSocketSession` 생성 + `onOpen()` 호출 |
| `TextWebSocketFrame` | `onText()` 호출 |
| `BinaryWebSocketFrame` | `onBinary()` 호출 |
| `CloseWebSocketFrame` | `onClose()` 호출 |
| `PingWebSocketFrame` | `PongWebSocketFrame` 자동 응답 |
| `channelInactive` | 연결 유실 시 `onClose(1006, "Connection lost")` |
| `exceptionCaught` | `onError()` 호출 + `ErrorLog` 기록 + 채널 close |

### NettyWebSocketSession

`ChannelHandlerContext`를 래핑한 세션 구현체:

- UUID 기반 세션 ID
- `sendText()` / `sendBinary()`: 채널이 활성 상태일 때만 전송
- `close()`: `CloseWebSocketFrame` 전송 후 `ChannelFutureListener.CLOSE`로 채널 종료

## 소스 구조

```
was-transport-netty/src/main/java/io/velo/was/transport/netty/
├── NettyServer.java                  서버 부트스트랩 (TLS/h2c 분기)
├── AlpnNegotiationHandler.java       ALPN 프로토콜 협상
│   └── Http2StreamChannelInitializer HTTP/2 스트림별 파이프라인
└── ReloadingSslContextProvider.java  ALPN 설정 포함 TLS 컨텍스트

was-protocol-http/src/main/java/io/velo/was/http/
├── WebSocketHandler.java             WebSocket 콜백 인터페이스
├── WebSocketSession.java             WebSocket 세션 인터페이스
├── WebSocketHandlerRegistry.java     경로→핸들러 레지스트리
├── NettyWebSocketFrameHandler.java   프레임 처리 + 세션 구현
└── NettyHttpChannelHandler.java      WebSocket 업그레이드 감지
```

## 테스트

```bash
# was-transport-netty 테스트 (10개)
mvn test -pl was-transport-netty -am
```

| 테스트 | 검증 내용 |
|---|---|
| `http11_get_returns_200` | HTTP/1.1 기본 동작 |
| `http11_not_found` | HTTP/1.1 404 |
| `http11_multiple_requests_keep_alive` | HTTP/1.1 Keep-Alive |
| `h2c_upgrade_returns_200` | h2c 업그레이드 성공 |
| `h2c_multiple_streams` | HTTP/2 스트림 멀티플렉싱 |
| `websocket_echo` | WebSocket 텍스트 에코 |
| `websocket_binary` | WebSocket 바이너리 전송 |
| `websocket_lifecycle_events` | onOpen/onClose 생명주기 |
| `websocket_no_handler_returns_404` | 미등록 경로 404 |
| `server_starts_and_stops_cleanly` | 서버 생명주기 |
