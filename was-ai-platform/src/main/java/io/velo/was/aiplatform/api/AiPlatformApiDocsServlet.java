package io.velo.was.aiplatform.api;

import io.velo.was.aiplatform.gateway.AiGatewayServlet;
import io.velo.was.config.ServerConfiguration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class AiPlatformApiDocsServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public AiPlatformApiDocsServlet(ServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || "/".equals(pathInfo) || "/openapi.json".equals(pathInfo)) {
            serveOpenApiSpec(req, resp);
            return;
        }
        if ("/ui".equals(pathInfo)) {
            serveDeveloperPortal(req, resp);
            return;
        }

        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        resp.getWriter().write("Not Found");
    }

    private void serveOpenApiSpec(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");

        ServerConfiguration.Server server = configuration.getServer();
        ServerConfiguration.AiPlatform ai = server.getAiPlatform();
        String host = "0.0.0.0".equals(server.getListener().getHost()) ? "localhost" : server.getListener().getHost();
        String baseUrl = "http://" + host + ":" + server.getListener().getPort() + ai.getConsole().getContextPath();

        String spec = """
                {
                  "openapi": "3.0.3",
                  "info": {
                    "title": "Velo AI Platform Gateway API",
                    "description": "Configuration-driven gateway and control plane API for the standalone Velo AI Platform module.",
                    "version": "0.5.0"
                  },
                  "servers": [
                    {
                      "url": "%s",
                      "description": "Current AI Platform base URL"
                    }
                  ],
                  "tags": [
                    {"name": "Gateway", "description": "Routing, inference, and streaming endpoints"},
                    {"name": "Control Plane", "description": "Status, registry, and usage endpoints for the standalone AI module"}
                  ],
                  "paths": {
                    "/gateway": {
                      "get": {
                        "tags": ["Gateway"],
                        "summary": "Discover gateway endpoints",
                        "responses": {
                          "200": {
                            "description": "Gateway endpoint list"
                          }
                        }
                      }
                    },
                    "/gateway/route": {
                      "post": {
                        "tags": ["Gateway"],
                        "summary": "Resolve request routing",
                        "description": "Applies prompt routing, route policy matching, and automatic model selection.",
                        "requestBody": {
                          "required": false,
                          "content": {
                            "application/json": {
                              "schema": {"$ref": "#/components/schemas/GatewayRequest"}
                            }
                          }
                        },
                        "responses": {
                          "200": {
                            "description": "Routing decision",
                            "content": {
                              "application/json": {
                                "schema": {"$ref": "#/components/schemas/RouteDecision"}
                              }
                            }
                          }
                        }
                      },
                      "get": {
                        "tags": ["Gateway"],
                        "summary": "Resolve request routing via query string",
                        "responses": {
                          "200": {
                            "description": "Routing decision"
                          }
                        }
                      }
                    },
                    "/gateway/infer": {
                      "post": {
                        "tags": ["Gateway"],
                        "summary": "Run a mock gateway inference",
                        "description": "Returns a routing decision and a mock inference payload using the selected model profile.",
                        "requestBody": {
                          "required": false,
                          "content": {
                            "application/json": {
                              "schema": {"$ref": "#/components/schemas/GatewayRequest"}
                            }
                          }
                        },
                        "responses": {
                          "200": {
                            "description": "Inference envelope",
                            "content": {
                              "application/json": {
                                "schema": {"$ref": "#/components/schemas/InferenceResponse"}
                              }
                            }
                          }
                        }
                      },
                      "get": {
                        "tags": ["Gateway"],
                        "summary": "Run a mock gateway inference via query string",
                        "responses": {
                          "200": {
                            "description": "Inference envelope"
                          }
                        }
                      }
                    },
                    "/gateway/stream": {
                      "get": {
                        "tags": ["Gateway"],
                        "summary": "Stream a mock inference response",
                        "description": "Returns server-sent events for token-style streaming when streaming is enabled.",
                        "responses": {
                          "200": {
                            "description": "SSE token stream",
                            "content": {
                              "text/event-stream": {
                                "schema": {
                                  "type": "string"
                                }
                              }
                            }
                          }
                        }
                      }
                    },
                    "/invoke/{model}": {
                      "post": {
                        "tags": ["Gateway"],
                        "summary": "Invoke a published generated API",
                        "description": "Public endpoint generated from the active model registry when auto API generation is enabled.",
                        "responses": {
                          "200": {
                            "description": "Published endpoint inference response"
                          }
                        }
                      }
                    },
                    "/api/status": {
                      "get": {
                        "tags": ["Control Plane"],
                        "summary": "Get AI Platform status",
                        "responses": {
                          "200": {
                            "description": "Platform health"
                          }
                        }
                      }
                    },
                    "/api/overview": {
                      "get": {
                        "tags": ["Control Plane"],
                        "summary": "Get AI Platform overview",
                        "responses": {
                          "200": {
                            "description": "Platform capability summary"
                          }
                        }
                      }
                    },
                    "/api/models": {
                      "get": {
                        "tags": ["Control Plane"],
                        "summary": "List registered models",
                        "description": "Requires an authenticated console session.",
                        "responses": {
                          "200": {
                            "description": "Registry snapshot"
                          }
                        }
                      },
                      "post": {
                        "tags": ["Control Plane"],
                        "summary": "Register or update a model version",
                        "description": "Requires an authenticated console session and model registration to be enabled.",
                        "responses": {
                          "201": {
                            "description": "Registered model"
                          }
                        }
                      }
                    },
                    "/api/models/{name}/versions/{version}/status": {
                      "post": {
                        "tags": ["Control Plane"],
                        "summary": "Change model version status",
                        "description": "Promote ACTIVE, keep CANARY, or retire a version. Requires an authenticated console session.",
                        "responses": {
                          "200": {
                            "description": "Updated model"
                          }
                        }
                      }
                    },
                    "/api/published-apis": {
                      "get": {
                        "tags": ["Control Plane"],
                        "summary": "List published generated APIs",
                        "description": "Requires an authenticated console session.",
                        "responses": {
                          "200": {
                            "description": "Published endpoint inventory"
                          }
                        }
                      }
                    },
                    "/api/billing": {
                      "get": {
                        "tags": ["Control Plane"],
                        "summary": "Get billing preview",
                        "description": "Requires an authenticated console session.",
                        "responses": {
                          "200": {
                            "description": "Estimated billing snapshot"
                          }
                        }
                      }
                    },
                    "/api/usage": {
                      "get": {
                        "tags": ["Control Plane"],
                        "summary": "Get usage and metering counters",
                        "description": "Requires an authenticated console session.",
                        "responses": {
                          "200": {
                            "description": "Usage snapshot"
                          }
                        }
                      }
                    },
                    "/api/metrics": {
                      "get": {
                        "tags": ["Control Plane"],
                        "summary": "Alias for usage counters",
                        "description": "Requires an authenticated console session.",
                        "responses": {
                          "200": {
                            "description": "Usage snapshot"
                          }
                        }
                      }
                    },
                    "/api/fine-tuning/jobs": {
                      "get": {
                        "tags": ["Control Plane"],
                        "summary": "List fine-tuning jobs",
                        "description": "Requires an authenticated console session and fine-tuning API to be enabled.",
                        "responses": {
                          "200": {
                            "description": "Fine-tuning jobs"
                          }
                        }
                      },
                      "post": {
                        "tags": ["Control Plane"],
                        "summary": "Create a fine-tuning job",
                        "description": "Requires an authenticated console session and fine-tuning API to be enabled.",
                        "responses": {
                          "201": {
                            "description": "Created job"
                          }
                        }
                      }
                    },
                    "/api/fine-tuning/jobs/{id}": {
                      "get": {
                        "tags": ["Control Plane"],
                        "summary": "Get a fine-tuning job",
                        "responses": {
                          "200": {
                            "description": "Fine-tuning job"
                          }
                        }
                      }
                    },
                    "/api/fine-tuning/jobs/{id}/cancel": {
                      "post": {
                        "tags": ["Control Plane"],
                        "summary": "Cancel a fine-tuning job",
                        "responses": {
                          "200": {
                            "description": "Cancelled job"
                          }
                        }
                      }
                    },
                    "/api/tenants": {
                      "get": {
                        "tags": ["Control Plane"],
                        "summary": "List tenants",
                        "description": "Requires an authenticated console session with multi-tenant enabled.",
                        "responses": {
                          "200": {
                            "description": "Tenant snapshot",
                            "content": {
                              "application/json": {
                                "schema": {"$ref": "#/components/schemas/TenantSnapshot"}
                              }
                            }
                          }
                        }
                      },
                      "post": {
                        "tags": ["Control Plane"],
                        "summary": "Register or update a tenant",
                        "requestBody": {
                          "required": true,
                          "content": {
                            "application/json": {
                              "schema": {"$ref": "#/components/schemas/TenantRegistration"}
                            }
                          }
                        },
                        "responses": {
                          "201": {
                            "description": "Registered tenant"
                          }
                        }
                      }
                    },
                    "/api/tenants/{id}/keys": {
                      "post": {
                        "tags": ["Control Plane"],
                        "summary": "Issue an API key for a tenant",
                        "responses": {
                          "201": {
                            "description": "Issued API key with secret"
                          }
                        }
                      }
                    },
                    "/api/tenants/{id}/usage": {
                      "get": {
                        "tags": ["Control Plane"],
                        "summary": "Get tenant usage metrics",
                        "responses": {
                          "200": {
                            "description": "Tenant usage info"
                          }
                        }
                      }
                    },
                    "/v1/chat/completions": {
                      "post": {
                        "tags": ["Gateway"],
                        "summary": "OpenAI-compatible chat completions proxy",
                        "description": "Accepts OpenAI-format requests and routes them through the AI gateway with failover support.",
                        "requestBody": {
                          "required": true,
                          "content": {
                            "application/json": {
                              "schema": {"$ref": "#/components/schemas/ChatCompletionRequest"}
                            }
                          }
                        },
                        "responses": {
                          "200": {
                            "description": "OpenAI-compatible chat completion response",
                            "content": {
                              "application/json": {
                                "schema": {"$ref": "#/components/schemas/ChatCompletionResponse"}
                              }
                            }
                          }
                        }
                      }
                    }
                  },
                  "components": {
                    "schemas": {
                      "GatewayRequest": {
                        "type": "object",
                        "properties": {
                          "requestType": {"type": "string", "example": "CHAT"},
                          "prompt": {"type": "string", "example": "Recommend three products for a new user"},
                          "sessionId": {"type": "string", "example": "demo-session"},
                          "stream": {"type": "boolean", "example": false}
                        }
                      },
                      "RouteDecision": {
                        "type": "object",
                        "properties": {
                          "requestedType": {"type": "string"},
                          "resolvedType": {"type": "string"},
                          "model": {
                            "type": "object",
                            "properties": {
                              "name": {"type": "string"},
                              "category": {"type": "string"},
                              "provider": {"type": "string"},
                              "version": {"type": "string"},
                              "expectedLatencyMs": {"type": "integer"},
                              "accuracyScore": {"type": "integer"}
                            }
                          },
                          "routePolicy": {"type": "string"},
                          "strategyApplied": {"type": "string"},
                          "cache": {
                            "type": "object",
                            "properties": {
                              "enabled": {"type": "boolean"},
                              "hit": {"type": "boolean"},
                              "key": {"type": "string"}
                            }
                          },
                          "gateway": {
                            "type": "object",
                            "properties": {
                              "streamingSupported": {"type": "boolean"},
                              "promptRouted": {"type": "boolean"}
                            }
                          },
                          "observability": {
                            "type": "object",
                            "properties": {
                              "totalRequests": {"type": "integer"},
                              "modelRequestCount": {"type": "integer"}
                            }
                          },
                          "reasoning": {"type": "string"}
                        }
                      },
                      "InferenceResponse": {
                        "type": "object",
                        "properties": {
                          "decision": {"$ref": "#/components/schemas/RouteDecision"},
                          "response": {
                            "type": "object",
                            "properties": {
                              "outputText": {"type": "string"},
                              "estimatedTokens": {"type": "integer"},
                              "confidence": {"type": "number"}
                            }
                          }
                        }
                      },
                      "TenantRegistration": {
                        "type": "object",
                        "properties": {
                          "tenantId": {"type": "string", "example": "tenant-a"},
                          "displayName": {"type": "string", "example": "Tenant A"},
                          "plan": {"type": "string", "example": "starter"},
                          "rateLimitPerMinute": {"type": "integer", "example": 120},
                          "tokenQuota": {"type": "integer", "example": 250000},
                          "active": {"type": "boolean", "example": true}
                        },
                        "required": ["tenantId"]
                      },
                      "TenantSnapshot": {
                        "type": "object",
                        "properties": {
                          "multiTenantEnabled": {"type": "boolean"},
                          "apiKeyHeader": {"type": "string"},
                          "totalTenants": {"type": "integer"},
                          "activeTenants": {"type": "integer"},
                          "tenants": {"type": "array", "items": {"type": "object"}}
                        }
                      },
                      "ChatCompletionRequest": {
                        "type": "object",
                        "properties": {
                          "model": {"type": "string", "example": "llm-general"},
                          "messages": {
                            "type": "array",
                            "items": {
                              "type": "object",
                              "properties": {
                                "role": {"type": "string", "enum": ["system","user","assistant"]},
                                "content": {"type": "string"}
                              }
                            }
                          },
                          "temperature": {"type": "number", "example": 0.7},
                          "max_tokens": {"type": "integer", "example": 1024},
                          "stream": {"type": "boolean", "example": false}
                        },
                        "required": ["messages"]
                      },
                      "ChatCompletionResponse": {
                        "type": "object",
                        "properties": {
                          "id": {"type": "string"},
                          "object": {"type": "string", "example": "chat.completion"},
                          "created": {"type": "integer"},
                          "model": {"type": "string"},
                          "choices": {
                            "type": "array",
                            "items": {
                              "type": "object",
                              "properties": {
                                "index": {"type": "integer"},
                                "message": {
                                  "type": "object",
                                  "properties": {
                                    "role": {"type": "string"},
                                    "content": {"type": "string"}
                                  }
                                },
                                "finish_reason": {"type": "string"}
                              }
                            }
                          },
                          "usage": {
                            "type": "object",
                            "properties": {
                              "prompt_tokens": {"type": "integer"},
                              "completion_tokens": {"type": "integer"},
                              "total_tokens": {"type": "integer"}
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """.formatted(baseUrl);
        resp.getWriter().write(spec.trim());
    }

    private void serveDeveloperPortal(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        String contextPath = req.getContextPath();
        String streamUrl = AiGatewayServlet.buildStreamUrl(contextPath, "AUTO", "portal-demo", "recommend products for a mobile user");

        String page = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Velo AI Platform Developer Portal</title>
                <style>
                  :root { --bg:#f5ede0; --card:rgba(255,255,255,0.84); --ink:#192923; --soft:#5d6d67; --teal:#0f766e; --deep:#12342f; --line:rgba(25,41,35,0.10); }
                  * { box-sizing:border-box; }
                  body { margin:0; font-family:"IBM Plex Sans","Segoe UI",sans-serif; background:radial-gradient(circle at top left, rgba(15,118,110,0.16), transparent 26%%), linear-gradient(180deg,#faf5eb,#efe4d4); color:var(--ink); }
                  .shell { width:min(1180px, calc(100vw - 32px)); margin:24px auto 48px; display:grid; gap:18px; }
                  .hero, .panel { border-radius:28px; border:1px solid rgba(255,255,255,0.50); background:var(--card); box-shadow:0 24px 64px rgba(18,52,47,0.12); backdrop-filter:blur(18px); }
                  .hero { padding:34px; background:linear-gradient(135deg, rgba(15,118,110,0.98), rgba(18,52,47,0.94)); color:#f8f3e8; }
                  .hero h1 { margin:12px 0; font-size:clamp(34px, 5vw, 56px); line-height:1.02; letter-spacing:-0.06em; }
                  .hero p { max-width:780px; line-height:1.8; color:rgba(248,243,232,0.84); }
                  .hero-row, .actions, .card-grid { display:flex; flex-wrap:wrap; gap:12px; }
                  .hero-pill, .actions a { border-radius:999px; }
                  .hero-pill { padding:10px 14px; background:rgba(255,255,255,0.14); border:1px solid rgba(255,255,255,0.18); font-size:12px; }
                  .grid { display:grid; gap:18px; grid-template-columns:repeat(12, minmax(0, 1fr)); }
                  .panel { padding:24px; }
                  .span-7 { grid-column:span 7; } .span-5 { grid-column:span 5; } .span-12 { grid-column:span 12; }
                  h2 { margin:0 0 8px; font-size:24px; letter-spacing:-0.04em; }
                  p, li, code, pre, textarea, input, select, button { font:inherit; }
                  .sub { color:var(--soft); line-height:1.7; font-size:14px; }
                  .actions a, button { display:inline-flex; align-items:center; justify-content:center; padding:11px 16px; border:0; cursor:pointer; text-decoration:none; background:var(--deep); color:#fff7ea; font-weight:700; }
                  .actions a.alt, button.alt { background:rgba(15,118,110,0.10); color:var(--teal); }
                  .card-grid > div { flex:1 1 220px; padding:16px 18px; border-radius:18px; border:1px solid var(--line); background:rgba(255,255,255,0.55); }
                  .card-grid strong { display:block; margin-bottom:6px; }
                  .code { margin:0; padding:18px; border-radius:18px; background:#14231f; color:#e6ddcf; overflow:auto; white-space:pre-wrap; line-height:1.75; font-size:12px; }
                  .json { margin:0; padding:18px; border-radius:18px; background:rgba(255,255,255,0.55); border:1px solid var(--line); min-height:280px; overflow:auto; white-space:pre-wrap; line-height:1.7; font-size:12px; }
                  ul { margin:0; padding-left:18px; color:var(--soft); }
                  @media (max-width:1024px) { .span-7, .span-5 { grid-column:span 12; } .shell { width:min(100vw - 18px, 1180px); } }
                </style>
                </head>
                <body>
                  <div class="shell">
                    <section class="hero">
                      <div class="hero-row">
                        <span class="hero-pill">Standalone AI module</span>
                        <span class="hero-pill">Developer portal enabled</span>
                        <span class="hero-pill">OpenAPI + Gateway sandbox</span>
                      </div>
                      <h1>Velo AI Platform Developer Portal</h1>
                      <p>Use this page to inspect the generated OpenAPI contract, test the built-in AI gateway, and verify routing, inference, and streaming behavior without going through the admin console.</p>
                      <div class="actions">
                        <a href="%s/api-docs">OpenAPI JSON</a>
                        <a class="alt" href="%s">Streaming demo</a>
                        <a class="alt" href="%s/">Back to console</a>
                      </div>
                    </section>
                    <section class="grid">
                      <div class="panel span-7">
                        <h2>OpenAPI Contract</h2>
                        <p class="sub">The specification is generated directly from the AI Platform module and documents the public gateway plus operational API surface.</p>
                        <pre class="json" id="openapiSpec">Loading specification...</pre>
                      </div>
                      <div class="panel span-5">
                        <h2>Quick Start</h2>
                        <p class="sub">Gateway endpoints are public service APIs. Control plane endpoints such as model registry and usage counters require an authenticated console session.</p>
                        <div class="card-grid">
                          <div>
                            <strong>Route</strong>
                            POST <code>%s/gateway/route</code>
                          </div>
                          <div>
                            <strong>Infer</strong>
                            POST <code>%s/gateway/infer</code>
                          </div>
                          <div>
                            <strong>Stream</strong>
                            GET <code>%s/gateway/stream</code>
                          </div>
                          <div>
                            <strong>Models</strong>
                            GET <code>%s/api/models</code>
                          </div>
                          <div>
                            <strong>Usage</strong>
                            GET <code>%s/api/usage</code>
                          </div>
                          <div>
                            <strong>Invoke</strong>
                            POST <code>%s/invoke/{model}</code>
                          </div>
                          <div>
                            <strong>Billing</strong>
                            GET <code>%s/api/billing</code>
                          </div>
                          <div>
                            <strong>Chat (OpenAI)</strong>
                            POST <code>%s/v1/chat/completions</code>
                          </div>
                          <div>
                            <strong>Completions</strong>
                            POST <code>%s/v1/completions</code>
                          </div>
                          <div>
                            <strong>Ensemble</strong>
                            POST <code>%s/gateway/ensemble</code>
                          </div>
                          <div>
                            <strong>Tenants</strong>
                            GET <code>%s/api/tenants</code>
                          </div>
                          <div>
                            <strong>Plugins</strong>
                            GET <code>%s/api/plugins</code>
                          </div>
                        </div>
                        <pre class="code">curl -X POST %s/gateway/infer \
  -H "Content-Type: application/json" \
  -d '{
    "requestType": "AUTO",
    "sessionId": "portal-demo",
    "prompt": "recommend products for a mobile user"
  }'</pre>
                      </div>
                    </section>
                    <section class="panel span-12">
                      <h2>What This Exposes</h2>
                      <ul>
                        <li>Prompt routing driven by <code>server.aiPlatform.advanced.promptRoutingEnabled</code>.</li>
                        <li>Model selection driven by route policies, category matching, and the default strategy.</li>
                        <li>Context cache keys derived from session and prompt fingerprints.</li>
                        <li>Streaming output when <code>server.aiPlatform.differentiation.streamingResponseEnabled</code> is true.</li><li>Model registry, version promotion, and usage APIs for the authenticated control plane.</li><li>Auto-generated published endpoints, billing preview, and fine-tuning job APIs.</li>
                        <li>OpenAI-compatible proxy at <code>/v1/chat/completions</code> and <code>/v1/completions</code> with automatic failover.</li>
                        <li>Ensemble serving at <code>/gateway/ensemble</code> for multi-model accuracy improvement.</li>
                        <li>Multi-tenant management with rate limits, token quotas, and API key issuance.</li>
                        <li>Plugin framework for custom pre/post processing of inference requests.</li>
                      </ul>
                    </section>
                  </div>
                  <script>
                    fetch('%s/api-docs')
                      .then(function(response) { return response.json(); })
                      .then(function(spec) { document.getElementById('openapiSpec').textContent = JSON.stringify(spec, null, 2); })
                      .catch(function(error) { document.getElementById('openapiSpec').textContent = 'Failed to load OpenAPI spec: ' + error; });
                  </script>
                </body>
                </html>
                """.formatted(contextPath, streamUrl, contextPath, contextPath, contextPath, contextPath, contextPath, contextPath, contextPath, contextPath, contextPath, contextPath, contextPath, contextPath, contextPath, contextPath, contextPath);
        resp.getWriter().write(page);
    }
}