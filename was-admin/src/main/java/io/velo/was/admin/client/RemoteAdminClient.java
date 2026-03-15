package io.velo.was.admin.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RemoteAdminClient implements AdminClient {

    private final String host;
    private final int port;
    private final HttpClient httpClient;
    private String authenticatedUser;

    public RemoteAdminClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    private String baseUrl() {
        return "http://" + host + ":" + port + "/admin";
    }

    // ── HTTP helpers ──────────────────────────────────────────

    private String get(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to " + baseUrl() + path + ": " + e.getMessage(), e);
        }
    }

    private String post(String path, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to " + baseUrl() + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Execute a CLI command via POST /api/execute and return the message.
     * Throws RuntimeException if the command fails.
     */
    private String executeCommand(String command) {
        String json = post("/api/execute", "{\"command\":\"" + escapeJson(command) + "\"}");
        boolean success = "true".equals(extractJsonValue(json, "success"));
        String message = extractJsonValue(json, "message");
        if (!success) {
            throw new RuntimeException("Command failed: " + (message != null ? message : "unknown error"));
        }
        return message != null ? message : "";
    }

    // ── Simple JSON parsing helpers (no external deps) ────────

    private static String extractJsonValue(String json, String key) {
        if (json == null) return null;
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        // skip whitespace after colon
        int pos = colon + 1;
        while (pos < json.length() && json.charAt(pos) == ' ') pos++;
        if (pos >= json.length()) return null;
        char ch = json.charAt(pos);
        if (ch == '"') {
            // string value - handle escaped quotes
            return extractQuotedString(json, pos);
        } else if (ch == 't') {
            return "true";
        } else if (ch == 'f') {
            return "false";
        } else if (ch == 'n' && json.startsWith("null", pos)) {
            return null;
        } else {
            // number or other literal
            int end = pos;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}'
                    && json.charAt(end) != ']') {
                end++;
            }
            return json.substring(pos, end).trim();
        }
    }

    /**
     * Extract a quoted string starting at the opening quote position,
     * handling escaped characters.
     */
    private static String extractQuotedString(String json, int openQuote) {
        StringBuilder sb = new StringBuilder();
        int i = openQuote + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> { sb.append('\\'); sb.append(next); }
                }
                i += 2;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static long extractJsonLong(String json, String key) {
        String val = extractJsonValue(json, key);
        if (val == null) return 0L;
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static int extractJsonInt(String json, String key) {
        String val = extractJsonValue(json, key);
        if (val == null) return 0;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean extractJsonBool(String json, String key) {
        return "true".equals(extractJsonValue(json, key));
    }

    /**
     * Extract JSON array elements from within [...].
     * Each element is returned as a raw JSON string.
     */
    private static List<String> extractJsonArray(String json, String arrayKey) {
        List<String> result = new ArrayList<>();
        String search = "\"" + arrayKey + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return result;
        int bracketStart = json.indexOf('[', idx + search.length());
        if (bracketStart < 0) return result;

        int depth = 0;
        int elementStart = -1;
        for (int i = bracketStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                // skip over quoted strings to avoid counting brackets inside strings
                i++;
                while (i < json.length()) {
                    if (json.charAt(i) == '\\') {
                        i++; // skip escaped char
                    } else if (json.charAt(i) == '"') {
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (c == '[') {
                depth++;
                if (depth == 1) {
                    elementStart = i + 1;
                }
            } else if (c == '{') {
                if (depth == 1) {
                    // find matching }
                    int objStart = i;
                    int objDepth = 0;
                    boolean inString = false;
                    for (int j = i; j < json.length(); j++) {
                        char oc = json.charAt(j);
                        if (inString) {
                            if (oc == '\\') {
                                j++;
                            } else if (oc == '"') {
                                inString = false;
                            }
                        } else {
                            if (oc == '"') {
                                inString = true;
                            } else if (oc == '{') {
                                objDepth++;
                            } else if (oc == '}') {
                                objDepth--;
                                if (objDepth == 0) {
                                    result.add(json.substring(objStart, j + 1));
                                    i = j;
                                    break;
                                }
                            }
                        }
                    }
                }
            } else if (c == ']') {
                depth--;
                if (depth == 0) break;
            }
        }
        return result;
    }

    /**
     * Parse a flat JSON object {"key":"value","key2":"value2",...} into a Map.
     */
    private static Map<String, String> parseJsonMap(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return map;
        // Find all key-value pairs using a simple pattern
        Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            map.put(unescapeJson(matcher.group(1)), unescapeJson(matcher.group(2)));
        }
        return map;
    }

    /**
     * Extract a string array like ["a","b","c"] from a JSON array field.
     */
    private static List<String> extractStringArray(String json, String key) {
        List<String> result = new ArrayList<>();
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return result;
        int bracketStart = json.indexOf('[', idx + search.length());
        if (bracketStart < 0) return result;
        int bracketEnd = json.indexOf(']', bracketStart);
        if (bracketEnd < 0) return result;
        String arrayContent = json.substring(bracketStart + 1, bracketEnd);
        Pattern p = Pattern.compile("\"([^\"]*)\"");
        Matcher m = p.matcher(arrayContent);
        while (m.find()) {
            result.add(unescapeJson(m.group(1)));
        }
        return result;
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ── Connection ────────────────────────────────────────────

    @Override
    public boolean isConnected() {
        try {
            get("/api/status");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String connectionInfo() {
        return "remote://" + host + ":" + port;
    }

    // ── Domain ────────────────────────────────────────────────

    @Override
    public List<DomainSummary> listDomains() {
        String output = executeCommand("list-domains");
        List<DomainSummary> result = new ArrayList<>();
        if (output == null || output.isBlank()) return result;
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("No ")) continue;
            // Expect lines like "domainName  RUNNING" or just "domainName"
            String[] parts = line.split("\\s+", 2);
            String name = parts[0];
            String status = parts.length > 1 ? parts[1] : "UNKNOWN";
            result.add(new DomainSummary(name, status));
        }
        return result;
    }

    @Override
    public DomainStatus domainInfo(String domainName) {
        String output = executeCommand("domain-info " + domainName);
        // Parse text output: expect key: value lines
        Map<String, String> props = parseOutputLines(output);
        String name = props.getOrDefault("Name", domainName);
        String status = props.getOrDefault("Status", "UNKNOWN");
        String adminServer = props.getOrDefault("Admin Server", "");
        int serverCount = parseIntSafe(props.getOrDefault("Server Count", "0"));
        Map<String, String> properties = new LinkedHashMap<>(props);
        properties.remove("Name");
        properties.remove("Status");
        properties.remove("Admin Server");
        properties.remove("Server Count");
        return new DomainStatus(name, status, adminServer, serverCount, properties);
    }

    @Override
    public void createDomain(String domainName) {
        executeCommand("create-domain " + domainName);
    }

    @Override
    public void removeDomain(String domainName) {
        executeCommand("remove-domain " + domainName);
    }

    @Override
    public void setDomainProperty(String domainName, String key, String value) {
        executeCommand("set-domain-property " + domainName + " " + key + " " + value);
    }

    @Override
    public String getDomainProperty(String domainName, String key) {
        String output = executeCommand("get-domain-property " + domainName + " " + key);
        return output.trim();
    }

    // ── Server ────────────────────────────────────────────────

    @Override
    public ServerStatus serverInfo(String serverName) {
        String json = get("/api/servers");
        List<String> servers = extractJsonArray(json, "servers");
        for (String s : servers) {
            String name = extractJsonValue(s, "name");
            if (serverName.equals(name)) {
                return new ServerStatus(
                        name,
                        extractJsonValue(s, "nodeId") != null ? extractJsonValue(s, "nodeId") : "",
                        extractJsonValue(s, "status") != null ? extractJsonValue(s, "status") : "UNKNOWN",
                        extractJsonValue(s, "host") != null ? extractJsonValue(s, "host") : "",
                        extractJsonInt(s, "port"),
                        extractJsonValue(s, "transport") != null ? extractJsonValue(s, "transport") : "",
                        extractJsonBool(s, "tlsEnabled"),
                        extractJsonLong(s, "uptimeMs"),
                        extractJsonInt(s, "bossThreads"),
                        extractJsonInt(s, "workerThreads"),
                        extractJsonInt(s, "businessThreads")
                );
            }
        }
        // Fall back to execute command
        String output = executeCommand("server-info " + serverName);
        Map<String, String> props = parseOutputLines(output);
        return new ServerStatus(
                serverName,
                props.getOrDefault("Node ID", ""),
                props.getOrDefault("Status", "UNKNOWN"),
                props.getOrDefault("Host", ""),
                parseIntSafe(props.getOrDefault("Port", "0")),
                props.getOrDefault("Transport", ""),
                "true".equalsIgnoreCase(props.getOrDefault("TLS Enabled", "false")),
                parseLongSafe(props.getOrDefault("Uptime", "0")),
                parseIntSafe(props.getOrDefault("Boss Threads", "0")),
                parseIntSafe(props.getOrDefault("Worker Threads", "0")),
                parseIntSafe(props.getOrDefault("Business Threads", "0"))
        );
    }

    @Override
    public List<ServerSummary> listServers() {
        String json = get("/api/servers");
        List<String> servers = extractJsonArray(json, "servers");
        List<ServerSummary> result = new ArrayList<>();
        for (String s : servers) {
            result.add(new ServerSummary(
                    extractJsonValue(s, "name") != null ? extractJsonValue(s, "name") : "",
                    extractJsonValue(s, "nodeId") != null ? extractJsonValue(s, "nodeId") : "",
                    extractJsonValue(s, "status") != null ? extractJsonValue(s, "status") : "UNKNOWN"
            ));
        }
        return result;
    }

    @Override
    public void startServer(String serverName) {
        executeCommand("start-server " + serverName);
    }

    @Override
    public void stopServer(String serverName) {
        executeCommand("stop-server " + serverName);
    }

    @Override
    public void restartServer(String serverName) {
        executeCommand("restart-server " + serverName);
    }

    @Override
    public void suspendServer(String serverName) {
        executeCommand("suspend-server " + serverName);
    }

    @Override
    public void resumeServer(String serverName) {
        executeCommand("resume-server " + serverName);
    }

    @Override
    public void killServer(String serverName) {
        executeCommand("kill-server " + serverName);
    }

    // ── Cluster ───────────────────────────────────────────────

    @Override
    public List<ClusterSummary> listClusters() {
        String json = get("/api/clusters");
        List<String> clusters = extractJsonArray(json, "clusters");
        List<ClusterSummary> result = new ArrayList<>();
        for (String c : clusters) {
            result.add(new ClusterSummary(
                    extractJsonValue(c, "name") != null ? extractJsonValue(c, "name") : "",
                    extractJsonInt(c, "memberCount"),
                    extractJsonValue(c, "status") != null ? extractJsonValue(c, "status") : "UNKNOWN"
            ));
        }
        return result;
    }

    @Override
    public ClusterStatus clusterInfo(String clusterName) {
        String output = executeCommand("cluster-info " + clusterName);
        Map<String, String> props = parseOutputLines(output);
        String name = props.getOrDefault("Name", clusterName);
        String status = props.getOrDefault("Status", "UNKNOWN");
        String membersStr = props.getOrDefault("Members", "");
        List<String> members = new ArrayList<>();
        if (!membersStr.isEmpty()) {
            for (String m : membersStr.split(",")) {
                m = m.trim();
                if (!m.isEmpty()) members.add(m);
            }
        }
        return new ClusterStatus(name, status, members);
    }

    @Override
    public void startCluster(String clusterName) {
        executeCommand("start-cluster " + clusterName);
    }

    @Override
    public void stopCluster(String clusterName) {
        executeCommand("stop-cluster " + clusterName);
    }

    @Override
    public void restartCluster(String clusterName) {
        executeCommand("restart-cluster " + clusterName);
    }

    @Override
    public void addServerToCluster(String clusterName, String serverName) {
        executeCommand("add-server-to-cluster " + clusterName + " " + serverName);
    }

    @Override
    public void removeServerFromCluster(String clusterName, String serverName) {
        executeCommand("remove-server-from-cluster " + clusterName + " " + serverName);
    }

    // ── Application ───────────────────────────────────────────

    @Override
    public List<AppSummary> listApplications() {
        String json = get("/api/applications");
        List<String> apps = extractJsonArray(json, "applications");
        List<AppSummary> result = new ArrayList<>();
        for (String a : apps) {
            result.add(new AppSummary(
                    extractJsonValue(a, "name") != null ? extractJsonValue(a, "name") : "",
                    extractJsonValue(a, "contextPath") != null ? extractJsonValue(a, "contextPath") : "",
                    extractJsonValue(a, "status") != null ? extractJsonValue(a, "status") : "UNKNOWN"
            ));
        }
        return result;
    }

    @Override
    public AppStatus applicationInfo(String appName) {
        String json = get("/api/applications");
        List<String> apps = extractJsonArray(json, "applications");
        for (String a : apps) {
            String name = extractJsonValue(a, "name");
            if (appName.equals(name)) {
                return new AppStatus(
                        name,
                        extractJsonValue(a, "contextPath") != null ? extractJsonValue(a, "contextPath") : "",
                        extractJsonValue(a, "status") != null ? extractJsonValue(a, "status") : "UNKNOWN",
                        extractJsonInt(a, "servletCount"),
                        extractJsonInt(a, "filterCount")
                );
            }
        }
        // Fall back to execute command
        String output = executeCommand("application-info " + appName);
        Map<String, String> props = parseOutputLines(output);
        return new AppStatus(
                appName,
                props.getOrDefault("Context Path", ""),
                props.getOrDefault("Status", "UNKNOWN"),
                parseIntSafe(props.getOrDefault("Servlet Count", "0")),
                parseIntSafe(props.getOrDefault("Filter Count", "0"))
        );
    }

    @Override
    public void deploy(String path, String contextPath) {
        executeCommand("deploy " + path + " " + contextPath);
    }

    @Override
    public void undeploy(String appName) {
        executeCommand("undeploy " + appName);
    }

    @Override
    public void redeploy(String appName) {
        executeCommand("redeploy " + appName);
    }

    @Override
    public void startApplication(String appName) {
        executeCommand("start-application " + appName);
    }

    @Override
    public void stopApplication(String appName) {
        executeCommand("stop-application " + appName);
    }

    // ── Datasource ────────────────────────────────────────────

    @Override
    public List<DatasourceSummary> listDatasources() {
        String output = executeCommand("list-datasources");
        List<DatasourceSummary> result = new ArrayList<>();
        if (output == null || output.isBlank()) return result;
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("No ") || line.startsWith("---")) continue;
            String[] parts = line.split("\\s+", 3);
            if (parts.length >= 1) {
                String name = parts[0];
                String type = parts.length > 1 ? parts[1] : "UNKNOWN";
                String status = parts.length > 2 ? parts[2] : "UNKNOWN";
                result.add(new DatasourceSummary(name, type, status));
            }
        }
        return result;
    }

    @Override
    public DatasourceStatus datasourceInfo(String dsName) {
        String output = executeCommand("datasource-info " + dsName);
        Map<String, String> props = parseOutputLines(output);
        return new DatasourceStatus(
                dsName,
                props.getOrDefault("Type", "UNKNOWN"),
                props.getOrDefault("URL", ""),
                props.getOrDefault("Status", "UNKNOWN"),
                parseIntSafe(props.getOrDefault("Active Connections", "0")),
                parseIntSafe(props.getOrDefault("Idle Connections", "0")),
                parseIntSafe(props.getOrDefault("Max Connections", "0"))
        );
    }

    @Override
    public void enableDatasource(String dsName) {
        executeCommand("enable-datasource " + dsName);
    }

    @Override
    public void disableDatasource(String dsName) {
        executeCommand("disable-datasource " + dsName);
    }

    @Override
    public void testDatasource(String dsName) {
        executeCommand("test-datasource " + dsName);
    }

    // ── JDBC / Connection Pool ────────────────────────────────

    @Override
    public List<JdbcResourceSummary> listJdbcResources() {
        String output = executeCommand("list-jdbc-resources");
        List<JdbcResourceSummary> result = new ArrayList<>();
        if (output == null || output.isBlank()) return result;
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("No ") || line.startsWith("---")) continue;
            String[] parts = line.split("\\s+", 3);
            if (parts.length >= 1) {
                String name = parts[0];
                String poolName = parts.length > 1 ? parts[1] : "";
                String type = parts.length > 2 ? parts[2] : "UNKNOWN";
                result.add(new JdbcResourceSummary(name, poolName, type));
            }
        }
        return result;
    }

    @Override
    public JdbcResourceStatus jdbcResourceInfo(String resourceName) {
        String output = executeCommand("jdbc-resource-info " + resourceName);
        Map<String, String> props = parseOutputLines(output);
        return new JdbcResourceStatus(
                resourceName,
                props.getOrDefault("Pool Name", ""),
                props.getOrDefault("Driver Class", ""),
                props.getOrDefault("URL", ""),
                parseIntSafe(props.getOrDefault("Active Connections", "0")),
                parseIntSafe(props.getOrDefault("Idle Connections", "0")),
                parseIntSafe(props.getOrDefault("Max Pool Size", "0"))
        );
    }

    @Override
    public void resetConnectionPool(String poolName) {
        executeCommand("reset-connection-pool " + poolName);
    }

    @Override
    public void flushConnectionPool(String poolName) {
        executeCommand("flush-connection-pool " + poolName);
    }

    // ── JMS ───────────────────────────────────────────────────

    @Override
    public List<JmsServerSummary> listJmsServers() {
        String output = executeCommand("list-jms-servers");
        List<JmsServerSummary> result = new ArrayList<>();
        if (output == null || output.isBlank()) return result;
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("No ") || line.startsWith("---")) continue;
            String[] parts = line.split("\\s+", 2);
            if (parts.length >= 1) {
                String name = parts[0];
                String status = parts.length > 1 ? parts[1] : "UNKNOWN";
                result.add(new JmsServerSummary(name, status));
            }
        }
        return result;
    }

    @Override
    public JmsServerStatus jmsServerInfo(String serverName) {
        String output = executeCommand("jms-server-info " + serverName);
        Map<String, String> props = parseOutputLines(output);
        return new JmsServerStatus(
                serverName,
                props.getOrDefault("Status", "UNKNOWN"),
                props.getOrDefault("Type", "UNKNOWN"),
                parseIntSafe(props.getOrDefault("Destination Count", "0"))
        );
    }

    @Override
    public List<JmsDestinationSummary> listJmsDestinations() {
        String output = executeCommand("list-jms-destinations");
        List<JmsDestinationSummary> result = new ArrayList<>();
        if (output == null || output.isBlank()) return result;
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("No ") || line.startsWith("---")) continue;
            String[] parts = line.split("\\s+", 3);
            if (parts.length >= 1) {
                String name = parts[0];
                String type = parts.length > 1 ? parts[1] : "UNKNOWN";
                int count = parts.length > 2 ? parseIntSafe(parts[2]) : 0;
                result.add(new JmsDestinationSummary(name, type, count));
            }
        }
        return result;
    }

    @Override
    public JmsDestinationStatus jmsDestinationInfo(String destinationName) {
        String output = executeCommand("jms-destination-info " + destinationName);
        Map<String, String> props = parseOutputLines(output);
        return new JmsDestinationStatus(
                destinationName,
                props.getOrDefault("Type", "UNKNOWN"),
                parseIntSafe(props.getOrDefault("Message Count", "0")),
                parseIntSafe(props.getOrDefault("Consumer Count", "0")),
                parseLongSafe(props.getOrDefault("Bytes Used", "0"))
        );
    }

    @Override
    public void purgeJmsQueue(String queueName) {
        executeCommand("purge-jms-queue " + queueName);
    }

    // ── Thread Pool ───────────────────────────────────────────

    @Override
    public List<ThreadPoolSummary> listThreadPools() {
        String json = get("/api/threadpools");
        List<String> pools = extractJsonArray(json, "threadPools");
        List<ThreadPoolSummary> result = new ArrayList<>();
        for (String p : pools) {
            result.add(new ThreadPoolSummary(
                    extractJsonValue(p, "name") != null ? extractJsonValue(p, "name") : "",
                    extractJsonInt(p, "activeCount"),
                    extractJsonInt(p, "poolSize"),
                    extractJsonInt(p, "maxPoolSize")
            ));
        }
        return result;
    }

    @Override
    public ThreadPoolStatus threadPoolInfo(String poolName) {
        String output = executeCommand("thread-pool-info " + poolName);
        Map<String, String> props = parseOutputLines(output);
        return new ThreadPoolStatus(
                poolName,
                parseIntSafe(props.getOrDefault("Active Count", "0")),
                parseIntSafe(props.getOrDefault("Pool Size", "0")),
                parseIntSafe(props.getOrDefault("Max Pool Size", "0")),
                parseLongSafe(props.getOrDefault("Completed Task Count", "0")),
                parseIntSafe(props.getOrDefault("Queue Size", "0"))
        );
    }

    @Override
    public void resetThreadPool(String poolName) {
        executeCommand("reset-thread-pool " + poolName);
    }

    @Override
    public Map<String, String> resourceInfo() {
        String json = get("/api/resources");
        return parseJsonMap(json);
    }

    // ── Monitoring ────────────────────────────────────────────

    @Override
    public Map<String, String> systemInfo() {
        String json = get("/api/system");
        return parseJsonMap(json);
    }

    @Override
    public Map<String, String> jvmInfo() {
        String json = get("/api/jvm");
        return parseJsonMap(json);
    }

    @Override
    public Map<String, String> memoryInfo() {
        // Memory info is part of resources endpoint
        String json = get("/api/resources");
        Map<String, String> result = new LinkedHashMap<>();
        // Extract nested values
        String heapUsed = extractJsonValue(json, "used");
        String heapMax = extractJsonValue(json, "max");
        String heapCommitted = extractJsonValue(json, "committed");
        if (heapUsed != null) result.put("Heap Used", heapUsed);
        if (heapMax != null) result.put("Heap Max", heapMax);
        if (heapCommitted != null) result.put("Heap Committed", heapCommitted);
        return result;
    }

    @Override
    public Map<String, String> threadInfo() {
        String json = get("/api/threads");
        Map<String, String> result = new LinkedHashMap<>();
        result.put("Thread Count", String.valueOf(extractJsonInt(json, "threadCount")));
        result.put("Daemon Thread Count", String.valueOf(extractJsonInt(json, "daemonThreadCount")));
        result.put("Peak Thread Count", String.valueOf(extractJsonInt(json, "peakThreadCount")));
        result.put("Deadlocked Count", String.valueOf(extractJsonInt(json, "deadlockedCount")));
        return result;
    }

    @Override
    public Map<String, String> transactionInfo() {
        String output = executeCommand("transaction-info");
        return parseOutputLines(output);
    }

    // ── Logging ───────────────────────────────────────────────

    @Override
    public List<LoggerSummary> listLoggers() {
        String json = get("/api/loggers");
        List<String> loggers = extractJsonArray(json, "loggers");
        List<LoggerSummary> result = new ArrayList<>();
        for (String l : loggers) {
            result.add(new LoggerSummary(
                    extractJsonValue(l, "name") != null ? extractJsonValue(l, "name") : "",
                    extractJsonValue(l, "level") != null ? extractJsonValue(l, "level") : "INFO"
            ));
        }
        return result;
    }

    @Override
    public LoggerStatus loggerInfo(String loggerName) {
        String output = executeCommand("logger-info " + loggerName);
        Map<String, String> props = parseOutputLines(output);
        String handlersStr = props.getOrDefault("Handlers", "");
        List<String> handlers = new ArrayList<>();
        if (!handlersStr.isEmpty()) {
            for (String h : handlersStr.split(",")) {
                h = h.trim();
                if (!h.isEmpty()) handlers.add(h);
            }
        }
        return new LoggerStatus(
                loggerName,
                props.getOrDefault("Level", "INFO"),
                props.getOrDefault("Effective Level", props.getOrDefault("Level", "INFO")),
                "true".equalsIgnoreCase(props.getOrDefault("Additivity", "true")),
                handlers
        );
    }

    @Override
    public String getLogLevel(String loggerName) {
        // Try to find in the loggers list first
        List<LoggerSummary> loggers = listLoggers();
        for (LoggerSummary l : loggers) {
            if (loggerName.equals(l.name())) {
                return l.level();
            }
        }
        String output = executeCommand("get-log-level " + loggerName);
        return output.trim();
    }

    @Override
    public void setLogLevel(String loggerName, String level) {
        post("/api/loggers/set",
                "{\"logger\":\"" + escapeJson(loggerName) + "\",\"level\":\"" + escapeJson(level) + "\"}");
    }

    // ── JMX / MBean ──────────────────────────────────────────

    @Override
    public List<MBeanSummary> listMBeans() {
        String output = executeCommand("list-mbeans");
        List<MBeanSummary> result = new ArrayList<>();
        if (output == null || output.isBlank()) return result;
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("No ") || line.startsWith("---")) continue;
            String[] parts = line.split("\\s+", 2);
            if (parts.length >= 1) {
                String objectName = parts[0];
                String className = parts.length > 1 ? parts[1] : "";
                result.add(new MBeanSummary(objectName, className));
            }
        }
        return result;
    }

    @Override
    public String getMBeanAttribute(String mbeanName, String attribute) {
        String output = executeCommand("get-mbean-attribute " + mbeanName + " " + attribute);
        return output.trim();
    }

    @Override
    public void setMBeanAttribute(String mbeanName, String attribute, String value) {
        executeCommand("set-mbean-attribute " + mbeanName + " " + attribute + " " + value);
    }

    @Override
    public String invokeMBeanOperation(String mbeanName, String operation, String[] params) {
        StringBuilder cmd = new StringBuilder("invoke-mbean-operation ");
        cmd.append(mbeanName).append(" ").append(operation);
        if (params != null) {
            for (String param : params) {
                cmd.append(" ").append(param);
            }
        }
        return executeCommand(cmd.toString()).trim();
    }

    // ── Security ──────────────────────────────────────────────

    @Override
    public boolean authenticate(String username, String password) {
        try {
            String json = post("/api/execute",
                    "{\"command\":\"authenticate " + escapeJson(username) + " " + escapeJson(password) + "\"}");
            boolean success = "true".equals(extractJsonValue(json, "success"));
            if (success) {
                this.authenticatedUser = username;
            }
            return success;
        } catch (Exception e) {
            // If 'authenticate' is not a known command, treat valid connection as success
            try {
                get("/api/status");
                this.authenticatedUser = username;
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    @Override
    public List<String> listUsers() {
        String json = get("/api/users");
        List<String> users = extractJsonArray(json, "users");
        List<String> result = new ArrayList<>();
        for (String u : users) {
            String username = extractJsonValue(u, "username");
            if (username != null) {
                result.add(username);
            }
        }
        return result;
    }

    @Override
    public void createUser(String username, String password) {
        String json = post("/api/users/create",
                "{\"username\":\"" + escapeJson(username) + "\",\"password\":\"" + escapeJson(password) + "\"}");
        boolean success = "true".equals(extractJsonValue(json, "success"));
        if (!success) {
            String message = extractJsonValue(json, "message");
            throw new RuntimeException("Failed to create user: " + (message != null ? message : "unknown error"));
        }
    }

    @Override
    public void removeUser(String username) {
        String json = post("/api/users/remove",
                "{\"username\":\"" + escapeJson(username) + "\"}");
        boolean success = "true".equals(extractJsonValue(json, "success"));
        if (!success) {
            String message = extractJsonValue(json, "message");
            throw new RuntimeException("Failed to remove user: " + (message != null ? message : "unknown error"));
        }
    }

    @Override
    public void changePassword(String username, String newPassword) {
        String json = post("/api/users/change-password",
                "{\"username\":\"" + escapeJson(username) + "\",\"password\":\"" + escapeJson(newPassword) + "\"}");
        boolean success = "true".equals(extractJsonValue(json, "success"));
        if (!success) {
            String message = extractJsonValue(json, "message");
            throw new RuntimeException("Failed to change password: " + (message != null ? message : "unknown error"));
        }
    }

    @Override
    public List<String> listRoles() {
        String output = executeCommand("list-roles");
        List<String> result = new ArrayList<>();
        if (output == null || output.isBlank()) return result;
        for (String line : output.split("\n")) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("No ") && !line.startsWith("---")) {
                result.add(line);
            }
        }
        return result;
    }

    @Override
    public void close() {
        // HttpClient doesn't need explicit close in Java 21 standard usage
    }

    // ── Text output parsing helpers ───────────────────────────

    /**
     * Parse CLI output lines in "Key: Value" format into a map.
     */
    private static Map<String, String> parseOutputLines(String output) {
        Map<String, String> map = new LinkedHashMap<>();
        if (output == null || output.isBlank()) return map;
        for (String line : output.split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && colon < line.length() - 1) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (!key.isEmpty()) {
                    map.put(key, value);
                }
            }
        }
        return map;
    }

    private static int parseIntSafe(String s) {
        if (s == null) return 0;
        try {
            // Remove any non-digit suffix (e.g. "ms", " bytes")
            s = s.trim().replaceAll("[^\\d-].*", "");
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLongSafe(String s) {
        if (s == null) return 0L;
        try {
            s = s.trim().replaceAll("[^\\d-].*", "");
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
