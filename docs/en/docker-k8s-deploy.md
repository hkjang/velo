# Velo WAS — Docker / Kubernetes Deployment Guide

## Overview

Velo WAS can run as a Docker container and on Kubernetes clusters.
Profile-based Docker Compose supports standalone to multi-node cluster with monitoring.

## File Structure

```
project-root/
├── Dockerfile                    # Multi-stage build (JDK 21 Alpine)
├── docker-compose.yml            # 4 profiles
├── .dockerignore
├── .env.example                  # Environment variable template
├── bin/
│   ├── docker-build.sh           # Image build script
│   ├── docker-run.sh             # Run script
│   └── k8s-deploy.sh             # K8s deploy script
└── deploy/
    ├── k8s/                      # Kubernetes manifests
    ├── helm/velo-was/            # Helm Chart
    ├── nginx/nginx.conf          # Cluster load balancer
    └── prometheus/prometheus.yml # Metrics collection
```

---

## 1. Docker

### 1.1 Building the Image

```bash
# Default build
docker build -t velo-was .

# With version tag
./bin/docker-build.sh 0.5.19

# Build + push to registry
DOCKER_REGISTRY=myregistry.io ./bin/docker-build.sh 0.5.19 --push
```

Build stages:
1. **Stage 1 (builder)**: JDK 21 + Maven to compile and create fat JAR
2. **Stage 2 (runtime)**: JRE 21 Alpine with JAR + config only (~150MB)

### 1.2 Standalone Run

```bash
# Simplest run
docker run -p 8080:8080 velo-was

# With environment variables
docker run -p 8080:8080 \
  -e OPENAI_API_KEY=sk-xxx \
  -e JAVA_OPTS="-Xms512m -Xmx2g" \
  -v $(pwd)/conf:/opt/velo/conf:ro \
  -v velo-data:/opt/velo/data \
  velo-was
```

### 1.3 Docker Compose

Prepare the environment file first:

```bash
cp .env.example .env
# Edit .env with your API keys and settings
```

#### Run Modes

| Mode | Command | Services |
|------|---------|----------|
| **Standalone** | `docker compose up -d` | Velo WAS only |
| **+ Redis** | `docker compose --profile redis up -d` | WAS + Redis (intent routing cache) |
| **Cluster** | `docker compose --profile cluster up -d` | 2 WAS nodes + Nginx LB + Redis |
| **+ Monitoring** | `docker compose --profile monitoring up -d` | WAS + Prometheus + Grafana |
| **Full stack** | `docker compose --profile redis --profile cluster --profile monitoring up -d` | All services |

```bash
# Convenience scripts
./bin/docker-run.sh                # Standalone
./bin/docker-run.sh --redis        # + Redis
./bin/docker-run.sh --cluster      # Cluster
./bin/docker-run.sh --monitoring   # + Monitoring
./bin/docker-run.sh --full         # Full stack

# Stop
./bin/docker-run.sh --stop
```

#### Access URLs

| Service | URL |
|---------|-----|
| AI Platform Console | http://localhost:8080/ai-platform |
| Developer Portal | http://localhost:8080/ai-platform/api-docs/ui |
| OpenAI-compatible API | http://localhost:8080/ai-platform/v1/chat/completions |
| Prometheus | http://localhost:9090 (monitoring profile) |
| Grafana | http://localhost:3000 (monitoring profile, admin/admin) |
| Nginx LB | http://localhost:80 (cluster profile) |

### 1.4 Volumes

| Volume | Purpose |
|--------|---------|
| `velo-data` | Persistent data (model registry, keyword policies, tenants) |
| `velo-logs` | Log files |
| `redis-data` | Redis persistent data |
| `./conf` | Config files (read-only mount) |

### 1.5 Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | `-Xms256m -Xmx1g -XX:+UseZGC` | JVM base options |
| `VELO_JVM_OPTS` | (none) | Additional JVM options |
| `VELO_CONFIG` | `/opt/velo/conf/server.yaml` | Config file path |
| `OPENAI_API_KEY` | (none) | OpenAI API key |
| `ANTHROPIC_API_KEY` | (none) | Anthropic API key |
| `VELO_REDIS_URL` | (none) | Redis connection URL |

### 1.6 Health Check

Built-in health check in Dockerfile:

```
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3
    CMD wget -qO- http://localhost:8080/ai-platform/api/status || exit 1
```

---

## 2. Kubernetes

### 2.1 Prerequisites

```bash
# Build image and push to a registry accessible by K8s nodes
docker build -t myregistry.io/velo-was:0.5.19 .
docker push myregistry.io/velo-was:0.5.19
```

### 2.2 kubectl Deployment

