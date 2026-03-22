package io.velo.was.aiplatform.servlet;

import io.velo.was.aiplatform.gateway.AiGatewayService;
import io.velo.was.aiplatform.gateway.AiGatewayServlet;
import io.velo.was.aiplatform.observability.AiPlatformUsageService;
import io.velo.was.aiplatform.registry.AiModelRegistryService;
import io.velo.was.config.ServerConfiguration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

public class AiPlatformDashboardServlet extends HttpServlet {

    private final ServerConfiguration configuration;

    public AiPlatformDashboardServlet(ServerConfiguration configuration) {
        this.configuration = configuration;
    }

    public AiPlatformDashboardServlet(ServerConfiguration configuration, AiModelRegistryService r, AiGatewayService g, AiPlatformUsageService u) {
        this(configuration);
    }

    public AiPlatformDashboardServlet(ServerConfiguration configuration, AiModelRegistryService r, AiGatewayService g, AiPlatformUsageService u, io.velo.was.aiplatform.tenant.AiTenantService t) {
        this(configuration);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        ServerConfiguration.Server server = configuration.getServer();
        ServerConfiguration.AiPlatform ai = server.getAiPlatform();
        String cp = req.getContextPath();

        StringBuilder b = new StringBuilder(48_000);

        // ===== TAB: overview — 대시보드 (트래킹 차트 포함) =====
        b.append("<div class=\"tab-panel active\" id=\"tab-overview\">\n");
        b.append("<div class=\"hero\"><div class=\"hero-eyebrow\">Velo AI Platform</div>");
        b.append("<h1>\ud1b5\ud569 AI \ud50c\ub7ab\ud3fc \ub300\uc2dc\ubcf4\ub4dc</h1>");
        b.append("<p>\uba40\ud2f0 LLM \ub77c\uc6b0\ud305, OpenAI \ud638\ud658 \ud504\ub85d\uc2dc, \uc7a5\uc560 \uc870\uce58, A/B \ud14c\uc2a4\ud2b8, \ube44\uc6a9 \ucd94\uc801, \ud14c\ub10c\ud2b8 \uad00\ub9ac\ub97c \uc124\uc815 \ud558\ub098\ub85c \uc81c\uc5b4\ud569\ub2c8\ub2e4.</p>");
        b.append("<div class=\"chips\">");
        chip(b, "\uc0c1\ud0dc: \uc815\uc0c1", true);
        chip(b, ai.getMode(), false);
        chip(b, "\ubaa8\ub378: " + ai.getServing().getModels().size() + "\uac1c", false);
        chip(b, "\uc815\ucc45: " + ai.getServing().getRoutePolicies().size() + "\uac1c", false);
        b.append("</div></div>\n");

        // Live metric cards (auto-refresh)
        b.append("<div class=\"metrics\" id=\"liveMetrics\">");
        metric(b, "\ucd1d \uc694\uccad", "0", "\ub77c\uc6b0\ud305 + \ucd94\ub860 + \uc2a4\ud2b8\ub9bc", "totalRequests");
        metric(b, "\uce90\uc2dc \uc801\uc911", "0", "\ucee8\ud14d\uc2a4\ud2b8 \uce90\uc2dc \ud788\ud2b8", "cacheHits");
        metric(b, "\ub4f1\ub85d \ubaa8\ub378", "0", "\ub808\uc9c0\uc2a4\ud2b8\ub9ac \ub4f1\ub85d \ubaa8\ub378 \uc218", "registeredModels");
        metric(b, "\ud65c\uc131 \ud14c\ub10c\ud2b8", "0", "\ud65c\uc131 \uc0c1\ud0dc \ud14c\ub10c\ud2b8", "activeTenants");
        b.append("</div>\n");

        // Traffic chart (CSS bars)
        b.append("<div class=\"card\">");
        b.append("<div class=\"card-header\">\ud2b8\ub798\ud53d \ud2b8\ub798\ud0b9</div>");
        b.append("<div class=\"card-desc\">\uc2e4\uc2dc\uac04 API \uc694\uccad \uc720\ud615\ubcc4 \ud2b8\ub798\ud53d (\uc790\ub3d9 \uc0c8\ub85c\uace0\uce68 5\ucd08)</div>");
        b.append("<div id=\"trafficChart\" class=\"chart-bars\"></div>");
        b.append("</div>\n");

        // Quick status JSON
        b.append("<div class=\"card\"><div class=\"card-header\">\ud50c\ub7ab\ud3fc \uc0c1\ud0dc JSON</div>");
        b.append("<div class=\"btns\"><button class=\"btn btn-secondary\" onclick=\"refreshOverview()\">\uc0c8\ub85c\uace0\uce68</button></div>");
        b.append("<pre class=\"json-box\" id=\"overviewJson\">\ub85c\ub529 \uc911...</pre></div>\n");
        b.append("</div>\n");

        // ===== TAB: usage — 사용량 =====
        b.append("<div class=\"tab-panel\" id=\"tab-usage\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\uc0ac\uc6a9\ub7c9 \ubc0f \ubbf8\ud130\ub9c1</div>");
        b.append("<div class=\"card-desc\">\uac8c\uc774\ud2b8\uc6e8\uc774 \ud2b8\ub798\ud53d, \ub808\uc9c0\uc2a4\ud2b8\ub9ac \ubcc0\uacbd, \ud1a0\ud070 \uc0ac\uc6a9\ub7c9, \ube44\uc6a9 \ucd94\uc801</div>");
        b.append("<div class=\"btns\"><button class=\"btn btn-primary\" onclick=\"refreshUsage()\">\uc0ac\uc6a9\ub7c9 \uc0c8\ub85c\uace0\uce68</button>");
        b.append("<button class=\"btn btn-secondary\" onclick=\"refreshBilling()\">\uacfc\uae08 \uc0c8\ub85c\uace0\uce68</button></div>");
        b.append("<pre class=\"json-box\" id=\"usageJson\">\ub85c\ub529 \uc911...</pre>");
        b.append("<pre class=\"json-box\" id=\"billingJson\">\uacfc\uae08 \ubbf8\ub9ac\ubcf4\uae30 \ub85c\ub529 \uc911...</pre>");
        b.append("</div>\n");
        b.append("<div class=\"card\"><div class=\"card-header\">API \uc5d4\ub4dc\ud3ec\uc778\ud2b8</div><div class=\"card-desc\">\ub3c5\ub9bd \ubaa8\ub4c8 \uc6b4\uc601 API \uacbd\ub85c</div>");
        b.append("<div class=\"info-list\">");
        info(b, "\uc804\uccb4 \uac1c\uc694", cp + "/api/overview", "\ud50c\ub7ab\ud3fc \uc0c1\ud0dc \uc694\uc57d");
        info(b, "\uc0ac\uc6a9\ub7c9 \uc870\ud68c", cp + "/api/usage", "\ud2b8\ub798\ud53d \uce74\uc6b4\ud130");
        info(b, "\uacfc\uae08 \uc870\ud68c", cp + "/api/billing", "\ube44\uc6a9 \ubbf8\ub9ac\ubcf4\uae30");
        info(b, "\uc124\uc815 \uc870\ud68c", cp + "/api/config", "\ud604\uc7ac \ud50c\ub7ab\ud3fc \uc124\uc815 JSON");
        b.append("</div></div>\n");
        b.append("</div>\n");

        // ===== TAB: serving — 서빙 설정 (읽기 전용 현재 설정) =====
        b.append("<div class=\"tab-panel\" id=\"tab-serving\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\uc11c\ube59 \uad6c\uc131</div><div class=\"card-desc\">\ub77c\uc6b0\ud305 \ubc0f \uc11c\ube59 \uc2a4\uc704\uce58 \uc124\uc815 (\uc124\uc815 \ud0ed\uc5d0\uc11c \ubcc0\uacbd \uac00\ub2a5)</div>");
        b.append("<div class=\"pills\">");
        b.append(AiPlatformLayout.featurePill("\ubaa8\ub378 \ub77c\uc6b0\ud130", ai.getServing().isModelRouterEnabled()));
        b.append(AiPlatformLayout.featurePill("A/B \ud14c\uc2a4\ud2b8", ai.getServing().isAbTestingEnabled()));
        b.append(AiPlatformLayout.featurePill("\uc790\ub3d9 \uc120\ud0dd", ai.getServing().isAutoModelSelectionEnabled()));
        b.append(AiPlatformLayout.featurePill("\uc559\uc0c1\ube14", ai.getServing().isEnsembleServingEnabled()));
        b.append(AiPlatformLayout.featurePill("Edge AI", ai.getServing().isEdgeAiEnabled()));
        b.append("</div>");
        b.append("<div class=\"info-list\">");
        info(b, "\ub77c\uc6b0\ud130 \ud0c0\uc784\uc544\uc6c3", ai.getServing().getRouterTimeoutMillis() + " ms", "\ub77c\uc6b0\ud305 \ud655\uc815 \uc804 \uc694\uccad \ubd84\uc11d \uc2dc\uac04");
        info(b, "\uae30\ubcf8 \ubaa8\ub378 \uc218", ai.getServing().getModels().stream().filter(ServerConfiguration.ModelProfile::isDefaultSelected).count() + "\uac1c", "\uc815\ucc45 \ud78c\ud2b8 \uc5c6\uc744 \ub54c \uae30\ubcf8 \ud504\ub85c\ud30c\uc77c");
        info(b, "\ub77c\uc6b0\ud2b8 \uc815\ucc45 \uc218", ai.getServing().getRoutePolicies().size() + "\uac1c", "\uce44\ud305, \ube44\uc804, \ucd94\ucc9c, \uae30\ubcf8 \ud2b8\ub798\ud53d \ucee4\ubc84");
        b.append("</div></div>\n");
        // models table
        b.append("<div class=\"card\"><div class=\"card-header\">\ub4f1\ub85d \ubaa8\ub378 \ubaa9\ub85d</div><div class=\"card-desc\">\uc11c\ubc84 YAML \uc124\uc815\uc5d0 \uc815\uc758\ub41c \ubaa8\ub378 \ud504\ub85c\ud30c\uc77c</div>");
        b.append("<div class=\"tbl-wrap\"><table><thead><tr><th>\ubaa8\ub378\uba85</th><th>\uce74\ud14c\uace0\ub9ac</th><th>\ud504\ub85c\ubc14\uc774\ub354</th><th>\ubc84\uc804</th><th>\uc9c0\uc5f0(ms)</th><th>\uc815\ud655\ub3c4</th><th>\uae30\ubcf8</th><th>\uc0c1\ud0dc</th></tr></thead><tbody>");
        for (ServerConfiguration.ModelProfile m : ai.getServing().getModels()) {
            b.append("<tr><td><strong>").append(h(m.getName())).append("</strong></td>")
                    .append("<td>").append(h(m.getCategory())).append("</td>")
                    .append("<td>").append(h(m.getProvider())).append("</td>")
                    .append("<td>").append(h(m.getVersion())).append("</td>")
                    .append("<td>").append(m.getLatencyMs()).append("</td>")
                    .append("<td>").append(m.getAccuracyScore()).append("/100</td>")
                    .append("<td>").append(m.isDefaultSelected() ? "\uc608" : "-").append("</td>")
                    .append("<td>").append(m.isEnabled() ? "<span class=\"status-on\">\ud65c\uc131</span>" : "<span class=\"status-off\">\ube44\ud65c\uc131</span>").append("</td></tr>");
        }
        b.append("</tbody></table></div></div>\n");
        // route policies
        b.append("<div class=\"card\"><div class=\"card-header\">\ub77c\uc6b0\ud2b8 \uc815\ucc45</div><div class=\"card-desc\">\uc694\uccad \uc720\ud615\ubcc4 \ubaa8\ub378 \ub77c\uc6b0\ud305 \uc815\ucc45</div>");
        b.append("<div class=\"tbl-wrap\"><table><thead><tr><th>\uc815\ucc45\uba85</th><th>\uc694\uccad \uc720\ud615</th><th>\ub300\uc0c1 \ubaa8\ub378</th><th>\uc6b0\uc120\uc21c\uc704</th></tr></thead><tbody>");
        for (ServerConfiguration.RoutePolicy p : ai.getServing().getRoutePolicies()) {
            b.append("<tr><td>").append(h(p.getName())).append("</td><td>").append(h(p.getRequestType())).append("</td><td>").append(h(p.getTargetModel())).append("</td><td>").append(p.getPriority()).append("</td></tr>");
        }
        b.append("</tbody></table></div></div>\n");
        b.append("</div>\n");

        // ===== TAB: registry — 모델 레지스트리 CRUD =====
        b.append("<div class=\"tab-panel\" id=\"tab-registry\">\n");
        // guide
        b.append("<div class=\"card guide-card\">");
        b.append("<div class=\"card-header\">\ud83d\udca1 \ubaa8\ub378 \ub808\uc9c0\uc2a4\ud2b8\ub9ac \uc0ac\uc6a9 \ubc29\ubc95</div>");
        b.append("<div class=\"card-desc\" style=\"line-height:1.8;\">");
        b.append("<strong>\u2460 \ubaa8\ub378 \ub4f1\ub85d:</strong> \ubaa8\ub378\uba85, \uce74\ud14c\uace0\ub9ac, \ud504\ub85c\ubc14\uc774\ub354, \ubc84\uc804\uc744 \uc785\ub825\ud558\uace0 <strong>[\ubc84\uc804 \ub4f1\ub85d]</strong> \ud074\ub9ad<br>");
        b.append("<strong>\u2461 \uc0c1\ud0dc \ubcc0\uacbd:</strong> \ub4f1\ub85d\ub41c \ubaa8\ub378\uc758 \ubc84\uc804\uc744 ACTIVE \ub610\ub294 CANARY\ub85c \ubcc0\uacbd<br>");
        b.append("<strong>\u2462 \ubaa8\ub378 \uc0ad\uc81c:</strong> \ub808\uc9c0\uc2a4\ud2b8\ub9ac\uc5d0\uc11c \ubaa8\ub378\uc744 \uc81c\uac70<br>");
        b.append("<strong>\ucc38\uace0:</strong> YAML\uc5d0 \uc815\uc758\ub41c \ubaa8\ub378\uc740 \uc11c\ubc84 \uc2dc\uc791 \uc2dc \uc790\ub3d9 \ub4f1\ub85d\ub429\ub2c8\ub2e4. \uc5ec\uae30\uc11c\ub294 \ub7f0\ud0c0\uc784 \ucd94\uac00/\ubcc0\uacbd/\uc0ad\uc81c\uac00 \uac00\ub2a5\ud569\ub2c8\ub2e4.");
        b.append("</div></div>\n");
        // form
        String defModelName = ai.getServing().getModels().isEmpty() ? "my-model" : ai.getServing().getModels().get(0).getName();
        String defModelVer = ai.getServing().getModels().isEmpty() ? "v1" : ai.getServing().getModels().get(0).getVersion();
        b.append("<div class=\"card\"><div class=\"card-header\">\ubaa8\ub378 \ub808\uc9c0\uc2a4\ud2b8\ub9ac CRUD</div>");
        b.append("<div class=\"form-grid cols-3\">");
        input(b, "registryModelName", defModelName, "\ubaa8\ub378\uba85 *");
        input(b, "registryCategory", "LLM", "\uce74\ud14c\uace0\ub9ac (LLM/CV/REC)");
        input(b, "registryProvider", "openai", "\ud504\ub85c\ubc14\uc774\ub354 (openai/anthropic/ollama)");
        input(b, "registryVersion", defModelVer, "\ubc84\uc804 *");
        input(b, "registryLatency", "210", "\uc9c0\uc5f0(ms)");
        input(b, "registryAccuracy", "89", "\uc815\ud655\ub3c4 (0-100)");
        b.append("</div>");
        b.append("<div class=\"form-grid\"><select id=\"registryStatus\" class=\"form-select\"><option value=\"ACTIVE\">ACTIVE (\ud65c\uc131)</option><option value=\"CANARY\">CANARY (\uce74\ub098\ub9ac)</option><option value=\"INACTIVE\">INACTIVE (\ube44\ud65c\uc131)</option></select></div>");
        b.append("<div class=\"btns\">");
        b.append("<button class=\"btn btn-primary\" onclick=\"registerRegistryModel()\">\ubc84\uc804 \ub4f1\ub85d</button>");
        b.append("<button class=\"btn btn-secondary\" onclick=\"updateRegistryStatus('ACTIVE')\">ACTIVE \uc2b9\uaca9</button>");
        b.append("<button class=\"btn btn-secondary\" onclick=\"updateRegistryStatus('CANARY')\">CANARY \uc9c0\uc815</button>");
        b.append("<button class=\"btn btn-danger\" onclick=\"deleteRegistryModel()\">\ubaa8\ub378 \uc0ad\uc81c</button>");
        b.append("<button class=\"btn btn-secondary\" onclick=\"refreshRegistry()\">\uc0c8\ub85c\uace0\uce68</button>");
        b.append("</div>");
        b.append("<pre class=\"json-box\" id=\"registryMutationOutput\">\ubaa8\ub378\uc744 \ub4f1\ub85d/\ubcc0\uacbd/\uc0ad\uc81c\ud558\uba74 \uacb0\uacfc\uac00 \uc5ec\uae30\uc5d0 \ud45c\uc2dc\ub429\ub2c8\ub2e4.</pre>");
        b.append("<pre class=\"json-box\" id=\"registryJson\">\ub85c\ub529 \uc911...</pre>");
        b.append("</div>\n");
        b.append("</div>\n");

        // ===== TAB: providers — 프로바이더 연동 =====
        b.append("<div class=\"tab-panel\" id=\"tab-providers\">\n");
        b.append("<div class=\"card guide-card\">");
        b.append("<div class=\"card-header\">\ud83d\udca1 AI \ud504\ub85c\ubc14\uc774\ub354 \uc5f0\ub3d9 \ubc29\ubc95</div>");
        b.append("<div class=\"card-desc\" style=\"line-height:1.8;\">");
        b.append("<strong>\u2460 \uc124\uc815 \uc704\uce58:</strong> <code>velo.yaml</code>\uc758 <code>server.aiPlatform.serving.models[]</code> \ubc30\uc5f4\uc5d0 \ubaa8\ub378 \ud504\ub85c\ud30c\uc77c \ucd94\uac00<br>");
        b.append("<strong>\u2461 \ud504\ub85c\ubc14\uc774\ub354 \uc9c0\uc815:</strong> <code>provider</code> \ud544\ub4dc\ub97c <code>openai</code>, <code>anthropic</code>, <code>ollama</code>, <code>vllm</code>, <code>sglang</code> \uc911 \ud558\ub098\ub85c \uc9c0\uc815<br>");
        b.append("<strong>\u2462 API \ud0a4 \uc124\uc815:</strong> \ud658\uacbd \ubcc0\uc218\ub85c \uc124\uc815 (<code>OPENAI_API_KEY</code>, <code>ANTHROPIC_API_KEY</code> \ub4f1)<br>");
        b.append("<strong>\u2463 \ub7f0\ud0c0\uc784 \ucd94\uac00:</strong> \uc704\uc758 <strong>\ubaa8\ub378 \ub808\uc9c0\uc2a4\ud2b8\ub9ac</strong> \ud0ed\uc5d0\uc11c \uc11c\ubc84 \uc7ac\uc2dc\uc791 \uc5c6\uc774 \ubaa8\ub378\uc744 \ucd94\uac00\ud560 \uc218\ub3c4 \uc788\uc2b5\ub2c8\ub2e4.<br>");
        b.append("<strong>OpenAI \ud638\ud658 \ud504\ub85d\uc2dc:</strong> \ubaa8\ub4e0 \ud504\ub85c\ubc14\uc774\ub354\ub97c <code>/v1/chat/completions</code> \uc5d4\ub4dc\ud3ec\uc778\ud2b8 \ud558\ub098\ub85c \ud1b5\ud569 \uc811\uc18d \uac00\ub2a5");
        b.append("</div></div>\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\uba40\ud2f0 LLM \ud504\ub85c\ubc14\uc774\ub354 \ud604\ud669</div><div class=\"card-desc\">\uc9c0\uc6d0 \ud504\ub85c\ubc14\uc774\ub354 \ubc0f \uc5f0\ub3d9 \uc0c1\ud0dc</div>");
        b.append("<div class=\"tbl-wrap\"><table><thead><tr><th>\ud504\ub85c\ubc14\uc774\ub354</th><th>\ud504\ub85c\ud1a0\ucf5c</th><th>\ub300\ud45c \ubaa8\ub378</th><th>Failover</th><th>\ub85c\ub4dc\ubc38\ub7f0\uc2f1</th><th>\uc0c1\ud0dc</th></tr></thead><tbody>");
        provRow(b, "OpenAI", "REST / SSE", "GPT-4o, GPT-4o-mini", true, true, true);
        provRow(b, "Anthropic", "REST / SSE", "Claude Opus, Sonnet", true, true, true);
        provRow(b, "vLLM", "OpenAI \ud638\ud658", "\ucee4\uc2a4\ud140 \ud30c\uc778\ud29c\ub2dd \ubaa8\ub378", true, false, ai.getServing().isModelRouterEnabled());
        provRow(b, "SGLang", "OpenAI \ud638\ud658", "\ucee4\uc2a4\ud140 \ud30c\uc778\ud29c\ub2dd \ubaa8\ub378", true, false, ai.getServing().isModelRouterEnabled());
        provRow(b, "Ollama", "REST", "Llama, Mistral, Phi", true, false, ai.getServing().isEdgeAiEnabled());
        b.append("</tbody></table></div>");
        b.append("</div>\n");
        // Quick start code
        b.append("<div class=\"card\"><div class=\"card-header\">\uc5f0\ub3d9 \ube60\ub978 \uc2dc\uc791</div><div class=\"card-desc\">OpenAI SDK\ub85c Velo AI \uac8c\uc774\ud2b8\uc6e8\uc774 \uc811\uc18d</div>");
        b.append("<pre class=\"code-box\">");
        b.append(h("# Python (OpenAI SDK)\nfrom openai import OpenAI\n\nclient = OpenAI(\n    base_url=\"http://localhost:8080" + ai.getConsole().getContextPath() + "/v1\",\n    api_key=\"velo-demo-key\"  # 테넌트 탭에서 API 키 발급\n)\n\nresponse = client.chat.completions.create(\n    model=\"llm-general\",\n    messages=[{\"role\": \"user\", \"content\": \"안녕하세요\"}]\n)\n\n# curl\ncurl -X POST " + ai.getConsole().getContextPath() + "/v1/chat/completions \\\n  -H \"Authorization: Bearer velo-demo-key\" \\\n  -H \"Content-Type: application/json\" \\\n  -d '{\"messages\":[{\"role\":\"user\",\"content\":\"안녕하세요\"}]}'"));
        b.append("</pre></div>\n");
        b.append("</div>\n");

        // ===== TAB: intent — 의도 기반 라우팅 =====
        b.append("<div class=\"tab-panel\" id=\"tab-intent\">\n");
        b.append("<div class=\"card guide-card\">");
        b.append("<div class=\"card-header\">\ud83c\udfaf \uc758\ub3c4 \uae30\ubc18 \ub77c\uc6b0\ud305 (Intent-Based Routing)</div>");
        b.append("<div class=\"card-desc\" style=\"line-height:1.8;\">");
        b.append("<strong>\uac1c\ub150:</strong> \ud504\ub86c\ud504\ud2b8\uc5d0\uc11c \ud0a4\uc6cc\ub4dc\ub97c \ucd94\ucd9c\ud558\uc5ec \uc758\ub3c4\ub97c \ud30c\uc545\ud558\uace0, \uc758\ub3c4\uc5d0 \ub9de\ub294 \ucd5c\uc801 \ubaa8\ub378\ub85c \ub77c\uc6b0\ud305<br>");
        b.append("<strong>\ucc98\ub9ac \ud750\ub984:</strong> \uc694\uccad \uc218\uc2e0 \u2192 \ud14d\uc2a4\ud2b8 \uc815\uaddc\ud654 \u2192 \ud0a4\uc6cc\ub4dc \ud0d0\uc9c0 \u2192 \uc758\ub3c4 \uacb0\uc815 \u2192 \uc815\ucc45 \uc870\ud68c \u2192 \ub77c\uc6b0\ud305 \uc2e4\ud589<br>");
        b.append("<strong>\uc774\uc810:</strong> \uc694\uc57d\u2192long-context \ubaa8\ub378, \ucf54\ub4dc\u2192\ucf54\ub4dc \ud2b9\ud654 \ubaa8\ub378, \ubc88\uc5ed\u2192\uacbd\ub7c9 \ubc88\uc5ed \ubaa8\ub378 \ub4f1 \uc758\ub3c4\ubcc4 \ucd5c\uc801 \ubaa8\ub378 \uc120\ud0dd");
        b.append("</div></div>\n");
        // 테스트 폼
        b.append("<div class=\"card\"><div class=\"card-header\">\uc758\ub3c4 \ub77c\uc6b0\ud305 \ud14c\uc2a4\ud2b8</div>");
        b.append("<div class=\"card-desc\">\ud504\ub86c\ud504\ud2b8\ub97c \uc785\ub825\ud558\uba74 \ud0a4\uc6cc\ub4dc \ubd84\uc11d \u2192 \uc758\ub3c4 \ud30c\uc545 \u2192 \ub77c\uc6b0\ud305 \uacb0\uc815 \uacfc\uc815\uc744 \ud655\uc778</div>");
        b.append("<div class=\"form-grid cols-2\">");
        b.append("<textarea id=\"intentPrompt\" class=\"form-textarea\" style=\"min-height:60px;\">\uc774\ubc88 \ub2ec \ub9e4\ucd9c \ubcf4\uace0\uc11c\ub97c \uc694\uc57d\ud574 \uc8fc\uc138\uc694</textarea>");
        input(b, "intentTenantId", "", "\ud14c\ub10c\ud2b8 ID (\uc120\ud0dd)");
        b.append("</div>");
        b.append("<div class=\"btns\">");
        b.append("<button class=\"btn btn-primary\" onclick=\"testIntentRoute()\">\uc758\ub3c4 \ub77c\uc6b0\ud305 \ud14c\uc2a4\ud2b8</button>");
        b.append("<button class=\"btn btn-secondary\" onclick=\"previewIntent()\">\ud0a4\uc6cc\ub4dc \ubd84\uc11d \ubbf8\ub9ac\ubcf4\uae30</button>");
        b.append("</div>");
        b.append("<pre class=\"json-box\" id=\"intentResult\">\ud504\ub86c\ud504\ud2b8\ub97c \uc785\ub825\ud558\uace0 \ud14c\uc2a4\ud2b8\ud558\uba74 \ub77c\uc6b0\ud305 \uacb0\uacfc\uac00 \ud45c\uc2dc\ub429\ub2c8\ub2e4.</pre></div>\n");
        // 키워드 관리
        b.append("<div class=\"card\"><div class=\"card-header\">\ud0a4\uc6cc\ub4dc \uad00\ub9ac</div>");
        b.append("<div class=\"card-desc\">\uc758\ub3c4 \ubd84\ub958\uc6a9 \ud0a4\uc6cc\ub4dc \ub4f1\ub85d/\uc870\ud68c. \ub3d9\uc758\uc5b4\ub294 \uc27c\ud45c(,)\ub85c \uad6c\ubd84</div>");
        b.append("<div class=\"form-grid cols-3\">");
        input(b, "kwPrimary", "", "\uc8fc \ud0a4\uc6cc\ub4dc *");
        input(b, "kwSynonyms", "", "\ub3d9\uc758\uc5b4 (\uc27c\ud45c \uad6c\ubd84)");
        b.append("<select id=\"kwIntent\" class=\"form-select\">");
        b.append("<option value=\"SUMMARIZATION\">\uc694\uc57d</option><option value=\"GENERATION\">\uc0dd\uc131</option>");
        b.append("<option value=\"CODE\">\ucf54\ub4dc</option><option value=\"CLASSIFICATION\">\ubd84\ub958</option>");
        b.append("<option value=\"EXTRACTION\">\ucd94\ucd9c</option><option value=\"SEARCH\">\uac80\uc0c9</option>");
        b.append("<option value=\"VALIDATION\">\uac80\uc99d</option><option value=\"TRANSLATION\">\ubc88\uc5ed</option>");
        b.append("<option value=\"CONVERSATION\">\ub300\ud654</option><option value=\"GENERAL\">\uc77c\ubc18</option></select>");
        input(b, "kwPriority", "50", "\uc6b0\uc120\uc21c\uc704 (0-100)");
        b.append("</div>");
        b.append("<div class=\"btns\">");
        b.append("<button class=\"btn btn-primary\" onclick=\"addKeyword()\">\ud0a4\uc6cc\ub4dc \ub4f1\ub85d</button>");
        b.append("<button class=\"btn btn-secondary\" onclick=\"refreshKeywords()\">\ud0a4\uc6cc\ub4dc \uc0c8\ub85c\uace0\uce68</button>");
        b.append("</div>");
        b.append("<pre class=\"json-box\" id=\"keywordResult\">\ud0a4\uc6cc\ub4dc \ub4f1\ub85d \uacb0\uacfc</pre>");
        b.append("<pre class=\"json-box\" id=\"keywordsJson\">\ub85c\ub529 \uc911...</pre></div>\n");
        // 정책 & 감사 로그
        b.append("<div class=\"card\"><div class=\"card-header\">\ub77c\uc6b0\ud305 \uc815\ucc45 \ubc0f \uac10\uc0ac \ub85c\uadf8</div>");
        b.append("<div class=\"btns\">");
        b.append("<button class=\"btn btn-secondary\" onclick=\"refreshPolicies()\">\uc815\ucc45 \uc0c8\ub85c\uace0\uce68</button>");
        b.append("<button class=\"btn btn-primary\" onclick=\"refreshIntentStats()\">\ud1b5\uacc4</button>");
        b.append("<button class=\"btn btn-secondary\" onclick=\"refreshAuditLog()\">\uac10\uc0ac \ub85c\uadf8</button>");
        b.append("</div>");
        b.append("<pre class=\"json-box\" id=\"policiesJson\">\ub85c\ub529 \uc911...</pre>");
        b.append("<pre class=\"json-box\" id=\"intentStatsJson\">\ud1b5\uacc4 \ub85c\ub529 \uc911...</pre>");
        b.append("<pre class=\"json-box\" id=\"auditLogJson\">\uac10\uc0ac \ub85c\uadf8 \ub85c\ub529 \uc911...</pre>");
        b.append("</div>\n");
        b.append("</div>\n");

        // ===== TAB: sandbox — 게이트웨이 테스트 =====
        b.append("<div class=\"tab-panel\" id=\"tab-sandbox\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\uac8c\uc774\ud2b8\uc6e8\uc774 \ud14c\uc2a4\ud2b8</div><div class=\"card-desc\">\ub77c\uc6b0\ud305/\ucd94\ub860/\uc559\uc0c1\ube14/\uc2a4\ud2b8\ub9ac\ubc0d \ub3d9\uc791\uc744 \uc9c1\uc811 \ud655\uc778\ud569\ub2c8\ub2e4.</div>");
        b.append("<div class=\"form-grid cols-2\">");
        b.append("<select id=\"gatewayType\" class=\"form-select\"><option value=\"AUTO\">\uc790\ub3d9 (AUTO)</option><option value=\"CHAT\">\ucc44\ud305 (CHAT)</option><option value=\"VISION\">\ube44\uc804 (VISION)</option><option value=\"RECOMMENDATION\">\ucd94\ucc9c (RECOMMENDATION)</option></select>");
        input(b, "gatewaySession", "console-demo", "\uc138\uc158 ID");
        b.append("</div>");
        b.append("<textarea id=\"gatewayPrompt\" class=\"form-textarea\">\uc2e0\uaddc \ubaa8\ubc14\uc77c \uace0\uac1d\uc5d0\uac8c \ucd94\ucc9c\ud560 \uc2a4\ud0c0\ud130 \uc0c1\ud488 3\uac1c\ub97c \uc81c\uc548\ud574 \uc8fc\uc138\uc694.</textarea>");
        b.append("<div class=\"btns\">");
        b.append("<button class=\"btn btn-primary\" onclick=\"callGateway('route')\">\ub77c\uc6b0\ud305</button>");
        b.append("<button class=\"btn btn-primary\" onclick=\"callGateway('infer')\">\ucd94\ub860</button>");
        b.append("<button class=\"btn btn-primary\" onclick=\"callGateway('ensemble')\">\uc559\uc0c1\ube14</button>");
        b.append("<button class=\"btn btn-secondary\" onclick=\"openGatewayStream()\">\uc2a4\ud2b8\ub9ac\ubc0d</button>");
        b.append("</div>");
        b.append("<pre class=\"json-box\" id=\"gatewayOutput\">\ub77c\uc6b0\ud305 \ub610\ub294 \ucd94\ub860\uc744 \uc2e4\ud589\ud558\uba74 \uacb0\uacfc\uac00 \uc5ec\uae30\uc5d0 \ud45c\uc2dc\ub429\ub2c8\ub2e4.</pre>");
        b.append("</div>\n");
        b.append("</div>\n");

        // ===== TAB: tenants — 테넌트 CRUD =====
        b.append("<div class=\"tab-panel\" id=\"tab-tenants\">\n");
        b.append("<div class=\"card guide-card\">");
        b.append("<div class=\"card-header\">\ud83d\udca1 \ud14c\ub10c\ud2b8 \uad00\ub9ac \uc0ac\uc6a9 \ubc29\ubc95</div>");
        b.append("<div class=\"card-desc\" style=\"line-height:1.8;\">");
        b.append("<strong>\u2460 \ud14c\ub10c\ud2b8 \ub4f1\ub85d:</strong> ID/\uc774\ub984/\uc694\uae08\uc81c \uc785\ub825 \ud6c4 <strong>[\ud14c\ub10c\ud2b8 \ub4f1\ub85d]</strong> \ud074\ub9ad<br>");
        b.append("<strong>\u2461 API \ud0a4 \ubc1c\uae09:</strong> \ub4f1\ub85d\ub41c \ud14c\ub10c\ud2b8 ID \uc785\ub825 \ud6c4 <strong>[API \ud0a4 \ubc1c\uae09]</strong> \ud074\ub9ad<br>");
        b.append("<strong>\u2462 \ud14c\ub10c\ud2b8 \uc0ad\uc81c:</strong> \ud14c\ub10c\ud2b8 ID \uc785\ub825 \ud6c4 <strong>[\ud14c\ub10c\ud2b8 \uc0ad\uc81c]</strong> \ud074\ub9ad<br>");
        b.append("<strong>\ucc38\uace0:</strong> \uc11c\ubc84 \uc2dc\uc791 \uc2dc <code>tenant-demo</code> + <code>velo-demo-key</code>\uac00 \uc790\ub3d9 \uc0dd\uc131\ub429\ub2c8\ub2e4.");
        b.append("</div></div>\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\ud14c\ub10c\ud2b8 CRUD</div>");
        b.append("<div class=\"form-grid cols-3\">");
        input(b, "tenantId", "tenant-demo", "\ud14c\ub10c\ud2b8 ID *");
        input(b, "tenantDisplayName", "\ub370\ubaa8 \ud14c\ub10c\ud2b8", "\ud45c\uc2dc \uc774\ub984");
        b.append("<select id=\"tenantPlan\" class=\"form-select\"><option value=\"starter\">Starter</option><option value=\"pro\">Pro</option><option value=\"enterprise\">Enterprise</option></select>");
        input(b, "tenantRateLimit", "120", "\uc694\uccad \uc81c\ud55c(\ubd84\ub2f9)");
        input(b, "tenantTokenQuota", "250000", "\ud1a0\ud070 \ucffc\ud130");
        input(b, "tenantKeyLabel", "default", "API \ud0a4 \ub77c\ubca8");
        b.append("</div>");
        b.append("<div class=\"btns\">");
        b.append("<button class=\"btn btn-primary\" onclick=\"registerTenant()\">\ud14c\ub10c\ud2b8 \ub4f1\ub85d</button>");
        b.append("<button class=\"btn btn-primary\" onclick=\"issueApiKey()\">API \ud0a4 \ubc1c\uae09</button>");
        b.append("<button class=\"btn btn-danger\" onclick=\"deleteTenant()\">\ud14c\ub10c\ud2b8 \uc0ad\uc81c</button>");
        b.append("<button class=\"btn btn-secondary\" onclick=\"refreshTenants()\">\uc0c8\ub85c\uace0\uce68</button>");
        b.append("</div>");
        b.append("<pre class=\"json-box\" id=\"tenantMutationOutput\">\ud14c\ub10c\ud2b8 \ub4f1\ub85d/\ud0a4\ubc1c\uae09/\uc0ad\uc81c \uacb0\uacfc\uac00 \uc5ec\uae30\uc5d0 \ud45c\uc2dc\ub429\ub2c8\ub2e4.</pre>");
        b.append("<pre class=\"json-box\" id=\"tenantJson\">\ub85c\ub529 \uc911...</pre>");
        b.append("</div>\n");
        b.append("</div>\n");

        // ===== TAB: published — API 발행 =====
        b.append("<div class=\"tab-panel\" id=\"tab-published\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">API \uc790\ub3d9 \ubc1c\ud589</div><div class=\"card-desc\">\ud65c\uc131 \ubaa8\ub378 \ub808\uc9c0\uc2a4\ud2b8\ub9ac\uc5d0\uc11c \uc790\ub3d9 \uc0dd\uc131\ub41c REST \uc5d4\ub4dc\ud3ec\uc778\ud2b8</div>");
        b.append("<div class=\"info-list\">");
        info(b, "Invoke \uacbd\ub85c", cp + "/invoke/{model}", "\ubaa8\ub378\uba85\uc744 \uacbd\ub85c\uc5d0 \ud3ec\ud568\ud558\uc5ec \uc9c1\uc811 \ud638\ucd9c");
        info(b, "\ubc1c\ud589 API \ubaa9\ub85d", cp + "/api/published-apis", "\ud65c\uc131\ub41c \uc5d4\ub4dc\ud3ec\uc778\ud2b8 JSON \uc751\ub2f5");
        b.append("</div>");
        b.append("<div class=\"form-grid cols-2\">");
        input(b, "publishedModelName", "llm-general", "\ubaa8\ub378\uba85");
        b.append("<textarea id=\"publishedPrompt\" class=\"form-textarea\" style=\"min-height:60px;\">\uc2e0\uaddc \uae30\uc5c5 \uace0\uac1d\uc744 \uc704\ud55c \uc628\ubcf4\ub529 \uac00\uc774\ub4dc\ub97c \uc694\uc57d\ud574 \uc8fc\uc138\uc694.</textarea>");
        b.append("</div>");
        b.append("<div class=\"btns\"><button class=\"btn btn-primary\" onclick=\"invokePublishedModel()\">API \ud638\ucd9c</button>");
        b.append("<button class=\"btn btn-secondary\" onclick=\"refreshPublishedApis()\">\ubc1c\ud589 API \uc0c8\ub85c\uace0\uce68</button></div>");
        b.append("<pre class=\"json-box\" id=\"publishedMutationOutput\">\ubaa8\ub378 API\ub97c \ud638\ucd9c\ud558\uba74 \uacb0\uacfc\uac00 \uc5ec\uae30\uc5d0 \ud45c\uc2dc\ub429\ub2c8\ub2e4.</pre>");
        b.append("<pre class=\"json-box\" id=\"publishedJson\">\ub85c\ub529 \uc911...</pre>");
        b.append("</div>\n");
        b.append("</div>\n");

        // ===== TAB: developer — 개발자 포탈 =====
        b.append("<div class=\"tab-panel\" id=\"tab-developer\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\uac1c\ubc1c\uc790 \ud3ec\ud138</div><div class=\"card-desc\">OpenAPI \ubb38\uc11c \ubc0f \uacf5\uac1c \uac8c\uc774\ud2b8\uc6e8\uc774 \uc9c4\uc785\uc810</div>");
        b.append("<div class=\"pills\">");
        b.append(AiPlatformLayout.featurePill("\ud504\ub86c\ud504\ud2b8 \ub77c\uc6b0\ud305", ai.getAdvanced().isPromptRoutingEnabled()));
        b.append(AiPlatformLayout.featurePill("\ucee8\ud14d\uc2a4\ud2b8 \uce90\uc2dc", ai.getAdvanced().isContextCacheEnabled()));
        b.append(AiPlatformLayout.featurePill("\uad00\uce21\uc131", ai.getAdvanced().isObservabilityEnabled()));
        b.append("</div>");
        b.append("<div class=\"info-list\">");
        info(b, "OpenAPI \uc2a4\ud399", cp + "/api-docs", "API \uacc4\uc57d\uc11c JSON");
        info(b, "\uac1c\ubc1c\uc790 \ud3ec\ud138 UI", cp + "/api-docs/ui", "\ube60\ub978 \uc2dc\uc791, curl \uc608\uc81c");
        info(b, "\uc5d4\ub4dc\ud3ec\uc778\ud2b8 \ubaa9\ub85d", cp + "/gateway", "\uac8c\uc774\ud2b8\uc6e8\uc774 \uc11c\ube44\uc2a4 \ubaa9\ub85d");
        b.append("</div></div>\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\ucc28\ubcc4\ud654 \uae30\ub2a5</div><div class=\"card-desc\">AI \uc804\uc6a9 \ub7f0\ud0c0\uc784 \ud575\uc2ec \uc5ed\ub7c9</div>");
        b.append("<div class=\"pills\">");
        b.append(AiPlatformLayout.featurePill("AI \ucd5c\uc801\ud654 WAS", ai.getDifferentiation().isAiOptimizedWasEnabled()));
        b.append(AiPlatformLayout.featurePill("\uc694\uccad \ub77c\uc6b0\ud305", ai.getDifferentiation().isRequestRoutingEnabled()));
        b.append(AiPlatformLayout.featurePill("\uc2a4\ud2b8\ub9ac\ubc0d \uc751\ub2f5", ai.getDifferentiation().isStreamingResponseEnabled()));
        b.append(AiPlatformLayout.featurePill("\ud50c\ub7ec\uadf8\uc778", ai.getDifferentiation().isPluginFrameworkEnabled()));
        b.append("</div></div>\n");
        b.append("</div>\n");

        // ===== TAB: platform — 플랫폼 기능 =====
        b.append("<div class=\"tab-panel\" id=\"tab-platform\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\ud50c\ub7ab\ud3fc \uae30\ub2a5 \ud604\ud669</div><div class=\"card-desc\">\ud65c\uc131\ud654\ub41c \ud50c\ub7ab\ud3fc \uae30\ub2a5 \uc2a4\uc704\uce58</div>");
        b.append("<div class=\"pills\">");
        b.append(AiPlatformLayout.featurePill("\ubaa8\ub378 \ub4f1\ub85d", ai.getPlatform().isModelRegistrationEnabled()));
        b.append(AiPlatformLayout.featurePill("\uc790\ub3d9 API \uc0dd\uc131", ai.getPlatform().isAutoApiGenerationEnabled()));
        b.append(AiPlatformLayout.featurePill("\ubc84\uc804 \uad00\ub9ac", ai.getPlatform().isVersionManagementEnabled()));
        b.append(AiPlatformLayout.featurePill("\uacfc\uae08", ai.getPlatform().isBillingEnabled()));
        b.append(AiPlatformLayout.featurePill("\uac1c\ubc1c\uc790 \ud3ec\ud138", ai.getPlatform().isDeveloperPortalEnabled()));
        b.append(AiPlatformLayout.featurePill("\uba40\ud2f0 \ud14c\ub10c\ud2b8", ai.getPlatform().isMultiTenantEnabled()));
        b.append("</div>");
        b.append("<div class=\"info-list\">");
        info(b, "\ubc84\uc804 \uad00\ub9ac \uc804\ub7b5", ai.getPlatform().getVersioningStrategy(), "\uce74\ub098\ub9ac, \ube14\ub8e8-\uadf8\ub9b0 \ub610\ub294 \ub864\ub9c1 \ub9b4\ub9ac\uc2a4 \ubc29\uc2dd");
        info(b, "\uc218\uc775 \ubaa8\ub378", String.join(", ", ai.getCommercialization().getRevenueStreams()), "API \uacfc\uae08 \ubc0f SaaS \ud655\uc7a5");
        info(b, "\ucc28\ubcc4\ud654", String.join(", ", ai.getCommercialization().getDifferentiators()), "\ub77c\uc6b0\ud305, \uc800\uc9c0\uc5f0, \ucee4\uc2a4\ud130\ub9c8\uc774\uc9d5");
        b.append("</div></div>\n");
        b.append("</div>\n");

        // ===== TAB: config — 설정 조회/YAML 미리보기 =====
        b.append("<div class=\"tab-panel\" id=\"tab-config\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\ud604\uc7ac \uc124\uc815 (JSON)</div><div class=\"card-desc\">\uc11c\ubc84\uc5d0\uc11c \ub85c\ub4dc\ub41c \ud604\uc7ac AI \ud50c\ub7ab\ud3fc \uc124\uc815\uc744 API\ub85c \uc870\ud68c\ud569\ub2c8\ub2e4.</div>");
        b.append("<div class=\"btns\"><button class=\"btn btn-primary\" onclick=\"refreshConfig()\">\uc124\uc815 \ub85c\ub4dc</button></div>");
        b.append("<pre class=\"json-box\" id=\"configJson\">\ub85c\ub529 \uc911...</pre></div>\n");
        b.append("<div class=\"card\"><div class=\"card-header\">YAML \uc124\uc815 \ubbf8\ub9ac\ubcf4\uae30</div><div class=\"card-desc\">\ud604\uc7ac server.aiPlatform \uc124\uc815 \uc694\uc57d</div>");
        b.append("<pre class=\"code-box\">").append(h(buildYamlPreview(ai))).append("</pre></div>\n");
        b.append("</div>\n");

        // ===== TAB: plugins — 플러그인 =====
        b.append("<div class=\"tab-panel\" id=\"tab-plugins\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\ud50c\ub7ec\uadf8\uc778 \uad00\ub9ac</div>");
        b.append("<div class=\"card-desc\">\ub4f1\ub85d\ub41c \ucd94\ub860 \uc804\ucc98\ub9ac/\ud6c4\ucc98\ub9ac \ud50c\ub7ec\uadf8\uc778 \ubaa9\ub85d</div>");
        b.append("<div class=\"btns\">");
        b.append("<button class=\"btn btn-secondary\" onclick=\"refreshPlugins()\">\uc0c8\ub85c\uace0\uce68</button>");
        b.append("</div>");
        b.append("<pre class=\"json-box\" id=\"pluginsJson\">\ub85c\ub529 \uc911...</pre>");
        b.append("</div>\n");
        b.append("</div>\n");

        // ===== TAB: roadmap =====
        b.append("<div class=\"tab-panel\" id=\"tab-roadmap\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\uc9c4\ud654 \ub85c\ub4dc\ub9f5</div><div class=\"card-desc\">\uae30\ubcf8 \uc11c\ube59\uc5d0\uc11c \ud50c\ub7ab\ud3fc \uc0c1\uc6a9\ud654\uae4c\uc9c0</div>");
        b.append("<div class=\"timeline\">");
        for (ServerConfiguration.RoadmapStage st : ai.getRoadmap().getStages()) {
            boolean act = st.getStage() == ai.getRoadmap().getCurrentStage();
            b.append("<div class=\"tl-item").append(act ? " active" : "").append("\"><div class=\"tl-num\">").append(st.getStage()).append("</div>");
            b.append("<div class=\"tl-title\">").append(h(st.getGoal())).append("</div>");
            b.append("<div class=\"tl-note\">\ub2e8\uacc4 ").append(st.getStage()).append(act ? " \u2014 \ud604\uc7ac \uc9c4\ud589 \uc911" : "").append("</div>");
            b.append("<div class=\"tl-caps\">");
            for (String cap : st.getCapabilities()) b.append("<span>").append(h(cap)).append("</span>");
            b.append("</div></div>");
        }
        b.append("</div></div>\n");
        b.append("</div>\n");

        // ===== JavaScript =====
        b.append("<script>\n");
        b.append("const CP='").append(h(cp)).append("';\n");
        b.append("function showJson(id,t){const el=document.getElementById(id);try{const j=JSON.parse(t);if(j.error){el.textContent='\\u274c '+j.error;el.style.color='#dc2626';}else{el.textContent=JSON.stringify(j,null,2);el.style.color='';}}catch(e){el.textContent=t;el.style.color='';}}\n");
        b.append("async function api(path,opts){try{return await(await fetch(CP+path,opts)).text();}catch(e){return'{\"error\":\"'+e.message+'\"}';}}\n");
        // Refresh functions
        b.append("async function refreshOverview(){showJson('overviewJson',await api('/api/overview'))}\n");
        b.append("async function refreshRegistry(){showJson('registryJson',await api('/api/models'))}\n");
        b.append("async function refreshUsage(){showJson('usageJson',await api('/api/usage'))}\n");
        b.append("async function refreshPublishedApis(){showJson('publishedJson',await api('/api/published-apis'))}\n");
        b.append("async function refreshBilling(){showJson('billingJson',await api('/api/billing'))}\n");
        b.append("async function refreshTenants(){showJson('tenantJson',await api('/api/tenants'))}\n");
        b.append("async function refreshConfig(){showJson('configJson',await api('/api/config'))}\n");
        // Registry CRUD
        b.append("function registryPayload(){return{name:document.getElementById('registryModelName').value,category:document.getElementById('registryCategory').value,provider:document.getElementById('registryProvider').value,version:document.getElementById('registryVersion').value,latencyTier:'balanced',latencyMs:Number(document.getElementById('registryLatency').value||0),accuracyScore:Number(document.getElementById('registryAccuracy').value||0),defaultSelected:false,enabled:true,status:document.getElementById('registryStatus').value,source:'runtime'}}\n");
        b.append("async function registerRegistryModel(){showJson('registryMutationOutput',await api('/api/models',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(registryPayload())}));refreshRegistry();refreshOverview()}\n");
        b.append("async function updateRegistryStatus(s){const n=document.getElementById('registryModelName').value.trim(),v=document.getElementById('registryVersion').value.trim();if(!n||!v){showJson('registryMutationOutput','{\"error\":\"\\ubaa8\\ub378\\uba85\\uacfc \\ubc84\\uc804\\uc744 \\uc785\\ub825\\ud558\\uc138\\uc694.\"}');return;}showJson('registryMutationOutput',await api('/api/models/'+encodeURIComponent(n)+'/versions/'+encodeURIComponent(v)+'/status?state='+s,{method:'POST'}));refreshRegistry()}\n");
        b.append("async function deleteRegistryModel(){const n=document.getElementById('registryModelName').value.trim();if(!n){showJson('registryMutationOutput','{\"error\":\"\\uc0ad\\uc81c\\ud560 \\ubaa8\\ub378\\uba85\\uc744 \\uc785\\ub825\\ud558\\uc138\\uc694.\"}');return;}if(!confirm('\\ubaa8\\ub378 \"'+n+'\"\\uc744(\\ub97c) \\uc0ad\\uc81c\\ud558\\uc2dc\\uaca0\\uc2b5\\ub2c8\\uae4c?'))return;showJson('registryMutationOutput',await api('/api/models/'+encodeURIComponent(n),{method:'DELETE'}));refreshRegistry();refreshOverview()}\n");
        // Gateway test
        b.append("async function callGateway(m){const p={requestType:document.getElementById('gatewayType').value,prompt:document.getElementById('gatewayPrompt').value,sessionId:document.getElementById('gatewaySession').value};showJson('gatewayOutput',await api('/gateway/'+m,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(p)}))}\n");
        b.append("function openGatewayStream(){const t=encodeURIComponent(document.getElementById('gatewayType').value),s=encodeURIComponent(document.getElementById('gatewaySession').value),p=encodeURIComponent(document.getElementById('gatewayPrompt').value);window.open(CP+'/gateway/stream?requestType='+t+'&sessionId='+s+'&prompt='+p,'_blank')}\n");
        // Published API
        b.append("async function invokePublishedModel(){const m=encodeURIComponent(document.getElementById('publishedModelName').value),p=encodeURIComponent(document.getElementById('publishedPrompt').value);showJson('publishedMutationOutput',await api('/invoke/'+m+'?sessionId=published-demo&prompt='+p));refreshUsage()}\n");
        // Tenant CRUD
        b.append("async function registerTenant(){const p={tenantId:document.getElementById('tenantId').value,displayName:document.getElementById('tenantDisplayName').value,plan:document.getElementById('tenantPlan').value,rateLimitPerMinute:Number(document.getElementById('tenantRateLimit').value||120),tokenQuota:Number(document.getElementById('tenantTokenQuota').value||250000),active:true};showJson('tenantMutationOutput',await api('/api/tenants',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(p)}));refreshTenants()}\n");
        b.append("async function issueApiKey(){const t=document.getElementById('tenantId').value.trim();if(!t){showJson('tenantMutationOutput','{\"error\":\"\\ud14c\\ub10c\\ud2b8 ID\\ub97c \\uc785\\ub825\\ud558\\uc138\\uc694.\"}');return;}const l=encodeURIComponent(document.getElementById('tenantKeyLabel').value);showJson('tenantMutationOutput',await api('/api/tenants/'+encodeURIComponent(t)+'/keys?label='+l,{method:'POST'}));refreshTenants()}\n");
        b.append("async function deleteTenant(){const t=document.getElementById('tenantId').value.trim();if(!t){showJson('tenantMutationOutput','{\"error\":\"\\uc0ad\\uc81c\\ud560 \\ud14c\\ub10c\\ud2b8 ID\\ub97c \\uc785\\ub825\\ud558\\uc138\\uc694.\"}');return;}if(!confirm('\\ud14c\\ub10c\\ud2b8 \"'+t+'\"\\ub97c \\uc0ad\\uc81c\\ud558\\uc2dc\\uaca0\\uc2b5\\ub2c8\\uae4c?'))return;showJson('tenantMutationOutput',await api('/api/tenants/'+encodeURIComponent(t),{method:'DELETE'}));refreshTenants()}\n");

        // Live dashboard update
        b.append("let usageHistory=[];\n");
        b.append("async function updateLiveDashboard(){\n");
        b.append("  try{\n");
        b.append("    const u=JSON.parse(await api('/api/usage'));\n");
        b.append("    const o=JSON.parse(await api('/api/overview'));\n");
        b.append("    if(u.requests){document.getElementById('mv-totalRequests').textContent=(u.requests.routeCalls||0)+(u.requests.inferCalls||0)+(u.requests.streamCalls||0)+(u.requests.intentRouteCalls||0);}\n");
        b.append("    if(u.cache){document.getElementById('mv-cacheHits').textContent=u.cache.hits||0;}\n");
        b.append("    if(o.registry){document.getElementById('mv-registeredModels').textContent=o.registry.registeredModels||o.registry.totalModels||0;}\n");
        b.append("    if(o.tenancy){document.getElementById('mv-activeTenants').textContent=o.tenancy.activeTenants||0;}\n");
        b.append("    if(u.requests){\n");
        b.append("      usageHistory.push({route:(u.requests.routeCalls||0)+(u.requests.intentRouteCalls||0),infer:u.requests.inferCalls||0,stream:u.requests.streamCalls||0});\n");
        b.append("      if(usageHistory.length>12)usageHistory.shift();\n");
        b.append("      renderTrafficChart();\n");
        b.append("    }\n");
        b.append("  }catch(e){}\n");
        b.append("}\n");
        b.append("function renderTrafficChart(){\n");
        b.append("  const c=document.getElementById('trafficChart');if(!c)return;\n");
        b.append("  let max=1;usageHistory.forEach(h=>{const t=h.route+h.infer+h.stream;if(t>max)max=t;});\n");
        b.append("  let html='<div class=\"chart-legend\"><span class=\"cl-route\">\\ub77c\\uc6b0\\ud305</span><span class=\"cl-infer\">\\ucd94\\ub860</span><span class=\"cl-stream\">\\uc2a4\\ud2b8\\ub9bc</span></div><div class=\"chart-row\">';\n");
        b.append("  usageHistory.forEach((h,i)=>{\n");
        b.append("    const total=h.route+h.infer+h.stream;\n");
        b.append("    const pct=Math.max(4,Math.round(total/max*100));\n");
        b.append("    const rp=total?Math.round(h.route/total*100):33;\n");
        b.append("    const ip=total?Math.round(h.infer/total*100):33;\n");
        b.append("    html+='<div class=\"chart-bar\" style=\"height:'+pct+'px\" title=\"\\ucd1d '+total+'\\uac74\"><div class=\"cb-route\" style=\"height:'+rp+'%\"></div><div class=\"cb-infer\" style=\"height:'+ip+'%\"></div><div class=\"cb-stream\" style=\"height:'+(100-rp-ip)+'%\"></div></div>';\n");
        b.append("  });\n");
        b.append("  html+='</div>';c.innerHTML=html;\n");
        b.append("}\n");

        // Intent routing functions
        b.append("async function testIntentRoute(){const p=document.getElementById('intentPrompt').value,t=document.getElementById('intentTenantId').value;if(!p){showJson('intentResult','{\"error\":\"\\ud504\\ub86c\\ud504\\ud2b8\\ub97c \\uc785\\ub825\\ud558\\uc138\\uc694.\"}');return;}showJson('intentResult',await api('/api/intent/test',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({prompt:p,tenantId:t})}))}\n");
        b.append("async function previewIntent(){const p=document.getElementById('intentPrompt').value;if(!p){showJson('intentResult','{\"error\":\"\\ud504\\ub86c\\ud504\\ud2b8\\ub97c \\uc785\\ub825\\ud558\\uc138\\uc694.\"}');return;}showJson('intentResult',await api('/api/intent/preview',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({prompt:p})}))}\n");
        b.append("async function addKeyword(){const p={primaryKeyword:document.getElementById('kwPrimary').value,synonyms:document.getElementById('kwSynonyms').value,intent:document.getElementById('kwIntent').value,priority:Number(document.getElementById('kwPriority').value||50)};showJson('keywordResult',await api('/api/intent/keywords',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(p)}));refreshKeywords()}\n");
        b.append("async function refreshKeywords(){showJson('keywordsJson',await api('/api/intent/keywords'))}\n");
        b.append("async function refreshPolicies(){showJson('policiesJson',await api('/api/intent/policies'))}\n");
        b.append("async function refreshIntentStats(){showJson('intentStatsJson',await api('/api/intent/stats'))}\n");
        b.append("async function refreshAuditLog(){showJson('auditLogJson',await api('/api/intent/audit?limit=20'))}\n");

        // Plugin functions
        b.append("async function refreshPlugins(){showJson('pluginsJson',await api('/api/plugins'))}\n");
        // Intent keyword/policy delete functions
        b.append("async function deleteKeyword(id){if(!confirm('\\ud0a4\\uc6cc\\ub4dc '+id+'\\ub97c \\uc0ad\\uc81c\\ud558\\uc2dc\\uaca0\\uc2b5\\ub2c8\\uae4c?'))return;showJson('keywordResult',await api('/api/intent/keywords/'+encodeURIComponent(id),{method:'DELETE'}));refreshKeywords()}\n");
        b.append("async function deletePolicy(id){if(!confirm('\\uc815\\ucc45 '+id+'\\ub97c \\uc0ad\\uc81c\\ud558\\uc2dc\\uaca0\\uc2b5\\ub2c8\\uae4c?'))return;showJson('keywordResult',await api('/api/intent/policies/'+encodeURIComponent(id),{method:'DELETE'}));refreshPolicies()}\n");

        // Init
        b.append("refreshOverview();refreshRegistry();refreshUsage();refreshPublishedApis();refreshBilling();refreshTenants();refreshConfig();refreshKeywords();refreshPolicies();refreshIntentStats();refreshPlugins();\n");
        b.append("updateLiveDashboard();\n");
        b.append("setInterval(updateLiveDashboard,5000);\n");
        b.append("</script>\n");

        resp.getWriter().write(AiPlatformLayout.page("Velo AI \ud50c\ub7ab\ud3fc", configuration, b.toString()));
    }

