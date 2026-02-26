# =============================================================================
# Devin FinOps Dashboard - Backend Dockerfile
# Multi-stage build: Maven build + JRE 17 runtime
# =============================================================================

# --- Stage 1: Build ---
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy pom.xml first for dependency caching
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and endpoints config
COPY backend/src ./src
COPY endpoints.yaml ./endpoints.yaml

# Build the application (skip tests for faster image build; tests run in CI)
RUN mvn clean package -DskipTests -B

# --- Stage 2: Runtime ---
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Copy endpoints.yaml (needed at runtime by EndpointLoader)
COPY --from=build /app/endpoints.yaml ./endpoints.yaml

# Create data directory for snapshot persistence
RUN mkdir -p /app/data && chown -R appuser:appgroup /app

USER appuser

# Expose the application port
EXPOSE 8080

# Health check using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
