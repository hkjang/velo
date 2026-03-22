#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────
#  Velo WAS — Docker Run Script
#  Usage:
#    ./bin/docker-run.sh                   # Run standalone
#    ./bin/docker-run.sh --redis           # Run with Redis
#    ./bin/docker-run.sh --cluster         # Run cluster (2 nodes + Nginx + Redis)
#    ./bin/docker-run.sh --monitoring      # Run with Prometheus + Grafana
#    ./bin/docker-run.sh --full            # Run everything
#    ./bin/docker-run.sh --stop            # Stop all
# ──────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

ACTION="${1:---standalone}"

case "$ACTION" in
    --stop)
        echo "Stopping all Velo containers..."
        docker compose --profile redis --profile cluster --profile monitoring down
        echo "All stopped."
        ;;
    --redis)
        echo "Starting Velo WAS + Redis..."
        docker compose --profile redis up -d
        ;;
    --cluster)
        echo "Starting Velo cluster (2 nodes + Nginx + Redis)..."
        docker compose --profile cluster up -d
        ;;
    --monitoring)
        echo "Starting Velo WAS + Monitoring (Prometheus + Grafana)..."
        docker compose --profile monitoring up -d
        ;;
    --full)
        echo "Starting full stack..."
        docker compose --profile redis --profile cluster --profile monitoring up -d
        ;;
    --standalone|*)
        echo "Starting Velo WAS standalone..."
        docker compose up -d
        ;;
esac

echo ""
echo "Services:"
docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || docker compose ps
echo ""
echo "AI Platform: http://localhost:8080/ai-platform"
