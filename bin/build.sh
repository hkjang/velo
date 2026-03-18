#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# Velo WAS - Build Script (Linux / macOS)
# ─────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# ── Toolchain detection ──────────────────────────────────────
if [[ -d "$PROJECT_ROOT/.tools/jdk/jdk-21.0.10+7" ]]; then
    export JAVA_HOME="$PROJECT_ROOT/.tools/jdk/jdk-21.0.10+7"
elif [[ -n "${JAVA_HOME:-}" ]]; then
    echo "[INFO] Using system JAVA_HOME=$JAVA_HOME"
else
    echo "[ERROR] JAVA_HOME is not set and local JDK not found." >&2
    exit 1
fi

if [[ -d "$PROJECT_ROOT/.tools/maven/apache-maven-3.9.13" ]]; then
    MVN="$PROJECT_ROOT/.tools/maven/apache-maven-3.9.13/bin/mvn"
elif command -v mvn &>/dev/null; then
    MVN="mvn"
else
    echo "[ERROR] Maven not found. Install Maven or place it in .tools/maven/" >&2
    exit 1
fi

export PATH="$JAVA_HOME/bin:$PATH"

find_fat_jar() {
    local candidates=("$PROJECT_ROOT"/was-bootstrap/target/was-bootstrap-*-jar-with-dependencies.jar)
    if [[ ${#candidates[@]} -eq 1 && ! -f "${candidates[0]}" ]]; then
        return 1
    fi
    ls -t "${candidates[@]}" 2>/dev/null | head -n 1
}

# ── Module list ──────────────────────────────────────────────
ALL_MODULES=(
    was-config was-observability was-protocol-http was-transport-netty
    was-servlet-core was-classloader was-deploy was-jndi was-admin
    was-jsp was-tcp-listener was-webadmin was-bootstrap
)

# ── Functions ────────────────────────────────────────────────
usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS] [MODULE...]

Build Velo WAS modules.

Options:
  -c, --clean         Clean before build (mvn clean)
  -t, --test          Run tests
  -s, --skip-tests    Skip tests (default)
  -p, --package       Create fat jar (mvn package)
  -q, --quiet         Quiet output
  -h, --help          Show this help

Modules:
  all                 Build all modules (default)
  <module-name>       Build specific module (e.g. was-admin, was-webadmin)

Examples:
  ./bin/build.sh                    # Build all modules, skip tests
  ./bin/build.sh was-admin          # Build was-admin only
  ./bin/build.sh -c -t              # Clean build with tests
  ./bin/build.sh -p                 # Package (create fat jar)
  ./bin/build.sh was-webadmin was-bootstrap  # Build two modules
EOF
}

resolve_modules() {
    local modules=()
    for m in "$@"; do
        if [[ "$m" == "all" ]]; then
            return  # empty = build all
        fi
        # Normalize: allow both "was-admin" and "admin"
        if [[ "$m" != was-* ]]; then
            m="was-$m"
        fi
        modules+=("$m")
    done
    if [[ ${#modules[@]} -gt 0 ]]; then
        echo "-pl $(IFS=,; echo "${modules[*]}") -am"
    fi
}

# ── Parse arguments ──────────────────────────────────────────
GOAL="compile"
CLEAN=""
SKIP_TESTS="-DskipTests"
QUIET=""
MODULE_ARGS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        -c|--clean)     CLEAN="clean"; shift ;;
        -t|--test)      SKIP_TESTS=""; shift ;;
        -s|--skip-tests) SKIP_TESTS="-DskipTests"; shift ;;
        -p|--package)   GOAL="package"; shift ;;
        -q|--quiet)     QUIET="-q"; shift ;;
        -h|--help)      usage; exit 0 ;;
        -*)             echo "Unknown option: $1"; usage; exit 1 ;;
        *)              MODULE_ARGS+=("$1"); shift ;;
    esac
done

PL_OPTION=$(resolve_modules "${MODULE_ARGS[@]:-}")

# ── Build ────────────────────────────────────────────────────
echo "════════════════════════════════════════════════════════"
echo "  Velo WAS Build"
echo "  JAVA_HOME : $JAVA_HOME"
echo "  Goal      : ${CLEAN:+clean }$GOAL"
echo "  Modules   : ${MODULE_ARGS[*]:-all}"
echo "  Tests     : ${SKIP_TESTS:+skipped}"
echo "════════════════════════════════════════════════════════"

cd "$PROJECT_ROOT"
# shellcheck disable=SC2086
"$MVN" $CLEAN $GOAL $SKIP_TESTS $QUIET $PL_OPTION

echo ""
echo "✓ Build completed successfully."

# If packaging, show artifact location
if [[ "$GOAL" == "package" ]]; then
    FAT_JAR="$(find_fat_jar || true)"
    if [[ -n "$FAT_JAR" && -f "$FAT_JAR" ]]; then
        echo "  Artifact: $FAT_JAR"
        echo "  Size    : $(du -h "$FAT_JAR" | cut -f1)"
    fi
fi
