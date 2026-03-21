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
        String e = ""; // shorthand for escapeHtml calls

        StringBuilder b = new StringBuilder(32_768);

        // ===== TAB: overview =====
        b.append("<div class=\"tab-panel\" id=\"tab-overview\">\n");
        b.append("<div class=\"hero\"><div class=\"hero-eyebrow\">\ub3c5\ub9bd \uc2e4\ud589\ud615 AI \ud50c\ub7ab\ud3fc \ucf58\uc194</div>");
        b.append("<h1>WAS \uc124\uc815 \ud558\ub098\ub85c AI \ud50c\ub7ab\ud3fc \ucee8\ud2b8\ub864 \ud50c\ub808\uc778\uc744 \uad6c\ucd95\ud569\ub2c8\ub2e4.</h1>");
        b.append("<p>\uba40\ud2f0 LLM \ub77c\uc6b0\ud305, OpenAI \ud638\ud658 \ud504\ub85d\uc2dc, \uc7a5\uc560 \uc870\uce58, A/B \ud14c\uc2a4\ud2b8, \ube44\uc6a9 \ucd94\uc801, \ud14c\ub10c\ud2b8 \uad00\ub9ac\ub97c <strong>server.aiPlatform</strong> \uc124\uc815\uc73c\ub85c \uc81c\uc5b4\ud569\ub2c8\ub2e4.</p>");
        b.append("<div class=\"chips\">");
        chip(b, "\uc0c1\ud0dc: \uc815\uc0c1", true);
        chip(b, "\ubaa8\ub4dc: " + h(ai.getMode()), false);
        chip(b, "\ubaa8\ub378: " + ai.getServing().getModels().size() + "\uac1c", false);
        chip(b, "\uc815\ucc45: " + ai.getServing().getRoutePolicies().size() + "\uac1c", false);
        chip(b, "\ub2e8\uacc4: " + ai.getRoadmap().getCurrentStage() + " / 5", false);
        chip(b, "OpenAI \ud504\ub85d\uc2dc: " + (ai.getAdvanced().isAiGatewayEnabled() ? "\ud65c\uc131" : "\ube44\ud65c\uc131"), false);
        b.append("</div></div>\n");
        // metrics
        b.append("<div class=\"metrics\">");
        metric(b, "\ud575\uc2ec \ubc29\ud5a5", ai.getCommercialization().getCoreDirection(), "WAS\ub97c AI \ud50c\ub7ab\ud3fc\uc73c\ub85c \ud655\uc7a5");
        metric(b, "\uc11c\ube59 \uc804\ub7b5", ai.getServing().getDefaultStrategy(), "\uc790\ub3d9 \ubaa8\ub378 \uc120\ud0dd \uae30\ubcf8 \uc804\ub7b5");
        metric(b, "\ubaa9\ud45c P99", ai.getServing().getTargetP99LatencyMs() + " ms", "\uac8c\uc774\ud2b8\uc6e8\uc774 \ub77c\uc6b0\ud305 \uc9c0\uc5f0 \ubaa9\ud45c");
        metric(b, "\ucee8\ud14d\uc2a4\ud2b8 \uce90\uc2dc", ai.getAdvanced().getContextCacheTtlSeconds() + "\ucd08", "\ubc18\ubcf5 \ud504\ub86c\ud504\ud2b8 \uce90\uc2dc \ubcf4\uc874 \uc2dc\uac04");
        b.append("</div>\n");
        // overview json
        b.append("<div class=\"card\"><div class=\"card-header\">\ud50c\ub7ab\ud3fc \uc804\uccb4 \uac1c\uc694 JSON</div><div class=\"card-desc\">\ucee8\ud2b8\ub864 \ud50c\ub808\uc778 API\uc758 JSON \uc751\ub2f5 \ubbf8\ub9ac\ubcf4\uae30</div>");
        b.append("<div class=\"btns\"><button class=\"btn btn-secondary\" onclick=\"refreshOverview()\">\uc0c8\ub85c\uace0\uce68</button></div>");
        b.append("<pre class=\"json-box\" id=\"overviewJson\">\ub85c\ub529 \uc911...</pre></div>\n");
        b.append("</div>\n");

        // ===== TAB: usage =====
        b.append("<div class=\"tab-panel\" id=\"tab-usage\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\uc0ac\uc6a9\ub7c9 \ubc0f \ubbf8\ud130\ub9c1</div><div class=\"card-desc\">\uac8c\uc774\ud2b8\uc6e8\uc774 \ud2b8\ub798\ud53d, \ub808\uc9c0\uc2a4\ud2b8\ub9ac \ubcc0\uacbd, \ud1a0\ud070 \uc0ac\uc6a9\ub7c9</div>");
        b.append("<div class=\"btns\"><button class=\"btn btn-primary\" onclick=\"refreshUsage()\">\uc0c8\ub85c\uace0\uce68</button></div>");
        b.append("<pre class=\"json-box\" id=\"usageJson\">\ub85c\ub529 \uc911...</pre></div>\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\ucee8\ud2b8\ub864 \ud50c\ub808\uc778 \uc5d4\ub4dc\ud3ec\uc778\ud2b8</div><div class=\"card-desc\">\ub3c5\ub9bd \ubaa8\ub4c8\uc5d0\uc11c \ub178\ucd9c\ub418\ub294 \uc6b4\uc601 API</div>");
        b.append("<div class=\"info-list\">");
        info(b, "\uc804\uccb4 \uac1c\uc694", cp + "/api/overview", "\ub808\uc9c0\uc2a4\ud2b8\ub9ac \ubc0f \uc0ac\uc6a9\ub7c9 \uc694\uc57d\uc744 \ud3ec\ud568\ud55c \ud50c\ub7ab\ud3fc \uc0c1\ud0dc");
        info(b, "\uc0ac\uc6a9\ub7c9 \uc870\ud68c", cp + "/api/usage", "\ub77c\uc6b0\ud2b8, \ucd94\ub860, \uc2a4\ud2b8\ub9bc, \uce90\uc2dc \uc801\uc911, \ud1a0\ud070 \uce74\uc6b4\ud130");
        info(b, "\uba54\ud2b8\ub9ad \ubcc4\uce6d", cp + "/api/metrics", "\ubaa8\ub2c8\ud130\ub9c1 \ub300\uc2dc\ubcf4\ub4dc\uc6a9 \ud3f4\ub9c1 \uacbd\ub85c");
        b.append("</div></div>\n");
        b.append("</div>\n");

        // ===== TAB: serving =====
        b.append("<div class=\"tab-panel\" id=\"tab-serving\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\uc11c\ube59 \uad6c\uc131</div><div class=\"card-desc\">\ub77c\uc6b0\ud305 \ubc0f \uc11c\ube59 \uc2a4\uc704\uce58 \uc124\uc815</div>");
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
        info(b, "\ub77c\uc6b0\ud2b8 \uc815\ucc45 \uc218", ai.getServing().getRoutePolicies().size() + "\uac1c", "\ucc44\ud305, \ube44\uc804, \ucd94\ucc9c, \uae30\ubcf8 \ud2b8\ub798\ud53d \ucee4\ubc84");
        b.append("</div></div>\n");
        // models table
        b.append("<div class=\"card\"><div class=\"card-header\">\ub4f1\ub85d \ubaa8\ub378 \ubaa9\ub85d</div><div class=\"card-desc\">\uac8c\uc774\ud2b8\uc6e8\uc774 \ub808\uc9c0\uc2a4\ud2b8\ub9ac\uc5d0 \ub85c\ub4dc\ub41c \ubaa8\ub378 \ud504\ub85c\ud30c\uc77c</div>");
        b.append("<div class=\"tbl-wrap\"><table><thead><tr><th>\ubaa8\ub378\uba85</th><th>\uce74\ud14c\uace0\ub9ac</th><th>\ud504\ub85c\ubc14\uc774\ub354</th><th>\ubc84\uc804</th><th>\uc9c0\uc5f0(ms)</th><th>\uc815\ud655\ub3c4</th><th>\uae30\ubcf8\uac12</th><th>\uc0c1\ud0dc</th></tr></thead><tbody>");
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

        // ===== TAB: registry =====
        b.append("<div class=\"tab-panel\" id=\"tab-registry\">\n");
        // guide card
        b.append("<div class=\"card\" style=\"border-left:4px solid var(--primary);\">");
        b.append("<div class=\"card-header\">\ud83d\udca1 \ubaa8\ub378 \ub808\uc9c0\uc2a4\ud2b8\ub9ac \uc0ac\uc6a9 \uac00\uc774\ub4dc</div>");
        b.append("<div class=\"card-desc\" style=\"margin-bottom:8px;line-height:1.8;\">");
        b.append("<strong>\ub2e8\uacc4 1.</strong> \uc544\ub798 \ud3fc\uc5d0 \ubaa8\ub378\uba85/\uce74\ud14c\uace0\ub9ac/\ud504\ub85c\ubc14\uc774\ub354/\ubc84\uc804\uc744 \uc785\ub825\ud55c \ud6c4 <strong>[\ubc84\uc804 \ub4f1\ub85d]</strong>\uc744 \ud074\ub9ad\ud558\uba74 \ubaa8\ub378 \ubc84\uc804\uc774 \ub808\uc9c0\uc2a4\ud2b8\ub9ac\uc5d0 \ub4f1\ub85d\ub429\ub2c8\ub2e4.<br>");
        b.append("<strong>\ub2e8\uacc4 2.</strong> \ub4f1\ub85d\ub41c \ubaa8\ub378\uc758 \ubc84\uc804\uc744 <strong>[ACTIVE \uc2b9\uaca9]</strong> \ub610\ub294 <strong>[CANARY \uc9c0\uc815]</strong>\uc73c\ub85c \uc0c1\ud0dc \ubcc0\uacbd\ud560 \uc218 \uc788\uc2b5\ub2c8\ub2e4.<br>");
        b.append("<strong>\ucc38\uace0:</strong> YAML \uc124\uc815\uc5d0 \uc815\uc758\ub41c \ubaa8\ub378\ub4e4\uc740 \uc11c\ubc84 \uc2dc\uc791 \uc2dc \uc790\ub3d9\uc73c\ub85c \ub808\uc9c0\uc2a4\ud2b8\ub9ac\uc5d0 \ub4f1\ub85d\ub429\ub2c8\ub2e4. \uc5ec\uae30\uc11c\ub294 \ub7f0\ud0c0\uc784 \ucd94\uac00/\ubcc0\uacbd\uc774 \uac00\ub2a5\ud569\ub2c8\ub2e4.");
        b.append("</div></div>\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\ubaa8\ub378 \ub808\uc9c0\uc2a4\ud2b8\ub9ac</div><div class=\"card-desc\">\ubaa8\ub378 \ubc84\uc804 \ub4f1\ub85d, \uce74\ub098\ub9ac \uc2b9\uaca9, \uc0c1\ud0dc \ubcc0\uacbd</div>");
        b.append("<div class=\"form-grid cols-2\">");
        // Use first model's name and version as defaults to avoid 404
        String defModelName = ai.getServing().getModels().isEmpty() ? "llm-general" : ai.getServing().getModels().get(0).getName();
        String defModelVer = ai.getServing().getModels().isEmpty() ? "v1" : ai.getServing().getModels().get(0).getVersion();
        input(b, "registryModelName", defModelName, "\ubaa8\ub378\uba85");
        input(b, "registryCategory", "LLM", "\uce74\ud14c\uace0\ub9ac");
        input(b, "registryProvider", "builtin", "\ud504\ub85c\ubc14\uc774\ub354");
        input(b, "registryVersion", defModelVer, "\ubc84\uc804");
        input(b, "registryLatency", "210", "\uc9c0\uc5f0 \uc2dc\uac04(ms)");
        input(b, "registryAccuracy", "89", "\uc815\ud655\ub3c4");
        b.append("</div>");
        b.append("<div class=\"form-grid\"><select id=\"registryStatus\" class=\"form-select\"><option value=\"ACTIVE\">ACTIVE</option><option value=\"CANARY\">CANARY</option><option value=\"INACTIVE\">INACTIVE</option><option value=\"DEPRECATED\">DEPRECATED</option></select></div>");
        b.append("<div class=\"btns\"><button class=\"btn btn-primary\" onclick=\"registerRegistryModel()\">\ubc84\uc804 \ub4f1\ub85d</button><button class=\"btn btn-secondary\" onclick=\"updateRegistryStatus('ACTIVE')\">ACTIVE \uc2b9\uaca9</button><button class=\"btn btn-secondary\" onclick=\"updateRegistryStatus('CANARY')\">CANARY \uc9c0\uc815</button><button class=\"btn btn-secondary\" onclick=\"refreshRegistry()\">\uc0c8\ub85c\uace0\uce68</button></div>");
        b.append("<pre class=\"json-box\" id=\"registryMutationOutput\">\ubaa8\ub378\uc744 \ub4f1\ub85d\ud558\ub824\uba74 [\ubc84\uc804 \ub4f1\ub85d] \ubc84\ud2bc\uc744 \ub204\ub974\uc138\uc694.\n\uc0c1\ud0dc \ubcc0\uacbd\uc740 \uba3c\uc800 \ub4f1\ub85d\ub41c \ubaa8\ub378/\ubc84\uc804\uc5d0\ub9cc \uc801\uc6a9\ub429\ub2c8\ub2e4.</pre>");
        b.append("<pre class=\"json-box\" id=\"registryJson\">\ub85c\ub529 \uc911...</pre>");
        b.append("</div>\n");
        b.append("</div>\n");

        // ===== TAB: providers =====
        b.append("<div class=\"tab-panel\" id=\"tab-providers\">\n");
        // guide card
        b.append("<div class=\"card\" style=\"border-left:4px solid var(--primary);\">");
        b.append("<div class=\"card-header\">\ud83d\udca1 AI \ud504\ub85c\ubc14\uc774\ub354 \uc5f0\ub3d9 \uac00\uc774\ub4dc</div>");
        b.append("<div class=\"card-desc\" style=\"margin-bottom:8px;line-height:1.8;\">");
        b.append("<strong>\ud504\ub85c\ubc14\uc774\ub354 \uc5f0\ub3d9 \ubc29\ubc95:</strong><br>");
        b.append("1\ufe0f\u20e3 <code>velo.yaml</code>\uc758 <code>server.aiPlatform.serving.models[]</code>\uc5d0 \ubaa8\ub378 \ud504\ub85c\ud30c\uc77c\uc744 \uc815\uc758\ud569\ub2c8\ub2e4.<br>");
        b.append("2\ufe0f\u20e3 <code>provider</code> \ud544\ub4dc\ub97c <code>openai</code>, <code>anthropic</code>, <code>ollama</code> \ub4f1\uc73c\ub85c \uc9c0\uc815\ud569\ub2c8\ub2e4.<br>");
        b.append("3\ufe0f\u20e3 \ud504\ub85c\ubc14\uc774\ub354 API \ud0a4\ub294 \ud658\uacbd \ubcc0\uc218(<code>OPENAI_API_KEY</code>, <code>ANTHROPIC_API_KEY</code>)\ub85c \uc124\uc815\ud569\ub2c8\ub2e4.<br>");
        b.append("4\ufe0f\u20e3 \uc11c\ubc84 \uc7ac\uc2dc\uc791 \uc5c6\uc774 <strong>\ubaa8\ub378 \ub808\uc9c0\uc2a4\ud2b8\ub9ac</strong> \ud0ed\uc5d0\uc11c \ub7f0\ud0c0\uc784 \ubaa8\ub378\uc744 \ucd94\uac00\ud560 \uc218\ub3c4 \uc788\uc2b5\ub2c8\ub2e4.<br>");
        b.append("<strong>OpenAI \ud638\ud658 \ud504\ub85d\uc2dc:</strong> \ubaa8\ub4e0 \ud504\ub85c\ubc14\uc774\ub354\ub97c <code>/v1/chat/completions</code> \uc5d4\ub4dc\ud3ec\uc778\ud2b8 \ud558\ub098\ub85c \ud1b5\ud569 \uc811\uc18d \uac00\ub2a5\ud569\ub2c8\ub2e4.");
        b.append("</div></div>\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\uba40\ud2f0 LLM \ud504\ub85c\ubc14\uc774\ub354 \uc5f0\ub3d9</div><div class=\"card-desc\">OpenAI, Anthropic, vLLM, SGLang, Ollama \ud1b5\ud569 API</div>");
        b.append("<div class=\"tbl-wrap\"><table><thead><tr><th>\ud504\ub85c\ubc14\uc774\ub354</th><th>\ud504\ub85c\ud1a0\ucf5c</th><th>\ubaa8\ub378</th><th>\uc7a5\uc560 \uc870\uce58</th><th>\ub85c\ub4dc \ubc38\ub7f0\uc2f1</th><th>\uc0c1\ud0dc</th></tr></thead><tbody>");
        provRow(b, "OpenAI", "REST / SSE", "GPT-4o, GPT-4o-mini", true, true, true);
        provRow(b, "Anthropic", "REST / SSE", "Claude Opus, Sonnet", true, true, true);
        provRow(b, "vLLM", "OpenAI \ud638\ud658", "\ucee4\uc2a4\ud140 \ud30c\uc778\ud29c\ub2dd", true, false, ai.getServing().isModelRouterEnabled());
        provRow(b, "SGLang", "OpenAI \ud638\ud658", "\ucee4\uc2a4\ud140 \ud30c\uc778\ud29c\ub2dd", true, false, ai.getServing().isModelRouterEnabled());
        provRow(b, "Ollama", "REST", "Llama, Mistral, Phi", true, false, ai.getServing().isEdgeAiEnabled());
        b.append("</tbody></table></div>");
        b.append("<div class=\"info-list\" style=\"margin-top:12px;\">");
        info(b, "OpenAI \ud638\ud658 \ud504\ub85d\uc2dc", cp + "/v1/chat/completions", "OpenAI SDK\ub97c \uadf8\ub300\ub85c \uc0ac\uc6a9\ud560 \uc218 \uc788\ub294 \ub4dc\ub86d\uc778 \ud504\ub85d\uc2dc");
        info(b, "\uc7a5\uc560 \uc870\uce58 \uc804\ub7b5", "\ud504\ub85c\ubc14\uc774\ub354 \uc7a5\uc560 \uc2dc \uc790\ub3d9 \ubaa8\ub378 \uc804\ud658", "\uae30\ubcf8 \ubaa8\ub378 \uc2e4\ud328 \uc2dc \ub2e4\uc74c \ud6c4\ubcf4\ub85c \uc790\ub3d9 \uc7ac\uc2dc\ub3c4");
        info(b, "\ub85c\ub4dc \ubc38\ub7f0\uc2f1", ai.getServing().isModelRouterEnabled() ? "\ud65c\uc131" : "\ube44\ud65c\uc131", "\ub77c\uc6b0\ud305 \uc804\ub7b5\uc5d0 \ub530\ub77c API \ud0a4/\uc5d4\ub4dc\ud3ec\uc778\ud2b8 \uac04 \ud2b8\ub798\ud53d \ubd84\uc0b0");
        b.append("</div></div>\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\uc5f0\ub3d9 \ube60\ub978 \uc2dc\uc791</div><div class=\"card-desc\">OpenAI \ud638\ud658 \ud074\ub77c\uc774\uc5b8\ud2b8\ub85c Velo AI \uac8c\uc774\ud2b8\uc6e8\uc774 \uc811\uc18d</div>");
        b.append("<pre class=\"code-box\">");
        b.append(h("# Python (OpenAI SDK)\nfrom openai import OpenAI\n\nclient = OpenAI(\n    base_url=\"http://localhost:8080" + ai.getConsole().getContextPath() + "/v1\",\n    api_key=\"velo-demo-key\"\n)\n\nresponse = client.chat.completions.create(\n    model=\"llm-general\",\n    messages=[{\"role\": \"user\", \"content\": \"\uc548\ub155\ud558\uc138\uc694\"}]\n)\n\n# curl\ncurl -X POST " + ai.getConsole().getContextPath() + "/v1/chat/completions \\\n  -H \"Authorization: Bearer velo-demo-key\" \\\n  -H \"Content-Type: application/json\" \\\n  -d '{\"messages\":[{\"role\":\"user\",\"content\":\"\uc548\ub155\ud558\uc138\uc694\"}]}'"));
        b.append("</pre></div>\n");
        b.append("</div>\n");

        // ===== TAB: sandbox =====
        b.append("<div class=\"tab-panel\" id=\"tab-sandbox\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\uac8c\uc774\ud2b8\uc6e8\uc774 \ud14c\uc2a4\ud2b8</div><div class=\"card-desc\">\ub77c\uc6b0\ud305/\ucd94\ub860/\uc559\uc0c1\ube14/\uc2a4\ud2b8\ub9ac\ubc0d \ub3d9\uc791\uc744 \uc9c1\uc811 \ud655\uc778\ud569\ub2c8\ub2e4</div>");
        b.append("<div class=\"form-grid\">");
        b.append("<select id=\"gatewayType\" class=\"form-select\"><option value=\"AUTO\">\uc790\ub3d9 (AUTO)</option><option value=\"CHAT\">\ucc44\ud305 (CHAT)</option><option value=\"VISION\">\ube44\uc804 (VISION)</option><option value=\"RECOMMENDATION\">\ucd94\ucc9c (RECOMMENDATION)</option></select>");
        input(b, "gatewaySession", "console-demo", "\uc138\uc158 ID");
        b.append("<textarea id=\"gatewayPrompt\" class=\"form-textarea\">\uc2e0\uaddc \ubaa8\ubc14\uc77c \uace0\uac1d\uc5d0\uac8c \ucd94\ucc9c\ud560 \uc2a4\ud0c0\ud130 \uc0c1\ud488 3\uac1c\ub97c \uc81c\uc548\ud574 \uc8fc\uc138\uc694.</textarea>");
        b.append("</div>");
        b.append("<div class=\"btns\"><button class=\"btn btn-primary\" onclick=\"callGateway('route')\">\ub77c\uc6b0\ud305</button><button class=\"btn btn-primary\" onclick=\"callGateway('infer')\">\ucd94\ub860</button><button class=\"btn btn-primary\" onclick=\"callGateway('ensemble')\">\uc559\uc0c1\ube14</button><button class=\"btn btn-secondary\" onclick=\"openGatewayStream()\">\uc2a4\ud2b8\ub9ac\ubc0d</button></div>");
        b.append("<pre class=\"json-box\" id=\"gatewayOutput\">\ub77c\uc6b0\ud305 \ub610\ub294 \ucd94\ub860\uc744 \uc2e4\ud589\ud558\uba74 \uacb0\uacfc\uac00 \uc5ec\uae30\uc5d0 \ud45c\uc2dc\ub429\ub2c8\ub2e4.</pre>");
        b.append("</div>\n");
        b.append("</div>\n");

        // ===== TAB: tenants =====
        b.append("<div class=\"tab-panel\" id=\"tab-tenants\">\n");
        // guide card
        b.append("<div class=\"card\" style=\"border-left:4px solid var(--primary);\">");
        b.append("<div class=\"card-header\">\ud83d\udca1 \ud14c\ub10c\ud2b8 \uad00\ub9ac \uc0ac\uc6a9 \uac00\uc774\ub4dc</div>");
        b.append("<div class=\"card-desc\" style=\"margin-bottom:8px;line-height:1.8;\">");
        b.append("<strong>\ub2e8\uacc4 1.</strong> \ud14c\ub10c\ud2b8 ID/\uc774\ub984/\uc694\uae08\uc81c \uc785\ub825 \ud6c4 <strong>[\ud14c\ub10c\ud2b8 \ub4f1\ub85d]</strong> \ud074\ub9ad \u2192 \ud14c\ub10c\ud2b8\uac00 \uc0dd\uc131\ub429\ub2c8\ub2e4.<br>");
        b.append("<strong>\ub2e8\uacc4 2.</strong> \ub4f1\ub85d\ub41c \ud14c\ub10c\ud2b8 ID\ub97c \uc785\ub825\ud55c \uc0c1\ud0dc\uc5d0\uc11c <strong>[API \ud0a4 \ubc1c\uae09]</strong> \ud074\ub9ad \u2192 \uac8c\uc774\ud2b8\uc6e8\uc774 \uc811\uadfc\uc6a9 \ud0a4\uac00 \ubc1c\uae09\ub429\ub2c8\ub2e4.<br>");
        b.append("<strong>\ucc38\uace0:</strong> \uc11c\ubc84 \uc2dc\uc791 \uc2dc <code>tenant-demo</code> \ud14c\ub10c\ud2b8\uc640 <code>velo-demo-key</code> API \ud0a4\uac00 \uc790\ub3d9 \uc0dd\uc131\ub429\ub2c8\ub2e4.");
        b.append("</div></div>\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\ud14c\ub10c\ud2b8 \uad00\ub9ac</div><div class=\"card-desc\">\ud14c\ub10c\ud2b8 \ub4f1\ub85d, API \ud0a4 \ubc1c\uae09, \uc694\uccad \uc81c\ud55c \ubc0f \ud1a0\ud070 \ucffc\ud130 \uad00\ub9ac</div>");
        b.append("<div class=\"form-grid cols-2\">");
        input(b, "tenantId", "tenant-demo", "\ud14c\ub10c\ud2b8 ID");
        input(b, "tenantDisplayName", "\ub370\ubaa8 \ud14c\ub10c\ud2b8", "\ud45c\uc2dc \uc774\ub984");
        b.append("<select id=\"tenantPlan\" class=\"form-select\"><option value=\"starter\">Starter</option><option value=\"pro\">Pro</option><option value=\"enterprise\">Enterprise</option></select>");
        input(b, "tenantRateLimit", "120", "\uc694\uccad \uc81c\ud55c(\ubd84\ub2f9)");
        input(b, "tenantTokenQuota", "250000", "\ud1a0\ud070 \ucffc\ud130");
        input(b, "tenantKeyLabel", "default", "API \ud0a4 \ub77c\ubca8");
        b.append("</div>");
        b.append("<div class=\"btns\"><button class=\"btn btn-primary\" onclick=\"registerTenant()\">\ud14c\ub10c\ud2b8 \ub4f1\ub85d</button><button class=\"btn btn-primary\" onclick=\"issueApiKey()\">API \ud0a4 \ubc1c\uae09</button><button class=\"btn btn-secondary\" onclick=\"refreshTenants()\">\uc0c8\ub85c\uace0\uce68</button></div>");
        b.append("<pre class=\"json-box\" id=\"tenantMutationOutput\">\ud14c\ub10c\ud2b8\ub97c \ub4f1\ub85d\ud558\uac70\ub098 API \ud0a4\ub97c \ubc1c\uae09\ud558\uc138\uc694.</pre>");
        b.append("<pre class=\"json-box\" id=\"tenantJson\">\ub85c\ub529 \uc911...</pre>");
        b.append("</div>\n");
        b.append("</div>\n");

        // ===== TAB: published =====
        b.append("<div class=\"tab-panel\" id=\"tab-published\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">API \uc790\ub3d9 \ubc1c\ud589</div><div class=\"card-desc\">\ud65c\uc131 \ubaa8\ub378 \ub808\uc9c0\uc2a4\ud2b8\ub9ac\uc5d0\uc11c \uc790\ub3d9 \uc0dd\uc131\ub41c REST \uc5d4\ub4dc\ud3ec\uc778\ud2b8</div>");
        b.append("<div class=\"info-list\">");
        info(b, "\uacf5\uac1c Invoke \uacbd\ub85c", cp + "/invoke", "\ucf58\uc194 \uc778\uc99d \uc5c6\uc774 \ud638\ucd9c \uac00\ub2a5\ud55c \uc790\ub3d9 \uc0dd\uc131 \uc5d4\ub4dc\ud3ec\uc778\ud2b8");
        info(b, "\ubc1c\ud589 API \ubaa9\ub85d", cp + "/api/published-apis", "\ubc1c\ud589\ub41c \uc5d4\ub4dc\ud3ec\uc778\ud2b8\uc640 \ud65c\uc131 \ubc84\uc804 \ubaa9\ub85d");
        b.append("</div>");
        b.append("<div class=\"form-grid\">");
        input(b, "publishedModelName", "llm-general", "\ubaa8\ub378\uba85");
        b.append("<textarea id=\"publishedPrompt\" class=\"form-textarea\">\uc2e0\uaddc \uae30\uc5c5 \uace0\uac1d\uc744 \uc704\ud55c \uc628\ubcf4\ub529 \uac00\uc774\ub4dc\ub97c \uc694\uc57d\ud574 \uc8fc\uc138\uc694.</textarea>");
        b.append("</div>");
        b.append("<div class=\"btns\"><button class=\"btn btn-secondary\" onclick=\"refreshPublishedApis()\">API \uc0c8\ub85c\uace0\uce68</button><button class=\"btn btn-primary\" onclick=\"invokePublishedModel()\">API \ud638\ucd9c</button><button class=\"btn btn-secondary\" onclick=\"refreshBilling()\">\uacfc\uae08 \uc0c8\ub85c\uace0\uce68</button></div>");
        b.append("<pre class=\"json-box\" id=\"publishedMutationOutput\">\ubc1c\ud589\ub41c \uc5d4\ub4dc\ud3ec\uc778\ud2b8\ub97c \uc870\ud68c\ud558\uac70\ub098 \ubaa8\ub378 API\ub97c \ud638\ucd9c\ud558\uc138\uc694.</pre>");
        b.append("<pre class=\"json-box\" id=\"publishedJson\">\ub85c\ub529 \uc911...</pre>");
        b.append("<pre class=\"json-box\" id=\"billingJson\">\uacfc\uae08 \ubbf8\ub9ac\ubcf4\uae30 \ub85c\ub529 \uc911...</pre>");
        b.append("</div>\n");
        b.append("</div>\n");

        // ===== TAB: developer =====
        b.append("<div class=\"tab-panel\" id=\"tab-developer\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\uac1c\ubc1c\uc790 \ud3ec\ud138</div><div class=\"card-desc\">OpenAPI \ubb38\uc11c \ubc0f \uacf5\uac1c \uac8c\uc774\ud2b8\uc6e8\uc774 \uc9c4\uc785\uc810</div>");
        b.append("<div class=\"pills\">");
        b.append(AiPlatformLayout.featurePill("\ud504\ub86c\ud504\ud2b8 \ub77c\uc6b0\ud305", ai.getAdvanced().isPromptRoutingEnabled()));
        b.append(AiPlatformLayout.featurePill("\ucee8\ud14d\uc2a4\ud2b8 \uce90\uc2dc", ai.getAdvanced().isContextCacheEnabled()));
        b.append(AiPlatformLayout.featurePill("\uad00\uce21\uc131", ai.getAdvanced().isObservabilityEnabled()));
        b.append("</div>");
        b.append("<div class=\"info-list\">");
        info(b, "OpenAPI \uc2a4\ud399", cp + "/api-docs", "AI \ud50c\ub7ab\ud3fc \ubaa8\ub4c8\uc758 \uc790\ub3d9 \uc0dd\uc131 API \uacc4\uc57d\uc11c");
        info(b, "\uac1c\ubc1c\uc790 \ud3ec\ud138 UI", cp + "/api-docs/ui", "\ube60\ub978 \uc2dc\uc791, \uc2a4\ud399 \ubdf0\uc5b4, curl \uc608\uc81c \ud3ec\ud568");
        info(b, "\uac8c\uc774\ud2b8\uc6e8\uc774 \uc11c\ud53c\uc2a4", cp + "/gateway/*", "\uacf5\uac1c \ub77c\uc6b0\ud2b8, \ucd94\ub860, \uc2a4\ud2b8\ub9bc API");
        b.append("</div></div>\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\ucc28\ubcc4\ud654 \uae30\ub2a5</div><div class=\"card-desc\">AI \uc804\uc6a9 \ub7f0\ud0c0\uc784 \ud575\uc2ec \uc5ed\ub7c9</div>");
        b.append("<div class=\"pills\">");
        b.append(AiPlatformLayout.featurePill("AI \ucd5c\uc801\ud654 WAS", ai.getDifferentiation().isAiOptimizedWasEnabled()));
        b.append(AiPlatformLayout.featurePill("\uc694\uccad \ub77c\uc6b0\ud305", ai.getDifferentiation().isRequestRoutingEnabled()));
        b.append(AiPlatformLayout.featurePill("\uc2a4\ud2b8\ub9ac\ubc0d \uc751\ub2f5", ai.getDifferentiation().isStreamingResponseEnabled()));
        b.append(AiPlatformLayout.featurePill("\ud50c\ub7ec\uadf8\uc778", ai.getDifferentiation().isPluginFrameworkEnabled()));
        b.append("</div>");
        b.append("<div class=\"info-list\">");
        info(b, "\ub7f0\ud0c0\uc784 \uc5d4\uc9c4", ai.getDifferentiation().getRuntimeEngine(), "Netty \uae30\ubc18 \ube44\ub3d9\uae30 \ucc98\ub9ac\ub85c \uc800\uc9c0\uc5f0 IO");
        info(b, "AI \uac8c\uc774\ud2b8\uc6e8\uc774", ai.getAdvanced().isAiGatewayEnabled() ? "\ud65c\uc131" : "\ube44\ud65c\uc131", "/gateway \ud558\uc704\uc5d0\uc11c \ucf58\uc194 \ub85c\uadf8\uc778 \uc5c6\uc774 \uc0ac\uc6a9 \uac00\ub2a5");
        b.append("</div></div>\n");
        b.append("</div>\n");

        // ===== TAB: platform =====
        b.append("<div class=\"tab-panel\" id=\"tab-platform\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\ud50c\ub7ab\ud3fc \uacc4\uce35</div><div class=\"card-desc\">\ubaa8\ub378 \ub4f1\ub85d, API \uc0dd\uc131, \ubc84\uc804 \uad00\ub9ac, \ud3ec\ud138, \uacfc\uae08 \uc124\uc815</div>");
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
        info(b, "\uc218\uc775 \ubaa8\ub378", String.join(", ", ai.getCommercialization().getRevenueStreams()), "API \uacfc\uae08 \ubc0f SaaS \ud655\uc7a5 \ub808\ubc84");
        info(b, "\ud575\uc2ec \ucc28\ubcc4\uc810", String.join(", ", ai.getCommercialization().getDifferentiators()), "\ub77c\uc6b0\ud305, \uc800\uc9c0\uc5f0, \ucee4\uc2a4\ud130\ub9c8\uc774\uc9d5");
        b.append("</div></div>\n");
        b.append("</div>\n");

        // ===== TAB: config =====
        b.append("<div class=\"tab-panel\" id=\"tab-config\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">YAML \uc124\uc815 \ubbf8\ub9ac\ubcf4\uae30</div><div class=\"card-desc\">\ud604\uc7ac server.aiPlatform \uc124\uc815 \uc694\uc57d</div>");
        b.append("<pre class=\"code-box\">").append(h(buildYamlPreview(ai))).append("</pre></div>\n");
        b.append("</div>\n");

        // ===== TAB: roadmap =====
        b.append("<div class=\"tab-panel\" id=\"tab-roadmap\">\n");
        b.append("<div class=\"card\"><div class=\"card-header\">\uc9c4\ud654 \ub85c\ub4dc\ub9f5</div><div class=\"card-desc\">\uae30\ubcf8 \uc11c\ube59\uc5d0\uc11c \ud50c\ub7ab\ud3fc \uc0c1\uc6a9\ud654\uae4c\uc9c0\uc758 \ub2e8\uacc4\ubcc4 \ubc1c\uc804 \uacc4\ud68d</div>");
        b.append("<div class=\"timeline\">");
        for (ServerConfiguration.RoadmapStage st : ai.getRoadmap().getStages()) {
            boolean act = st.getStage() == ai.getRoadmap().getCurrentStage();
            b.append("<div class=\"tl-item").append(act ? " active" : "").append("\"><div class=\"tl-num\">").append(st.getStage()).append("</div>");
            b.append("<div class=\"tl-title\">").append(h(st.getGoal())).append("</div>");
            b.append("<div class=\"tl-note\">\ub2e8\uacc4 ").append(st.getStage()).append(act ? " \u2014 \ud604\uc7ac \uc9c4\ud589 \uc911" : " \u2014 \uc608\uc815\ub41c \uae30\ub2a5 \uc138\ud2b8").append("</div>");
            b.append("<div class=\"tl-caps\">");
            for (String cap : st.getCapabilities()) b.append("<span>").append(h(cap)).append("</span>");
            b.append("</div></div>");
        }
        b.append("</div></div>\n");
        b.append("</div>\n");

        // ===== JavaScript =====
        b.append("<script>\n");
        b.append("function showJson(id,t){const el=document.getElementById(id);try{const j=JSON.parse(t);if(j.error){el.textContent='\\u274c \\uc624\\ub958: '+j.error;el.style.color='#dc2626';}else{el.textContent=JSON.stringify(j,null,2);el.style.color='';}}catch(e){el.textContent=t;el.style.color='';}}\n");
        b.append("async function refreshOverview(){showJson('overviewJson',await(await fetch('").append(h(cp)).append("/api/overview')).text())}\n");
        b.append("async function refreshRegistry(){showJson('registryJson',await(await fetch('").append(h(cp)).append("/api/models')).text())}\n");
        b.append("async function refreshUsage(){showJson('usageJson',await(await fetch('").append(h(cp)).append("/api/usage')).text())}\n");
        b.append("async function refreshPublishedApis(){showJson('publishedJson',await(await fetch('").append(h(cp)).append("/api/published-apis')).text())}\n");
        b.append("async function refreshBilling(){showJson('billingJson',await(await fetch('").append(h(cp)).append("/api/billing')).text())}\n");
        b.append("async function refreshTenants(){showJson('tenantJson',await(await fetch('").append(h(cp)).append("/api/tenants')).text())}\n");
        b.append("function registryPayload(){return{name:document.getElementById('registryModelName').value,category:document.getElementById('registryCategory').value,provider:document.getElementById('registryProvider').value,version:document.getElementById('registryVersion').value,latencyTier:'balanced',latencyMs:Number(document.getElementById('registryLatency').value||0),accuracyScore:Number(document.getElementById('registryAccuracy').value||0),defaultSelected:false,enabled:true,status:document.getElementById('registryStatus').value,source:'runtime'}}\n");
        b.append("async function registerRegistryModel(){showJson('registryMutationOutput',await(await fetch('").append(h(cp)).append("/api/models',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(registryPayload())})).text());refreshRegistry();refreshOverview()}\n");
        b.append("async function updateRegistryStatus(s){const n=document.getElementById('registryModelName').value.trim(),v=document.getElementById('registryVersion').value.trim();if(!n||!v){document.getElementById('registryMutationOutput').textContent='\\u274c \\ubaa8\\ub378\\uba85\\uacfc \\ubc84\\uc804\\uc744 \\uc785\\ub825\\ud558\\uc138\\uc694.';document.getElementById('registryMutationOutput').style.color='#dc2626';return;}showJson('registryMutationOutput',await(await fetch('").append(h(cp)).append("/api/models/'+encodeURIComponent(n)+'/versions/'+encodeURIComponent(v)+'/status?state='+s,{method:'POST'})).text());refreshRegistry()}\n");
        b.append("async function callGateway(m){const p={requestType:document.getElementById('gatewayType').value,prompt:document.getElementById('gatewayPrompt').value,sessionId:document.getElementById('gatewaySession').value};showJson('gatewayOutput',await(await fetch('").append(h(cp)).append("/gateway/'+m,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(p)})).text())}\n");
        b.append("function openGatewayStream(){const t=encodeURIComponent(document.getElementById('gatewayType').value),s=encodeURIComponent(document.getElementById('gatewaySession').value),p=encodeURIComponent(document.getElementById('gatewayPrompt').value);window.open('").append(h(cp)).append("/gateway/stream?requestType='+t+'&sessionId='+s+'&prompt='+p,'_blank')}\n");
        b.append("async function invokePublishedModel(){const m=encodeURIComponent(document.getElementById('publishedModelName').value),p=encodeURIComponent(document.getElementById('publishedPrompt').value);showJson('publishedMutationOutput',await(await fetch('").append(h(cp)).append("/invoke/'+m+'?sessionId=published-demo&prompt='+p)).text());refreshUsage();refreshBilling()}\n");
        b.append("async function registerTenant(){const p={tenantId:document.getElementById('tenantId').value,displayName:document.getElementById('tenantDisplayName').value,plan:document.getElementById('tenantPlan').value,rateLimitPerMinute:Number(document.getElementById('tenantRateLimit').value||120),tokenQuota:Number(document.getElementById('tenantTokenQuota').value||250000),active:true};showJson('tenantMutationOutput',await(await fetch('").append(h(cp)).append("/api/tenants',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(p)})).text());refreshTenants()}\n");
        b.append("async function issueApiKey(){const t=document.getElementById('tenantId').value.trim();if(!t){document.getElementById('tenantMutationOutput').textContent='\\u274c \\ud14c\\ub10c\\ud2b8 ID\\ub97c \\uc785\\ub825\\ud558\\uc138\\uc694.';document.getElementById('tenantMutationOutput').style.color='#dc2626';return;}const l=encodeURIComponent(document.getElementById('tenantKeyLabel').value);showJson('tenantMutationOutput',await(await fetch('").append(h(cp)).append("/api/tenants/'+encodeURIComponent(t)+'/keys?label='+l,{method:'POST'})).text());refreshTenants()}\n");
        b.append("refreshOverview();refreshRegistry();refreshUsage();refreshPublishedApis();refreshBilling();refreshTenants();\n");
        b.append("</script>\n");

        resp.getWriter().write(AiPlatformLayout.page("Velo AI \ud50c\ub7ab\ud3fc", configuration, b.toString()));
    }

    // ── helpers ──

    private static String h(String v) { return AiPlatformLayout.escapeHtml(v); }

    private static void metric(StringBuilder b, String label, String value, String note) {
        b.append("<div class=\"metric\"><div class=\"metric-label\">").append(h(label)).append("</div><div class=\"metric-val\">").append(h(value)).append("</div><div class=\"metric-note\">").append(h(note)).append("</div></div>");
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
                .findFirst().map(ServerConfiguration.ModelProfile::getName).orElse(models.get(0).getName());
        return """
                server:
                  aiPlatform:
                    enabled: %s
                    mode: %s
                    console:
                      contextPath: %s
                    serving:
                      modelRouterEnabled: %s
                      abTestingEnabled: %s
                      autoModelSelectionEnabled: %s
                      defaultStrategy: %s
                      models: %d
                      routePolicies: %d
                      defaultModel: %s
                    platform:
                      modelRegistrationEnabled: %s
                      versionManagementEnabled: %s
                      billingEnabled: %s
                      multiTenantEnabled: %s
                    advanced:
                      promptRoutingEnabled: %s
                      contextCacheEnabled: %s
                      aiGatewayEnabled: %s
                      observabilityEnabled: %s
                    roadmap:
                      currentStage: %d
                """.formatted(
                ai.isEnabled(), ai.getMode(), ai.getConsole().getContextPath(),
                ai.getServing().isModelRouterEnabled(), ai.getServing().isAbTestingEnabled(),
                ai.getServing().isAutoModelSelectionEnabled(), ai.getServing().getDefaultStrategy(),
                models.size(), policies.size(), defModel,
                ai.getPlatform().isModelRegistrationEnabled(), ai.getPlatform().isVersionManagementEnabled(),
                ai.getPlatform().isBillingEnabled(), ai.getPlatform().isMultiTenantEnabled(),
                ai.getAdvanced().isPromptRoutingEnabled(), ai.getAdvanced().isContextCacheEnabled(),
                ai.getAdvanced().isAiGatewayEnabled(), ai.getAdvanced().isObservabilityEnabled(),
                ai.getRoadmap().getCurrentStage());
    }
}
