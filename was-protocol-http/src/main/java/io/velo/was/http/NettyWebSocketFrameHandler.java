package io.velo.was.http;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.velo.was.observability.ErrorLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.UUID;

/**
 * Netty channel handler that processes WebSocket frames and delegates
 * to a {@link WebSocketHandler} implementation.
 */
public class NettyWebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger log = LoggerFactory.getLogger(NettyWebSocketFrameHandler.class);

    private final WebSocketHandler handler;
    private final String path;
    private volatile NettyWebSocketSession session;

    public NettyWebSocketFrameHandler(WebSocketHandler handler, String path) {
        this.handler = handler;
        this.path = path;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.HandshakeComplete) {
            session = new NettyWebSocketSession(ctx, path);
            log.debug("WebSocket opened: path={} remote={}", path, ctx.channel().remoteAddress());
            handler.onOpen(session);
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (session == null) {
            return;
        }
        if (frame instanceof TextWebSocketFrame textFrame) {
            handler.onText(session, textFrame.text());
        } else if (frame instanceof BinaryWebSocketFrame binaryFrame) {
            byte[] data = new byte[binaryFrame.content().readableBytes()];
            binaryFrame.content().readBytes(data);
            handler.onBinary(session, data);
        } else if (frame instanceof CloseWebSocketFrame closeFrame) {
            handler.onClose(session, closeFrame.statusCode(), closeFrame.reasonText());
        } else if (frame instanceof PingWebSocketFrame) {
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (session != null) {
            log.debug("WebSocket disconnected: path={} remote={}", path, ctx.channel().remoteAddress());
            handler.onClose(session, 1006, "Connection lost");
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (session != null) {
            handler.onError(session, cause);
        }
        ErrorLog.log(NettyWebSocketFrameHandler.class.getName(),
                "WebSocket error on " + path, cause);
        ctx.close();
    }

    /**
     * Concrete {@link WebSocketSession} backed by a Netty {@link ChannelHandlerContext}.
     */
    private static class NettyWebSocketSession implements WebSocketSession {

        private final ChannelHandlerContext ctx;
        private final String path;
        private final String id;

        NettyWebSocketSession(ChannelHandlerContext ctx, String path) {
            this.ctx = ctx;
            this.path = path;
            this.id = UUID.randomUUID().toString();
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public SocketAddress remoteAddress() {
            return ctx.channel().remoteAddress();
        }

        @Override
        public void sendText(String message) {
            if (isOpen()) {
                ctx.writeAndFlush(new TextWebSocketFrame(message));
            }
        }

        @Override
        public void sendBinary(byte[] data) {
            if (isOpen()) {
                ctx.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data)));
            }
        }

        @Override
        public void close() {
            close(1000, "Normal closure");
        }

        @Override
        public void close(int statusCode, String reason) {
            if (isOpen()) {
                ctx.writeAndFlush(new CloseWebSocketFrame(statusCode, reason))
                        .addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public boolean isOpen() {
            return ctx.channel().isActive();
        }
    }
}
