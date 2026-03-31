# Multi-stage Dockerfile for Voice Banking Assistant (Java)
# Optimized for small image size and fast startup

# ============================================
# Stage 1: Build
# ============================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy POMs first for dependency caching
COPY pom.xml .
COPY voice-banking-app/pom.xml voice-banking-app/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B

# Copy source code
COPY voice-banking-app/src voice-banking-app/src

# Build application
RUN mvn clean package -DskipTests -pl voice-banking-app

# ============================================
# Stage 2: Runtime
# ============================================
FROM eclipse-temurin:21-jre-alpine

# Security: Run as non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copy JAR from builder
COPY --from=builder /build/voice-banking-app/target/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
