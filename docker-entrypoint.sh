#!/usr/bin/env bash
set -e
echo "[entrypoint] Waiting for Postgres..."
/wait-for-db.sh db
echo "[entrypoint] Postgres is ready, starting app..."
exec java -jar app.jar