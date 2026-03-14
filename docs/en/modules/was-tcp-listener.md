# `was-tcp-listener` Module Guide

The `was-tcp-listener` module expands Velo WAS far beyond a standard HTTP application server by supporting raw, stateful, and customizable TCP connections using Netty's robust pipeline architecture.

## Key Components

### 1. Codec and Ingestion (`MessageCodec`)
TCP traffic often lacks the structured headers of HTTP. Velo WAS provides an extensible `MessageCodec` and `FrameDecoderFactory` system that splits continuous byte streams into discrete `TcpMessage` records.
- Connectors can be configured via `TcpListenerConfig` for `RAW`, `LINE`, `DELIMITER`, or `LENGTH_FIELD` packet extraction.
- Handled actively via `DefaultMessageCodec` during bootstrapping.

### 2. TCP Lifecycle (`TcpListenerServer` / `TcpListenerManager`)
The `TcpListenerManager` governs instances of `TcpListenerServer`, each binding a different port independent of the HTTP listener cluster.
- **Isolation**: Custom Thread pools (`TcpHandlerExecutor`) prevent TCP logic from blocking HTTP threads.

### 3. Connection Security & Limits
- **`TcpRateLimiter`**: Denies abusive IP connections using token buckets.
- **`TcpSecurityHandler`**: Enforces strict CIDR allowlist/denylist rules directly upon a `SocketChannel` initialization, preventing unauthorized subnets from consuming backend memory.

### 4. Router & Session Handlers
- **`TcpMessageRouter`**: Routes structured incoming bytes matching custom criteria to application-level handlers.
- **`TcpSession` / `TcpSessionManager`**: TCP is inherently stateful over long periods. This module provides a distinct TCP Session tracker mapping identifiers to active channel channels, managing timeouts and disconnect events asynchronously.

### 5. Management Control (`TcpListenerAdmin`)
Exemplifying JEUS-like centralized control, metrics specific to the `was-tcp-listener` are routed through `TcpListenerAdminMBean`, logging `TcpAccessLog` and generic statistics so that operators can intervene, track throughput (`TcpMetrics`), and close rogue socket sessions from the `was-admin` interface.
