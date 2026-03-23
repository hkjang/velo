package io.velo.was.aiplatform.acp.servlet;

import io.velo.was.aiplatform.acp.*;
import io.velo.was.aiplatform.agp.AgpChannel;
import io.velo.was.aiplatform.agp.AgpGateway;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Google A2A (Agent-to-Agent) 프로토콜 호환 서블릿.
 *
 * <p>JSON-RPC 2.0 over HTTP로 에이전트 간 협업을 지원한다.
 * 기존 ACP/AGP 인프라를 재사용하며, A2A 표준 메서드 이름을 매핑한다.</p>
 *
 * <h3>지원 메서드</h3>
 * <ul>
 *   <li>{@code tasks/send} — 태스크 전송 (동기 응답)</li>
 *   <li>{@code tasks/sendSubscribe} — 태스크 전송 + SSE 구독</li>
 *   <li>{@code tasks/get} — 태스크 조회</li>
 *   <li>{@code tasks/cancel} — 태스크 취소</li>
 *   <li>{@code message/send} — 에이전트 간 직접 메시지</li>
 *   <li>{@code agent/authenticatedExtendedCard} — 에이전트 카드 조회</li>
 * </ul>
 *
 * <h3>사용 예</h3>
 * <pre>{@code
 * POST /ai-platform/a2a
 * Content-Type: application/json
 *
 * {
 *   "jsonrpc": "2.0",
 *   "method": "tasks/send",
 *   "params": {
 *     "message": {
 *       "role": "user",
 *       "parts": [{"type": "text", "text": "이 문서를 번역해주세요."}]
 *     },
 *     "toAgent": "translation-agent",
 *     "capability": "translation"
 *   },
 *   "id": 1
 * }
 * }</pre>
 */
public class A2aServlet extends HttpServlet {

    private final AgpGateway gateway;

    public A2aServlet(AgpGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * POST /a2a — JSON-RPC 2.0 디스패치.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");

        String body = req.getReader().lines().collect(Collectors.joining("\n"));
        if (body == null || body.isBlank()) {
            resp.getWriter().write(A2aJsonRpc.parseError());
            return;
        }

        String method = A2aJsonRpc.extractMethod(body);
        String id = A2aJsonRpc.extractId(body);

        if (method.isBlank()) {
            resp.getWriter().write(A2aJsonRpc.invalidRequest(id));
            return;
        }

        String remoteAddr = req.getRemoteAddr();

        switch (method) {
            case "tasks/send" -> handleTasksSend(body, id, remoteAddr, resp);
            case "tasks/sendSubscribe" -> handleTasksSendSubscribe(body, id, remoteAddr, req, resp);
            case "tasks/get" -> handleTasksGet(body, id, resp);
            case "tasks/cancel" -> handleTasksCancel(body, id, resp);
            case "message/send" -> handleMessageSend(body, id, resp);
            case "agent/authenticatedExtendedCard" -> handleAgentCard(id, req, resp);
            default -> resp.getWriter().write(A2aJsonRpc.methodNotFound(id, method));
        }
    }

