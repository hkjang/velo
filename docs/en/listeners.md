# Velo WAS Listeners Guide

Velo WAS serves traffic via multiple concurrent listeners. A "Listener" conceptually maps to an open server socket bound to a specific port and protocol configuration. Velo WAS supports two major categories of listeners: HTTP(S) and raw TCP.

## 1. The HTTP Listener (`server.listener`)

This is the primary listener initialized during server startup, managed inside the `was-transport-netty` module.
It handles all web application context traffic, administrative REST endpoints, and WebSocket upgrades.

### Core Capabilities
- **ALPN & HTTP/2 Support**: Capable of dynamically negotiating HTTP version based on TLS certificates, preventing the need for multiple open ports for different protocols.
- **Cleartext `h2c`**: Allows HTTP/1.1 traffic to upgrade to HTTP/2 natively over port `80` or `8080` without encryption.
- **Servlet Container Integration**: Converts Netty `FullHttpRequest` objects seamlessly into `HttpExchange` records for processing by the Jakarta Servlet implementation (`was-servlet-core`).
- **Dynamic TLS**: Facilitated by `ReloadingSslContextProvider`, adding or modifying certificate files does not require an active server reboot.

### Configuration (`server.yaml`)
```yaml
server:
  listener:
    host: 0.0.0.0
    port: 8080
    maxContentLength: 10485760 # max request body size
  tls:
    enabled: true
    mode: PEM
    certChainFile: /cert/server.crt
    privateKeyFile: /cert/server.key
```

## 2. The TCP Listeners (`server.tcpListeners`)

Managed entirely by the `was-tcp-listener` module, TCP Listeners provide Velo WAS with gateway capabilities. This permits applications to communicate beyond structured HTTP APIs.

### Core Capabilities
- **Protocol Agnostic**: Capable of framing TCP streams using `RAW`, `LINE`, `DELIMITER`, or `LENGTH_FIELD` logic (`FrameDecoderFactory`).
- **Granular Security Controls**: Supports independent connection rate limiting (`perIpRateLimit`), idle connection culling timeouts, and custom IP-based CIDR Access Control Lists (`allowedCidrs`/`deniedCidrs`).
- **Isolated Threading**: Uses dedicated `workerThreads` and `businessThreads`. Heavy TCP background traffic will not starve the main HTTP threads serving enterprise web applications.

### Configuration (`server.yaml`)
```yaml
server:
  tcpListeners:
    - name: "custom-tcp-gateway"
      host: 0.0.0.0
      port: 9090
      framing: LENGTH_FIELD
      maxConnections: 5000
      businessThreads: 4
```

## Observing Listeners in `velo-admin`

Using the CLI, system operators can query the current socket bounds and metric behaviors of all underlying listeners transparently.

- **`server-info`**: Validates the HTTP port binding, TLS status, and HTTP/2 integrations.
- **`resource-info`**: Aggregates throughput data, actively charting max connections against current Netty thread availability.
