# Velo WAS Admin CLI (`velo-admin`) Command Reference

The `velo-admin` is the central command-line interface for managing Velo WAS instances. Designed with compatibility and ease of use in mind, it provides a comprehensive suite of tools to control server lifecycles, inspect JVM metrics, trace active connections, and adjust configurations dynamically.

## 1. Entering the CLI Environment

To start the interactive CLI session, execute the admin jar:
```bash
java -jar was-admin.jar
```
You will enter the `velo>` prompt, which features JLine-powered auto-completion (via the `<TAB>` key) and command history.

## 2. General Commands

- `help`: Lists all available command categories and commands.
- `help <command>`: Provides detailed usage instructions for a specific command.
- `clear`: Clears the terminal screen.
- `exit` / `quit`: Exits the `velo-admin` session.
- `version`: Displays the Velo WAS Admin CLI version.

## 3. Server Node Management

Commands to inspect and control the lifecycle of the Velo WAS server instance.

- **`server-info`**: Displays the core metadata of the connected server, including node ID, bound HTTP/TCP ports, TLS mode, and current state (e.g., `RUNNING`).
- **`suspend-server <name>`**: Temporarily stops the server from accepting new connections while preserving existing ones. Useful during load-balancer drain states.
- **`resume-server <name>`**: Re-enables connection accepting on a previously suspended server.
- **`stop-server <name>`**: Initiates a graceful shutdown of the target server node.
- **`restart-server <name>`**: Performs a clean stop followed by an immediate start of the server JVM.

## 4. Application Deployment and Operations

Commands to manage web application contexts (`.war` deployments).

- **`app-info`**: Lists all actively deployed web application contexts, showing their bound context paths (e.g., `/api`) and deployment statuses.
- **`deploy <path-to-war> [context-path]`**: Deploys a new web application from a local WAR file archive to the specified context path.
- **`undeploy <context-path>`**: Removes the application bound to the path, destroying its `WebAppClassLoader` and halting all its traffic routing.
- **`reload <context-path>`**: Safely recycles the application context by undeploying and subsequently redeploying it, useful for updating class files without a full server restart.

## 5. System Observability and Metrics

These commands peer deep into the JVM and Netty subsystems.

- **`memory-info`**: Prints the current JVM heap and non-heap memory utilization (Used vs. Committed vs. Max).
- **`thread-info`**: Dumps an active profile of threads. You can see Netty `boss/worker` threads and any active HTTP request processing threads.
- **`resource-info`**: Provides statistics on currently active HTTP connections, TCP gateway socket limits, and background Thread Pool exhaustions.

## 6. Logging and Verbosity

Manage how verbose Velo WAS outputs its internal traces.

- **`log-level`**: Displays the current foundational logging level of the system.
- **`set-log-level <level>`**: Dynamically adjusts the SLF4J/Logback instrumentation at runtime. Valid levels: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`. (e.g., `set-log-level DEBUG`).

## 7. Security and Access

Manage user authentication and administrative privileges.

- **`create-user <username> <password>`**: Provisions a new administrative user inside the internal directory.
- **`remove-user <username>`**: Deletes a user account.
- **`change-password <username> <new-password>`**: Updates credentials for an existing user.
- **`list-roles`**: Displays the active Role-Based Access Control (RBAC) groups configured (e.g., `admin`, `operator`, `monitor`).

## 8. Automation and Scripts

The CLI supports macro recordings for repetitive administrative tasks.

- **`record-script <filepath.velo>`**: Starts recording all subsequent commands entered into the CLI into the defined text file.
- **`stop-record`**: Finishes the current recording session and flushes the buffer to disk.
- **`run-script <filepath.velo>`**: Executes a previously recorded or manually written script sequentially without human intervention. Ideal for automated CI/CD pipeline deployments.
