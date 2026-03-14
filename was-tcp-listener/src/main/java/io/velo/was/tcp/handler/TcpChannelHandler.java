package io.velo.was.tcp.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import io.velo.was.tcp.codec.TcpMessage;
import io.velo.was.tcp.observability.TcpMetrics;
import io.velo.was.tcp.router.TcpResponseSender;
import io.velo.was.tcp.session.TcpSession;
import io.velo.was.tcp.session.TcpSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty channel handler that bridges the Netty pipeline to the TCP Listener framework.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Session creation on channel active</li>
 *     <li>Session removal on channel inactive</li>
 *     <li>Message routing through the handler executor</li>
 *     <li>Exception handling with error isolation</li>
 *     <li>Metrics collection</li>
 * </ul>
 */
public class TcpChannelHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(TcpChannelHandler.class);
    private static final AttributeKey<TcpSession> SESSION_KEY = AttributeKey.valueOf("tcpSession");

    private final TcpSessionManager sessionManager;
    private final TcpHandlerExecutor handlerExecutor;
    private final TcpMetrics metrics;

    public TcpChannelHandler(TcpSessionManager sessionManager,
                             TcpHandlerExecutor handlerExecutor,
                             TcpMetrics metrics) {
        this.sessionManager = sessionManager;
        this.handlerExecutor = handlerExecutor;
        this.metrics = metrics;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        TcpSession session = sessionManager.createSession(
                ctx.channel().remoteAddress(),
                ctx.channel().localAddress());
        ctx.channel().attr(SESSION_KEY).set(session);
        metrics.onConnectionAccepted();
        log.debug("Connection active: {} session={}", ctx.channel().remoteAddress(), session.sessionId());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        TcpSession session = ctx.channel().attr(SESSION_KEY).getAndSet(null);
        if (session != null) {
            sessionManager.removeSession(session.sessionId());
            metrics.onConnectionClosed();
            log.debug("Connection inactive: {} session={}", ctx.channel().remoteAddress(), session.sessionId());
        }
        super.channelInactive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        TcpSession session = ctx.channel().attr(SESSION_KEY).get();
        if (session == null) {
            log.warn("Message received with no session: {}", ctx.channel().remoteAddress());
            return;
        }

        TcpMessage message;
        if (msg instanceof TcpMessage tcpMsg) {
            message = tcpMsg;
        } else if (msg instanceof io.netty.buffer.ByteBuf buf) {
            byte[] payload = new byte[buf.readableBytes()];
            buf.readBytes(payload);
            buf.release();
            message = new TcpMessage("default", payload);
        } else {
            log.warn("Unexpected message type: {}", msg.getClass().getName());
            return;
        }

        metrics.onMessageReceived();
        long startNanos = System.nanoTime();

        TcpResponseSender sender = new TcpResponseSender(ctx);
        handlerExecutor.execute(session, message, sender);

        metrics.onMessageProcessed(System.nanoTime() - startNanos);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        TcpSession session = ctx.channel().attr(SESSION_KEY).get();
        String sessionId = session != null ? session.sessionId() : "unknown";
        log.error("Channel exception session={} remote={}: {}",
                sessionId, ctx.channel().remoteAddress(), cause.getMessage(), cause);
        metrics.onError();
        ctx.close();
    }
}
