package io.velo.was.webadmin.api;

import io.velo.was.admin.VeloAdmin;
import io.velo.was.admin.cli.Command;
import io.velo.was.admin.cli.CommandContext;
import io.velo.was.admin.cli.CommandRegistry;
import io.velo.was.admin.cli.CommandResult;
import io.velo.was.admin.client.AdminClient;
import io.velo.was.admin.client.LocalAdminClient;
import io.velo.was.config.ServerConfiguration;
import io.velo.was.observability.MetricsCollector;
import io.velo.was.webadmin.audit.AuditEngine;
import io.velo.was.webadmin.config.ConfigChangeEngine;
import io.velo.was.webadmin.config.ConfigDraft;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API servlet for Velo Web Admin.
 * <p>
 * GET endpoints provide JSON data for dashboard widgets.
 * POST /api/execute delegates to the full {@link CommandRegistry} from was-admin,
 * giving 100% CLI command coverage through the web console.
 */
public class AdminApiServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AdminApiServlet.class);
    private final ServerConfiguration configuration;
    private final CommandRegistry commandRegistry;
    private final AdminClient adminClient;

    public AdminApiServlet(ServerConfiguration configuration) {
        this.configuration = configuration;
        this.commandRegistry = VeloAdmin.createRegistry();
        this.adminClient = new LocalAdminClient(configuration);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache, no-store");

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "/";

        PrintWriter out = resp.getWriter();

        switch (pathInfo) {
            case "/status" -> writeStatus(out);
            case "/servers" -> writeServers(out);
            case "/applications" -> writeApplications(out);
            case "/resources" -> writeResources(out);
            case "/threads" -> writeThreads(out);
            case "/monitoring" -> writeMonitoring(out);
            case "/commands" -> writeCommands(out);
            case "/audit" -> writeAudit(req, out);
            case "/drafts" -> writeDrafts(req, out);
            case "/config" -> writeConfig(out);
            case "/jvm" -> writeJvm(out);
            case "/system" -> writeSystem(out);
            case "/threadpools" -> writeThreadPools(out);
            case "/loggers" -> writeLoggers(out);
            case "/users" -> writeUsers(out);
            case "/clusters" -> writeClusters(out);
            case "/nodes" -> writeNodes(out);
            default -> {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.write("""
                        {"error":"Not Found","path":"%s"}""".formatted(escapeJson(pathInfo)));
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json; charset=UTF-8");
        resp.setCharacterEncoding("UTF-8");

        String pathInfo = req.getPathInfo();
        if ("/execute".equals(pathInfo)) {
            executeCommand(req, resp);
        } else if ("/config/save".equals(pathInfo)) {
            handleConfigSave(req, resp);
        } else if ("/loggers/set".equals(pathInfo)) {
            handleSetLogLevel(req, resp);
        } else if ("/drafts/create".equals(pathInfo)) {
            handleDraftCreate(req, resp);
        } else if (pathInfo != null && pathInfo.startsWith("/drafts/") && pathInfo.endsWith("/action")) {
            handleDraftAction(req, resp, pathInfo);
        } else if ("/users/create".equals(pathInfo)) {
            handleUserCreate(req, resp);
        } else if ("/users/remove".equals(pathInfo)) {
            handleUserRemove(req, resp);
        } else if ("/users/change-password".equals(pathInfo)) {
            handleChangePassword(req, resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("""
                    {"error":"Not Found"}""");
        }
    }

    // ── GET endpoints ──────────────────────────────────────────

    private void writeStatus(PrintWriter out) {
        ServerConfiguration.Server server = configuration.getServer();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        long heapUsed = memory.getHeapMemoryUsage().getUsed();
        long heapMax = memory.getHeapMemoryUsage().getMax();
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();

        out.write("""
                {"status":"RUNNING","serverName":"%s","nodeId":"%s",\
                "host":"%s","port":%d,"tlsEnabled":%s,\
                "uptimeMs":%d,"heapUsedBytes":%d,"heapMaxBytes":%d,\
                "threadCount":%d,"availableProcessors":%d,\
                "javaVersion":"%s"}""".formatted(
                escapeJson(server.getName()),
                escapeJson(server.getNodeId()),
                escapeJson(server.getListener().getHost()),
                server.getListener().getPort(),
                server.getTls().isEnabled(),
                uptimeMs, heapUsed, heapMax,
                threadCount,
                Runtime.getRuntime().availableProcessors(),
                escapeJson(System.getProperty("java.version"))
        ));
    }

    private void writeServers(PrintWriter out) {
        List<AdminClient.ServerSummary> servers = adminClient.listServers();
        StringBuilder sb = new StringBuilder("{\"servers\":[");
        boolean first = true;
        for (AdminClient.ServerSummary s : servers) {
            if (!first) sb.append(',');
            first = false;
            try {
                AdminClient.ServerStatus info = adminClient.serverInfo(s.name());
                sb.append("""
                        {"name":"%s","nodeId":"%s","status":"%s",\
                        "host":"%s","port":%d,"uptimeMs":%d,\
                        "transport":"%s","tlsEnabled":%s,\
                        "bossThreads":%d,"workerThreads":%d,"businessThreads":%d}""".formatted(
                        escapeJson(info.name()), escapeJson(info.nodeId()), escapeJson(info.status()),
                        escapeJson(info.host()), info.port(), info.uptimeMillis(),
                        escapeJson(info.transport()), info.tlsEnabled(),
                        info.bossThreads(), info.workerThreads(), info.businessThreads()
                ));
            } catch (Exception e) {
                sb.append("{\"name\":\"%s\",\"nodeId\":\"%s\",\"status\":\"%s\"}".formatted(
                        escapeJson(s.name()), escapeJson(s.nodeId()), escapeJson(s.status())));
            }
        }
        sb.append("]}");
        out.write(sb.toString());
    }

    private void writeApplications(PrintWriter out) {
        // Combine AdminClient apps with known internal apps
        StringBuilder sb = new StringBuilder("{\"applications\":[");
        boolean first = true;

        // Always include the built-in webadmin app
        sb.append("{\"name\":\"velo-webadmin\",\"contextPath\":\"%s\",\"status\":\"RUNNING\",\"type\":\"INTERNAL\",\"servletCount\":18,\"filterCount\":1}".formatted(
                escapeJson(configuration.getServer().getWebAdmin().getContextPath())));
        first = false;

        // Add apps from AdminClient (WAR-deployed applications)
        try {
            List<AdminClient.AppSummary> apps = adminClient.listApplications();
            for (AdminClient.AppSummary app : apps) {
                sb.append(',');
                try {
                    AdminClient.AppStatus info = adminClient.applicationInfo(app.name());
                    sb.append("{\"name\":\"%s\",\"contextPath\":\"%s\",\"status\":\"%s\",\"type\":\"DEPLOYED\",\"servletCount\":%d,\"filterCount\":%d}".formatted(
                            escapeJson(info.name()), escapeJson(info.contextPath()), escapeJson(info.status()),
                            info.servletCount(), info.filterCount()));
                } catch (Exception e) {
                    sb.append("{\"name\":\"%s\",\"contextPath\":\"%s\",\"status\":\"%s\",\"type\":\"DEPLOYED\"}".formatted(
                            escapeJson(app.name()), escapeJson(app.contextPath()), escapeJson(app.status())));
                }
            }
        } catch (Exception ignored) {}

        sb.append("]}");
        out.write(sb.toString());
    }

    private void writeResources(PrintWriter out) {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        out.write("""
                {"resources":{\
                "heapMemory":{"used":%d,"committed":%d,"max":%d},\
                "nonHeapMemory":{"used":%d,"committed":%d}\
                }}""".formatted(
                memory.getHeapMemoryUsage().getUsed(),
                memory.getHeapMemoryUsage().getCommitted(),
                memory.getHeapMemoryUsage().getMax(),
                memory.getNonHeapMemoryUsage().getUsed(),
                memory.getNonHeapMemoryUsage().getCommitted()
        ));
    }

    private void writeThreads(PrintWriter out) {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        int total = threads.getThreadCount();
        int daemon = threads.getDaemonThreadCount();
        int peak = threads.getPeakThreadCount();
        long[] deadlocked = threads.findDeadlockedThreads();

        StringBuilder sb = new StringBuilder();
        sb.append("""
                {"threadCount":%d,"daemonThreadCount":%d,"peakThreadCount":%d,\
                "deadlockedCount":%d,"threads":[""".formatted(
                total, daemon, peak, deadlocked != null ? deadlocked.length : 0));

        ThreadInfo[] infos = threads.getThreadInfo(threads.getAllThreadIds(), 0);
        boolean first = true;
        for (ThreadInfo info : infos) {
            if (info == null) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append("""
                    {"id":%d,"name":"%s","state":"%s","daemon":%s}""".formatted(
                    info.getThreadId(),
                    escapeJson(info.getThreadName()),
                    info.getThreadState().name(),
                    info.isDaemon()
            ));
        }
        sb.append("]}");
        out.write(sb.toString());
    }

    private void writeMonitoring(PrintWriter out) {
        out.write(MetricsCollector.instance().snapshot().toJson());
    }

    /**
     * Lists all available CLI commands grouped by category.
     */
    private void writeCommands(PrintWriter out) {
        List<Command> allCommands = commandRegistry.all();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"commands\":[");
        boolean first = true;
        for (Command cmd : allCommands) {
            if (!first) sb.append(',');
            first = false;
            sb.append("""
                    {"name":"%s","description":"%s","category":"%s","usage":"%s"}""".formatted(
                    escapeJson(cmd.name()),
                    escapeJson(cmd.description()),
                    escapeJson(cmd.category().name()),
                    escapeJson(cmd.usage())
            ));
        }
        sb.append("]}");
        out.write(sb.toString());
    }

    // ── POST /api/execute ──────────────────────────────────────

    /**
     * Executes a CLI command via the full CommandRegistry.
     * This provides 100% CLI parity — every command available in the
     * interactive shell can be executed from the web console.
     */
    private void executeCommand(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }

        String json = body.toString();
        String commandLine = extractJsonValue(json, "command");

        if (commandLine == null || commandLine.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("""
                    {"success":false,"message":"Empty command"}""");
            return;
        }

        log.info("WebAdmin console execute: {}", commandLine);

        // Handle 'clear' locally (not a real CLI command)
        if ("clear".equals(commandLine.trim())) {
            resp.getWriter().write("""
                    {"success":true,"message":""}""");
            return;
        }

        // Parse command name and arguments
        String[] tokens = commandLine.trim().split("\\s+");
        String commandName = tokens[0].toLowerCase();
        String[] args = new String[tokens.length - 1];
        System.arraycopy(tokens, 1, args, 0, args.length);

        // Look up command in the real registry
        Command command = commandRegistry.find(commandName);
        if (command == null) {
            resp.getWriter().write("""
                    {"success":false,"message":"Unknown command: %s\\nType 'help' for available commands."}""".formatted(
                    escapeJson(commandName)
            ));
            return;
        }

        // Execute via CommandContext, capturing output
        StringWriter outputCapture = new StringWriter();
        PrintWriter outputWriter = new PrintWriter(outputCapture, true);
        CommandContext context = new CommandContext(adminClient, outputWriter, Path.of("conf", "server.yaml"));

        String user = getSessionUser(req);
        try {
            CommandResult result = command.execute(context, args);

            // Combine captured output and result message
            String captured = outputCapture.toString().trim();
            String message = result.message();
            if (!captured.isEmpty()) {
                message = captured + (message != null && !message.isEmpty() ? "\n" + message : "");
            }

            AuditEngine.instance().record(user, "EXECUTE_COMMAND", commandName,
                    commandLine, getClientIp(req), result.success());

            resp.getWriter().write("{\"success\":%s,\"message\":\"%s\"}".formatted(
                    result.success(),
                    escapeJson(message)
            ));
        } catch (Exception e) {
            log.warn("Command execution failed: {} - {}", commandName, e.getMessage());
            AuditEngine.instance().record(user, "EXECUTE_COMMAND", commandName,
                    "Error: " + e.getMessage(), getClientIp(req), false);
            resp.getWriter().write("""
                    {"success":false,"message":"Error executing '%s': %s"}""".formatted(
                    escapeJson(commandName),
                    escapeJson(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
            ));
        }
    }

    // ── GET /api/config ─────────────────────────────────────────

    private void writeConfig(PrintWriter out) {
        Path configPath = Path.of("conf", "server.yaml");
        try {
            String yaml = Files.readString(configPath, StandardCharsets.UTF_8);
            out.write("{\"path\":\"%s\",\"content\":\"%s\"}".formatted(
                    escapeJson(configPath.toString()),
                    escapeJson(yaml)
            ));
        } catch (IOException e) {
            out.write("{\"path\":\"%s\",\"error\":\"%s\"}".formatted(
                    escapeJson(configPath.toString()),
                    escapeJson(e.getMessage())
            ));
        }
    }

    // ── GET /api/jvm ────────────────────────────────────────────

    private void writeJvm(PrintWriter out) {
        Map<String, String> jvmInfo = adminClient.jvmInfo();
        out.write(mapToJson(jvmInfo));
    }

    // ── GET /api/system ─────────────────────────────────────────

    private void writeSystem(PrintWriter out) {
        Map<String, String> sysInfo = adminClient.systemInfo();
        out.write(mapToJson(sysInfo));
    }

    // ── GET /api/threadpools ────────────────────────────────────

    private void writeThreadPools(PrintWriter out) {
        List<AdminClient.ThreadPoolSummary> pools = adminClient.listThreadPools();
        StringBuilder sb = new StringBuilder("{\"threadPools\":[");
        boolean first = true;
        for (AdminClient.ThreadPoolSummary pool : pools) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"name\":\"%s\",\"activeCount\":%d,\"poolSize\":%d,\"maxPoolSize\":%d}".formatted(
                    escapeJson(pool.name()), pool.activeCount(), pool.poolSize(), pool.maxPoolSize()
            ));
        }
        sb.append("]}");
        out.write(sb.toString());
    }

    // ── GET /api/loggers ────────────────────────────────────────

    private void writeLoggers(PrintWriter out) {
        List<AdminClient.LoggerSummary> loggers = adminClient.listLoggers();
        StringBuilder sb = new StringBuilder("{\"loggers\":[");
        boolean first = true;
        for (AdminClient.LoggerSummary logger : loggers) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"name\":\"%s\",\"level\":\"%s\"}".formatted(
                    escapeJson(logger.name()), escapeJson(logger.level())
            ));
        }
        sb.append("]}");
        out.write(sb.toString());
    }

    // ── POST /api/config/save ───────────────────────────────────

    private void handleConfigSave(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String json = readBody(req);
        String content = extractJsonValue(json, "content");
        String user = getSessionUser(req);

        if (content == null || content.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"success\":false,\"message\":\"Empty config content\"}");
            return;
        }

        // Create a draft for the config change instead of writing directly
        Map<String, String> changes = new HashMap<>();
        changes.put("server.yaml", content);
        ConfigDraft draft = ConfigChangeEngine.instance().createDraft(
                user, "server.yaml", "Configuration update via Web Admin", changes);

        AuditEngine.instance().record(user, "CONFIG_SAVE_DRAFT", "server.yaml",
                "Created draft " + draft.id(), getClientIp(req), true);

        resp.getWriter().write("{\"success\":true,\"message\":\"Configuration draft created. ID: "
                + draft.id() + "\",\"draftId\":\"" + draft.id() + "\"}");
    }

    // ── POST /api/loggers/set ───────────────────────────────────

    private void handleSetLogLevel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String json = readBody(req);
        String loggerName = extractJsonValue(json, "logger");
        String level = extractJsonValue(json, "level");
        String user = getSessionUser(req);

        if (loggerName == null || level == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"success\":false,\"message\":\"Missing logger or level\"}");
            return;
        }

        try {
            adminClient.setLogLevel(loggerName, level);
            AuditEngine.instance().record(user, "SET_LOG_LEVEL", loggerName,
                    "Level set to " + level, getClientIp(req), true);
            resp.getWriter().write("{\"success\":true,\"message\":\"Log level for '%s' set to %s\"}".formatted(
                    escapeJson(loggerName), escapeJson(level)));
        } catch (Exception e) {
            resp.getWriter().write("{\"success\":false,\"message\":\"%s\"}".formatted(escapeJson(e.getMessage())));
        }
    }

    // ── GET /api/audit ──────────────────────────────────────────

    private void writeAudit(HttpServletRequest req, PrintWriter out) {
        int limit = 100;
        String limitParam = req.getParameter("limit");
        if (limitParam != null) {
            try { limit = Integer.parseInt(limitParam); } catch (NumberFormatException ignored) {}
        }

        String userFilter = req.getParameter("user");
        String actionFilter = req.getParameter("action");

        if (userFilter != null && !userFilter.isEmpty()) {
            out.write("{\"events\":" + toAuditJson(AuditEngine.instance().byUser(userFilter, limit))
                    + ",\"total\":" + AuditEngine.instance().size() + "}");
        } else if (actionFilter != null && !actionFilter.isEmpty()) {
            out.write("{\"events\":" + toAuditJson(AuditEngine.instance().byAction(actionFilter, limit))
                    + ",\"total\":" + AuditEngine.instance().size() + "}");
        } else {
            out.write("{\"events\":" + AuditEngine.instance().toJson(limit)
                    + ",\"total\":" + AuditEngine.instance().size() + "}");
        }
    }

    private String toAuditJson(List<io.velo.was.webadmin.audit.AuditEvent> events) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var event : events) {
            if (!first) sb.append(',');
            first = false;
            sb.append(event.toJson());
        }
        sb.append("]");
        return sb.toString();
    }

    // ── GET /api/drafts ─────────────────────────────────────────

    private void writeDrafts(HttpServletRequest req, PrintWriter out) {
        String statusFilter = req.getParameter("status");
        List<ConfigDraft> drafts;
        if (statusFilter != null && !statusFilter.isEmpty()) {
            try {
                drafts = ConfigChangeEngine.instance().listByStatus(
                        ConfigDraft.DraftStatus.valueOf(statusFilter.toUpperCase()));
            } catch (IllegalArgumentException e) {
                drafts = ConfigChangeEngine.instance().listAll();
            }
        } else {
            drafts = ConfigChangeEngine.instance().listAll();
        }

        StringBuilder sb = new StringBuilder("{\"drafts\":[");
        boolean first = true;
        for (ConfigDraft draft : drafts) {
            if (!first) sb.append(',');
            first = false;
            sb.append(draft.toJson());
        }
        sb.append("],\"total\":").append(ConfigChangeEngine.instance().size()).append("}");
        out.write(sb.toString());
    }

    // ── POST /api/drafts/create ─────────────────────────────────

    private void handleDraftCreate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String json = readBody(req);
        String target = extractJsonValue(json, "target");
        String description = extractJsonValue(json, "description");
        String user = getSessionUser(req);

        if (target == null || target.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"success\":false,\"message\":\"Missing target\"}");
            return;
        }

        Map<String, String> changes = new HashMap<>();
        String changesStr = extractJsonValue(json, "changes");
        if (changesStr != null) {
            changes.put("raw", changesStr);
        }

        ConfigDraft draft = ConfigChangeEngine.instance().createDraft(
                user, target, description != null ? description : "", changes);
        resp.getWriter().write("{\"success\":true,\"draft\":" + draft.toJson() + "}");
    }

    // ── POST /api/drafts/{id}/action ────────────────────────────

    private void handleDraftAction(HttpServletRequest req, HttpServletResponse resp,
                                    String pathInfo) throws IOException {
        // Extract draft ID from /drafts/{id}/action
        String[] parts = pathInfo.split("/");
        if (parts.length < 4) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"success\":false,\"message\":\"Invalid path\"}");
            return;
        }
        String draftId = parts[2];
        String json = readBody(req);
        String action = extractJsonValue(json, "action");
        String user = getSessionUser(req);

        try {
            ConfigDraft result = switch (action != null ? action.toLowerCase() : "") {
                case "validate" -> ConfigChangeEngine.instance().validate(draftId, user);
                case "review" -> ConfigChangeEngine.instance().review(draftId, user);
                case "approve" -> ConfigChangeEngine.instance().approve(draftId, user);
                case "apply" -> ConfigChangeEngine.instance().apply(draftId, user);
                case "rollback" -> ConfigChangeEngine.instance().rollback(draftId, user);
                case "reject" -> {
                    String reason = extractJsonValue(json, "reason");
                    yield ConfigChangeEngine.instance().reject(draftId, user,
                            reason != null ? reason : "");
                }
                default -> throw new IllegalArgumentException("Unknown action: " + action);
            };
            resp.getWriter().write("{\"success\":true,\"draft\":" + result.toJson() + "}");
        } catch (Exception e) {
            resp.getWriter().write("{\"success\":false,\"message\":\"%s\"}".formatted(
                    escapeJson(e.getMessage())));
        }
    }

    private void writeClusters(PrintWriter out) {
        List<AdminClient.ClusterSummary> clusters = adminClient.listClusters();
        StringBuilder sb = new StringBuilder("{\"clusters\":[");
        boolean first = true;
        for (AdminClient.ClusterSummary c : clusters) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"name\":\"%s\",\"memberCount\":%d,\"status\":\"%s\"}".formatted(
                    escapeJson(c.name()), c.memberCount(), escapeJson(c.status())));
        }
        sb.append("]}");
        out.write(sb.toString());
    }

    private void writeNodes(PrintWriter out) {
        ServerConfiguration.Server server = configuration.getServer();
        out.write("""
                {"nodes":[{"nodeId":"%s","host":"%s","os":"%s %s","arch":"%s",\
                "cpus":%d,"java":"%s","status":"ONLINE","servers":[{"name":"%s","port":%d}],\
                "tcpListeners":%d}]}""".formatted(
                escapeJson(server.getNodeId()),
                escapeJson(server.getListener().getHost()),
                escapeJson(System.getProperty("os.name")),
                escapeJson(System.getProperty("os.version")),
                escapeJson(System.getProperty("os.arch")),
                Runtime.getRuntime().availableProcessors(),
                escapeJson(System.getProperty("java.version")),
                escapeJson(server.getName()),
                server.getListener().getPort(),
                server.getTcpListeners().size()
        ));
    }

    private void writeUsers(PrintWriter out) {
        List<String> users = adminClient.listUsers();
        StringBuilder sb = new StringBuilder("{\"users\":[");
        boolean first = true;
        for (String u : users) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"username\":\"%s\"}".formatted(escapeJson(u)));
        }
        sb.append("]}");
        out.write(sb.toString());
    }

    private void handleUserCreate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readBody(req);
        String username = extractJsonValue(body, "username");
        String password = extractJsonValue(body, "password");
        PrintWriter out = resp.getWriter();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            out.write("{\"success\":false,\"message\":\"Username and password are required\"}");
            return;
        }
        try {
            adminClient.createUser(username, password);
            String user = getSessionUser(req);
            AuditEngine.instance().record(user, "CREATE_USER", username,
                    "User created", getClientIp(req), true);
            out.write("{\"success\":true,\"message\":\"User '%s' created\"}".formatted(escapeJson(username)));
        } catch (Exception e) {
            out.write("{\"success\":false,\"message\":\"%s\"}".formatted(escapeJson(e.getMessage())));
        }
    }

    private void handleUserRemove(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readBody(req);
        String username = extractJsonValue(body, "username");
        PrintWriter out = resp.getWriter();
        if (username == null || username.isBlank()) {
            out.write("{\"success\":false,\"message\":\"Username is required\"}");
            return;
        }
        try {
            adminClient.removeUser(username);
            String user = getSessionUser(req);
            AuditEngine.instance().record(user, "REMOVE_USER", username,
                    "User removed", getClientIp(req), true);
            out.write("{\"success\":true,\"message\":\"User '%s' removed\"}".formatted(escapeJson(username)));
        } catch (Exception e) {
            out.write("{\"success\":false,\"message\":\"%s\"}".formatted(escapeJson(e.getMessage())));
        }
    }

    private void handleChangePassword(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = readBody(req);
        String username = extractJsonValue(body, "username");
        String newPassword = extractJsonValue(body, "password");
        PrintWriter out = resp.getWriter();
        if (username == null || newPassword == null || newPassword.isBlank()) {
            out.write("{\"success\":false,\"message\":\"Username and new password are required\"}");
            return;
        }
        try {
            adminClient.changePassword(username, newPassword);
            String user = getSessionUser(req);
            AuditEngine.instance().record(user, "CHANGE_PASSWORD", username,
                    "Password changed", getClientIp(req), true);
            out.write("{\"success\":true,\"message\":\"Password changed for '%s'\"}".formatted(escapeJson(username)));
        } catch (Exception e) {
            out.write("{\"success\":false,\"message\":\"%s\"}".formatted(escapeJson(e.getMessage())));
        }
    }

    private String readBody(HttpServletRequest req) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }

    private static String getSessionUser(HttpServletRequest req) {
        Object user = req.getSession(false) != null
                ? req.getSession(false).getAttribute("velo.admin.username") : null;
        return user != null ? user.toString() : "anonymous";
    }

    private static String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }

    // ── Utilities ──────────────────────────────────────────────

    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int qStart = json.indexOf('"', colon + 1);
        if (qStart < 0) return null;
        int qEnd = json.indexOf('"', qStart + 1);
        if (qEnd < 0) return null;
        return json.substring(qStart + 1, qEnd);
    }

    private static String mapToJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("\"%s\":\"%s\"".formatted(escapeJson(entry.getKey()), escapeJson(entry.getValue())));
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
