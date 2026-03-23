# was-ai-platform

Standalone AI platform module for Velo WAS. Transforms a lightweight WAS into a fully-featured AI serving platform with multi-LLM routing, intent-based routing, OpenAI-compatible proxy, tenant management, billing, and observability.

## Architecture

```
+----------------------------------------------------------+
|                  Velo AI Platform                         |
|                                                          |
|  +--------------------+    +-------------------------+   |
|  |  Dashboard Console |    |  Developer Portal       |   |
|  |  (HTML UI)         |    |  (OpenAPI + Swagger UI) |   |
|  +--------------------+    +-------------------------+   |
|                                                          |
|  +----------------------------------------------------+  |
|  |              Control Plane API (/api/*)             |  |
|  |  models | tenants | billing | usage | intent-routing |  |
|  +----------------------------------------------------+  |
|                                                          |
|  +----------------------------------------------------+  |
|  |           AI Gateway (/gateway/*)                   |  |
|  |  route | infer | stream | intent-route               |  |
|  +----------------------------------------------------+  |
|                                                          |
|  +----------------------------------------------------+  |
|  |     OpenAI-Compatible Proxy (/v1/*)                 |  |
|  |  /v1/chat/completions | /v1/models                  |  |
|  +----------------------------------------------------+  |
|                                                          |
|  +----------------------------------------------------+  |
|  |         Published APIs (/invoke/*)                  |  |
|  |  Auto-generated per-model REST endpoints            |  |
|  +----------------------------------------------------+  |
|                                                          |
|  +-----------+ +-----------+ +-----------+ +-----------+ |
|  |  Model    | |  Tenant   | | Billing   | |  Intent   | |
|  |  Registry | |  Service  | | Service   | |  Routing  | |
|  +-----------+ +-----------+ +-----------+ +-----------+ |
|                                                          |
|  +----------------------------------------------------+  |
|  |              Observability & Usage Metering          |  |
|  +----------------------------------------------------+  |
+----------------------------------------------------------+
```

## Core Features

### Multi-LLM Integration
- **Unified API**: OpenAI, Anthropic, vLLM, SGLang, Ollama models callable through a single gateway interface
- **OpenAI-Compatible Proxy**: Drop-in `/v1/chat/completions` endpoint — use any OpenAI SDK client
- **Prompt Routing**: Automatic request type detection (CHAT, VISION, RECOMMENDATION) from prompt content
- **Provider Abstraction**: Category-based routing (LLM, CV, RECOMMENDER) maps to configured model profiles

### Intelligent Routing
- **Policy-Based Routing**: Request type to model mapping via configurable route policies
- **Auto Model Selection**: Strategy-based selection — LATENCY_FIRST, ACCURACY_FIRST, COST_OPTIMIZED, BALANCED
- **A/B Testing**: When enabled, traffic splits 50/50 between top candidates to compare model performance
- **Ensemble Serving**: Run inference on multiple models simultaneously, select best result for improved accuracy
- **Failover**: Automatic model fallback when the primary provider fails
- **Load Balancing**: Traffic distribution across multiple model endpoints via routing strategy

### Gateway Features
- **Streaming Response**: Server-sent events (SSE) for token-by-token delivery
- **Context Cache**: Session-aware caching with configurable TTL to avoid redundant routing
- **Cost Tracking**: Per-request and per-model cost estimation with billing snapshots
- **Rate Limiting**: Per-tenant request-per-minute limits with sliding window enforcement
- **Token Quotas**: Per-tenant token budget enforcement across all gateway operations

### Platform Services
- **Model Registry**: Register, version, promote (ACTIVE/CANARY), and retire model profiles at runtime
- **Tenant Management**: Multi-tenant isolation with API key auth, rate limits, token quotas, and usage tracking
- **Billing**: Category-based pricing with per-model cost breakdown and metered request accounting
- **Intent-Based Routing**: Keyword extraction → intent detection → policy-based model selection with audit logging
- **Published APIs**: Auto-generated per-model `/invoke/{model}` REST endpoints from the active registry
- **Developer Portal**: Generated OpenAPI 3.0.3 spec with interactive docs UI
- **Plugin Framework**: Extensible pre/post processing pipeline with built-in content filter

