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

    public AiPlatformDashboardServlet(ServerConfiguration configuration, AiModelRegistryService registryService, AiGatewayService gatewayService, AiPlatformUsageService usageService) {
        this(configuration);
    }

    public AiPlatformDashboardServlet(ServerConfiguration configuration, AiModelRegistryService registryService, AiGatewayService gatewayService, AiPlatformUsageService usageService, io.velo.was.aiplatform.tenant.AiTenantService tenantService) {
        this(configuration);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=UTF-8");
        ServerConfiguration.Server server = configuration.getServer();
        ServerConfiguration.AiPlatform ai = server.getAiPlatform();
        String contextPath = req.getContextPath();

        StringBuilder body = new StringBuilder(24_576);
        body.append("<section class=\"hero\" id=\"overview\">");
        body.append("<div class=\"eyebrow\">Standalone Built-in Console</div>");
        body.append("<h1>Turn WAS configuration into a live AI platform control plane.</h1>");
        body.append("<p>This console is deployed independently from webadmin and surfaces routing, gateway, developer portal, advanced platform controls, and the commercialization roadmap from <strong>server.aiPlatform</strong>.</p>");
        body.append("<div class=\"hero-chips\">")
                .append("<span class=\"hero-chip\">Mode ").append(AiPlatformLayout.escapeHtml(ai.getMode())).append("</span>")
                .append("<span class=\"hero-chip\">Path ").append(AiPlatformLayout.escapeHtml(ai.getConsole().getContextPath())).append("</span>")
                .append("<span class=\"hero-chip\">Models ").append(ai.getServing().getModels().size()).append("</span>")
                .append("<span class=\"hero-chip\">Policies ").append(ai.getServing().getRoutePolicies().size()).append("</span>")
                .append("<span class=\"hero-chip\">Stage ").append(ai.getRoadmap().getCurrentStage()).append(" / 5</span>")
                .append("</div>");
        body.append("</section>");

        body.append("<div class=\"stack\">");
        body.append("<section class=\"grid\">");
        metricPanel(body, "span-3", "Core Direction", ai.getCommercialization().getCoreDirection(), "The product direction expands WAS into an AI platform instead of stopping at a single inference endpoint.");
        metricPanel(body, "span-3", "Serving Strategy", ai.getServing().getDefaultStrategy(), "This is the default strategy for auto model selection.");
        metricPanel(body, "span-3", "Target P99", ai.getServing().getTargetP99LatencyMs() + " ms", "A built-in latency objective for gateway routing.");
        metricPanel(body, "span-3", "Context Cache", ai.getAdvanced().getContextCacheTtlSeconds() + " sec", "Session-aware cache retention for repeated prompts.");
        body.append("</section>");

        body.append("<section class=\"grid\" id=\"serving\">");
        body.append("<div class=\"panel span-5\">");
        titleBlock(body, "Serving Fabric", "Core routing and serving switches bundled into the module");
        body.append("<div class=\"pill-row\">")
                .append(AiPlatformLayout.featurePill("Model Router", ai.getServing().isModelRouterEnabled()))
                .append(AiPlatformLayout.featurePill("A/B Test", ai.getServing().isAbTestingEnabled()))
                .append(AiPlatformLayout.featurePill("Auto Selection", ai.getServing().isAutoModelSelectionEnabled()))
                .append(AiPlatformLayout.featurePill("Ensemble", ai.getServing().isEnsembleServingEnabled()))
                .append(AiPlatformLayout.featurePill("Edge AI", ai.getServing().isEdgeAiEnabled()))
                .append("</div>");
        body.append("<div class=\"soft-list\">");
        softItem(body, "Router Timeout", ai.getServing().getRouterTimeoutMillis() + " ms", "Budget reserved for request analysis before routing is locked in.");
        softItem(body, "Default Model Set", ai.getServing().getModels().stream().filter(ServerConfiguration.ModelProfile::isDefaultSelected).count() + " default models", "Default selections provide the base gateway profile when policy hints are absent.");
        softItem(body, "Route Coverage", ai.getServing().getRoutePolicies().size() + " policies", "Built-in policies cover chat, vision, recommendation, and default traffic.");
        body.append("</div></div>");

        body.append("<div class=\"panel span-7\">");
        titleBlock(body, "Bundled Models", "Initial model profiles loaded into the gateway registry");
        body.append("<div class=\"table-wrap\"><table><thead><tr><th>Name</th><th>Category</th><th>Provider</th><th>Version</th><th>Latency Tier</th><th>Latency</th><th>Accuracy</th><th>Default</th><th>State</th></tr></thead><tbody>");
        for (ServerConfiguration.ModelProfile model : ai.getServing().getModels()) {
            body.append("<tr>")
                    .append("<td><strong>").append(AiPlatformLayout.escapeHtml(model.getName())).append("</strong></td>")
                    .append("<td>").append(AiPlatformLayout.escapeHtml(model.getCategory())).append("</td>")
                    .append("<td>").append(AiPlatformLayout.escapeHtml(model.getProvider())).append("</td>")
                    .append("<td>").append(AiPlatformLayout.escapeHtml(model.getVersion())).append("</td>")
                    .append("<td>").append(AiPlatformLayout.escapeHtml(model.getLatencyTier())).append("</td>")
                    .append("<td>").append(model.getLatencyMs()).append(" ms</td>")
                    .append("<td>").append(model.getAccuracyScore()).append(" / 100</td>")
                    .append("<td>").append(model.isDefaultSelected() ? "Yes" : "No").append("</td>")
                    .append("<td>").append(model.isEnabled() ? "Enabled" : "Disabled").append("</td>")
                    .append("</tr>");
        }
        body.append("</tbody></table></div></div>");
        body.append("</section>");

        body.append("<section class=\"grid\">");
        body.append("<div class=\"panel span-6\">");
        titleBlock(body, "Route Policies", "Request-type policies that influence the gateway");
        body.append("<div class=\"table-wrap\"><table><thead><tr><th>Policy</th><th>Request Type</th><th>Target Model</th><th>Priority</th></tr></thead><tbody>");
        for (ServerConfiguration.RoutePolicy policy : ai.getServing().getRoutePolicies()) {
            body.append("<tr>")
                    .append("<td>").append(AiPlatformLayout.escapeHtml(policy.getName())).append("</td>")
                    .append("<td>").append(AiPlatformLayout.escapeHtml(policy.getRequestType())).append("</td>")
                    .append("<td>").append(AiPlatformLayout.escapeHtml(policy.getTargetModel())).append("</td>")
                    .append("<td>").append(policy.getPriority()).append("</td>")
                    .append("</tr>");
        }
        body.append("</tbody></table></div></div>");

        body.append("<div class=\"panel span-6\" id=\"platform\">");
        titleBlock(body, "Platformization Layer", "Model registration, API generation, versioning, portal, and billing");
        body.append("<div class=\"pill-row\">")
                .append(AiPlatformLayout.featurePill("Model Registration", ai.getPlatform().isModelRegistrationEnabled()))
                .append(AiPlatformLayout.featurePill("Auto API", ai.getPlatform().isAutoApiGenerationEnabled()))
                .append(AiPlatformLayout.featurePill("Versioning", ai.getPlatform().isVersionManagementEnabled()))
                .append(AiPlatformLayout.featurePill("Billing", ai.getPlatform().isBillingEnabled()))
                .append(AiPlatformLayout.featurePill("Developer Portal", ai.getPlatform().isDeveloperPortalEnabled()))
                .append(AiPlatformLayout.featurePill("Multi Tenant", ai.getPlatform().isMultiTenantEnabled()))
                .append("</div>");
        body.append("<div class=\"soft-list\">");
        softItem(body, "Versioning Strategy", ai.getPlatform().getVersioningStrategy(), "Use canary, blue-green, or rolling release behavior for model versions.");
        softItem(body, "Revenue Streams", String.join(", ", ai.getCommercialization().getRevenueStreams()), "The module keeps API billing and SaaS expansion visible as built-in product levers.");
        softItem(body, "Differentiators", String.join(", ", ai.getCommercialization().getDifferentiators()), "Routing, low latency, and customization remain the primary positioning points.");
        body.append("</div></div>");
        body.append("</section>");

        body.append("<section class=\"grid\" id=\"advanced\">");
        body.append("<div class=\"panel span-6\">");
        titleBlock(body, "Differentiation", "Capabilities that make this a specialized AI runtime surface");
        body.append("<div class=\"pill-row\">")
                .append(AiPlatformLayout.featurePill("AI Optimized WAS", ai.getDifferentiation().isAiOptimizedWasEnabled()))
                .append(AiPlatformLayout.featurePill("Request Routing", ai.getDifferentiation().isRequestRoutingEnabled()))
                .append(AiPlatformLayout.featurePill("Streaming Response", ai.getDifferentiation().isStreamingResponseEnabled()))
                .append(AiPlatformLayout.featurePill("Plugin Framework", ai.getDifferentiation().isPluginFrameworkEnabled()))
                .append("</div>");
        body.append("<div class=\"soft-list\">");
        softItem(body, "Runtime Engine", ai.getDifferentiation().getRuntimeEngine(), "Netty-based async execution remains the runtime default for low-latency routing and IO.");
        softItem(body, "AI Gateway", ai.getAdvanced().isAiGatewayEnabled() ? "Enabled" : "Disabled", "Gateway endpoints are exposed under /gateway and can be consumed without console login.");
        body.append("</div></div>");

        body.append("<div class=\"panel span-6\" id=\"developer\">");
        titleBlock(body, "Developer Portal", "Generated API docs and public gateway entrypoints");
        body.append("<div class=\"pill-row\">")
                .append(AiPlatformLayout.featurePill("Prompt Routing", ai.getAdvanced().isPromptRoutingEnabled()))
                .append(AiPlatformLayout.featurePill("Context Cache", ai.getAdvanced().isContextCacheEnabled()))
                .append(AiPlatformLayout.featurePill("Observability", ai.getAdvanced().isObservabilityEnabled()))
                .append(AiPlatformLayout.featurePill("GPU Scheduling", ai.getAdvanced().isGpuSchedulingEnabled()))
                .append("</div>");
        body.append("<div class=\"soft-list\">");
        softItem(body, "OpenAPI Spec", contextPath + "/api-docs", "Generated contract for the standalone AI Platform module.");
        softItem(body, "Developer Portal", contextPath + "/api-docs/ui", "Portal page with a built-in quick start, spec viewer, and curl samples.");
        softItem(body, "Gateway Surface", contextPath + "/gateway/*", "Public route, infer, and stream APIs driven by current platform settings.");
        body.append("</div></div>");
        body.append("</section>");

        body.append("<section class=\"grid\" id=\"sandbox\">");
        body.append("<div class=\"panel span-7\">");
        titleBlock(body, "Gateway Sandbox", "Exercise route and inference behavior from the same configuration that powers the console");
        body.append("<div class=\"soft-list\">");
        body.append("<div class=\"soft-item\"><strong>Prompt</strong><span>Try a chat, vision, or recommendation request and inspect how the gateway decides.</span></div>");
        body.append("</div>");
        body.append("<div style=\"display:grid;gap:12px;margin-top:14px;\">");
        body.append("<select id=\"gatewayType\" style=\"padding:14px 16px;border-radius:14px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);\">")
                .append("<option value=\"AUTO\">AUTO</option>")
                .append("<option value=\"CHAT\">CHAT</option>")
                .append("<option value=\"VISION\">VISION</option>")
                .append("<option value=\"RECOMMENDATION\">RECOMMENDATION</option>")
                .append("</select>");
        body.append("<input id=\"gatewaySession\" type=\"text\" value=\"console-demo\" style=\"padding:14px 16px;border-radius:14px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);\" placeholder=\"Session ID\">");
        body.append("<textarea id=\"gatewayPrompt\" style=\"min-height:148px;padding:14px 16px;border-radius:18px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);resize:vertical;\">Recommend three starter products for a new mobile customer.</textarea>");
        body.append("<div class=\"toolbar\">")
                .append("<button type=\"button\" onclick=\"callGateway('route')\">Route</button>")
                .append("<button type=\"button\" onclick=\"callGateway('infer')\">Infer</button>")
                .append("<button type=\"button\" onclick=\"openGatewayStream()\">Stream</button>")
                .append("</div>");
        body.append("</div></div>");

        body.append("<div class=\"panel span-5\">");
        titleBlock(body, "Gateway Output", "Live response from the built-in AI gateway module");
        body.append("<pre class=\"json-box\" id=\"gatewayOutput\">Run Route or Infer to inspect the response envelope.</pre>");
        body.append("</div></section>");

        body.append("<section class=\"grid\" id=\"registry\">");
        body.append("<div class=\"panel span-7\">");
        titleBlock(body, "Registry Workbench", "Register a model version, promote canaries, and inspect the standalone control plane state");
        body.append("<div class=\"soft-list\">");
        softItem(body, "Registry API", contextPath + "/api/models", "Protected control plane endpoint for in-memory model registration and listing.");
        softItem(body, "Version API", contextPath + "/api/models/{name}/versions/{version}/status", "Promote ACTIVE, keep CANARY, or retire a version without touching webadmin.");
        body.append("</div>");
        body.append("<div style=\"display:grid;gap:12px;margin-top:14px;\">");
        body.append("<input id=\"registryModelName\" type=\"text\" value=\"llm-general\" style=\"padding:14px 16px;border-radius:14px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);\" placeholder=\"Model name\">");
        body.append("<div style=\"display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;\">");
        body.append("<input id=\"registryCategory\" type=\"text\" value=\"LLM\" style=\"padding:14px 16px;border-radius:14px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);\" placeholder=\"Category\">");
        body.append("<input id=\"registryProvider\" type=\"text\" value=\"builtin\" style=\"padding:14px 16px;border-radius:14px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);\" placeholder=\"Provider\">");
        body.append("<input id=\"registryVersion\" type=\"text\" value=\"v2\" style=\"padding:14px 16px;border-radius:14px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);\" placeholder=\"Version\">");
        body.append("<input id=\"registryLatency\" type=\"number\" value=\"210\" style=\"padding:14px 16px;border-radius:14px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);\" placeholder=\"Latency ms\">");
        body.append("<input id=\"registryAccuracy\" type=\"number\" value=\"89\" style=\"padding:14px 16px;border-radius:14px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);\" placeholder=\"Accuracy\">");
        body.append("<select id=\"registryStatus\" style=\"padding:14px 16px;border-radius:14px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);\"><option value=\"CANARY\">CANARY</option><option value=\"ACTIVE\">ACTIVE</option><option value=\"INACTIVE\">INACTIVE</option><option value=\"DEPRECATED\">DEPRECATED</option></select>");
        body.append("</div>");
        body.append("<div class=\"toolbar\"><button type=\"button\" onclick=\"registerRegistryModel()\">Register Version</button><button type=\"button\" onclick=\"updateRegistryStatus('ACTIVE')\">Promote Active</button><button type=\"button\" onclick=\"updateRegistryStatus('CANARY')\">Mark Canary</button></div>");
        body.append("<pre class=\"json-box\" id=\"registryMutationOutput\">Submit a model registration or version status change.</pre>");
        body.append("</div></div>");
        body.append("<div class=\"panel span-5\">");
        titleBlock(body, "Registry Snapshot", "Current registered models and active versions");
        body.append("<div class=\"toolbar\"><button type=\"button\" onclick=\"refreshRegistry()\">Refresh Registry</button></div>");
        body.append("<pre class=\"json-box\" id=\"registryJson\">Loading...</pre>");
        body.append("</div></section>");

        body.append("<section class=\"grid\" id=\"usage\">");
        body.append("<div class=\"panel span-5\">");
        titleBlock(body, "Usage and Metering", "Gateway traffic, registry mutations, and token volume from the built-in platform services");
        body.append("<div class=\"toolbar\"><button type=\"button\" onclick=\"refreshUsage()\">Refresh Usage</button></div>");
        body.append("<pre class=\"json-box\" id=\"usageJson\">Loading...</pre>");
        body.append("</div>");
        body.append("<div class=\"panel span-7\">");
        titleBlock(body, "Control Plane Surface", "Operational endpoints exposed by the standalone module");
        body.append("<div class=\"soft-list\">");
        softItem(body, "Overview", contextPath + "/api/overview", "High-level platform state including registry and usage summaries.");
        softItem(body, "Usage", contextPath + "/api/usage", "Metering-style counters for route, infer, stream, cache hits, and tokens.");
        softItem(body, "Metrics Alias", contextPath + "/api/metrics", "Alternate path for usage polling and observability dashboards.");
        body.append("</div></div></section>");
        body.append("<section class=\"grid\" id=\"published\">");
        body.append("<div class=\"panel span-7\">");
        titleBlock(body, "Published APIs", "Auto-generated service endpoints published from the active model registry");
        body.append("<div class=\"soft-list\">");
        softItem(body, "Public Invoke Root", contextPath + "/invoke", "Lists generated REST endpoints that can be called without console authentication.");
        softItem(body, "Published API Index", contextPath + "/api/published-apis", "Authenticated control plane view of published endpoints and active versions.");
        body.append("</div>");
        body.append("<div style=\"display:grid;gap:12px;margin-top:14px;\">");
        body.append("<input id=\"publishedModelName\" type=\"text\" value=\"llm-general\" style=\"padding:14px 16px;border-radius:14px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);\" placeholder=\"Published model\">");
        body.append("<textarea id=\"publishedPrompt\" style=\"min-height:124px;padding:14px 16px;border-radius:18px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);resize:vertical;\">Summarize onboarding guidance for a new enterprise customer.</textarea>");
        body.append("<div class=\"toolbar\"><button type=\"button\" onclick=\"refreshPublishedApis()\">Refresh APIs</button><button type=\"button\" onclick=\"invokePublishedModel()\">Invoke Published API</button></div>");
        body.append("</div>");
        body.append("<pre class=\"json-box\" id=\"publishedMutationOutput\">Load published endpoints or invoke a generated model API.</pre>");
        body.append("</div>");
        body.append("<div class=\"panel span-5\">");
        titleBlock(body, "Published API Snapshot", "Generated endpoint inventory and billing preview");
        body.append("<div class=\"toolbar\"><button type=\"button\" onclick=\"refreshBilling()\">Refresh Billing</button></div>");
        body.append("<pre class=\"json-box\" id=\"publishedJson\">Loading...</pre>");
        body.append("<pre class=\"json-box\" id=\"billingJson\">Loading billing preview...</pre>");
        body.append("</div></section>");

        body.append("<section class=\"grid\" id=\"tuning\">");
        body.append("<div class=\"panel span-7\">");
        titleBlock(body, "Fine-Tuning Lab", "Launch and track mock fine-tuning jobs that materialize runtime model profiles");
        body.append("<div style=\"display:grid;gap:12px;margin-top:14px;\">");
        body.append("<input id=\"tuningBaseModel\" type=\"text\" value=\"llm-general\" style=\"padding:14px 16px;border-radius:14px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);\" placeholder=\"Base model\">");
        body.append("<input id=\"tuningDataset\" type=\"text\" value=\"s3://datasets/customer-support-v1.jsonl\" style=\"padding:14px 16px;border-radius:14px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);\" placeholder=\"Dataset URI\">");
        body.append("<div style=\"display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;\">");
        body.append("<input id=\"tuningTenant\" type=\"text\" value=\"tenant-a\" style=\"padding:14px 16px;border-radius:14px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);\" placeholder=\"Tenant\">");
        body.append("<input id=\"tuningObjective\" type=\"text\" value=\"customer-support\" style=\"padding:14px 16px;border-radius:14px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);\" placeholder=\"Objective\">");
        body.append("<input id=\"tuningEpochs\" type=\"number\" value=\"3\" style=\"padding:14px 16px;border-radius:14px;border:1px solid rgba(17,24,39,0.08);background:rgba(255,255,255,0.76);\" placeholder=\"Epochs\">");
        body.append("</div>");
        body.append("<div class=\"toolbar\"><button type=\"button\" onclick=\"createFineTuningJob()\">Create Job</button><button type=\"button\" onclick=\"refreshFineTuningJobs()\">Refresh Jobs</button><button type=\"button\" onclick=\"cancelLatestFineTuningJob()\">Cancel Latest</button></div>");
        body.append("<pre class=\"json-box\" id=\"tuningMutationOutput\">Submit a fine-tuning job to materialize a runtime-tuned model.</pre>");
        body.append("</div>");
        body.append("<div class=\"panel span-5\">");
        titleBlock(body, "Fine-Tuning Jobs", "Job queue and promoted tuned models");
        body.append("<pre class=\"json-box\" id=\"tuningJson\">Loading...</pre>");
        body.append("</div></section>");
        body.append("<section class=\"grid\" id=\"roadmap\">");
        body.append("<div class=\"panel span-12\">");
        titleBlock(body, "Evolution Roadmap", "From basic serving to platform commercialization");
        body.append("<div class=\"timeline\">");
        for (ServerConfiguration.RoadmapStage stage : ai.getRoadmap().getStages()) {
            boolean active = stage.getStage() == ai.getRoadmap().getCurrentStage();
            body.append("<div class=\"timeline-item").append(active ? " active" : "").append("\">")
                    .append("<div class=\"timeline-index\">").append(stage.getStage()).append("</div>")
                    .append("<div class=\"timeline-goal\">").append(AiPlatformLayout.escapeHtml(stage.getGoal())).append("</div>")
                    .append("<div class=\"metric-note\">Stage ").append(stage.getStage()).append(active ? " - Current active target" : " - Planned capability set").append("</div>")
                    .append("<div class=\"timeline-caps\">");
            for (String capability : stage.getCapabilities()) {
                body.append("<span>").append(AiPlatformLayout.escapeHtml(capability)).append("</span>");
            }
            body.append("</div></div>");
        }
        body.append("</div></div></section>");

        body.append("<section class=\"grid\" id=\"configuration\">");
        body.append("<div class=\"panel span-7\">");
        titleBlock(body, "Configuration Snapshot", "Current server.aiPlatform YAML summary");
        body.append("<pre class=\"yaml\">").append(AiPlatformLayout.escapeHtml(buildYamlPreview(ai))).append("</pre>");
        body.append("</div>");
        body.append("<div class=\"panel span-5\">");
        titleBlock(body, "Console API", "Operational overview JSON produced by the control plane API");
        body.append("<div class=\"toolbar\"><button type=\"button\" onclick=\"refreshOverview()\">Refresh JSON</button></div>");
        body.append("<pre class=\"json-box\" id=\"overviewJson\">Loading...</pre>");
        body.append("</div></section>");
        body.append("</div>");

        body.append("<script>")
                .append("function showJson(id,text){try{document.getElementById(id).textContent=JSON.stringify(JSON.parse(text),null,2);}catch(err){document.getElementById(id).textContent=text;}}")
                .append("async function refreshOverview(){const res=await fetch('").append(AiPlatformLayout.escapeHtml(contextPath)).append("/api/overview');const text=await res.text();showJson('overviewJson',text);}")
                .append("async function refreshRegistry(){const res=await fetch('").append(AiPlatformLayout.escapeHtml(contextPath)).append("/api/models');const text=await res.text();showJson('registryJson',text);}")
                .append("async function refreshUsage(){const res=await fetch('").append(AiPlatformLayout.escapeHtml(contextPath)).append("/api/usage');const text=await res.text();showJson('usageJson',text);}")
                .append("async function refreshPublishedApis(){const res=await fetch('").append(AiPlatformLayout.escapeHtml(contextPath)).append("/api/published-apis');const text=await res.text();showJson('publishedJson',text);}")
                .append("async function refreshBilling(){const res=await fetch('").append(AiPlatformLayout.escapeHtml(contextPath)).append("/api/billing');const text=await res.text();showJson('billingJson',text);}")
                .append("async function refreshFineTuningJobs(){const res=await fetch('").append(AiPlatformLayout.escapeHtml(contextPath)).append("/api/fine-tuning/jobs');const text=await res.text();showJson('tuningJson',text);window.__latestTuningJobs=text;}")
                .append("function fineTuningPayload(){return {baseModel:document.getElementById('tuningBaseModel').value,datasetUri:document.getElementById('tuningDataset').value,tenant:document.getElementById('tuningTenant').value,objective:document.getElementById('tuningObjective').value,epochs:Number(document.getElementById('tuningEpochs').value||0)};}")
                .append("async function createFineTuningJob(){const res=await fetch('").append(AiPlatformLayout.escapeHtml(contextPath)).append("/api/fine-tuning/jobs',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(fineTuningPayload())});const text=await res.text();showJson('tuningMutationOutput',text);refreshFineTuningJobs();refreshRegistry();refreshOverview();}")
                .append("async function cancelLatestFineTuningJob(){let id='';try{id=JSON.parse(window.__latestTuningJobs||'{}').jobs?.[0]?.jobId||'';}catch(err){id='';}if(!id){document.getElementById('tuningMutationOutput').textContent='No fine-tuning job available to cancel.';return;}const res=await fetch('").append(AiPlatformLayout.escapeHtml(contextPath)).append("/api/fine-tuning/jobs/'+id+'/cancel',{method:'POST'});const text=await res.text();showJson('tuningMutationOutput',text);refreshFineTuningJobs();refreshRegistry();refreshOverview();}")
                .append("async function invokePublishedModel(){const model=encodeURIComponent(document.getElementById('publishedModelName').value);const prompt=encodeURIComponent(document.getElementById('publishedPrompt').value);const res=await fetch('").append(AiPlatformLayout.escapeHtml(contextPath)).append("/invoke/'+model+'?sessionId=published-demo&prompt='+prompt);const text=await res.text();showJson('publishedMutationOutput',text);refreshUsage();refreshBilling();}")
                .append("function registryPayload(){return {name:document.getElementById('registryModelName').value,category:document.getElementById('registryCategory').value,provider:document.getElementById('registryProvider').value,version:document.getElementById('registryVersion').value,latencyTier:'balanced',latencyMs:Number(document.getElementById('registryLatency').value||0),accuracyScore:Number(document.getElementById('registryAccuracy').value||0),defaultSelected:false,enabled:true,status:document.getElementById('registryStatus').value,source:'runtime'};}")
                .append("async function registerRegistryModel(){const res=await fetch('").append(AiPlatformLayout.escapeHtml(contextPath)).append("/api/models',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(registryPayload())});const text=await res.text();showJson('registryMutationOutput',text);refreshRegistry();refreshUsage();refreshOverview();}")
                .append("async function updateRegistryStatus(state){const name=encodeURIComponent(document.getElementById('registryModelName').value);const version=encodeURIComponent(document.getElementById('registryVersion').value);const res=await fetch('").append(AiPlatformLayout.escapeHtml(contextPath)).append("/api/models/'+name+'/versions/'+version+'/status?state='+encodeURIComponent(state),{method:'POST'});const text=await res.text();showJson('registryMutationOutput',text);refreshRegistry();refreshUsage();refreshOverview();}")
                .append("async function callGateway(mode){const payload={requestType:document.getElementById('gatewayType').value,prompt:document.getElementById('gatewayPrompt').value,sessionId:document.getElementById('gatewaySession').value,stream:mode==='stream'};const res=await fetch('").append(AiPlatformLayout.escapeHtml(contextPath)).append("/gateway/'+mode,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});const text=await res.text();try{document.getElementById('gatewayOutput').textContent=JSON.stringify(JSON.parse(text),null,2);}catch(err){document.getElementById('gatewayOutput').textContent=text;}}")
                .append("function openGatewayStream(){const type=encodeURIComponent(document.getElementById('gatewayType').value);const sessionId=encodeURIComponent(document.getElementById('gatewaySession').value);const prompt=encodeURIComponent(document.getElementById('gatewayPrompt').value);window.open('").append(AiPlatformLayout.escapeHtml(contextPath)).append("/gateway/stream?requestType='+type+'&sessionId='+sessionId+'&prompt='+prompt,'_blank');}")
                .append("refreshOverview();refreshRegistry();refreshUsage();refreshPublishedApis();refreshBilling();refreshFineTuningJobs();")
                .append("</script>");
        resp.getWriter().write(AiPlatformLayout.page("Velo AI Platform", configuration, body.toString()));
    }

    private static void metricPanel(StringBuilder body, String spanCss, String kicker, String value, String note) {
        body.append("<div class=\"panel ").append(spanCss).append("\"><div class=\"metric-kicker\">")
                .append(AiPlatformLayout.escapeHtml(kicker))
                .append("</div><div class=\"metric-value\">")
                .append(AiPlatformLayout.escapeHtml(value))
                .append("</div><div class=\"metric-note\">")
                .append(AiPlatformLayout.escapeHtml(note))
                .append("</div></div>");
    }

    private static void titleBlock(StringBuilder body, String title, String subtitle) {
        body.append("<div class=\"section-title\"><div><h2>")
                .append(AiPlatformLayout.escapeHtml(title))
                .append("</h2><p>")
                .append(AiPlatformLayout.escapeHtml(subtitle))
                .append("</p></div></div>");
    }

    private static void softItem(StringBuilder body, String title, String value, String description) {
        body.append("<div class=\"soft-item\"><strong>")
                .append(AiPlatformLayout.escapeHtml(title))
                .append(" - ")
                .append(AiPlatformLayout.escapeHtml(value))
                .append("</strong><span>")
                .append(AiPlatformLayout.escapeHtml(description))
                .append("</span></div>");
    }

    private static String buildYamlPreview(ServerConfiguration.AiPlatform ai) {
        List<ServerConfiguration.ModelProfile> models = ai.getServing().getModels();
        List<ServerConfiguration.RoutePolicy> policies = ai.getServing().getRoutePolicies();
        String defaultModel = models.stream().filter(ServerConfiguration.ModelProfile::isDefaultSelected)
                .findFirst().map(ServerConfiguration.ModelProfile::getName).orElse(models.get(0).getName());
        return """
                server:
                  aiPlatform:
                    enabled: %s
                    mode: %s
                    console:
                      enabled: %s
                      contextPath: %s
                    serving:
                      modelRouterEnabled: %s
                      abTestingEnabled: %s
                      autoModelSelectionEnabled: %s
                      ensembleServingEnabled: %s
                      edgeAiEnabled: %s
                      defaultStrategy: %s
                      routerTimeoutMillis: %d
                      targetP99LatencyMs: %d
                      models: %d
                      routePolicies: %d
                      defaultModel: %s
                    platform:
                      modelRegistrationEnabled: %s
                      autoApiGenerationEnabled: %s
                      versionManagementEnabled: %s
                      billingEnabled: %s
                      developerPortalEnabled: %s
                      multiTenantEnabled: %s
                      versioningStrategy: %s
                    differentiation:
                      aiOptimizedWasEnabled: %s
                      requestRoutingEnabled: %s
                      streamingResponseEnabled: %s
                      pluginFrameworkEnabled: %s
                      runtimeEngine: %s
                    advanced:
                      promptRoutingEnabled: %s
                      promptRoutingMode: %s
                      contextCacheEnabled: %s
                      contextCacheTtlSeconds: %d
                      aiGatewayEnabled: %s
                      fineTuningApiEnabled: %s
                      observabilityEnabled: %s
                      gpuSchedulingEnabled: %s
                    roadmap:
                      currentStage: %d
                """.formatted(
                ai.isEnabled(),
                ai.getMode(),
                ai.getConsole().isEnabled(),
                ai.getConsole().getContextPath(),
                ai.getServing().isModelRouterEnabled(),
                ai.getServing().isAbTestingEnabled(),
                ai.getServing().isAutoModelSelectionEnabled(),
                ai.getServing().isEnsembleServingEnabled(),
                ai.getServing().isEdgeAiEnabled(),
                ai.getServing().getDefaultStrategy(),
                ai.getServing().getRouterTimeoutMillis(),
                ai.getServing().getTargetP99LatencyMs(),
                models.size(),
                policies.size(),
                defaultModel,
                ai.getPlatform().isModelRegistrationEnabled(),
                ai.getPlatform().isAutoApiGenerationEnabled(),
                ai.getPlatform().isVersionManagementEnabled(),
                ai.getPlatform().isBillingEnabled(),
                ai.getPlatform().isDeveloperPortalEnabled(),
                ai.getPlatform().isMultiTenantEnabled(),
                ai.getPlatform().getVersioningStrategy(),
                ai.getDifferentiation().isAiOptimizedWasEnabled(),
                ai.getDifferentiation().isRequestRoutingEnabled(),
                ai.getDifferentiation().isStreamingResponseEnabled(),
                ai.getDifferentiation().isPluginFrameworkEnabled(),
                ai.getDifferentiation().getRuntimeEngine(),
                ai.getAdvanced().isPromptRoutingEnabled(),
                ai.getAdvanced().getPromptRoutingMode(),
                ai.getAdvanced().isContextCacheEnabled(),
                ai.getAdvanced().getContextCacheTtlSeconds(),
                ai.getAdvanced().isAiGatewayEnabled(),
                ai.getAdvanced().isFineTuningApiEnabled(),
                ai.getAdvanced().isObservabilityEnabled(),
                ai.getAdvanced().isGpuSchedulingEnabled(),
                ai.getRoadmap().getCurrentStage()
        );
    }
}