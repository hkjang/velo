# `was-servlet-core` 모듈 가이드

`was-servlet-core` 모듈은 방대한 Jakarta Servlet API 생태계 표준에 대한 코어 구현체입니다. `was-protocol-http` 단계에서 정리된 가공되지 않은 `HttpExchange` 데이터 레코드를 표준 서블릿 문맥 환경에 맞춰 변환함으로써 레거시 웹 환경의 거대함 없이 전통적인 애플리케이션 운영 경험을 고스란히 복제합니다.

## 핵심 추상화 구조

### 1. JDK 기반 동적 프록시 패턴 (`ServletProxyFactory`)
Jakarta EE 명세 안의 다양한 인터페이스 구현을 위해, 그 모든 것의 콘크리트 인스턴스 어댑터 모델들을 생성하는 방식 대신 `was-servlet-core`는 동적 프록시 구조(`java.lang.reflect.Proxy`)를 택하였습니다. 클라이언트 앱에 의한 메서드 호출 인터셉션을 가로채서 `HttpExchange` 의 본체 객체에 직접 대응시켜 버립니다.

- **Request / Response**: Proxy가 타겟으로 둔 `HttpServletRequest`와 `HttpServletResponse` 내부에서 유저에 의해 발생한 메서드명(`Method.getName()`)을 단순히 switch문으로 캐스팅합니다. 가령 `.getHeader("Host")`의 엑세스가 유입되면 그대로 `HttpExchange.request.headers().get("Host")` 의 데이터에 바인딩합니다.
- **Context / Config 파싱**: 위와 동일한 형태의 프록시가 `ServletContext`, `FilterConfig` 및 `ServletConfig` 환경에 쓰입니다. 앱 이름, 컨텍스트 패스 등 구동 환경에서의 설정값이 프록시에 내장 래핑(Wrapping)되어 보관됩니다.
- **예외처리 없는 폴백 메커니즘**: 미구현 메서드나 비핵심/옵셔널 명세가 호출되더라도 예외상황인 Exception 을 방출하지 않습니다. 시스템 기동을 안전히 보호하기 위해 로직상 허용되는 한 빈 반환값(`null`, `false`, `0`, 비어있는 컬렉터본)을 기본 반환하여 충돌을 회피합니다.

### 2. 내부 요청 라우팅 설계 (`SimpleServletContainer`)
`ServletContainer` 객체는 실질적인 컨테이너로, 디플로이 완료된 모든 `ServletApplication` 구조물의 관문을 통관합니다.
- `deploy()`: 앱 고유의 컨텍스트(문맥 패스 공간 - ex. ROOT, 등)를 묶어주는 디렉토리 연결을 컨테이너 안에 등록하게 됩니다.
- `handle()`: 사용자의 URI 입력을 기초로 컨텍스트 경로를 제거 후 해당되는 서블릿 매핑의 `SimpleFilterChain` 단을 타겟팅합니다.
- 일치하는 경로 기반 애플리케이션 맵이나 엔드포인트에 맵핑되지 않은 요청 환경이라면 HTTP 404 (Not Found) 처리를 직접 산출시킵니다.

### 3. 세션 영구 저장화 환경 (`SessionState` & `InMemoryHttpSessionStore`)
디폴트 상태로 Velo WAS 는 모든 자사의 세션값을 독립적인 인메모리(In-Memory) 공간에서 자체 운영합니다.
- 백엔드 내부 자료구조 `ConcurrentHashMap` 로 구성된 딕셔너리 트리를 뻗칩니다.
- 추적이 불가하도록 하이픈 무늬를 발췌하여 UUID 식별자 기반 고유 `JSESSIONID` 시드 값을 세팅합니다.
- 프레임워크 층위에서부터 보안이 설정된 `HttpOnly` 옵션을 가미하여 브라우저에 배분해 스크립트 도난공격을 배제시킵니다.
- 지속상태 모의객체인 `SessionState`는 `creationTime` 및 최종 동기화인 `lastAccessedTime`를 참조하며, 후면에서는 `maxInactiveIntervalSeconds` 값에 맞추어 장기 유휴 세션을 배출(Expirations) 처리합니다.

### 4. 애플리케이션 안 브릿지 전달 조율 체계
애플리케이션 안의 내부 포워딩 처리기능(`RequestDispatcher`) 호출과 관련하여, 별도 특수화된 인터페이스 인 `InternalRequestBridge` 와 `InternalResponseBridge` 가 개입합니다:
- **`forward()` (포워드)**: 버퍼상 출력되어진 스트리밍 아웃풋들을 초기화하고 새 타깃 URI 포인터로의 이동 후 원래 내부 체인 매커니즘 라우팅 구조를 처음부터 재발진시킵니다.
- **`include()` (인클루드)**: 응답스트림 출력 보존 형태를 징발하여 외부 서브 서블릿으로 일방적 진출만 가동한 뒤, 추가분 페이로드를 조립 모아 현 주 메인 메서드로 순탄하게 통합시킵니다.
