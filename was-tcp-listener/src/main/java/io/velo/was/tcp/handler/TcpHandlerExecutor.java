package io.velo.was.tcp.handler;

import io.velo.was.tcp.codec.TcpMessage;
import io.velo.was.tcp.router.TcpMessageRouter;
import io.velo.was.tcp.router.TcpResponseSender;
import io.velo.was.tcp.session.TcpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

/**
 * Executes business handlers on a separate thread pool to keep Netty EventLoop
 * free from blocking I/O. Enforces the design principle: no blocking in EventLoop.
 */
public class TcpHandlerExecutor {

    private static final Logger log = LoggerFactory.getLogger(TcpHandlerExecutor.class);

    private final ExecutorService businessExecutor;
    private final TcpMessageRouter router;

    public TcpHandlerExecutor(ExecutorService businessExecutor, TcpMessageRouter router) {
        this.businessExecutor = businessExecutor;
        this.router = router;
    }

    /**
     * Submits a message for processing on the business thread pool.
     */
    public void execute(TcpSession session, TcpMessage message, TcpResponseSender sender) {
        businessExecutor.submit(() -> {
            try {
                session.touch();
                boolean dispatched = router.dispatch(session, message, sender);
                if (!dispatched) {
                    log.warn("No handler dispatched for message type: {} session: {}",
                            message.messageType(), session.sessionId());
                }
            } catch (Exception e) {
                log.error("Handler execution failed for session={} messageType={}",
                        session.sessionId(), message.messageType(), e);
            }
        });
    }

    /**
     * Shuts down the business executor.
     */
    public void shutdown() {
        businessExecutor.shutdown();
    }
}
