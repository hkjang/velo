package io.velo.was.aiplatform.acp.servlet;

import io.velo.was.aiplatform.acp.AcpAgentCard;
import io.velo.was.aiplatform.agp.AgpAgentRegistry;
import io.velo.was.config.ServerConfiguration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * ACP 에이전트 카드 서블릿.
 *
 * <table>
 *   <tr><td>GET /.well-known/agent-card.json</td><td>이 플랫폼의 에이전트 카드</td></tr>
 *   <tr><td>GET /acp/agents</td><td>등록된 에이전트 목록</td></tr>
 *   <tr><td>GET /acp/agents/{id}</td><td>특정 에이전트 카드</td></tr>
 * </table>
 *
 * <p>이 서블릿은 두 경로에 매핑된다:
 * <ul>
 *   <li>{@code /.well-known/*} — 표준 에이전트 카드 발행</li>
 *   <li>에이전트 목록 조회는 {@link AcpTaskServlet}에서 /acp/agents 를 처리하지 않고,
 *       {@link AgpAdminServlet}의 /agp/admin/agents 경로로 처리된다.</li>
 * </ul>
 */
public class AcpAgentCardServlet extends HttpServlet {

    private final ServerConfiguration configuration;
    private final AgpAgentRegistry agentRegistry;

    public AcpAgentCardServlet(ServerConfiguration configuration, AgpAgentRegistry agentRegistry) {
        this.configuration = configuration;
        this.agentRegistry = agentRegistry;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("Access-Control-Allow-Origin", "*");

        String path = req.getPathInfo();
        if (path == null) path = "";

        // /.well-known/agent-card.json 또는 /.well-known/agent.json (A2A 표준)
        if (path.isEmpty() || "/agent-card.json".equals(path) || "/agent.json".equals(path)) {
            AcpAgentCard selfCard = buildSelfAgentCard(req);
            resp.getWriter().write(selfCard.toJson());
            return;
        }

        resp.setStatus(404);
        resp.getWriter().write("{\"error\":\"Not Found\"}");
    }

    /**
     * 이 AI 플랫폼 자체를 에이전트로 광고하는 카드를 생성한다.
     */
    private AcpAgentCard buildSelfAgentCard(HttpServletRequest req) {
        ServerConfiguration.AiPlatform ai = configuration.getServer().getAiPlatform();
        String contextPath = ai.getConsole().getContextPath();
        String baseUrl = req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort() + contextPath;

        return new AcpAgentCard(
                "velo-ai-platform",
                "Velo AI Platform",
                "Multi-LLM gateway with intelligent routing, intent-based model selection, "
                        + "multi-tenant management, and multimodal support (vision, audio, TTS, embedding).",
                "1.0",
                "velo",
                baseUrl,
                List.of("chat", "vision", "image-generation", "speech-to-text", "text-to-speech",
                        "embedding", "code-generation", "summarization", "translation", "intent-routing"),
                List.of("text", "image", "audio"),
                List.of("acp", "agp", "mcp", "openai"),
                "api-key",
                Instant.now(),
                Map.of(
                        "mcp_endpoint", baseUrl + "/mcp",
                        "openai_endpoint", baseUrl + "/v1/chat/completions",
                        "gateway_endpoint", baseUrl + "/gateway",
                        "acp_endpoint", baseUrl + "/acp",
                        "agp_admin_endpoint", baseUrl + "/agp/admin"
                )
        );
    }
}
