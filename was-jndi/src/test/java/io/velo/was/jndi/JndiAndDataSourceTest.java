package io.velo.was.jndi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Hashtable;

import static org.junit.jupiter.api.Assertions.*;

class JndiAndDataSourceTest {

    private VeloNamingContext root;

    @BeforeEach
    void setUp() {
        root = new VeloNamingContext();
    }

    // ────────────────────────────────────────────────────────────────────────
    // JNDI NamingContext tests
    // ────────────────────────────────────────────────────────────────────────

    @Test
    void bindAndLookup() throws NamingException {
        root.bind("java:comp/env/greeting", "Hello Velo");
        Object result = root.lookup("java:comp/env/greeting");
        assertEquals("Hello Velo", result);
    }

    @Test
    void lookupNonExistentThrowsNameNotFoundException() {
        assertThrows(NameNotFoundException.class, () -> root.lookup("java:comp/env/missing"));
    }

    @Test
    void rebindOverwritesExistingBinding() throws NamingException {
        root.bind("key", "value1");
        root.rebind("key", "value2");
        assertEquals("value2", root.lookup("key"));
    }

    @Test
    void unbindRemovesEntry() throws NamingException {
        root.bind("key", "value");
        root.unbind("key");
        assertThrows(NameNotFoundException.class, () -> root.lookup("key"));
    }

    @Test
    void duplicateBindThrowsNamingException() throws NamingException {
        root.bind("key", "value");
        assertThrows(NamingException.class, () -> root.bind("key", "other"));
    }

    @Test
    void renameMovesBinding() throws NamingException {
        root.bind("old", "data");
        root.rename("old", "new");
        assertEquals("data", root.lookup("new"));
        assertThrows(NameNotFoundException.class, () -> root.lookup("old"));
    }

    @Test
    void lookupSubcontextReturnsContext() throws NamingException {
        root.bind("java:comp/env/jdbc/ds1", "datasource1");
        root.bind("java:comp/env/jdbc/ds2", "datasource2");

        Object sub = root.lookup("java:comp/env/jdbc");
        assertInstanceOf(Context.class, sub);
    }