### Observability
- Control plane, route, inference, and stream call counters
- Per-model request distribution and resolved type breakdown
- Cache hit rates and context cache size tracking
- Registry mutation audit (register, status changes)
- A/B test group distribution metrics (Group A / Group B)
- Failover and ensemble invocation counters

### Security
- Session-based authentication with CSRF protection for the admin console
- API key-based authentication for gateway and published API endpoints
- Bearer token support (`Authorization: Bearer <token>`)
- Security headers: `X-Content-Type-Options`, `X-Frame-Options`, `Cache-Control`, `Referrer-Policy`
- Tenant-scoped rate limiting and quota enforcement

## Default Paths

| Path | Description |
|------|-------------|
| `/ai-platform` | Dashboard console (authenticated) |
| `/ai-platform/login` | Login page |
| `/ai-platform/api/status` | Health check (public) |
| `/ai-platform/api/overview` | Platform overview (authenticated) |
| `/ai-platform/api/models` | Model registry CRUD (authenticated) |
| `/ai-platform/api/tenants` | Tenant management (authenticated) |
| `/ai-platform/api/billing` | Billing snapshot (authenticated) |
| `/ai-platform/api/usage` | Usage metering (authenticated) |
| `/ai-platform/api/fine-tuning/jobs` | Fine-tuning jobs (authenticated) |
| `/ai-platform/api/published-apis` | Published endpoint inventory (authenticated) |
| `/ai-platform/gateway/*` | AI gateway — route, infer, stream (public, API key required when multi-tenant) |
| `/ai-platform/invoke/*` | Published model endpoints (public, API key required when multi-tenant) |
| `/ai-platform/v1/chat/completions` | OpenAI-compatible chat proxy (public, API key required when multi-tenant) |
| `/ai-platform/v1/completions` | OpenAI-compatible text completion proxy (public, API key required when multi-tenant) |
| `/ai-platform/v1/models` | List available models (public) |
| `/ai-platform/gateway/ensemble` | Ensemble serving — multi-model inference (public, API key required when multi-tenant) |
| `/ai-platform/api/plugins` | Plugin registry (authenticated) |
| `/ai-platform/api-docs` | OpenAPI JSON spec |
| `/ai-platform/api-docs/ui` | Developer portal UI |

## OpenAI-Compatible Proxy

The `/v1/chat/completions` endpoint accepts standard OpenAI SDK requests and routes them through the AI gateway:

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:8080/ai-platform/v1",
    api_key="velo-demo-key"
)

response = client.chat.completions.create(
    model="llm-general",
    messages=[{"role": "user", "content": "Hello"}]
)
```

```bash
curl -X POST http://localhost:8080/ai-platform/v1/chat/completions \
  -H "Authorization: Bearer velo-demo-key" \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"Hello"}]}'
```

Features:
- Standard `choices[].message.content` response format
- Streaming support via `"stream": true` (returns SSE chunks)
- `velo_routing` extension in response with routing decision details
- Automatic failover on provider failure
- Token usage tracking in OpenAI format

## Gateway API

### Route Decision
```bash
curl -X POST /ai-platform/gateway/route \
  -H "Content-Type: application/json" \
  -d '{"requestType":"AUTO","prompt":"analyze this image","sessionId":"demo"}'
```

### Inference
```bash
curl -X POST /ai-platform/gateway/infer \
  -H "Content-Type: application/json" \
  -d '{"requestType":"CHAT","prompt":"Hello","sessionId":"demo"}'
```

### Streaming
```bash
curl /ai-platform/gateway/stream?requestType=AUTO&prompt=Hello&sessionId=demo
```

### Ensemble Serving
```bash
curl -X POST /ai-platform/gateway/ensemble \
  -H "Content-Type: application/json" \
  -d '{"requestType":"CHAT","prompt":"Summarize the report","sessionId":"demo"}'
