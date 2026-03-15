package io.velo.was.http;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslHandler;
import io.velo.was.observability.AccessLog;
import io.velo.was.observability.AccessLogEntry;
import io.velo.was.observability.ErrorLog;
import io.velo.was.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class NettyHttpChannelHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final Logger log = LoggerFactory.getLogger(NettyHttpChannelHandler.class);

    private final HttpHandlerRegistry registry;
    private final WebSocketHandlerRegistry wsRegistry;

    public NettyHttpChannelHandler(HttpHandlerRegistry registry) {
        this(registry, null);
    }

    public NettyHttpChannelHandler(HttpHandlerRegistry registry, WebSocketHandlerRegistry wsRegistry) {
        this.registry = registry;
        this.wsRegistry = wsRegistry;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        MetricsCollector.instance().connectionOpened();
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        MetricsCollector.instance().connectionClosed();
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        // ── WebSocket upgrade detection ──
        if (wsRegistry != null && isWebSocketUpgrade(request)) {
            String path = new QueryStringDecoder(request.uri()).path();
            WebSocketHandler wsHandler = wsRegistry.resolve(path);
            if (wsHandler != null) {
                installWebSocketPipeline(ctx, request, wsHandler, path);
                return;
            }
        }

        // ── Normal HTTP processing ──
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        long startTime = System.nanoTime();
        MetricsCollector.instance().requestStarted();

        ResponseSink responseSink = response -> {
            long durationNanos = System.nanoTime() - startTime;
            long durationMs = durationNanos / 1_000_000;
            MetricsCollector.instance().requestCompleted(durationNanos, response.status().code());

            if (keepAlive) {
                response.headers().set(HttpHeaderNames.CONNECTION, "keep-alive");
            }

            AccessLog.log(new AccessLogEntry(
                    Instant.now(),
                    request.method().name(),
                    request.uri(),
                    request.protocolVersion().text(),
                    response.status().code(),
                    response.content().readableBytes(),
                    durationMs,
                    ctx.channel().remoteAddress() != null ? ctx.channel().remoteAddress().toString() : null,
                    request.headers().get(HttpHeaderNames.USER_AGENT),
                    request.headers().get(HttpHeaderNames.REFERER),
                    null));

            if (keepAlive) {
                ctx.writeAndFlush(response);
            } else {
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            }
        };

        FullHttpResponse response;
        try {
            if (!request.decoderResult().isSuccess()) {
                response = HttpResponses.badRequest("Malformed HTTP request");
            } else {
                boolean secure = ctx.pipeline().get(SslHandler.class) != null;
                HttpExchange exchange = new HttpExchange(
                        request, ctx.channel().remoteAddress(), ctx.channel().localAddress(), responseSink, secure);
                response = registry.resolve(exchange).handle(exchange);
            }
        } catch (Exception exception) {
            ErrorLog.log(NettyHttpChannelHandler.class.getName(),
                    "Unhandled request failure", exception, null, request.uri());
            response = HttpResponses.serverError("Unhandled server error");
        }

        if (response == null) {
            return;
        }

        responseSink.send(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ErrorLog.log(NettyHttpChannelHandler.class.getName(), "Netty pipeline error", cause);
        ctx.close();
    }

    // ────────────────────────────────────────────────────────────────────────
    // WebSocket helpers
    // ────────────────────────────────────────────────────────────────────────

    private static boolean isWebSocketUpgrade(FullHttpRequest request) {
        return request.headers().containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)
                && request.headers().containsValue(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true);
    }

    /**
     * Reconfigures the channel pipeline for WebSocket communication.
     * <ol>
     *   <li>Adds {@link WebSocketServerProtocolHandler} which performs the upgrade handshake</li>
     *   <li>Adds {@link NettyWebSocketFrameHandler} which delegates frames to user code</li>
     *   <li>Removes this HTTP handler (no longer needed for this connection)</li>
     *   <li>Re-fires the original request so the protocol handler can see the upgrade</li>
     * </ol>
     */
    private void installWebSocketPipeline(ChannelHandlerContext ctx, FullHttpRequest request,
                                          WebSocketHandler wsHandler, String path) {
        ChannelPipeline pipeline = ctx.pipeline();

        pipeline.addLast("wsProtocol",
                new WebSocketServerProtocolHandler(path, null, true, 65536));
        pipeline.addLast("wsFrameHandler",
                new NettyWebSocketFrameHandler(wsHandler, path));

        // Remove ourselves — the WebSocketServerProtocolHandler takes over HTTP upgrade
        pipeline.remove(this);

        // Re-fire the request so WebSocketServerProtocolHandler sees the upgrade
        ctx.fireChannelRead(request.retain());

        log.debug("WebSocket pipeline installed for path={}", path);
    }
}
