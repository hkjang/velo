package io.velo.was.jndi;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

/**
 * {@link DataSource} implementation backed by {@link SimpleConnectionPool}.
 * <p>
 * Returned connections are wrapped so that {@code close()} returns them
 * to the pool instead of truly closing the underlying connection.
 */
public class PooledDataSource implements DataSource, AutoCloseable {

    private final String name;
    private final SimpleConnectionPool pool;
    private PrintWriter logWriter = new PrintWriter(System.out);
    private int loginTimeout = 30;

    public PooledDataSource(String name, SimpleConnectionPool pool) {
        this.name = name;
        this.pool = pool;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection raw = pool.borrow();
        return new PooledConnection(raw, pool);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        // Pool already has credentials configured
        return getConnection();
    }

    public String name() {
        return name;
    }

    public int activeConnections() {
        return pool.activeCount();
    }

    public int idleConnections() {
        return pool.idleCount();
    }

    public int maxConnections() {
        return pool.maxPoolSize();
    }

    public String url() {
        return pool.url();
    }

    @Override
    public void close() {
        pool.close();
    }

    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        this.logWriter = out;
    }

    @Override
    public void setLoginTimeout(int seconds) {
        this.loginTimeout = seconds;
    }

    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }
}