```

Returns multiple candidate results with the best one selected by confidence score.

## Plugin Framework

The plugin framework provides extensible pre/post processing for inference requests.

### Built-in Plugins
- **Content Filter** (`content-filter`): Normalizes prompts by trimming whitespace and tracking original/normalized lengths.

### Plugin API
```bash
# List registered plugins
curl /ai-platform/api/plugins
```

### Custom Plugin Implementation
```java
public class MyPlugin implements AiPlugin {
    public String id() { return "my-plugin"; }
    public String name() { return "My Plugin"; }
    public String type() { return "preprocessor"; }

    public AiPluginContext preProcess(AiPluginContext context) {
        // Modify prompt, request type, or add metadata
        context.setAttribute("myFlag", true);
        return context;
    }
}
```

## Tenant Management

### Register a Tenant
```bash
curl -X POST /ai-platform/api/tenants \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"tenant-a","displayName":"Tenant A","plan":"pro","rateLimitPerMinute":200,"tokenQuota":500000}'
```

### Issue an API Key
```bash
curl -X POST /ai-platform/api/tenants/tenant-a/keys?label=production
```

### Use the API Key
```bash
curl -X POST /ai-platform/gateway/infer \
  -H "X-AI-API-Key: vtk_tenant-a_abc123" \
  -H "Content-Type: application/json" \
  -d '{"requestType":"CHAT","prompt":"Hello"}'
```

Rate limit and quota headers are returned in responses:
- `X-AI-Tenant` — Tenant ID
- `X-AI-Plan` — Subscription plan
- `X-RateLimit-Limit` — Requests per minute
- `X-RateLimit-Remaining` — Remaining requests in current window
- `X-Token-Quota-Remaining` — Remaining token budget

## Model Registry

### Register a Model Version
```bash
curl -X POST /ai-platform/api/models \
  -H "Content-Type: application/json" \
  -d '{"name":"llm-general","category":"LLM","provider":"openai","version":"v2","latencyMs":180,"accuracyScore":92,"status":"CANARY"}'
```

### Promote a Version
```bash
curl -X POST /ai-platform/api/models/llm-general/versions/v2/status?state=ACTIVE
```

Version lifecycle: `ACTIVE` → `CANARY` → `INACTIVE` → `DEPRECATED`

## Fine-Tuning

### Create a Job
```bash
curl -X POST /ai-platform/api/fine-tuning/jobs \
  -H "Content-Type: application/json" \
  -d '{"baseModel":"llm-general","datasetUri":"s3://data/train.jsonl","tenant":"tenant-a","objective":"support","epochs":3}'
```

Jobs progress through QUEUED → RUNNING → SUCCEEDED and auto-register the tuned model in the registry as a CANARY version.

## Configuration

All features are driven by `server.aiPlatform` in `velo.yml`:

```yaml
server:
  aiPlatform:
    enabled: true
    mode: platform
    console:
      enabled: true
      contextPath: /ai-platform
    serving:
      modelRouterEnabled: true
      abTestingEnabled: true
      autoModelSelectionEnabled: true
      ensembleServingEnabled: false
      edgeAiEnabled: false
      defaultStrategy: BALANCED
      routerTimeoutMillis: 500
      targetP99LatencyMs: 300
    platform:
      modelRegistrationEnabled: true
      autoApiGenerationEnabled: true
      versionManagementEnabled: true
      billingEnabled: true
      developerPortalEnabled: true
      multiTenantEnabled: true
      versioningStrategy: canary
    differentiation:
      aiOptimizedWasEnabled: true
      requestRoutingEnabled: true
      streamingResponseEnabled: true
      pluginFrameworkEnabled: true
      runtimeEngine: netty
    advanced:
      promptRoutingEnabled: true
      promptRoutingMode: keyword
      contextCacheEnabled: true
      contextCacheTtlSeconds: 300
      aiGatewayEnabled: true
      fineTuningApiEnabled: true
      observabilityEnabled: true
      gpuSchedulingEnabled: false