    // ── helpers ──

    private static String h(String v) { return AiPlatformLayout.escapeHtml(v); }

    private static void metric(StringBuilder b, String label, String value, String note, String metricId) {
        b.append("<div class=\"metric\"><div class=\"metric-label\">").append(h(label)).append("</div><div class=\"metric-val\" id=\"mv-").append(metricId).append("\">").append(h(value)).append("</div><div class=\"metric-note\">").append(h(note)).append("</div></div>");
    }

    private static void chip(StringBuilder b, String text, boolean green) {
        b.append("<span class=\"chip").append(green ? " green" : "").append("\">").append(h(text)).append("</span>");
    }

    private static void info(StringBuilder b, String title, String value, String desc) {
        b.append("<div class=\"info-item\"><strong>").append(h(title)).append(" \u2014 ").append(h(value)).append("</strong><span>").append(h(desc)).append("</span></div>");
    }

    private static void input(StringBuilder b, String id, String val, String ph) {
        b.append("<input id=\"").append(id).append("\" type=\"text\" value=\"").append(h(val)).append("\" class=\"form-input\" placeholder=\"").append(h(ph)).append("\">");
    }

    private static void provRow(StringBuilder b, String prov, String proto, String models, boolean fo, boolean lb, boolean on) {
        b.append("<tr><td><strong>").append(h(prov)).append("</strong></td><td>").append(h(proto)).append("</td><td>").append(h(models)).append("</td>")
                .append("<td>").append(fo ? "\uc9c0\uc6d0" : "-").append("</td>")
                .append("<td>").append(lb ? "\uc9c0\uc6d0" : "-").append("</td>")
                .append("<td>").append(on ? "<span class=\"status-on\">\ud65c\uc131</span>" : "<span class=\"status-off\">\ube44\ud65c\uc131</span>").append("</td></tr>");
    }

