# AsyncContext (비동기 서블릿)

Jakarta Servlet 6.1의 `AsyncContext` 인터페이스를 구현하여 비동기 요청 처리를 지원한다.

## 모듈

`was-servlet-core` — `io.velo.was.servlet.VeloAsyncContext`

## 개요

`VeloAsyncContext`는 서블릿에서 `request.startAsync()`를 호출했을 때 반환되는 비동기 컨텍스트 구현체이다. 요청 스레드를 즉시 반환하고, 별도 스레드에서 응답을 완성할 수 있게 한다.

## 아키텍처

```
Servlet.service()
  │
  ├── request.startAsync()
  │     └── VeloAsyncContext 생성
  │           ├── timeout 스케줄 등록 (기본 30초)
  │           └── 요청 스레드 반환
  │
  ├── (비동기 작업 수행)
  │
  └── asyncContext.complete()  또는  asyncContext.dispatch()
        ├── timeout 취소
        ├── AsyncListener.onComplete() 호출
        └── Netty 응답 전송 (ResponseSink)
```

## 핵심 메커니즘

### 타임아웃 관리

`ScheduledExecutorService`를 사용하여 비동기 요청의 타임아웃을 관리한다.

- 기본 타임아웃: **30,000ms** (30초)
- `setTimeout()` 호출 시 기존 스케줄 취소 후 재등록
- 타임아웃 값이 0 이하이면 타임아웃 비활성화
- 타임아웃 발생 시: `onTimeout()` → `complete()` 순서로 실행

### 완료 처리 (`complete()`)

`AtomicBoolean`으로 중복 완료를 방지한다.

1. `completed` 플래그를 `false → true`로 CAS (실패 시 무시)
2. 타임아웃 스케줄 취소
3. 등록된 모든 `AsyncListener.onComplete()` 호출
4. 응답이 아직 커밋되지 않은 경우 `ResponseSink`를 통해 Netty 응답 전송
5. 세션 쿠키 처리 (`SessionHolder`로 JSESSIONID 발급 여부 결정)

### 디스패치 (`dispatch()`)

비동기 컨텍스트에서 다른 서블릿 경로로 재디스패치한다.

- `dispatch()` — 원래 요청 경로로 재디스패치
- `dispatch(String path)` — 지정 경로로 재디스패치
- `dispatch(ServletContext, String path)` — 컨텍스트 + 경로 지정
- 디스패치는 `ScheduledExecutorService.execute()`로 비동기 실행
- 디스패치 완료 후 자동으로 `complete()` 호출

### 이벤트 리스너

`CopyOnWriteArrayList`로 관리되는 `AsyncListener` 목록:

| 이벤트 | 호출 시점 |
|---|---|
| `onComplete` | `complete()` 호출 시 |
| `onTimeout` | 타임아웃 만료 시 |
| `onError` | 디스패치 중 예외 발생 시 |

## 내부 인터페이스

### `AsyncDispatcher`

```java
@FunctionalInterface
interface AsyncDispatcher {
    void dispatch(String path, ServletRequest request, ServletResponse response) throws Exception;
}
```

서블릿 컨테이너의 디스패치 로직을 주입받는 함수형 인터페이스.

### `SessionHolder`

```java
interface SessionHolder {
    boolean created();
    String sessionId();
}
```

비동기 응답 전송 시 세션 쿠키 발급 여부를 결정하는 인터페이스.

## 스레드 안전성

- `completed`: `AtomicBoolean` — CAS 기반 중복 완료 방지
- `timeout` / `timeoutFuture`: `volatile` — 가시성 보장
- `listeners`: `CopyOnWriteArrayList` — 동시 순회 안전

## 소스 파일

```
was-servlet-core/src/main/java/io/velo/was/servlet/
├── VeloAsyncContext.java          AsyncContext 구현체
└── ServletProxyFactory.java       request.startAsync() 프록시 연동
```
