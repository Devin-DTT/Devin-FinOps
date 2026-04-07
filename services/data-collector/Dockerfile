# =============================================================================
# Devin FinOps - Data Collector Dockerfile
# Multi-stage build: Maven build + JRE 17 runtime
# =============================================================================

# --- Stage 1: Build ---
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copy parent POM and all module POMs for dependency resolution
COPY pom.xml ./pom.xml
COPY shared/common-models/pom.xml ./shared/common-models/pom.xml
COPY services/api-gateway/pom.xml ./services/api-gateway/pom.xml
COPY services/data-collector/pom.xml ./services/data-collector/pom.xml
COPY services/websocket-service/pom.xml ./services/websocket-service/pom.xml
COPY backend/pom.xml ./backend/pom.xml

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B -pl services/data-collector -am || true

# Copy source code
COPY shared/common-models/src ./shared/common-models/src
COPY services/data-collector/src ./services/data-collector/src
COPY endpoints.yaml ./endpoints.yaml

# Build only the data-collector module and its dependencies
RUN mvn clean package -DskipTests -B -pl services/data-collector -am

# --- Stage 2: Runtime ---
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /app/services/data-collector/target/*.jar app.jar
COPY --from=build /app/endpoints.yaml ./endpoints.yaml

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q --spider http://localhost:8081/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
