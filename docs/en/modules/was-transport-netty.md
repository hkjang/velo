# `was-transport-netty` Module Guide

The `was-transport-netty` module is the foundation of network ingestion. It utilizes Netty to bind to network interfaces, manage low-level socket options, and coordinate traffic into the system.

## Key Responsibilities

### 1. Transport Selection (`NativeTransportSelector`)
Velo WAS strives for maximum performance. Instead of always defaulting to Java's `NIO`, the engine inspects the host operating system.
- **Linux**: Loads the `Epoll` extensions for edge-triggered multiplexing.
- **macOS / BSD**: Drops down to `KQueue` extensions.
- **Windows / Others**: Falls back to the universally compatible standard `NIO`.
The `bossGroup` and `workerGroup` EventLoops are correctly sized based on the `Threading` properties mapped in the `was-config` module.

### 2. The `NettyServer` Lifecycle
This is the main orchestrator class.
- It initializes the `ServerBootstrap` class with socket backlogs, keep-alive states, and TCP no-delay flags.
- **Booting**: It binds securely (using `.sync()`) preventing the application from starting if a port is in use.
- **Graceful Shutdown**: It utilizes Netty's shutdown hooks. During server closure via `close()`, the EventLoops are commanded to gracefully reject new connections but flush existing workloads before tearing down memory.

### 3. Securing Pipelines (TLS & ALPN)
If TLS is enabled in configuration:
- The pipeline inserts Netty's `SslHandler`.
- `ReloadingSslContextProvider` manages the `SslContext`. A background worker periodically inspects the filesystem modification times of configured PEM/Keystore files. If updated, the context is swapped safely. Existing connections retain their old encryption state while newly accepted connections negotiate using the updated certificates.
- The pipeline utilizes **ALPN (Application-Layer Protocol Negotiation)** via `AlpnNegotiationHandler` to seamlessly upgrade browsers capable of HTTP/2 (`h2`) communication, while falling back gracefully to `http/1.1`.

### 4. Cleartext Pipelines (`h2c`)
If TLS is disabled, the module configures `CleartextHttp2ServerUpgradeHandler`. Clients can send an `Upgrade: h2c` header or utilize HTTP/2 Prior Knowledge (sending the connection preface `PRI *`) to transparently bypass HTTP/1.1 processing limits on port 80/8080.
