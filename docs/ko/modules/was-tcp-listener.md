# `was-tcp-listener` 모듈 가이드

`was-tcp-listener` 모듈은 Velo WAS 의 역할을 종래 일반적인 웹을 서빙하던 HTTP WAS 의 수준을 넘어선 거대한 게이트웨이 영역으로 영지를 확대시켜줍니다. 고착된 상태보존성 네트워크 규격인 Raw(원시) TCP 커넥션 연결 계열을 Netty의 견고한 파이프라인(Robust pipeline) 아키텍처 위에 흡수해 올립니다.

## 핵심 구성 요소

### 1. 전용 코덱 및 인제스쳔 결착계 (`MessageCodec`)
일반 TCP 대역으로 몰려드는 트래픽은 구조화가 완료된 HTTP 와는 달리 아무런 헤더도 경계도 없는 막무가내 무정형 통신 양상을 띕니다. Velo WAS의 이 모듈은 폭발적으로 쏟아지는 연속된 바이트(Byte) 조도 데이터를 명확한 `TcpMessage` 단위 레코드 레벨 조각으로 찢어 맞추는 우수한 `MessageCodec` 체계와 `FrameDecoderFactory` 환경을 도입했습니다.
- 외부 사용자 요청은 TCP 설정인 `TcpListenerConfig`의 옵션 분기에 맞추어 `RAW`(가공 안됨), `LINE`(줄단위 끝맺음), `DELIMITER`(특구 구분자), 혹은 `LENGTH_FIELD`(기지정된 길이 규격) 중 한 방향의 패킷 추출 방식으로 조절될 수 있습니다.
- 이 복잡한 프레이밍은 초단 부트스트랩핑 상태에서 기본 탑재된 `DefaultMessageCodec`의 액티브 처리를 받습니다. 

### 2. 고유 TCP 생명주기 관리 (`TcpListenerServer` / `TcpListenerManager`)
`TcpListenerManager` 통제자는 다각도의 `TcpListenerServer`의 거대한 인스턴스 무리들을 독단으로 관리합니다. 이들은 기존 통상 HTTP 리스너들의 클러스터 연합 환경과는 동떨어져 독립적으로 바인딩(포트 점유)됩니다.
- **철저한 격리 (Isolation)**: HTTP 에 비해 비대칭적으로 긴급하고 오래 걸릴 연산을 위해 특수 개조된 전용 TCP 파이프용 독립형 스레드 풀 (`TcpHandlerExecutor`) 공간을 분할 사용하며, 이로 말미암아 HTTP 웹 트래픽들이 무거운 TCP 트래픽 백그라운드 구동에 치여 기아 (Starve block) 현상에 빠지지 않도록 유도합니다.

### 3. 커넥션 제재 및 네트워크 한도 설정 (Security & Limits)
- **`TcpRateLimiter`**: 토큰 버킷(Token buckets) 룰을 필두로 단기간 악성으로 접근하는 무차별 IP 인바운드 커넥들을 차단/드랍(Drop) 시킵니다.
- **`TcpSecurityHandler`**: SocketChannel 초기 진입화와 동시에 엄격하게 등록된 CIDR 대역 접근허용/접근불가(Allow/Denylist) 리스트 정책을 평가 관철합니다. 백그라운드 환경 메모리 누수나 무허가 해킹 서브넷들의 침탈 가능성을 차단합니다. 

### 4. 자체 라우터 및 상태 유지형 세션 핸들링 (Router & Sessions)
- **`TcpMessageRouter`**: 커스텀 규격에 구조화되어 걸러맞춰 들어온 바이트 단위들을 각 대응하는 애플리케이션 레벨 핸들러 내부 파이프 쪽으로 유도해내는 라우터입니다.
- **`TcpSession` / `TcpSessionManager`**: TCP 통신 고유의 기나긴 지연(Long periods)이 수반되는 상태유지성(Stateful) 에 걸맞게, 고유 식별자 키와 현재 기계 안에서 호흡하고 뚫려있는 채널 연결고리를 매핑해 두는 파트입니다. 해당 핸들러를 이용해 끊어진(Disconnecting) 상태 이벤트 전이 판정이나 통신 지연(Timeout)을 비동기식 체계로 유연히 감별해 줍니다. 

### 5. 매니징 및 통제력 확보 (`TcpListenerAdmin`)
JEUS CLI와 동일한 규격의 범중앙화 통제 관리를 위해, `was-tcp-listener` 전용 관리 지표는 철두철미하게 `TcpListenerAdminMBean` JMX 구조쪽으로 우회 등록됩니다. 따라서 운영 책임자들이 직접 `was-admin` CLI 커맨드 화면에서 `TcpAccessLog` 열람과 통신량 처리량(`TcpMetrics`) 의 증감 폭을 확인가능하고, 더 나아가 시스템 점유만을 자국하고 비정상 가동하는 나쁜 소켓 찌꺼기 세션 등도 강제로 종료시켜버릴 강력한 제재 컨트롤 지휘력을 선사합니다.
