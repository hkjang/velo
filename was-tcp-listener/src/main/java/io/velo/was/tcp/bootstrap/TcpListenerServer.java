package io.velo.was.tcp.bootstrap;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.IdleStateHandler;
import io.velo.was.config.ServerConfiguration.FramingConfig;
import io.velo.was.config.ServerConfiguration.FramingType;
import io.velo.was.config.ServerConfiguration.TcpListenerConfig;
import io.velo.was.tcp.codec.DefaultMessageCodec;
import io.velo.was.tcp.codec.FrameDecoderFactory;
import io.velo.was.tcp.codec.MessageCodec;
import io.velo.was.tcp.handler.TcpChannelHandler;
import io.velo.was.tcp.handler.TcpHandlerExecutor;
import io.velo.was.tcp.observability.TcpMetrics;
import io.velo.was.tcp.router.TcpMessageRouter;
import io.velo.was.tcp.security.TcpRateLimiter;
import io.velo.was.tcp.security.TcpSecurityHandler;
import io.velo.was.tcp.session.TcpSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.*;

/**
 * Represents a single TCP listener server instance bound to a specific port.
 * Each listener has its own EventLoopGroup for fault isolation from HTTP transport.
 */
public class TcpListenerServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TcpListenerServer.class);

    private final TcpListenerConfig config;
    private final TcpMessageRouter router;
    private final TcpSessionManager sessionManager;
    private final TcpMetrics metrics;
    private MessageCodec messageCodec;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ExecutorService businessExecutor;
    private Channel serverChannel;
    private volatile boolean running;

    public TcpListenerServer(TcpListenerConfig config, TcpMessageRouter router) {
        this.config = config;
        this.router = router;
        this.sessionManager = new TcpSessionManager();
        this.metrics = new TcpMetrics(config.getName());
    }

    /**
     * Sets a custom message codec. If not set, DefaultMessageCodec is used.
     */
    public TcpListenerServer messageCodec(MessageCodec codec) {
        this.messageCodec = codec;
        return this;
    }

    public void start() throws Exception {
        if (running) {
            log.warn("Listener {} already running on port {}", config.getName(), config.getPort());
            return;
        }

        int workerThreads = config.getWorkerThreads() > 0
                ? config.getWorkerThreads()
                : Runtime.getRuntime().availableProcessors();

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup(workerThreads);
        businessExecutor = new ThreadPoolExecutor(
                config.getBusinessThreads(), config.getBusinessThreads(),
                60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(10000),
                Thread.ofVirtual().name("tcp-biz-" + config.getName() + "-", 0).factory());

        TcpHandlerExecutor handlerExecutor = new TcpHandlerExecutor(businessExecutor, router);

        // Sharable handlers
        TcpSecurityHandler securityHandler = new TcpSecurityHandler(
                config.getAllowedCidrs(), config.getDeniedCidrs());
        TcpRateLimiter rateLimiter = new TcpRateLimiter(
                config.getMaxConnections(), config.getPerIpRateLimit());

        // Optional TLS
        SslContext sslContext = null;
        if (config.isTlsEnabled()) {
            File certFile = new File(config.getTlsCertChainFile());
            File keyFile = new File(config.getTlsPrivateKeyFile());
            sslContext = SslContextBuilder.forServer(certFile, keyFile).build();
        }
        final SslContext finalSslContext = sslContext;

        MessageCodec codec = messageCodec != null ? messageCodec : new DefaultMessageCodec();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 2048)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        // TLS
                        if (finalSslContext != null) {
                            pipeline.addLast("ssl", finalSslContext.newHandler(ch.alloc()));
                        }

                        // Security
                        pipeline.addLast("security", securityHandler);
                        pipeline.addLast("rateLimiter", rateLimiter);

                        // Idle timeout
                        if (config.getIdleTimeoutSeconds() > 0) {
                            pipeline.addLast("idleState", new IdleStateHandler(
                                    config.getReadTimeoutSeconds(),
                                    config.getWriteTimeoutSeconds(),
                                    config.getIdleTimeoutSeconds(),
                                    TimeUnit.SECONDS));
                        }

                        // Frame decoder
                        ChannelHandler frameDecoder = FrameDecoderFactory.createDecoder(
                                config.getFraming(), config.getFramingConfig());
                        if (frameDecoder != null) {
                            pipeline.addLast("frameDecoder", frameDecoder);
                        }

                        // Message codec
                        pipeline.addLast("messageCodec", codec);

                        // Business handler
                        pipeline.addLast("handler", new TcpChannelHandler(
                                sessionManager, handlerExecutor, metrics));
                    }
                });

        serverChannel = bootstrap.bind(config.getHost(), config.getPort()).sync().channel();
        running = true;

        log.info("TCP Listener started: name={} host={} port={} framing={} tls={} workers={} bizThreads={}",
                config.getName(), config.getHost(), config.getPort(),
                config.getFraming(), config.isTlsEnabled(),
                workerThreads, config.getBusinessThreads());
    }

    public void stop() {
        if (!running) return;
        running = false;

        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (businessExecutor != null) {
            businessExecutor.shutdown();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        }
        sessionManager.clear();

        log.info("TCP Listener stopped: name={} port={}", config.getName(), config.getPort());
    }

    @Override
    public void close() {
        stop();
    }

    public String name() { return config.getName(); }
    public String host() { return config.getHost(); }
    public int port() { return config.getPort(); }
    public boolean isRunning() { return running; }
    public TcpMessageRouter router() { return router; }
    public TcpSessionManager sessionManager() { return sessionManager; }
    public TcpMetrics metrics() { return metrics; }
    public TcpListenerConfig config() { return config; }
}
