package io.velo.was.webadmin.api;

import io.velo.was.config.ServerConfiguration;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * Server-Sent Events endpoint for real-time monitoring data.
 * Pushes server status updates to connected clients at regular intervals.
 * <p>
 * Endpoint: {@code GET /sse/status} - streams status updates every 2 seconds.
 */
public class AdminSseServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(AdminSseServlet.class);
    private final ServerConfiguration configuration;

    public AdminSseServlet(ServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();

        if ("/status".equals(pathInfo)) {
            streamStatus(resp);
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().write("Unknown SSE stream");
        }
    }

    private void streamStatus(HttpServletResponse resp) throws IOException {
        resp.setContentType("text/event-stream");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");
        resp.setHeader("X-Accel-Buffering", "no");

        PrintWriter out = resp.getWriter();
        ServerConfiguration.Server server = configuration.getServer();

        // Stream up to 60 events (2 minutes at 2s intervals), then let client reconnect
        for (int i = 0; i < 60; i++) {
            MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
            long heapUsed = memory.getHeapMemoryUsage().getUsed();
            long heapMax = memory.getHeapMemoryUsage().getMax();
            long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
            int threadCount = ManagementFactory.getThreadMXBean().getThreadCount();

            String data = """
                    {"status":"RUNNING","serverName":"%s","uptimeMs":%d,\
                    "heapUsedBytes":%d,"heapMaxBytes":%d,"threadCount":%d,\
                    "timestamp":%d}""".formatted(
                    escapeJson(server.getName()),
                    uptimeMs, heapUsed, heapMax, threadCount,
                    System.currentTimeMillis()
            );

            out.write("event: status\n");
            out.write("data: " + data + "\n\n");
            out.flush();

            if (out.checkError()) {
                // Client disconnected
                return;
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
