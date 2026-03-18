# Build / Start / Stop Scripts Guide

Velo WAS provides platform-specific build, start, and stop scripts for Linux/macOS, Windows CMD, and Windows PowerShell.
All scripts are located in the `bin/` directory.

## Directory Structure

```
bin/
├── build.sh          # Linux / macOS build
├── build.bat         # Windows CMD build
├── build.ps1         # Windows PowerShell build
├── start.sh          # Linux / macOS start
├── start.bat         # Windows CMD start
├── start.ps1         # Windows PowerShell start
├── stop.sh           # Linux / macOS stop
├── stop.bat          # Windows CMD stop
└── stop.ps1          # Windows PowerShell stop
```

## Prerequisites

### Auto-detected Toolchain

Scripts first look for a project-local toolchain and fall back to system installations if not found.

| Tool | Local Path | System Fallback |
|------|-----------|----------------|
| JDK 21 | `.tools/jdk/jdk-21.0.10+7` | `JAVA_HOME` environment variable |
| Maven 3.9 | `.tools/maven/apache-maven-3.9.13` | `mvn` on PATH |

> **Important**: Velo WAS requires JDK 21+ due to use of Java 21 features (switch expressions, text blocks, etc.).

---

## Build Scripts

### Basic Usage

```bash
# Linux / macOS
./bin/build.sh

# Windows CMD
bin\build.bat

# Windows PowerShell
.\bin\build.ps1
```

### Options

| Option | sh | bat | ps1 | Description |
|--------|-----|-----|-----|-------------|
| Clean | `-c`, `--clean` | `-c`, `--clean` | `-Clean` | Run clean before build |
| Test | `-t`, `--test` | `-t`, `--test` | `-Test` | Run tests |
| Skip Tests | `-s`, `--skip-tests` | `-s`, `--skip-tests` | (default) | Skip tests (default) |
| Package | `-p`, `--package` | `-p`, `--package` | `-Package` | Create fat JAR |
| Quiet | `-q`, `--quiet` | `-q`, `--quiet` | `-Quiet` | Minimize output |
| Help | `-h`, `--help` | `-h`, `--help` | `-Help` | Show help |

### Module-specific Builds

You can build specific modules. Dependencies are automatically included via `-am`.

```bash
# Single module
./bin/build.sh was-admin
bin\build.bat was-admin
.\bin\build.ps1 -Module was-admin

# Multiple modules
./bin/build.sh was-webadmin was-bootstrap
bin\build.bat was-webadmin was-bootstrap
.\bin\build.ps1 -Module was-webadmin,was-bootstrap

# "was-" prefix can be omitted
./bin/build.sh admin webadmin
```

### Module List

| Module | Description |
|--------|-------------|
| `was-config` | Server configuration (YAML parsing, data classes) |
| `was-observability` | Metrics collection, logging |
| `was-protocol-http` | HTTP/1.1, HTTP/2 protocol handling |
| `was-transport-netty` | Netty transport layer |
| `was-servlet-core` | Jakarta Servlet 6.1 implementation |
| `was-classloader` | Application classloader isolation |
| `was-deploy` | WAR deployment, hot deploy |
| `was-jndi` | JNDI directory service |
| `was-admin` | CLI admin tool (73 commands) |
| `was-jsp` | JSP engine |
| `was-tcp-listener` | TCP socket listener |
| `was-webadmin` | Web administration console |
| `was-bootstrap` | Bootstrap, fat JAR entry point |

### Build Examples

```bash
# Full clean build with tests
./bin/build.sh -c -t

# Full packaging (create fat JAR)
./bin/build.sh -p

# Build only was-webadmin, quietly
./bin/build.sh -q was-webadmin

# Windows CMD: clean + package
bin\build.bat -c -p

# PowerShell: full clean build + tests + package
.\bin\build.ps1 -Clean -Test -Package
```

### Fat JAR Location

After packaging, the fat JAR is at:

```
was-bootstrap/target/was-bootstrap-0.5.1-jar-with-dependencies.jar
```

---

## Start Scripts

### Basic Usage

```bash
# Linux / macOS — foreground
./bin/start.sh

# Linux / macOS — daemon mode
./bin/start.sh -d

# Windows CMD
bin\start.bat
bin\start.bat -d

# Windows PowerShell
.\bin\start.ps1
.\bin\start.ps1 -Daemon
```

### Options

