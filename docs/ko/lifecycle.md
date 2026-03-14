# Velo WAS 라이프사이클 및 IPL 가이드

본 문서는 Velo WAS의 초기 프로그램 로드(IPL, Initial Program Load), 안전한 종료(Graceful Shutdown), 재기동(Restart) 절차 및 상태 모니터링 방법에 대해 설명합니다. 무중단 배포를 유지하고 시스템 무결성을 확보하기 위해서는 서버의 생명주기를 정확히 이해하는 것이 필수적입니다.

## 1. 초기 프로그램 로드 (IPL) / 서버 기동

Velo WAS의 기동 단계는 환경 설정 초기화, 네트워크 리스너 바인딩, 클러스터 토폴로지 구성, 그리고 초기 애플리케이션의 배포(Deploy) 과정을 포함합니다.

### 1.1 기동 방식
일반적으로 `was-bootstrap` 모듈을 호출하는 시작 스크립트나 명령어를 통해 서버를 구동합니다.

```bash
# 기본 설정파일(conf/server.yaml)을 읽어 기동
java -jar was-bootstrap.jar

# 특정 설정 파일 지정 기동
java -jar was-bootstrap.jar conf/production.yaml
```

### 1.2 부트 시퀀스 (IPL 수행 단계)
1. **설정 해석**: YAML 설정 파일을 파싱하여 `ServerConfiguration` 내부 객체 구성을 완료합니다.
2. **서브시스템 초기화**: 
   - `was-observability` 모듈을 구동하여 접근/에러/감사 로그 스트림을 확보합니다.
   - `was-jndi` 모듈을 통해 JNDI 컨텍스트와 데이터베이스 커넥션 풀을 미리 체결해 둡니다.
3. **컨테이너 부트**: 핵심인 `SimpleServletContainer` 인스턴스를 메모리에 할당합니다.
4. **애플리케이션 배포 단계**: `WEB-INF/web.xml`을 파싱하고, `was-deploy` 모듈을 거쳐 지정된 정적 WAR 파일들을 런타임에 안착시킵니다.
5. **네트워크 바인딩**: `was-transport-netty` 모듈이 OS의 지정된 포트(HTTP/TCP)를 점유(Bind)합니다.
   - *참고*: 만약 이미 사용 중인 포트라면 포트 마스킹 등에 의한 조용한 실패(Silent Failure)를 막기 위해 의도적으로 `BindException` 에러를 표출하고 서버 기동을 즉시 멈춥니다.
6. **준비 상태(Ready State)**: 서버는 "Started" 메시지를 로그에 남기고 외부 트래픽 수신 모드에 돌입합니다. *(이 시점부터 Admin CLI 접속이 가능해집니다.)*

## 2. 안전한 종료 (Graceful Shutdown)

Velo WAS는 진행 중(In-flight)인 요청이 비정상적으로 끊기거나 트랜잭션이 유실되는 것을 막기 위해 단계적인 안전 종료 시퀀스를 구현하고 있습니다.

### 2.1 종료 트리거
종료는 다음 방법으로 촉발됩니다:
- **OS 시그널**: 쉘에서 `SIGTERM` 신호를 전송하거나(`kill -15 <PID>`) 콘솔에서 `Ctrl+C`를 입력했을 때.
- **Admin CLI**: `velo-admin` 환경에서 `stop-server <server-name>` 명령을 하달했을 때.

### 2.2 종료 시퀀스
1. **신규 수신 거부**: Netty의 `bossGroup`이 즉시 신규 커넥션(소켓 연결) 수락을 중단합니다.
2. **진행 중인 요청 보장**: 기존에 연결되어 있던 Keep-alive 커넥션들은 현재 처리 중인 HTTP 응답까지만 무사히 반환한 뒤 안전하게 TCP 세션을 닫도록 지시받습니다.
3. **유예 기간 (Grace Period)**: 시스템은 `server.yaml`에 정의된 `gracefulShutdownMillis` (기본값: 30초) 시간 동안 스레드가 자연 소멸하기를 기다립니다.
   - 유예 시간 내에 모든 요청 처리가 끝나면 종료 시퀀스는 지체 없이 바로 다음 단계로 넘어갑니다.
   - 유예 시간이 만료되었음에도 끝나지 않은 스레드가 있다면, 무한 대기(Hanging) 현상을 막기 위해 `workerGroup`이 남은 스레드를 강제(Forcefully) 폐기시킵니다.
