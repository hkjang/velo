# Velo WAS 사용자 가이드

Velo WAS는 Tomcat 수준의 서블릿 호환성과 Jetty 수준의 성능/I/O를 지향하는 Netty 기반의 엔터프라이즈 WAS 플랫폼입니다. 본 문서에서는 개발자가 Velo WAS 환경에 웹 애플리케이션을 개발하고 배포하는 방법을 다룹니다.

## 1. 지원되는 표준 스펙
Velo WAS는 **Jakarta Servlet 6.1 API** 규격을 지원하며, 내부적으로 Netty의 비동기 I/O 및 프로토콜 엔진을 활용합니다. 개발자는 표준 서블릿 스펙에 맞추어 프로젝트를 개발하고, `.war` 형태의 아카이브로 빌드해야 합니다.

지원 기능:
- `HttpServlet`을 상속한 기본 서블릿 기능
- `Filter` 체인 및 생명주기 관리
- Request Dispatcher 기능 (상대 경로 및 `forward`, `include` 지원)
- Servlet API 리스너 생명주기
- In-memory `JSESSIONID` 세션 관리

## 2. 애플리케이션 개발 준비

의존성 관리 도구(예: Maven의 `pom.xml`)에 Jakarta Servlet API 의존성을 추가합니다. Velo WAS 자체에서 서블릿 스펙을 구현하므로 `provided` 스코프를 사용합니다.

```xml
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>6.1.0</version>
    <scope>provided</scope>
</dependency>
```

프로젝트 구성을 마치면 `mvn clean package` 등의 명령어로 `.war` 패키징을 수행합니다.

## 3. 애플리케이션 배포

Velo WAS에서는 `conf/server.yaml` 파일 수정을 통한 수동 배포 방식과, `velo-admin` CLI 도구를 이용한 동적 배포 방식을 지원합니다.

### Admin CLI를 통한 배포
가장 권장되는 동적 배포 방식은 `velo-admin` 인터랙티브 셸을 사용하는 것입니다.

1. 로컬 환경 혹은 원격 서버에 연결하여 관리자 셸(`velo-admin`) 환경으로 진입합니다.
2. 아래 명령어들을 활용하여 애플리케이션을 배포하고 제어할 수 있습니다.

```shell
velo> deploy /path/to/your-app.war /yourapp
velo> list-applications
velo> start-application yourapp
```

- `deploy <war-path> <context-path>`: 지정된 WAR 파일을 컨텍스트 경로에 배포합니다.
- `list-applications`: 배포된 앱의 상태와 컨텍스트 경로 목록을 조회합니다.
- `start-application <name>`: 애플리케이션 컨텍스트를 활성화하여 트래픽을 처리합니다.

재배포 및 배포 취소의 경우 다음과 같은 명령어를 지원합니다:
```shell
velo> redeploy yourapp
velo> undeploy yourapp
```

## 4. 로깅 및 로거 설정
웹 애플리케이션은 서버단과 통합된 `SLF4J` 인터페이스를 사용하여 로깅을 처리할 수 있습니다. WAS 시스템 로거를 사용하게 되므로, WAR 내의 `WEB-INF/lib` 등에 `logback-classic` 과 같은 구현체가 중복되어 포함되지 않는지 주의하십시오.

## 5. 자주 발생하는 이슈 대응 가이드
- **ClassNotFoundError 클래스 탐색 이슈**: `WEB-INF/lib`나 `WEB-INF/classes` 등에 의존성이 정상적으로 위치해 있는지 확인하십시오. 
- **Port Conflict (포트 충돌)**: 서버가 기동 시 TCP 바인딩에 실패한 경우 발생합니다. 기존 프로세스가 점유 중이 아닌지 점검하세요. 관리 콘솔에서 `server-info` 명령어로 바인딩 정보를 살필 수 있습니다.
