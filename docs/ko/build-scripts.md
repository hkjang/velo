# 빌드 / 기동 / 종료 스크립트 가이드

Velo WAS는 Linux/macOS, Windows CMD, Windows PowerShell 각 환경에 맞는 빌드·기동·종료 스크립트를 제공한다.
모든 스크립트는 `bin/` 디렉토리에 위치한다.

## 디렉토리 구조

```
bin/
├── build.sh          # Linux / macOS 빌드
├── build.bat         # Windows CMD 빌드
├── build.ps1         # Windows PowerShell 빌드
├── start.sh          # Linux / macOS 기동
├── start.bat         # Windows CMD 기동
├── start.ps1         # Windows PowerShell 기동
├── stop.sh           # Linux / macOS 종료
├── stop.bat          # Windows CMD 종료
└── stop.ps1          # Windows PowerShell 종료
```

## 사전 요구사항

### 자동 감지 도구체인

스크립트는 프로젝트 내 로컬 도구체인을 먼저 찾고, 없으면 시스템 설치를 사용한다.

| 도구 | 로컬 경로 | 시스템 폴백 |
|------|-----------|-------------|
| JDK 21 | `.tools/jdk/jdk-21.0.10+7` | `JAVA_HOME` 환경변수 |
| Maven 3.9 | `.tools/maven/apache-maven-3.9.13` | `mvn` 명령어 (PATH) |

> **중요**: Velo WAS는 Java 21 switch expression 등의 문법을 사용하므로 반드시 JDK 21 이상이 필요하다.

---

## 빌드 스크립트

### 기본 사용법

```bash
# Linux / macOS
./bin/build.sh

# Windows CMD
bin\build.bat

# Windows PowerShell
.\bin\build.ps1
```

### 옵션

| 옵션 | sh | bat | ps1 | 설명 |
|------|-----|-----|-----|------|
| Clean | `-c`, `--clean` | `-c`, `--clean` | `-Clean` | 빌드 전 clean 수행 |
| Test | `-t`, `--test` | `-t`, `--test` | `-Test` | 테스트 실행 |
| Skip Tests | `-s`, `--skip-tests` | `-s`, `--skip-tests` | (기본값) | 테스트 건너뛰기 (기본) |
| Package | `-p`, `--package` | `-p`, `--package` | `-Package` | Fat JAR 패키징 |
| Quiet | `-q`, `--quiet` | `-q`, `--quiet` | `-Quiet` | 출력 최소화 |
| Help | `-h`, `--help` | `-h`, `--help` | `-Help` | 도움말 표시 |

### 모듈 지정 빌드

특정 모듈만 빌드할 수 있다. 의존 모듈은 자동으로 포함된다 (`-am`).

```bash
# 단일 모듈
./bin/build.sh was-admin
bin\build.bat was-admin
.\bin\build.ps1 -Module was-admin

# 복수 모듈
./bin/build.sh was-webadmin was-bootstrap
bin\build.bat was-webadmin was-bootstrap
.\bin\build.ps1 -Module was-webadmin,was-bootstrap

# 접두사 "was-" 생략 가능
./bin/build.sh admin webadmin
```

### 전체 모듈 목록

| 모듈명 | 설명 |
|--------|------|
| `was-config` | 서버 설정 (YAML 파싱, 데이터 클래스) |
| `was-observability` | 메트릭 수집, 로깅 |
| `was-protocol-http` | HTTP/1.1, HTTP/2 프로토콜 처리 |
| `was-transport-netty` | Netty 전송 레이어 |
| `was-servlet-core` | Jakarta Servlet 6.1 구현 |
| `was-classloader` | 애플리케이션 클래스로더 격리 |
| `was-deploy` | WAR 배포, 핫 디플로이 |
| `was-jndi` | JNDI 디렉토리 서비스 |
| `was-admin` | CLI 관리도구 (73개 명령어) |
| `was-jsp` | JSP 엔진 |
| `was-tcp-listener` | TCP 소켓 리스너 |
| `was-webadmin` | 웹 관리 콘솔 |
| `was-bootstrap` | 부트스트랩, Fat JAR 진입점 |

### 빌드 예시

```bash
# 전체 클린 빌드 + 테스트
./bin/build.sh -c -t

# 전체 패키징 (Fat JAR 생성)
./bin/build.sh -p

# was-webadmin만 조용히 빌드
./bin/build.sh -q was-webadmin

# Windows CMD: 클린 패키징
bin\build.bat -c -p

# PowerShell: 전체 클린 빌드 + 테스트 + 패키징
.\bin\build.ps1 -Clean -Test -Package
```

### Fat JAR 위치

패키징 완료 후 Fat JAR 경로:

```
was-bootstrap/target/was-bootstrap-0.5.5-jar-with-dependencies.jar
```

---

## 기동 스크립트

### 기본 사용법

```bash
# Linux / macOS — 포그라운드
./bin/start.sh

# Linux / macOS — 데몬 모드
./bin/start.sh -d

# Windows CMD
bin\start.bat
bin\start.bat -d

# Windows PowerShell
.\bin\start.ps1
.\bin\start.ps1 -Daemon
```

### 옵션

| 옵션 | sh | bat | ps1 | 설명 |
|------|-----|-----|-----|------|
| Config | `-c <path>`, `--config <path>` | `-c <path>`, `--config <path>` | `-Config <path>` | 설정 파일 경로 (기본: `conf/server.yaml`) |
| Daemon | `-d`, `--daemon` | `-d`, `--daemon` | `-Daemon` | 백그라운드 실행 |
| JVM 옵션 | `-j <opts>`, `--jvm-opts <opts>` | `-j <opts>`, `--jvm-opts <opts>` | `-JvmOpts <opts>` | JVM 옵션 (기본: `-Xms256m -Xmx1g -XX:+UseZGC`) |
| Help | `-h`, `--help` | `-h`, `--help` | `-Help` | 도움말 표시 |

