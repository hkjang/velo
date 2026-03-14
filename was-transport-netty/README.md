# was-transport-netty

Netty 서버 부트스트랩 및 네트워크 전송 계층 모듈. 서버 바인딩, 채널 파이프라인 구성, TLS 핸들링, 네이티브 전송 선택을 담당한다.

## 클래스 목록

### `NettyServer`

서버 생명주기 관리 핵심 클래스. `AutoCloseable`을 구현한다.

| 메서드 | 설명 |
|---|---|
| `start()` | boss/worker EventLoopGroup 생성, 채널 파이프라인 구성, 서버 바인딩 |
| `blockUntilShutdown()` | 서버 채널이 닫힐 때까지 현재 스레드 블로킹 |
| `close()` | 서버 채널 닫기 + EventLoopGroup graceful shutdown |

**채널 파이프라인 구성:**

```
[ssl]  ->  httpCodec  ->  httpAggregator  ->  chunkedWriter  ->  httpHandler
(선택적)    (코덱)        (집합)             (청크 전송)       (비즈니스 로직)
```

- `ssl`: TLS 활성화 시에만 추가. `ReloadingSslContextProvider`에서 현재 SSL 컨텍스트를 가져온다.
- `httpCodec`: `HttpServerCodec` - HTTP 인코딩/디코딩
- `httpAggregator`: `HttpObjectAggregator` - 청크 메시지를 `FullHttpRequest`로 집합
- `chunkedWriter`: `ChunkedWriteHandler` - 대용량 응답의 청크 전송 지원
- `httpHandler`: `NettyHttpChannelHandler` - 비즈니스 로직 진입점

### `NativeTransportSelector`

운영체제별 최적 전송 계층 자동 선택 유틸리티.

| 환경 | EventLoopGroup | ServerChannel | 이름 |
|---|---|---|---|
| Linux (epoll 사용 가능) | `EpollEventLoopGroup` | `EpollServerSocketChannel` | `epoll` |
| macOS (kqueue 사용 가능) | `KQueueEventLoopGroup` | `KQueueServerSocketChannel` | `kqueue` |
| 그 외 (Windows 등) | `NioEventLoopGroup` | `NioServerSocketChannel` | `nio` |

### `ReloadingSslContextProvider`

TLS 인증서 핫 리로드를 지원하는 SSL 컨텍스트 제공자.

**리로드 로직:**
1. `reloadIntervalSeconds` 경과 여부 확인
2. 마커 파일(인증서/키스토어)의 수정 시간이 마지막 로드 시점 이후인지 확인
3. 두 조건 모두 충족 시 `SslContext`를 새로 빌드하여 `AtomicReference`에 저장
4. 기존 연결은 이전 SSL 컨텍스트를 계속 사용 (connection-safe)

**지원 모드:**
- `PEM`: cert chain 파일 + private key 파일
- `PKCS12`: KeyStore 파일 기반

## 의존성

- `was-config`, `was-protocol-http` (내부)
- `io.netty:netty-handler`
- `io.netty:netty-transport-classes-epoll`, `netty-transport-classes-kqueue`
- `io.netty:netty-transport-native-epoll` (linux-x86_64, runtime)
- `io.netty:netty-transport-native-kqueue` (osx-x86_64, runtime)
- `org.slf4j:slf4j-api`
