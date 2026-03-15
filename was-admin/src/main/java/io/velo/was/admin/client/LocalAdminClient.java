package io.velo.was.admin.client;

import io.velo.was.config.ServerConfiguration;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.MBeanInfo;
import javax.management.MBeanAttributeInfo;

public class LocalAdminClient implements AdminClient {

    /**
     * Callback interface for application deployment operations.
     * Set via {@link #setDeployHandler(DeployHandler)} from bootstrap to wire
     * the real {@code DeploymentRegistry} without circular module dependencies.
     */
    public interface DeployHandler {
        void deploy(String path, String contextPath);
        void undeploy(String appName);
        void redeploy(String appName);
        void startApplication(String appName);
        void stopApplication(String appName);
        List<AppSummary> listApplications();
    }

    private static volatile DeployHandler deployHandler;

    /**
     * Sets the global deploy handler. Called once from bootstrap after DeploymentRegistry is created.
     */
    public static void setDeployHandler(DeployHandler handler) {
        deployHandler = handler;
    }

    private final ServerConfiguration configuration;
    private final Map<String, String> logLevels = new ConcurrentHashMap<>();
    private final Map<String, String> users = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> domains = new ConcurrentHashMap<>();
    private final long startTimeMillis = System.currentTimeMillis();

    public LocalAdminClient(ServerConfiguration configuration) {
        this.configuration = configuration;
        logLevels.put("ROOT", "INFO");
        logLevels.put("io.velo.was", "INFO");
        logLevels.put("io.velo.was.transport", "INFO");
        logLevels.put("io.velo.was.servlet", "INFO");
        users.put("admin", "admin");

        Map<String, String> defaultProps = new ConcurrentHashMap<>();
        defaultProps.put("admin.port", String.valueOf(configuration.getServer().getListener().getPort()));
        defaultProps.put("server.name", configuration.getServer().getName());
        domains.put("default", defaultProps);
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public String connectionInfo() {
        ServerConfiguration.Server server = configuration.getServer();
        return "local://" + server.getName() + " (nodeId=" + server.getNodeId() + ")";
    }

    // ── Domain ──

    @Override
    public List<DomainSummary> listDomains() {
        return domains.keySet().stream()
                .map(name -> new DomainSummary(name, "RUNNING"))
                .toList();
    }

    @Override
    public DomainStatus domainInfo(String domainName) {
        Map<String, String> domainProps = domains.get(domainName);
        if (domainProps == null) {
            throw new IllegalArgumentException("Domain not found: " + domainName);
        }
        Map<String, String> props = new LinkedHashMap<>(domainProps);
        return new DomainStatus(domainName, "RUNNING",
                configuration.getServer().getName(), 1, props);
    }

    @Override
    public void createDomain(String domainName) {
        if (domains.containsKey(domainName)) {
            throw new IllegalArgumentException("Domain already exists: " + domainName);
        }
        domains.put(domainName, new ConcurrentHashMap<>());
    }

    @Override
    public void removeDomain(String domainName) {
        if ("default".equals(domainName)) {
            throw new IllegalArgumentException("Cannot remove the default domain");
        }
        if (domains.remove(domainName) == null) {
            throw new IllegalArgumentException("Domain not found: " + domainName);
        }
    }

    @Override
    public void setDomainProperty(String domainName, String key, String value) {
        Map<String, String> props = domains.get(domainName);
        if (props == null) {
            throw new IllegalArgumentException("Domain not found: " + domainName);
        }
        props.put(key, value);
    }

    @Override
    public String getDomainProperty(String domainName, String key) {
        Map<String, String> props = domains.get(domainName);
        if (props == null) {
            throw new IllegalArgumentException("Domain not found: " + domainName);
        }
        String value = props.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Property not found: " + key + " in domain: " + domainName);
        }
        return value;
    }

    // ── Server ──