### 환경변수

| 환경변수 | 설명 | 기본값 |
|----------|------|--------|
| `JAVA_HOME` | JDK 경로 (로컬 도구체인 없을 때) | - |
| `VELO_JVM_OPTS` | JVM 옵션 오버라이드 | `-Xms256m -Xmx1g -XX:+UseZGC` |
| `VELO_CONFIG` | 설정 파일 경로 오버라이드 (bat만 해당) | `conf/server.yaml` |

### 동작 방식

1. **Fat JAR 자동 빌드**: Fat JAR가 없으면 `build.sh -p -q`를 자동 실행
2. **PID 관리**: 데몬 모드 시 `velo-was.pid` 파일에 프로세스 ID 기록
3. **중복 기동 방지**: PID 파일이 존재하고 해당 프로세스가 실행 중이면 기동 거부
4. **로그 출력**: 데몬 모드 시 `logs/velo-was.out`, `logs/velo-was.err`에 출력

### 기동 예시

```bash
# 기본 포그라운드 기동
./bin/start.sh

# 운영 설정으로 데몬 기동
./bin/start.sh -d -c conf/prod.yaml

# 메모리 4GB, ZGC로 기동
./bin/start.sh -d -j "-Xms1g -Xmx4g -XX:+UseZGC"

# Windows PowerShell: 데몬 기동
.\bin\start.ps1 -Daemon -Config conf\prod.yaml

# Windows CMD: 데몬 기동
bin\start.bat -d -c conf\prod.yaml
```

### JVM 시스템 프로퍼티

기동 시 자동으로 설정되는 JVM 프로퍼티:

| 프로퍼티 | 값 |
|----------|-----|
| `-Dvelo.config` | 설정 파일 경로 |
| `-Dvelo.home` | 프로젝트 루트 경로 |

---

## 종료 스크립트

### 기본 사용법

```bash
# Linux / macOS
./bin/stop.sh

# Windows CMD
bin\stop.bat

# Windows PowerShell
.\bin\stop.ps1
```

### 옵션

| 옵션 | sh | bat | ps1 | 설명 |
|------|-----|-----|-----|------|
| Force | `-f`, `--force` | `-f`, `--force` | `-Force` | 즉시 강제 종료 |
| Timeout | `-t <sec>`, `--timeout <sec>` | - | `-Timeout <sec>` | Graceful 종료 대기 시간 (기본: 30초) |
| Help | `-h`, `--help` | `-h`, `--help` | `-Help` | 도움말 표시 |

### 종료 방식

#### Graceful Shutdown (기본)

1. PID 파일 확인 → 프로세스 존재 확인
2. SIGTERM 전송 (Linux/macOS) 또는 `taskkill` (Windows)
3. 최대 30초 대기 (5초 간격으로 진행 상황 출력)
4. 타임아웃 시 SIGKILL / 강제 종료

#### Force Kill (`-f`)

PID 파일 또는 프로세스 탐색 후 즉시 SIGKILL / `taskkill /F` 실행.

### 프로세스 탐색 순서

| 순위 | 방법 | 설명 |
|------|------|------|
| 1 | PID 파일 | `velo-was.pid` 파일 확인 |
| 2 | 프로세스 검색 | `was-bootstrap.*jar-with-dependencies` 명령줄 패턴 매칭 |

### 종료 예시

```bash
# 기본 graceful 종료
./bin/stop.sh

# 즉시 강제 종료
./bin/stop.sh -f

# 10초 타임아웃으로 종료
./bin/stop.sh -t 10

# Windows CMD: 강제 종료
bin\stop.bat -f

# PowerShell: 60초 타임아웃
.\bin\stop.ps1 -Timeout 60
```

---

## 플랫폼별 차이점

### Linux / macOS (.sh)

- `#!/usr/bin/env bash`, `set -euo pipefail` 사용
- SIGTERM/SIGKILL 시그널 사용
- `pgrep` 명령으로 프로세스 검색
- PID 파일 기반 관리

### Windows CMD (.bat)

- `setlocal enabledelayedexpansion` 사용
- `taskkill` / `tasklist` 명령 사용
- `wmic` 명령으로 프로세스 검색 (커맨드라인 패턴 매칭)
- `start /B` 명령으로 백그라운드 실행

### Windows PowerShell (.ps1)

- `$ErrorActionPreference = "Stop"` 사용
- `Start-Process` / `Stop-Process` cmdlet 사용
- `Get-WmiObject Win32_Process` 또는 `Get-Process`로 프로세스 검색
- `Start-Process -WindowStyle Hidden -PassThru`로 백그라운드 실행
- 기동 2초 후 프로세스 생존 확인

---

## 운영 시나리오

### 개발 환경

```bash
# 빌드 후 포그라운드 기동 (Ctrl+C로 종료)
./bin/build.sh && ./bin/start.sh
```

### 스테이징/운영 환경

```bash
# 클린 빌드 + 패키징
./bin/build.sh -c -p

# 데몬 기동
./bin/start.sh -d -c conf/prod.yaml -j "-Xms2g -Xmx4g -XX:+UseZGC"

# 상태 확인
curl http://localhost:8080/admin/api/status

# graceful 종료
./bin/stop.sh

# 롤링 재시작
./bin/stop.sh && ./bin/start.sh -d -c conf/prod.yaml
```

### 단일 모듈 수정 후 빠른 반영

```bash
# webadmin만 재빌드 → 패키징 → 재기동
./bin/stop.sh
./bin/build.sh -p was-webadmin was-bootstrap
./bin/start.sh -d
```
