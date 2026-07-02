# ── Stage 1: Build ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

RUN apk add --no-cache maven

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="BECS Processing Team"
LABEL description="BECS BPY File Processor – H2 embedded DB, JDK 21"

RUN addgroup -S becs && adduser -S becs -G becs

WORKDIR /app

COPY --from=builder /build/target/becs-processor-*.jar app.jar

RUN mkdir -p /data/becs/inbox /data/becs/archive /data/becs/output /data/becs/error && \
    chown -R becs:becs /data /app

USER becs

# 8080 = REST API / Swagger / H2 Web Console
# 9092 = H2 TCP server (DataGrip / DBeaver remote connection)
EXPOSE 8080 9092

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseZGC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
