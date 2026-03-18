# 클러스터 세션 사용 및 설정 가이드

이 문서는 `was-servlet-core`에 추가된 클러스터 세션 저장소 SPI와
`ClusteredHttpSessionStore` 사용 방법을 설명한다.

목표는 다음과 같다.

- 동일 사용자의 요청이 다른 노드로 이동해도 `HttpSession`이 유지될 것
- sticky session을 사용하더라도 특정 노드 장애 시 다른 노드가 세션을 이어받을 수 있을 것
- 세션 변경을 비동기로 복제하되 TTL, 세션 ID 변경, 무효화가 노드 간 일관되게 보일 것
- 직렬화 불가 세션 속성에 대해 정책적으로 통제할 수 있을 것

## 현재 제공 범위

이번 구현으로 다음이 추가되었다.

- `HttpSessionStore` SPI
- `ClusteredHttpSessionStore`
- `SessionRepository` 저장소 추상화
- `SessionReplicationChannel` 비동기 복제 채널 추상화
- `SessionSerializer` / `SessionSerializationPolicy`
- `LatestVersionSessionConflictResolver`
- `SimpleServletContainer(HttpSessionStore, int)` 생성자

현재 상태에서 중요한 점은 다음 두 가지다.

1. 기본 부트스트랩인 `VeloWasApplication`은 아직 기존 `InMemoryHttpSessionStore`를 기본 사용한다.
2. 따라서 클러스터 세션은 현재 **코드에서 저장소를 직접 주입하는 방식**으로 사용한다.

즉, 이번 문서는 "지금 바로 사용할 수 있는 실제 API" 기준이다.  
`server.yaml`에 클러스터 전용 키를 추가해 자동 배선하는 작업은 아직 남아 있다.

## 핵심 구조

### 1. `HttpSessionStore`

서블릿 컨테이너가 의존하는 세션 저장소 인터페이스다.

```java
public interface HttpSessionStore extends AutoCloseable {
    SessionState find(String sessionId);
    SessionState create();
    String changeSessionId(SessionState state);
    void invalidate(String sessionId);
    void addExpirationListener(Consumer<SessionState> listener);
    int purgeExpired();
    int size();
}
```

기존 `InMemoryHttpSessionStore`도 이 인터페이스를 구현하도록 변경되었다.

### 2. `ClusteredHttpSessionStore`

실제 클러스터 세션 저장소다.

- 로컬 노드 캐시를 유지한다.
- 로컬에 세션이 없으면 `SessionRepository`에서 조회한다.
- 세션 변경 시 `SessionRepository`에 저장하고 `SessionReplicationChannel`로 비동기 전파한다.
- 복제 메시지가 늦게 도착해도 `version`, `lastModifiedTime`, `expiresAtEpochMillis`를 기준으로 충돌을 정리한다.

### 3. `SessionRepository`

세션의 "공유 원본" 역할을 한다.

- 예: Redis, DB, 분산 캐시, 외부 KV 저장소
- 저장 단위: `SessionRecord`
- 기본 제공 구현: `InMemorySessionRepository`

`InMemorySessionRepository`는 테스트나 단일 JVM 시뮬레이션용이다.  
실제 멀티 프로세스/멀티 서버 환경에서는 외부 저장소 구현체를 연결하는 것을 권장한다.

### 4. `SessionReplicationChannel`

노드 간 세션 갱신 이벤트를 비동기로 전달한다.

- 메시지 타입: `UPSERT`, `INVALIDATE`
- 기본 제공 구현: `InMemorySessionReplicationChannel`

이 구현도 단일 JVM 테스트용이다.  
운영에서는 메시지 브로커, Redis Pub/Sub, Kafka, NATS 등으로 교체하는 것이 자연스럽다.

### 5. `SessionSerializer`

세션 속성을 직렬화/역직렬화한다.

- 기본 구현: `JavaSessionSerializer`
- 정책:
  - `STRICT`: 직렬화 불가 속성이 있으면 즉시 예외
  - `DROP_NON_SERIALIZABLE`: 직렬화 불가 속성은 복제/저장 시 제외

## 세션 일관성 모델

### 노드 이동

요청이 원래 노드가 아닌 다른 노드로 들어오면 다음 순서로 처리한다.

1. 대상 노드가 로컬 캐시에서 세션을 찾는다.
2. 없으면 `SessionRepository`에서 조회한다.
3. 레코드를 `SessionState`로 복원하여 로컬 캐시에 적재한다.
4. 이후 요청부터는 해당 노드도 같은 세션을 계속 사용한다.

즉, 복제 메시지가 아직 도착하지 않았더라도 공유 저장소만 최신이면 세션은 이어진다.

### sticky session

sticky session이 켜져 있으면 새 세션 ID는 다음 형태가 된다.

```text
JSESSIONID=node-a.0123456789abcdef...
```

- prefix: `stickyRoute`
- suffix: 랜덤 세션 키

sticky session은 로드밸런서가 같은 노드로 계속 붙이도록 힌트를 주는 용도다.  
하지만 실제 지속성은 `SessionRepository`가 담당하므로, 다른 노드로 이동해도 세션이 끊기지 않는다.

