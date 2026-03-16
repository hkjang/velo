# 멀티 노드 구성 가이드

Velo WAS는 동일한 서버에서 여러 노드를 독립적으로 기동할 수 있습니다.
각 노드는 별도의 설정 파일을 사용하며, HTTP/TCP 포트를 다르게 지정하여 충돌을 방지합니다.

## 현재 노드 구성

| 노드 | 설정 파일 | HTTP 포트 | TCP 포트 | nodeId |
|------|-----------|-----------|----------|--------|
| node-1 | `conf/server.yaml` | 8080 | 9090 | node-1 |
| node-2 | `conf/server-node2.yaml` | 8180 | 9190 | node-2 |

## 새 노드 추가 방법

### 1단계: 설정 파일 복사

기존 설정 파일을 복사하여 새 노드용 설정 파일을 생성합니다.

```bash
cp conf/server.yaml conf/server-node3.yaml
```

### 2단계: 설정 파일 수정

새 설정 파일에서 다음 항목을 변경합니다.

```yaml
server:
  nodeId: node-3              # 고유한 노드 ID로 변경

  listener:
    port: 8280                # 다른 노드와 겹치지 않는 HTTP 포트

  jsp:
    scratchDir: work/jsp-node3  # 노드별 JSP 작업 디렉토리

  tcpListeners:
    - name: raw-tcp
      port: 9290              # 다른 노드와 겹치지 않는 TCP 포트
```

**필수 변경 항목:**

| 항목 | 설명 | 예시 (node-3) |
|------|------|---------------|
| `server.nodeId` | 클러스터 내 고유 식별자 | `node-3` |
| `server.listener.port` | HTTP 리스너 포트 | `8280` |
| `server.jsp.scratchDir` | JSP 컴파일 디렉토리 | `work/jsp-node3` |
| `server.tcpListeners[].port` | TCP 리스너 포트 | `9290` |

**포트 규칙 권장사항:**

| 노드 | HTTP 포트 | TCP 포트 |
|------|-----------|----------|
| node-1 | 8080 | 9090 |
| node-2 | 8180 | 9190 |
| node-3 | 8280 | 9290 |
| node-N | 8080 + (N-1)*100 | 9090 + (N-1)*100 |

### 3단계: 노드 기동

```bash
# node-1 기동 (기본 설정)
./bin/start.sh -d

# node-2 기동
./bin/start.sh -c conf/server-node2.yaml -d

# node-3 기동
./bin/start.sh -c conf/server-node3.yaml -d
```

각 노드는 설정 파일명을 기반으로 별도의 PID 파일과 로그 파일을 생성합니다.

| 설정 파일 | PID 파일 | 로그 파일 |
|-----------|----------|----------|
| `conf/server.yaml` | `velo-was.pid` | `logs/velo-was.out` |
| `conf/server-node2.yaml` | `velo-was-node2.pid` | `logs/velo-was-node2.out` |
| `conf/server-node3.yaml` | `velo-was-node3.pid` | `logs/velo-was-node3.out` |

### 4단계: 노드 확인

각 노드의 헬스체크 엔드포인트로 정상 기동을 확인합니다.

```bash
# node-1 확인
curl http://localhost:8080/health
# {"status":"UP","name":"velo-was","nodeId":"node-1"}

# node-2 확인
curl http://localhost:8180/health
# {"status":"UP","name":"velo-was","nodeId":"node-2"}
```

### 5단계: 노드 중지

```bash
# node-1 중지
./bin/stop.sh

# node-2 중지
./bin/stop.sh -c conf/server-node2.yaml

# node-3 중지
./bin/stop.sh -c conf/server-node3.yaml
```

## 주의 사항

- **포트 충돌**: 각 노드의 HTTP 포트와 TCP 포트가 겹치지 않도록 설정해야 합니다.
- **JSP 작업 디렉토리**: 노드별로 별도의 `scratchDir`을 지정하여 JSP 컴파일 충돌을 방지합니다.
- **배포 디렉토리**: 기본적으로 모든 노드가 동일한 `deploy/` 디렉토리를 공유합니다. 노드별 독립 배포가 필요한 경우 `server.deploy.directory`를 변경하세요.
- **세션 비공유**: 현재 세션은 인메모리 방식으로 노드 간 공유되지 않습니다. 로드밸런서 사용 시 sticky session 설정이 필요합니다.
- **로그 분리**: 각 노드는 별도의 로그 파일(`logs/velo-was-nodeN.out`)에 출력됩니다.
