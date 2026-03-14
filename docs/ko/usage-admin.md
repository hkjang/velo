# Velo WAS 시스템 관리자 가이드

Velo WAS는 인프라 및 시스템 관리자를 위해 JEUS의 `jeusadmin`과 호환되는 인터랙티브 CLI 인터페이스 `velo-admin`을 제공합니다. 관리자는 터미널 환경에서 다양한 컴포넌트의 모니터링, 제어, 배포 및 클러스터 설정을 수행할 수 있습니다.

## 1. 관리 콘솔 접속

`velo-admin` 기능은 강력한 셸 기능을 가지며, 다음과 같이 JLine 라이브러리에 의해 구동됩니다.
로컬 모드에서 기본 실행:
```bash
java -cp was-admin.jar io.velo.was.admin.VeloAdmin
```

단독 구성 파일(`server.yaml`)을 지정하여 서버 환경을 바인딩할 수 있습니다:
```bash
java -cp was-admin.jar io.velo.was.admin.VeloAdmin conf/server.yaml
```

정상적으로 진입 시 `velo>` 프롬프트가 표시되며, 명령어 입력 상태가 됩니다.

## 2. 서버 및 클러스터 프로비저닝 제어

Velo WAS 운영 관리에 핵심적인 노드 및 클러스터링 생명주기 관리 명령이 포함되어 있습니다.

- **`list-servers` / `server-info <name>`**: 현존 서버 목록을 표시하고, 세부 노드 호스트 정보와 서비스 상태를 진단합니다.
- **`start-server <name>` / `stop-server <name>`**: 지목된 서버를 온보딩하거나 점진적 종료(Graceful Shutdown)를 지시합니다.
- **`restart-server <name>`**: 변경된 설정을 바탕으로 서버 프로세스 일시 재가동을 수행할 때 사용합니다.
- **`suspend-server <name>` / `resume-server <name>`**: 서버가 일시적으로 클라이언트 트래픽을 처리하지 않게(suspend) 구동을 중단시키거나 재개(resume)합니다.
- **클러스터 관리**: 클러스터 연동 시 `list-clusters`, `cluster-info <name>`, `start-cluster <name>`, `add-server-to-cluster <cluster> <server>` 등을 통하여 클러스터 전역 토폴로지를 제어합니다.

## 3. 웹 애플리케이션 운영

관리자는 개발자가 배포한 WAR 애플리케이션의 런타임 수명 주기를 무중단 환경에서 관리합니다.

- **`deploy <war-path> <context-path>`**: 시스템 런타임에 동적으로 새 애플리케이션을 배포합니다.
- **`undeploy <name>`**: 배포 자원을 해제하고 매핑된 요청 체인을 정리합니다.
- **`start-application <name>` / `stop-application <name>`**: 일시적인 오류 분석 및 트래픽 분산을 위해 애플리케이션 수신 처리를 활성화 혹은 비활성 상태로 전환합니다.

## 4. 리소스 대시보드 및 지표 모니터링

WAS 모니터링 기능(JMX 연동)을 통해 애플리케이션의 안전성을 담보할 수 있습니다. 

- **`system-info`**: 시스템 운영체제 버전 및 물리적 아키텍처, 프로비저닝 코어 수 스냅샷을 나타냅니다.
- **`jvm-info`**: 작동 중인 JVM 환경 설정(Argument 포함)과 서버 업타임 지표를 제공합니다.
- **`memory-info`**: Heap 및 Non-Heap 등 JVM 메모리 영역의 커밋, 최대 크기 할당량을 확인합니다.
- **`thread-info`**: 현재 동작 중인 스레드의 누적 상태 분석 및 데드락 검출 관리를 지원합니다.
- **`resource-info`**: 종합 CPU, 자원 사용 상태 및 JDBC 커넥션 풀 등의 인프라 사용률을 통합 요약해 표출합니다.

## 5. 정책 보안 및 시스템 로그 제어

- **계정 및 접근 통제**: 콘솔 접근 보안 강화를 위해 `list-users`, `create-user`, `remove-user`, `change-password` 명령으로 관리자 식별을 중앙화합니다. `list-roles`는 배포된 정책 권한 세트를 검토합니다.
- **동적 로깅 레벨 제어**: 서비스의 런타임 부담을 줄이기 위해 `get-log-level <name>` 과 `set-log-level <name> <level>` 을 이용해서 동적(SLF4J/Logback 등 하위 구현체 제어)으로 특정 패키지 레벨 수준의 로그 필터링(`DEBUG`, `INFO`, `ERROR` 등)을 실시간 변경할 수 있습니다.

## 6. 스크립트 기반 구성 자동화

주기적인 점검 및 스크립팅 자동화를 위해 `velo-admin` 자체 스크립트 구성을 지원합니다.
1. `record-script <filepath>`: 입력된 명령 로그 녹화 환경을 구동합니다.
2. `stop-record`: 메모리상에 입력된 작업 목록을 `.velo` 확장자 등으로 내려받습니다.
3. `run-script <filepath>`: 저장된 자동화 스크립트를 재호출합니다.
