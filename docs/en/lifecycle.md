# Velo WAS Lifecycle & IPL Guide

This guide details the Initial Program Load (IPL), graceful shutdown, restart procedures, and monitoring mechanisms for Velo WAS. Understanding the lifecycle is crucial for maintaining zero-downtime deployments and ensuring system integrity.

## 1. Initial Program Load (IPL) / Startup

The startup phase of Velo WAS involves initializing the configuration, binding network listeners, establishing cluster topologies, and deploying the initial applications.

### 1.1 Command Line Startup
Typically, the server is started via a startup script that invokes the `was-bootstrap` module.

```bash
# Start with default configuration (conf/server.yaml)
java -jar was-bootstrap.jar

# Start with a specific configuration file
java -jar was-bootstrap.jar conf/production.yaml
```

### 1.2 Boot Sequence (What happens during IPL)
1. **Configuration Resolution**: Parses the YAML configuration to build the `ServerConfiguration` POJO.
2. **Subsystem Initialization**: 
   - Starts the `was-observability` module (Access, Error, and Audit logs).
   - Initializes JNDI Contexts and Connection Pools (`was-jndi`).
3. **Servlet Container Boot**: Instantiates `SimpleServletContainer`.
4. **Application Deployment**: Parses `WEB-INF/web.xml` and deploys static/configured WAR files into the container (via `was-deploy`).
5. **Network Binding**: The `was-transport-netty` module binds to the configured OS ports (HTTP/TCP).
   - *Note*: If the port is already in use, the server will intentionally fail to start (`BindException`) to prevent silent failures.
6. **Ready State**: The server logs a "Started" message and begins accepting external traffic. *(Admin CLI commands can now attach)*.

## 2. Graceful Shutdown

Velo WAS implements a graceful shutdown sequence to ensure that in-flight requests are completed and transactions are not abruptly terminated.

### 2.1 Triggering a Shutdown
A shutdown can be triggered via:
- **OS Signal**: Sending a `SIGTERM` (e.g., `kill -15 <PID>`) or pressing `Ctrl+C`.
- **Admin CLI**: Executing `stop-server <server-name>` from `velo-admin`.

### 2.2 Shutdown Sequence
1. **Listener Rejection**: The Netty `bossGroup` immediately stops accepting *new* connections.
2. **In-Flight Processing**: Existing keep-alive connections are instructed to close gracefully after their current request completes.
3. **Grace Period**: The system waits for up to `gracefulShutdownMillis` (default: 30 seconds, configured in `server.yaml`).
   - If requests finish before this timeout, the shutdown continues immediately.
   - If the timeout is reached, the `workerGroup` forcefully terminates remaining threads to prevent hanging.
4. **Subsystem Teardown**: 
   - Connection pools are flushed.
   - Applications are undeployed, executing `ServletContextListener.contextDestroyed()`.
   - The JVM exits with status code `0`.

## 3. Server Suspension and Resumption

Sometimes a full server restart is unnecessary if you only need to halt traffic temporarily (e.g., investigating a database issue).

- **Suspend (`suspend-server <name>`)**: Instructs the Netty pipelines to pause incoming reads. The server process remains alive, and existing state is maintained, but new traffic will hang or be rejected depending on the OS backlog.
- **Resume (`resume-server <name>`)**: Re-enables the network listeners to process the queued backlog of requests.

## 4. Restarting the Server

A restart is essentially a sequential execution of a Graceful Shutdown followed by an IPL.

### 4.1 Using the Admin CLI
```shell
velo> restart-server <server-name>
```
*Note*: If the Admin CLI is connected locally, it will lose connection briefly while the JVM restarts.

### 4.2 Zero-Downtime Rolling Restarts (Cluster)
For highly available environments, Velo WAS nodes should be placed behind a load balancer. 
1. Use `suspend-server node-1` so the load balancer stops routing traffic to it.
2. Wait for active connections to drain.
3. Run `restart-server node-1` or apply OS-level patches.
4. Once IPL completes, verify health using the `/health` endpoint.
5. Repeat for `node-2`, etc.

## 5. Lifecycle Monitoring

You can verify the status and health of the IPL and operations using several methods:

### 5.1 Built-in Endpoints
Velo WAS exposes lightweight, non-servlet endpoints by default (configured in `HttpHandlerRegistry`):
- `http://<host>:<port>/health`: Returns HTTP 200 OK if the EventLoops are successfully bound and running.
- `http://<host>:<port>/info`: Returns basic build version and node identification metrics.

### 5.2 Admin CLI Monitoring
Monitor the active lifecycle state using `velo-admin`:
- **`server-info`**: Displays `uptimeMillis` (time since IPL completed) and current `status` (STARTING, RUNNING, SUSPENDED, STOPPING).
- **`resource-info`**: Validates whether background connection pools initialized correctly during the IPL phase.
