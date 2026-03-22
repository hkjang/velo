#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────
#  Velo WAS — Docker Build Script
#  Usage:
#    ./bin/docker-build.sh                  # Build with latest tag
#    ./bin/docker-build.sh 0.5.19           # Build with specific tag
#    ./bin/docker-build.sh --push           # Build and push
# ──────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

VERSION="${1:-latest}"
PUSH=false
REGISTRY="${DOCKER_REGISTRY:-}"

if [[ "$VERSION" == "--push" ]]; then
    VERSION="latest"
    PUSH=true
fi
if [[ "${2:-}" == "--push" ]]; then
    PUSH=true
fi

IMAGE_NAME="velo-was"
FULL_TAG="${REGISTRY:+${REGISTRY}/}${IMAGE_NAME}:${VERSION}"
LATEST_TAG="${REGISTRY:+${REGISTRY}/}${IMAGE_NAME}:latest"

echo "═══════════════════════════════════════════════"
echo "  Velo WAS Docker Build"
echo "  Image: ${FULL_TAG}"
echo "═══════════════════════════════════════════════"

# Build
docker build \
    --tag "$FULL_TAG" \
    --tag "$LATEST_TAG" \
    --build-arg VERSION="$VERSION" \
    --label "org.opencontainers.image.version=${VERSION}" \
    --label "org.opencontainers.image.created=$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    .

echo ""
echo "Build complete: $FULL_TAG"

if $PUSH; then
    echo "Pushing to registry..."
    docker push "$FULL_TAG"
    docker push "$LATEST_TAG"
    echo "Push complete."
fi
