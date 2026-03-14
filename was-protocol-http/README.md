# was-protocol-http

HTTP 프로토콜 계층 모듈. Netty의 `FullHttpRequest`/`FullHttpResponse`를 기반으로 요청 라우팅과 응답 생성을 담당한다.

## 클래스 목록

### `HttpExchange`

```java
public record HttpExchange(
    FullHttpRequest request,
    SocketAddress remoteAddress,
    SocketAddress localAddress
)
```

하나의 HTTP 요청/응답 사이클을 표현하는 불변 레코드. `uri()`, `path()`, `headers()` 편의 메서드를 제공한다.

### `HttpHandler`

```java
public interface HttpHandler {
    FullHttpResponse handle(HttpExchange exchange);
}
```

모든 HTTP 핸들러가 구현하는 함수형 인터페이스. `HttpHandlerRegistry`에 등록된다.

### `HttpHandlerRegistry`

경로 기반 핸들러 라우팅 레지스트리.

| 메서드 | 설명 |
|---|---|
| `registerGet(path, handler)` | GET 경로에 핸들러를 등록 |
| `fallback(handler)` | 매칭되지 않는 요청에 대한 폴백 핸들러 설정 |
| `resolve(exchange)` | 요청 경로에 매칭되는 핸들러 반환 (없으면 폴백) |

내부적으로 `ConcurrentHashMap`을 사용하므로 런타임 등록이 스레드 안전하다.

### `NettyHttpChannelHandler`

Netty `SimpleChannelInboundHandler<FullHttpRequest>`를 확장한 채널 핸들러.

- `channelRead0`: 디코딩 결과 검사 -> `HttpExchange` 생성 -> 레지스트리에서 핸들러 resolve -> 응답 작성
- keep-alive 헤더 처리 및 access 로그 출력
- 예외 발생 시 500 응답 반환 후 로그

### `HttpResponses`

HTTP 응답 팩토리 유틸리티. 모든 응답에 보안 헤더를 기본 포함한다.

| 메서드 | HTTP 상태 |
|---|---|
| `jsonOk(json)` | 200 OK |
| `notFound(message)` | 404 Not Found |
| `methodNotAllowed(message)` | 405 Method Not Allowed |
| `badRequest(message)` | 400 Bad Request |
| `serverError(message)` | 500 Internal Server Error |

기본 보안 헤더:
- `Content-Type: application/json; charset=UTF-8`
- `Cache-Control: no-store`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: no-referrer`

## 의존성

- `io.netty:netty-codec-http`
- `org.slf4j:slf4j-api`
