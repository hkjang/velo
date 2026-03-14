# `was-observability` 모듈 가이드

`was-observability` 모듈은 Velo WAS의 핵심적인 로깅과 감사(오딧, Audit) 추적 기능을 제공합니다. 이 모듈은 운영 중 발생하는 로깅 흔적(Trace)을 파악하기 쉽도록 목적별 스트림으로 분리하여 관리합니다.

## 핵심 구성 요소

### 1. 접근 로그 (`AccessLog` / `AccessLogEntry`)
기본적인 HTTP 접근 요청(일반적인 Tomcat 환경의 `localhost_access_log` 와 동일)을 기록합니다.
- 클라이언트의 고유 IP 대역폭 지표, 요청 URI 리소스 포인터, HTTP Method 방식, 응답 상태 결과 코드(Status Code), 단일 프로세스 요청에 소요된 지연 시간(Time Taken)을 모두 로깅 내에 보존합니다.
- 트래픽의 분석기반 모니터링, 과금 측정을 위한 지표 또는 시스템을 향한 분산 징후 탐지나 비정상 접속 등 위협 분석(Anomaly detection) 처리에 필수로 활용됩니다.

### 2. 에러 및 예외 로그 (`ErrorLog` / `ErrorLogEntry`)
처리 과정 중에 예기치 못한 애플리케이션의 Unhandled Exception 예외 사항이나 HTTP 500 단위 오류 등 시스템 실패 시퀀스를 잡아냅니다.
- 최종 사용자에게 스택 추적(Stack traces) 데이터가 직접 유출되지 않도록 안전하게 숨기며, 후면 로그 콘솔에 기록합니다.
- 실패 원인을 찾기 위해 실패 지점의 문맥을 공유하는 `HttpExchange` 파편 정보와 함께 로깅됩니다.

### 3. 감사 식별 로그 (`AuditLog` / `AuditLogEntry`)
서버 내부의 시스템 수명 주기, 운영 통제권 발동, 제어 이벤트 시그널 등을 철저하게 추적합니다.
- `was-admin` 관리용 인프라 모듈에서 환경 구성을 동적으로 개변시키거나 보안 롤(Role)이 변경, 혹은 애플리케이션 배포 상태변경 (Deployment Lifecycles) 등을 모조리 기입하는 데 쓰입니다.
- 기업의 엄격한 컴플라이언스 기준 준수(Compliance) 및 보안 사고 발생 시 추적 용이성을 위해 단단하게 구축되어야만 하는 주요 로그 테이블 영역입니다.
