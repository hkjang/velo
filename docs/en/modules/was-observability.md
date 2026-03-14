# `was-observability` Module Guide

The `was-observability` module provides the core logging and auditing capabilities for Velo WAS. It separates operational logging traces into distinct, manageable streams.

## Key Components

### 1. `AccessLog` / `AccessLogEntry`
Records standard HTTP access requests (similar to Tomcat's `localhost_access_log`).
- Logs the client IP, request URI, HTTP method, response status code, and the time taken to process the request.
- Useful for traffic analysis, billing metrics, and anomaly detection.

### 2. `ErrorLog` / `ErrorLogEntry`
Captures unhandled exceptions and HTTP 500-level sequence failures.
- Captures stack traces securely without exposing them directly to the end user.
- Provides contextual information about the failed `HttpExchange`.

### 3. `AuditLog` / `AuditLogEntry`
Tracks administrative and lifecycle events within the server.
- Used extensively by the `was-admin` module to record configuration modifications, application deployment lifecycles, and security changes (e.g., role adjustments).
- Essential for enterprise compliance and security tracing.