```

## Multimodal Support (vLLM / Image / Audio)

AI 게이트웨이는 텍스트뿐 아니라 이미지, 오디오 등 멀티모달 요청을 지원합니다.

### 지원 모달리티

| Modality | 요청 타입 | 카테고리 | 프로바이더 API | 설명 |
|----------|-----------|----------|----------------|------|
| `text` | CHAT | LLM | `/v1/chat/completions` | 일반 텍스트 대화 |
| `vision` | VISION | CV | `/v1/chat/completions` (image_url) | 이미지 분석, OCR |
| `image_gen` | IMAGE_GENERATION | IMAGE_GEN | `/v1/images/generations` | 이미지 생성 (DALL-E, SD) |
| `stt` | STT | AUDIO | `/v1/audio/transcriptions` | 음성 인식 (Whisper) |
| `tts` | TTS | AUDIO | `/v1/audio/speech` | 음성 합성 |
| `embedding` | EMBEDDING | EMBEDDING | `/v1/embeddings` | 벡터 임베딩 |

### Vision 요청 (이미지 분석)

```bash
curl -X POST /ai-platform/gateway/infer \
  -H "Content-Type: application/json" \
  -d '{
    "requestType": "VISION",
    "prompt": "이 이미지를 분석해주세요",
    "imageUrl": "https://example.com/image.jpg",
    "modality": "vision",
    "sessionId": "demo"
  }'
```

OpenAI SDK (vLLM vision 모델):
```python
response = client.chat.completions.create(
    model="llava-v1.5-7b",
    messages=[{
        "role": "user",
        "content": [
            {"type": "text", "text": "이 이미지를 설명해주세요"},
            {"type": "image_url", "image_url": {"url": "data:image/png;base64,..."}}
        ]
    }]
)
```

### 이미지 생성

```bash
curl -X POST /ai-platform/gateway/infer \
  -H "Content-Type: application/json" \
  -d '{
    "requestType": "IMAGE_GENERATION",
    "prompt": "A futuristic cityscape at sunset",
    "modality": "image_gen",
    "sessionId": "demo"
  }'
```

### 음성 인식 (STT / Whisper)

```bash
curl -X POST /ai-platform/gateway/infer \
  -H "Content-Type: application/json" \
  -d '{
    "requestType": "STT",
    "audioData": "<base64-encoded-audio>",
    "modality": "stt",
    "sessionId": "demo"
  }'
```

### 음성 합성 (TTS)

```bash
curl -X POST /ai-platform/gateway/infer \
  -H "Content-Type: application/json" \
  -d '{
    "requestType": "TTS",
    "prompt": "안녕하세요, Velo AI 플랫폼입니다.",
    "modality": "tts",
    "sessionId": "demo"
  }'
```

### 임베딩

```bash
curl -X POST /ai-platform/gateway/infer \
  -H "Content-Type: application/json" \
  -d '{
    "requestType": "EMBEDDING",
    "prompt": "텍스트를 벡터로 변환합니다",
    "modality": "embedding",
    "sessionId": "demo"
  }'
```

### vLLM / SGLang 연동

vLLM 및 SGLang은 OpenAI 호환 API를 제공하므로, `OpenAiProviderAdapter`를 사용하여 연동합니다:

```yaml
server:
  aiPlatform:
    serving:
      models:
        - name: vllm-llama
          category: LLM
          provider: vllm
          version: v1
          latencyMs: 200
          accuracyScore: 88
          enabled: true
        - name: vllm-llava
          category: CV
          provider: vllm
          version: v1
          latencyMs: 500
          accuracyScore: 82
          enabled: true
        - name: vllm-whisper
          category: AUDIO
          provider: vllm
          version: v1
          latencyMs: 300
          accuracyScore: 90
          enabled: true
