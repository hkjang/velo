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

        String cp = req.getContextPath();
        String streamUrl = AiGatewayServlet.buildStreamUrl(cp, "AUTO", "portal-demo", "recommend products for a mobile user");

        // Use StringBuilder to avoid String.formatted() issues with CSS % characters
        StringBuilder b = new StringBuilder(8192);
        b.append("<!DOCTYPE html>\n<html lang=\"ko\">\n<head>\n<meta charset=\"UTF-8\">\n");
        b.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        b.append("<title>Velo AI Platform \uAC1C\uBC1C\uC790 \uD3EC\uD138</title>\n<style>\n");
        b.append(":root{--bg:#f4efe6;--card:#fff;--ink:#192923;--soft:#5d6d67;--teal:#0f766e;--deep:#12342f;--line:rgba(25,41,35,0.10);}\n");
        b.append("*{box-sizing:border-box;margin:0;padding:0;}\n");
        b.append("body{font-family:'Pretendard','Noto Sans KR','IBM Plex Sans',system-ui,sans-serif;background:var(--bg);color:var(--ink);}\n");
        b.append(".shell{max-width:1180px;margin:24px auto 48px;padding:0 16px;}\n");
        b.append(".hero{border-radius:20px;padding:34px;background:linear-gradient(135deg,var(--teal),var(--deep));color:#f8f3e8;margin-bottom:18px;}\n");
        b.append(".hero h1{font-size:clamp(26px,4vw,42px);font-weight:800;margin:12px 0;letter-spacing:-0.04em;}\n");
        b.append(".hero p{max-width:780px;line-height:1.8;color:rgba(248,243,232,0.84);font-size:14px;}\n");
        b.append(".pills{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:12px;}\n");
        b.append(".pill{padding:6px 14px;border-radius:20px;font-size:12px;background:rgba(255,255,255,0.14);border:1px solid rgba(255,255,255,0.18);color:rgba(255,255,255,0.9);}\n");
        b.append(".actions{display:flex;flex-wrap:wrap;gap:10px;margin-top:16px;}\n");
        b.append(".actions a{display:inline-flex;align-items:center;padding:10px 18px;border-radius:10px;font-size:13px;font-weight:700;text-decoration:none;}\n");
        b.append(".btn-p{background:var(--card);color:var(--deep);}\n");
        b.append(".btn-s{background:rgba(255,255,255,0.12);color:#fff;border:1px solid rgba(255,255,255,0.2);}\n");
        b.append(".row{display:grid;grid-template-columns:7fr 5fr;gap:18px;margin-bottom:18px;}\n");
        b.append(".card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:22px;box-shadow:0 1px 3px rgba(0,0,0,0.06);}\n");
        b.append("h2{font-size:20px;font-weight:700;margin-bottom:6px;}\n");
        b.append(".sub{color:var(--soft);font-size:13px;line-height:1.7;margin-bottom:12px;}\n");
        b.append(".ep-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:8px;margin-bottom:14px;}\n");
        b.append(".ep{padding:12px 14px;border-radius:10px;border:1px solid var(--line);background:#f8fafc;}\n");
        b.append(".ep strong{display:block;font-size:13px;margin-bottom:2px;}\n");
        b.append(".ep code{font-size:11px;color:var(--soft);}\n");
        b.append("pre.json{background:#f8fafc;border:1px solid var(--line);border-radius:12px;padding:16px;font-family:'JetBrains Mono',Consolas,monospace;font-size:12px;line-height:1.6;overflow:auto;max-height:400px;white-space:pre-wrap;}\n");
        b.append("pre.code{background:#1e293b;color:#e2e8f0;border-radius:12px;padding:16px;font-family:'JetBrains Mono',Consolas,monospace;font-size:12px;line-height:1.7;overflow:auto;white-space:pre-wrap;margin-top:12px;}\n");
        b.append("ul{padding-left:18px;color:var(--soft);font-size:13px;line-height:1.8;}\n");
        b.append("@media(max-width:900px){.row{grid-template-columns:1fr;}}\n");
        b.append("</style>\n</head>\n<body>\n<div class=\"shell\">\n");

        // Hero
        b.append("<section class=\"hero\">\n");
        b.append("<div class=\"pills\">");
        b.append("<span class=\"pill\">\uB3C5\uB9BD AI \uBAA8\uB4C8</span>");
        b.append("<span class=\"pill\">\uAC1C\uBC1C\uC790 \uD3EC\uD138 \uD65C\uC131</span>");
        b.append("<span class=\"pill\">OpenAPI + \uAC8C\uC774\uD2B8\uC6E8\uC774 \uC0CC\uB4DC\uBC15\uC2A4</span>");
        b.append("</div>\n");
        b.append("<h1>Velo AI Platform \uAC1C\uBC1C\uC790 \uD3EC\uD138</h1>\n");
        b.append("<p>\uC790\uB3D9 \uC0DD\uC131\uB41C OpenAPI \uACC4\uC57D\uC11C\uB97C \uD655\uC778\uD558\uACE0, AI \uAC8C\uC774\uD2B8\uC6E8\uC774\uB97C \uD14C\uC2A4\uD2B8\uD558\uBA70, \uB77C\uC6B0\uD305\u00B7\uCD94\uB860\u00B7\uC2A4\uD2B8\uB9AC\uBC0D \uB3D9\uC791\uC744 \uAD00\uB9AC \uCF58\uC194 \uC5C6\uC774 \uAC80\uC99D\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.</p>\n");
        b.append("<div class=\"actions\">");
        b.append("<a class=\"btn-p\" href=\"").append(cp).append("/api-docs\">OpenAPI JSON</a>");
        b.append("<a class=\"btn-s\" href=\"").append(streamUrl).append("\">\uC2A4\uD2B8\uB9AC\uBC0D \uB370\uBAA8</a>");
        b.append("<a class=\"btn-s\" href=\"").append(cp).append("/\">\uCF58\uC194\uC73C\uB85C \uB3CC\uC544\uAC00\uAE30</a>");
        b.append("</div>\n</section>\n");

        // Two-column row
        b.append("<div class=\"row\">\n");
        // Left: OpenAPI spec
        b.append("<div class=\"card\">\n");
        b.append("<h2>OpenAPI \uACC4\uC57D\uC11C</h2>\n");
        b.append("<p class=\"sub\">AI \uD50C\uB7AB\uD3FC \uBAA8\uB4C8\uC5D0\uC11C \uC790\uB3D9 \uC0DD\uC131\uB41C \uACF5\uAC1C \uAC8C\uC774\uD2B8\uC6E8\uC774 + \uC6B4\uC601 API \uC2A4\uD399\uC785\uB2C8\uB2E4.</p>\n");
        b.append("<pre class=\"json\" id=\"openapiSpec\">\uB85C\uB529 \uC911...</pre>\n");
        b.append("</div>\n");
        // Right: Quick Start
        b.append("<div class=\"card\">\n");
        b.append("<h2>\uBE60\uB978 \uC2DC\uC791</h2>\n");
        b.append("<p class=\"sub\">\uAC8C\uC774\uD2B8\uC6E8\uC774 \uC5D4\uB4DC\uD3EC\uC778\uD2B8\uB294 \uACF5\uAC1C API\uC785\uB2C8\uB2E4. \uBAA8\uB378 \uB808\uC9C0\uC2A4\uD2B8\uB9AC \ub4f1 \uCEE8\uD2B8\uB864 \uD50C\uB808\uC778 API\uB294 \uCF58\uC194 \uB85C\uADF8\uC778\uC774 \uD544\uC694\uD569\uB2C8\uB2E4.</p>\n");
        b.append("<div class=\"ep-grid\">\n");
        endpoint(b, "\uB77C\uC6B0\uD305", "POST", cp + "/gateway/route");
        endpoint(b, "\uCD94\uB860", "POST", cp + "/gateway/infer");
        endpoint(b, "\uC2A4\uD2B8\uB9AC\uBC0D", "GET", cp + "/gateway/stream");
        endpoint(b, "\uBAA8\uB378 \uBAA9\uB85D", "GET", cp + "/api/models");
        endpoint(b, "\uC0AC\uC6A9\uB7C9", "GET", cp + "/api/usage");
        endpoint(b, "API \uD638\uCD9C", "POST", cp + "/invoke/{model}");
        endpoint(b, "\uACFC\uAE08", "GET", cp + "/api/billing");
        endpoint(b, "Chat (OpenAI)", "POST", cp + "/v1/chat/completions");
        endpoint(b, "Completions", "POST", cp + "/v1/completions");
        endpoint(b, "\uC559\uC0C1\uBE14", "POST", cp + "/gateway/ensemble");
        endpoint(b, "\uD14C\uB10C\uD2B8", "GET", cp + "/api/tenants");
        endpoint(b, "\uD50C\uB7EC\uADF8\uC778", "GET", cp + "/api/plugins");
        b.append("</div>\n");
        b.append("<pre class=\"code\">curl -X POST ").append(cp).append("/gateway/infer \\\n");
        b.append("  -H \"Content-Type: application/json\" \\\n");
        b.append("  -d '{\n    \"requestType\": \"AUTO\",\n    \"sessionId\": \"portal-demo\",\n    \"prompt\": \"\uBAA8\uBC14\uC77C \uACE0\uAC1D\uC5D0\uAC8C \uCD94\uCC9C\uD560 \uC0C1\uD488 3\uAC1C\"\n  }'</pre>\n");
        b.append("</div>\n</div>\n");

        // Full-width: What This Exposes
        b.append("<div class=\"card\">\n");
        b.append("<h2>\uC8FC\uC694 \uAE30\uB2A5 \uC694\uC57D</h2>\n");
        b.append("<ul>\n");
        b.append("<li>\uD504\uB86C\uD504\uD2B8 \uB77C\uC6B0\uD305: <code>server.aiPlatform.advanced.promptRoutingEnabled</code> \uC124\uC815\uC73C\uB85C \uC81C\uC5B4</li>\n");
        b.append("<li>\uBAA8\uB378 \uC120\uD0DD: \uB77C\uC6B0\uD2B8 \uC815\uCC45, \uCE74\uD14C\uACE0\uB9AC \uB9E4\uCE6D, \uAE30\uBCF8 \uC804\uB7B5 \uAE30\uBC18 \uC790\uB3D9 \uC120\uD0DD</li>\n");
        b.append("<li>\uCEE8\uD14D\uC2A4\uD2B8 \uCE90\uC2DC: \uC138\uC158\uACFC \uD504\uB86C\uD504\uD2B8 \uD551\uAC70\uD504\uB9B0\uD2B8 \uAE30\uBC18 \uCE90\uC2DC \uD0A4 \uC0DD\uC131</li>\n");
        b.append("<li>\uC2A4\uD2B8\uB9AC\uBC0D: <code>server.aiPlatform.differentiation.streamingResponseEnabled</code> \uD65C\uC131 \uC2DC SSE \uD1A0\uD070 \uC2A4\uD2B8\uB9BC</li>\n");
        b.append("<li>OpenAI \uD638\uD658 \uD504\uB85D\uC2DC: <code>/v1/chat/completions</code>, <code>/v1/completions</code> \uC790\uB3D9 Failover \uC9C0\uC6D0</li>\n");
        b.append("<li>\uC559\uC0C1\uBE14 \uC11C\uBE59: <code>/gateway/ensemble</code>\uC5D0\uC11C \uBA40\uD2F0 \uBAA8\uB378 \uACB0\uACFC \uACB0\uD569</li>\n");
        b.append("<li>\uBA40\uD2F0 \uD14C\uB10C\uD2B8: \uC694\uCCAD \uC81C\uD55C, \uD1A0\uD070 \uCFFC\uD130, API \uD0A4 \uBC1C\uAE09 \uAD00\uB9AC</li>\n");
        b.append("<li>\uD50C\uB7EC\uADF8\uC778 \uD504\uB808\uC784\uC6CC\uD06C: \uCD94\uB860 \uC694\uCCAD\uC758 \uC804\uCC98\uB9AC/\uD6C4\uCC98\uB9AC \uCEE4\uC2A4\uD140 \uD50C\uB7EC\uADF8\uC778</li>\n");
        b.append("</ul>\n</div>\n");

        // Script
        b.append("<script>\n");
        b.append("fetch('").append(cp).append("/api-docs')\n");
        b.append("  .then(function(r){return r.json();})\n");
        b.append("  .then(function(s){document.getElementById('openapiSpec').textContent=JSON.stringify(s,null,2);})\n");
        b.append("  .catch(function(e){document.getElementById('openapiSpec').textContent='\uC2A4\uD399 \uB85C\uB4DC \uC2E4\uD328: '+e;});\n");
        b.append("</script>\n");
        b.append("</div>\n</body>\n</html>");
        resp.getWriter().write(b.toString());
    }

    private static void endpoint(StringBuilder b, String label, String method, String path) {
        b.append("<div class=\"ep\"><strong>").append(label).append("</strong><code>").append(method).append(" ").append(path).append("</code></div>\n");
    }
}