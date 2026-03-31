# Dockerfile for BFA MCP Spike (ADR-CES-003)
# Standalone MCP server for location services — Streamable HTTP transport on port 8081

FROM eclipse-temurin:21-jre-alpine

# Security: Run as non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copy pre-built JAR (expects mvn package to be run before docker build)
COPY target/bfa-mcp-spike-*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Health check — MCP server on 8081
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q --spider http://localhost:8081/actuator/health || exit 1

# Expose port (Cloud Run will map $PORT → 8081)
EXPOSE 8081

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