    @Override
    public ServerStatus serverInfo(String serverName) {
        ServerConfiguration.Server server = configuration.getServer();
        return new ServerStatus(
                server.getName(),
                server.getNodeId(),
                "RUNNING",
                server.getListener().getHost(),
                server.getListener().getPort(),
                "netty-nio",
                server.getTls().isEnabled(),
                System.currentTimeMillis() - startTimeMillis,
                server.getThreading().getBossThreads(),
                server.getThreading().getWorkerThreads(),
                server.getThreading().getBusinessThreads());
    }

    @Override
    public List<ServerSummary> listServers() {
        ServerConfiguration.Server server = configuration.getServer();
        return List.of(new ServerSummary(server.getName(), server.getNodeId(), "RUNNING"));
    }

    @Override
    public void startServer(String serverName) {
        throw new UnsupportedOperationException("Server is already running (local mode)");
    }

    @Override
    public void stopServer(String serverName) {
        throw new UnsupportedOperationException("Use 'exit' to shutdown in local mode");
    }

    @Override
    public void restartServer(String serverName) {
        throw new UnsupportedOperationException("Restart not supported in local mode");
    }

    @Override
    public void suspendServer(String serverName) {
        throw new UnsupportedOperationException("Suspend not supported in local mode");
    }

    @Override
    public void resumeServer(String serverName) {
        throw new UnsupportedOperationException("Resume not supported in local mode");
    }

    @Override
    public void killServer(String serverName) {
        throw new UnsupportedOperationException("Kill not supported in local mode. Use 'exit'.");
    }

    // ── Cluster ──

    @Override
    public List<ClusterSummary> listClusters() {
        return List.of();
    }

    @Override
    public ClusterStatus clusterInfo(String clusterName) {
        throw new UnsupportedOperationException("No clusters configured");
    }

    @Override
    public void startCluster(String clusterName) {
        throw new UnsupportedOperationException("No clusters configured");
    }

    @Override
    public void stopCluster(String clusterName) {
        throw new UnsupportedOperationException("No clusters configured");
    }

    @Override
    public void restartCluster(String clusterName) {
        throw new UnsupportedOperationException("No clusters configured");
    }

    @Override
    public void addServerToCluster(String clusterName, String serverName) {
        throw new UnsupportedOperationException("No clusters configured");
    }

    @Override
    public void removeServerFromCluster(String clusterName, String serverName) {
        throw new UnsupportedOperationException("No clusters configured");
    }

    // ── Application ──

    @Override
    public List<AppSummary> listApplications() {
        if (deployHandler != null) {
            return deployHandler.listApplications();
        }
        return List.of();
    }

    @Override
    public AppStatus applicationInfo(String appName) {
        // Find from list
        List<AppSummary> apps = listApplications();
        for (AppSummary app : apps) {
            if (app.name().equals(appName)) {
                return new AppStatus(app.name(), app.contextPath(), app.status(), 0, 0);
            }
        }
        throw new UnsupportedOperationException("Application not found: " + appName);
    }

    @Override
    public void deploy(String path, String contextPath) {
        if (deployHandler != null) {
            deployHandler.deploy(path, contextPath);
            return;
        }
        throw new UnsupportedOperationException("Deploy handler not configured. Server may need restart.");
    }

    @Override
    public void undeploy(String appName) {
        if (deployHandler != null) {
            deployHandler.undeploy(appName);
            return;
        }
        throw new UnsupportedOperationException("Deploy handler not configured. Server may need restart.");
    }

    @Override
    public void redeploy(String appName) {
        if (deployHandler != null) {
            deployHandler.redeploy(appName);
            return;
        }
        throw new UnsupportedOperationException("Deploy handler not configured. Server may need restart.");
    }

    @Override
    public void startApplication(String appName) {
        if (deployHandler != null) {
            deployHandler.startApplication(appName);
            return;
        }
        throw new UnsupportedOperationException("Deploy handler not configured. Server may need restart.");
    }

    @Override
    public void stopApplication(String appName) {
        if (deployHandler != null) {
            deployHandler.stopApplication(appName);
            return;
        }
        throw new UnsupportedOperationException("Deploy handler not configured. Server may need restart.");
    }

    // ── Datasource ──

