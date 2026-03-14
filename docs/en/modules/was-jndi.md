# `was-jndi` Module Guide

The `was-jndi` module establishes the Java Naming and Directory Interface (JNDI) backbone for Velo WAS. It allows Servlet applications to securely lookup data sources and connection pools defined centrally by administrators via the `server.yaml` file, without hardcoding connection strings.

## Key Components

### 1. `VeloNamingContext` & `VeloInitialContextFactory`
The central registry for all environment (`java:comp/env/`) variables and resource references within the server footprint.
- Applications interface with this through standard `new InitialContext().lookup("jdbc/mydb")` calls.
- Provides isolated JNDI sub-contexts depending on the currently executing web application context.

### 2. `SimpleConnectionPool` & `PooledDataSource`
An integrated lightweight JDBC Connection Pool manager.
- **Resource Management**: Provides pooling semantics allowing Velo WAS to effectively cap maximum outbound connections, maintain idle connections, and reap stalled databases.
- Integrates gracefully into the JNDI Context so applications only see a standard `javax.sql.DataSource`.
- Handled actively by `was-admin` CLI commands (`reset-connection-pool`, `flush-connection-pool`, and `jdbc-resource-info`).
