package io.velo.was.transport.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AsciiString;
import io.velo.was.config.ServerConfiguration;
import io.velo.was.http.HttpHandlerRegistry;
import io.velo.was.http.NettyHttpChannelHandler;
import io.velo.was.http.WebSocketHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class NettyServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NettyServer.class);

    private final ServerConfiguration.Server server;
    private final HttpHandlerRegistry registry;
    private final WebSocketHandlerRegistry wsRegistry;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private ReloadingSslContextProvider sslContextProvider;

    public NettyServer(ServerConfiguration.Server server, HttpHandlerRegistry registry) {
        this(server, registry, null);
    }

    public NettyServer(ServerConfiguration.Server server, HttpHandlerRegistry registry,
                       WebSocketHandlerRegistry wsRegistry) {
        this.server = server;
        this.registry = registry;
        this.wsRegistry = wsRegistry;
    }

    public void start() throws Exception {
        ServerConfiguration.Listener listener = server.getListener();
        ServerConfiguration.Threading threading = server.getThreading();

        bossGroup = NativeTransportSelector.createBossGroup(threading.getBossThreads());
        workerGroup = NativeTransportSelector.createWorkerGroup(threading.getWorkerThreads());

        if (server.getTls().isEnabled()) {
            sslContextProvider = new ReloadingSslContextProvider(server.getTls());
        }

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NativeTransportSelector.serverChannelClass())
                .option(ChannelOption.SO_BACKLOG, listener.getSoBacklog())
                .option(ChannelOption.SO_REUSEADDR, listener.isReuseAddress())
                .childOption(ChannelOption.SO_KEEPALIVE, listener.isKeepAlive())
                .childOption(ChannelOption.TCP_NODELAY, listener.isTcpNoDelay())
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) throws Exception {
                        if (sslContextProvider != null) {
                            // ── TLS: ALPN negotiates h2 / http/1.1 ──
                            SslContext sslContext = sslContextProvider.current();
                            channel.pipeline().addLast("ssl", sslContext.newHandler(channel.alloc()));
                            channel.pipeline().addLast("alpn",
                                    new AlpnNegotiationHandler(server, registry, wsRegistry));
                        } else {
                            // ── Cleartext: h2c upgrade + HTTP/1.1 ──
                            configureCleartextPipeline(channel);
                        }
                    }
                });

        serverChannel = bootstrap.bind(listener.getHost(), listener.getPort()).sync().channel();

        log.info("velo-was started name={} nodeId={} host={} port={} tlsEnabled={} http2={} transport={}",
                server.getName(),
                server.getNodeId(),
                listener.getHost(),
                listener.getPort(),
                server.getTls().isEnabled(),
                true,
                NativeTransportSelector.transportName());
    }

    /**
     * Configures the cleartext pipeline that supports:
     * <ul>
     *   <li>Direct HTTP/2 connection (PRI * preface)</li>
     *   <li>HTTP/1.1 → HTTP/2 upgrade (Upgrade: h2c)</li>
     *   <li>Plain HTTP/1.1 (no upgrade)</li>
     * </ul>
     */
    private void configureCleartextPipeline(SocketChannel channel) {
        ServerConfiguration.Listener listener = server.getListener();
        ServerConfiguration.Compression compression = server.getCompression();
        int maxContentLength = listener.getMaxContentLength();

        HttpServerCodec sourceCodec = new HttpServerCodec(
                listener.getMaxInitialLineLength(),
                listener.getMaxHeaderSize(),
                8192);

        HttpServerUpgradeHandler.UpgradeCodecFactory upgradeCodecFactory = protocol -> {
            if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
                return new Http2ServerUpgradeCodec(
                        Http2FrameCodecBuilder.forServer().build(),
                        new Http2MultiplexHandler(
                                new AlpnNegotiationHandler.Http2StreamChannelInitializer(
                                        server, registry, wsRegistry)));
            }
            return null;
        };

        HttpServerUpgradeHandler upgradeHandler =
                new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory, maxContentLength);

        CleartextHttp2ServerUpgradeHandler h2cHandler = new CleartextHttp2ServerUpgradeHandler(
                sourceCodec,
                upgradeHandler,
                // Handler for direct HTTP/2 (PRI * preface without upgrade)
                new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast("h2Codec", Http2FrameCodecBuilder.forServer().build());
                        ch.pipeline().addLast("h2Multiplexer", new Http2MultiplexHandler(
                                new AlpnNegotiationHandler.Http2StreamChannelInitializer(
                                        server, registry, wsRegistry)));
                    }
                });

        channel.pipeline().addLast("h2cUpgrade", h2cHandler);

        // Fallback: if no HTTP/2 upgrade happens, these handlers process HTTP/1.1
        channel.pipeline().addLast("httpAggregator", new HttpObjectAggregator(maxContentLength));
        channel.pipeline().addLast("chunkedWriter", new ChunkedWriteHandler());

        // Idle timeout
        int idleTimeout = listener.getIdleTimeoutSeconds();
        if (idleTimeout > 0) {
            channel.pipeline().addLast("idleHandler",
                    new IdleStateHandler(idleTimeout, 0, 0, TimeUnit.SECONDS));
            channel.pipeline().addLast("idleEventHandler", new IdleChannelHandler());
        }

        // Gzip compression
        if (compression.isEnabled()) {
            channel.pipeline().addLast("compressor",
                    new HttpContentCompressor(compression.getCompressionLevel()));
        }

        channel.pipeline().addLast("httpHandler", new NettyHttpChannelHandler(registry, wsRegistry));
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.closeFuture().sync();
        }
    }

    @Override
    public void close() {
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        long timeoutMillis = server.getGracefulShutdownMillis();
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, timeoutMillis, TimeUnit.MILLISECONDS).syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, timeoutMillis, TimeUnit.MILLISECONDS).syncUninterruptibly();
        }
        log.info("velo-was shutdown completed");
    }
}
