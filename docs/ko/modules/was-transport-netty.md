# `was-transport-netty` 모듈 가이드

`was-transport-netty` 모듈은 네트워크 환경 유입에 대한 기본적인 토대를 제공합니다. Netty 라이브러리를 통해 네트워크 인터페이스를 바인딩하고 가동하며, 기본적인 로우레벨 소켓 옵션을 매니징합니다.

## 핵심 담당 구역

### 1. 트랜스포트 계층 동적 선택 (`NativeTransportSelector`)
Velo WAS는 최대의 효율성 성능을 지향합니다. Java가 기본 제공하는 무거운 `NIO`에 국한되지 않고 구동되는 호스트 운영체제를 실시간 식별합니다.
- **Linux**: 엣지 트리거링(Edge-Triggered) 다중화 처리를 위해 `Epoll` 네이티브 확장을 로드합니다.
- **macOS / BSD**: Mac 환경을 위한 `KQueue` 확장을 로드합니다.
- **Windows / 기타**: 전 우주적 호환성을 보장하는 기본 스탠다드 `NIO` 시스템으로 폴백 조치합니다.
`was-config`에서 설정된 대로 동적으로 OS 코어에 대비한 최상의 이벤트 루프망(`bossGroup` 및 `workerGroup`)을 형성합니다.

### 2. `NettyServer` 생명주기 관리
Netty 계층의 가장 메인이 되는 관현악 오케스트레이션 클래스입니다.
- 백로그 제어, TCP 통신 지연 방지 플래그, Keep-Alive 제반 조건 등과 함께 `ServerBootstrap` 인스턴스를 초기 구동시킵니다.
- **부팅(Booting)**: 포트 점유 여부를 식별하고 완고하게 소켓을 바인딩하기 위해 `.sync()` 구조를 활용합니다.
- **점진적 종료(Graceful Shutdown)**: Netty의 종료 훅(Hook) 구조를 따릅니다. 서버 `close()` 명령 진입 시 내부 이벤트 루프에 새로운 요청 처리를 모두 반려하도록 명령시킨 통과중인(In-flight) 잔존 데이터스트림을 비운 뒤 메모리를 폐기합니다.

### 3. 인프라 보안 파이프라인 구성 (TLS & ALPN)
설정에서 TLS 암호화가 활성화 되면:
- 통신 파이프라인 최상위에 안전하게 `SslHandler`를 끼워넣습니다.
- `ReloadingSslContextProvider`가 실시간으로 인증서의 변동성을 관리합니다. 서버 운영중에 백그라운드 워커가 구성에 기입된 인증서 파일(PEM/Keystore)의 파일 갱신 시간을 탐지합니다. 최신본이 탐지되면 활성화된 SSL Context를 치환합니다. 기존 접속된 세션의 암호화 무결성을 지키면서 새로운 연결 요청에만 즉각 교체된 새 인증서를 건네어 보안을 유지합니다.
- ALPN (방어계층 프로토콜 식별 협상, Application-Layer Protocol Negotiation) 기법인 `AlpnNegotiationHandler`를 이용해서, `http/1.1` 호출만 수용할지 혹은 최신 브라우저 통신에 걸맞게 `h2` (HTTP/2) 체계로 통신할지 동적 캡슐화를 진행합니다.

### 4. 평문(Cleartext) 업그레이드 지원 파이프라인 (`h2c`)
TLS 보안 레이어가 적용되지 않은 80포트 통신 영역의 경우 모듈이 `CleartextHttp2ServerUpgradeHandler`를 설치합니다. 브라우저나 외부 클라이언트는 `Upgrade: h2c` 헤더를 담거나, 초기 연결 시 HTTP/2 Prior Knowledge 텍스트 데이터(`PRI *`)를 직접 날림으로써, 암호화 환경이 아닌 비보안 통신에서도 HTTP/1.1 프로토콜 이상의 효율인 HTTP/2 멀티플렉싱을 사용할 수 있도록 보장합니다.
