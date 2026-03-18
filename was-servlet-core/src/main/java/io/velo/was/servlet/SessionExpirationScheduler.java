package io.velo.was.servlet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically scans the session store and removes expired sessions.
 * The default interval is 60 seconds, configurable via constructor.
 */
public class SessionExpirationScheduler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SessionExpirationScheduler.class);

    private final HttpSessionStore sessionStore;
    private final ScheduledExecutorService scheduler;
    private final long intervalSeconds;

    public SessionExpirationScheduler(HttpSessionStore sessionStore) {
        this(sessionStore, 60);
    }

    public SessionExpirationScheduler(HttpSessionStore sessionStore, long intervalSeconds) {
        this.sessionStore = sessionStore;
        this.intervalSeconds = intervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-expiry-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the periodic purge task.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                sessionStore.purgeExpired();
            } catch (Exception e) {
                log.error("Session purge failed", e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

        log.info("Session expiration scheduler started: interval={}s", intervalSeconds);
    }

    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Session expiration scheduler stopped");
    }
}