```bash
# Apply all manifests
./bin/k8s-deploy.sh

# Or manually
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/secret.yaml
kubectl apply -f deploy/k8s/pvc.yaml
kubectl apply -f deploy/k8s/deployment.yaml
kubectl apply -f deploy/k8s/service.yaml
kubectl apply -f deploy/k8s/hpa.yaml
kubectl apply -f deploy/k8s/ingress.yaml   # Requires Ingress Controller
```

### 2.3 Manifests

| File | Resource | Description |
|------|----------|-------------|
| `namespace.yaml` | Namespace `velo` | Dedicated namespace |
| `configmap.yaml` | ConfigMap | server.yaml configuration |
| `secret.yaml` | Secret | API keys (use Vault in production) |
| `pvc.yaml` | PVC 1Gi | Persistent data |
| `deployment.yaml` | Deployment (2 replicas) | RollingUpdate, 3 probe types |
| `service.yaml` | ClusterIP + NodePort:30080 | Internal/external access |
| `hpa.yaml` | HPA (2-10 pods) | CPU 70%, Memory 80% targets |
| `ingress.yaml` | Ingress | Nginx controller, SSE streaming support |

### 2.4 Probes

The deployment configures three types of probes:

- **Startup Probe**: Waits for server initialization (up to 60s)
- **Readiness Probe**: Controls traffic routing to the pod
- **Liveness Probe**: Detects failures and triggers restart

### 2.5 Resource Limits

```yaml
resources:
  requests:
    cpu: 250m
    memory: 512Mi
  limits:
    cpu: "2"
    memory: 1536Mi
```

### 2.6 Secrets

For production, use external secret management instead of `secret.yaml`:

```bash
kubectl create secret generic velo-secrets \
  --namespace velo \
  --from-literal=OPENAI_API_KEY=sk-xxx \
  --from-literal=ANTHROPIC_API_KEY=sk-ant-xxx
```

### 2.7 Checking Status

```bash
./bin/k8s-deploy.sh --status

# Or
kubectl get pods -n velo
kubectl get svc -n velo
kubectl get hpa -n velo
kubectl logs -n velo -l app.kubernetes.io/name=velo-was --tail=50
```

### 2.8 Access

```bash
# NodePort (dev/test)
http://<node-ip>:30080/ai-platform

# Ingress (production)
http://velo.local/ai-platform   # Update host in ingress.yaml

# Port forwarding (debugging)
kubectl port-forward -n velo svc/velo-was 8080:8080
```

### 2.9 Deletion

```bash
./bin/k8s-deploy.sh --delete
```

---

## 3. Helm Chart

### 3.1 Installation

```bash
# Default install
helm install velo-was deploy/helm/velo-was/ \
  --namespace velo --create-namespace

# With overrides
helm install velo-was deploy/helm/velo-was/ \
  --namespace velo --create-namespace \
  --set replicaCount=3 \
  --set image.repository=myregistry.io/velo-was \
  --set image.tag=0.5.19 \
  --set secrets.openaiApiKey=sk-xxx

# Using a values file
helm install velo-was deploy/helm/velo-was/ \
  --namespace velo --create-namespace \
  --values my-values.yaml
```

### 3.2 Key Configuration (values.yaml)

```yaml
replicaCount: 2               # Number of pods
image:
  repository: velo-was         # Image repository
  tag: "0.5.19"                # Image tag

jvm:
  opts: "-Xms512m -Xmx1g -XX:+UseZGC"

aiPlatform:
  enabled: true
  mode: PLATFORM
  contextPath: /ai-platform
  intentRouting:
    enabled: true
    analysisWindow: 8000       # Keyword analysis window (characters)

autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 10

persistence:
  enabled: true
  size: 1Gi

redis:
  enabled: false               # Set true to enable Redis
  url: "redis://redis:6379"
```

### 3.3 Upgrade / Uninstall

```bash
# Upgrade
helm upgrade velo-was deploy/helm/velo-was/ \
  --namespace velo \
  --set image.tag=0.5.20

# Uninstall
helm uninstall velo-was --namespace velo

# Convenience scripts
./bin/k8s-deploy.sh --helm           # Install/upgrade
./bin/k8s-deploy.sh --helm-delete    # Uninstall
```

---

## 4. Production Checklist

- [ ] Set actual API keys in `.env`
- [ ] Change `webadmin.password` in `conf/server.yaml`
- [ ] Manage secrets via Vault / Sealed Secrets
- [ ] Configure TLS certificates on Ingress
- [ ] Set PVC storageClass for your cluster
- [ ] Adjust resource limits for your workload
- [ ] Tune HPA min/max replicas
- [ ] Set up log collection (Fluentd / Loki)
- [ ] Configure monitoring dashboards (Grafana)
- [ ] Establish backup strategy (PVC data, Redis data)
