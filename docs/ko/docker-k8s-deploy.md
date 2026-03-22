# Velo WAS — Docker / Kubernetes 배포 가이드

## 개요

Velo WAS는 Docker 컨테이너와 Kubernetes 클러스터에서 실행할 수 있습니다.
단독 실행부터 멀티 노드 클러스터, 모니터링 스택까지 프로파일 기반으로 선택 가능합니다.

## 파일 구조

```
프로젝트 루트/
├── Dockerfile                    # 멀티 스테이지 빌드 (JDK 21 Alpine)
├── docker-compose.yml            # 4개 프로파일
├── .dockerignore
├── .env.example                  # 환경변수 템플릿
├── bin/
│   ├── docker-build.sh           # 이미지 빌드 스크립트
│   ├── docker-run.sh             # 실행 스크립트
│   └── k8s-deploy.sh             # K8s 배포 스크립트
└── deploy/
    ├── k8s/                      # Kubernetes 매니페스트
    ├── helm/velo-was/            # Helm Chart
    ├── nginx/nginx.conf          # 클러스터 로드밸런서
    └── prometheus/prometheus.yml # 메트릭 수집
```

---

## 1. Docker

### 1.1 이미지 빌드

```bash
# 기본 빌드
docker build -t velo-was .

# 버전 태그 지정
./bin/docker-build.sh 0.5.19

# 빌드 + 레지스트리 푸시
DOCKER_REGISTRY=myregistry.io ./bin/docker-build.sh 0.5.19 --push
```

빌드 과정:
1. **Stage 1 (builder)**: JDK 21 + Maven으로 소스 컴파일 및 fat JAR 생성
2. **Stage 2 (runtime)**: JRE 21 Alpine에 JAR + 설정 파일만 복사 (~150MB)

### 1.2 단독 실행

```bash
# 가장 간단한 실행
docker run -p 8080:8080 velo-was

# 환경변수 전달
docker run -p 8080:8080 \
  -e OPENAI_API_KEY=sk-xxx \
  -e JAVA_OPTS="-Xms512m -Xmx2g" \
  -v $(pwd)/conf:/opt/velo/conf:ro \
  -v velo-data:/opt/velo/data \
  velo-was
```

### 1.3 Docker Compose

환경변수 파일을 먼저 준비합니다:

```bash
cp .env.example .env
# .env 파일을 편집하여 API 키 등 설정
```

#### 실행 모드

| 모드 | 명령어 | 구성 |
|------|--------|------|
| **단독** | `docker compose up -d` | Velo WAS 1대 |
| **+ Redis** | `docker compose --profile redis up -d` | WAS + Redis (의도 라우팅 캐시) |
| **클러스터** | `docker compose --profile cluster up -d` | WAS 2대 + Nginx LB + Redis |
| **+ 모니터링** | `docker compose --profile monitoring up -d` | WAS + Prometheus + Grafana |
| **전체** | `docker compose --profile redis --profile cluster --profile monitoring up -d` | 모든 서비스 |

```bash
# 편의 스크립트 사용
./bin/docker-run.sh                # 단독
./bin/docker-run.sh --redis        # + Redis
./bin/docker-run.sh --cluster      # 클러스터
./bin/docker-run.sh --monitoring   # + 모니터링
./bin/docker-run.sh --full         # 전체

# 중지
./bin/docker-run.sh --stop
```

#### 접속 정보

| 서비스 | URL |
|--------|-----|
| AI Platform 콘솔 | http://localhost:8080/ai-platform |
| 개발자 포탈 | http://localhost:8080/ai-platform/api-docs/ui |
| OpenAI 호환 API | http://localhost:8080/ai-platform/v1/chat/completions |
| Prometheus | http://localhost:9090 (monitoring 프로파일) |
| Grafana | http://localhost:3000 (monitoring 프로파일, admin/admin) |
| Nginx LB | http://localhost:80 (cluster 프로파일) |

### 1.4 볼륨 및 데이터

| 볼륨 | 용도 |
|------|------|
| `velo-data` | 영속 데이터 (모델 레지스트리, 키워드 정책, 테넌트) |
| `velo-logs` | 로그 파일 |
| `redis-data` | Redis 영속 데이터 |
| `./conf` | 설정 파일 (읽기 전용 마운트) |

### 1.5 환경변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `JAVA_OPTS` | `-Xms256m -Xmx1g -XX:+UseZGC` | JVM 기본 옵션 |
| `VELO_JVM_OPTS` | (없음) | 추가 JVM 옵션 |
| `VELO_CONFIG` | `/opt/velo/conf/server.yaml` | 설정 파일 경로 |
| `OPENAI_API_KEY` | (없음) | OpenAI API 키 |
| `ANTHROPIC_API_KEY` | (없음) | Anthropic API 키 |
| `VELO_REDIS_URL` | (없음) | Redis 연결 URL |

### 1.6 헬스체크

Dockerfile에 내장된 헬스체크:

```
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3
    CMD wget -qO- http://localhost:8080/ai-platform/api/status || exit 1
```

---

## 2. Kubernetes

### 2.1 사전 준비

```bash
# 이미지 빌드 (K8s 노드에서 접근 가능한 레지스트리에 푸시)
docker build -t myregistry.io/velo-was:0.5.19 .
docker push myregistry.io/velo-was:0.5.19
```

### 2.2 kubectl 배포

