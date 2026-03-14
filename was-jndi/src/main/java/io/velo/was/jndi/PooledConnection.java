package io.velo.was.jndi;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * A delegating {@link Connection} wrapper that returns the underlying
 * connection to the pool on {@code close()} instead of closing it.
 */
class PooledConnection implements Connection {

    private final Connection delegate;
    private final SimpleConnectionPool pool;
    private volatile boolean logicallyClosed = false;

    PooledConnection(Connection delegate, SimpleConnectionPool pool) {
        this.delegate = delegate;
        this.pool = pool;
    }

    @Override
    public void close() throws SQLException {
        if (!logicallyClosed) {
            logicallyClosed = true;
            pool.release(delegate);
        }
    }

    @Override
    public boolean isClosed() throws SQLException {
        return logicallyClosed || delegate.isClosed();
    }

    private void checkOpen() throws SQLException {
        if (logicallyClosed) {
            throw new SQLException("Connection is closed (returned to pool)");
        }
    }

    // ── Delegating methods ──

    @Override public Statement createStatement() throws SQLException { checkOpen(); return delegate.createStatement(); }
    @Override public PreparedStatement prepareStatement(String sql) throws SQLException { checkOpen(); return delegate.prepareStatement(sql); }
    @Override public CallableStatement prepareCall(String sql) throws SQLException { checkOpen(); return delegate.prepareCall(sql); }
    @Override public String nativeSQL(String sql) throws SQLException { checkOpen(); return delegate.nativeSQL(sql); }
    @Override public void setAutoCommit(boolean autoCommit) throws SQLException { checkOpen(); delegate.setAutoCommit(autoCommit); }
    @Override public boolean getAutoCommit() throws SQLException { checkOpen(); return delegate.getAutoCommit(); }
    @Override public void commit() throws SQLException { checkOpen(); delegate.commit(); }
    @Override public void rollback() throws SQLException { checkOpen(); delegate.rollback(); }
    @Override public DatabaseMetaData getMetaData() throws SQLException { checkOpen(); return delegate.getMetaData(); }
    @Override public void setReadOnly(boolean readOnly) throws SQLException { checkOpen(); delegate.setReadOnly(readOnly); }
    @Override public boolean isReadOnly() throws SQLException { checkOpen(); return delegate.isReadOnly(); }
    @Override public void setCatalog(String catalog) throws SQLException { checkOpen(); delegate.setCatalog(catalog); }
    @Override public String getCatalog() throws SQLException { checkOpen(); return delegate.getCatalog(); }
    @Override public void setTransactionIsolation(int level) throws SQLException { checkOpen(); delegate.setTransactionIsolation(level); }
    @Override public int getTransactionIsolation() throws SQLException { checkOpen(); return delegate.getTransactionIsolation(); }
    @Override public SQLWarning getWarnings() throws SQLException { checkOpen(); return delegate.getWarnings(); }
    @Override public void clearWarnings() throws SQLException { checkOpen(); delegate.clearWarnings(); }
    @Override public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException { checkOpen(); return delegate.createStatement(resultSetType, resultSetConcurrency); }
    @Override public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { checkOpen(); return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency); }
    @Override public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { checkOpen(); return delegate.prepareCall(sql, resultSetType, resultSetConcurrency); }
    @Override public Map<String, Class<?>> getTypeMap() throws SQLException { checkOpen(); return delegate.getTypeMap(); }
    @Override public void setTypeMap(Map<String, Class<?>> map) throws SQLException { checkOpen(); delegate.setTypeMap(map); }
    @Override public void setHoldability(int holdability) throws SQLException { checkOpen(); delegate.setHoldability(holdability); }
    @Override public int getHoldability() throws SQLException { checkOpen(); return delegate.getHoldability(); }
    @Override public Savepoint setSavepoint() throws SQLException { checkOpen(); return delegate.setSavepoint(); }
    @Override public Savepoint setSavepoint(String name) throws SQLException { checkOpen(); return delegate.setSavepoint(name); }
    @Override public void rollback(Savepoint savepoint) throws SQLException { checkOpen(); delegate.rollback(savepoint); }
    @Override public void releaseSavepoint(Savepoint savepoint) throws SQLException { checkOpen(); delegate.releaseSavepoint(savepoint); }
    @Override public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { checkOpen(); return delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability); }
    @Override public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { checkOpen(); return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability); }
    @Override public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { checkOpen(); return delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability); }
    @Override public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException { checkOpen(); return delegate.prepareStatement(sql, autoGeneratedKeys); }
    @Override public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException { checkOpen(); return delegate.prepareStatement(sql, columnIndexes); }
    @Override public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException { checkOpen(); return delegate.prepareStatement(sql, columnNames); }
    @Override public Clob createClob() throws SQLException { checkOpen(); return delegate.createClob(); }
    @Override public Blob createBlob() throws SQLException { checkOpen(); return delegate.createBlob(); }
    @Override public NClob createNClob() throws SQLException { checkOpen(); return delegate.createNClob(); }
    @Override public SQLXML createSQLXML() throws SQLException { checkOpen(); return delegate.createSQLXML(); }
    @Override public boolean isValid(int timeout) throws SQLException { return !logicallyClosed && delegate.isValid(timeout); }
    @Override public void setClientInfo(String name, String value) throws SQLClientInfoException { delegate.setClientInfo(name, value); }
    @Override public void setClientInfo(Properties properties) throws SQLClientInfoException { delegate.setClientInfo(properties); }
    @Override public String getClientInfo(String name) throws SQLException { checkOpen(); return delegate.getClientInfo(name); }
    @Override public Properties getClientInfo() throws SQLException { checkOpen(); return delegate.getClientInfo(); }
    @Override public Array createArrayOf(String typeName, Object[] elements) throws SQLException { checkOpen(); return delegate.createArrayOf(typeName, elements); }
    @Override public Struct createStruct(String typeName, Object[] attributes) throws SQLException { checkOpen(); return delegate.createStruct(typeName, attributes); }
    @Override public void setSchema(String schema) throws SQLException { checkOpen(); delegate.setSchema(schema); }
    @Override public String getSchema() throws SQLException { checkOpen(); return delegate.getSchema(); }
    @Override public void abort(Executor executor) throws SQLException { delegate.abort(executor); }
    @Override public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException { checkOpen(); delegate.setNetworkTimeout(executor, milliseconds); }
    @Override public int getNetworkTimeout() throws SQLException { checkOpen(); return delegate.getNetworkTimeout(); }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { if (iface.isInstance(this)) return iface.cast(this); return delegate.unwrap(iface); }
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return iface.isInstance(this) || delegate.isWrapperFor(iface); }
}