```

프로바이더 등록 (Java):
```java
providerRegistry.register(new OpenAiProviderAdapter(
    "vllm", "vLLM Local", "http://localhost:8000", "token-xxx"));
```

## Gateway Audit (게이트웨이 감사)

게이트웨이를 통과하는 모든 요청에 대해 프롬프트, 모델 선택, 응답 시간, 토큰 사용량, 모달리티를 감사 기록합니다.

### 감사 데이터 흐름

```
요청 → 서블릿(감사 기록) → AiGatewayAuditLog
                              ├→ 인메모리 버퍼 (10,000건, API 조회용)
                              ├→ SLF4J AI_GATEWAY_AUDIT 로거 (JSON Lines)
                              └→ logs/ai-gateway-audit.log (일별 로테이션 파일)
```

### 감사 로그 조회 API

```bash
# 전체 감사 로그 조회 (최근 50건)
curl /ai-platform/api/gateway-audit

# 필터 조회
curl "/ai-platform/api/gateway-audit?limit=20&endpoint=v1/chat/completions&modality=vision"

# 감사 통계
curl /ai-platform/api/gateway-audit/stats
```

**필터 파라미터:**

| 파라미터 | 설명 |
|----------|------|
| `limit` | 최대 반환 건수 (기본 50) |
| `endpoint` | 엔드포인트 필터 (예: `v1/chat/completions`, `gateway/infer`) |
| `tenantId` | 테넌트 ID 필터 |
| `modelName` | 모델명 필터 |
| `modality` | 모달리티 필터 (text, vision, image_gen, stt, tts, embedding) |

### 감사 엔트리 JSON 포맷

```json
{
  "timestamp": "2026-03-23T10:30:00Z",
  "requestId": "ai-a1b2c3d4",
  "tenantId": "tenant-a",
  "endpoint": "v1/chat/completions",
  "modelName": "llm-general",
  "provider": "openai",
  "requestType": "CHAT",
  "prompt": "사용자 프롬프트 (최대 1000자)...",
  "durationMs": 245,
  "estimatedTokens": 128,
  "success": true,
  "streaming": false,
  "errorMsg": null,
  "remoteAddr": "127.0.0.1",
  "routePolicy": "default-chat",
  "intentType": "GENERATION",
  "modality": "text"
}
```

### 파일 기반 감사 로그

일별 로테이션 JSON Lines 파일로 영속화됩니다:
- 당일: `logs/ai-gateway-audit.log`
- 로테이션: `logs/ai-gateway-audit.2026-03-23.log`

Fluentd, Filebeat, Promtail 등 로그 수집 도구와 연동 가능합니다.

### SLF4J 감사 로거

`AI_GATEWAY_AUDIT` 이름의 전용 SLF4J 로거를 통해 JSON Lines 출력이 가능합니다:

```xml
<logger name="AI_GATEWAY_AUDIT" level="INFO" additivity="false">
  <appender-ref ref="AI_AUDIT_FILE" />
</logger>
```

### 대시보드

AI 플랫폼 대시보드의 **게이트웨이 감사** 탭에서 실시간 감사 로그를 조회할 수 있습니다:
- 통계 메트릭 (총 요청, 성공률, 평균 응답시간, 토큰 합계)
- 엔드포인트/테넌트/모델/모달리티 필터
- 감사 로그 테이블 (프롬프트 미리보기 포함)

## ACP (Agent Communication Protocol)

에이전트 간 태스크 기반 비동기 통신 프로토콜. 멀티모달 메시징, 태스크 상태 머신, 아티팩트 교환을 지원한다.

### 태스크 생명주기

```
SUBMITTED → WORKING → COMPLETED
                   ↘ FAILED
                   ↘ CANCELED
                   ↘ INPUT_REQUIRED → (입력 제공) → WORKING
```

### 태스크 생성

```bash
curl -X POST /ai-platform/acp/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "toAgent": "agent-abc12345",
    "capability": "translation",
    "text": "이 문서를 영어로 번역해주세요."
  }'
