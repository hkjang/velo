# was-bootstrap

서버 시작점 및 샘플 애플리케이션 모듈. YAML 설정 파일을 로드하고, 서블릿 컨테이너에 샘플 앱을 배포한 뒤, Netty 서버를 기동한다.

## 클래스 목록

### `VeloWasApplication`

서버 메인 클래스.

**기동 흐름:**
1. `conf/server.yaml` (또는 CLI 인수로 지정한 경로) 로드 및 검증
2. `SimpleServletContainer` 생성
3. 샘플 서블릿 앱 배포 (`/app` context path)
4. `HttpHandlerRegistry`에 내장 엔드포인트 등록 + 서블릿 컨테이너를 폴백으로 설정
5. `NettyServer` 생성 및 시작
6. shutdown hook 등록 (graceful shutdown)

**내장 엔드포인트:**

| 경로 | 메서드 | 설명 |
|---|---|---|
| `/health` | GET, HEAD | 서버 상태 확인 (`{"status":"UP","name":"...","nodeId":"..."}`) |
| `/info` | GET, HEAD | 서버 정보 (`{"product":"velo-was","phase":"..."}`) |

**실행 방법:**
```bash
java -jar was-bootstrap/target/was-bootstrap-0.5.2-jar-with-dependencies.jar [config-path]
```

- `config-path` 미지정 시 `conf/server.yaml` 사용

### `ServerConfigurationLoader`

SnakeYAML을 사용하여 YAML 파일을 `ServerConfiguration` 객체로 로드하는 유틸리티.

- 중복 키 허용 안 함 (`allowDuplicateKeys=false`)
- 빈 설정 파일일 경우 예외 발생
- 로드 후 `validate()` 호출

### `SampleHelloServlet`

샘플 `HttpServlet` 구현. `GET /app/hello` 엔드포인트.

응답 예시:
```json
{
  "message": "Hello from Velo Servlet",
  "contextPath": "/app",
  "servletPath": "/hello",
  "visits": 1,
  "requestCount": 1,
  "lifecycle": "started"
}
```

- 세션 기반 방문 횟수(`visits`) 추적
- `ServletContext` 속성에서 `requestCount`와 `appLifecycle` 읽기

### `SampleTraceFilter`

샘플 `Filter` 구현.

- 응답 인코딩을 `UTF-8`로 설정
- `X-Velo-Filter: applied` 헤더 추가

### `SampleLifecycleListener`

`ServletContextListener`와 `ServletRequestListener`를 동시에 구현하는 샘플 리스너.

| 이벤트 | 동작 |
|---|---|
| `contextInitialized` | `appLifecycle` 속성을 `"started"`로 설정 |
| `requestInitialized` | `requestCount` 속성 증가 |

## 테스트

`VeloWasServerIntegrationTest`에서 실제 서버 기동 후 HTTP 요청을 보내 전체 파이프라인을 통합 테스트한다.

## 의존성

- `was-config`, `was-protocol-http`, `was-transport-netty`, `was-servlet-core` (내부)
- `org.yaml:snakeyaml`
- `org.slf4j:slf4j-api`, `slf4j-simple` (runtime)
- `org.junit.jupiter:junit-jupiter` (test)

## 빌드

`maven-assembly-plugin`으로 `jar-with-dependencies` fat JAR를 생성한다. Main-Class는 `io.velo.was.bootstrap.VeloWasApplication`.
