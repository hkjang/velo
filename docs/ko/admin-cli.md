# Admin CLI (velo-admin)

JEUS `jeusadmin` CLI 구조를 기반으로 한 velo-was 운영 관리 CLI이다.
JLine을 사용한 인터랙티브 셸을 제공하며, 14개 카테고리 73개 명령어를 지원한다.

## 실행 방법

```bash
# 로컬 모드 (기본 설정)
java -cp was-admin.jar io.velo.was.admin.VeloAdmin

# 설정 파일 지정
java -cp was-admin.jar io.velo.was.admin.VeloAdmin conf/server.yaml

# 원격 서버 접속
java -cp was-admin.jar io.velo.was.admin.VeloAdmin --remote localhost:9090
```

## 아키텍처

```
VeloAdmin (main)
  ├── CliShell (JLine 인터랙티브 셸)
  ├── CommandRegistry (명령어 등록·검색·그룹핑)
  ├── Command (73개 명령어 인터페이스)
  ├── CommandContext (실행 컨텍스트)
  └── AdminClient (관리 기능 추상화)
        ├── LocalAdminClient (JMX / in-memory)
        └── RemoteAdminClient (HTTP, 미구현)
```

### AdminClient 구현 전략

| 영역 | LocalAdminClient | RemoteAdminClient |
|---|---|---|
| Domain | default 도메인 1개, 생성/삭제 미지원 | REST API 연동 (미구현) |
| Server | `ServerConfiguration`에서 실시간 정보 | REST API 연동 (미구현) |
| Cluster / App / DS / JDBC / JMS | 빈 목록 반환, 조작 미지원 | REST API 연동 (미구현) |
| Thread Pool | `ServerConfiguration.Threading` 기반 | REST API 연동 (미구현) |
| Monitoring | `ManagementFactory` MXBean 직접 접근 | REST API 연동 (미구현) |
| Log | `ConcurrentHashMap` in-memory 로거 관리 | REST API 연동 (미구현) |
| JMX | `PlatformMBeanServer` 직접 접근 | REST API 연동 (미구현) |
| Security | `ConcurrentHashMap` in-memory 사용자 관리 | REST API 연동 (미구현) |

---

## 명령어 레퍼런스

### Basic (7)

| 명령어 | 설명 |
|---|---|
| `help` | 전체 명령어 목록 (카테고리별 그룹) |
| `help <command>` | 특정 명령어 상세 도움말 |
| `exit` | CLI 종료 |
| `quit` | CLI 종료 (alias) |
| `version` | 버전 출력 |
| `history` | 실행 히스토리 안내 (JLine 화살표키) |
| `clear` | 화면 초기화 |

### Domain Management (6)

| 명령어 | 사용법 | 설명 |
|---|---|---|
| `domain-info` | `domain-info [name]` | 도메인 이름·상태·서버 수·속성 |
| `list-domains` | `list-domains` | 도메인 목록 (이름·상태) |
| `create-domain` | `create-domain <name>` | 도메인 생성 |
| `remove-domain` | `remove-domain <name>` | 도메인 삭제 |
| `set-domain-property` | `set-domain-property <domain> <key> <value>` | 속성 설정 |
| `get-domain-property` | `get-domain-property <domain> <key>` | 속성 조회 |

### Server Management (8)

| 명령어 | 사용법 | 설명 |
|---|---|---|
| `list-servers` | `list-servers` | 서버 목록 (이름·nodeId·상태) |
| `server-info` | `server-info [name]` | 상세 정보 (host·port·uptime·스레드) |
| `start-server` | `start-server <name>` | 서버 시작 |
| `stop-server` | `stop-server <name>` | 서버 중지 |
| `restart-server` | `restart-server <name>` | 서버 재시작 |
| `suspend-server` | `suspend-server <name>` | 서버 일시 중단 |
| `resume-server` | `resume-server <name>` | 중단 서버 재개 |
| `kill-server` | `kill-server <name>` | 서버 강제 종료 |

### Cluster Management (7)

