# Hot Deploy Guide

`velo-was` provides a hot deploy feature that allows you to deploy, update, or undeploy applications continuously without restarting the server.

## Configuration
You can enable and configure the hot deploy feature in the `deploy` section of the `conf/server.yaml` file.

```yaml
server:
  deploy:
    directory: deploy             # Target directory for deployment (default: deploy)
    hotDeploy: true               # Enable hot deploy (change to true)
    scanIntervalSeconds: 5        # Directory watch debounce interval in seconds
```

## How It Works
Internally, `velo-was` uses `HotDeployWatcher` and Java NIO's `WatchService` to monitor changes to `.war` files or exploded directories in the specified `directory`.

- **Deploy (ENTRY_CREATE)**: When a new `.war` file is detected, it is automatically instantiated and deployed.
- **Redeploy (ENTRY_MODIFY)**: When an existing `.war` file is overwritten, the changes are detected, the current application is undeployed, and the new version is redeployed.
- **Undeploy (ENTRY_DELETE)**: When a `.war` file or a directory is removed from the deployment directory, the corresponding application is automatically undeployed without delay.

## Important Notes
- **Debounce Interval**: To prevent deploying incomplete files while a large WAR file is being copied, the server waits for the duration specified in `scanIntervalSeconds` with no further file changes before proceeding with the deployment. Ensure this value is sufficiently large for your deployment file sizes.
- **ClassLoader Isolation**: Upon hot redeployment, the previous `WebAppClassLoader` is closed and removed from JVM memory, and a new one is created to initialize the application. To prevent memory leaks between deployments, ensure your application properly cleans up resources like `ThreadLocal` variables during destruction (e.g., inside `ServletContextListener.contextDestroyed()`).
