#!/bin/sh
# SpringAdmin container boot sequence (FlazHost / CapRover):
#   1. Map CapRover's $PORT -> APP_PORT (application.yml reads ${APP_PORT:8006})
#   2. Ensure secrets exist (generate + persist in /app/data/.runtime-secrets)
#   3. Compose DB_URL from platform env (DB_TYPE/DB_HOST/DB_PORT/DB_DATABASE)
#      or fall back to the zero-config `sqlite` Spring profile
#   4. Start bundled redis-server when Redis points at localhost
#   5. Wait for external DB (best-effort), then exec the fat jar
set -eu

DATA_DIR=/app/data
mkdir -p "$DATA_DIR"

# ── 1. Port: CapRover injects $PORT (default 80). ────────────────────────────
: "${PORT:=80}"
export APP_PORT="${APP_PORT:-$PORT}"

# ── 2. Secrets (SESSION_SECRET / JWT_SECRET) ─────────────────────────────────
# Honour env-supplied values; otherwise generate strong random secrets once and
# persist them so sessions/JWTs survive restarts (when /app/data is a volume).
SECRETS_FILE="$DATA_DIR/.runtime-secrets"
[ -f "$SECRETS_FILE" ] && . "$SECRETS_FILE"

gen_secret() {
    head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n'
}

if [ -z "${SESSION_SECRET:-}" ]; then
    SESSION_SECRET="$(gen_secret)"
    echo "SESSION_SECRET=$SESSION_SECRET" >> "$SECRETS_FILE"
    echo "[entrypoint] Generated SESSION_SECRET (persisted in $SECRETS_FILE)"
fi
if [ -z "${JWT_SECRET:-}" ]; then
    JWT_SECRET="$(gen_secret)"
    echo "JWT_SECRET=$JWT_SECRET" >> "$SECRETS_FILE"
    echo "[entrypoint] Generated JWT_SECRET (persisted in $SECRETS_FILE)"
fi
export SESSION_SECRET JWT_SECRET

# ── 3. Database ──────────────────────────────────────────────────────────────
# Platform env: DB_TYPE / DB_HOST / DB_PORT / DB_USERNAME / DB_PASSWORD /
# DB_DATABASE. The app itself reads DB_URL (JDBC), DB_USERNAME, DB_PASSWORD.
# No DB_URL and no DB_HOST → zero-config SQLite (bundled driver + `sqlite`
# Spring profile: SQLite dialect, in-memory servlet sessions, Flyway intact).
DB_MODE=external
if [ -z "${DB_URL:-}" ]; then
    case "${DB_TYPE:-}" in
        sqlite)
            DB_MODE=sqlite
            ;;
        postgres|postgresql)
            echo "[entrypoint] ERROR: DB_TYPE=postgres is not supported — this image ships only the MySQL and SQLite JDBC drivers." >&2
            echo "[entrypoint]        Link a MySQL/MariaDB database instead, or leave DB unset for zero-config SQLite." >&2
            exit 1
            ;;
        *)
            if [ -n "${DB_HOST:-}" ]; then
                DB_URL="jdbc:mysql://${DB_HOST}:${DB_PORT:-3306}/${DB_DATABASE:-springadmin}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
                export DB_URL
                echo "[entrypoint] Composed DB_URL for ${DB_TYPE:-mysql} at ${DB_HOST}:${DB_PORT:-3306}/${DB_DATABASE:-springadmin}"
            else
                DB_MODE=sqlite
            fi
            ;;
    esac
else
    echo "[entrypoint] Using provided DB_URL"
fi

if [ "$DB_MODE" = "sqlite" ]; then
    # `sqlite` profile fixes driver/dialect/session; env overrides the file URL
    # so the DB lives on the persistent /app/data volume.
    SQLITE_FILE="$DATA_DIR/springadmin.db"
    export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-sqlite}"
    export SPRING_DATASOURCE_URL="jdbc:sqlite:${SQLITE_FILE}"
    echo "[entrypoint] No external database linked — using zero-config SQLite at $SQLITE_FILE (profile: $SPRING_PROFILES_ACTIVE)"
fi

# ── 4. Redis ─────────────────────────────────────────────────────────────────
# Default profile uses Redis-backed sessions (Spring Session connects at boot),
# so a local redis-server is bundled. External Redis via REDIS_URL/REDIS_HOST.
# NOTE: application.yml reads ${REDIS_URL:} — an EMPTY string breaks Lettuce
# ("Invalid Redis URL ''"), so always export a fully-formed URL here.
if [ -z "${REDIS_URL:-}" ]; then
    if [ -n "${REDIS_HOST:-}" ]; then
        if [ -n "${REDIS_PASSWORD:-}" ]; then
            REDIS_URL="redis://:${REDIS_PASSWORD}@${REDIS_HOST}:${REDIS_PORT:-6379}"
        else
            REDIS_URL="redis://${REDIS_HOST}:${REDIS_PORT:-6379}"
        fi
    else
        REDIS_URL="redis://127.0.0.1:6379"
    fi
fi
export REDIS_URL

case "$REDIS_URL" in
    *127.0.0.1*|*localhost*)
        echo "[entrypoint] Starting bundled redis-server ($REDIS_URL)"
        redis-server --daemonize yes --save "" --appendonly no >/dev/null 2>&1 || \
            echo "[entrypoint] WARN: could not start bundled redis-server"
        ;;
    *)
        echo "[entrypoint] Using external Redis at $REDIS_URL"
        ;;
esac

# ── 5. Wait for external DB (best-effort), then start ────────────────────────
# Flyway runs on boot and fails fast if the DB is unreachable; give a linked
# MySQL up to ~90s to come up and print a clear message if it never does.
if [ "$DB_MODE" = "external" ] && [ -n "${DB_HOST:-}" ]; then
    _dbport="${DB_PORT:-3306}"
    echo "[entrypoint] Waiting for database at ${DB_HOST}:${_dbport} ..."
    _tries=0
    until nc -w 2 "$DB_HOST" "$_dbport" </dev/null >/dev/null 2>&1; do
        _tries=$((_tries + 1))
        if [ "$_tries" -ge 45 ]; then
            echo "[entrypoint] ERROR: database ${DB_HOST}:${_dbport} unreachable after 90s." >&2
            echo "[entrypoint]        Pastikan database sudah di-link / env DB_HOST benar." >&2
            break
        fi
        sleep 2
    done
fi

# Flyway migrates + seeds (admin@admin.com) automatically during Spring boot.
echo "[entrypoint] Starting SpringAdmin on 0.0.0.0:${APP_PORT} (Flyway migrates on boot)"
exec java $JAVA_OPTS -jar /app/app.jar
