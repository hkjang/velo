package io.velo.was.tcp.router;

import io.velo.was.tcp.codec.TcpMessage;
import io.velo.was.tcp.session.TcpSession;

/**
 * Business handler interface for processing TCP messages.
 * Implementations handle specific message types.
 */
@FunctionalInterface
public interface TcpMessageHandler {

    /**
     * Handles an incoming TCP message.
     *
     * @param session  the connection session
     * @param message  the decoded message
     * @param sender   the response sender for replying
     */
    void handle(TcpSession session, TcpMessage message, TcpResponseSender sender);
}
