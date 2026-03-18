package io.velo.was.transport.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.velo.was.config.ServerConfiguration;
import io.velo.was.http.HttpHandlerRegistry;
import io.velo.was.http.NettyHttpChannelHandler;
import io.velo.was.http.WebSocketHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * ALPN-based protocol negotiation handler.
 * After the TLS handshake, inspects the negotiated application protocol
 * and configures the pipeline for either HTTP/2 or HTTP/1.1.
 */
class AlpnNegotiationHandler extends ApplicationProtocolNegotiationHandler {

    private static final Logger log = LoggerFactory.getLogger(AlpnNegotiationHandler.class);

    private final ServerConfiguration.Server serverConfig;
    private final HttpHandlerRegistry registry;
    private final WebSocketHandlerRegistry wsRegistry;
    private final Supplier<ChannelHandler> httpPipelineHandlerFactory;

    AlpnNegotiationHandler(ServerConfiguration.Server serverConfig,
                           HttpHandlerRegistry registry,
                           WebSocketHandlerRegistry wsRegistry,
                           Supplier<ChannelHandler> httpPipelineHandlerFactory) {
        super(ApplicationProtocolNames.HTTP_1_1);
        this.serverConfig = serverConfig;
        this.registry = registry;
        this.wsRegistry = wsRegistry;
        this.httpPipelineHandlerFactory = httpPipelineHandlerFactory;
    }

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
            configureHttp2(ctx.pipeline());
            log.debug("ALPN negotiated HTTP/2 for {}", ctx.channel().remoteAddress());
        } else if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
            configureHttp1(ctx.pipeline());
            log.debug("ALPN negotiated HTTP/1.1 for {}", ctx.channel().remoteAddress());
        } else {
            throw new IllegalStateException("Unsupported ALPN protocol: " + protocol);
        }
    }

    private void configureHttp2(ChannelPipeline pipeline) {
        pipeline.addLast("h2Codec", Http2FrameCodecBuilder.forServer().build());
        pipeline.addLast("h2Multiplexer", new Http2MultiplexHandler(
                new Http2StreamChannelInitializer(serverConfig, registry, wsRegistry, httpPipelineHandlerFactory)));
    }

    private void configureHttp1(ChannelPipeline pipeline) {
        ServerConfiguration.Listener listener = serverConfig.getListener();
        ServerConfiguration.Compression compression = serverConfig.getCompression();

        pipeline.addLast("httpCodec", new HttpServerCodec(
                listener.getMaxInitialLineLength(),
                listener.getMaxHeaderSize(),
                8192));
        if (httpPipelineHandlerFactory != null) {
            pipeline.addLast("httpPipelineHandler", httpPipelineHandlerFactory.get());
        }
        pipeline.addLast("httpAggregator", new HttpObjectAggregator(listener.getMaxContentLength()));
        pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());

        int idleTimeout = listener.getIdleTimeoutSeconds();
        if (idleTimeout > 0) {
            pipeline.addLast("idleHandler",
                    new IdleStateHandler(idleTimeout, 0, 0, TimeUnit.SECONDS));
            pipeline.addLast("idleEventHandler", new IdleChannelHandler());
        }

        if (compression.isEnabled()) {
            pipeline.addLast("compressor",
                    new HttpContentCompressor(compression.getCompressionLevel()));
        }

        pipeline.addLast("httpHandler", new NettyHttpChannelHandler(registry, wsRegistry));
    }

    /**
     * Initializer for each HTTP/2 stream (virtual channel).
     * Converts HTTP/2 frames to HTTP/1.1 objects so the same
     * {@link NettyHttpChannelHandler} can serve both protocols.
     */
    static class Http2StreamChannelInitializer extends ChannelInitializer<Channel> {

        private final ServerConfiguration.Server serverConfig;
        private final HttpHandlerRegistry registry;
        private final WebSocketHandlerRegistry wsRegistry;
        private final Supplier<ChannelHandler> httpPipelineHandlerFactory;

        Http2StreamChannelInitializer(ServerConfiguration.Server serverConfig,
                                      HttpHandlerRegistry registry,
                                      WebSocketHandlerRegistry wsRegistry,
                                      Supplier<ChannelHandler> httpPipelineHandlerFactory) {
            this.serverConfig = serverConfig;
            this.registry = registry;
            this.wsRegistry = wsRegistry;
            this.httpPipelineHandlerFactory = httpPipelineHandlerFactory;
        }

        @Override
        protected void initChannel(Channel ch) {
            int maxContentLength = serverConfig.getListener().getMaxContentLength();
            ch.pipeline().addLast("h2ToHttp", new Http2StreamFrameToHttpObjectCodec(true));
            if (httpPipelineHandlerFactory != null) {
                ch.pipeline().addLast("httpPipelineHandler", httpPipelineHandlerFactory.get());
            }
            ch.pipeline().addLast("httpAggregator", new HttpObjectAggregator(maxContentLength));

            if (serverConfig.getCompression().isEnabled()) {
                ch.pipeline().addLast("compressor",
                        new HttpContentCompressor(serverConfig.getCompression().getCompressionLevel()));
            }

            ch.pipeline().addLast("httpHandler", new NettyHttpChannelHandler(registry, wsRegistry));
        }
    }
}