```bash
# 전체 매니페스트 적용
./bin/k8s-deploy.sh

# 또는 수동으로
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/secret.yaml
kubectl apply -f deploy/k8s/pvc.yaml
kubectl apply -f deploy/k8s/deployment.yaml
kubectl apply -f deploy/k8s/service.yaml
kubectl apply -f deploy/k8s/hpa.yaml
kubectl apply -f deploy/k8s/ingress.yaml   # Ingress Controller 필요
```

### 2.3 매니페스트 구성

| 파일 | 리소스 | 설명 |
|------|--------|------|
| `namespace.yaml` | Namespace `velo` | 전용 네임스페이스 |
| `configmap.yaml` | ConfigMap | server.yaml 설정 |
| `secret.yaml` | Secret | API 키 (프로덕션에서는 Vault 등 사용 권장) |
| `pvc.yaml` | PVC 1Gi | 영속 데이터 (모델, 키워드, 테넌트) |
| `deployment.yaml` | Deployment (2 replicas) | RollingUpdate, 3종 프로브 |
| `service.yaml` | ClusterIP + NodePort:30080 | 내부/외부 접근 |
| `hpa.yaml` | HPA (2-10 pods) | CPU 70%, Memory 80% |
| `ingress.yaml` | Ingress | Nginx Controller, SSE 스트리밍 지원 |

### 2.4 프로브 설정

```yaml
# Startup Probe — 서버 시작 대기 (최대 60초)
startupProbe:
  httpGet: { path: /ai-platform/api/status, port: 8080 }
  initialDelaySeconds: 10
  periodSeconds: 5
  failureThreshold: 12

# Readiness Probe — 트래픽 수신 준비
readinessProbe:
  httpGet: { path: /ai-platform/api/status, port: 8080 }
  initialDelaySeconds: 15
  periodSeconds: 10

# Liveness Probe — 장애 감지 및 재시작
livenessProbe:
  httpGet: { path: /ai-platform/api/status, port: 8080 }
  initialDelaySeconds: 30
  periodSeconds: 15
```

### 2.5 리소스 제한

```yaml
resources:
  requests:
    cpu: 250m
    memory: 512Mi
  limits:
    cpu: "2"
    memory: 1536Mi
```

### 2.6 Secret 설정

프로덕션에서는 `secret.yaml`을 직접 사용하지 말고 외부 시크릿 관리 도구를 사용하세요:

```bash
# kubectl로 시크릿 생성
kubectl create secret generic velo-secrets \
  --namespace velo \
  --from-literal=OPENAI_API_KEY=sk-xxx \
  --from-literal=ANTHROPIC_API_KEY=sk-ant-xxx
```

### 2.7 상태 확인

```bash
./bin/k8s-deploy.sh --status

# 또는
kubectl get pods -n velo
kubectl get svc -n velo
kubectl get hpa -n velo
kubectl logs -n velo -l app.kubernetes.io/name=velo-was --tail=50
```

### 2.8 접속

```bash
# NodePort (개발/테스트)
http://<node-ip>:30080/ai-platform

# Ingress (프로덕션)
http://velo.local/ai-platform   # ingress.yaml의 host 수정 필요

# 포트 포워딩 (디버깅)
kubectl port-forward -n velo svc/velo-was 8080:8080
```

### 2.9 삭제

```bash
./bin/k8s-deploy.sh --delete
```

---

## 3. Helm Chart

### 3.1 설치

```bash
# 기본 설치
helm install velo-was deploy/helm/velo-was/ \
  --namespace velo --create-namespace

# 값 오버라이드
helm install velo-was deploy/helm/velo-was/ \
  --namespace velo --create-namespace \
  --set replicaCount=3 \
  --set image.repository=myregistry.io/velo-was \
  --set image.tag=0.5.19 \
  --set secrets.openaiApiKey=sk-xxx

# values 파일 사용
helm install velo-was deploy/helm/velo-was/ \
  --namespace velo --create-namespace \
  --values my-values.yaml
```

### 3.2 주요 설정값 (values.yaml)

```yaml
replicaCount: 2               # Pod 수
image:
  repository: velo-was         # 이미지 저장소
  tag: "0.5.19"                # 이미지 태그

jvm:
  opts: "-Xms512m -Xmx1g -XX:+UseZGC"

aiPlatform:
  enabled: true
  mode: PLATFORM
  contextPath: /ai-platform
  intentRouting:
    enabled: true
    analysisWindow: 8000       # 키워드 분석 윈도우 (문자 수)

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10

persistence:
  enabled: true
  size: 1Gi

redis:
  enabled: false               # true로 변경 시 Redis 사용
  url: "redis://redis:6379"
```

### 3.3 업그레이드 / 삭제

```bash
# 업그레이드
helm upgrade velo-was deploy/helm/velo-was/ \
  --namespace velo \
  --set image.tag=0.5.20

# 삭제
helm uninstall velo-was --namespace velo

# 편의 스크립트
./bin/k8s-deploy.sh --helm           # 설치/업그레이드
./bin/k8s-deploy.sh --helm-delete    # 삭제
```

---

## 4. 프로덕션 체크리스트

- [ ] `.env` 파일에 실제 API 키 설정
- [ ] `conf/server.yaml`에서 `webadmin.password` 변경
- [ ] Secret을 Vault/Sealed Secrets 등으로 관리
- [ ] Ingress에 TLS 인증서 설정
- [ ] PVC storageClass를 클러스터에 맞게 설정
- [ ] 리소스 limits를 워크로드에 맞게 조정
- [ ] HPA min/max replicas 조정
- [ ] 로그 수집 (Fluentd/Loki 등) 설정
- [ ] 모니터링 대시보드 (Grafana) 구성
- [ ] 백업 전략 수립 (PVC 데이터, Redis 데이터)
