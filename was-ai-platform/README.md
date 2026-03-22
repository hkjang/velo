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
