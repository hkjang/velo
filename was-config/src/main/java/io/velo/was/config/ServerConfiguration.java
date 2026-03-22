package io.velo.was.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ServerConfiguration {

    private Server server = new Server();

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public void validate() {
        if (server == null) {
            throw new IllegalArgumentException("server section is required");
        }
        server.validate();
    }

    public static class Server {
        private String name = "velo-was";
        private String nodeId = "node-1";
        private long gracefulShutdownMillis = 30_000;
        private Listener listener = new Listener();
        private Threading threading = new Threading();
        private Tls tls = new Tls();
        private ServletEngine servlet = new ServletEngine();
        private Jsp jsp = new Jsp();
        private Compression compression = new Compression();
        private Session session = new Session();
        private Deploy deploy = new Deploy();
        private WebAdmin webAdmin = new WebAdmin();
        private AiPlatform aiPlatform = new AiPlatform();
        private List<TcpListenerConfig> tcpListeners = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public long getGracefulShutdownMillis() {
            return gracefulShutdownMillis;
        }

        public void setGracefulShutdownMillis(long gracefulShutdownMillis) {
            this.gracefulShutdownMillis = gracefulShutdownMillis;
        }

        public Listener getListener() {
            return listener;
        }

        public void setListener(Listener listener) {
            this.listener = listener;
        }

        public Threading getThreading() {
            return threading;
        }

        public void setThreading(Threading threading) {
            this.threading = threading;
        }

        public Tls getTls() {
            return tls;
        }

        public void setTls(Tls tls) {
            this.tls = tls;
        }

        public Jsp getJsp() {
            return jsp;
        }

        public void setJsp(Jsp jsp) {
            this.jsp = jsp;
        }

        public ServletEngine getServlet() {
            return servlet;
        }

        public void setServlet(ServletEngine servlet) {
            this.servlet = servlet;
        }

        public Compression getCompression() {
            return compression;
        }

        public void setCompression(Compression compression) {
            this.compression = compression;
        }

        public Session getSession() {
            return session;
        }

        public void setSession(Session session) {
            this.session = session;
        }

        public Deploy getDeploy() {
            return deploy;
        }

        public void setDeploy(Deploy deploy) {
            this.deploy = deploy;
        }

        public WebAdmin getWebAdmin() {
            return webAdmin;
        }

        public void setWebAdmin(WebAdmin webAdmin) {
            this.webAdmin = webAdmin;
        }

        public AiPlatform getAiPlatform() {
            return aiPlatform;
        }

        public void setAiPlatform(AiPlatform aiPlatform) {
            this.aiPlatform = aiPlatform;
        }

        public List<TcpListenerConfig> getTcpListeners() {
            return tcpListeners;
        }

        public void setTcpListeners(List<TcpListenerConfig> tcpListeners) {
            this.tcpListeners = tcpListeners;
        }

        public void validate() {
            if (listener == null) {
                throw new IllegalArgumentException("server.listener is required");
            }
            listener.validate();
            if (threading == null) {
                threading = new Threading();
            }
            if (tls == null) {
                tls = new Tls();
            }
            if (servlet == null) {
                servlet = new ServletEngine();
            }
            servlet.validate();
            if (jsp == null) {
                jsp = new Jsp();
            }
            if (compression == null) {
                compression = new Compression();
            }
            if (session == null) {
                session = new Session();
            }
            session.validate();
            if (deploy == null) {
                deploy = new Deploy();
            }
            if (webAdmin == null) {
                webAdmin = new WebAdmin();
            }
            if (aiPlatform == null) {
                aiPlatform = new AiPlatform();
            }
            aiPlatform.validate();
            if (tcpListeners == null) {
                tcpListeners = new ArrayList<>();
            }
            for (TcpListenerConfig tcpListener : tcpListeners) {
                tcpListener.validate();
            }
        }
    }

    public static class Listener {
        private String host = "0.0.0.0";
        private int port = 8080;
        private int soBacklog = 2048;
        private boolean reuseAddress = true;
        private boolean tcpNoDelay = true;
        private boolean keepAlive = true;
        private int maxContentLength = 10 * 1024 * 1024;
        private int idleTimeoutSeconds = 60;
        private int maxHeaderSize = 8192;
        private int maxInitialLineLength = 4096;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getSoBacklog() {
            return soBacklog;
        }

        public void setSoBacklog(int soBacklog) {
            this.soBacklog = soBacklog;
        }

        public boolean isReuseAddress() {
            return reuseAddress;
        }

        public void setReuseAddress(boolean reuseAddress) {
            this.reuseAddress = reuseAddress;
        }

        public boolean isTcpNoDelay() {
            return tcpNoDelay;
        }

        public void setTcpNoDelay(boolean tcpNoDelay) {
            this.tcpNoDelay = tcpNoDelay;
        }

        public boolean isKeepAlive() {
            return keepAlive;
        }

        public void setKeepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
        }

        public int getMaxContentLength() {
            return maxContentLength;
        }

        public void setMaxContentLength(int maxContentLength) {
            this.maxContentLength = maxContentLength;
        }

        public int getIdleTimeoutSeconds() {
            return idleTimeoutSeconds;
        }

        public void setIdleTimeoutSeconds(int idleTimeoutSeconds) {
            this.idleTimeoutSeconds = idleTimeoutSeconds;
        }

        public int getMaxHeaderSize() {
            return maxHeaderSize;
        }

        public void setMaxHeaderSize(int maxHeaderSize) {
            this.maxHeaderSize = maxHeaderSize;
        }

        public int getMaxInitialLineLength() {
            return maxInitialLineLength;
        }

        public void setMaxInitialLineLength(int maxInitialLineLength) {
            this.maxInitialLineLength = maxInitialLineLength;
        }

        public void validate() {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("server.listener.port must be between 1 and 65535");
            }
            if (maxContentLength <= 0) {
                throw new IllegalArgumentException("server.listener.maxContentLength must be positive");
            }
        }
    }

    public static class Threading {
        private int bossThreads = 1;
        private int workerThreads = 0;
        private int businessThreads = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);

        public int getBossThreads() {
            return bossThreads;
        }

        public void setBossThreads(int bossThreads) {
            this.bossThreads = bossThreads;
        }

        public int getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
        }

        public int getBusinessThreads() {
            return businessThreads;
        }

        public void setBusinessThreads(int businessThreads) {
            this.businessThreads = businessThreads;
        }
    }

    public static class Tls {
        private boolean enabled;
        private TlsMode mode = TlsMode.PEM;
        private String certChainFile = "";
        private String privateKeyFile = "";
        private String privateKeyPassword = "";
        private String keyStoreFile = "";
        private String keyStorePassword = "";
        private String keyStoreType = "PKCS12";
        private List<String> protocols = new ArrayList<>(List.of("TLSv1.3", "TLSv1.2"));
        private int reloadIntervalSeconds = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public TlsMode getMode() {
            return mode;
        }

        public void setMode(TlsMode mode) {
            this.mode = mode;
        }

        public String getCertChainFile() {
            return certChainFile;
        }

        public void setCertChainFile(String certChainFile) {
            this.certChainFile = certChainFile;
        }

        public String getPrivateKeyFile() {
            return privateKeyFile;
        }

        public void setPrivateKeyFile(String privateKeyFile) {
            this.privateKeyFile = privateKeyFile;
        }

        public String getPrivateKeyPassword() {
            return privateKeyPassword;
        }

        public void setPrivateKeyPassword(String privateKeyPassword) {
            this.privateKeyPassword = privateKeyPassword;
        }

        public String getKeyStoreFile() {
            return keyStoreFile;
        }

        public void setKeyStoreFile(String keyStoreFile) {
            this.keyStoreFile = keyStoreFile;
        }

        public String getKeyStorePassword() {
            return keyStorePassword;
        }

        public void setKeyStorePassword(String keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
        }

        public String getKeyStoreType() {
            return keyStoreType;
        }

        public void setKeyStoreType(String keyStoreType) {
            this.keyStoreType = keyStoreType;
        }

        public List<String> getProtocols() {
            return protocols;
        }

        public void setProtocols(List<String> protocols) {
            this.protocols = protocols;
        }

        public int getReloadIntervalSeconds() {
            return reloadIntervalSeconds;
        }

        public void setReloadIntervalSeconds(int reloadIntervalSeconds) {
            this.reloadIntervalSeconds = reloadIntervalSeconds;
        }
    }

    public static class Jsp {
        private String scratchDir = "work/jsp";
        private boolean developmentMode = true;
        private boolean precompile = false;
        private int reloadIntervalSeconds = 5;
        private String pageEncoding = "UTF-8";
        private int bufferSize = 8192;
        private int maxLoadedJsps = 1000;

        public String getScratchDir() { return scratchDir; }
        public void setScratchDir(String scratchDir) { this.scratchDir = scratchDir; }
        public boolean isDevelopmentMode() { return developmentMode; }
        public void setDevelopmentMode(boolean developmentMode) { this.developmentMode = developmentMode; }
        public boolean isPrecompile() { return precompile; }
        public void setPrecompile(boolean precompile) { this.precompile = precompile; }
        public int getReloadIntervalSeconds() { return reloadIntervalSeconds; }
        public void setReloadIntervalSeconds(int reloadIntervalSeconds) { this.reloadIntervalSeconds = reloadIntervalSeconds; }
        public String getPageEncoding() { return pageEncoding; }
        public void setPageEncoding(String pageEncoding) { this.pageEncoding = pageEncoding; }
        public int getBufferSize() { return bufferSize; }
        public void setBufferSize(int bufferSize) { this.bufferSize = bufferSize; }
        public int getMaxLoadedJsps() { return maxLoadedJsps; }
        public void setMaxLoadedJsps(int maxLoadedJsps) { this.maxLoadedJsps = maxLoadedJsps; }
    }

    public static class ServletEngine {
        private String mappingStrategy = "TOMCAT_COMPAT";

        public String getMappingStrategy() {
            return mappingStrategy;
        }

        public void setMappingStrategy(String mappingStrategy) {
            this.mappingStrategy = mappingStrategy;
        }

        public void validate() {
            String normalized = mappingStrategy == null ? "TOMCAT_COMPAT" : mappingStrategy.trim().toUpperCase();
            if (!List.of("VELO", "TOMCAT_COMPAT").contains(normalized)) {
                throw new IllegalArgumentException("server.servlet.mappingStrategy must be VELO or TOMCAT_COMPAT");
            }
            mappingStrategy = normalized;
        }
    }

    public static class Compression {
        private boolean enabled = false;
        private int minResponseSizeBytes = 1024;
        private int compressionLevel = 6;
        private List<String> mimeTypes = new ArrayList<>(List.of(
                "text/html", "text/plain", "text/css",
                "application/javascript", "application/json"));

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMinResponseSizeBytes() { return minResponseSizeBytes; }
        public void setMinResponseSizeBytes(int minResponseSizeBytes) { this.minResponseSizeBytes = minResponseSizeBytes; }
        public int getCompressionLevel() { return compressionLevel; }
        public void setCompressionLevel(int compressionLevel) { this.compressionLevel = compressionLevel; }
        public List<String> getMimeTypes() { return mimeTypes; }
        public void setMimeTypes(List<String> mimeTypes) { this.mimeTypes = mimeTypes; }
    }

    public static class Session {
        private int timeoutSeconds = 1800;
        private int purgeIntervalSeconds = 60;
        private SessionCookie cookie = new SessionCookie();

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getPurgeIntervalSeconds() { return purgeIntervalSeconds; }
        public void setPurgeIntervalSeconds(int purgeIntervalSeconds) { this.purgeIntervalSeconds = purgeIntervalSeconds; }
        public SessionCookie getCookie() { return cookie; }
        public void setCookie(SessionCookie cookie) { this.cookie = cookie; }

        public void validate() {
            if (timeoutSeconds < 0) {
                throw new IllegalArgumentException("server.session.timeoutSeconds must be >= 0");
            }
            if (purgeIntervalSeconds <= 0) {
                throw new IllegalArgumentException("server.session.purgeIntervalSeconds must be positive");
            }
            if (cookie == null) {
                cookie = new SessionCookie();
            }
            cookie.validate();
        }
    }

    public static class SessionCookie {
        private String name = "JSESSIONID";
        private String path = "";
        private boolean httpOnly = true;
        private String secureMode = "AUTO";
        private String sameSite = "";
        private int maxAgeSeconds = -1;
        private String domain = "";

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public boolean isHttpOnly() { return httpOnly; }
        public void setHttpOnly(boolean httpOnly) { this.httpOnly = httpOnly; }
        public String getSecureMode() { return secureMode; }
        public void setSecureMode(String secureMode) { this.secureMode = secureMode; }
        public String getSameSite() { return sameSite; }
        public void setSameSite(String sameSite) { this.sameSite = sameSite; }
        public int getMaxAgeSeconds() { return maxAgeSeconds; }
        public void setMaxAgeSeconds(int maxAgeSeconds) { this.maxAgeSeconds = maxAgeSeconds; }
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }

        public void validate() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("server.session.cookie.name is required");
            }
            if (maxAgeSeconds < -1) {
                throw new IllegalArgumentException("server.session.cookie.maxAgeSeconds must be >= -1");
            }
            String normalizedSecureMode = secureMode == null ? "AUTO" : secureMode.trim().toUpperCase();
            if (!List.of("AUTO", "ALWAYS", "NEVER").contains(normalizedSecureMode)) {
                throw new IllegalArgumentException("server.session.cookie.secureMode must be AUTO, ALWAYS, or NEVER");
            }
            secureMode = normalizedSecureMode;
            if (sameSite != null && !sameSite.isBlank()) {
                String normalizedSameSite = switch (sameSite.trim().toLowerCase()) {
                    case "strict" -> "Strict";
                    case "lax" -> "Lax";
                    case "none" -> "None";
                    default -> null;
                };
                if (normalizedSameSite == null) {
                    throw new IllegalArgumentException("server.session.cookie.sameSite must be Strict, Lax, or None");
                }
                sameSite = normalizedSameSite;
            }
        }
    }

    public static class Deploy {
        private String directory = "deploy";
        private boolean hotDeploy = false;
        private int scanIntervalSeconds = 5;

        public String getDirectory() { return directory; }
        public void setDirectory(String directory) { this.directory = directory; }
        public boolean isHotDeploy() { return hotDeploy; }
        public void setHotDeploy(boolean hotDeploy) { this.hotDeploy = hotDeploy; }
        public int getScanIntervalSeconds() { return scanIntervalSeconds; }
        public void setScanIntervalSeconds(int scanIntervalSeconds) { this.scanIntervalSeconds = scanIntervalSeconds; }
    }

    public static class WebAdmin {
        private boolean enabled = true;
        private String contextPath = "/admin";
        private boolean apiDocsEnabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getContextPath() { return contextPath; }
        public void setContextPath(String contextPath) { this.contextPath = contextPath; }
        public boolean isApiDocsEnabled() { return apiDocsEnabled; }
        public void setApiDocsEnabled(boolean apiDocsEnabled) { this.apiDocsEnabled = apiDocsEnabled; }
    }

    public static class AiPlatform {
        private boolean enabled = true;
        private String mode = "PLATFORM";
        private Console console = new Console();
        private Serving serving = new Serving();
        private Platform platform = new Platform();
        private Differentiation differentiation = new Differentiation();
        private Advanced advanced = new Advanced();
        private Commercialization commercialization = new Commercialization();
        private Roadmap roadmap = new Roadmap();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public Console getConsole() { return console; }
        public void setConsole(Console console) { this.console = console; }
        public Serving getServing() { return serving; }
        public void setServing(Serving serving) { this.serving = serving; }
        public Platform getPlatform() { return platform; }
        public void setPlatform(Platform platform) { this.platform = platform; }
        public Differentiation getDifferentiation() { return differentiation; }
        public void setDifferentiation(Differentiation differentiation) { this.differentiation = differentiation; }
        public Advanced getAdvanced() { return advanced; }
        public void setAdvanced(Advanced advanced) { this.advanced = advanced; }
        public Commercialization getCommercialization() { return commercialization; }
        public void setCommercialization(Commercialization commercialization) { this.commercialization = commercialization; }
        public Roadmap getRoadmap() { return roadmap; }
        public void setRoadmap(Roadmap roadmap) { this.roadmap = roadmap; }

        public void validate() {
            String normalizedMode = mode == null ? "PLATFORM" : mode.trim().toUpperCase();
            if (!List.of("SERVING", "PLATFORM", "SAAS").contains(normalizedMode)) {
                throw new IllegalArgumentException("server.aiPlatform.mode must be SERVING, PLATFORM, or SAAS");
            }
            mode = normalizedMode;
            if (console == null) {
                console = new Console();
            }
            console.validate();
            if (serving == null) {
                serving = new Serving();
            }
            serving.validate();
            if (platform == null) {
                platform = new Platform();
            }
            platform.validate();
            if (differentiation == null) {
                differentiation = new Differentiation();
            }
            differentiation.validate();
            if (advanced == null) {
                advanced = new Advanced();
            }
            advanced.validate();
            if (commercialization == null) {
                commercialization = new Commercialization();
            }
            commercialization.validate();
            if (roadmap == null) {
                roadmap = new Roadmap();
            }
            roadmap.validate();
        }
    }

    public static class Console {
        private boolean enabled = true;
        private String contextPath = "/ai-platform";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getContextPath() { return contextPath; }
        public void setContextPath(String contextPath) { this.contextPath = contextPath; }

        public void validate() {
            if (contextPath == null || contextPath.isBlank() || !contextPath.startsWith("/")) {
                throw new IllegalArgumentException("server.aiPlatform.console.contextPath must start with /");
            }
        }
    }

    public static class Serving {
        private boolean modelRouterEnabled = true;
        private boolean abTestingEnabled = true;
        private boolean autoModelSelectionEnabled = true;
        private boolean ensembleServingEnabled = false;
        private boolean edgeAiEnabled = false;
        private String defaultStrategy = "BALANCED";
        private int routerTimeoutMillis = 1500;
        private int targetP99LatencyMs = 1200;
        private List<ModelProfile> models = new ArrayList<>(List.of(
                new ModelProfile("llm-general", "LLM", "builtin", "v1", "standard", 650, 86, true, true),
                new ModelProfile("vision-edge", "CV", "edge", "v1", "low-latency", 180, 79, false, true),
                new ModelProfile("reco-personalization", "RECOMMENDER", "builtin", "v1", "balanced", 95, 81, false, true)
        ));
        private List<RoutePolicy> routePolicies = new ArrayList<>(List.of(
                new RoutePolicy("chat-router", "CHAT", "llm-general", 100),
                new RoutePolicy("vision-router", "VISION", "vision-edge", 90),
                new RoutePolicy("recommendation-router", "RECOMMENDATION", "reco-personalization", 80),
                new RoutePolicy("fallback-router", "DEFAULT", "llm-general", 10)
        ));

        public boolean isModelRouterEnabled() { return modelRouterEnabled; }
        public void setModelRouterEnabled(boolean modelRouterEnabled) { this.modelRouterEnabled = modelRouterEnabled; }
        public boolean isAbTestingEnabled() { return abTestingEnabled; }
        public void setAbTestingEnabled(boolean abTestingEnabled) { this.abTestingEnabled = abTestingEnabled; }
        public boolean isAutoModelSelectionEnabled() { return autoModelSelectionEnabled; }
        public void setAutoModelSelectionEnabled(boolean autoModelSelectionEnabled) { this.autoModelSelectionEnabled = autoModelSelectionEnabled; }
        public boolean isEnsembleServingEnabled() { return ensembleServingEnabled; }
        public void setEnsembleServingEnabled(boolean ensembleServingEnabled) { this.ensembleServingEnabled = ensembleServingEnabled; }
        public boolean isEdgeAiEnabled() { return edgeAiEnabled; }
        public void setEdgeAiEnabled(boolean edgeAiEnabled) { this.edgeAiEnabled = edgeAiEnabled; }
        public String getDefaultStrategy() { return defaultStrategy; }
        public void setDefaultStrategy(String defaultStrategy) { this.defaultStrategy = defaultStrategy; }
        public int getRouterTimeoutMillis() { return routerTimeoutMillis; }
        public void setRouterTimeoutMillis(int routerTimeoutMillis) { this.routerTimeoutMillis = routerTimeoutMillis; }
        public int getTargetP99LatencyMs() { return targetP99LatencyMs; }
        public void setTargetP99LatencyMs(int targetP99LatencyMs) { this.targetP99LatencyMs = targetP99LatencyMs; }
        public List<ModelProfile> getModels() { return models; }
        public void setModels(List<ModelProfile> models) { this.models = models; }
        public List<RoutePolicy> getRoutePolicies() { return routePolicies; }
        public void setRoutePolicies(List<RoutePolicy> routePolicies) { this.routePolicies = routePolicies; }

        public void validate() {
            String normalizedStrategy = defaultStrategy == null ? "BALANCED" : defaultStrategy.trim().toUpperCase();
            if (!List.of("LATENCY_FIRST", "ACCURACY_FIRST", "COST_FIRST", "BALANCED").contains(normalizedStrategy)) {
                throw new IllegalArgumentException("server.aiPlatform.serving.defaultStrategy must be LATENCY_FIRST, ACCURACY_FIRST, COST_FIRST, or BALANCED");
            }
            defaultStrategy = normalizedStrategy;
            if (routerTimeoutMillis <= 0) {
                throw new IllegalArgumentException("server.aiPlatform.serving.routerTimeoutMillis must be positive");
            }
            if (targetP99LatencyMs <= 0) {
                throw new IllegalArgumentException("server.aiPlatform.serving.targetP99LatencyMs must be positive");
            }
            if (models == null || models.isEmpty()) {
                throw new IllegalArgumentException("server.aiPlatform.serving.models must not be empty");
            }
            for (ModelProfile model : models) {
                model.validate();
            }
            if (routePolicies == null || routePolicies.isEmpty()) {
                throw new IllegalArgumentException("server.aiPlatform.serving.routePolicies must not be empty");
            }
            for (RoutePolicy routePolicy : routePolicies) {
                routePolicy.validate();
            }
        }
    }

    public static class ModelProfile {
        private String name;
        private String category = "LLM";
        private String provider = "builtin";
        private String version = "v1";
        private String latencyTier = "balanced";
        private int latencyMs = 250;
        private int accuracyScore = 75;
        private boolean defaultSelected;
        private boolean enabled = true;

        public ModelProfile() {
        }

        public ModelProfile(String name, String category, String provider, String version,
                            String latencyTier, int latencyMs, int accuracyScore,
                            boolean defaultSelected, boolean enabled) {
            this.name = name;
            this.category = category;
            this.provider = provider;
            this.version = version;
            this.latencyTier = latencyTier;
            this.latencyMs = latencyMs;
            this.accuracyScore = accuracyScore;
            this.defaultSelected = defaultSelected;
            this.enabled = enabled;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getLatencyTier() { return latencyTier; }
        public void setLatencyTier(String latencyTier) { this.latencyTier = latencyTier; }
        public int getLatencyMs() { return latencyMs; }
        public void setLatencyMs(int latencyMs) { this.latencyMs = latencyMs; }
        public int getAccuracyScore() { return accuracyScore; }
        public void setAccuracyScore(int accuracyScore) { this.accuracyScore = accuracyScore; }
        public boolean isDefaultSelected() { return defaultSelected; }
        public void setDefaultSelected(boolean defaultSelected) { this.defaultSelected = defaultSelected; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public void validate() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("server.aiPlatform.serving.models.name is required");
            }
            if (latencyMs <= 0) {
                throw new IllegalArgumentException("server.aiPlatform.serving.models.latencyMs must be positive");
            }
            if (accuracyScore < 0 || accuracyScore > 100) {
                throw new IllegalArgumentException("server.aiPlatform.serving.models.accuracyScore must be between 0 and 100");
            }
        }
    }

    public static class RoutePolicy {
        private String name;
        private String requestType = "DEFAULT";
        private String targetModel = "llm-general";
        private int priority = 10;

        public RoutePolicy() {
        }

        public RoutePolicy(String name, String requestType, String targetModel, int priority) {
            this.name = name;
            this.requestType = requestType;
            this.targetModel = targetModel;
            this.priority = priority;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRequestType() { return requestType; }
        public void setRequestType(String requestType) { this.requestType = requestType; }
        public String getTargetModel() { return targetModel; }
        public void setTargetModel(String targetModel) { this.targetModel = targetModel; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }

        public void validate() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("server.aiPlatform.serving.routePolicies.name is required");
            }
            if (targetModel == null || targetModel.isBlank()) {
                throw new IllegalArgumentException("server.aiPlatform.serving.routePolicies.targetModel is required");
            }
            if (priority < 0) {
                throw new IllegalArgumentException("server.aiPlatform.serving.routePolicies.priority must be >= 0");
            }
        }
    }

    public static class Platform {
        private boolean modelRegistrationEnabled = true;
        private boolean autoApiGenerationEnabled = true;
        private boolean versionManagementEnabled = true;
        private boolean billingEnabled = false;
        private boolean developerPortalEnabled = true;
        private boolean multiTenantEnabled = false;
        private int defaultTenantRateLimitPerMinute = 120;
        private long defaultTenantTokenQuota = 250_000L;
        private String apiKeyHeader = "X-AI-API-Key";
        private String versioningStrategy = "CANARY";

        public boolean isModelRegistrationEnabled() { return modelRegistrationEnabled; }
        public void setModelRegistrationEnabled(boolean modelRegistrationEnabled) { this.modelRegistrationEnabled = modelRegistrationEnabled; }
        public boolean isAutoApiGenerationEnabled() { return autoApiGenerationEnabled; }
        public void setAutoApiGenerationEnabled(boolean autoApiGenerationEnabled) { this.autoApiGenerationEnabled = autoApiGenerationEnabled; }
        public boolean isVersionManagementEnabled() { return versionManagementEnabled; }
        public void setVersionManagementEnabled(boolean versionManagementEnabled) { this.versionManagementEnabled = versionManagementEnabled; }
        public boolean isBillingEnabled() { return billingEnabled; }
        public void setBillingEnabled(boolean billingEnabled) { this.billingEnabled = billingEnabled; }
        public boolean isDeveloperPortalEnabled() { return developerPortalEnabled; }
        public void setDeveloperPortalEnabled(boolean developerPortalEnabled) { this.developerPortalEnabled = developerPortalEnabled; }
        public boolean isMultiTenantEnabled() { return multiTenantEnabled; }
        public void setMultiTenantEnabled(boolean multiTenantEnabled) { this.multiTenantEnabled = multiTenantEnabled; }
        public int getDefaultTenantRateLimitPerMinute() { return defaultTenantRateLimitPerMinute; }
        public void setDefaultTenantRateLimitPerMinute(int defaultTenantRateLimitPerMinute) { this.defaultTenantRateLimitPerMinute = defaultTenantRateLimitPerMinute; }
        public long getDefaultTenantTokenQuota() { return defaultTenantTokenQuota; }
        public void setDefaultTenantTokenQuota(long defaultTenantTokenQuota) { this.defaultTenantTokenQuota = defaultTenantTokenQuota; }
        public String getApiKeyHeader() { return apiKeyHeader; }
        public void setApiKeyHeader(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }
        public String getVersioningStrategy() { return versioningStrategy; }
        public void setVersioningStrategy(String versioningStrategy) { this.versioningStrategy = versioningStrategy; }

        public void validate() {
            String normalizedStrategy = versioningStrategy == null ? "CANARY" : versioningStrategy.trim().toUpperCase();
            if (!List.of("CANARY", "BLUE_GREEN", "ROLLING").contains(normalizedStrategy)) {
                throw new IllegalArgumentException("server.aiPlatform.platform.versioningStrategy must be CANARY, BLUE_GREEN, or ROLLING");
            }
            versioningStrategy = normalizedStrategy;
            if (defaultTenantRateLimitPerMinute <= 0) {
                throw new IllegalArgumentException("server.aiPlatform.platform.defaultTenantRateLimitPerMinute must be positive");
            }
            if (defaultTenantTokenQuota <= 0) {
                throw new IllegalArgumentException("server.aiPlatform.platform.defaultTenantTokenQuota must be positive");
            }
            if (apiKeyHeader == null || apiKeyHeader.isBlank()) {
                throw new IllegalArgumentException("server.aiPlatform.platform.apiKeyHeader is required");
            }
            apiKeyHeader = apiKeyHeader.trim();
        }
    }

    public static class Differentiation {
        private boolean aiOptimizedWasEnabled = true;
        private boolean requestRoutingEnabled = true;
        private boolean streamingResponseEnabled = true;
        private boolean pluginFrameworkEnabled = true;
        private String runtimeEngine = "NETTY_ASYNC";

        public boolean isAiOptimizedWasEnabled() { return aiOptimizedWasEnabled; }
        public void setAiOptimizedWasEnabled(boolean aiOptimizedWasEnabled) { this.aiOptimizedWasEnabled = aiOptimizedWasEnabled; }
        public boolean isRequestRoutingEnabled() { return requestRoutingEnabled; }
        public void setRequestRoutingEnabled(boolean requestRoutingEnabled) { this.requestRoutingEnabled = requestRoutingEnabled; }
        public boolean isStreamingResponseEnabled() { return streamingResponseEnabled; }
        public void setStreamingResponseEnabled(boolean streamingResponseEnabled) { this.streamingResponseEnabled = streamingResponseEnabled; }
        public boolean isPluginFrameworkEnabled() { return pluginFrameworkEnabled; }
        public void setPluginFrameworkEnabled(boolean pluginFrameworkEnabled) { this.pluginFrameworkEnabled = pluginFrameworkEnabled; }
        public String getRuntimeEngine() { return runtimeEngine; }
        public void setRuntimeEngine(String runtimeEngine) { this.runtimeEngine = runtimeEngine; }

        public void validate() {
            String normalizedEngine = runtimeEngine == null ? "NETTY_ASYNC" : runtimeEngine.trim().toUpperCase();
            if (!List.of("NETTY_ASYNC", "EDGE_BRIDGE", "HYBRID").contains(normalizedEngine)) {
                throw new IllegalArgumentException("server.aiPlatform.differentiation.runtimeEngine must be NETTY_ASYNC, EDGE_BRIDGE, or HYBRID");
            }
            runtimeEngine = normalizedEngine;
        }
    }

    public static class Advanced {
        private boolean promptRoutingEnabled = true;
        private String promptRoutingMode = "HYBRID";
        private boolean contextCacheEnabled = true;
        private int contextCacheTtlSeconds = 300;
        private boolean aiGatewayEnabled = true;
        // fineTuningApiEnabled removed (feature removed in v0.5.16)
        private boolean observabilityEnabled = true;
        private boolean gpuSchedulingEnabled = false;

        public boolean isPromptRoutingEnabled() { return promptRoutingEnabled; }
        public void setPromptRoutingEnabled(boolean promptRoutingEnabled) { this.promptRoutingEnabled = promptRoutingEnabled; }
        public String getPromptRoutingMode() { return promptRoutingMode; }
        public void setPromptRoutingMode(String promptRoutingMode) { this.promptRoutingMode = promptRoutingMode; }
        public boolean isContextCacheEnabled() { return contextCacheEnabled; }
        public void setContextCacheEnabled(boolean contextCacheEnabled) { this.contextCacheEnabled = contextCacheEnabled; }
        public int getContextCacheTtlSeconds() { return contextCacheTtlSeconds; }
        public void setContextCacheTtlSeconds(int contextCacheTtlSeconds) { this.contextCacheTtlSeconds = contextCacheTtlSeconds; }
        public boolean isAiGatewayEnabled() { return aiGatewayEnabled; }
        public void setAiGatewayEnabled(boolean aiGatewayEnabled) { this.aiGatewayEnabled = aiGatewayEnabled; }
        /** @deprecated Fine-tuning removed in v0.5.16 */
        @Deprecated public boolean isFineTuningApiEnabled() { return false; }
        /** @deprecated Fine-tuning removed in v0.5.16 */
        @Deprecated public void setFineTuningApiEnabled(boolean ignored) { }
        public boolean isObservabilityEnabled() { return observabilityEnabled; }
        public void setObservabilityEnabled(boolean observabilityEnabled) { this.observabilityEnabled = observabilityEnabled; }
        public boolean isGpuSchedulingEnabled() { return gpuSchedulingEnabled; }
        public void setGpuSchedulingEnabled(boolean gpuSchedulingEnabled) { this.gpuSchedulingEnabled = gpuSchedulingEnabled; }

        public void validate() {
            String normalizedMode = promptRoutingMode == null ? "HYBRID" : promptRoutingMode.trim().toUpperCase();
            if (!List.of("KEYWORD", "CLASSIFIER", "HYBRID").contains(normalizedMode)) {
                throw new IllegalArgumentException("server.aiPlatform.advanced.promptRoutingMode must be KEYWORD, CLASSIFIER, or HYBRID");
            }
            promptRoutingMode = normalizedMode;
            if (contextCacheTtlSeconds <= 0) {
                throw new IllegalArgumentException("server.aiPlatform.advanced.contextCacheTtlSeconds must be positive");
            }
        }
    }

    public static class Commercialization {
        private String coreDirection = "WAS_TO_AI_PLATFORM";
        private List<String> differentiators = new ArrayList<>(List.of(
                "MODEL_ROUTING",
                "LOW_LATENCY",
                "CUSTOMIZATION"
        ));
        private List<String> revenueStreams = new ArrayList<>(List.of(
                "API_BILLING",
                "SAAS"
        ));

        public String getCoreDirection() { return coreDirection; }
        public void setCoreDirection(String coreDirection) { this.coreDirection = coreDirection; }
        public List<String> getDifferentiators() { return differentiators; }
        public void setDifferentiators(List<String> differentiators) { this.differentiators = differentiators; }
        public List<String> getRevenueStreams() { return revenueStreams; }
        public void setRevenueStreams(List<String> revenueStreams) { this.revenueStreams = revenueStreams; }

        public void validate() {
            if (coreDirection == null || coreDirection.isBlank()) {
                throw new IllegalArgumentException("server.aiPlatform.commercialization.coreDirection is required");
            }
            if (differentiators == null) {
                differentiators = new ArrayList<>();
            }
            if (revenueStreams == null) {
                revenueStreams = new ArrayList<>();
            }
        }
    }

    public static class Roadmap {
        private int currentStage = 2;
        private List<RoadmapStage> stages = new ArrayList<>(List.of(
                new RoadmapStage(1, "Basic Serving", List.of("Single model API")),
                new RoadmapStage(2, "Expansion", List.of("Multi-model routing", "Context cache")),
                new RoadmapStage(3, "Platformization", List.of("Model registry", "Version management")),
                new RoadmapStage(4, "Optimization", List.of("GPU scheduling")),
                new RoadmapStage(5, "Commercialization", List.of("Billing", "Multi-tenant SaaS"))
        ));

        public int getCurrentStage() { return currentStage; }
        public void setCurrentStage(int currentStage) { this.currentStage = currentStage; }
        public List<RoadmapStage> getStages() { return stages; }
        public void setStages(List<RoadmapStage> stages) { this.stages = stages; }

        public void validate() {
            if (currentStage < 1 || currentStage > 5) {
                throw new IllegalArgumentException("server.aiPlatform.roadmap.currentStage must be between 1 and 5");
            }
            if (stages == null || stages.isEmpty()) {
                throw new IllegalArgumentException("server.aiPlatform.roadmap.stages must not be empty");
            }
            for (RoadmapStage stage : stages) {
                stage.validate();
            }
        }
    }

    public static class RoadmapStage {
        private int stage;
        private String goal;
        private List<String> capabilities = new ArrayList<>();

        public RoadmapStage() {
        }

        public RoadmapStage(int stage, String goal, List<String> capabilities) {
            this.stage = stage;
            this.goal = goal;
            this.capabilities = new ArrayList<>(capabilities);
        }

        public int getStage() { return stage; }
        public void setStage(int stage) { this.stage = stage; }
        public String getGoal() { return goal; }
        public void setGoal(String goal) { this.goal = goal; }
        public List<String> getCapabilities() { return capabilities; }
        public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities; }

        public void validate() {
            if (stage < 1) {
                throw new IllegalArgumentException("server.aiPlatform.roadmap.stages.stage must be >= 1");
            }
            if (goal == null || goal.isBlank()) {
                throw new IllegalArgumentException("server.aiPlatform.roadmap.stages.goal is required");
            }
            if (capabilities == null) {
                capabilities = new ArrayList<>();
            }
        }
    }

    public static class TcpListenerConfig {
        private String name = "default";
        private String host = "0.0.0.0";
        private int port = 9090;
        private FramingType framing = FramingType.LENGTH_FIELD;
        private FramingConfig framingConfig = new FramingConfig();
        private boolean tlsEnabled = false;
        private String tlsCertChainFile = "";
        private String tlsPrivateKeyFile = "";
        private int idleTimeoutSeconds = 300;
        private int readTimeoutSeconds = 30;
        private int writeTimeoutSeconds = 30;
        private int maxConnections = 10000;
        private int perIpRateLimit = 100;
        private int workerThreads = 0;
        private int businessThreads = 16;
        private List<String> allowedCidrs = new ArrayList<>();
        private List<String> deniedCidrs = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public FramingType getFraming() { return framing; }
        public void setFraming(FramingType framing) { this.framing = framing; }
        public FramingConfig getFramingConfig() { return framingConfig; }
        public void setFramingConfig(FramingConfig framingConfig) { this.framingConfig = framingConfig; }
        public boolean isTlsEnabled() { return tlsEnabled; }
        public void setTlsEnabled(boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }
        public String getTlsCertChainFile() { return tlsCertChainFile; }
        public void setTlsCertChainFile(String tlsCertChainFile) { this.tlsCertChainFile = tlsCertChainFile; }
        public String getTlsPrivateKeyFile() { return tlsPrivateKeyFile; }
        public void setTlsPrivateKeyFile(String tlsPrivateKeyFile) { this.tlsPrivateKeyFile = tlsPrivateKeyFile; }
        public int getIdleTimeoutSeconds() { return idleTimeoutSeconds; }
        public void setIdleTimeoutSeconds(int idleTimeoutSeconds) { this.idleTimeoutSeconds = idleTimeoutSeconds; }
        public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
        public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
        public int getWriteTimeoutSeconds() { return writeTimeoutSeconds; }
        public void setWriteTimeoutSeconds(int writeTimeoutSeconds) { this.writeTimeoutSeconds = writeTimeoutSeconds; }
        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
        public int getPerIpRateLimit() { return perIpRateLimit; }
        public void setPerIpRateLimit(int perIpRateLimit) { this.perIpRateLimit = perIpRateLimit; }
        public int getWorkerThreads() { return workerThreads; }
        public void setWorkerThreads(int workerThreads) { this.workerThreads = workerThreads; }
        public int getBusinessThreads() { return businessThreads; }
        public void setBusinessThreads(int businessThreads) { this.businessThreads = businessThreads; }
        public List<String> getAllowedCidrs() { return allowedCidrs; }
        public void setAllowedCidrs(List<String> allowedCidrs) { this.allowedCidrs = allowedCidrs; }
        public List<String> getDeniedCidrs() { return deniedCidrs; }
        public void setDeniedCidrs(List<String> deniedCidrs) { this.deniedCidrs = deniedCidrs; }

        public void validate() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("tcpListener.name is required");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("tcpListener.port must be between 1 and 65535");
            }
            if (maxConnections <= 0) {
                throw new IllegalArgumentException("tcpListener.maxConnections must be positive");
            }
        }
    }

    public enum FramingType {
        RAW, LINE, FIXED_LENGTH, DELIMITER, LENGTH_FIELD
    }

    public static class FramingConfig {
        private int length = 1024;
        private String delimiter = "\n";
        private int lengthFieldOffset = 0;
        private int lengthFieldLength = 4;
        private int maxFrameLength = 1048576;

        public int getLength() { return length; }
        public void setLength(int length) { this.length = length; }
        public String getDelimiter() { return delimiter; }
        public void setDelimiter(String delimiter) { this.delimiter = delimiter; }
        public int getLengthFieldOffset() { return lengthFieldOffset; }
        public void setLengthFieldOffset(int lengthFieldOffset) { this.lengthFieldOffset = lengthFieldOffset; }
        public int getLengthFieldLength() { return lengthFieldLength; }
        public void setLengthFieldLength(int lengthFieldLength) { this.lengthFieldLength = lengthFieldLength; }
        public int getMaxFrameLength() { return maxFrameLength; }
        public void setMaxFrameLength(int maxFrameLength) { this.maxFrameLength = maxFrameLength; }
    }
}
