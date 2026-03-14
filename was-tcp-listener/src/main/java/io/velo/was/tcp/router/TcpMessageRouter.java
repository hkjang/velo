package io.velo.was.tcp.router;

import io.velo.was.tcp.codec.TcpMessage;
import io.velo.was.tcp.session.TcpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes incoming TCP messages to registered handlers based on message type.
 * Supports a fallback handler for unmatched message types.
 */
public class TcpMessageRouter {

    private static final Logger log = LoggerFactory.getLogger(TcpMessageRouter.class);

    private final Map<String, TcpMessageHandler> handlers = new ConcurrentHashMap<>();
    private volatile TcpMessageHandler fallbackHandler;

    /**
     * Registers a handler for a specific message type.
     */
    public TcpMessageRouter route(String messageType, TcpMessageHandler handler) {
        handlers.put(messageType, handler);
        log.debug("Registered handler for message type: {}", messageType);
        return this;
    }

    /**
     * Sets the fallback handler for unmatched message types.
     */
    public TcpMessageRouter fallback(TcpMessageHandler handler) {
        this.fallbackHandler = handler;
        return this;
    }

    /**
     * Routes a message to the appropriate handler.
     * Returns true if a handler was found and invoked.
     */
    public boolean dispatch(TcpSession session, TcpMessage message, TcpResponseSender sender) {
        TcpMessageHandler handler = handlers.get(message.messageType());
        if (handler == null) {
            handler = fallbackHandler;
        }
        if (handler == null) {
            log.warn("No handler for message type: {} from session: {}",
                    message.messageType(), session.sessionId());
            return false;
        }
        handler.handle(session, message, sender);
        return true;
    }

    /**
     * Removes a handler for a message type.
     */
    public void removeRoute(String messageType) {
        handlers.remove(messageType);
    }

    /**
     * Returns the number of registered routes.
     */
    public int routeCount() {
        return handlers.size();
    }

    /**
     * Checks if a route exists for a message type.
     */
    public boolean hasRoute(String messageType) {
        return handlers.containsKey(messageType) || fallbackHandler != null;
    }
}
