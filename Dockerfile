# syntax=docker/dockerfile:1
# ── SpringAdmin starter kit · FlazHost PaaS (CapRover) ───────────────────────
# Multi-stage build:
#   1) eclipse-temurin:21-jdk  → Maven wrapper build (deps cached separately)
#   2) eclipse-temurin:21-jre-alpine → fat-jar runtime + bundled Redis
#
# Zero-config boot: without external DB env the entrypoint activates the
# `sqlite` Spring profile (DB file under /app/data) and starts a bundled
# local redis-server. Managed MySQL/MariaDB is driven purely by env
# (DB_TYPE/DB_HOST/DB_PORT/DB_USERNAME/DB_PASSWORD/DB_DATABASE → DB_URL).

# 1) Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Cache Maven dependencies first: only pom.xml + wrapper, then go-offline.
# go-offline is best-effort (some plugins resolve lazily) — never fail here.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline -DskipTests || true

# App source + package (Spring Boot repackaged fat jar).
COPY src src
RUN ./mvnw -q -B package -DskipTests \
 && cp target/*.jar /app.jar

# 2) Runtime stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# redis : bundled session/cache store for zero-config deploys
#         (Spring Session Redis connects at boot in the default profile)
# tini  : proper PID 1 / signal handling (graceful SIGTERM shutdown)
RUN apk add --no-cache redis tini

COPY --from=build /app.jar /app/app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh \
 && mkdir -p /app/data /app/uploads

# ── Zero-config defaults (all overridable via env) ───────────────────────────
# CapRover injects PORT; the entrypoint maps it to APP_PORT (application.yml
# reads ${APP_PORT:8006}).
ENV PORT=80 \
    APP_ENV=production \
    APP_MODE=web \
    STORAGE_ROOT=/app/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 80

ENTRYPOINT ["/sbin/tini", "--", "/app/docker-entrypoint.sh"]
