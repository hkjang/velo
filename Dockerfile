# ──────────────────────────────────────────────────────────
#  Velo WAS — Multi-stage Docker Build
#  Usage:
#    docker build -t velo-was .
#    docker run -p 8080:8080 velo-was
# ──────────────────────────────────────────────────────────

# ── Stage 1: Build ────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Cache Maven dependencies first
COPY pom.xml ./
COPY was-admin/pom.xml           was-admin/pom.xml
COPY was-ai-platform/pom.xml     was-ai-platform/pom.xml
COPY was-bootstrap/pom.xml       was-bootstrap/pom.xml
COPY was-classloader/pom.xml     was-classloader/pom.xml
COPY was-config/pom.xml          was-config/pom.xml
COPY was-deploy/pom.xml          was-deploy/pom.xml
COPY was-jndi/pom.xml            was-jndi/pom.xml
COPY was-jsp/pom.xml             was-jsp/pom.xml
COPY was-mcp/pom.xml             was-mcp/pom.xml
COPY was-observability/pom.xml   was-observability/pom.xml
COPY was-protocol-http/pom.xml   was-protocol-http/pom.xml
COPY was-servlet-core/pom.xml    was-servlet-core/pom.xml
COPY was-tcp-listener/pom.xml    was-tcp-listener/pom.xml
COPY was-transport-netty/pom.xml was-transport-netty/pom.xml
COPY was-webadmin/pom.xml        was-webadmin/pom.xml

# Install Maven (Alpine)
RUN apk add --no-cache maven \
    && mvn dependency:go-offline -B -q 2>/dev/null || true

# Copy source and build
COPY . .
RUN mvn package -DskipTests -q \
    && mv was-bootstrap/target/was-bootstrap-*-jar-with-dependencies.jar /build/velo-was.jar

# ── Stage 2: Runtime ──────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Velo WAS Project"
LABEL org.opencontainers.image.title="Velo WAS"
LABEL org.opencontainers.image.description="Lightweight Java WAS with AI Platform, MCP Server, and Intent Routing"
LABEL org.opencontainers.image.version="0.5.19"

# Create non-root user
RUN addgroup -S velo && adduser -S velo -G velo

WORKDIR /opt/velo

# Copy artifacts
COPY --from=builder /build/velo-was.jar ./velo-was.jar
COPY conf/ ./conf/
COPY webapps/ ./webapps/ 2>/dev/null || true

# Create data directories
RUN mkdir -p data logs webapps \
    && chown -R velo:velo /opt/velo

# JVM defaults (override via VELO_JVM_OPTS)
ENV JAVA_OPTS="-Xms256m -Xmx1g -XX:+UseZGC -XX:+ZGenerational" \
    VELO_JVM_OPTS="" \
    VELO_CONFIG="/opt/velo/conf/server.yaml" \
    VELO_HOME="/opt/velo"

EXPOSE 8080

USER velo

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/ai-platform/api/status || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS $VELO_JVM_OPTS -Dvelo.config=$VELO_CONFIG -Dvelo.home=$VELO_HOME -jar velo-was.jar"]
