# WAR 배포 및 JSP 테스트 가이드

이 문서는 Velo WAS 프로젝트에서 `test-war`와 `test-app.war`를 배포하고,
Servlet, JSP, TCP 엔드포인트가 올바르게 동작하는지 확인하는 과정을 설명한다.

## 시스템 요구 사항

- Java 21 이상
- Maven
- `curl` 유틸리티
- Python 3 (`test_tcp_echo.py` 실행 시)

## 1. 사전 준비 및 빌드

배포 및 핫 디플로이 기능을 테스트하려면 `conf/server.yaml`에서
`hotDeploy`를 활성화해야 한다.

```yaml
deploy:
  directory: deploy
  hotDeploy: true
  scanIntervalSeconds: 2
```

설정 변경 후 Velo WAS 전체를 빌드한다.

```sh
mvn clean package -DskipTests
```

## 2. 서버 실행

새 터미널에서 다음 명령으로 Velo WAS를 실행한다.

```sh
java -jar was-bootstrap/target/was-bootstrap-0.5.9-jar-with-dependencies.jar
```

서버 로그에 `Hot deploy watcher started` 메시지가 보이면
`deploy/` 디렉토리 자동 감시가 정상 동작 중인 상태다.

---

## 3. Servlet 프로젝트 배포 테스트 (`test.war`)

`test-war` 예시는 기본적인 Servlet 및 Filter 동작 검증을 위한 샘플 WAR를 기준으로 한다.

### 3.1 패키징 및 배포

`test-war` 디렉토리로 이동한 뒤 WAR를 준비하고 `deploy/` 디렉토리로 복사한다.

```sh
# 예시 WAR 준비
cd test-war
mvn clean package

# 배포 디렉토리로 복사
cp target/test-war-0.5.9.war ../deploy/test.war
```

`hotDeploy`가 활성화되어 있다면 복사 직후 약 2초 내에
서버 로그에 배포 완료 메시지가 출력된다.

### 3.2 엔드포인트 검증

배포가 완료되면 `curl`로 서블릿 응답을 확인한다.

```sh
curl -s http://localhost:8080/test/hello
```

예상 예시:

```text
Hello from TestServlet! User-Agent: curl/8.x.x
```

![TestServlet 응답 화면](../images/test_war_hello.png)

정상 응답이 나오면 `test.war`의 Servlet 매핑이 올바르게 동작하는 것이다.

---

## 4. JSP 프로젝트 배포 테스트 (`test-app`)

`test-app`은 JSP 파싱, Java 변환, `JspServlet` 매핑을 검증하기 위한 예제다.

### 4.1 프로젝트 구조

```text
test-app/
├── index.jsp         # 기본 시작 페이지
├── info.jsp          # 시스템 정보 페이지
└── WEB-INF/
    └── web.xml       # 매핑 정보
```

### 4.2 패키징 및 배포

`test-app` 디렉토리에서 WAR 파일을 만들어 배포한다.

```sh
cd ../test-app

# 수동 WAR 생성
jar -cvf ../deploy/test-app.war *
```

배포 완료 시 로그 예시:

```text
INFO io.velo.was.deploy.WarDeployer - WAR deployed: name=test-app contextPath=/test-app source=deploy\test-app.war...
```

### 4.3 JSP 엔드포인트 검증

배포가 완료되면 다음 URL로 JSP가 HTML로 렌더링되는지 확인한다.

```sh
# 메인 페이지
curl -s http://localhost:8080/test-app/index.jsp
```

응답 예시:

```html
<!DOCTYPE html>
<html>
  <head>
    <title>Velo WAS - Test App</title>
  </head>
  <body>
    <h1>Welcome to Velo WAS Test Application</h1>
    <p>This page is rendered by the JSP Engine.</p>
    ...
  </body>
</html>
```

![test-app 메인 페이지 화면](../images/test_app_index.png)

```sh
# 서버 정보 페이지
curl -s http://localhost:8080/test-app/info.jsp
```

응답 예시:

```html
<!DOCTYPE html>
<html>
...
    <table border="1" cellpadding="8" cellspacing="0">
        <tr><th>항목</th><th>값</th></tr>
        <tr><td>현재 시간</td><td>2026-03-15T11:03:34.840</td></tr>
...
</html>
```

![test-app 서버 정보 페이지 화면](../images/test_app_info.png)

`No servlet mapping`이나 `500 Server Error` 없이
정상적인 HTML과 `200 OK`가 반환되면 JSP 컴파일, 변환, 클래스 로딩이 정상이다.

### 4.4 추가 API 엔드포인트 검증

`test-app`은 JSP 외에도 일반 Servlet 라우팅을 함께 검증할 수 있는 예시 엔드포인트를 포함한다.

```sh
# Greeting Servlet 테스트
curl -s "http://localhost:8080/test-app/greeting?name=Velo"
```

응답 예시:

```html
<!DOCTYPE html>
<html>
  <head>
    <title>Greeting</title>
  </head>
  <body>
    <h1>Hello, Velo!</h1>
    ...
  </body>
</html>
```

![Greeting Servlet 화면](../images/test_app_greeting.png)

```sh
# 상태 조회 API 테스트
curl -s http://localhost:8080/test-app/api/status
```

응답 예시:

```json
{
  "status": "running",
  "appName": "test-app",
  "uptimeMs": 5564648,
  "memory": { "...": "..." },
  "javaVersion": "21.0.10",
  "threads": 23
}
```

![상태 정보 JSON 응답 화면](../images/test_app_status.png)

JSP와 일반 Servlet 엔드포인트가 모두 정상 응답하면
혼합 라우팅도 문제없이 동작하는 것으로 볼 수 있다.

---

## 5. TCP 리스너 테스트 (포트 9090)

Velo WAS는 HTTP 외에도 TCP 리스너를 지원한다.
`conf/server.yaml`에 정의된 `9090` 포트의 echo 동작을 확인한다.

### 5.1 테스트 스크립트 (`test_tcp_echo.py`)

TCP 리스너는 `LENGTH_FIELD` 프레이밍(4바이트 길이 헤더)을 사용하므로
다음과 같은 테스트 스크립트로 검증할 수 있다.

```python
import socket
import struct

def test_echo(msg):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.connect(('127.0.0.1', 9090))

    payload = msg.encode('utf-8')
    s.sendall(struct.pack('>I', len(payload)) + payload)

    header = s.recv(4)
    resp_len = struct.unpack('>I', header)[0]
    data = s.recv(resp_len)
    print("Received: " + data.decode('utf-8'))
    s.close()

test_echo("Hello Velo TCP!")
```

### 5.2 실행 및 결과

서버가 실행 중인 상태에서 스크립트를 실행한다.

```sh
python test_tcp_echo.py
```

응답 예시:

```text
Received: Hello Velo TCP!
```

보낸 메시지와 받은 메시지가 일치하면 TCP 프레임 디코딩과 라우팅이 정상이다.
