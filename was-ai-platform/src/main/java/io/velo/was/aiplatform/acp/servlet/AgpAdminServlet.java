package io.velo.was.aiplatform.acp.servlet;

import io.velo.was.aiplatform.acp.*;
import io.velo.was.aiplatform.agp.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AGP 관리 서블릿 — /agp/admin/* 게이트웨이 관리 API.
 *
 * <table>
 *   <tr><td>POST /agp/admin/agents</td><td>에이전트 등록</td></tr>
 *   <tr><td>DELETE /agp/admin/agents/{id}</td><td>에이전트 해제</td></tr>
 *   <tr><td>GET /agp/admin/agents</td><td>에이전트 목록</td></tr>
 *   <tr><td>POST /agp/admin/routes</td><td>라우팅 규칙 추가</td></tr>
 *   <tr><td>DELETE /agp/admin/routes</td><td>라우팅 규칙 삭제</td></tr>
 *   <tr><td>GET /agp/admin/routes</td><td>라우팅 테이블 조회</td></tr>
 *   <tr><td>GET /agp/admin/channels</td><td>활성 채널 목록</td></tr>
 *   <tr><td>GET /agp/admin/audit</td><td>ACP/AGP 감사 로그</td></tr>
 *   <tr><td>GET /agp/admin/stats</td><td>게이트웨이 통계</td></tr>
 *   <tr><td>POST /agp/admin/resolve</td><td>의도 기반 에이전트 탐색</td></tr>
 * </table>
 */
public class AgpAdminServlet extends HttpServlet {

    private static final Pattern JSON_STRING = Pattern.compile(
            "\"([A-Za-z0-9_]+)\"\\s*:\\s*\"((?:\\\\.|[^\\\"])*)\"", Pattern.DOTALL);
    private static final Pattern JSON_NUMBER = Pattern.compile(
            "\"([A-Za-z0-9_]+)\"\\s*:\\s*(-?\\d+)");

    private final AgpGateway gateway;

    public AgpAdminServlet(AgpGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        json(resp);
        String path = normPath(req.getPathInfo());

        // GET /agp/admin/agents
        if ("/agents".equals(path)) {
            List<AcpAgentCard> agents = gateway.agentRegistry().listAgents();
            String capability = req.getParameter("capability");
            if (capability != null && !capability.isBlank()) {
                agents = gateway.agentRegistry().findByCapability(capability);
            }
            StringBuilder sb = new StringBuilder(4096);
            sb.append("{\"total\":").append(agents.size()).append(",\"agents\":[");
            for (int i = 0; i < agents.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(agents.get(i).toJson());
            }
            sb.append("]}");
            resp.getWriter().write(sb.toString());
            return;
        }

        // GET /agp/admin/agents/{id}
        if (path.startsWith("/agents/")) {
            String agentId = path.substring("/agents/".length());
            AcpAgentCard card = gateway.agentRegistry().find(agentId);
            if (card == null) {
                resp.setStatus(404);
                resp.getWriter().write("{\"error\":\"Agent not found\"}");
                return;
            }
            resp.getWriter().write(card.toJson());
            return;
        }

        // GET /agp/admin/routes
        if ("/routes".equals(path)) {
            List<AgpRouteTable.RouteRule> routes = gateway.routeTable().allRoutes();
            StringBuilder sb = new StringBuilder(2048);
            sb.append("{\"total\":").append(routes.size()).append(",\"routes\":[");
            for (int i = 0; i < routes.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(routes.get(i).toJson());
            }
            sb.append("]}");
            resp.getWriter().write(sb.toString());
            return;
        }

        // GET /agp/admin/channels
        if ("/channels".equals(path)) {
            String agentId = req.getParameter("agentId");
            List<AgpChannel> channels = gateway.getChannels(agentId);
            StringBuilder sb = new StringBuilder(2048);
            sb.append("{\"total\":").append(channels.size()).append(",\"channels\":[");
            for (int i = 0; i < channels.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(channels.get(i).toJson());
            }
            sb.append("]}");
            resp.getWriter().write(sb.toString());
            return;
        }

        // GET /agp/admin/audit
        if ("/audit".equals(path)) {
            int limit = parseInt(req.getParameter("limit"), 50);
            List<AcpAuditEntry> entries = gateway.taskManager().recentAudit(limit);
            StringBuilder sb = new StringBuilder(4096);
            sb.append("{\"total\":").append(entries.size()).append(",\"entries\":[");
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(entries.get(i).toJson());
            }
            sb.append("]}");
            resp.getWriter().write(sb.toString());
            return;
        }

