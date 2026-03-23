package io.velo.was.bootstrap;

import io.velo.was.admin.client.AdminClient;
import io.velo.was.admin.client.LocalAdminClient;
import io.velo.was.aiplatform.AiPlatformApplication;
import io.velo.was.config.ServerConfiguration;
import io.velo.was.deploy.DeploymentRegistry;
import io.velo.was.deploy.HotDeployWatcher;
import io.velo.was.deploy.WarDeployer;
import io.velo.was.http.HttpHandlerRegistry;
import io.velo.was.http.HttpResponses;
import io.velo.was.http.SseHandlerRegistry;
import io.velo.was.observability.MetricsCollector;
import io.velo.was.jsp.JspServlet;
import io.velo.was.servlet.SessionCookieSettings;
import io.velo.was.servlet.SimpleServletApplication;
import io.velo.was.servlet.SimpleServletContainer;
import io.velo.was.servlet.ServletPathMapperFactory;
import io.velo.was.tcp.bootstrap.TcpListenerManager;
import io.velo.was.tcp.router.TcpMessageRouter;
import io.velo.was.transport.netty.NettyServer;
import io.velo.was.webadmin.WebAdminApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class VeloWasApplication {

    private static final Logger log = LoggerFactory.getLogger(VeloWasApplication.class);

    private VeloWasApplication() {
    }

    public static void main(String[] args) throws Exception {
        String configProperty = System.getProperty("velo.config");
        Path configPath = configProperty != null ? Path.of(configProperty)
                : args.length > 0 ? Path.of(args[0])
                : Path.of("conf", "server.yaml");
        ServerConfiguration configuration = ServerConfigurationLoader.load(configPath);
        ServerConfiguration.Session sessionConfig = configuration.getServer().getSession();
        ServerConfiguration.Deploy deployConfig = configuration.getServer().getDeploy();

        SimpleServletContainer servletContainer = new SimpleServletContainer(
                sessionConfig.getPurgeIntervalSeconds(),
                sessionConfig.getTimeoutSeconds(),
                toSessionCookieSettings(sessionConfig.getCookie()),
                ServletPathMapperFactory.fromStrategy(configuration.getServer().getServlet().getMappingStrategy()));
        log.info("Servlet mapping strategy: {}", configuration.getServer().getServlet().getMappingStrategy());

        // Deploy Web Admin application (before business apps for bootstrap priority)
        ServerConfiguration.WebAdmin webAdminConfig = configuration.getServer().getWebAdmin();
        if (webAdminConfig.isEnabled()) {
            SimpleServletApplication webAdminApp = WebAdminApplication.create(configuration);
            servletContainer.deploy(webAdminApp);
            log.info("Web Admin deployed at context path: {}", webAdminConfig.getContextPath());
        } else {
            log.info("Web Admin is disabled");
        }

        ServerConfiguration.AiPlatform aiPlatformConfig = configuration.getServer().getAiPlatform();
        if (aiPlatformConfig.isEnabled() && aiPlatformConfig.getConsole().isEnabled()) {
            SimpleServletApplication aiPlatformApp = AiPlatformApplication.create(configuration);
            servletContainer.deploy(aiPlatformApp);
            log.info("AI Platform console deployed at context path: {}",
                    aiPlatformConfig.getConsole().getContextPath());
        } else {
            log.info("AI Platform console is disabled");
        }

        // Register JSP servlet for *.jsp handling
        SimpleServletApplication.Builder sampleAppBuilder = SimpleServletApplication.builder("sample-app", "/app")
                .filter(new SampleTraceFilter())
                .servletContextListener(new SampleLifecycleListener())
                .servletRequestListener(new SampleLifecycleListener())
                .servlet("/hello", new SampleHelloServlet())
                .servlet("*.jsp", new JspServlet())
                .initParameter("io.velo.was.jsp.scratchDir",
                        configuration.getServer().getJsp().getScratchDir());

        servletContainer.deploy(sampleAppBuilder.build());

        // Deploy WARs from deploy directory
        Path deployDir = Path.of(deployConfig.getDirectory());
        WarDeployer warDeployer = new WarDeployer(deployDir.resolve(".work"));
        DeploymentRegistry deploymentRegistry = new DeploymentRegistry(warDeployer, servletContainer);
        AdminWarUploadService.UploadDeployer uploadDeployer =
                (warPath, contextPath) -> deployApplication(warDeployer, deploymentRegistry, servletContainer,
                        configuration, warPath, contextPath);
        AdminWarUploadService warUploadService =
                new AdminWarUploadService(configuration, servletContainer.sessionStore(), uploadDeployer);

        // Wire deploy handler so LocalAdminClient can delegate deploy operations
        LocalAdminClient.setDeployHandler(new LocalAdminClient.DeployHandler() {
            @Override
            public void deploy(String path, String contextPath) {
                try {
                    deployApplication(warDeployer, deploymentRegistry, servletContainer, configuration,
                            Path.of(path), contextPath);
                } catch (Exception e) {
                    throw new UnsupportedOperationException("Deploy failed: " + e.getMessage());
                }
            }

            @Override
            public void undeploy(String appName) {
                deploymentRegistry.undeploy(appName);
            }

            @Override
            public void redeploy(String appName) {
                // Find WAR path from deploy directory
                Path warFile = Path.of(deployConfig.getDirectory(), appName + ".war");
                if (Files.exists(warFile)) {
                    deploymentRegistry.redeploy(warFile);
                } else {
                    throw new UnsupportedOperationException("WAR file not found for redeploy: " + warFile);
                }
            }

            @Override
            public void startApplication(String appName) {
                throw new UnsupportedOperationException("Start application not supported. Use redeploy.");
            }

            @Override
            public void stopApplication(String appName) {
                deploymentRegistry.undeploy(appName);
            }

            @Override
            public java.util.List<AdminClient.AppSummary> listApplications() {
                java.util.List<AdminClient.AppSummary> apps = new java.util.ArrayList<>();
                for (SimpleServletContainer.DeployedAppInfo info : servletContainer.listDeployedApplications()) {
                    apps.add(new AdminClient.AppSummary(info.name(), info.contextPath(), "RUNNING"));
                }
                return apps;
            }
        });
        log.info("Deploy handler wired for admin operations");

        if (Files.exists(deployDir)) {
            try (Stream<Path> warFiles = Files.list(deployDir)
                    .filter(p -> p.toString().endsWith(".war"))) {
                warFiles.forEach(deploymentRegistry::deploy);
            }
        }

        // Hot deploy watcher
        HotDeployWatcher hotDeployWatcher = null;
        if (deployConfig.isHotDeploy()) {
            hotDeployWatcher = new HotDeployWatcher(
                    deployDir, deploymentRegistry, deployConfig.getScanIntervalSeconds());
            hotDeployWatcher.start();
        }

        HttpHandlerRegistry registry = new HttpHandlerRegistry()
                .registerGet("/health", exchange -> {
                    if (!"GET".equals(exchange.request().method().name()) && !"HEAD".equals(exchange.request().method().name())) {
                        return HttpResponses.methodNotAllowed("Only GET and HEAD are supported for /health");
                    }
                    return HttpResponses.jsonOk("""
                            {"status":"UP","name":"%s","nodeId":"%s"}
                            """.formatted(configuration.getServer().getName(), configuration.getServer().getNodeId()).trim());
                })
                .registerGet("/metrics", exchange ->
                        HttpResponses.jsonOk(MetricsCollector.instance().snapshot().toJson()))
                .registerGet("/info", exchange -> {
                    if (!"GET".equals(exchange.request().method().name()) && !"HEAD".equals(exchange.request().method().name())) {
                        return HttpResponses.methodNotAllowed("Only GET and HEAD are supported for /info");
                    }
                    return HttpResponses.jsonOk("""
                            {"product":"velo-was","phase":"servlet-foundation","transport":"netty","servletCompatibility":"minimal"}
                            """.trim());
                })
                .fallback(servletContainer::handle);

        // ── MCP server ──────────────────────────────────────────────────────
        SseHandlerRegistry sseRegistry = new SseHandlerRegistry();
        if (aiPlatformConfig.isEnabled()) {
            io.velo.was.aiplatform.registry.AiModelRegistryService mcpRegistryService =
                    new io.velo.was.aiplatform.registry.AiModelRegistryService(configuration);
            io.velo.was.aiplatform.gateway.AiGatewayService mcpGatewayService =
                    new io.velo.was.aiplatform.gateway.AiGatewayService(configuration, mcpRegistryService);
            AdminClient mcpAdminClient = new LocalAdminClient(configuration);
            io.velo.was.mcp.McpApplication.InstallResult mcpResult =
                    io.velo.was.mcp.McpApplication.install(registry, sseRegistry, mcpRegistryService, mcpGatewayService,
                    mcpAdminClient, "velo-mcp",
                    configuration.getServer().getName() + "-" + configuration.getServer().getNodeId());

            // ── Wire App MCP Gateway for monitoring deployed app MCP traffic ──
            io.velo.was.mcp.gateway.McpAppGatewayService appMcpGateway =
                    new io.velo.was.mcp.gateway.McpAppGatewayService(mcpResult.auditLog());
            mcpResult.adminHandler().setAppGatewayService(appMcpGateway);

            // Install global filter provider to intercept MCP traffic from apps
            servletContainer.setGlobalFilterProvider((contextPath, appName) -> {
                // Skip built-in WAS apps (web admin, ai-platform, sample-app)
                if ("/admin".equals(contextPath) || "/ai-platform".equals(contextPath)
                        || "/app".equals(contextPath)) {
                    return java.util.List.of();
                }
                return java.util.List.of(
                        new io.velo.was.mcp.gateway.McpAppTrafficInterceptor(appMcpGateway, contextPath, appName));
            });

            log.info("MCP server installed at /ai-platform/mcp (admin at /ai-platform/mcp/admin/*) with app MCP gateway");
        }

        NettyServer server = new NettyServer(
                configuration.getServer(),
                registry,
                null, // WebSocketHandlerRegistry
                sseRegistry,
                () -> new AdminWarUploadChannelHandler(warUploadService, deployDir.resolve(".upload-staging")));

        // Start TCP listeners from configuration
        TcpListenerManager tcpManager = new TcpListenerManager();
        servletContainer.setServerAttribute("io.velo.was.TcpListenerManager", tcpManager);

        List<ServerConfiguration.TcpListenerConfig> tcpConfigs = configuration.getServer().getTcpListeners();
        for (ServerConfiguration.TcpListenerConfig tcpConfig : tcpConfigs) {
            TcpMessageRouter tcpRouter = new TcpMessageRouter()
                    .fallback((session, message, sender) -> {
                        log.debug("TCP echo: session={} type={} size={}",
                                session.sessionId(), message.messageType(), message.payloadLength());
                        sender.replyWithLengthHeader(message.payload());
                    });
            tcpManager.register(tcpConfig, tcpRouter);
        }

        final HotDeployWatcher watcher = hotDeployWatcher;
        final DeploymentRegistry depRegistry = deploymentRegistry;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (watcher != null) {
                watcher.close();
            }
            depRegistry.undeployAll();
            tcpManager.close();
            servletContainer.close();
            server.close();
        }, "velo-was-shutdown"));

        try {
            server.start();
            if (!tcpConfigs.isEmpty()) {
                tcpManager.startAll();
            }
            server.blockUntilShutdown();
        } catch (Exception exception) {
            log.error("Server startup failed", exception);
            if (watcher != null) {
                watcher.close();
            }
            tcpManager.close();
            servletContainer.close();
            server.close();
            throw exception;
        }
    }

    private static void deployApplication(WarDeployer warDeployer,
                                          DeploymentRegistry deploymentRegistry,
                                          SimpleServletContainer servletContainer,
                                          ServerConfiguration configuration,
                                          Path warPath,
                                          String contextPath) throws Exception {
        if (!Files.exists(warPath)) {
            throw new IllegalArgumentException("File not found: " + warPath);
        }
        if (contextPath != null && !contextPath.isBlank()) {
            WarDeployer.DeploymentResult result = warDeployer.deploy(warPath, contextPath);
            servletContainer.deploy(result.application());
            log.info("Deployed via admin: {} -> {}", warPath, contextPath);
            return;
        }
        deploymentRegistry.deploy(warPath);
        if (!configuration.getServer().getDeploy().isHotDeploy()) {
            log.info("Deployed via upload without hot deploy: {}", warPath);
        }
    }

    private static SessionCookieSettings toSessionCookieSettings(ServerConfiguration.SessionCookie cookie) {
        if (cookie == null) {
            return SessionCookieSettings.defaults();
        }
        return new SessionCookieSettings(
                cookie.getName(),
                cookie.getPath(),
                cookie.isHttpOnly(),
                SessionCookieSettings.SecureMode.from(cookie.getSecureMode()),
                cookie.getSameSite(),
                cookie.getMaxAgeSeconds(),
                cookie.getDomain());
    }
}
