# 구조화된 로깅 (Structured Logging)

액세스 로그, 에러 로그, 감사 로그를 JSON 형식으로 구조화하여 출력한다.
외부 JSON 라이브러리 의존 없이 `StringBuilder` 기반으로 직렬화한다.

## 모듈

`was-observability` — `io.velo.was.observability`

## 아키텍처

```
┌──────────────────────────────────────────────────────┐
│ 애플리케이션 / 서블릿 컨테이너                         │
│                                                      │
│  AccessLog.log(entry)   요청 처리 완료 후             │
│  ErrorLog.log(entry)    예외 발생 시                  │
│  AuditLog.log(entry)    관리 작업 수행 시             │
└──────────┬──────────────┬──────────────┬─────────────┘
           │              │              │
           ▼              ▼              ▼
     velo.access     velo.error     velo.audit
     (SLF4J INFO)    (SLF4J ERROR)  (SLF4J INFO)
           │              │              │
           ▼              ▼              ▼
     access.log      error.log      audit.log
     (로깅 프레임워크에서 분리 설정)
```

## 3개 로그 채널

### 1. AccessLog — 액세스 로그

HTTP 요청/응답을 기록한다.

| 항목 | 설명 |
|---|---|
| SLF4J 로거 | `velo.access` |
| 로그 레벨 | INFO |
| Entry 클래스 | `AccessLogEntry` (record) |

**AccessLogEntry 필드:**

| 필드 | 타입 | 설명 |
|---|---|---|
| `timestamp` | `Instant` | 요청 시각 |
| `method` | `String` | HTTP 메서드 (GET, POST, ...) |
| `uri` | `String` | 요청 URI |
| `protocol` | `String` | 프로토콜 (HTTP/1.1, HTTP/2) |
| `status` | `int` | HTTP 응답 코드 |
| `contentLength` | `long` | 응답 바이트 수 |
| `durationMs` | `long` | 처리 시간 (ms) |
| `remoteAddress` | `String` | 클라이언트 IP |
| `userAgent` | `String` | User-Agent 헤더 |
| `referer` | `String` | Referer 헤더 |
| `requestId` | `String` | 요청 추적 ID |

**출력 형식:**

JSON:
```json
{"timestamp":"2025-01-15T10:30:00Z","type":"access","method":"GET","uri":"/api/users","protocol":"HTTP/1.1","status":200,"contentLength":1234,"durationMs":45,"remoteAddress":"192.168.1.1","userAgent":"Mozilla/5.0"}
```

Combined Log Format (CLF):
```
192.168.1.1 - - [2025-01-15T10:30:00Z] "GET /api/users HTTP/1.1" 200 1234 "-" "Mozilla/5.0"
```

### 2. ErrorLog — 에러 로그

예외 및 에러를 구조화하여 기록한다.

| 항목 | 설명 |
|---|---|
| SLF4J 로거 | `velo.error` |
| 로그 레벨 | ERROR |
| Entry 클래스 | `ErrorLogEntry` (record) |

**ErrorLogEntry 필드:**

| 필드 | 타입 | 설명 |
|---|---|---|
| `timestamp` | `Instant` | 발생 시각 |
| `level` | `String` | 로그 레벨 ("ERROR") |
| `logger` | `String` | 발생 클래스명 |
| `message` | `String` | 에러 메시지 |
| `exceptionClass` | `String` | 예외 클래스 FQCN |
| `exceptionMessage` | `String` | 예외 메시지 |
| `stackTrace` | `String` | 전체 스택 트레이스 |
| `requestId` | `String` | 관련 요청 ID |
| `uri` | `String` | 관련 요청 URI |

**편의 메서드:**

```java
// 기본 에러 로깅
ErrorLog.log("MyHandler", "Unexpected error", exception);

// 요청 컨텍스트 포함
ErrorLog.log("MyHandler", "Request failed", exception, requestId, "/api/users");
```

### 3. AuditLog — 감사 로그

관리 작업(배포, 설정 변경, 서버 생명주기 등)을 기록한다.

| 항목 | 설명 |
|---|---|
| SLF4J 로거 | `velo.audit` |
| 로그 레벨 | INFO |
| Entry 클래스 | `AuditLogEntry` (record) |

**AuditLogEntry 필드:**