        // GET /agp/admin/stats
        if ("/stats".equals(path)) {
            resp.getWriter().write(gateway.stats().toJson());
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

        // POST /agp/admin/agents — 에이전트 등록
        if ("/agents".equals(path)) {
            String name = extractStr(body, "name");
            String description = extractStr(body, "description");
            String version = extractStr(body, "version");
            String provider = extractStr(body, "provider");
            String endpoint = extractStr(body, "endpoint");
            String capabilities = extractStr(body, "capabilities");
            String modalities = extractStr(body, "modalities");
            String protocols = extractStr(body, "protocols");
            String authScheme = extractStr(body, "authScheme");

            if (name.isBlank()) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"name is required\"}");
                return;
            }

            AcpAgentCard card = new AcpAgentCard(
                    null, name, description, version.isBlank() ? "v1" : version,
                    provider.isBlank() ? "external" : provider,
                    endpoint,
                    splitCsv(capabilities),
                    splitCsv(modalities.isBlank() ? "text" : modalities),
                    splitCsv(protocols.isBlank() ? "acp" : protocols),
                    authScheme.isBlank() ? "none" : authScheme,
                    Instant.now(), Map.of()
            );
            AcpAgentCard registered = gateway.agentRegistry().register(card);
            gateway.taskManager().auditEvent("AGENT_REGISTERED", null, registered.agentId(), null,
                    String.join(",", registered.capabilities()), null, 0, true, null, req.getRemoteAddr());
            resp.setStatus(201);
            resp.getWriter().write(registered.toJson());
            return;
        }

        // POST /agp/admin/routes — 라우팅 규칙 추가
        if ("/routes".equals(path)) {
            String capability = extractStr(body, "capability");
            String agentId = extractStr(body, "agentId");
            int priority = extractInt(body, "priority", 50);
            int weight = extractInt(body, "weight", 100);

            if (capability.isBlank() || agentId.isBlank()) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"capability and agentId are required\"}");
                return;
            }
            AgpRouteTable.RouteRule rule = gateway.routeTable().addRoute(capability, agentId, priority, weight);
            resp.setStatus(201);
            resp.getWriter().write(rule.toJson());
            return;
        }

        // POST /agp/admin/resolve — 의도 기반 에이전트 탐색
        if ("/resolve".equals(path)) {
            String capability = extractStr(body, "capability");
            String modality = extractStr(body, "modality");
            String intent = extractStr(body, "intent");

            List<AcpAgentCard> result;
            if (!intent.isBlank()) {
                AcpAgentCard match = gateway.routeTable().resolveByIntent(intent);
                result = match != null ? List.of(match) : List.of();
            } else if (!capability.isBlank()) {
                result = gateway.routeTable().resolve(capability);
            } else if (!modality.isBlank()) {
                result = gateway.agentRegistry().findByModality(modality);
            } else {
                result = gateway.agentRegistry().listAgents();
            }

            StringBuilder sb = new StringBuilder(2048);
            sb.append("{\"total\":").append(result.size()).append(",\"agents\":[");
            for (int i = 0; i < result.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(result.get(i).toJson());
            }
            sb.append("]}");
            resp.getWriter().write(sb.toString());
            return;
        }

        resp.setStatus(404);
        resp.getWriter().write("{\"error\":\"Not Found\"}");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        json(resp);
        String path = normPath(req.getPathInfo());

        // DELETE /agp/admin/agents/{id}
        if (path.startsWith("/agents/")) {
            String agentId = path.substring("/agents/".length());
            AcpAgentCard removed = gateway.agentRegistry().unregister(agentId);
            if (removed == null) {
                resp.setStatus(404);
                resp.getWriter().write("{\"error\":\"Agent not found\"}");
                return;
            }
            gateway.taskManager().auditEvent("AGENT_UNREGISTERED", null, agentId, null,
                    null, null, 0, true, null, req.getRemoteAddr());
            resp.getWriter().write("{\"removed\":true,\"agentId\":\"" + AcpMessage.esc(agentId) + "\"}");
            return;
        }

        // DELETE /agp/admin/routes?capability=X&agentId=Y
        if ("/routes".equals(path)) {
            String capability = req.getParameter("capability");
            String agentId = req.getParameter("agentId");
            if (capability == null || agentId == null) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"capability and agentId parameters required\"}");
                return;
            }
            boolean removed = gateway.routeTable().removeRoute(capability, agentId);
            resp.getWriter().write("{\"removed\":" + removed + "}");
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

    private static String normPath(String path) {
        return (path == null || path.isBlank()) ? "/" : path;
    }

    private static String extractStr(String body, String field) {
        Matcher m = JSON_STRING.matcher(body == null ? "" : body);
        while (m.find()) { if (field.equals(m.group(1))) return m.group(2); }
        return "";
    }

    private static int extractInt(String body, String field, int fallback) {
        Matcher m = JSON_NUMBER.matcher(body == null ? "" : body);
        while (m.find()) {
            if (field.equals(m.group(1))) {
                try { return Integer.parseInt(m.group(2)); } catch (NumberFormatException e) { return fallback; }
            }
        }
        return fallback;
    }

    private static int parseInt(String v, int fallback) {
        if (v == null || v.isBlank()) return fallback;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return fallback; }
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split("[,;|]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
