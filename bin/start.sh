#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# Velo WAS - Start Server (Linux / macOS)
# ─────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# ── Toolchain ────────────────────────────────────────────────
if [[ -d "$PROJECT_ROOT/.tools/jdk/jdk-21.0.10+7" ]]; then
    export JAVA_HOME="$PROJECT_ROOT/.tools/jdk/jdk-21.0.10+7"
elif [[ -z "${JAVA_HOME:-}" ]]; then
    echo "[ERROR] JAVA_HOME is not set." >&2; exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"

# ── Defaults ─────────────────────────────────────────────────
FAT_JAR="$PROJECT_ROOT/was-bootstrap/target/was-bootstrap-0.1.0-SNAPSHOT-jar-with-dependencies.jar"
CONFIG_FILE="${VELO_CONFIG:-$PROJECT_ROOT/conf/server.yaml}"
LOG_DIR="$PROJECT_ROOT/logs"
DAEMON=false
JVM_OPTS="${VELO_JVM_OPTS:--Xms256m -Xmx1g -XX:+UseZGC}"

# ── Functions ────────────────────────────────────────────────
usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Start the Velo WAS server.

Options:
  -c, --config <path>   Config file path (default: conf/server.yaml)
  -d, --daemon          Run in background (daemon mode)
  -j, --jvm-opts <opts> JVM options (default: -Xms256m -Xmx1g -XX:+UseZGC)
  -h, --help            Show this help

Environment Variables:
  JAVA_HOME             Java 21+ installation path
  VELO_CONFIG           Config file path (alternative to -c)
  VELO_JVM_OPTS         JVM options (alternative to -j)

Examples:
  ./bin/start.sh                          # Foreground start (node-1)
  ./bin/start.sh -d                       # Daemon mode (node-1)
  ./bin/start.sh -c conf/server-node2.yaml -d   # Start node-2 as daemon
  VELO_JVM_OPTS="-Xmx2g" ./bin/start.sh         # Custom JVM options
EOF
}

check_jar() {
    if [[ ! -f "$FAT_JAR" ]]; then
        echo "[WARN] Fat jar not found. Building..."
        "$SCRIPT_DIR/build.sh" -p -q
    fi
}

check_already_running() {
    if [[ -f "$PID_FILE" ]]; then
        local pid
        pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            echo "[ERROR] Velo WAS is already running (PID: $pid)." >&2
            echo "  Use ./bin/stop.sh to stop it first." >&2
            exit 1
        fi
        rm -f "$PID_FILE"
    fi
}

# ── Parse arguments ──────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        -c|--config)    CONFIG_FILE="$2"; shift 2 ;;
        -d|--daemon)    DAEMON=true; shift ;;
        -j|--jvm-opts)  JVM_OPTS="$2"; shift 2 ;;
        -h|--help)      usage; exit 0 ;;
        *)              echo "Unknown option: $1"; usage; exit 1 ;;
    esac
done

# ── Derive node-specific PID / log file names ───────────────
# e.g. conf/server.yaml      -> velo-was.pid / velo-was.out
#      conf/server-node2.yaml -> velo-was-node2.pid / velo-was-node2.out
CONFIG_BASENAME="$(basename "$CONFIG_FILE" .yaml)"
CONFIG_BASENAME="$(basename "$CONFIG_BASENAME" .yml)"
if [[ "$CONFIG_BASENAME" == "server" ]]; then
    NODE_SUFFIX=""
else
    NODE_SUFFIX="${CONFIG_BASENAME#server}"   # e.g. "-node2"
fi
PID_FILE="$PROJECT_ROOT/velo-was${NODE_SUFFIX}.pid"
LOG_FILE="$LOG_DIR/velo-was${NODE_SUFFIX}.out"

# ── Pre-checks ───────────────────────────────────────────────
check_jar
check_already_running
mkdir -p "$LOG_DIR"

# ── Build classpath ──────────────────────────────────────────
MAIN_CLASS="io.velo.was.bootstrap.VeloWasApplication"

echo "════════════════════════════════════════════════════════"
echo "  Velo WAS - Starting"
echo "  JAVA_HOME : $JAVA_HOME"
echo "  Config    : $CONFIG_FILE"
echo "  JVM Opts  : $JVM_OPTS"
echo "  Mode      : $(if $DAEMON; then echo 'Daemon'; else echo 'Foreground'; fi)"
echo "════════════════════════════════════════════════════════"

# ── Start ────────────────────────────────────────────────────
if $DAEMON; then
    nohup "$JAVA_HOME/bin/java" \
        $JVM_OPTS \
        -Dvelo.config="$CONFIG_FILE" \
        -Dvelo.home="$PROJECT_ROOT" \
        -jar "$FAT_JAR" \
        > "$LOG_FILE" 2>&1 &
    PID=$!
    echo "$PID" > "$PID_FILE"
    echo "✓ Velo WAS started in daemon mode (PID: $PID)"
    echo "  Log: $LOG_FILE"
    echo "  PID file: $PID_FILE"

    # Wait briefly and check if still alive
    sleep 2
    if ! kill -0 "$PID" 2>/dev/null; then
        echo "[ERROR] Server failed to start. Check logs:" >&2
        tail -20 "$LOG_FILE"
        rm -f "$PID_FILE"
        exit 1
    fi
else
    exec "$JAVA_HOME/bin/java" \
        $JVM_OPTS \
        -Dvelo.config="$CONFIG_FILE" \
        -Dvelo.home="$PROJECT_ROOT" \
        -jar "$FAT_JAR"
fi
