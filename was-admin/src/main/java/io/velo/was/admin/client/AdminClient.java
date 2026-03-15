package io.velo.was.admin.client;

import java.util.List;
import java.util.Map;

public interface AdminClient extends AutoCloseable {

    // ── Connection ──
    boolean isConnected();
    String connectionInfo();

    // ── Domain ──
    List<DomainSummary> listDomains();
    DomainStatus domainInfo(String domainName);
    void createDomain(String domainName);
    void removeDomain(String domainName);
    void setDomainProperty(String domainName, String key, String value);
    String getDomainProperty(String domainName, String key);

    // ── Server ──
    ServerStatus serverInfo(String serverName);
    List<ServerSummary> listServers();
    void startServer(String serverName);
    void stopServer(String serverName);
    void restartServer(String serverName);
    void suspendServer(String serverName);
    void resumeServer(String serverName);
    void killServer(String serverName);

    // ── Cluster ──
    List<ClusterSummary> listClusters();
    ClusterStatus clusterInfo(String clusterName);
    void startCluster(String clusterName);
    void stopCluster(String clusterName);
    void restartCluster(String clusterName);
    void addServerToCluster(String clusterName, String serverName);
    void removeServerFromCluster(String clusterName, String serverName);

    // ── Application ──
    List<AppSummary> listApplications();
    AppStatus applicationInfo(String appName);
    void deploy(String path, String contextPath);
    void undeploy(String appName);
    void redeploy(String appName);
    void startApplication(String appName);
    void stopApplication(String appName);

    // ── Datasource ──
    List<DatasourceSummary> listDatasources();
    DatasourceStatus datasourceInfo(String dsName);
    void enableDatasource(String dsName);
    void disableDatasource(String dsName);
    void testDatasource(String dsName);

    // ── JDBC / Connection Pool ──
    List<JdbcResourceSummary> listJdbcResources();
    JdbcResourceStatus jdbcResourceInfo(String resourceName);
    void resetConnectionPool(String poolName);
    void flushConnectionPool(String poolName);

    // ── JMS ──
    List<JmsServerSummary> listJmsServers();
    JmsServerStatus jmsServerInfo(String serverName);
    List<JmsDestinationSummary> listJmsDestinations();
    JmsDestinationStatus jmsDestinationInfo(String destinationName);
    void purgeJmsQueue(String queueName);

    // ── Thread Pool ──
    List<ThreadPoolSummary> listThreadPools();
    ThreadPoolStatus threadPoolInfo(String poolName);
    void resetThreadPool(String poolName);
    Map<String, String> resourceInfo();

    // ── Monitoring ──
    Map<String, String> systemInfo();
    Map<String, String> jvmInfo();
    Map<String, String> memoryInfo();
    Map<String, String> threadInfo();
    Map<String, String> transactionInfo();

    // ── Logging ──
    List<LoggerSummary> listLoggers();
    LoggerStatus loggerInfo(String loggerName);
    String getLogLevel(String loggerName);
    void setLogLevel(String loggerName, String level);

    // ── JMX / MBean ──
    List<MBeanSummary> listMBeans();
    String getMBeanAttribute(String mbeanName, String attribute);
    void setMBeanAttribute(String mbeanName, String attribute, String value);
    String invokeMBeanOperation(String mbeanName, String operation, String[] params);

    // ── Security ──
    boolean authenticate(String username, String password);
    List<String> listUsers();
    void createUser(String username, String password);
    void removeUser(String username);
    void changePassword(String username, String newPassword);
    List<String> listRoles();

    // ── DTOs ──

    record DomainSummary(String name, String status) {}
    record DomainStatus(String name, String status, String adminServerName, int serverCount,
                        Map<String, String> properties) {}

    record ServerSummary(String name, String nodeId, String status) {}
    record ServerStatus(String name, String nodeId, String status, String host, int port,
                        String transport, boolean tlsEnabled, long uptimeMillis,
                        int bossThreads, int workerThreads, int businessThreads) {}

    record ClusterSummary(String name, int memberCount, String status) {}
    record ClusterStatus(String name, String status, List<String> members) {}

    record AppSummary(String name, String contextPath, String status) {}
    record AppStatus(String name, String contextPath, String status, int servletCount, int filterCount) {}

    record DatasourceSummary(String name, String type, String status) {}
    record DatasourceStatus(String name, String type, String url, String status,
                            int activeConnections, int idleConnections, int maxConnections) {}

    record JdbcResourceSummary(String name, String poolName, String type) {}
    record JdbcResourceStatus(String name, String poolName, String driverClass, String url,
                              int activeConnections, int idleConnections, int maxPoolSize) {}

    record JmsServerSummary(String name, String status) {}
    record JmsServerStatus(String name, String status, String type, int destinationCount) {}

    record JmsDestinationSummary(String name, String type, int messageCount) {}
    record JmsDestinationStatus(String name, String type, int messageCount,
                                int consumerCount, long bytesUsed) {}

    record ThreadPoolSummary(String name, int activeCount, int poolSize, int maxPoolSize) {}
    record ThreadPoolStatus(String name, int activeCount, int poolSize, int maxPoolSize,
                            long completedTaskCount, int queueSize) {}

    record LoggerSummary(String name, String level) {}
    record LoggerStatus(String name, String level, String effectiveLevel, boolean additivity,
                        List<String> handlers) {}

    record MBeanSummary(String objectName, String className) {}
}