    @Override
    public List<DatasourceSummary> listDatasources() {
        return List.of();
    }

    @Override
    public DatasourceStatus datasourceInfo(String dsName) {
        throw new UnsupportedOperationException("Datasource not found: " + dsName);
    }

    @Override
    public void enableDatasource(String dsName) {
        throw new UnsupportedOperationException("Datasource not found: " + dsName);
    }

    @Override
    public void disableDatasource(String dsName) {
        throw new UnsupportedOperationException("Datasource not found: " + dsName);
    }

    @Override
    public void testDatasource(String dsName) {
        throw new UnsupportedOperationException("Datasource not found: " + dsName);
    }

    // ── JDBC / Connection Pool ──

    @Override
    public List<JdbcResourceSummary> listJdbcResources() {
        return List.of();
    }

    @Override
    public JdbcResourceStatus jdbcResourceInfo(String resourceName) {
        throw new UnsupportedOperationException("JDBC resource not found: " + resourceName);
    }

    @Override
    public void resetConnectionPool(String poolName) {
        throw new UnsupportedOperationException("Connection pool not found: " + poolName);
    }

    @Override
    public void flushConnectionPool(String poolName) {
        throw new UnsupportedOperationException("Connection pool not found: " + poolName);
    }

    // ── JMS ──

    @Override
    public List<JmsServerSummary> listJmsServers() {
        return List.of();
    }

    @Override
    public JmsServerStatus jmsServerInfo(String serverName) {
        throw new UnsupportedOperationException("JMS server not found: " + serverName);
    }

    @Override
    public List<JmsDestinationSummary> listJmsDestinations() {
        return List.of();
    }

    @Override
    public JmsDestinationStatus jmsDestinationInfo(String destinationName) {
        throw new UnsupportedOperationException("JMS destination not found: " + destinationName);
    }

    @Override
    public void purgeJmsQueue(String queueName) {
        throw new UnsupportedOperationException("JMS queue not found: " + queueName);
    }

    // ── Thread Pool ──

    @Override
    public List<ThreadPoolSummary> listThreadPools() {
        ServerConfiguration.Threading threading = configuration.getServer().getThreading();
        List<ThreadPoolSummary> pools = new ArrayList<>();
        pools.add(new ThreadPoolSummary("boss", 0, threading.getBossThreads(), threading.getBossThreads()));
        pools.add(new ThreadPoolSummary("worker", 0, threading.getWorkerThreads(), threading.getWorkerThreads()));
        pools.add(new ThreadPoolSummary("business", 0, threading.getBusinessThreads(), threading.getBusinessThreads()));
        return pools;
    }

    @Override
    public ThreadPoolStatus threadPoolInfo(String poolName) {
        ServerConfiguration.Threading threading = configuration.getServer().getThreading();
        return switch (poolName) {
            case "boss" -> new ThreadPoolStatus("boss", 0, threading.getBossThreads(),
                    threading.getBossThreads(), 0, 0);
            case "worker" -> new ThreadPoolStatus("worker", 0, threading.getWorkerThreads(),
                    threading.getWorkerThreads(), 0, 0);
            case "business" -> new ThreadPoolStatus("business", 0, threading.getBusinessThreads(),
                    threading.getBusinessThreads(), 0, 0);
            default -> throw new UnsupportedOperationException("Thread pool not found: " + poolName);
        };
    }

    @Override
    public void resetThreadPool(String poolName) {
        // Validate pool name exists
        threadPoolInfo(poolName);
        // In local mode, reset is a no-op simulation
    }