| 명령어 | 사용법 | 설명 |
|---|---|---|
| `list-clusters` | `list-clusters` | 클러스터 목록 |
| `cluster-info` | `cluster-info <name>` | 클러스터 상태·멤버 목록 |
| `start-cluster` | `start-cluster <name>` | 클러스터 시작 |
| `stop-cluster` | `stop-cluster <name>` | 클러스터 중지 |
| `restart-cluster` | `restart-cluster <name>` | 클러스터 재시작 |
| `add-server-to-cluster` | `add-server-to-cluster <cluster> <server>` | 서버 추가 |
| `remove-server-from-cluster` | `remove-server-from-cluster <cluster> <server>` | 서버 제거 |

### Application Management (7)

| 명령어 | 사용법 | 설명 |
|---|---|---|
| `list-applications` | `list-applications` | 앱 목록 (이름·contextPath·상태) |
| `application-info` | `application-info <name>` | 앱 상세 (servlet·filter 수) |
| `deploy` | `deploy <war-path> <context-path>` | 애플리케이션 배포 |
| `undeploy` | `undeploy <name>` | 배포 해제 |
| `redeploy` | `redeploy <name>` | 재배포 |
| `start-application` | `start-application <name>` | 앱 시작 |
| `stop-application` | `stop-application <name>` | 앱 중지 |

### Datasource Management (5)

| 명령어 | 사용법 | 설명 |
|---|---|---|
| `list-datasources` | `list-datasources` | 데이터소스 목록 |
| `datasource-info` | `datasource-info <name>` | 상세 (URL·활성/유휴 커넥션) |
| `enable-datasource` | `enable-datasource <name>` | 데이터소스 활성화 |
| `disable-datasource` | `disable-datasource <name>` | 데이터소스 비활성화 |
| `test-datasource` | `test-datasource <name>` | 연결 테스트 |

### JDBC / Connection Pool (4)

| 명령어 | 사용법 | 설명 |
|---|---|---|
| `list-jdbc-resources` | `list-jdbc-resources` | JDBC 리소스 목록 |
| `jdbc-resource-info` | `jdbc-resource-info <name>` | 드라이버·URL·커넥션 풀 상태 |
| `reset-connection-pool` | `reset-connection-pool <pool>` | 커넥션 풀 초기화 |
| `flush-connection-pool` | `flush-connection-pool <pool>` | 커넥션 풀 플러시 |

### JMS Management (5)

| 명령어 | 사용법 | 설명 |
|---|---|---|
| `list-jms-servers` | `list-jms-servers` | JMS 서버 목록 |
| `jms-server-info` | `jms-server-info <name>` | JMS 서버 상태·타입·destination 수 |
| `list-jms-destinations` | `list-jms-destinations` | destination 목록 |
| `jms-destination-info` | `jms-destination-info <name>` | consumer 수·바이트 사용량 |
| `purge-jms-queue` | `purge-jms-queue <queue>` | 큐 메시지 전체 삭제 |

### Thread / Resource Management (4)

| 명령어 | 사용법 | 설명 |
|---|---|---|
| `list-thread-pools` | `list-thread-pools` | 스레드 풀 목록 (boss/worker/business) |
| `thread-pool-info` | `thread-pool-info <pool>` | 활성·풀 크기·완료 태스크·큐 크기 |
| `reset-thread-pool` | `reset-thread-pool <pool>` | 스레드 풀 초기화 |
| `resource-info` | `resource-info` | CPU·메모리·풀·DS·JDBC·JMS 전체 요약 |

### Monitoring (5)

| 명령어 | 설명 | 데이터 소스 |
|---|---|---|
| `system-info` | OS 이름·버전·아키텍처·프로세서 수 | `System.getProperty()` |
| `jvm-info` | JVM 이름·벤더·버전·업타임·실행 인자 | `RuntimeMXBean` |
| `memory-info` | Heap/Non-Heap used·committed·max·사용률 | `MemoryMXBean` |
| `thread-info` | 스레드 수·피크·데몬·데드락 | `ThreadMXBean` |
| `transaction-info` | 활성·커밋·롤백 트랜잭션·타임아웃 | 시뮬레이션 |

### Log Management (4)

