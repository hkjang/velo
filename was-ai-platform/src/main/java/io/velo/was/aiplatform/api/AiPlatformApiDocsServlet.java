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

        // Use StringBuilder to avoid String.formatted() issues with special characters
        StringBuilder s = new StringBuilder(8192);
        s.append("{\n");
        s.append("  \"openapi\": \"3.0.3\",\n");
        s.append("  \"info\": {\n");
        s.append("    \"title\": \"Velo AI Platform Gateway API\",\n");
        s.append("    \"description\": \"Configuration-driven gateway and control plane API for the standalone Velo AI Platform module.\",\n");
        s.append("    \"version\": \"0.5.12\"\n");
        s.append("  },\n");
        s.append("  \"servers\": [\n");
        s.append("    {\n");
        s.append("      \"url\": \"").append(baseUrl).append("\",\n");
        s.append("      \"description\": \"Current AI Platform base URL\"\n");
        s.append("    }\n");
        s.append("  ],\n");
        s.append("  \"tags\": [\n");
        s.append("    {\"name\": \"Gateway\", \"description\": \"Routing, inference, and streaming endpoints\"},\n");
        s.append("    {\"name\": \"Control Plane\", \"description\": \"Status, registry, and usage endpoints for the standalone AI module\"}\n");
        s.append("  ],\n");
        s.append("  \"paths\": {\n");
        // Gateway paths
        apiPath(s, "/gateway", "get", "Gateway", "Discover gateway endpoints", null, false);
        s.append(",\n");
        apiPath(s, "/gateway/route", "post", "Gateway", "Resolve request routing", "Applies prompt routing, route policy matching, and automatic model selection.", true);
        s.append(",\n");
        apiPath(s, "/gateway/infer", "post", "Gateway", "Run a mock gateway inference", "Returns a routing decision and a mock inference payload using the selected model profile.", true);
        s.append(",\n");
        apiPath(s, "/gateway/stream", "get", "Gateway", "Stream a mock inference response", "Returns server-sent events for token-style streaming.", false);
        s.append(",\n");
        apiPath(s, "/gateway/ensemble", "post", "Gateway", "Multi-model ensemble inference", "Sends request to multiple models and combines results.", true);
        s.append(",\n");
        apiPath(s, "/invoke/{model}", "post", "Gateway", "Invoke a published generated API", "Public endpoint generated from the active model registry.", false);
        s.append(",\n");
        apiPath(s, "/v1/chat/completions", "post", "Gateway", "OpenAI-compatible chat completions proxy", "Accepts OpenAI-format requests and routes them through the AI gateway with failover support.", true);
        s.append(",\n");
        apiPath(s, "/v1/completions", "post", "Gateway", "OpenAI-compatible text completions proxy", "Accepts completion requests and routes them through the AI gateway.", true);
        s.append(",\n");
        apiPath(s, "/v1/models", "get", "Gateway", "List available models", "Returns models in OpenAI-compatible format.", false);
        s.append(",\n");
        // Control Plane paths
        apiPath(s, "/api/status", "get", "Control Plane", "Get AI Platform status", null, false);
        s.append(",\n");
        apiPath(s, "/api/overview", "get", "Control Plane", "Get AI Platform overview", null, false);
        s.append(",\n");
        apiPath(s, "/api/models", "get", "Control Plane", "List registered models", "Requires an authenticated console session.", false);
        s.append(",\n");
        s.append("    \"/api/models\": {\n");
        s.append("      \"post\": {\n");
        s.append("        \"tags\": [\"Control Plane\"],\n");
        s.append("        \"summary\": \"Register or update a model version\",\n");
        s.append("        \"responses\": { \"201\": { \"description\": \"Registered model\" } }\n");
        s.append("      }\n");
        s.append("    },\n");
        apiPath(s, "/api/models/{name}/versions/{version}/status", "post", "Control Plane", "Change model version status", "Promote ACTIVE, keep CANARY, or retire a version.", false);
        s.append(",\n");
        apiPath(s, "/api/usage", "get", "Control Plane", "Get usage and metering counters", null, false);
        s.append(",\n");
        apiPath(s, "/api/billing", "get", "Control Plane", "Get billing preview", null, false);
        s.append(",\n");
        apiPath(s, "/api/tenants", "get", "Control Plane", "List tenants", null, false);
        s.append(",\n");
        apiPath(s, "/api/tenants/{id}/keys", "post", "Control Plane", "Issue an API key for a tenant", null, false);
        s.append(",\n");
        apiPath(s, "/api/tenants/{id}/usage", "get", "Control Plane", "Get tenant usage metrics", null, false);
        s.append(",\n");
        apiPath(s, "/api/providers", "get", "Control Plane", "List AI providers", null, false);
        s.append(",\n");
        apiPath(s, "/api/plugins", "get", "Control Plane", "List plugins", null, false);
        s.append(",\n");
        apiPath(s, "/api/published-apis", "get", "Control Plane", "List published generated APIs", null, false);
        s.append(",\n");
        apiPath(s, "/api/fine-tuning/jobs", "get", "Control Plane", "List fine-tuning jobs", null, false);
        s.append(",\n");
        apiPath(s, "/api/fine-tuning/jobs", "post", "Control Plane", "Create a fine-tuning job", null, false);
        s.append(",\n");
        apiPath(s, "/api/fine-tuning/jobs/{id}/cancel", "post", "Control Plane", "Cancel a fine-tuning job", null, false);
        s.append(",\n");
        apiPath(s, "/api/config", "get", "Control Plane", "Get current AI platform configuration", null, false);
        s.append("\n");
        s.append("  },\n");
        // Components
        s.append("  \"components\": {\n");
        s.append("    \"schemas\": {\n");
        s.append("      \"GatewayRequest\": {\n");
        s.append("        \"type\": \"object\",\n");
        s.append("        \"properties\": {\n");
        s.append("          \"requestType\": {\"type\": \"string\", \"example\": \"CHAT\"},\n");
        s.append("          \"prompt\": {\"type\": \"string\", \"example\": \"Recommend three products\"},\n");
        s.append("          \"sessionId\": {\"type\": \"string\", \"example\": \"demo-session\"},\n");
        s.append("          \"stream\": {\"type\": \"boolean\", \"example\": false}\n");
        s.append("        }\n");
        s.append("      },\n");
        s.append("      \"ChatCompletionRequest\": {\n");
        s.append("        \"type\": \"object\",\n");
        s.append("        \"required\": [\"messages\"],\n");
        s.append("        \"properties\": {\n");
        s.append("          \"model\": {\"type\": \"string\", \"example\": \"llm-general\"},\n");
        s.append("          \"messages\": {\"type\": \"array\", \"items\": {\"type\": \"object\", \"properties\": {\"role\": {\"type\": \"string\"}, \"content\": {\"type\": \"string\"}}}},\n");
        s.append("          \"temperature\": {\"type\": \"number\", \"example\": 0.7},\n");
        s.append("          \"max_tokens\": {\"type\": \"integer\", \"example\": 1024},\n");
        s.append("          \"stream\": {\"type\": \"boolean\", \"example\": false}\n");
        s.append("        }\n");
        s.append("      }\n");
        s.append("    }\n");
        s.append("  }\n");
        s.append("}");
        resp.getWriter().write(s.toString());
    }

    private static void apiPath(StringBuilder s, String path, String method, String tag, String summary, String desc, boolean hasBody) {
        s.append("    \"").append(path).append("\": {\n");
        s.append("      \"").append(method).append("\": {\n");
        s.append("        \"tags\": [\"").append(tag).append("\"],\n");
        s.append("        \"summary\": \"").append(summary).append("\"");
        if (desc != null) {
            s.append(",\n        \"description\": \"").append(desc).append("\"");
        }
        if (hasBody) {
            s.append(",\n        \"requestBody\": { \"required\": false, \"content\": { \"application/json\": { \"schema\": { \"type\": \"object\" } } } }");
        }
        s.append(",\n        \"responses\": { \"200\": { \"description\": \"Success\" } }\n");
        s.append("      }\n");
        s.append("    }");
    }

    private void serveDeveloperPortal(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        String cp = req.getContextPath();
        String streamUrl = AiGatewayServlet.buildStreamUrl(cp, "AUTO", "portal-demo", "recommend products for a mobile user");

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
        b.append("<span class=\"pill\">\ub3c5\ub9bd AI \ubaa8\ub4c8</span>");
        b.append("<span class=\"pill\">\uac1c\ubc1c\uc790 \ud3ec\ud138 \ud65c\uc131</span>");
        b.append("<span class=\"pill\">OpenAPI + \uac8c\uc774\ud2b8\uc6e8\uc774 \uc0cc\ub4dc\ubc15\uc2a4</span>");
        b.append("</div>\n");
        b.append("<h1>Velo AI Platform \uac1c\ubc1c\uc790 \ud3ec\ud138</h1>\n");
        b.append("<p>\uc790\ub3d9 \uc0dd\uc131\ub41c OpenAPI \uacc4\uc57d\uc11c\ub97c \ud655\uc778\ud558\uace0, AI \uac8c\uc774\ud2b8\uc6e8\uc774\ub97c \ud14c\uc2a4\ud2b8\ud558\uba70, \ub77c\uc6b0\ud305/\ucd94\ub860/\uc2a4\ud2b8\ub9ac\ubc0d \ub3d9\uc791\uc744 \uad00\ub9ac \ucf58\uc194 \uc5c6\uc774 \uac80\uc99d\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4.</p>\n");
        b.append("<div class=\"actions\">");
        b.append("<a class=\"btn-p\" href=\"").append(cp).append("/api-docs\">OpenAPI JSON</a>");
        b.append("<a class=\"btn-s\" href=\"").append(streamUrl).append("\">\uc2a4\ud2b8\ub9ac\ubc0d \ub370\ubaa8</a>");
        b.append("<a class=\"btn-s\" href=\"").append(cp).append("/\">\ucf58\uc194\uc73c\ub85c \ub3cc\uc544\uac00\uae30</a>");
        b.append("</div>\n</section>\n");

        // Two-column row
        b.append("<div class=\"row\">\n");
        b.append("<div class=\"card\">\n");
        b.append("<h2>OpenAPI \uacc4\uc57d\uc11c</h2>\n");
        b.append("<p class=\"sub\">AI \ud50c\ub7ab\ud3fc \ubaa8\ub4c8\uc5d0\uc11c \uc790\ub3d9 \uc0dd\uc131\ub41c \uacf5\uac1c \uac8c\uc774\ud2b8\uc6e8\uc774 + \uc6b4\uc601 API \uc2a4\ud399\uc785\ub2c8\ub2e4.</p>\n");
        b.append("<pre class=\"json\" id=\"openapiSpec\">\ub85c\ub529 \uc911...</pre>\n");
        b.append("</div>\n");
        // Right: Quick Start
        b.append("<div class=\"card\">\n");
        b.append("<h2>\ube60\ub978 \uc2dc\uc791</h2>\n");
        b.append("<p class=\"sub\">\uac8c\uc774\ud2b8\uc6e8\uc774 \uc5d4\ub4dc\ud3ec\uc778\ud2b8\ub294 \uacf5\uac1c API\uc785\ub2c8\ub2e4. \ucee8\ud2b8\ub864 \ud50c\ub808\uc778 API\ub294 \ucf58\uc194 \ub85c\uadf8\uc778\uc774 \ud544\uc694\ud569\ub2c8\ub2e4.</p>\n");
        b.append("<div class=\"ep-grid\">\n");
        endpoint(b, "\ub77c\uc6b0\ud305", "POST", cp + "/gateway/route");
        endpoint(b, "\ucd94\ub860", "POST", cp + "/gateway/infer");
        endpoint(b, "\uc2a4\ud2b8\ub9ac\ubc0d", "GET", cp + "/gateway/stream");
        endpoint(b, "\ubaa8\ub378 \ubaa9\ub85d", "GET", cp + "/api/models");
        endpoint(b, "\uc0ac\uc6a9\ub7c9", "GET", cp + "/api/usage");
        endpoint(b, "API \ud638\ucd9c", "POST", cp + "/invoke/{model}");
        endpoint(b, "\uacfc\uae08", "GET", cp + "/api/billing");
        endpoint(b, "Chat (OpenAI)", "POST", cp + "/v1/chat/completions");
        endpoint(b, "Completions", "POST", cp + "/v1/completions");
        endpoint(b, "\uc559\uc0c1\ube14", "POST", cp + "/gateway/ensemble");
        endpoint(b, "\ud14c\ub10c\ud2b8", "GET", cp + "/api/tenants");
        endpoint(b, "\ud504\ub85c\ubc14\uc774\ub354", "GET", cp + "/api/providers");
        b.append("</div>\n");
        b.append("<pre class=\"code\">curl -X POST ").append(cp).append("/gateway/infer \\\n");
        b.append("  -H \"Content-Type: application/json\" \\\n");
        b.append("  -d '{\"requestType\":\"AUTO\",\"sessionId\":\"demo\",\"prompt\":\"\ubaa8\ubc14\uc77c \uace0\uac1d \ucd94\ucc9c \uc0c1\ud488 3\uac1c\"}'</pre>\n");
        b.append("</div>\n</div>\n");

        // Full-width: Features
        b.append("<div class=\"card\">\n");
        b.append("<h2>\uc8fc\uc694 \uae30\ub2a5 \uc694\uc57d</h2>\n");
        b.append("<ul>\n");
        b.append("<li>\ud504\ub86c\ud504\ud2b8 \ub77c\uc6b0\ud305: \ud504\ub86c\ud504\ud2b8 \ubd84\uc11d \ud6c4 \uc801\ud569\ud55c \ubaa8\ub378\uc744 \uc790\ub3d9 \uc120\ud0dd</li>\n");
        b.append("<li>\ubaa8\ub378 \uc120\ud0dd: \ub77c\uc6b0\ud2b8 \uc815\ucc45, \uce74\ud14c\uace0\ub9ac \ub9e4\uce6d, \uae30\ubcf8 \uc804\ub7b5 \uae30\ubc18 \uc790\ub3d9 \uc120\ud0dd</li>\n");
        b.append("<li>\ucee8\ud14d\uc2a4\ud2b8 \uce90\uc2dc: \uc138\uc158\uacfc \ud504\ub86c\ud504\ud2b8 \ud551\uac70\ud504\ub9b0\ud2b8 \uae30\ubc18 \uce90\uc2dc \ud0a4 \uc0dd\uc131</li>\n");
        b.append("<li>\uc2a4\ud2b8\ub9ac\ubc0d: SSE \ud1a0\ud070 \uc2a4\ud2b8\ub9bc \uc9c0\uc6d0</li>\n");
        b.append("<li>OpenAI \ud638\ud658 \ud504\ub85d\uc2dc: /v1/chat/completions, /v1/completions \uc790\ub3d9 Failover \uc9c0\uc6d0</li>\n");
        b.append("<li>\uc559\uc0c1\ube14 \uc11c\ube59: /gateway/ensemble\uc5d0\uc11c \uba40\ud2f0 \ubaa8\ub378 \uacb0\uacfc \uacb0\ud569</li>\n");
        b.append("<li>\uba40\ud2f0 \ud14c\ub10c\ud2b8: \uc694\uccad \uc81c\ud55c, \ud1a0\ud070 \ucffc\ud130, API \ud0a4 \ubc1c\uae09 \uad00\ub9ac</li>\n");
        b.append("<li>\ud50c\ub7ec\uadf8\uc778: \ucd94\ub860 \uc694\uccad\uc758 \uc804\ucc98\ub9ac/\ud6c4\ucc98\ub9ac \ucee4\uc2a4\ud140 \ud50c\ub7ec\uadf8\uc778</li>\n");
        b.append("</ul>\n</div>\n");

        // Script
        b.append("<script>\n");
        b.append("fetch('").append(cp).append("/api-docs')\n");
        b.append("  .then(function(r){return r.json();})\n");
        b.append("  .then(function(s){document.getElementById('openapiSpec').textContent=JSON.stringify(s,null,2);})\n");
        b.append("  .catch(function(e){document.getElementById('openapiSpec').textContent='\uc2a4\ud399 \ub85c\ub4dc \uc2e4\ud328: '+e;});\n");
        b.append("</script>\n");
        b.append("</div>\n</body>\n</html>");
        resp.getWriter().write(b.toString());
    }

    private static void endpoint(StringBuilder b, String label, String method, String path) {
        b.append("<div class=\"ep\"><strong>").append(label).append("</strong><code>").append(method).append(" ").append(path).append("</code></div>\n");
    }
}
