package io.velo.was.mcp.builtin;

import io.velo.was.admin.client.AdminClient;
import io.velo.was.mcp.tool.McpTool;
import io.velo.was.mcp.tool.McpToolCallResult;
import io.velo.was.mcp.tool.McpToolExecutor;
import io.velo.was.mcp.tool.McpToolInputSchema;
import io.velo.was.mcp.tool.McpToolRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Registers all Velo WAS Admin CLI commands as MCP tools.
 *
 * <p>Categories:
 * <ol>
 *   <li>Server management</li>
 *   <li>Application management</li>
 *   <li>Monitoring (system, JVM, memory, threads)</li>
 *   <li>Logging</li>
 *   <li>Thread pool management</li>
 *   <li>Datasource management</li>
 *   <li>JDBC / Connection pool</li>
 *   <li>JMS management</li>
 *   <li>Domain management</li>
 *   <li>Cluster management</li>
 *   <li>Security (user/role)</li>
 *   <li>JMX / MBean</li>
 * </ol>
 */
public final class McpAdminCliTools {

    private McpAdminCliTools() {}

    /**
     * Register all admin CLI tools onto the given registry.
     */
    public static void registerAll(McpToolRegistry registry, AdminClient client) {
        // ── 1. Server ──────────────────────────────────────────────────────
        registry.register(tool("list_servers",
                "List all WAS server instances with node ID and status.",
                schema(Map.of())), args -> {
            List<AdminClient.ServerSummary> servers = client.listServers();
            StringBuilder sb = new StringBuilder();
            for (var s : servers) sb.append("  ").append(s.name()).append("  nodeId=").append(s.nodeId())
                    .append("  status=").append(s.status()).append('\n');
            return McpToolCallResult.text("Servers (" + servers.size() + "):\n" + sb);
        });

        registry.register(tool("server_info",
                "Show detailed server status: host, port, transport, TLS, uptime, threading.",
                schema(props("serverName", "string", "Server name (default: current server)"), List.of())), args -> {
            String name = str(args, "serverName", "velo-was");
            AdminClient.ServerStatus s = client.serverInfo(name);
            return McpToolCallResult.text(formatMap("Server: " + s.name(), Map.of(
                    "nodeId", s.nodeId(), "status", s.status(), "host", s.host(),
                    "port", String.valueOf(s.port()), "transport", s.transport(),
                    "tlsEnabled", String.valueOf(s.tlsEnabled()),
                    "uptimeMs", String.valueOf(s.uptimeMillis()),
                    "bossThreads", String.valueOf(s.bossThreads()),
                    "workerThreads", String.valueOf(s.workerThreads()),
                    "businessThreads", String.valueOf(s.businessThreads()))));
        });

        registry.register(tool("restart_server",
                "Restart a WAS server instance.",
                schema(props("serverName", "string", "Server name to restart"), List.of("serverName"))), args -> {
            String name = require(args, "serverName");
            client.restartServer(name);
            return McpToolCallResult.text("Server '" + name + "' restart initiated.");
        });

        // ── 2. Application ─────────────────────────────────────────────────
        registry.register(tool("list_applications",
                "List all deployed web applications with context path and status.",
                schema(Map.of())), args -> {
            List<AdminClient.AppSummary> apps = client.listApplications();
            StringBuilder sb = new StringBuilder();
            for (var a : apps) sb.append("  ").append(a.name()).append("  contextPath=").append(a.contextPath())
                    .append("  status=").append(a.status()).append('\n');
            return McpToolCallResult.text("Applications (" + apps.size() + "):\n" + sb);
        });

        registry.register(tool("application_info",
                "Show detailed info for a deployed application: servlets, filters, context path.",
                schema(props("appName", "string", "Application name"), List.of("appName"))), args -> {
            String name = require(args, "appName");
            AdminClient.AppStatus a = client.applicationInfo(name);
            return McpToolCallResult.text(formatMap("Application: " + a.name(), Map.of(
                    "contextPath", a.contextPath(), "status", a.status(),
                    "servlets", String.valueOf(a.servletCount()), "filters", String.valueOf(a.filterCount()))));
        });

        registry.register(tool("deploy_application",
                "Deploy a WAR file to the server. Provide the WAR file path and optional context path.",
                schema(props2(
                        "warPath", "string", "Absolute path to the WAR file",
                        "contextPath", "string", "Context path (default: derived from WAR filename)"),
                        List.of("warPath"))), args -> {
            String path = require(args, "warPath");
            String ctx = str(args, "contextPath", null);
            client.deploy(path, ctx);
            return McpToolCallResult.text("Deployed: " + path + (ctx != null ? " -> " + ctx : ""));
        });

        registry.register(tool("undeploy_application",
                "Undeploy a web application by name.",
                schema(props("appName", "string", "Application name to undeploy"), List.of("appName"))), args -> {
            String name = require(args, "appName");
            client.undeploy(name);
            return McpToolCallResult.text("Undeployed: " + name);
        });

        registry.register(tool("redeploy_application",
                "Redeploy (restart) an existing application.",
                schema(props("appName", "string", "Application name to redeploy"), List.of("appName"))), args -> {
            String name = require(args, "appName");
            client.redeploy(name);
            return McpToolCallResult.text("Redeployed: " + name);
        });

        // ── 3. Monitoring ──────────────────────────────────────────────────
        registry.register(tool("system_info",
                "Show OS information: name, version, architecture, processors, user, working directory.",
                schema(Map.of())), args -> McpToolCallResult.text(formatMap("System Info", client.systemInfo())));

        registry.register(tool("jvm_info",
                "Show JVM information: name, vendor, version, spec, uptime, arguments.",
                schema(Map.of())), args -> McpToolCallResult.text(formatMap("JVM Info", client.jvmInfo())));

        registry.register(tool("memory_info",
                "Show memory usage: heap used/committed/max, non-heap, usage percentage.",
                schema(Map.of())), args -> McpToolCallResult.text(formatMap("Memory Info", client.memoryInfo())));

        registry.register(tool("thread_info",
                "Show thread status: total count, peak, daemon count, deadlocked threads.",
                schema(Map.of())), args -> McpToolCallResult.text(formatMap("Thread Info", client.threadInfo())));

        registry.register(tool("resource_overview",
                "Show resource status overview: processors, memory, thread pools, datasources.",
                schema(Map.of())), args -> McpToolCallResult.text(formatMap("Resource Overview", client.resourceInfo())));

        // ── 4. Logging ─────────────────────────────────────────────────────
        registry.register(tool("list_loggers",
                "List all loggers with their current log levels.",
                schema(Map.of())), args -> {
            List<AdminClient.LoggerSummary> loggers = client.listLoggers();
            StringBuilder sb = new StringBuilder();
            for (var l : loggers) sb.append("  ").append(l.name()).append("  level=").append(l.level()).append('\n');
            return McpToolCallResult.text("Loggers (" + loggers.size() + "):\n" + sb);
        });

        registry.register(tool("get_log_level",
                "Get the current log level for a specific logger.",
                schema(props("loggerName", "string", "Logger name"), List.of("loggerName"))), args -> {
            String name = require(args, "loggerName");
            String level = client.getLogLevel(name);
            return McpToolCallResult.text("Logger '" + name + "' level: " + level);
        });

        registry.register(tool("set_log_level",
                "Change the log level for a logger. Levels: TRACE, DEBUG, INFO, WARN, ERROR, OFF.",
                schema(props2("loggerName", "string", "Logger name", "level", "string",
                        "Log level: TRACE, DEBUG, INFO, WARN, ERROR, OFF"), List.of("loggerName", "level"))), args -> {
            String name = require(args, "loggerName");
            String level = require(args, "level");
            client.setLogLevel(name, level);
            return McpToolCallResult.text("Logger '" + name + "' level set to: " + level);
        });

        // ── 5. Thread Pool ─────────────────────────────────────────────────
        registry.register(tool("list_thread_pools",
                "List all thread pools with active count, pool size, and max size.",
                schema(Map.of())), args -> {
            List<AdminClient.ThreadPoolSummary> pools = client.listThreadPools();
            StringBuilder sb = new StringBuilder();
            for (var p : pools) sb.append("  ").append(p.name()).append("  active=").append(p.activeCount())
                    .append("  poolSize=").append(p.poolSize()).append("  maxSize=").append(p.maxPoolSize()).append('\n');
            return McpToolCallResult.text("Thread Pools (" + pools.size() + "):\n" + sb);
        });

        registry.register(tool("thread_pool_info",
                "Show detailed thread pool status: active count, completed tasks, queue size.",
                schema(props("poolName", "string", "Thread pool name"), List.of("poolName"))), args -> {
            String name = require(args, "poolName");
            AdminClient.ThreadPoolStatus p = client.threadPoolInfo(name);
            return McpToolCallResult.text(formatMap("Thread Pool: " + p.name(), Map.of(
                    "activeCount", String.valueOf(p.activeCount()), "poolSize", String.valueOf(p.poolSize()),
                    "maxPoolSize", String.valueOf(p.maxPoolSize()), "completedTasks", String.valueOf(p.completedTaskCount()),
                    "queueSize", String.valueOf(p.queueSize()))));
        });

        // ── 6. Datasource ──────────────────────────────────────────────────
        registry.register(tool("list_datasources",
                "List all configured datasources with type and status.",
                schema(Map.of())), args -> {
            List<AdminClient.DatasourceSummary> ds = client.listDatasources();
            StringBuilder sb = new StringBuilder();
            for (var d : ds) sb.append("  ").append(d.name()).append("  type=").append(d.type())
                    .append("  status=").append(d.status()).append('\n');
            return McpToolCallResult.text("Datasources (" + ds.size() + "):\n" + sb);
        });

        registry.register(tool("datasource_info",
                "Show datasource details: URL, driver, connection counts.",
                schema(props("datasourceName", "string", "Datasource name"), List.of("datasourceName"))), args -> {
            String name = require(args, "datasourceName");
            AdminClient.DatasourceStatus d = client.datasourceInfo(name);
            return McpToolCallResult.text(formatMap("Datasource: " + d.name(), Map.of(
                    "type", d.type(), "url", d.url(), "status", d.status(),
                    "active", String.valueOf(d.activeConnections()),
                    "idle", String.valueOf(d.idleConnections()),
                    "max", String.valueOf(d.maxConnections()))));
        });

        registry.register(tool("test_datasource",
                "Test connectivity for a datasource.",
                schema(props("datasourceName", "string", "Datasource name to test"), List.of("datasourceName"))), args -> {
            String name = require(args, "datasourceName");
            client.testDatasource(name);
            return McpToolCallResult.text("Datasource '" + name + "' connection test: OK");
        });

        // ── 7. JDBC / Connection Pool ──────────────────────────────────────
        registry.register(tool("list_jdbc_resources",
                "List all JDBC resources with pool names and types.",
                schema(Map.of())), args -> {
            List<AdminClient.JdbcResourceSummary> res = client.listJdbcResources();
            StringBuilder sb = new StringBuilder();
            for (var r : res) sb.append("  ").append(r.name()).append("  pool=").append(r.poolName())
                    .append("  type=").append(r.type()).append('\n');
            return McpToolCallResult.text("JDBC Resources (" + res.size() + "):\n" + sb);
        });

        registry.register(tool("jdbc_resource_info",
                "Show JDBC resource details: driver, URL, connection counts, pool size.",
                schema(props("resourceName", "string", "JDBC resource name"), List.of("resourceName"))), args -> {
            String name = require(args, "resourceName");
            AdminClient.JdbcResourceStatus r = client.jdbcResourceInfo(name);
            return McpToolCallResult.text(formatMap("JDBC Resource: " + r.name(), Map.of(
                    "pool", r.poolName(), "driver", r.driverClass(), "url", r.url(),
                    "active", String.valueOf(r.activeConnections()),
                    "idle", String.valueOf(r.idleConnections()),
                    "maxPoolSize", String.valueOf(r.maxPoolSize()))));
        });

        registry.register(tool("flush_connection_pool",
                "Flush all connections in a connection pool.",
                schema(props("poolName", "string", "Connection pool name"), List.of("poolName"))), args -> {
            String name = require(args, "poolName");
            client.flushConnectionPool(name);
            return McpToolCallResult.text("Connection pool '" + name + "' flushed.");
        });

        // ── 8. JMS ─────────────────────────────────────────────────────────
        registry.register(tool("list_jms_servers",
                "List all JMS servers.",
                schema(Map.of())), args -> {
            List<AdminClient.JmsServerSummary> servers = client.listJmsServers();
            StringBuilder sb = new StringBuilder();
            for (var s : servers) sb.append("  ").append(s.name()).append("  status=").append(s.status()).append('\n');
            return McpToolCallResult.text("JMS Servers (" + servers.size() + "):\n" + sb);
        });

        registry.register(tool("list_jms_destinations",
                "List all JMS destinations with type and message count.",
                schema(Map.of())), args -> {
            List<AdminClient.JmsDestinationSummary> dests = client.listJmsDestinations();
            StringBuilder sb = new StringBuilder();
            for (var d : dests) sb.append("  ").append(d.name()).append("  type=").append(d.type())
                    .append("  messages=").append(d.messageCount()).append('\n');
            return McpToolCallResult.text("JMS Destinations (" + dests.size() + "):\n" + sb);
        });

        registry.register(tool("purge_jms_queue",
                "Purge all messages from a JMS queue.",
                schema(props("queueName", "string", "JMS queue name to purge"), List.of("queueName"))), args -> {
            String name = require(args, "queueName");
            client.purgeJmsQueue(name);
            return McpToolCallResult.text("JMS queue '" + name + "' purged.");
        });

        // ── 9. Domain ──────────────────────────────────────────────────────
        registry.register(tool("list_domains",
                "List all WAS domains.",
                schema(Map.of())), args -> {
            List<AdminClient.DomainSummary> domains = client.listDomains();
            StringBuilder sb = new StringBuilder();
            for (var d : domains) sb.append("  ").append(d.name()).append("  status=").append(d.status()).append('\n');
            return McpToolCallResult.text("Domains (" + domains.size() + "):\n" + sb);
        });

        registry.register(tool("domain_info",
                "Show domain details: admin server, server count, properties.",
                schema(props("domainName", "string", "Domain name (default: 'default')"), List.of())), args -> {
            String name = str(args, "domainName", "default");
            AdminClient.DomainStatus d = client.domainInfo(name);
            String header = "Domain: " + d.name() + "\n  status=" + d.status()
                    + "\n  adminServer=" + d.adminServerName() + "\n  servers=" + d.serverCount();
            if (!d.properties().isEmpty()) {
                header += "\n  properties:";
                for (var e : d.properties().entrySet()) header += "\n    " + e.getKey() + "=" + e.getValue();
            }
            return McpToolCallResult.text(header);
        });

        // ── 10. Cluster ────────────────────────────────────────────────────
        registry.register(tool("list_clusters",
                "List all clusters with member count and status.",
                schema(Map.of())), args -> {
            List<AdminClient.ClusterSummary> clusters = client.listClusters();
            StringBuilder sb = new StringBuilder();
            for (var c : clusters) sb.append("  ").append(c.name()).append("  members=").append(c.memberCount())
                    .append("  status=").append(c.status()).append('\n');
            return McpToolCallResult.text("Clusters (" + clusters.size() + "):\n" + sb);
        });

        registry.register(tool("cluster_info",
                "Show cluster details: status and member list.",
                schema(props("clusterName", "string", "Cluster name"), List.of("clusterName"))), args -> {
            String name = require(args, "clusterName");
            AdminClient.ClusterStatus c = client.clusterInfo(name);
            return McpToolCallResult.text("Cluster: " + c.name() + "\n  status=" + c.status()
                    + "\n  members=" + String.join(", ", c.members()));
        });

        // ── 11. Security ───────────────────────────────────────────────────
        registry.register(tool("list_users",
                "List all registered users.",
                schema(Map.of())), args -> {
            List<String> users = client.listUsers();
            return McpToolCallResult.text("Users (" + users.size() + "): " + String.join(", ", users));
        });

        registry.register(tool("list_roles",
                "List all available security roles.",
                schema(Map.of())), args -> {
            List<String> roles = client.listRoles();
            return McpToolCallResult.text("Roles (" + roles.size() + "): " + String.join(", ", roles));
        });

        registry.register(tool("create_user",
                "Create a new user account.",
                schema(props2("username", "string", "Username", "password", "string", "Password"),
                        List.of("username", "password"))), args -> {
            String username = require(args, "username");
            String password = require(args, "password");
            client.createUser(username, password);
            return McpToolCallResult.text("User '" + username + "' created.");
        });

        registry.register(tool("remove_user",
                "Remove a user account.",
                schema(props("username", "string", "Username to remove"), List.of("username"))), args -> {
            String name = require(args, "username");
            client.removeUser(name);
            return McpToolCallResult.text("User '" + name + "' removed.");
        });

        // ── 12. JMX / MBean ────────────────────────────────────────────────
        registry.register(tool("list_mbeans",
                "List all JMX MBeans with object names and class names.",
                schema(Map.of())), args -> {
            List<AdminClient.MBeanSummary> mbeans = client.listMBeans();
            StringBuilder sb = new StringBuilder();
            for (var m : mbeans) sb.append("  ").append(m.objectName()).append("  class=").append(m.className()).append('\n');
            return McpToolCallResult.text("MBeans (" + mbeans.size() + "):\n" + sb);
        });

        registry.register(tool("get_mbean_attribute",
                "Get the value of a JMX MBean attribute.",
                schema(props2("mbeanName", "string", "MBean object name",
                        "attribute", "string", "Attribute name"), List.of("mbeanName", "attribute"))), args -> {
            String mbean = require(args, "mbeanName");
            String attr = require(args, "attribute");
            String value = client.getMBeanAttribute(mbean, attr);
            return McpToolCallResult.text(mbean + "." + attr + " = " + value);
        });

        registry.register(tool("set_mbean_attribute",
                "Set a JMX MBean attribute value.",
                schema(props3("mbeanName", "string", "MBean object name",
                        "attribute", "string", "Attribute name",
                        "value", "string", "New attribute value"),
                        List.of("mbeanName", "attribute", "value"))), args -> {
            String mbean = require(args, "mbeanName");
            String attr = require(args, "attribute");
            String value = require(args, "value");
            client.setMBeanAttribute(mbean, attr, value);
            return McpToolCallResult.text("Set " + mbean + "." + attr + " = " + value);
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Schema helpers
    // ═════════════════════════════════════════════════════════════════════════

    private static McpTool tool(String name, String description, McpToolInputSchema inputSchema) {
        return new McpTool(name, description, inputSchema);
    }

    private static McpToolInputSchema schema(Map<String, Object> properties) {
        return new McpToolInputSchema("object", properties, List.of());
    }

    private static McpToolInputSchema schema(Map<String, Object> properties, List<String> required) {
        return new McpToolInputSchema("object", properties, required);
    }

    private static Map<String, Object> props(String name, String type, String description) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(name, Map.of("type", type, "description", description));
        return map;
    }

    private static Map<String, Object> props2(String n1, String t1, String d1,
                                               String n2, String t2, String d2) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(n1, Map.of("type", t1, "description", d1));
        map.put(n2, Map.of("type", t2, "description", d2));
        return map;
    }

    private static Map<String, Object> props3(String n1, String t1, String d1,
                                               String n2, String t2, String d2,
                                               String n3, String t3, String d3) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(n1, Map.of("type", t1, "description", d1));
        map.put(n2, Map.of("type", t2, "description", d2));
        map.put(n3, Map.of("type", t3, "description", d3));
        return map;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Argument helpers
    // ═════════════════════════════════════════════════════════════════════════

    private static String require(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null || v.toString().isBlank()) throw new IllegalArgumentException("'" + key + "' is required");
        return v.toString();
    }

    private static String str(Map<String, Object> args, String key, String defaultValue) {
        Object v = args.get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString() : defaultValue;
    }

    private static String formatMap(String header, Map<String, String> map) {
        StringBuilder sb = new StringBuilder(header).append('\n');
        for (var e : map.entrySet()) sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
        return sb.toString();
    }
}
