# was-ai-platform

Standalone built-in AI platform module for Velo WAS.

## What it provides

- Independent deployment from `was-webadmin`
- Config-driven AI platform console under `server.aiPlatform.console.contextPath`
- Public AI gateway endpoints for route, infer, and stream flows
- Developer portal with generated OpenAPI JSON and a docs UI
- Dashboard sections for serving, platformization, advanced controls, roadmap, and gateway sandbox

## Default paths

- Console: `/ai-platform`
- Status API: `/ai-platform/api/status`
- Overview API: `/ai-platform/api/overview`
- Gateway: `/ai-platform/gateway/*`
- Developer portal: `/ai-platform/api-docs/ui`
- OpenAPI JSON: `/ai-platform/api-docs`