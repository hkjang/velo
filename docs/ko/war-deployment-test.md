# WAR 배포 및 JSP 테스트 가이드

이 문서는 Velo WAS 프로젝트에서 `test-war` 및 `test-app.war`를 배포하고 Servlet 및 JSP 엔드포인트가 올바르게 동작하는지 확인하는 과정을 안내합니다.

## 시스템 요구 사항

- Java 21 이상
- Maven (빌드 용도)
- curl 유틸리티 (HTTP 테스트 용도)

## 1. 사전 준비 및 빌드

배포 및 핫 디플로이(Hot Deploy) 기능을 테스트하기 위해, 서버 환경 설정 파일(`conf/server.yaml`)에서 해당 기능을 활성화해야 합니다.

`conf/server.yaml` 파일을 열고 다음과 같이 `hotDeploy`를 `true`로 설정합니다:

```yaml
deploy:
  directory: deploy
  hotDeploy: true
  scanIntervalSeconds: 2
```

설정 변경 후 Velo WAS 전체를 빌드합니다.

```sh
# Velo WAS 전체 빌드
mvn clean package -DskipTests
```

## 2. 서버 실행

다음 명령어를 통해 Velo WAS 서버를 백그라운드나 새 터미널 창에서 실행합니다.

```sh
java -jar was-bootstrap/target/was-bootstrap-0.5.3-jar-with-dependencies.jar
```

서버 구동 로그에 `Hot deploy watcher started` 메시지가 표시되면 정상적으로 자동 배포를 모니터링 중인 상태입니다.

---

## 3. Servlet 프로젝트 배포 테스트 (`test.war`)

`test-war` 모듈은 기본적인 Servlet과 Filter 동작을 검증하기 위한 프로젝트입니다.

### 3.1. 패키징 및 배포

`test-war` 디렉토리로 이동하여 패키징 후 서버의 `deploy` 디렉토리로 복사합니다.

```sh
# test-war 빌드
cd test-war
mvn clean package

# 빌드 결과물을 Velo WAS의 deploy 디렉토리로 복사
cp target/test-war-0.5.3.war ../deploy/test.war
```

`hotDeploy`가 활성화되어 있으므로, 복사 즉시(약 2초 내) 서버 로그에 배포 완료 메시지가 나타납니다.

### 3.2. 엔드포인트 검증

배포가 완료되면 `curl` 명령어를 통해 서블릿 응답을 확인합니다.

```sh
$ curl -s http://localhost:8080/test/hello

Hello from TestServlet! User-Agent: curl/8.x.x
```

![TestServlet 응답 화면](../images/test_war_hello.png)

응답이 올바르게 출력된다면 `test.war`의 Servlet 매핑이 성공적으로 동작하는 것입니다.

---

## 4. JSP 프로젝트 배포 테스트 (`test-app`)

`test-app`은 JSP 파일 파싱, Java 변환 및 JspServlet 매핑을 검증하기 위한 프로젝트입니다.

### 4.1. 프로젝트 구조

```text
test-app/
├── index.jsp         # 기본 시작 페이지
├── info.jsp          # 시스템 정보를 보여주는 페이지 (<%@ page import="..." %> 포함)
└── WEB-INF/
    └── web.xml       # 매핑 정보
```

### 4.2. 패키징 및 배포

`test-app` 디렉토리에서 WAR 포맷으로 컴파일 후 배포합니다.

```sh
cd ../test-app

# 수동으로 WAR 파일 압축 (jar 명령어 활용)
jar -cvf ../deploy/test-app.war *
```

배포 완료 시 로딩 로그에 다음과 같이 출력됩니다.
> `INFO io.velo.was.deploy.WarDeployer - WAR deployed: name=test-app contextPath=/test-app source=deploy\test-app.war...`

### 4.3. JSP 엔드포인트 검증

정상적으로 배포되었다면, 다음 URL을 호출하여 JSP 파일이 동적으로 HTML을 렌더링하는 것을 확인합니다.

```sh
# 1. 메인 페이지 테스트
$ curl -s http://localhost:8080/test-app/index.jsp

<!DOCTYPE html>
<html>
<head>
    <title>Velo WAS - Test App</title>
</head>
<body>
    <h1>Welcome to Velo WAS Test Application</h1>
    <p>This page is rendered by the JSP Engine.</p>
...
</html>
```

![test-app 메인 페이지 화면](../images/test_app_index.png)

```sh
# 2. 서버 정보 페이지 테스트 (내부 JVM 객체 참조)
$ curl -s http://localhost:8080/test-app/info.jsp

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

위와 같이 `No servlet mapping` 에러나 `500 Server Error` 없이 HTML 본문과 정상적인 200 상태 코드가 반환되면 모든 JSP 컴파일, 트랜스레이션 및 클래스로더 연동이 완벽하게 성공한 것입니다.

---

## 5. TCP 리스너 테스트 (Port 9090)

Velo WAS는 웹 프로토콜 외에도 원원시 TCP 통신을 처리하기 위한 리스너를 지원합니다. `conf/server.yaml`에 정의된 `9090` 포트의 에코(Echo) 기능을 테스트합니다.

### 5.1. 테스트용 파이썬 스크립트 (`test_tcp_echo.py`)

TCP 리스너는 `LENGTH_FIELD` 프레이밍(4바이트 길이 헤더)을 사용하므로, 이를 준수하는 간단한 스크립트로 검증합니다.

```python
import socket
import struct

def test_echo(msg):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.connect(('127.0.0.1', 9090))
    
    # [4바이트 길이] + [데이터] 전송
    payload = msg.encode('utf-8')
    s.sendall(struct.pack('>I', len(payload)) + payload)
    
    # 응답 수신
    header = s.recv(4)
    resp_len = struct.unpack('>I', header)[0]
    data = s.recv(resp_len)
    print("Received: " + data.decode('utf-8'))
    s.close()

test_echo("Hello Velo TCP!")
```

### 5.2. 실행 및 결과

서버가 구동 중인 상태에서 위 스크립트를 실행하여 에코 응답을 확인합니다.

```sh
$ python test_tcp_echo.py
Received: Hello Velo TCP!
```

응답 데이터가 전송한 메시지와 일치한다면 TCP 레이어의 프레임 디코딩 및 라우팅이 정상적으로 동작하고 있는 것입니다.

### 4.4. 추가 API 엔드포인트 검증

`test-app`은 혼합 모드(JSP + 서블릿) 라우팅을 검증하기 위해 `WEB-INF/classes` 내부에 일반 서블릿들도 포함하고 있습니다. 해당 서블릿들이 정상 호출되는지 확인합니다.

```sh
# 1. Greeting 서블릿 테스트
$ curl -s "http://localhost:8080/test-app/greeting?name=Velo"

<!DOCTYPE html>
<html><head><title>Greeting</title></head>
<body>
<h1>Hello, Velo!</h1>
...
</html>
```

![Greeting 서블릿 화면](../images/test_app_greeting.png)

```sh
# 2. 상태 조회 API 테스트
$ curl -s http://localhost:8080/test-app/api/status

{
  "status": "running",
  "appName": "test-app",
  "uptimeMs": 5564648,
  "memory": { ... },
  "javaVersion": "21.0.10",
  "threads": 23
}
```

![상태 정보 JSON 응답 화면](../images/test_app_status.png)
