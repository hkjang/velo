# Velo WAS Administration Guide

Velo WAS provides a robust, JEUS-compatible interactive command-line interface called `velo-admin`. This Admin CLI equips system administrators with the necessary tools to monitor, configure, and manage WAS processes systematically.

## 1. Entering the Admin Console

The `velo-admin` application uses JLine to provide an interactive shell environment for executing administrative commands.
By default, you can start the console locally:
```bash
java -cp was-admin.jar io.velo.was.admin.VeloAdmin
```

If you wish to pass a specific `server.yaml` config file, you can append it:
```bash
java -cp was-admin.jar io.velo.was.admin.VeloAdmin conf/server.yaml
```

Once loaded, you will see a `velo>` prompt awaiting commands.

## 2. Server & Cluster Management

Administrators can handle server lifecycles, and coordinate cluster environments.

- **`list-servers` / `server-info <name>`**: Shows the current statuses and underlying node layouts for WAS servers.
- **`start-server <name>` / `stop-server <name>`**: Controls server startup and graceful shutdowns.
- **`restart-server <name>`**: Restarts a server process entirely.
- **`suspend-server <name>` / `resume-server <name>`**: Suspends and resumes traffic processing momentarily.
- **`list-clusters` / `cluster-info <name>`**: Enumerates running clusters and their respective configurations.
- **`start-cluster <name>` / `stop-cluster <name>`**: Manage lifecycles across an entire cluster grouping.

## 3. Application Operational Management

Velo WAS applications (`.war` deployments) are deployed to contexts path endpoints.

- **`deploy <war-path> <context-path>`**: Allows dynamic onboarding of a new web application.
- **`undeploy <name>`**: Undeploys the application context safely.
- **`start-application <name>` / `stop-application <name>`**: Effectively turns on or switches down an entire application's processing endpoints without removing definitions.

## 4. Monitoring Resources

The Admin CLI integrates cleanly with Velo WAS management backends, allowing operators to monitor deep server telemetry.

- **`system-info`**: Extracts OS metrics, available cores, architectures.
- **`jvm-info`**: Reviews up-time, runtime arguments, and native vendor versions.
- **`memory-info`**: Real-time snapshots of Heap and Non-Heap allocations.
- **`thread-info`**: Observes deadlock situations and live thread accumulations.
- **`resource-info`**: Produces a summary layout including CPU, RAM, connection pools (Datasource/JDBC) and JMS targets.

## 5. Security Access & Logging

- Use `list-users`, `create-user`, `remove-user`, and `change-password` contexts to secure administrative access environments.
- Administrators can review the roles defined centrally using `list-roles` to maintain policy integrity.
- Logging levels limit debugging footprint in Production contexts. Target individual loggers with `get-log-level <name>` and enforce changes using `set-log-level <name> <level>` (for instance, `DEBUG`, `INFO`, `OFF`).

## 6. Automation Scripts

You can build `.velo` script records for automation:
1. `record-script <filepath>`: Initiates tracking the typed commands.
2. `stop-record`: Saves tracked commands to the provided path.
3. `run-script <filepath>`: Re-executes commands from an existing .velo macro.