| Option | sh | bat | ps1 | Description |
|--------|-----|-----|-----|-------------|
| Config | `-c <path>`, `--config <path>` | `-c <path>`, `--config <path>` | `-Config <path>` | Config file path (default: `conf/server.yaml`) |
| Daemon | `-d`, `--daemon` | `-d`, `--daemon` | `-Daemon` | Run in background |
| JVM Options | `-j <opts>`, `--jvm-opts <opts>` | `-j <opts>`, `--jvm-opts <opts>` | `-JvmOpts <opts>` | JVM options (default: `-Xms256m -Xmx1g -XX:+UseZGC`) |
| Help | `-h`, `--help` | `-h`, `--help` | `-Help` | Show help |

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `JAVA_HOME` | JDK path (when no local toolchain) | - |
| `VELO_JVM_OPTS` | JVM options override | `-Xms256m -Xmx1g -XX:+UseZGC` |
| `VELO_CONFIG` | Config file path override (bat only) | `conf/server.yaml` |

### How It Works

1. **Auto-build**: If the fat JAR does not exist, `build.sh -p -q` is run automatically
2. **PID management**: In daemon mode, the process ID is written to `velo-was.pid`
3. **Duplicate prevention**: Startup is rejected if the PID file exists and the process is running
4. **Log output**: In daemon mode, stdout/stderr go to `logs/velo-was.out` and `logs/velo-was.err`

### Start Examples

```bash
# Default foreground start
./bin/start.sh

# Daemon start with production config
./bin/start.sh -d -c conf/prod.yaml

# Start with 4GB heap and ZGC
./bin/start.sh -d -j "-Xms1g -Xmx4g -XX:+UseZGC"

# Windows PowerShell: daemon start
.\bin\start.ps1 -Daemon -Config conf\prod.yaml

# Windows CMD: daemon start
bin\start.bat -d -c conf\prod.yaml
```

### JVM System Properties

The following system properties are set automatically on startup:

| Property | Value |
|----------|-------|
| `-Dvelo.config` | Config file path |
| `-Dvelo.home` | Project root path |

---

## Stop Scripts

### Basic Usage

```bash
# Linux / macOS
./bin/stop.sh

# Windows CMD
bin\stop.bat

# Windows PowerShell
.\bin\stop.ps1
```

### Options

| Option | sh | bat | ps1 | Description |
|--------|-----|-----|-----|-------------|
| Force | `-f`, `--force` | `-f`, `--force` | `-Force` | Force kill immediately |
| Timeout | `-t <sec>`, `--timeout <sec>` | - | `-Timeout <sec>` | Graceful shutdown timeout (default: 30s) |
| Help | `-h`, `--help` | `-h`, `--help` | `-Help` | Show help |

### Shutdown Behavior

#### Graceful Shutdown (default)

1. Check PID file → verify process is running
2. Send SIGTERM (Linux/macOS) or `taskkill` (Windows)
3. Wait up to 30 seconds (progress printed every 5 seconds)
4. Force kill on timeout (SIGKILL / `taskkill /F`)

#### Force Kill (`-f`)

Immediately sends SIGKILL / `taskkill /F` after locating the process.

### Process Discovery Order

| Priority | Method | Description |
|----------|--------|-------------|
| 1 | PID file | Check `velo-was.pid` |
| 2 | Process search | Match `was-bootstrap.*jar-with-dependencies` in command line |

### Stop Examples

```bash
# Default graceful shutdown
./bin/stop.sh

# Immediate force kill
./bin/stop.sh -f

# Shutdown with 10-second timeout
./bin/stop.sh -t 10

# Windows CMD: force kill
bin\stop.bat -f

# PowerShell: 60-second timeout
.\bin\stop.ps1 -Timeout 60
```

---

## Platform Differences

### Linux / macOS (.sh)

- Uses `#!/usr/bin/env bash` with `set -euo pipefail`
- SIGTERM/SIGKILL signals for process management
- `pgrep` for process search
- PID file-based management

### Windows CMD (.bat)

- Uses `setlocal enabledelayedexpansion`
- `taskkill` / `tasklist` for process management
- `wmic` for process search (command-line pattern matching)
- `start /B` for background execution

### Windows PowerShell (.ps1)

- Uses `$ErrorActionPreference = "Stop"`
- `Start-Process` / `Stop-Process` cmdlets
- `Get-WmiObject Win32_Process` or `Get-Process` for process search
- `Start-Process -WindowStyle Hidden -PassThru` for background execution
- Post-start health check after 2 seconds

---

## Operational Scenarios

### Development

```bash
# Build then run in foreground (Ctrl+C to stop)
./bin/build.sh && ./bin/start.sh
```

### Staging / Production

```bash
# Clean build + package
./bin/build.sh -c -p

# Start as daemon
./bin/start.sh -d -c conf/prod.yaml -j "-Xms2g -Xmx4g -XX:+UseZGC"

# Check status
curl http://localhost:8080/admin/api/status

# Graceful stop
./bin/stop.sh

# Rolling restart
./bin/stop.sh && ./bin/start.sh -d -c conf/prod.yaml
```

### Quick Module Rebuild

```bash
# Rebuild webadmin only → repackage → restart
./bin/stop.sh
./bin/build.sh -p was-webadmin was-bootstrap
./bin/start.sh -d
```
