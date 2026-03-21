# AI Platform Console

Documentation for the standalone built-in AI Platform module in Velo WAS.

- It is deployed separately from `was-webadmin`.
- The console path is controlled by `server.aiPlatform.console.contextPath`.
- The module exposes both a control-plane console and public AI gateway APIs.

## Default access

- Console: `http://localhost:8080/ai-platform`
- Auth: console pages reuse the existing WAS admin account
- Public API: gateway and API docs endpoints are intentionally public so they can act as service APIs

## Core configuration

```yaml
server:
  aiPlatform:
    enabled: true
    mode: PLATFORM
    console:
      enabled: true
      contextPath: /ai-platform
```

## Included surfaces

- Overview: product direction, default strategy, latency objective, cache TTL
- Serving: multi-model routing, A/B testing, auto selection, bundled models, route policies
- Platform: model registration, auto API generation, versioning, billing, developer portal, multi-tenant flags
- Advanced: prompt routing, context cache, AI gateway, observability, GPU scheduling flags
- Gateway Sandbox: route, infer, and stream requests directly from the console
- Roadmap: staged progression from basic serving to commercialization

## Public API

- `GET {contextPath}/api/status`
- `GET {contextPath}/api/overview`
- `GET {contextPath}/gateway`
- `POST {contextPath}/gateway/route`
- `POST {contextPath}/gateway/infer`
- `GET {contextPath}/gateway/stream`
- `GET {contextPath}/api-docs`
- `GET {contextPath}/api-docs/ui`

## Location

- Module: `was-ai-platform`
- Bootstrap wiring: `was-bootstrap`