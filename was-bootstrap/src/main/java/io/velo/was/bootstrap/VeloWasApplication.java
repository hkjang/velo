package io.velo.was.bootstrap;

import io.velo.was.config.ServerConfiguration;
import io.velo.was.deploy.DeploymentRegistry;
import io.velo.was.deploy.HotDeployWatcher;
import io.velo.was.deploy.WarDeployer;
import io.velo.was.http.HttpHandlerRegistry;
import io.velo.was.http.HttpResponses;
import io.velo.was.observability.MetricsCollector;
import io.velo.was.jsp.JspServlet;
import io.velo.was.servlet.SimpleServletApplication;
import io.velo.was.servlet.SimpleServletContainer;
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
        Path configPath = args.length > 0 ? Path.of(args[0]) : Path.of("conf", "server.yaml");
        ServerConfiguration configuration = ServerConfigurationLoader.load(configPath);
        ServerConfiguration.Session sessionConfig = configuration.getServer().getSession();
        ServerConfiguration.Deploy deployConfig = configuration.getServer().getDeploy();

        SimpleServletContainer servletContainer = new SimpleServletContainer(
                sessionConfig.getPurgeIntervalSeconds(),
                sessionConfig.getTimeoutSeconds());

        // Deploy Web Admin application (before business apps for bootstrap priority)
        ServerConfiguration.WebAdmin webAdminConfig = configuration.getServer().getWebAdmin();
        if (webAdminConfig.isEnabled()) {
            SimpleServletApplication webAdminApp = WebAdminApplication.create(configuration);
            servletContainer.deploy(webAdminApp);
            log.info("Web Admin deployed at context path: {}", webAdminConfig.getContextPath());
        } else {
            log.info("Web Admin is disabled");
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

        NettyServer server = new NettyServer(configuration.getServer(), registry);

        // Start TCP listeners from configuration
        TcpListenerManager tcpManager = new TcpListenerManager();
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
}
