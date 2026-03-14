# `was-jndi` 모듈 가이드

`was-jndi` 모듈은 Velo WAS 생태계의 Java Naming and Directory Interface (JNDI) 근간을 마련해주는 환경설정 허브입니다. 시스템 인프라 관리자가 선제적으로 `server.yaml` 안에 기입해둔 데이터 소스(Database)나 커넥션 풀을, 개발 중인 애플리케이션 안에서 하드코딩된 연결 문자열(Connection strings) 노출 없이 안전하게 꺼내어 찾아올 수 있도록(Lookup) 브릿지 되어 줍니다.

## 핵심 구성 요소

### 1. `VeloNamingContext` 및 `VeloInitialContextFactory`
웹 애플리케이션 구동간 서버 영토 내부에 지정된 모든 공통 환경 요소(`java:comp/env/`)와 외부 리소스 레퍼런스(Resource References)의 중앙 지휘소이자 등록소입니다.
- 개발 애플리케이션 내에선 표준 규격인 `new InitialContext().lookup("jdbc/mydb")` 호출 구조만으로 이 팩토리 클래스와 매끄럽게 호환되어 연동할 수 있습니다.
- 현재 백그라운드에서 트래픽을 처리중인 활성화된 애플리케이션 단위의 컨텍스트(Context Path)에 따라, 서로에게 철저히 배타적으로 격리된 고유한 JNDI 서브 컨텍스트 환경 객체를 부여해 반환합니다.

### 2. `SimpleConnectionPool` 및 `PooledDataSource`
모듈 내부에 탑재된 경량화 및 통합된 JDBC 커넥션 풀(Connection Pool) 통제 관리자입니다.
- **리소스 가용성 관리(Resource Management)**: Velo WAS 서버가 데이터베이스 쪽으로 무한정 연결을 발산하는 현상을 막기 위해, 외부(Outbound)로 이어지는 커넥션 점유 최대치(Maximum)를 보전(Cap)합니다. 그 외 사용 없는 잉여 자원의 유휴 접속을 유지 파악하며, 정체되거나 지연된 커넥션의 연결을 다시 회수(Reap) 해 데이터베이스단의 과부하를 막는 필수적인 풀링(Pooling) 규칙성 제어를 확립합니다.
- `was-jndi`의 구조는 앞선 JNDI Context 단에 매끄럽게 연결 및 흡수되므로, 클라이언트 애플리케이션 눈에는 철저히 자바 표준인 `javax.sql.DataSource` 인터페이스로만 탈바꿈되어 보이게끔 가공됩니다.
- 또한 `was-admin` CLI 운영 콘솔명령어(`reset-connection-pool`, `flush-connection-pool`, `jdbc-resource-info` 등)와 실시간 연동되어 풀 상황을 통제하고 직관적으로 파악할 길을 제공합니다.