    /**
     * GET /a2a — SSE 스트리밍 (tasks/sendSubscribe의 응답 채널).
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String taskId = req.getParameter("taskId");
        if (taskId == null || taskId.isBlank()) {
            resp.setContentType("application/json; charset=UTF-8");
            // 에이전트 카드 반환 (GET 기본)
            AcpAgentCard selfCard = buildSelfCard(req);
            resp.getWriter().write(A2aJsonRpc.success("null", selfCard.toJson()));
            return;
        }

        // SSE 스트리밍
        resp.setContentType("text/event-stream; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");

        AcpTask task = gateway.taskManager().getTask(taskId);
        PrintWriter out = resp.getWriter();
        if (task == null) {
            out.write("event: error\ndata: {\"error\":\"Task not found\"}\n\n");
            out.flush();
            return;
        }

        // 초기 상태 전송
        out.write("event: status\ndata: " + task.toJson() + "\n\n");
        out.flush();

        // 폴링으로 상태 변화 감지 (간단한 구현)
        AcpTask.State lastState = task.state();
        for (int i = 0; i < 60; i++) { // 최대 60초
            try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
            task = gateway.taskManager().getTask(taskId);
            if (task == null) break;
            if (task.state() != lastState) {
                out.write("event: status\ndata: " + task.toJson() + "\n\n");
                out.flush();
                lastState = task.state();
                if (task.state().isTerminal()) break;
            }
        }

        out.write("event: done\ndata: {\"taskId\":\"" + AcpMessage.esc(taskId) + "\"}\n\n");
        out.flush();
    }

    // ── JSON-RPC 메서드 핸들러 ──

    private void handleTasksSend(String body, String id, String remoteAddr, HttpServletResponse resp) throws IOException {
        String toAgent = A2aJsonRpc.extractParam(body, "toAgent");
        String capability = A2aJsonRpc.extractParam(body, "capability");
        String fromAgent = A2aJsonRpc.extractParam(body, "fromAgent");
        if (fromAgent.isBlank()) fromAgent = "a2a-client-" + remoteAddr;

        AcpMessage message = A2aJsonRpc.toAcpMessage(body);
        Map<String, String> meta = Map.of("protocol", "a2a", "remoteAddr", remoteAddr);
        AcpTask task = gateway.routeTask(fromAgent, toAgent.isBlank() ? null : toAgent, message, capability, meta);

        resp.getWriter().write(A2aJsonRpc.success(id, taskToA2aResult(task)));
    }

    private void handleTasksSendSubscribe(String body, String id, String remoteAddr,
                                           HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // 동기적으로 태스크 생성 후 SSE URL 반환
        String toAgent = A2aJsonRpc.extractParam(body, "toAgent");
        String capability = A2aJsonRpc.extractParam(body, "capability");
        String fromAgent = A2aJsonRpc.extractParam(body, "fromAgent");
        if (fromAgent.isBlank()) fromAgent = "a2a-client-" + remoteAddr;

        AcpMessage message = A2aJsonRpc.toAcpMessage(body);
        Map<String, String> meta = Map.of("protocol", "a2a", "remoteAddr", remoteAddr);
        AcpTask task = gateway.routeTask(fromAgent, toAgent.isBlank() ? null : toAgent, message, capability, meta);

        String sseUrl = req.getContextPath() + "/a2a?taskId=" + task.taskId();
        String result = "{\"task\":" + task.toJson() + ",\"subscribeUrl\":\"" + AcpMessage.esc(sseUrl) + "\"}";
        resp.getWriter().write(A2aJsonRpc.success(id, result));
    }

    private void handleTasksGet(String body, String id, HttpServletResponse resp) throws IOException {
        String taskId = A2aJsonRpc.extractParam(body, "taskId");
        if (taskId.isBlank()) taskId = A2aJsonRpc.extractParam(body, "id");
        if (taskId.isBlank()) {
            resp.getWriter().write(A2aJsonRpc.error(id, -32602, "taskId is required"));
            return;
        }
        AcpTask task = gateway.taskManager().getTask(taskId);
        if (task == null) {
            resp.getWriter().write(A2aJsonRpc.error(id, -32001, "Task not found: " + taskId));
            return;
        }
        resp.getWriter().write(A2aJsonRpc.success(id, taskToA2aResult(task)));
    }

    private void handleTasksCancel(String body, String id, HttpServletResponse resp) throws IOException {
        String taskId = A2aJsonRpc.extractParam(body, "taskId");
        if (taskId.isBlank()) taskId = A2aJsonRpc.extractParam(body, "id");
        if (taskId.isBlank()) {
            resp.getWriter().write(A2aJsonRpc.error(id, -32602, "taskId is required"));
            return;
        }
        AcpTask task = gateway.taskManager().cancelTask(taskId);
        if (task == null) {
            resp.getWriter().write(A2aJsonRpc.error(id, -32001, "Task not found or already terminal"));
            return;
        }
        resp.getWriter().write(A2aJsonRpc.success(id, taskToA2aResult(task)));
    }

    private void handleMessageSend(String body, String id, HttpServletResponse resp) throws IOException {
        String from = A2aJsonRpc.extractParam(body, "fromAgent");
        String to = A2aJsonRpc.extractParam(body, "toAgent");
        if (from.isBlank() || to.isBlank()) {
            resp.getWriter().write(A2aJsonRpc.error(id, -32602, "fromAgent and toAgent are required"));
            return;
        }
        AcpMessage message = A2aJsonRpc.toAcpMessage(body);
        AgpChannel channel = gateway.forwardMessage(from, to, message);
        resp.getWriter().write(A2aJsonRpc.success(id, channel.toJson()));
    }

    private void handleAgentCard(String id, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        AcpAgentCard card = buildSelfCard(req);
        resp.getWriter().write(A2aJsonRpc.success(id, card.toJson()));
    }

    // ── 유틸 ──

    /** AcpTask를 A2A 표준 결과 포맷으로 변환. */
    private static String taskToA2aResult(AcpTask task) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("{\"id\":\"").append(AcpMessage.esc(task.taskId())).append("\"");
        sb.append(",\"status\":{\"state\":\"").append(mapState(task.state())).append("\"}");
        if (task.output() != null) {
            sb.append(",\"artifacts\":[{\"parts\":[");
            List<AcpMessage.Part> parts = task.output().parts();
            for (int i = 0; parts != null && i < parts.size(); i++) {
                if (i > 0) sb.append(',');
                AcpMessage.Part p = parts.get(i);
                sb.append("{\"type\":\"").append(partType(p)).append("\"");
                if (p.text() != null) sb.append(",\"text\":\"").append(AcpMessage.esc(p.text())).append("\"");
                if (p.dataBase64() != null) sb.append(",\"data\":\"").append(AcpMessage.esc(p.dataBase64())).append("\"");
                sb.append('}');
            }
            sb.append("]}]");
        }
        sb.append(",\"history\":[");
        List<AcpMessage> history = task.history();
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(history.get(i).toJson());
        }
        sb.append("]}");
        return sb.toString();
    }

    /** AcpTask.State → A2A 표준 상태 문자열. */
    private static String mapState(AcpTask.State state) {
        return switch (state) {
            case SUBMITTED -> "submitted";
            case WORKING -> "working";
            case INPUT_REQUIRED -> "input-required";
            case COMPLETED -> "completed";
            case FAILED -> "failed";
            case CANCELED -> "canceled";
        };
    }

    /** Part contentType → A2A Part type. */
    private static String partType(AcpMessage.Part part) {
        if (part.dataBase64() != null) return "file";
        if (part.contentType() != null && part.contentType().contains("json")) return "data";
        return "text";
    }

    private AcpAgentCard buildSelfCard(HttpServletRequest req) {
        String contextPath = req.getContextPath();
        String baseUrl = req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort() + contextPath;
        return new AcpAgentCard(
                "velo-ai-platform", "Velo AI Platform",
                "Multi-LLM gateway with A2A, ACP, AGP, MCP protocol support.",
                "0.6.0", "velo", baseUrl,
                List.of("chat", "vision", "image-generation", "speech-to-text", "text-to-speech",
                        "embedding", "code-generation", "summarization", "translation", "intent-routing"),
                List.of("text", "image", "audio"),
                List.of("a2a", "acp", "agp", "mcp", "openai"),
                "api-key", java.time.Instant.now(),
                Map.of("a2a_endpoint", baseUrl + "/a2a",
                        "mcp_endpoint", baseUrl + "/mcp",
                        "openai_endpoint", baseUrl + "/v1/chat/completions")
        );
    }
}