    @Override
    public Map<String, String> resourceInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        Runtime runtime = Runtime.getRuntime();
        info.put("Available Processors", String.valueOf(runtime.availableProcessors()));
        info.put("Free Memory", formatBytes(runtime.freeMemory()));
        info.put("Total Memory", formatBytes(runtime.totalMemory()));
        info.put("Max Memory", formatBytes(runtime.maxMemory()));
        info.put("Thread Pools", "boss, worker, business");
        info.put("Datasources", "0");
        info.put("JDBC Resources", "0");
        info.put("JMS Servers", "0");
        return info;
    }

    // ── Monitoring ──

    @Override
    public Map<String, String> systemInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("OS Name", System.getProperty("os.name"));
        info.put("OS Version", System.getProperty("os.version"));
        info.put("OS Arch", System.getProperty("os.arch"));
        info.put("Available Processors", String.valueOf(Runtime.getRuntime().availableProcessors()));
        info.put("User Name", System.getProperty("user.name"));
        info.put("User Home", System.getProperty("user.home"));
        info.put("Working Directory", System.getProperty("user.dir"));
        return info;
    }

    @Override
    public Map<String, String> jvmInfo() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        Map<String, String> info = new LinkedHashMap<>();
        info.put("JVM Name", runtime.getVmName());
        info.put("JVM Vendor", runtime.getVmVendor());
        info.put("JVM Version", runtime.getVmVersion());
        info.put("Java Version", System.getProperty("java.version"));
        info.put("Java Home", System.getProperty("java.home"));
        info.put("Spec Version", runtime.getSpecVersion());
        info.put("Uptime", formatDuration(runtime.getUptime()));
        info.put("Input Arguments", String.join(" ", runtime.getInputArguments()));
        return info;
    }

    @Override
    public Map<String, String> memoryInfo() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
        Runtime runtime = Runtime.getRuntime();

        Map<String, String> info = new LinkedHashMap<>();
        info.put("Heap Used", formatBytes(heap.getUsed()));
        info.put("Heap Committed", formatBytes(heap.getCommitted()));
        info.put("Heap Max", formatBytes(heap.getMax()));
        info.put("Heap Usage", String.format("%.1f%%", (double) heap.getUsed() / heap.getMax() * 100));
        info.put("Non-Heap Used", formatBytes(nonHeap.getUsed()));
        info.put("Non-Heap Committed", formatBytes(nonHeap.getCommitted()));
        info.put("Free Memory", formatBytes(runtime.freeMemory()));
        info.put("Total Memory", formatBytes(runtime.totalMemory()));
        info.put("Max Memory", formatBytes(runtime.maxMemory()));
        return info;
    }

    @Override
    public Map<String, String> threadInfo() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        Map<String, String> info = new LinkedHashMap<>();
        info.put("Thread Count", String.valueOf(threadBean.getThreadCount()));
        info.put("Peak Thread Count", String.valueOf(threadBean.getPeakThreadCount()));
        info.put("Daemon Thread Count", String.valueOf(threadBean.getDaemonThreadCount()));
        info.put("Total Started Threads", String.valueOf(threadBean.getTotalStartedThreadCount()));
        long[] deadlocked = threadBean.findDeadlockedThreads();
        info.put("Deadlocked Threads", deadlocked == null ? "0" : String.valueOf(deadlocked.length));
        return info;
    }

    @Override
    public Map<String, String> transactionInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("Active Transactions", "0");
        info.put("Committed Transactions", "0");
        info.put("Rolled Back Transactions", "0");
        info.put("Transaction Timeout", "300s");
        return info;
    }

    // ── Logging ──

    @Override
    public List<LoggerSummary> listLoggers() {
        return logLevels.entrySet().stream()
                .map(e -> new LoggerSummary(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    public LoggerStatus loggerInfo(String loggerName) {
        String level = logLevels.get(loggerName);
        if (level == null) {
            throw new UnsupportedOperationException("Logger not found: " + loggerName);
        }
        return new LoggerStatus(loggerName, level, level, true, List.of("ConsoleHandler"));
    }

    @Override
    public String getLogLevel(String loggerName) {
        String level = logLevels.get(loggerName);
        if (level == null) {
            throw new UnsupportedOperationException("Logger not found: " + loggerName);
        }
        return level;
    }

    @Override
    public void setLogLevel(String loggerName, String level) {
        String normalized = level.toUpperCase();
        if (!List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF").contains(normalized)) {
            throw new IllegalArgumentException("Invalid log level: " + level);
        }
        logLevels.put(loggerName, normalized);
    }

    // ── JMX / MBean ──

    @Override
    public List<MBeanSummary> listMBeans() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            List<MBeanSummary> result = new ArrayList<>();
            for (ObjectName name : mbs.queryNames(null, null)) {
                MBeanInfo info = mbs.getMBeanInfo(name);
                result.add(new MBeanSummary(name.toString(), info.getClassName()));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list MBeans: " + e.getMessage(), e);
        }
    }

    @Override
    public String getMBeanAttribute(String mbeanName, String attribute) {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName objName = new ObjectName(mbeanName);
            Object value = mbs.getAttribute(objName, attribute);
            return value == null ? "null" : value.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get MBean attribute: " + e.getMessage(), e);
        }
    }

    @Override
    public void setMBeanAttribute(String mbeanName, String attribute, String value) {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName objName = new ObjectName(mbeanName);
            // Determine attribute type from MBeanInfo
            MBeanInfo info = mbs.getMBeanInfo(objName);
            String type = "java.lang.String";
            for (MBeanAttributeInfo attrInfo : info.getAttributes()) {
                if (attrInfo.getName().equals(attribute)) {
                    type = attrInfo.getType();
                    break;
                }
            }
            Object converted = convertToType(value, type);
            mbs.setAttribute(objName, new javax.management.Attribute(attribute, converted));
        } catch (Exception e) {
            throw new RuntimeException("Failed to set MBean attribute: " + e.getMessage(), e);
        }
    }

    @Override
    public String invokeMBeanOperation(String mbeanName, String operation, String[] params) {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName objName = new ObjectName(mbeanName);
            String[] signature = new String[params.length];
            java.util.Arrays.fill(signature, "java.lang.String");
            Object result = mbs.invoke(objName, operation, params, signature);
            return result == null ? "null" : result.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke MBean operation: " + e.getMessage(), e);
        }
    }

    // ── Security ──

    @Override
    public boolean authenticate(String username, String password) {
        if (username == null || password == null) return false;
        String stored = users.get(username);
        return password.equals(stored);
    }

    @Override
    public List<String> listUsers() {
        return List.copyOf(users.keySet());
    }

    @Override
    public void createUser(String username, String password) {
        if (users.containsKey(username)) {
            throw new IllegalArgumentException("User already exists: " + username);
        }
        users.put(username, password);
    }

    @Override
    public void removeUser(String username) {
        if (users.remove(username) == null) {
            throw new IllegalArgumentException("User not found: " + username);
        }
    }

    @Override
    public void changePassword(String username, String newPassword) {
        if (!users.containsKey(username)) {
            throw new IllegalArgumentException("User not found: " + username);
        }
        users.put(username, newPassword);
    }

    @Override
    public List<String> listRoles() {
        return List.of("admin", "operator", "monitor", "deployer");
    }

    @Override
    public void close() {
        // nothing to close in local mode
    }

    // ── Utilities ──

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) {
            return "%dd %dh %dm %ds".formatted(days, hours % 24, minutes % 60, seconds % 60);
        }
        if (hours > 0) {
            return "%dh %dm %ds".formatted(hours, minutes % 60, seconds % 60);
        }
        if (minutes > 0) {
            return "%dm %ds".formatted(minutes, seconds % 60);
        }
        return "%ds".formatted(seconds);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return "%.1f KB".formatted(bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return "%.1f MB".formatted(bytes / (1024.0 * 1024));
        return "%.2f GB".formatted(bytes / (1024.0 * 1024 * 1024));
    }

    private Object convertToType(String value, String type) {
        return switch (type) {
            case "int", "java.lang.Integer" -> Integer.parseInt(value);
            case "long", "java.lang.Long" -> Long.parseLong(value);
            case "boolean", "java.lang.Boolean" -> Boolean.parseBoolean(value);
            case "double", "java.lang.Double" -> Double.parseDouble(value);
            case "float", "java.lang.Float" -> Float.parseFloat(value);
            default -> value;
        };
    }
}