| 필드 | 타입 | 설명 |
|---|---|---|
| `timestamp` | `Instant` | 수행 시각 |
| `action` | `String` | 수행한 작업 (DEPLOY, UNDEPLOY, ...) |
| `actor` | `String` | 수행자 (admin, system, ...) |
| `target` | `String` | 대상 (애플리케이션명, 서버명, ...) |
| `result` | `String` | 결과 (SUCCESS, FAILURE, ...) |
| `details` | `Map<String, String>` | 추가 상세 정보 |

**편의 메서드:**

```java
// 단순 감사 로그
AuditLog.log("DEPLOY", "admin", "myapp", "SUCCESS");

// 상세 정보 포함
AuditLog.log("CONFIG_CHANGE", "admin", "server1", "SUCCESS",
    Map.of("property", "maxThreads", "oldValue", "200", "newValue", "400"));
```

## JSON 직렬화

모든 Entry record는 `toJson()` 메서드로 JSON 문자열을 생성한다.

**설계 결정:**

- 외부 의존성 없음: Jackson/Gson 대신 `StringBuilder` 직접 사용
- `escapeJson()` 메서드로 `\`, `"`, `\n`, `\r`, `\t` 이스케이프 처리
- null 필드는 JSON에서 생략 (선택적 필드)
- `type` 필드로 로그 종류 식별: `"access"`, `"error"`, `"audit"`

## 로깅 프레임워크 설정 예시

Logback 설정으로 로그 파일 분리:

```xml
<!-- access log → logs/access.log -->
<logger name="velo.access" level="INFO" additivity="false">
    <appender-ref ref="ACCESS_FILE"/>
</logger>

<!-- error log → logs/error.log -->
<logger name="velo.error" level="ERROR" additivity="false">
    <appender-ref ref="ERROR_FILE"/>
</logger>

<!-- audit log → logs/audit.log -->
<logger name="velo.audit" level="INFO" additivity="false">
    <appender-ref ref="AUDIT_FILE"/>
</logger>
```

## 메트릭 수집 (Metrics)

`MetricsCollector`는 서버의 런타임 메트릭을 스레드 안전하게 수집하는 싱글턴 컴포넌트이다.
`java.util.concurrent.atomic.LongAdder`를 사용하여 잠금 없이 높은 동시성 환경에서도 정확한 카운터를 유지한다.

### 수집 항목

| 메트릭 | 타입 | 설명 |
|---|---|---|
| `totalRequests` | `LongAdder` | 총 처리 요청 수 |
| `activeRequests` | `LongAdder` | 현재 처리 중인 요청 수 |
| `activeConnections` | `LongAdder` | 현재 활성 연결 수 |
| `totalResponseTimeNanos` | `LongAdder` | 총 응답 시간 (나노초) |
| `statusBuckets[1xx~5xx]` | `LongAdder[6]` | HTTP 상태 코드 분포 |

### API

```java
MetricsCollector metrics = MetricsCollector.instance(); // 싱글턴

metrics.requestStarted();                    // 요청 시작
metrics.requestCompleted(durationNanos, 200); // 요청 완료 + 상태 코드
metrics.connectionOpened();                   // 연결 열림
metrics.connectionClosed();                   // 연결 닫힘

MetricsSnapshot snapshot = metrics.snapshot(); // 스냅샷 조회
String json = snapshot.toJson();               // JSON 직렬화
```

### 통합 지점

- **`NettyHttpChannelHandler`**: `channelActive()`/`channelInactive()`에서 연결 카운터, `channelRead0()`에서 요청 카운터 및 응답 시간 측정
- **`/metrics` 엔드포인트**: `HttpHandlerRegistry`에 등록되어 JSON 형식으로 스냅샷 반환

### MetricsSnapshot

스냅샷의 JSON 출력 예시:

```json
{"totalRequests":150,"activeRequests":2,"activeConnections":5,"averageResponseTimeMs":12.34,"status":{"1xx":0,"2xx":140,"3xx":3,"4xx":5,"5xx":2}}
```

## 소스 구조

```
was-observability/src/main/java/io/velo/was/observability/
├── AccessLog.java           액세스 로그 정적 API
├── AccessLogEntry.java      액세스 로그 엔트리 record
├── ErrorLog.java            에러 로그 정적 API
├── ErrorLogEntry.java       에러 로그 엔트리 record
├── AuditLog.java            감사 로그 정적 API
├── AuditLogEntry.java       감사 로그 엔트리 record
├── MetricsCollector.java    메트릭 수집 싱글턴 (LongAdder)
└── MetricsSnapshot.java     메트릭 스냅샷 record (toJson)
```

## 테스트

```bash
# was-observability 테스트 (15개)
mvn test -pl was-observability -am
```
