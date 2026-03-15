# 세션 관리 (Session Management)

JSESSIONID 쿠키 기반 In-Memory 세션에 TTL(Time-To-Live) 만료 메커니즘을 추가하여
만료된 세션을 자동으로 정리한다.

## 모듈

`was-servlet-core` — `io.velo.was.servlet`

## 아키텍처

```
클라이언트 요청 (JSESSIONID 쿠키)
    │
    ▼
┌─────────────────────────────────────────────┐
│ InMemoryHttpSessionStore                     │
│                                             │
│  find(sessionId)                            │
│    ├── 세션 존재 + 만료 → expire() + null   │  ← Lazy Eviction
│    ├── 세션 존재 + 유효 → SessionState 반환  │
│    └── 세션 없음 → null                     │
│                                             │
│  purgeExpired()                             │
│    └── 전체 세션 스캔 → 만료 세션 제거      │  ← Scheduled Eviction
└─────────────────────────────────────────────┘
                  │
                  │ 주기적 호출
                  ▼
┌─────────────────────────────────────────────┐
│ SessionExpirationScheduler                   │
│   데몬 스레드, 기본 60초 간격               │
│   ScheduledExecutorService                  │
└─────────────────────────────────────────────┘
```

## 이중 만료 전략

### 1. Lazy Eviction (지연 제거)

`find()` 호출 시 세션의 만료 여부를 확인하여 즉시 제거한다.

- 클라이언트가 만료된 세션 ID로 접근하면 세션이 사라진 것처럼 동작
- 별도 스레드 불필요, 접근 시점에 판단
- 접근되지 않는 세션은 메모리에 남아있을 수 있음 (→ Scheduled Eviction이 보완)

### 2. Scheduled Eviction (주기적 제거)

`SessionExpirationScheduler`가 주기적으로 전체 세션을 스캔하여 만료된 세션을 제거한다.

- 기본 주기: **60초**
- 데몬 스레드로 동작 (JVM 종료 시 자동 종료)
- `SimpleServletContainer` 생성 시 자동 시작
- 접근되지 않는 만료 세션도 확실히 정리

## 핵심 컴포넌트

### SessionState

세션의 상태를 관리하는 클래스.

| 필드 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `id` | `String` | (UUID) | 세션 식별자 (하이픈 제거) |
| `creationTime` | `long` | 생성 시점 | 세션 생성 시각 |
| `lastAccessedTime` | `volatile long` | 생성 시점 | 마지막 접근 시각 |
| `valid` | `volatile boolean` | `true` | 유효 여부 |
| `maxInactiveIntervalSeconds` | `volatile int` | `1800` (30분) | TTL 설정 |
| `attributes` | `ConcurrentHashMap` | 빈 맵 | 세션 속성 |

**만료 판단 (`isExpired()`):**

1. `valid == false` → 만료
2. `maxInactiveIntervalSeconds <= 0` → 만료 안 함 (무한 수명)
3. `현재 시각 - lastAccessedTime > maxInactiveIntervalSeconds * 1000` → 만료

**접근 갱신 (`touch()`):**

`lastAccessedTime`을 현재 시각으로 갱신. 세션이 접근될 때마다 호출되어 TTL을 리셋한다.

### InMemoryHttpSessionStore

`ConcurrentHashMap` 기반 세션 저장소.

생성자에서 `defaultMaxInactiveIntervalSeconds`를 받아 새 세션 생성 시 기본 TTL을 설정한다.

```java
// 기본 타임아웃 (1800초 = 30분)
InMemoryHttpSessionStore store = new InMemoryHttpSessionStore();

// 커스텀 타임아웃 (서버 설정 연동)
InMemoryHttpSessionStore store = new InMemoryHttpSessionStore(3600); // 1시간
```