    @Test
    void listReturnsBindingsUnderPrefix() throws NamingException {
        root.bind("java:comp/env/a", "1");
        root.bind("java:comp/env/b", "2");
        root.bind("other/c", "3");

        var names = root.list("java:comp/env/");
        int count = 0;
        while (names.hasMore()) {
            names.next();
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    void initialContextFactoryProvidesSharedRoot() throws NamingException {
        // Bind via factory's static method
        VeloInitialContextFactory.root().rebind("test/factoryKey", "factoryValue");

        // Lookup via InitialContext with explicit factory
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "io.velo.was.jndi.VeloInitialContextFactory");
        Context ctx = new InitialContext(env);
        assertEquals("factoryValue", ctx.lookup("test/factoryKey"));

        // Cleanup
        VeloInitialContextFactory.root().unbind("test/factoryKey");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Connection pool tests (using H2 in-memory DB)
    // ────────────────────────────────────────────────────────────────────────

    @Test
    void connectionPoolBorrowAndRelease() throws SQLException {
        try (SimpleConnectionPool pool = SimpleConnectionPool.builder("jdbc:h2:mem:testpool1;DB_CLOSE_DELAY=-1")
                .driverClassName("org.h2.Driver")
                .minIdle(1)
                .maxPoolSize(5)
                .build()) {

            assertEquals(1, pool.idleCount());

            Connection conn = pool.borrow();
            assertNotNull(conn);
            assertFalse(conn.isClosed());
            assertEquals(1, pool.activeCount());
            assertEquals(0, pool.idleCount());

            pool.release(conn);
            assertEquals(0, pool.activeCount());
            assertEquals(1, pool.idleCount());
        }
    }

    @Test
    void connectionPoolReusesConnections() throws SQLException {
        try (SimpleConnectionPool pool = SimpleConnectionPool.builder("jdbc:h2:mem:testpool2;DB_CLOSE_DELAY=-1")
                .driverClassName("org.h2.Driver")
                .minIdle(0)
                .maxPoolSize(2)
                .build()) {

            Connection conn1 = pool.borrow();
            pool.release(conn1);

            Connection conn2 = pool.borrow();
            // conn2 should be the same underlying connection reused
            assertNotNull(conn2);
            pool.release(conn2);

            assertEquals(1, pool.totalCount());
        }
    }

    @Test
    void connectionPoolRespectsMaxPoolSize() throws SQLException {
        try (SimpleConnectionPool pool = SimpleConnectionPool.builder("jdbc:h2:mem:testpool3;DB_CLOSE_DELAY=-1")
                .driverClassName("org.h2.Driver")
                .minIdle(0)
                .maxPoolSize(2)
                .borrowTimeoutMs(500)
                .build()) {

            Connection c1 = pool.borrow();
            Connection c2 = pool.borrow();
            assertEquals(2, pool.activeCount());

            // Third borrow should timeout because pool is exhausted
            assertThrows(SQLException.class, () -> pool.borrow());

            pool.release(c1);
            pool.release(c2);
        }
    }

    @Test
    void connectionPoolValidationQuery() throws SQLException {
        try (SimpleConnectionPool pool = SimpleConnectionPool.builder("jdbc:h2:mem:testpool4;DB_CLOSE_DELAY=-1")
                .driverClassName("org.h2.Driver")
                .minIdle(1)
                .maxPoolSize(5)
                .validationQuery("SELECT 1")
                .build()) {

            Connection conn = pool.borrow();
            assertNotNull(conn);
            pool.release(conn);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // PooledDataSource tests
    // ────────────────────────────────────────────────────────────────────────

    @Test
    void pooledDataSourceGetConnectionAndQuery() throws SQLException {
        SimpleConnectionPool pool = SimpleConnectionPool.builder("jdbc:h2:mem:testds1;DB_CLOSE_DELAY=-1")
                .driverClassName("org.h2.Driver")
                .minIdle(1)
                .maxPoolSize(5)
                .build();

        try (PooledDataSource ds = new PooledDataSource("testDS", pool)) {
            // Create a table and insert data
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS test_table (id INT PRIMARY KEY, name VARCHAR(50))");
                stmt.execute("INSERT INTO test_table VALUES (1, 'velo')");
            }

            // Query the data using a new connection (should be reused from pool)
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT name FROM test_table WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("velo", rs.getString("name"));
            }

            assertEquals("testDS", ds.name());
            assertEquals(0, ds.activeConnections()); // all connections returned
            assertTrue(ds.idleConnections() > 0);
        }
    }

    @Test
    void pooledConnectionCloseReturnsToPool() throws SQLException {
        SimpleConnectionPool pool = SimpleConnectionPool.builder("jdbc:h2:mem:testds2;DB_CLOSE_DELAY=-1")
                .driverClassName("org.h2.Driver")
                .minIdle(0)
                .maxPoolSize(1)
                .build();

        try (PooledDataSource ds = new PooledDataSource("testDS2", pool)) {
            Connection conn = ds.getConnection();
            assertEquals(1, ds.activeConnections());

            conn.close(); // returns to pool, not actually closed
            assertEquals(0, ds.activeConnections());
            assertEquals(1, ds.idleConnections());

            // Should be able to get a connection again (reused)
            Connection conn2 = ds.getConnection();
            assertNotNull(conn2);
            conn2.close();
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // JNDI + DataSource integration
    // ────────────────────────────────────────────────────────────────────────

    @Test
    void jndiLookupDataSource() throws Exception {
        SimpleConnectionPool pool = SimpleConnectionPool.builder("jdbc:h2:mem:jnditest;DB_CLOSE_DELAY=-1")
                .driverClassName("org.h2.Driver")
                .minIdle(1)
                .maxPoolSize(5)
                .build();
        PooledDataSource ds = new PooledDataSource("myDB", pool);

        try {
            root.bind("java:comp/env/jdbc/myDB", ds);

            // Simulate what a servlet would do
            DataSource lookedUp = (DataSource) root.lookup("java:comp/env/jdbc/myDB");
            assertNotNull(lookedUp);
            assertInstanceOf(PooledDataSource.class, lookedUp);

            try (Connection conn = lookedUp.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE jndi_test (id INT)");
                stmt.execute("INSERT INTO jndi_test VALUES (42)");
                try (ResultSet rs = stmt.executeQuery("SELECT id FROM jndi_test")) {
                    assertTrue(rs.next());
                    assertEquals(42, rs.getInt("id"));
                }
            }
        } finally {
            ds.close();
        }
    }
}