### 비동기 복제

세션 변경은 저장 후 채널로 전송된다.

- 속성 추가/교체/삭제
- `touch()`에 의한 접근 시간 갱신
- `setMaxInactiveInterval()`
- `changeSessionId()`
- `invalidate()`

복제는 비동기이므로 짧은 지연이 생길 수 있다.  
대신 각 노드는 저장소 조회로 최신 상태를 따라잡을 수 있다.

### 충돌 해소

기본 충돌 해소기는 `LatestVersionSessionConflictResolver`다.

우선순위는 다음과 같다.

1. `version`
2. `lastModifiedTime`
3. `expiresAtEpochMillis`
4. `ownerNodeId`
5. `sessionId`

즉, 일반적으로 **가장 나중에 기록된 변경이 승리**한다.

### TTL 일관성

클러스터 세션은 상대 시간이 아니라 `expiresAtEpochMillis` 절대 만료시각을 사용한다.

이 방식의 장점은 다음과 같다.

- 어떤 노드에서 세션을 보더라도 동일한 만료 기준을 사용할 수 있다.
- 복제 메시지가 늦게 도착해도 더 긴 TTL 또는 더 짧은 TTL이 버전과 함께 일관되게 반영된다.
- `purgeExpired()`를 어느 노드에서 실행해도 만료 판단이 같아진다.

단, 운영 환경에서는 **노드 간 시계 오차를 작게 유지**해야 한다.  
NTP 동기화를 권장한다.

## 실제 사용 방법

### 가장 간단한 구성

테스트 또는 단일 JVM 개발 환경에서는 아래처럼 사용할 수 있다.

```java
SessionRepository repository = new InMemorySessionRepository();
SessionReplicationChannel replicationChannel = new InMemorySessionReplicationChannel();

HttpSessionStore sessionStore = new ClusteredHttpSessionStore(
        "node-a",
        "node-a",
        true,
        1800,
        repository,
        replicationChannel,
        new JavaSessionSerializer(SessionSerializationPolicy.STRICT),
        LatestVersionSessionConflictResolver.INSTANCE
);

SimpleServletContainer container = new SimpleServletContainer(sessionStore, 60);
```

여기서 `60`은 purge interval 초 단위다.

### 두 노드를 같은 프로세스에서 시뮬레이션

```java
SessionRepository repository = new InMemorySessionRepository();
SessionReplicationChannel replicationChannel = new InMemorySessionReplicationChannel();

SimpleServletContainer nodeA = new SimpleServletContainer(
        new ClusteredHttpSessionStore(
                "node-a", "node-a", true, 1800,
                repository,
                replicationChannel,
                new JavaSessionSerializer(SessionSerializationPolicy.STRICT),
                LatestVersionSessionConflictResolver.INSTANCE),
        60);

SimpleServletContainer nodeB = new SimpleServletContainer(
        new ClusteredHttpSessionStore(
                "node-b", "node-b", true, 1800,
                repository,
                replicationChannel,
                new JavaSessionSerializer(SessionSerializationPolicy.STRICT),
                LatestVersionSessionConflictResolver.INSTANCE),
        60);
```

이 구성은 테스트에서 사용한 방식과 같다.

### 운영 환경에서의 권장 구성

운영에서는 다음처럼 역할을 나누는 편이 좋다.

- `SessionRepository`: Redis/DB/외부 캐시
- `SessionReplicationChannel`: Pub/Sub 또는 메시지 브로커
- `SessionSerializer`: 모든 노드에서 동일한 구현과 정책 사용
- `nodeId`: 클러스터 내에서 유일
- `stickyRoute`: 로드밸런서 라우팅 키와 동일하게 맞춤

## 설정 가이드

### 현재 실제로 설정 가능한 값

현재 코드 기준으로 세션 관련 설정은 두 계층으로 나뉜다.

#### 1. 기존 YAML 설정

이미 연결되어 있는 값이다.

```yaml
server:
  nodeId: node-1
  session:
    timeoutSeconds: 1800
    purgeIntervalSeconds: 60
```

- `server.nodeId`
  - 노드 식별자
  - 클러스터 세션을 수동 배선할 때 그대로 `nodeId`로 재사용하는 것을 권장
- `server.session.timeoutSeconds`
  - 기본 세션 timeout
- `server.session.purgeIntervalSeconds`
  - 만료 세션 정리 주기

#### 2. 코드 주입 설정

아직 YAML에 직접 연결되지 않은 값이다.

| 항목 | 설명 | 권장값 |
|---|---|---|
| `stickyRoute` | 세션 ID에 포함할 라우팅 키 | 일반적으로 `nodeId`와 동일 |
| `stickySessionsEnabled` | sticky route 포함 여부 | LB가 route 기반이면 `true` |
| `SessionRepository` | 공유 세션 저장소 | 외부 저장소 구현 |
| `SessionReplicationChannel` | 노드 간 비동기 복제 채널 | 외부 Pub/Sub 구현 |
| `SessionSerializationPolicy` | 비직렬화 가능 속성 처리 정책 | 운영 기본은 `STRICT` 권장 |
| `SessionConflictResolver` | 충돌 해소 로직 | 기본값 사용 가능 |