```

`toAgent`를 생략하면 `capability` 또는 텍스트 의도로 자동 라우팅된다.

### 태스크 조회/취소

```bash
# 태스크 상세 조회
curl /ai-platform/acp/tasks/task-abc123def456

# 태스크 목록 (필터 지원)
curl "/ai-platform/acp/tasks?agentId=agent-a&state=WORKING&limit=20"

# 태스크 취소
curl -X POST /ai-platform/acp/tasks/task-abc123def456/cancel

# 태스크 완료 (에이전트 측에서 호출)
curl -X POST /ai-platform/acp/tasks/task-abc123def456/complete \
  -d '{"text": "번역 결과입니다."}'
```

### 에이전트 간 직접 메시지

```bash
curl -X POST /ai-platform/acp/messages \
  -H "Content-Type: application/json" \
  -d '{
    "fromAgent": "agent-a",
    "toAgent": "agent-b",
    "text": "작업 진행 상황을 알려주세요."
  }'
```

### 에이전트 카드 (Agent Card)

```bash
# 이 플랫폼의 에이전트 카드 (표준 경로)
curl /ai-platform/.well-known/agent-card.json
```

에이전트 카드 JSON 포맷:
```json
{
  "agentId": "velo-ai-platform",
  "name": "Velo AI Platform",
  "capabilities": ["chat", "vision", "image-generation", "speech-to-text", "text-to-speech", "embedding"],
  "modalities": ["text", "image", "audio"],
  "protocols": ["acp", "agp", "mcp", "openai"],
  "endpoint": "http://localhost:8080/ai-platform"
}
```

## A2A (Agent-to-Agent Protocol)

Google A2A 표준 JSON-RPC 2.0 프로토콜로 에이전트 간 직접 협업을 지원한다.

### A2A 엔드포인트

- **POST** `/a2a` — JSON-RPC 2.0 요청 디스패치
- **GET** `/a2a?taskId={id}` — SSE 스트리밍 (태스크 상태 업데이트)
- **GET** `/.well-known/agent.json` — 에이전트 카드 (A2A 표준 경로)

### 지원 메서드

| JSON-RPC Method | 설명 |
|----------------|------|
| `tasks/send` | 태스크 전송 (동기 응답) |
| `tasks/sendSubscribe` | 태스크 전송 + SSE 구독 URL |
| `tasks/get` | 태스크 상태 조회 |
| `tasks/cancel` | 태스크 취소 |
| `message/send` | 에이전트 간 직접 메시지 |
| `agent/authenticatedExtendedCard` | 에이전트 카드 조회 |

### JSON-RPC 2.0 요청 예제

```bash
# 태스크 전송
curl -X POST /ai-platform/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "tasks/send",
    "params": {
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "이 문서를 번역해주세요."}]
      },
      "capability": "translation"
    },
    "id": 1
  }'

# 태스크 조회
curl -X POST /ai-platform/a2a \
  -d '{"jsonrpc":"2.0","method":"tasks/get","params":{"taskId":"task-abc123"},"id":2}'

# 에이전트 간 메시지
curl -X POST /ai-platform/a2a \
  -d '{
    "jsonrpc": "2.0",
    "method": "message/send",
    "params": {
      "fromAgent": "agent-a",
      "toAgent": "agent-b",
      "message": {"role": "agent", "parts": [{"type": "text", "text": "작업 완료했습니다."}]}
    },
    "id": 3
  }'