4. **서브시스템 자원 회수**: 
   - 데이터베이스 커넥션 풀을 정리(Flush)합니다.
   - 애플리케이션들을 내려(Undeploy) 각자 정의된 `ServletContextListener.contextDestroyed()` 이벤트를 안전하게 호출해 줍니다.
   - JVM 프로세스가 정상 종료 상태 인 `0` 코드를 반환하며 완전히 내려갑니다.

## 3. 서버 일시 정지 (Suspend) 및 재개 (Resume)

단순 데이터베이스 점검이나 짧은 일시적 통신 단절이 필요할 때, 굳이 JVM을 내렸다가 무겁게 재기동할 필요 없이 트래픽만 제어할 수 있습니다.

- **일시 정지 (`suspend-server <name>`)**: Netty 파이프라인에게 인바운드 데이터 읽기 행위를 일시 중지하도록 지시합니다. 서버 프로세스 자체와 내부 세션/상태는 그대로 유지되나, 외부에서 들어오는 새로운 트래픽은 OS의 백로그 한도에 도달할 때까지 무한 대기(Hang)하거나 거절당하게 됩니다.
- **재개 (`resume-server <name>`)**: 보류되었던 네트워크 리스너의 읽기 모드가 다시 활성화되며, 대기 줄(Queue)에 쌓여있던 요청들을 일제히 소화하기 시작합니다.

## 4. 서버 재기동 (Restart)

재기동은 본질적으로 앞서 설명한 **안전한 종료(Graceful Shutdown)** 직후 **초기 프로그램 로드(IPL)** 를 연달아 수행하는 일련의 과정입니다.

### 4.1 Admin CLI 활용
```shell
velo> restart-server <server-name>
```
*참고*: 로컬 머신에서 바로 Admin CLI를 부착해 사용하고 있었다면, JVM이 내려갔다 올라오는 동안 짧은 찰나에 연결이 끊어질 수 있습니다.

### 4.2 무중단 롤링 재기동 (클러스터 환경)
엔터프라이즈의 고가용성(HA) 환경에서는 Velo WAS 노드들의 앞에 Load Balancer(서드파티 웹서버나 L4 노드 등)를 두어 관리합니다.
1. Load Balancer 단에서 트래픽이 더이상 들어오지 못하게 막은 뒤, 해당 노드에 `suspend-server node-1` 을 지시합니다.
2. 수행 중이던 활성 연결들이 완전히 빠져나가길(Drain) 기다립니다.
3. `restart-server node-1`을 실행하여 메모리 단편화 등을 해결하거나 서버 OS의 패치를 반영합니다.
4. IPL 기동이 달성되면 `/health` 엔드포인트를 타격해 생존 및 정상 여부를 검증합니다.
5. 이어지는 `node-2` 및 다른 서버들도 동일한 방법으로 순차 적용(Rolling) 하여 전체 무중단 서비스를 이어나갑니다.

## 5. 라이프사이클 생존 모니터링 (Health Check)

서버 기동의 성패나 운영 중인 건강 상태는 아래의 방법으로 체크할 수 있습니다:

### 5.1 내장형 검증 엔드포인트
Velo WAS는 가장 기초적인 무거운 서블릿 환경을 배제하고 즉시 응답 가능한 경량화된 프로브(Probe)를 내장하고 있습니다. (`HttpHandlerRegistry`에서 작동)
- `http://<host>:<port>/health`: 시스템 EventLoop 가 포트에 성공적으로 바인딩되었고 락(Lock)에 걸리지 않은 채 숨쉬고 있다면 HTTP `200 OK`를 빠르게 반환합니다.
- `http://<host>:<port>/info`: 현재 가동된 바이너리 빌드의 버전이나 노드 명칭 정보를 수집할 수 있습니다.

### 5.2 Admin CLI 모니터링 현황
`velo-admin`을 이용하여 아래 파라미터로 실시간으로 생명주기 표지판을 조회 가능합니다:
- **`server-info`**: 서버가 완전히 IPL에 성공한 이후로부터의 경과 시간(`uptimeMillis`)과, 현 서버의 정확한 구동 스테이터스(`STARTING`, `RUNNING`, `SUSPENDED`, `STOPPING`)를 식별합니다.
- **`resource-info`**: IPL 초반부에 구성된 백그라운드 스레드 풀 및 DB 커넥션 자원이 이상 없이 최대 할당량을 확보했는지 점검할 수 있습니다.
