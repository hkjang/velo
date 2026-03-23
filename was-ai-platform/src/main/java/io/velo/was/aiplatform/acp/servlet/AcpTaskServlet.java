package io.velo.was.aiplatform.acp.servlet;

import io.velo.was.aiplatform.acp.*;
import io.velo.was.aiplatform.agp.AgpChannel;
import io.velo.was.aiplatform.agp.AgpGateway;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ACP 태스크 API 서블릿 — /acp/* 엔드포인트.
 *
 * <table>
 *   <tr><td>POST /acp/tasks</td><td>태스크 생성 (라우팅 포함)</td></tr>
 *   <tr><td>GET /acp/tasks</td><td>태스크 목록</td></tr>
 *   <tr><td>GET /acp/tasks/{id}</td><td>태스크 상세</td></tr>
 *   <tr><td>POST /acp/tasks/{id}/cancel</td><td>태스크 취소</td></tr>
 *   <tr><td>POST /acp/tasks/{id}/input</td><td>입력 제공</td></tr>
 *   <tr><td>POST /acp/tasks/{id}/complete</td><td>태스크 완료</td></tr>
 *   <tr><td>POST /acp/messages</td><td>에이전트 간 메시지 전달</td></tr>
 * </table>
 */
public class AcpTaskServlet extends HttpServlet {

    private static final Pattern JSON_STRING = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"((?:\\\\.|[^\\\"])*)\"", Pattern.DOTALL);

    private final AgpGateway gateway;

    public AcpTaskServlet(AgpGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        json(resp);
        String path = normPath(req.getPathInfo());

        if ("/tasks".equals(path) || "/".equals(path)) {
            String agentId = req.getParameter("agentId");
            String state = req.getParameter("state");
            int limit = parseInt(req.getParameter("limit"), 50);
            List<AcpTask> tasks = gateway.taskManager().listTasks(agentId, state, limit);
            StringBuilder sb = new StringBuilder(4096);
            sb.append("{\"total\":").append(tasks.size()).append(",\"tasks\":[");
            for (int i = 0; i < tasks.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(tasks.get(i).toSummaryJson());
            }
            sb.append("]}");
            resp.getWriter().write(sb.toString());
            return;
        }

        if (path.startsWith("/tasks/")) {
            String taskId = path.substring("/tasks/".length()).split("/")[0];
            AcpTask task = gateway.taskManager().getTask(taskId);
            if (task == null) {
                resp.setStatus(404);
                resp.getWriter().write("{\"error\":\"Task not found\"}");
                return;
            }
            resp.getWriter().write(task.toJson());
            return;
        }

        resp.setStatus(404);
        resp.getWriter().write("{\"error\":\"Not Found\"}");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        json(resp);
        String path = normPath(req.getPathInfo());
        String body = req.getReader().lines().collect(Collectors.joining("\n"));

        // POST /acp/tasks — 태스크 생성
        if ("/tasks".equals(path)) {
            String fromAgent = extractStr(body, "fromAgent");
            String toAgent = extractStr(body, "toAgent");
            String capability = extractStr(body, "capability");
            String text = extractStr(body, "text");
            if (fromAgent.isBlank()) fromAgent = "client-" + req.getRemoteAddr();
            AcpMessage input = AcpMessage.text("user", text.isBlank() ? body : text);
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("remoteAddr", req.getRemoteAddr());
            if (!capability.isBlank()) meta.put("capability", capability);

            AcpTask task = gateway.routeTask(fromAgent, toAgent.isBlank() ? null : toAgent, input, capability, meta);
            resp.setStatus(201);
            resp.getWriter().write(task.toJson());
            return;
        }

        // POST /acp/tasks/{id}/cancel
        if (path.matches("/tasks/[^/]+/cancel")) {
            String taskId = path.split("/")[2];
            AcpTask task = gateway.taskManager().cancelTask(taskId);
            if (task == null) { notFound(resp); return; }
            resp.getWriter().write(task.toJson());
            return;
        }

        // POST /acp/tasks/{id}/input
        if (path.matches("/tasks/[^/]+/input")) {
            String taskId = path.split("/")[2];
            String text = extractStr(body, "text");
            AcpMessage input = AcpMessage.text("user", text.isBlank() ? body : text);
            AcpTask task = gateway.taskManager().provideInput(taskId, input);
            if (task == null) { notFound(resp); return; }
            resp.getWriter().write(task.toJson());
            return;
        }

        // POST /acp/tasks/{id}/complete
        if (path.matches("/tasks/[^/]+/complete")) {
            String taskId = path.split("/")[2];
            String text = extractStr(body, "text");
            AcpMessage output = AcpMessage.text("agent", text.isBlank() ? "Completed." : text);
            AcpTask task = gateway.taskManager().completeTask(taskId, output);
            if (task == null) { notFound(resp); return; }
            resp.getWriter().write(task.toJson());
            return;
        }

        // POST /acp/messages — 에이전트 간 직접 메시지
        if ("/messages".equals(path)) {
            String from = extractStr(body, "fromAgent");
            String to = extractStr(body, "toAgent");
            String text = extractStr(body, "text");
            if (from.isBlank() || to.isBlank()) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"fromAgent and toAgent are required\"}");
                return;
            }
            AcpMessage msg = AcpMessage.text("agent", text.isBlank() ? body : text);
            AgpChannel channel = gateway.forwardMessage(from, to, msg);
            resp.getWriter().write(channel.toJson());
            return;
        }

        resp.setStatus(404);
        resp.getWriter().write("{\"error\":\"Not Found\"}");
    }

    private static void json(HttpServletResponse resp) {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
    }

    private static void notFound(HttpServletResponse resp) throws IOException {
        resp.setStatus(404);
        resp.getWriter().write("{\"error\":\"Task not found\"}");
    }

    private static String normPath(String path) {
        return (path == null || path.isBlank()) ? "/" : path;
    }

    private static String extractStr(String body, String field) {
        Matcher m = JSON_STRING.matcher(body == null ? "" : body);
        while (m.find()) {
            if (field.equals(m.group(1))) return m.group(2);
        }
        return "";
    }

    private static int parseInt(String v, int fallback) {
        if (v == null || v.isBlank()) return fallback;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return fallback; }
    }
}
