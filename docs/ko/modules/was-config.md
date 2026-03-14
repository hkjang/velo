# `was-config` 모듈 가이드

`was-config` 모듈은 서버의 런타임 설정에 대한 단일 진실 공급원(Single Source of Truth) 역할을 합니다. 외부 종속성이 없는 순수 자바 객체(POJO) 모델로 구축되어 있으며, 관리자 CLI(`was-admin`)나 Netty 부트스트랩 계층과 같은 다른 모듈에서 클래스패스 충돌을 일으키지 않고 깔끔하게 사용할 수 있습니다.

## 핵심 구성 요소

### 1. `ServerConfiguration` 클래스
최상위 설정 클래스입니다. 내부적으로 `server.yaml` 파일의 여러 섹션을 표현하기 위해 다양한 중첩 정적(nested static) 클래스들을 많이 활용합니다.

#### 1.1 `Server` 섹션
전역 노드 식별자 정보를 포함합니다.
- `name`: WAS 인스턴스의 표시용 이름입니다 (예: `velo-was`).
- `nodeId`: 클러스터 토폴로지 상의 고유 식별자입니다 (예: `node-1`).
- `gracefulShutdownMillis`: 서버가 Netty 스레드를 강제로 종료하기 전에 실행 중인 요청이 완료되도록 허용하는 유예 시간입니다. 기본값: 30초.

#### 1.2 `Listener` 섹션 (기본 HTTP)
기본 HTTP 바인딩을 설정합니다.
- `host` / `port`: 메인 서버 소켓을 위한 좌표입니다 (기본값 `0.0.0.0:8080`).
- `soBacklog`: 들어오는 연결에 대한 최대 큐 대기열 길이를 설정합니다.
- `reuseAddress` / `tcpNoDelay` / `keepAlive`: 표준 TCP 소켓 플래그들입니다.
- `maxContentLength`: HTTP 요청 본문(예: 파일 업로드)에 허용되는 최대 크기입니다. 기본값: 10MB.

#### 1.3 `Threading` 섹션
Netty의 EventLoopGroup 스레드 크기를 관리합니다.
- `bossThreads`: 새로운 연결을 수락하는 스레드의 수입니다. 일반적으로 `1`이면 충분합니다.
- `workerThreads`: 읽기/쓰기를 처리하는 Netty I/O 스레드의 수입니다. 기본값 `0`은 Netty 자체의 CPU 휴리스틱 알고리즘(`코어 수 * 2`)에 위임함을 뜻합니다.
- `businessThreads`: (향후 비동기 서블릿 도입을 위한 예비 필드) Netty I/O 워커의 부담을 덜고 느린 블로킹 연산을 담당할 스레드 풀입니다.

#### 1.4 `Tls` 섹션 (보안)
리스너를 위한 SSL/TLS를 구성합니다.
- 암호화 라이브러리를 직접 가져오지 않고도 PEM 파일이나 KeyStore 위치를 지정합니다.
- 변경되는 인증서를 핫 리로딩(Hot-Reloading)하기 위해 트랜스포트 계층에서 참조하는 `reloadIntervalSeconds` 필드를 포함합니다.

#### 1.5 `Jsp` 섹션
동적 생성되는 서블릿이 쓰일임시 폴더(scratch directory)나 JSP 파일 수정 상태를 체크할 인터벌 등 JSP 컴파일러(Jasper) 구동에 필요한 기본 환경 변수들을 관장합니다.

#### 1.6 `TcpListenerConfig` 섹션 (고급 라우팅)
일반 HTTP를 넘어서 Velo WAS가 확장 게이트웨이나 프록시 역할을 할 수 있도록 라인 기반, 원시(Raw) 기반 프레이밍 방식의 추가 TCP 리스너 엔드포인트를 정의합니다.

## 검증 (Validations)
이 POJO 구조 안에는 `port > 0` 이나 `maxContentLength > 0` 과 같은 기본적이고 필수적인 제약을 검사하는 `validate()` 메서드를 내포합니다. 구동 중에 발견하기 힘든 런타임오류를 미연에 방지하기 위해 부트스트랩 단계 초기에 `IllegalArgumentException`을 발생시켜 서버 기동을 멈추게 합니다.
