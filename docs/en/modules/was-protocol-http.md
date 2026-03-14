# `was-protocol-http` Module Guide

This module bridges the physical network frames with the logical application concepts. Netty's HTTP codecs natively produce chunks of data that are closely tied to Netty's ByteBuf and Channel mechanics. The `was-protocol-http` module acts as a buffer layer to protect the higher-level Servlet APIs from depending directly on Netty specifics.

## Key Interfaces and Classes

### 1. `HttpExchange` Record
At the core of this module is the `HttpExchange` record. It encapsulates the complete lifecycle of a single HTTP traversal.
- It stores the `FullHttpRequest` containing headers, the payload, and URI paths.
- It maintains the physical connection metadata: `remoteAddress` and `localAddress`.
- It carries a `ResponseSink` dependency which is used back in the pipeline to emit responses.

### 2. Built-in Abstract Handlers
Before routing requests to the heavy Servlet engine, the `was-protocol-http` module contains an `HttpHandlerRegistry`. 

#### 2.1 Pattern Matching
This registry is responsible for exact-match or prefix-match routing for internal engine endpoints. It acts as an extremely fast, lightweight router that bypasses the Servlet filters entirely. 

#### 2.2 Default Endpoints
Typically, endpoints like `/health` (liveness/readiness probes) and `/info` (system statistics) are registered here. Because they bypass proxy instantiation and Servlet contexts, they impose virtually no overhead on the server.

### 3. Netty integration (`NettyHttpChannelHandler`)
This handler lives at the end of Netty's clear-text or TLS pipelines.
1. It reads inbound Netty HTTP objects.
2. It aggregates chunks (using Netty's `HttpObjectAggregator` which is inserted prior in the pipeline) into a single standard message.
3. It constructs the `HttpExchange`.
4. It surveys the `HttpHandlerRegistry`. If a match is found, the built-in handler fires.
5. If no internal handler matches the path, it yields execution to the fallback interface, which is typically the `ServletContainer` connected from the `was-servlet-core` module.

### 4. WebSocket Abstractions
This module also houses a `WebSocketHandlerRegistry` and `NettyWebSocketFrameHandler`. 
When early upgrade negotiations occur (or routes match mapped WebSocket URIs), the pipeline is dynamically altered. Standard HTTP handlers are replaced by WebSocket frame handlers which abstract Netty's `WebSocketFrame` into higher-level `WebSocketSession` API events.
