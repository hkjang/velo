# Velo WAS 리스너(Listener) 가이드

Velo WAS는 특정 포트와 프로토콜에 바인딩되어 외부 트래픽을 허용하는 복수의 '리스너'를 동시 운영합니다. Velo WAS의 주요 엔진은 크게 HTTP(S) 기반의 웹 애플리케이션 리스너와 원시 TCP 프로토콜을 관장하는 TCP 리스너 두 가지 축으로 나뉘게 됩니다.

## 1. HTTP 리스너 (`server.listener`)

서버 구동 시 가장 기본이 되는 핵심 포트로, `was-transport-netty` 안에서 초기화됩니다.
일반적인 서블릿 컨텍스트 트래픽과 WebSocket 통신을 모두 담당합니다.

### 주요 기능
- **ALPN 및 HTTP/2 지원**: TLS 인증 과정에서 애플리케이션 계층 프로토콜 협상(ALPN)을 지원하여 HTTP/1.1 클라이언트와 HTTP/2 클라이언트를 단일 포트에서 판별하고 통신합니다.
- **`h2c` 평문 업그레이드**: 암호화가 안 된 80이나 8080 포트 통신에서도 `Upgrade: h2c` 헤더를 통해 온전한 HTTP/2 통신으로 전환이 가능합니다.
- **동적 인증서 갱신(Hot-Reload)**: `ReloadingSslContextProvider`를 탑재하여, OS 단의 파일시스템 수정 감지 기능을 통해 서버 재기동 없이 보안 인증서를 교체하여 새 연결(Connection)부터 자동으로 암호화를 적용합니다.

### 구성 방식 (`server.yaml`)
```yaml
server:
  listener:
    host: 0.0.0.0
    port: 8080
    maxContentLength: 10485760 # 최대 파일 업로드/본문 규격
  tls:
    enabled: true
    mode: PEM
    certChainFile: /cert/server.crt
    privateKeyFile: /cert/server.key
```

## 2. TCP 다중 리스너 (`server.tcpListeners`)

순수 웹 프로토콜의 한계를 극복하기 위해 `was-tcp-listener` 모듈에 위임된 게이트웨이 영역입니다.

### 주요 기능
- **프레이밍 자율화**: 연속된 바이트 스트림에서 메시지 규격을 자르기 위해 `RAW`, `LINE`, `DELIMITER`, 또는 `LENGTH_FIELD` 기반의 프레임 파서를 자유롭게 채택할 수 있습니다.
- **세분화된 보안 제어**: HTTP 스레드에 영향을 주지 않고, 각 포트별로 방화벽 수준의 CIDR ACL 필터링, 토큰 버킷 기반의 동시 접속 제한(`maxConnections`), 및 초당 IP 생성 제한(`perIpRateLimit`)을 거칠 수 있습니다.
- **스레드 풀 격리**: TCP 전용 `workerThreads`와 `businessThreads`를 할당하므로, 아무리 무거운 패킷 연산이 수반되더라도 기존 웹 애플리케이션의 처리량(Throughput)이 저하되지 않습니다.

### 구성 방식 (`server.yaml`)
```yaml
server:
  tcpListeners:
    - name: "custom-tcp-gateway"
      host: 0.0.0.0
      port: 9090
      framing: LENGTH_FIELD
      maxConnections: 5000
      businessThreads: 4
```

## `velo-admin`을 통한 모니터링

`velo-admin` 관리용 CLI에서는 이러한 하위 계층 네트워크 소켓들의 상태를 즉시 도출해 냅니다.

- **`server-info`**: 바인딩 중인 기본 HTTP의 포트 번호, TLS 활성 유무를 체크합니다.
- **`resource-info`**: 현재 포트 체류 중인 연결 개수와 한계치, 그리고 그에 맞춰 활성화된 Netty 스레드의 상태를 프로파일링하여 나타냅니다.
