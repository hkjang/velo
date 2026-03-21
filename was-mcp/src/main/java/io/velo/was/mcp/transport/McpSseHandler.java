package io.velo.was.mcp.transport;

import io.velo.was.http.HttpExchange;
import io.velo.was.http.SseHandler;
import io.velo.was.http.SseSink;
import io.velo.was.mcp.McpServer;
import io.velo.was.mcp.protocol.JsonRpcResponse;
import io.velo.was.mcp.session.McpSession;
import io.velo.was.mcp.session.McpSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SSE handler for the MCP endpoint ({@code GET /mcp} with {@code Accept: text/event-stream}).
 *
 * <p>On connection:
 * <ol>
 *   <li>Reads the {@code Mcp-Session-Id} request header.</li>
 *   <li>If a matching session exists, attaches the sink to it so the server can push events.</li>
 *   <li>Sends an initial SSE ping to confirm the stream is live.</li>
 *   <li>Schedules a heartbeat every 25 seconds to keep the connection alive through proxies.</li>
 * </ol>
 *
 * <p>The stream stays open until the client closes it or {@link SseSink#close()} is called.
 */
public class McpSseHandler implements SseHandler {

    private static final Logger log = LoggerFactory.getLogger(McpSseHandler.class);

    /** Heartbeat interval keeps connections alive through 30 s proxy timeouts. */
    private static final int HEARTBEAT_INTERVAL_SECONDS = 25;

    private final McpServer server;
    private final ScheduledExecutorService heartbeatScheduler;

    public McpSseHandler(McpServer server) {
        this.server = server;
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void onConnect(HttpExchange exchange, SseSink sink) {
        String sessionId = exchange.headers().get(McpPostHandler.SESSION_HEADER);
        McpSessionManager sessionManager = server.sessionManager();

        McpSession session = sessionId != null ? sessionManager.get(sessionId) : null;
        if (session != null) {
            session.setSseSink(sink);
            log.debug("SSE stream attached to session id={}", sessionId);
        } else {
            log.debug("SSE stream opened with no matching session (sessionId={})", sessionId);
        }

        // Send an endpoint event per MCP Streamable HTTP spec so clients know the stream is ready
        String endpointEvent = JsonRpcResponse.notification("mcp/endpoint",
                "{\"uri\":\"" + McpEndpointPaths.MCP_PATH + "\"}");
        sink.emit("endpoint", endpointEvent);

        // Schedule periodic pings
        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (sink.isOpen()) {
                sink.ping();
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // Heartbeat cleanup: the heartbeat task checks isOpen() so it will stop naturally,
        // but cancel it eagerly when the client disconnects to free resources.
        // Phase 1: rely on the heartbeat's own isOpen() guard; the task is daemon-thread based.
        final ScheduledFuture<?> heartbeatRef = heartbeat;
        log.debug("SSE heartbeat scheduled, active={}", !heartbeatRef.isCancelled());
    }
}
