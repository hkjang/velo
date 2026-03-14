# `was-config` Module Guide

The `was-config` module acts as the single source of truth for the server's runtime configuration. It is built as a pure Java POJO (Plain Old Java Object) library without external dependencies, ensuring it can be used cleanly across all other modules (such as the admin CLI or the Netty bootstrap tier) without introducing classpath conflicts.

## Key Components

### 1. `ServerConfiguration` Class
This is the root configuration class. It heavily utilizes nested static classes to represent different sections of the `server.yaml` file natively.

#### 1.1 `Server` Section
Contains global node identifiers.
- `name`: the display name of the WAS instance (e.g. `velo-was`).
- `nodeId`: A unique identifier for cluster topology (e.g. `node-1`).
- `gracefulShutdownMillis`: Time allowed for active requests to finish before the server forcefully terminates Netty threads. Default: 30 seconds.

#### 1.2 `Listener` Section (Default HTTP)
Configures the primary HTTP binding.
- `host` / `port`: Coordinates for the primary server socket (default `0.0.0.0:8080`).
- `soBacklog`: The maximum queue length for incoming connections.
- `reuseAddress` / `tcpNoDelay` / `keepAlive`: Standard TCP socket flags.
- `maxContentLength`: The maximum allowed size for an HTTP request body (e.g., file uploads). Default: 10MB.

#### 1.3 `Threading` Section
Manages the EventLoopGroup sizing for Netty.
- `bossThreads`: Number of threads accepting new connections. Usually `1` is sufficient.
- `workerThreads`: Number of Netty I/O threads processing read/writes. Default `0` delegates to Netty's CPU heuristic (usually `cores * 2`).
- `businessThreads`: (Reserved for future Async Servlet use) Threads for long-running blocking operations, freeing up Netty's I/O workers.

#### 1.4 `Tls` Section (Security)
Configures SSL/TLS for the listeners. 
- It points to PEM files or KeyStores without directly importing cryptography classes. 
- Contains a `reloadIntervalSeconds` field which is read by the transport module to handle hot-reloading of changing certificates.

#### 1.5 `Jsp` Section
Prepares the environment variables necessary for Jasper (the JSP compiler), such as the scratch directory where compiled servlets will be written and interval checking for JSP file modifications.

#### 1.6 `TcpListenerConfig` Section (Advanced Routing)
Defines additional TCP listener endpoints with customizable framing (e.g., Line-based, Raw, Length-Field) which allows Velo WAS to potentially act as a TCP gateway or proxy beyond standard HTTP.

## Validations
The POJO includes standard `validate()` methods asserting basic constraints (like `port > 0` and `maxContentLength > 0`). These validations throw `IllegalArgumentException` early during bootstrap to prevent cryptic runtime errors.
