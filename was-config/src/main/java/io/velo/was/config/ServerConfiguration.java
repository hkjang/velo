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
        private Jsp jsp = new Jsp();
        private Compression compression = new Compression();
        private Session session = new Session();
        private Deploy deploy = new Deploy();
        private WebAdmin webAdmin = new WebAdmin();
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
            if (jsp == null) {
                jsp = new Jsp();
            }
            if (compression == null) {
                compression = new Compression();
            }
            if (session == null) {
                session = new Session();
            }
            if (deploy == null) {
                deploy = new Deploy();
            }
            if (webAdmin == null) {
                webAdmin = new WebAdmin();
            }
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

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getPurgeIntervalSeconds() { return purgeIntervalSeconds; }
        public void setPurgeIntervalSeconds(int purgeIntervalSeconds) { this.purgeIntervalSeconds = purgeIntervalSeconds; }
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
