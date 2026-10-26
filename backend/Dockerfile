# ============================================================
# Stage 1: Maven Builder
# ============================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache maven dependencies
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy backend source and compile production artifact
COPY src ./src
RUN mvn clean package -DskipTests -B

# ============================================================
# Stage 2: Hardened Production Runtime (Non-root JRE)
# ============================================================
FROM eclipse-temurin:21-jre-alpine AS runner

# Create dedicated unprivileged system user and group
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Create storage directory for student documents with proper ownership
RUN mkdir -p /var/erp/storage/student-documents && \
    chown -R appuser:appgroup /var/erp/storage

WORKDIR /app

# Copy the built jar from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/erp-portal-0.0.1-SNAPSHOT.jar app.jar

# Switch to non-root user
USER appuser:appgroup

# Expose HTTP port
EXPOSE 8080

# Environment defaults
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Healthcheck probe using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Launch Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
