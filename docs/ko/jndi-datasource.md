# JNDI / DataSource

`javax.naming` SPI 기반 In-Memory JNDI 네이밍 컨텍스트와 JDBC 커넥션 풀링 DataSource를 제공한다.

## 모듈

`was-jndi` — `io.velo.was.jndi`

## 아키텍처

```
┌────────────────────────────────────────────────────────┐
│ 애플리케이션 코드                                       │
│                                                        │
│  InitialContext ctx = new InitialContext();             │
│  DataSource ds = (DataSource) ctx.lookup(              │
│      "java:comp/env/jdbc/myDB");                       │
│  Connection conn = ds.getConnection();                 │
└──────────┬────────────────────────┬────────────────────┘
           │ JNDI SPI              │ JDBC
           ▼                       ▼
┌───────────────────┐  ┌──────────────────────┐
│ VeloNamingContext  │  │ PooledDataSource      │
│  (싱글턴 ROOT)     │  │   │                   │
│  bind/lookup/      │  │   ▼                   │
│  rebind/unbind     │  │ SimpleConnectionPool  │
│                    │  │   borrow → validate   │
│  ConcurrentHashMap │  │   release → rollback  │
└───────────────────┘  │   idle pool reuse     │
                       └──────────────────────┘
                              │
                              ▼
                       ┌──────────────┐
                       │ PooledConnection │
                       │  close() → pool │
                       │  로 반환         │
                       └──────────────┘
```

## JNDI 구현

### VeloNamingContext

`javax.naming.Context` 인터페이스의 In-Memory 구현체.

| 기능 | 설명 |
|---|---|
| 저장소 | `ConcurrentHashMap<String, Object>` |
| 계층 구조 | `/` 구분자 기반 (예: `java:comp/env/jdbc/myDB`) |
| Sub-context | 프리픽스 매칭으로 하위 컨텍스트 자동 탐지 |
| 스레드 안전 | `ConcurrentHashMap` 사용 |

**지원 메서드:**

| 메서드 | 설명 |
|---|---|
| `lookup(name)` | 이름으로 바인딩된 객체 조회, 하위 컨텍스트 자동 생성 |
| `bind(name, obj)` | 이름에 객체 바인딩 (중복 시 예외) |
| `rebind(name, obj)` | 이름에 객체 재바인딩 (덮어쓰기) |
| `unbind(name)` | 바인딩 제거 |
| `rename(old, new)` | 바인딩 이름 변경 |
| `list(name)` | 하위 바인딩 `NameClassPair` 열거 |
| `listBindings(name)` | 하위 바인딩 `Binding` 열거 |
| `createSubcontext(name)` | 하위 컨텍스트 생성 |
| `destroySubcontext(name)` | 하위 컨텍스트 + 소속 바인딩 제거 |
| `allBindings()` | 전체 바인딩 조회 (관리 용도) |

### VeloInitialContextFactory

`javax.naming.spi.InitialContextFactory` SPI 구현체.

- **싱글턴 ROOT 컨텍스트**: 같은 클래스로더 내 모든 `new InitialContext()`가 동일 네임스페이스를 공유
- SPI 자동 활성화: `META-INF/services/javax.naming.spi.InitialContextFactory` 파일 제공

**활성화 방법:**

```java
// 방법 1: 시스템 프로퍼티
System.setProperty(Context.INITIAL_CONTEXT_FACTORY,
    "io.velo.was.jndi.VeloInitialContextFactory");

// 방법 2: SPI 자동 탐지 (서비스 파일)
InitialContext ctx = new InitialContext(); // 자동으로 VeloInitialContextFactory 사용

// 방법 3: 프로그래밍 방식 직접 바인딩
VeloInitialContextFactory.root().bind("java:comp/env/jdbc/myDB", dataSource);
```

### VeloNameParser

`javax.naming.NameParser` 구현체. `CompositeName`을 사용한 이름 파싱을 제공한다. 싱글턴 인스턴스(`INSTANCE`)로 사용.

## 커넥션 풀

### SimpleConnectionPool

경량 JDBC 커넥션 풀.

**주요 특성:**

| 항목 | 기본값 | 설명 |
|---|---|---|
| `minIdle` | 2 | 초기 유휴 커넥션 수 |
| `maxPoolSize` | 20 | 최대 커넥션 수 |
| `borrowTimeoutMs` | 30,000ms | 대기 타임아웃 |
| `validationQuery` | (null) | 검증 쿼리 (null이면 `isValid(2)`) |

**Builder 패턴:**

