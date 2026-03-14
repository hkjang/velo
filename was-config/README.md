# was-config

서버 설정 모델을 정의하는 모듈. YAML 설정 파일의 구조를 Java 클래스로 표현하며, 다른 모든 모듈이 참조하는 설정 계층의 루트이다.

## 클래스 구조

### `ServerConfiguration`

설정 파일 최상위 객체. `server` 섹션 하나를 포함한다.

| 내부 클래스 | 설명 |
|---|---|
| `Server` | 서버 이름, 노드 ID, graceful shutdown 타임아웃, 하위 설정 블록 보유 |
| `Listener` | 바인드 호스트/포트, SO_BACKLOG, TCP 옵션, 최대 요청 본문 크기 |
| `Threading` | boss/worker/business 스레드 수 (`workerThreads=0`이면 Netty 기본값 사용) |
| `Tls` | TLS 활성화 여부, PEM/PKCS12 모드, 인증서 경로, 프로토콜 목록, 리로드 주기 |

### `TlsMode`

TLS 키 자료 형식 열거형.

| 값 | 설명 |
|---|---|
| `PEM` | PEM 인증서 체인 + 개인키 파일 |
| `PKCS12` | PKCS12 키스토어 파일 |

## 설정 검증

`ServerConfiguration.validate()`를 호출하면 다음을 검증한다:

- `server` 섹션 필수
- `server.listener` 섹션 필수
- 포트 범위: 1 ~ 65535
- `maxContentLength` > 0

## 기본값

| 속성 | 기본값 |
|---|---|
| `host` | `0.0.0.0` |
| `port` | `8080` |
| `soBacklog` | `2048` |
| `maxContentLength` | 10 MB |
| `bossThreads` | `1` |
| `workerThreads` | `0` (Netty 기본) |
| `businessThreads` | `max(4, availableProcessors * 2)` |
| `tls.enabled` | `false` |
| `tls.protocols` | `TLSv1.3`, `TLSv1.2` |
| `tls.reloadIntervalSeconds` | `30` |
| `gracefulShutdownMillis` | `30000` |

## 의존성

외부 의존성 없음 (순수 Java POJO).
