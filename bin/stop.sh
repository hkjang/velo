#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# Velo WAS - Stop Server (Linux / macOS)
# ─────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONFIG_FILE=""
FORCE=false
TIMEOUT=30

usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Stop the Velo WAS server.

Options:
  -c, --config <path>   Config file path (to identify the node to stop)
  -f, --force           Force kill (SIGKILL) immediately
  -t, --timeout <sec>   Graceful shutdown timeout (default: 30s)
  -h, --help            Show this help

Examples:
  ./bin/stop.sh                             # Stop default node (node-1)
  ./bin/stop.sh -c conf/server-node2.yaml   # Stop node-2
  ./bin/stop.sh -f                          # Force kill default node
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -c|--config)    CONFIG_FILE="$2"; shift 2 ;;
        -f|--force)     FORCE=true; shift ;;
        -t|--timeout)   TIMEOUT="$2"; shift 2 ;;
        -h|--help)      usage; exit 0 ;;
        *)              echo "Unknown option: $1"; usage; exit 1 ;;
    esac
done

# ── Derive node-specific PID file name ──────────────────────
if [[ -n "$CONFIG_FILE" ]]; then
    CONFIG_BASENAME="$(basename "$CONFIG_FILE" .yaml)"
    CONFIG_BASENAME="$(basename "$CONFIG_BASENAME" .yml)"
    if [[ "$CONFIG_BASENAME" == "server" ]]; then
        NODE_SUFFIX=""
    else
        NODE_SUFFIX="${CONFIG_BASENAME#server}"
    fi
    PID_FILE="$PROJECT_ROOT/velo-was${NODE_SUFFIX}.pid"
else
    PID_FILE="$PROJECT_ROOT/velo-was.pid"
fi

if [[ ! -f "$PID_FILE" ]]; then
    echo "[WARN] PID file not found: $PID_FILE"
    echo "  Server may not be running, or was started in foreground mode."

    # Try to find by process
    PID=$(pgrep -f "was-bootstrap.*jar-with-dependencies" 2>/dev/null || true)
    if [[ -z "$PID" ]]; then
        echo "  No Velo WAS process found."
        exit 0
    fi
    echo "  Found process: PID $PID"
else
    PID=$(cat "$PID_FILE")
fi

if ! kill -0 "$PID" 2>/dev/null; then
    echo "[INFO] Process $PID is not running."
    rm -f "$PID_FILE"
    exit 0
fi

echo "════════════════════════════════════════════════════════"
echo "  Velo WAS - Stopping (PID: $PID)"
echo "════════════════════════════════════════════════════════"

if $FORCE; then
    echo "  Force killing..."
    kill -9 "$PID" 2>/dev/null || true
    rm -f "$PID_FILE"
    echo "✓ Process killed."
    exit 0
fi

# Graceful shutdown (SIGTERM)
echo "  Sending SIGTERM (graceful shutdown, timeout: ${TIMEOUT}s)..."
kill "$PID" 2>/dev/null || true

ELAPSED=0
while kill -0 "$PID" 2>/dev/null; do
    if [[ $ELAPSED -ge $TIMEOUT ]]; then
        echo "  Timeout reached. Force killing..."
        kill -9 "$PID" 2>/dev/null || true
        break
    fi
    sleep 1
    ELAPSED=$((ELAPSED + 1))
    if [[ $((ELAPSED % 5)) -eq 0 ]]; then
        echo "  Waiting... (${ELAPSED}s / ${TIMEOUT}s)"
    fi
done

rm -f "$PID_FILE"
echo "✓ Velo WAS stopped."