```java
SimpleConnectionPool pool = SimpleConnectionPool.builder("jdbc:h2:mem:test")
    .driverClassName("org.h2.Driver")
    .username("sa")
    .password("")
    .minIdle(5)
    .maxPoolSize(50)
    .borrowTimeoutMs(10_000)
    .validationQuery("SELECT 1")
    .build();
```

**borrow() 알고리즘:**

```
1. idle pool에서 꺼내기 시도
   ├── 성공 + valid → 반환
   └── 성공 + stale → discard, totalCreated--

2. totalCreated < maxPoolSize?
   ├── CAS로 totalCreated 증가
   ├── 새 커넥션 생성 → 반환
   └── (실패 시 totalCreated 복원)

3. idle pool에서 borrowTimeoutMs 동안 blocking 대기
   ├── 성공 + valid → 반환
   ├── 성공 + stale → discard, 재귀 retry
   └── 타임아웃 → SQLException
```

**release() 처리:**

1. 커넥션이 autoCommit=false이면 rollback 후 autoCommit=true로 복원
2. idle pool에 반환
3. (반환 실패 또는 풀 닫힘 시) 물리 커넥션 close

**모니터링 메서드:**

| 메서드 | 설명 |
|---|---|
| `activeCount()` | 대여 중인 커넥션 수 (totalCreated - idle) |
| `idleCount()` | 유휴 커넥션 수 |
| `totalCount()` | 전체 관리 커넥션 수 |
| `maxPoolSize()` | 최대 풀 크기 |

### PooledDataSource

`javax.sql.DataSource` + `AutoCloseable` 구현체.

```java
PooledDataSource ds = new PooledDataSource("myDB", pool);
Connection conn = ds.getConnection(); // PooledConnection 반환
conn.close(); // 풀로 반환 (물리 close 아님)
```

| 메서드 | 설명 |
|---|---|
| `getConnection()` | 풀에서 커넥션 대여 → `PooledConnection`으로 래핑 |
| `activeConnections()` | 활성 커넥션 수 |
| `idleConnections()` | 유휴 커넥션 수 |
| `maxConnections()` | 최대 커넥션 수 |
| `url()` | JDBC URL |
| `close()` | 풀 종료 |

### PooledConnection

`java.sql.Connection`을 위임(delegate)하는 래퍼.

**핵심 동작:**
- `close()`: 물리 커넥션을 닫지 않고 풀에 반환
- 모든 메서드 호출 전 `checkOpen()`으로 논리적 close 상태 확인
- 논리적으로 닫힌 후 메서드 호출 시 `SQLException` 발생

## JNDI + DataSource 통합 사용 예시

```java
// 1. DataSource 생성
SimpleConnectionPool pool = SimpleConnectionPool.builder("jdbc:h2:mem:myDB")
    .driverClassName("org.h2.Driver")
    .minIdle(2).maxPoolSize(10)
    .build();
PooledDataSource ds = new PooledDataSource("myDB", pool);

// 2. JNDI에 등록
VeloInitialContextFactory.root().bind("java:comp/env/jdbc/myDB", ds);

// 3. 애플리케이션에서 JNDI 조회
InitialContext ctx = new InitialContext();
DataSource appDs = (DataSource) ctx.lookup("java:comp/env/jdbc/myDB");
try (Connection conn = appDs.getConnection()) {
    // SQL 실행
}
```

## 소스 구조

```
was-jndi/src/main/java/io/velo/was/jndi/
├── VeloNamingContext.java            JNDI Context 구현
├── VeloNameParser.java              이름 파서 (싱글턴)
├── VeloInitialContextFactory.java   SPI 팩토리 (싱글턴 ROOT)
├── SimpleConnectionPool.java        JDBC 커넥션 풀
├── PooledDataSource.java            DataSource 구현
└── PooledConnection.java            위임 Connection (close→반환)

was-jndi/src/main/resources/
└── META-INF/services/
    └── javax.naming.spi.InitialContextFactory
```

## 테스트

```bash
# was-jndi 테스트 (16개)
mvn test -pl was-jndi -am
```

| 카테고리 | 테스트 수 | 검증 내용 |
|---|---|---|
| JNDI | 9 | bind/lookup/rebind/unbind/rename, sub-context, list, listBindings, 중복 bind 예외 |
| Connection Pool | 4 | borrow/release, 풀 고갈 타임아웃, validation, close |
| DataSource | 2 | getConnection + 쿼리, activeCount/idleCount |
| 통합 | 1 | JNDI → DataSource lookup → 쿼리 실행 |