| 명령어 | 사용법 | 설명 |
|---|---|---|
| `list-loggers` | `list-loggers` | 로거 목록 (이름·레벨) |
| `logger-info` | `logger-info <name>` | 레벨·effectiveLevel·additivity·핸들러 |
| `get-log-level` | `get-log-level <name>` | 로거 레벨 조회 |
| `set-log-level` | `set-log-level <name> <level>` | 로거 레벨 변경 (TRACE/DEBUG/INFO/WARN/ERROR/OFF) |

### JMX / MBean Management (4)

| 명령어 | 사용법 | 설명 |
|---|---|---|
| `list-mbeans` | `list-mbeans` | 플랫폼 MBean 전체 목록 |
| `get-mbean-attribute` | `get-mbean-attribute <mbean> <attr>` | MBean 속성 읽기 |
| `set-mbean-attribute` | `set-mbean-attribute <mbean> <attr> <value>` | MBean 속성 쓰기 (자동 타입 변환) |
| `invoke-mbean-operation` | `invoke-mbean-operation <mbean> <op> [params...]` | MBean 오퍼레이션 호출 |

**JMX 사용 예시:**

```
velo> list-mbeans
OBJECT NAME                                                  CLASS
------------------------------------------------------------------------------------------------------
java.lang:type=Runtime                                       sun.management.RuntimeImpl
java.lang:type=Memory                                        sun.management.MemoryImpl
...
Total: 42 MBeans

velo> get-mbean-attribute java.lang:type=Runtime VmName
VmName = OpenJDK 64-Bit Server VM
```

### Security Management (5)

| 명령어 | 사용법 | 설명 |
|---|---|---|
| `list-users` | `list-users` | 사용자 목록 |
| `create-user` | `create-user <user> <password>` | 사용자 생성 (중복 검사) |
| `remove-user` | `remove-user <user>` | 사용자 삭제 |
| `change-password` | `change-password <user> <new-password>` | 비밀번호 변경 |
| `list-roles` | `list-roles` | 역할 목록 (admin, operator, monitor, deployer) |

### Script / Automation (3)

| 명령어 | 사용법 | 설명 |
|---|---|---|
| `run-script` | `run-script <file>` | 스크립트 파일 실행 (`#` 주석 지원) |
| `record-script` | `record-script <file>` | 명령어 녹화 시작 |
| `stop-record` | `stop-record` | 녹화 중지·파일 저장 |

**스크립트 예시:**

```bash
# deploy-check.velo
version
list-servers
server-info
memory-info
thread-info
list-applications
```

```
velo> run-script deploy-check.velo
```

---

## DTO 구조 (AdminClient 내부 record)

```java
record DomainSummary(String name, String status)
record DomainStatus(String name, String status, String adminServerName,
                    int serverCount, Map<String, String> properties)

record ServerSummary(String name, String nodeId, String status)
record ServerStatus(String name, String nodeId, String status, String host, int port,
                    String transport, boolean tlsEnabled, long uptimeMillis,
                    int bossThreads, int workerThreads, int businessThreads)

record ClusterSummary(String name, int memberCount, String status)
record ClusterStatus(String name, String status, List<String> members)

record AppSummary(String name, String contextPath, String status)
record AppStatus(String name, String contextPath, String status,
                 int servletCount, int filterCount)

record DatasourceSummary(String name, String type, String status)
record DatasourceStatus(String name, String type, String url, String status,
                        int activeConnections, int idleConnections, int maxConnections)

record JdbcResourceSummary(String name, String poolName, String type)
record JdbcResourceStatus(String name, String poolName, String driverClass, String url,
                          int activeConnections, int idleConnections, int maxPoolSize)

record JmsServerSummary(String name, String status)
record JmsServerStatus(String name, String status, String type, int destinationCount)

record JmsDestinationSummary(String name, String type, int messageCount)
record JmsDestinationStatus(String name, String type, int messageCount,
                            int consumerCount, long bytesUsed)

record ThreadPoolSummary(String name, int activeCount, int poolSize, int maxPoolSize)
record ThreadPoolStatus(String name, int activeCount, int poolSize, int maxPoolSize,
                        long completedTaskCount, int queueSize)

record LoggerSummary(String name, String level)
record LoggerStatus(String name, String level, String effectiveLevel,
                    boolean additivity, List<String> handlers)

record MBeanSummary(String objectName, String className)
```

