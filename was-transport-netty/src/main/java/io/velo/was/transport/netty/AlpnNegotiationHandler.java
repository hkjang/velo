package io.velo.was.transport.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.velo.was.http.HttpHandlerRegistry;
import io.velo.was.http.NettyHttpChannelHandler;
import io.velo.was.http.WebSocketHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ALPN-based protocol negotiation handler.
 * After the TLS handshake, inspects the negotiated application protocol
 * and configures the pipeline for either HTTP/2 or HTTP/1.1.
 */
class AlpnNegotiationHandler extends ApplicationProtocolNegotiationHandler {

    private static final Logger log = LoggerFactory.getLogger(AlpnNegotiationHandler.class);

    private final HttpHandlerRegistry registry;
    private final WebSocketHandlerRegistry wsRegistry;
    private final int maxContentLength;

    AlpnNegotiationHandler(HttpHandlerRegistry registry, WebSocketHandlerRegistry wsRegistry,
                           int maxContentLength) {
        super(ApplicationProtocolNames.HTTP_1_1);
        this.registry = registry;
        this.wsRegistry = wsRegistry;
        this.maxContentLength = maxContentLength;
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
                new Http2StreamChannelInitializer(registry, wsRegistry, maxContentLength)));
    }

    private void configureHttp1(ChannelPipeline pipeline) {
        pipeline.addLast("httpCodec", new HttpServerCodec());
        pipeline.addLast("httpAggregator", new HttpObjectAggregator(maxContentLength));
        pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());
        pipeline.addLast("httpHandler", new NettyHttpChannelHandler(registry, wsRegistry));
    }

    /**
     * Initializer for each HTTP/2 stream (virtual channel).
     * Converts HTTP/2 frames to HTTP/1.1 objects so the same
     * {@link NettyHttpChannelHandler} can serve both protocols.
     */
    static class Http2StreamChannelInitializer extends ChannelInitializer<Channel> {

        private final HttpHandlerRegistry registry;
        private final WebSocketHandlerRegistry wsRegistry;
        private final int maxContentLength;

        Http2StreamChannelInitializer(HttpHandlerRegistry registry,
                                      WebSocketHandlerRegistry wsRegistry,
                                      int maxContentLength) {
            this.registry = registry;
            this.wsRegistry = wsRegistry;
            this.maxContentLength = maxContentLength;
        }

        @Override
        protected void initChannel(Channel ch) {
            ch.pipeline().addLast("h2ToHttp", new Http2StreamFrameToHttpObjectCodec(true));
            ch.pipeline().addLast("httpAggregator", new HttpObjectAggregator(maxContentLength));
            ch.pipeline().addLast("httpHandler", new NettyHttpChannelHandler(registry, wsRegistry));
        }
    }
}