| 메서드 | 설명 |
|---|---|
| `find(sessionId)` | 세션 조회 + lazy 만료 체크 |
| `create()` | UUID 기반 새 세션 생성 (기본 TTL 적용) |
| `invalidate(sessionId)` | 세션 무효화 + 제거 |
| `purgeExpired()` | 전체 스캔하여 만료 세션 제거, 제거 수 반환 |
| `size()` | 현재 활성 세션 수 |
| `addExpirationListener(Consumer<SessionState>)` | 만료 이벤트 리스너 등록 |

**만료 처리 (`expire()`):**

1. `sessions.remove(sessionId)`
2. `state.invalidate()` (속성 clear)
3. 등록된 모든 `expirationListener` 호출

### SessionExpirationScheduler

주기적으로 `purgeExpired()`를 호출하는 스케줄러.

```java
SessionExpirationScheduler scheduler =
    new SessionExpirationScheduler(sessionStore, 60); // 60초 간격
scheduler.start();
// ...
scheduler.close(); // graceful shutdown
```

| 항목 | 설명 |
|---|---|
| 스레드명 | `session-expiry-scheduler` |
| 타입 | 데몬 스레드 |
| 종료 | `close()` 호출 시 5초 대기 후 강제 종료 |
| 예외 | 퍼지 실패 시 로그 기록, 스케줄 계속 |

### SimpleServletContainer 통합

`SimpleServletContainer`가 `AutoCloseable`을 구현하며 세션 스케줄러를 관리한다.
생성자에서 퍼지 간격과 세션 기본 타임아웃을 모두 설정할 수 있다.

```java
// 기본 생성자: 퍼지 60초 간격, 세션 타임아웃 1800초
SimpleServletContainer container = new SimpleServletContainer();

// 커스텀 퍼지 간격 (세션 타임아웃은 기본 1800초)
SimpleServletContainer container = new SimpleServletContainer(30); // 30초

// 퍼지 간격 + 세션 타임아웃 모두 설정 (서버 설정 연동)
SimpleServletContainer container = new SimpleServletContainer(60, 3600); // 60초 퍼지, 1시간 타임아웃

// 종료 시 스케줄러도 정리
container.close();
```

### 서버 설정 연동

`server.yaml`의 `session` 섹션에서 타임아웃과 퍼지 간격을 설정할 수 있다:

```yaml
server:
  session:
    timeoutSeconds: 1800          # 세션 타임아웃 (기본 30분)
    purgeIntervalSeconds: 60      # 만료 세션 정리 주기
```

`VeloWasApplication`이 기동 시 설정값을 읽어 `SimpleServletContainer`에 전달한다.

## 서블릿 API 연동

`ServletProxyFactory`에서 `HttpSession` 프록시의 세션 TTL 메서드를 위임한다:

| 서블릿 API 메서드 | 위임 대상 |
|---|---|
| `session.getMaxInactiveInterval()` | `sessionState.getMaxInactiveIntervalSeconds()` |
| `session.setMaxInactiveInterval(int)` | `sessionState.setMaxInactiveIntervalSeconds(int)` |

## 소스 구조

```
was-servlet-core/src/main/java/io/velo/was/servlet/
├── SessionState.java                  세션 상태 (TTL, 만료 판단)
├── InMemoryHttpSessionStore.java      세션 저장소 (lazy + listener)
├── SessionExpirationScheduler.java    주기적 퍼지 스케줄러
├── SimpleServletContainer.java        컨테이너 (스케줄러 통합)
└── ServletProxyFactory.java           서블릿 API 프록시 (TTL 위임)
```

## 테스트

```bash
# was-servlet-core 테스트 (13개, 세션 관련 5개)
mvn test -pl was-servlet-core -am
```

| 테스트 | 검증 내용 |
|---|---|
| `sessionExpiresAfterMaxInactiveInterval` | TTL 초과 시 find() → null |
| `sessionPurgeRemovesExpiredSessions` | purgeExpired()로 만료 세션 제거 |
| `sessionWithZeroMaxInactiveIntervalNeverExpires` | TTL=0이면 영구 세션 |
| `expirationListenerCalledOnPurge` | 만료 시 리스너 콜백 호출 |
| `sessionTouchResetsExpirationTimer` | touch()로 TTL 리셋 |