---

## 소스 구조

```
was-admin/src/main/java/io/velo/was/admin/
├── VeloAdmin.java                     진입점 (73개 명령어 등록)
├── cli/
│   ├── CliShell.java                  JLine 인터랙티브 셸
│   ├── Command.java                   명령어 인터페이스
│   ├── CommandCategory.java           14개 카테고리 enum
│   ├── CommandContext.java            실행 컨텍스트
│   ├── CommandRegistry.java           등록·검색·그룹핑
│   ├── CommandResult.java             실행 결과 record
│   └── ScriptRecorder.java           스크립트 녹화
├── client/
│   ├── AdminClient.java              관리 기능 인터페이스 + 20개 DTO
│   ├── LocalAdminClient.java         로컬 구현 (586줄)
│   └── RemoteAdminClient.java        원격 구현 (stub)
└── command/
    ├── basic/          help, exit, quit, clear, history, version
    ├── domain/         domain-info, list-domains, create/remove, set/get-property
    ├── server/         list, info, start/stop/restart/suspend/resume/kill
    ├── cluster/        list, info, start/stop/restart, add/remove-server
    ├── application/    list, info, deploy/undeploy/redeploy, start/stop
    ├── datasource/     list, info, enable/disable, test
    ├── jdbc/           list, info, reset/flush connection-pool
    ├── jms/            list-servers/destinations, info, purge-queue
    ├── thread/         reset-pool, resource-info
    ├── monitoring/     system/jvm/memory/thread/transaction-info
    ├── log/            list, info, get/set-log-level
    ├── jmx/            list-mbeans, get/set-attr, invoke-operation
    ├── security/       list-users/roles, create/remove-user, change-password
    └── script/         run-script, record-script, stop-record
```

---

## 테스트

```bash
# 전체 테스트 (94개)
mvn test -pl was-admin -am

# 특정 카테고리만
mvn test -pl was-admin -am -Dtest="AllCommandsTest\$ServerCommands"
mvn test -pl was-admin -am -Dtest="AllCommandsTest\$JmxCommands"
```

### 테스트 커버리지

| 카테고리 | 테스트 수 | 검증 내용 |
|---|---|---|
| Registry | 4 | 73개 명령어, 14개 카테고리, 메타데이터, 36개 명령어 인자 누락 검증 |
| Basic | 8 | help, help(specific/invalid), exit, quit, version, history, clear |
| Domain | 9 | list/info/create/remove/set/get + 기본값 + 인자 부족 |
| Server | 9 | list/info/start/stop/restart/suspend/resume/kill + 인자 부족 |
| Cluster | 8 | list/info/start/stop/restart/add/remove + 인자 부족 |
| Application | 8 | list/info/deploy/undeploy/redeploy/start/stop + 인자 부족 |
| Datasource | 5 | list/info/enable/disable/test |
| JDBC | 4 | list/info/reset/flush |
| JMS | 5 | list-servers/info/list-destinations/dest-info/purge |
| Thread | 6 | list/info(정상·실패)/reset(정상·실패)/resource-info |
| Monitoring | 5 | system/jvm/memory/thread/transaction |
| Log | 6 | list/info/get/set/잘못된 레벨/없는 로거 |
| JMX | 5 | list/get-attr/잘못된 MBean/set 인자 부족/invoke 인자 부족 |
| Security | 7 | list/create+remove/중복/없는 유저/비밀번호/roles |
| Script | 5 | record+stop/run-script/인자 누락/비녹화 상태 stop |

---

## 향후 확장

| 항목 | 설명 |
|---|---|
| `RemoteAdminClient` 구현 | Admin REST API 엔드포인트 구현 후 HTTP 연동 |
| Application deploy/undeploy | WAR 실제 배포·해제 로직 |
| 실제 Datasource/JDBC/JMS | 리소스 등록 시 실시간 상태 조회 |
| Tab completion | JLine `Completer` 연동으로 명령어·인자 자동 완성 |
| 패키징 | `maven-assembly-plugin`으로 fat JAR + 실행 스크립트 (`bin/velo-admin.sh`) |
