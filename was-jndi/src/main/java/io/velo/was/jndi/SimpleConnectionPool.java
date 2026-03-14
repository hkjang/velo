package io.velo.was.jndi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight JDBC connection pool.
 * <ul>
 *   <li>Creates connections lazily up to {@code maxPoolSize}</li>
 *   <li>Idle connections are returned to the pool for reuse</li>
 *   <li>Validates connections before handing them out (configurable)</li>
 *   <li>Thread-safe via a bounded {@link BlockingDeque}</li>
 * </ul>
 */
public class SimpleConnectionPool implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SimpleConnectionPool.class);

    private final String url;
    private final Properties connectionProperties;
    private final int minIdle;
    private final int maxPoolSize;
    private final long borrowTimeoutMs;
    private final String validationQuery;

    private final BlockingDeque<Connection> idlePool = new LinkedBlockingDeque<>();
    private final AtomicInteger totalCreated = new AtomicInteger(0);
    private volatile boolean closed = false;

    private SimpleConnectionPool(Builder builder) {
        this.url = builder.url;
        this.connectionProperties = new Properties();
        if (builder.username != null) {
            this.connectionProperties.setProperty("user", builder.username);
        }
        if (builder.password != null) {
            this.connectionProperties.setProperty("password", builder.password);
        }
        this.minIdle = builder.minIdle;
        this.maxPoolSize = builder.maxPoolSize;
        this.borrowTimeoutMs = builder.borrowTimeoutMs;
        this.validationQuery = builder.validationQuery;

        // Load driver class if specified
        if (builder.driverClassName != null && !builder.driverClassName.isBlank()) {
            try {
                Class.forName(builder.driverClassName);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("JDBC driver not found: " + builder.driverClassName, e);
            }
        }

        // Pre-fill minIdle connections
        for (int i = 0; i < minIdle; i++) {
            try {
                Connection conn = createConnection();
                idlePool.offer(conn);
                totalCreated.incrementAndGet();
            } catch (SQLException e) {
                log.warn("Failed to pre-fill idle connection {}/{}", i + 1, minIdle, e);
            }
        }

        log.info("Connection pool created: url={} minIdle={} maxPoolSize={}", url, minIdle, maxPoolSize);
    }

    /**
     * Borrows a connection from the pool.
     * If no idle connection is available and the pool hasn't reached max size,
     * a new connection is created. Otherwise, blocks up to borrowTimeout.
     */
    public Connection borrow() throws SQLException {
        if (closed) {
            throw new SQLException("Connection pool is closed");
        }

        // Try to get an idle connection
        Connection conn = idlePool.pollFirst();
        if (conn != null) {
            if (isValid(conn)) {
                return conn;
            }
            // Connection is stale, discard it
            closeQuietly(conn);
            totalCreated.decrementAndGet();
        }

        // Try to create a new connection if under limit
        if (totalCreated.get() < maxPoolSize) {
            int current = totalCreated.incrementAndGet();
            if (current <= maxPoolSize) {
                try {
                    return createConnection();
                } catch (SQLException e) {
                    totalCreated.decrementAndGet();
                    throw e;
                }
            }
            totalCreated.decrementAndGet();
        }

        // Pool is exhausted — wait for a returned connection
        try {
            conn = idlePool.pollFirst(borrowTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for connection", e);
        }

        if (conn == null) {
            throw new SQLException("Connection pool exhausted, timeout after " + borrowTimeoutMs + "ms");
        }

        if (isValid(conn)) {
            return conn;
        }

        closeQuietly(conn);
        totalCreated.decrementAndGet();
        return borrow(); // recursive retry
    }

    /**
     * Returns a connection to the pool for reuse.
     */
    public void release(Connection connection) {
        if (connection == null) {
            return;
        }
        if (closed) {
            closeQuietly(connection);
            totalCreated.decrementAndGet();
            return;
        }
        try {
            if (!connection.isClosed() && !connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            closeQuietly(connection);
            totalCreated.decrementAndGet();
            return;
        }
        if (!idlePool.offerFirst(connection)) {
            closeQuietly(connection);
            totalCreated.decrementAndGet();
        }
    }

    /** Number of active (borrowed) connections. */
    public int activeCount() {
        return totalCreated.get() - idlePool.size();
    }

    /** Number of idle connections in the pool. */
    public int idleCount() {
        return idlePool.size();
    }

    /** Total number of connections managed by the pool (active + idle). */
    public int totalCount() {
        return totalCreated.get();
    }

    public int maxPoolSize() {
        return maxPoolSize;
    }

    public String url() {
        return url;
    }

    @Override
    public void close() {
        closed = true;
        Connection conn;
        while ((conn = idlePool.poll()) != null) {
            closeQuietly(conn);
            totalCreated.decrementAndGet();
        }
        log.info("Connection pool closed: url={}", url);
    }

    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(url, connectionProperties);
    }

    private boolean isValid(Connection conn) {
        try {
            if (conn.isClosed()) {
                return false;
            }
            if (validationQuery != null && !validationQuery.isBlank()) {
                try (var stmt = conn.createStatement()) {
                    stmt.execute(validationQuery);
                }
            } else {
                return conn.isValid(2);
            }
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private void closeQuietly(Connection conn) {
        try {
            conn.close();
        } catch (SQLException ignored) {
        }
    }

    public static Builder builder(String url) {
        return new Builder(url);
    }

    public static class Builder {
        private final String url;
        private String driverClassName;
        private String username;
        private String password;
        private int minIdle = 2;
        private int maxPoolSize = 20;
        private long borrowTimeoutMs = 30_000;
        private String validationQuery;

        Builder(String url) {
            this.url = url;
        }

        public Builder driverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder minIdle(int minIdle) {
            this.minIdle = minIdle;
            return this;
        }

        public Builder maxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return this;
        }

        public Builder borrowTimeoutMs(long borrowTimeoutMs) {
            this.borrowTimeoutMs = borrowTimeoutMs;
            return this;
        }

        public Builder validationQuery(String validationQuery) {
            this.validationQuery = validationQuery;
            return this;
        }

        public SimpleConnectionPool build() {
            return new SimpleConnectionPool(this);
        }
    }
}
