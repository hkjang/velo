# `was-servlet-core` Module Guide

The `was-servlet-core` module implements the Jakarta Servlet API subset. It wraps the raw incoming requests created by `was-protocol-http` in standardized Servlet contexts, emulating the environment of traditional application servers without their bloated footprint.

## Key Abstractions

### 1. Dynamic JDK Proxies (`ServletProxyFactory`)
Rather than explicitly building dozens of concrete implementations for every interface found in the Jakarta EE specification, `was-servlet-core` takes a dynamic proxy route (`java.lang.reflect.Proxy`). Method invocations to the Jakarta interfaces are dynamically trapped and routed to the corresponding accessors on the intermediate `HttpExchange` payload.

- **Request / Response**: For `HttpServletRequest` and `HttpServletResponse`, the `Method.getName()` invoked by the user app is mapped via a switch expression. For example, `.getHeader("Host")` routes back to `HttpExchange.request.headers().get("Host")`.
- **Context / Config**: Similar proxies simulate `ServletContext`, `FilterConfig`, and `ServletConfig`. Initialization parameters and context paths are stored locally in the proxy handler.
- **Unimplemented Fallback**: Any method not explicitly supported defaults automatically to avoid throwing unexpected exceptions, returning safe values like `null`, `false`, `0`, or empty collections whenever logically permissible.

### 2. Traffic Flow (`SimpleServletContainer`)
The `ServletContainer` acts as the overarching gateway manager for deployed `ServletApplication`s.
- `deploy()`: Binds a specific domain logical unit (such as a WAR context path) to the container.
- `handle()`: Incoming network paths are stripped and mapped against the registered context paths. If matched, the appropriate application executes its `SimpleFilterChain`.
- If no application context matches the request base, a default HTTP 404 (Not Found) is rendered directly.

### 3. State Management (`SessionState` & `InMemoryHttpSessionStore`)
By default, standard sessions are completely in-memory.
- Session structures are captured within an internal data dictionary (`ConcurrentHashMap`).
- Identifiers (`JSESSIONID`) are assigned securely via UUIDs (minus hyphens).
- The HTTP cookie generation occurs at the framework level and maintains `HttpOnly` attributes for security.
- The state map (`SessionState`) tracks `creationTime`, `lastAccessedTime`, and evaluates the `maxInactiveIntervalSeconds` for background expirations.

### 4. Intra-App Dispatching
For routing that originates internally (`RequestDispatcher`), special proxy interfaces (`InternalRequestBridge` and `InternalResponseBridge`) interact directly with the pipeline:
- `forward()` drops the inherited response stream output buffer contents, shifts the URI pointer, and re-triggers the internal routing logic seamlessly.
- `include()` preserves the response buffer output stream but forks out temporarily to gather the sub-servlet's payload before resolving back natively.
