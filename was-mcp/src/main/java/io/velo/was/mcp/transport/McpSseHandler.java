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
import java.util.concurrent.atomic.AtomicReference;

/**
 * SSE handler for the MCP endpoint ({@code GET /ai-platform/mcp} with {@code Accept: text/event-stream}).
 *
 * <p>On connection:
 * <ol>
 *   <li>Reads the {@code Mcp-Session-Id} request header.</li>
 *   <li>If a matching session exists, attaches the sink and touches the session.</li>
 *   <li>Sends an initial {@code endpoint} event per the MCP Streamable HTTP spec.</li>
 *   <li>Schedules a heartbeat every 25 seconds to keep the connection alive through proxies.
 *       The heartbeat also touches the session to prevent idle eviction.</li>
 * </ol>
 *
 * <p>When the client disconnects (sink closes), the heartbeat task detects this
 * and cancels itself to free resources.
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
            session.touch();
            log.info("SSE stream attached to session id={} client={}", sessionId, session.clientName());
        } else {
            if (sessionId != null && !sessionId.isBlank()) {
                log.warn("SSE stream opened with unknown session id={}", sessionId);
            } else {
                log.debug("SSE stream opened without session");
            }
        }

        // Send an endpoint event per MCP Streamable HTTP spec so clients know the stream is ready
        String endpointEvent = JsonRpcResponse.notification("mcp/endpoint",
                "{\"uri\":\"" + McpEndpointPaths.MCP_PATH + "\"}");
        sink.emit("endpoint", endpointEvent);

        // Schedule periodic heartbeat: keeps proxy alive + touches session to prevent eviction.
        // When the sink closes (client disconnect), the task detects isOpen()==false and cancels itself.
        AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();
        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!sink.isOpen()) {
                // Client disconnected — cancel this task to free scheduler resources
                ScheduledFuture<?> self = futureRef.get();
                if (self != null) {
                    self.cancel(false);
                }
                log.debug("SSE heartbeat stopped (sink closed), sessionId={}", sessionId);
                return;
            }
            sink.ping();
            // Touch session to prevent idle-timeout eviction while SSE is active
            if (session != null) {
                session.touch();
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        futureRef.set(heartbeat);

        log.debug("SSE heartbeat scheduled for sessionId={}", sessionId);
    }
}