### 부트스트랩에 수동 배선하는 예시

`VeloWasApplication` 또는 별도 부트스트랩에서 아래처럼 연결하면 된다.

```java
ServerConfiguration.Session sessionConfig = configuration.getServer().getSession();
String nodeId = configuration.getServer().getNodeId();

SessionRepository repository = buildSharedRepository(configuration);
SessionReplicationChannel replicationChannel = buildReplicationChannel(configuration);

HttpSessionStore sessionStore = new ClusteredHttpSessionStore(
        nodeId,
        nodeId,
        true,
        sessionConfig.getTimeoutSeconds(),
        repository,
        replicationChannel,
        new JavaSessionSerializer(SessionSerializationPolicy.STRICT),
        LatestVersionSessionConflictResolver.INSTANCE
);

SimpleServletContainer servletContainer = new SimpleServletContainer(
        sessionStore,
        sessionConfig.getPurgeIntervalSeconds()
);
```

`buildSharedRepository()`와 `buildReplicationChannel()`은 현재 프로젝트에 아직 포함되지 않았으므로
환경에 맞는 구현체를 추가해야 한다.

## 직렬화 정책 선택 기준

### `STRICT`

권장 기본값이다.

- 장점:
  - 복제되지 않는 세션 속성을 초기에 바로 발견할 수 있다.
  - 노드마다 세션 내용이 달라지는 문제를 예방한다.
- 단점:
  - 직렬화 불가 객체를 세션에 넣는 순간 예외가 발생한다.

다음 경우에 추천한다.

- 운영 환경
- 여러 노드가 같은 세션을 반드시 일관되게 봐야 하는 경우
- 장애보다 데이터 불일치가 더 위험한 경우

### `DROP_NON_SERIALIZABLE`

개발/마이그레이션 단계에서만 제한적으로 고려할 수 있다.

- 장점:
  - 기존 애플리케이션이 당장 죽지 않고 점진적으로 이전 가능
- 단점:
  - 일부 속성이 다른 노드로 복제되지 않을 수 있다.
  - 요청이 노드를 옮기면 세션 내용이 일부 사라진 것처럼 보일 수 있다.

이 정책을 운영 기본값으로 쓰는 것은 권장하지 않는다.

## 세션 ID 변경과 무효화

### `changeSessionId()`

세션 fixation 완화 흐름은 그대로 유지된다.

- 기존 ID 삭제
- 새 ID 생성
- 같은 세션 내용을 새 ID에 저장
- `INVALIDATE(oldId)` + `UPSERT(newId)` 복제

따라서 한 노드에서 세션 ID를 교체해도 다른 노드가 이전 ID를 계속 들고 있을 가능성을 줄일 수 있다.

### `invalidate()`

무효화는 다음 순서로 반영된다.

1. 공유 저장소 삭제
2. 로컬 캐시 삭제
3. `INVALIDATE` 복제 메시지 전송
4. 세션 destroy 이벤트 발생

## 운영 권장사항

- 모든 노드에서 동일한 `SessionSerializer`와 동일한 정책을 사용한다.
- `server.nodeId`는 중복되지 않게 관리한다.
- sticky session을 쓰더라도 공유 저장소 없이 운영하지 않는다.
- `SessionRepository`는 장애 복구 후에도 데이터를 유지할 수 있는 저장소를 권장한다.
- 클럭 동기화(NTP)를 켠다.
- 세션에 대용량 객체를 넣지 않는다.
- 세션 속성은 가능한 한 명시적으로 `Serializable`을 만족시키는 타입으로 정리한다.

## 현재 제한 사항

- 기본 `VeloWasApplication`은 아직 클러스터 세션을 자동 배선하지 않는다.
- `server.yaml`에 `session.cluster.*` 형태의 전용 설정 키는 아직 없다.
- `InMemorySessionRepository`, `InMemorySessionReplicationChannel`은 테스트/샘플용이다.
- 복제는 비동기이므로 아주 짧은 순간에는 노드 간 관찰 시점 차이가 있을 수 있다.

## 관련 문서

- [세션 관리](session-management.md)
- [멀티 노드 구성 가이드](multi-node.md)
- [로드맵](roadmap.md)

## 검증 테스트

클러스터 세션 동작은 다음 테스트로 검증할 수 있다.

```bash
.\scripts\use-local-toolchain.ps1
mvn test -pl was-servlet-core -am
```

주요 테스트:

- `ClusteredHttpSessionStoreTest.sessionSurvivesNodeHopAndKeepsStickyRoute`
- `ClusteredHttpSessionStoreTest.latestWriteWinsWhenTwoNodesUpdateSameSession`
- `ClusteredHttpSessionStoreTest.ttlPurgeIsConsistentAcrossNodes`
- `ClusteredHttpSessionStoreTest.strictSerializationPolicyRejectsNonSerializableAttributes`
