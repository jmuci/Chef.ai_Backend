#!/usr/bin/env bash
set -e

host="$1"
shift
count=0
while ! pg_isready -h "$host" -U postgres; do
  echo "[wait-for-db] Attempt $count: Waiting for Postgres at $host..."
  sleep 2
  count=$((count+1))
done
echo "[wait-for-db] Postgres at $host is ready (pg_isready)!"
exit 0