    private static String buildYamlPreview(ServerConfiguration.AiPlatform ai) {
        List<ServerConfiguration.ModelProfile> models = ai.getServing().getModels();
        List<ServerConfiguration.RoutePolicy> policies = ai.getServing().getRoutePolicies();
        String defModel = models.stream().filter(ServerConfiguration.ModelProfile::isDefaultSelected)
                .findFirst().map(ServerConfiguration.ModelProfile::getName).orElse(models.isEmpty() ? "none" : models.get(0).getName());
        StringBuilder sb = new StringBuilder();
        sb.append("server:\n");
        sb.append("  aiPlatform:\n");
        sb.append("    enabled: ").append(ai.isEnabled()).append("\n");
        sb.append("    mode: ").append(ai.getMode()).append("\n");
        sb.append("    console:\n");
        sb.append("      contextPath: ").append(ai.getConsole().getContextPath()).append("\n");
        sb.append("    serving:\n");
        sb.append("      modelRouterEnabled: ").append(ai.getServing().isModelRouterEnabled()).append("\n");
        sb.append("      abTestingEnabled: ").append(ai.getServing().isAbTestingEnabled()).append("\n");
        sb.append("      autoModelSelectionEnabled: ").append(ai.getServing().isAutoModelSelectionEnabled()).append("\n");
        sb.append("      defaultStrategy: ").append(ai.getServing().getDefaultStrategy()).append("\n");
        sb.append("      models: ").append(models.size()).append(" # 등록된 모델 수\n");
        sb.append("      routePolicies: ").append(policies.size()).append("\n");
        sb.append("      defaultModel: ").append(defModel).append("\n");
        sb.append("    platform:\n");
        sb.append("      modelRegistrationEnabled: ").append(ai.getPlatform().isModelRegistrationEnabled()).append("\n");
        sb.append("      versionManagementEnabled: ").append(ai.getPlatform().isVersionManagementEnabled()).append("\n");
        sb.append("      billingEnabled: ").append(ai.getPlatform().isBillingEnabled()).append("\n");
        sb.append("      multiTenantEnabled: ").append(ai.getPlatform().isMultiTenantEnabled()).append("\n");
        sb.append("    advanced:\n");
        sb.append("      promptRoutingEnabled: ").append(ai.getAdvanced().isPromptRoutingEnabled()).append("\n");
        sb.append("      contextCacheEnabled: ").append(ai.getAdvanced().isContextCacheEnabled()).append("\n");
        sb.append("      aiGatewayEnabled: ").append(ai.getAdvanced().isAiGatewayEnabled()).append("\n");
        sb.append("      observabilityEnabled: ").append(ai.getAdvanced().isObservabilityEnabled()).append("\n");
        sb.append("    roadmap:\n");
        sb.append("      currentStage: ").append(ai.getRoadmap().getCurrentStage()).append("\n");
        return sb.toString();
    }
}
