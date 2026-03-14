# Velo WAS Detailed Architecture Guide

This document delves into the internal technical design and module interactions of Velo WAS. While `architecture.md` provides a high-level overview, this document dissects how standard HTTP requests are ingested, translated, and ultimately served by standard Jakarta Servlet applications.

## 1. Core Module Responsibilities

The system is separated into multiple Maven modules to ensure strict boundary enforcement between network transmission code and the Servlet application logic.

### 1.1. `was-transport-netty` (Network Ingestion)
This module acts as the sheer physical entry point bounds. Its primary responsibilities include:
- Establishing Server Sockets and binding ports using native transport (`epoll`, `kqueue`, or standard `NIO`) via `NativeTransportSelector`.
- Initializing the Netty `ChannelPipeline`. 
- Handling TLS setups using `ReloadingSslContextProvider`. It supports dynamic hot-reloading of X.509 certificates without dropping existing active connections.
- HTTP version negotiation: Implements Application-Layer Protocol Negotiation (ALPN) to upgrade secure connections to HTTP/2. For clear-text (HTTP/1.1), it uses `CleartextHttp2ServerUpgradeHandler` to support `h2c` upgrades.

### 1.2. `was-protocol-http` (Protocol Abstraction)
Once bits fall through the Netty codecs, they arrive in this middleware abstraction layer. Netty's HTTP objects are too closely tied to Netty's lifecycle. Thus, Velo WAS shields the business logic by utilizing an intermediary `HttpExchange` record.
- **`HttpExchange`**: A neutral data carrier enveloping the request method, URI, headers, body, remote address, and providing a unified structure not tightly coupled to any specific Netty handler.
- **`HttpHandlerRegistry`**: A lightweight exact-match routing engine used for built-in administrative or observability endpoints (such as `/health` or `/info`).
- **`NettyHttpChannelHandler`**: A Netty-native `SimpleChannelInboundHandler` that converts Netty `FullHttpRequest`s into the intermediary `HttpExchange`, evaluates whether it matches a built-in handler on the `HttpHandlerRegistry`, and if not, passes it down to a configured `ServletContainer` fallback.

### 1.3. `was-servlet-core` (The Jakarta Servlet Layer)
This is the heart of Velo WAS's compliance engine, implementing the core routing to user-deployed applications.
- **`SimpleServletContainer`**: Manages all lifecycle events for multiple deployed `ServletApplication`s. It utilizes the incoming Request URI to determine which Context Path the application resides at and finds the matching app.
- **Proxy-Based Interface Emulation (`ServletProxyFactory`)**: To avoid creating massive boilerplate adapter classes for `HttpServletRequest` and `HttpServletResponse`, Velo WAS utilizes JDK Dynamic Proxies (`java.lang.reflect.Proxy`). Method invocations to the Jakarta interfaces are dynamically trapped and routed to the corresponding accessors on the intermediate `HttpExchange` payload.
- **Servlet Request Lifecycle**:
  1. Locates target `Servlet` within the mapped `ServletApplication`.
  2. Constructs the session (parsing `JSESSIONID` map via `SessionState`).
  3. Prepares the `SimpleFilterChain`.
  4. Triggers `ServletRequestListener.requestInitialized()`.
  5. Evaluates Servlet Filters.
  6. Invokes `Servlet.service()`.
  7. Triggers `ServletRequestListener.requestDestroyed()`.

## 2. Deep Dive: Dynamic Proxy Request Mapping

Unlike Apache Tomcat that generates large concrete implements of `HttpServletRequest` (e.g., `RequestFacade`), Velo WAS routes API calls dynamically.
For example, when an application invokes `request.getHeader("Authorization")`, the `ServletProxyFactory` intercepts this call. The interceptor switches on the invoked method's name (`"getHeader"`) and runs the appropriate routine against the inner `HttpExchange`'s Netty HTTP Headers. Unimplemented methods return safe defaults or throw `UnsupportedOperationException`.

## 3. Request Dispatcher (Internal Bridge)

When applications use `request.getRequestDispatcher("/other").forward(req, res)` or `.include(req, res)`, Velo WAS must halt or fork execution internally.
This is achieved via the `InternalRequestBridge` and `InternalResponseBridge` interfaces. 
- The proxy interceptor recognizes the `getRequestDispatcher()` call and resolves the relative/absolute target path (cleaning up relative segments like `..`).
- In a `forward` instruction, the output buffer in the original response wrapper is wiped clean, and the underlying pipeline executes a fresh chain lookup for the target path.
- In an `include` instruction, the current response state is preserved, and the execution of the included sub-servlet flushes strictly to the inherited output stream.

## 4. Session State Mechanics

To maintain state, Velo WAS implements an `InMemoryHttpSessionStore`.
- A `SessionState` wrapper encapsulates the attributes, creation time, and last access metadata.
- Sessions are keyed by a dynamically generated UUID (without hyphens) exposed to the client via a standard `JSESSIONID` HTTP-Only cookie.
- Automatic Eviction: In future iterations or implementations, the idle elapsed time (`lastAccessedTime` vs `System.currentTimeMillis()`) is checked against the configured `maxInactiveIntervalSeconds`. During request ingestion, expired sessions are structurally invalidated and purged.