```

### 프로토콜 통합 현황

| 프로토콜 | 역할 | 엔드포인트 |
|----------|------|------------|
| **A2A** | 에이전트 간 협업 (JSON-RPC 2.0) | `/a2a` |
| **ACP** | 태스크 기반 에이전트 통신 | `/acp/*` |
| **AGP** | 에이전트 게이트웨이 허브 | `/agp/admin/*` |
| **MCP** | 에이전트 → 도구/리소스 | `/mcp` |
| **OpenAI** | OpenAI 호환 프록시 | `/v1/chat/completions` |

## AGP (Agent Gateway Protocol)

중앙 허브로서 여러 에이전트/서비스를 연결하는 게이트웨이. 에이전트 등록, 발견, 의도 기반 라우팅, 채널 관리를 제공한다.

### 프로토콜 관계

```
┌─────────────────────────────────────────┐
│         AGP Gateway Hub (중앙 허브)      │
│                                         │
│  ┌──────────┐  ┌──────────┐  ┌────────┐ │
│  │ Agent A  │  │ Agent B  │  │Agent C │ │
│  │ (Chat)   │  │ (Vision) │  │ (TTS)  │ │
│  └────┬─────┘  └────┬─────┘  └───┬────┘ │
│       │             │            │      │
│  ┌────┴─────────────┴────────────┴────┐ │
│  │      ACP (에이전트 ↔ 에이전트)      │ │
│  └────────────────────────────────────┘ │
│  ┌────────────────────────────────────┐ │
│  │      MCP (에이전트 → 도구)          │ │
│  └────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

### 에이전트 등록

```bash
curl -X POST /ai-platform/agp/admin/agents \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Translation Agent",
    "endpoint": "http://agent-host:9000/acp",
    "capabilities": "translation,summarization",
    "modalities": "text",
    "protocols": "acp"
  }'
```

### 라우팅 규칙

```bash
# 라우팅 규칙 추가 (능력 → 에이전트 매핑)
curl -X POST /ai-platform/agp/admin/routes \
  -d '{"capability": "translation", "agentId": "agent-abc12345", "priority": 80, "weight": 100}'

# 라우팅 테이블 조회
curl /ai-platform/agp/admin/routes

# 의도 기반 에이전트 탐색
curl -X POST /ai-platform/agp/admin/resolve \
  -d '{"intent": "이 이미지를 분석해주세요"}'
```

### 게이트웨이 통계

```bash
curl /ai-platform/agp/admin/stats
```

### AGP Admin API 목록

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST /agp/admin/agents` | 에이전트 등록 |
| `DELETE /agp/admin/agents/{id}` | 에이전트 해제 |
| `GET /agp/admin/agents` | 에이전트 목록 |
| `POST /agp/admin/routes` | 라우팅 규칙 추가 |
| `GET /agp/admin/routes` | 라우팅 테이블 |
| `GET /agp/admin/channels` | 활성 채널 |
| `GET /agp/admin/audit` | 감사 로그 |
| `GET /agp/admin/stats` | 게이트웨이 통계 |
| `POST /agp/admin/resolve` | 의도 기반 탐색 |

## Module Dependencies

- `was-config` — Server configuration model
- `was-servlet-core` — Servlet container and application wiring
- `was-admin` — Admin authentication client
- Jakarta Servlet API 6.x
- SLF4J (logging)

## Dashboard Sections

| Section | Description |
|---------|-------------|
| Overview | Health status, mode, model count, policy count, proxy status |
| Serving Fabric | Feature flags, router timeout, default models, route policies |
| Bundled Models | Table of configured model profiles with latency/accuracy scores |
| Platformization | Registration, API generation, versioning, billing, portal, multi-tenant flags |
| Provider Integration | Multi-LLM provider table, OpenAI proxy quick start, failover/load balance status |
| Differentiation | AI-optimized WAS, routing, streaming, plugin framework flags |
| Developer Portal | OpenAPI spec, portal UI, gateway surface links |
| Gateway Sandbox | Interactive route/infer/ensemble/stream testing with live JSON output |
| Registry Workbench | Model registration, version promotion, registry snapshot viewer |
| Tenant Management | Tenant registration, API key issuance, tenant snapshot viewer |
| Usage & Metering | Real-time usage counters, control plane endpoints list |
| Published APIs | Auto-generated endpoint list, model invocation, billing preview |
| Fine-Tuning Lab | Job creation, progress tracking, tuned model materialization |
| Roadmap | Evolution stages from basic serving to platform commercialization |
| Configuration | YAML preview, control plane API JSON viewer |
