#!/usr/bin/env bash
# The Docker image the way a shop runs it: docker-compose.yml with Caddy on its local CA in front, the end to end
# walk through the proxy, a restore finished with the admin restart route, and docker stop closing H2 cleanly.
# Runs as its own compose project (ticketman-smoke) on its own ports and network, so it leaves a real install alone,
# and removes its containers and volumes afterwards.
#
#   scripts/docker_smoke.sh             # builds the server (:server:installDist -PwithWeb) and the image first
#   scripts/docker_smoke.sh --no-build  # uses the server already in server/build/install/server
#   scripts/docker_smoke.sh --keep      # leaves it running afterwards, at https://localhost:8445
#
# Settings, all optional: HTTPS_PORT (8445), HTTP_PORT (8081), SUBNET (172.30.81.0/24). See docs/testing.md
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD=true
KEEP=false
for arg in "$@"; do
    case "$arg" in
        --no-build) BUILD=false ;;
        --keep) KEEP=true ;;
        *) echo "Unknown option $arg" >&2; exit 2 ;;
    esac
done

export DOMAIN=localhost CERTIFICATE=private TZ="${TZ:-America/New_York}" ADMIN_PASSWORD=admin_pass
export HTTPS_PORT="${HTTPS_PORT:-8445}" HTTP_PORT="${HTTP_PORT:-8081}" SUBNET="${SUBNET:-172.30.81.0/24}"
BASE="https://localhost:$HTTPS_PORT"
compose() { docker compose -p ticketman-smoke -f "$ROOT/docker-compose.yml" "$@"; }

cleanup() {
    if $KEEP; then
        echo "Left running at $BASE (admin / admin_pass). Remove it with: docker compose -p ticketman-smoke down -v"
    else
        compose down -v --remove-orphans >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

if $BUILD; then
    echo "Building the server with the web client"
    (cd "$ROOT" && ./gradlew --console=plain -q :server:installDist -PwithWeb)
fi

echo "Starting the server and Caddy"
compose up -d --build

health() { docker inspect --format '{{.State.Health.Status}}' "$(compose ps -q ticketman)"; }
echo -n "Waiting for the health check"
for _ in $(seq 1 90); do
    [[ "$(health)" == "healthy" ]] && break
    echo -n "."
    sleep 2
done
echo
if [[ "$(health)" != "healthy" ]]; then
    compose logs ticketman | tail -40
    echo "FAIL the server never got healthy" >&2
    exit 1
fi

# The desktop and Android apps send no SNI for localhost or an address; Caddy has to answer them anyway
if echo | openssl s_client -connect "localhost:$HTTPS_PORT" -noservername 2>&1 | grep -q "alert"; then
    echo "FAIL Caddy refuses a handshake without SNI, the way Java connects (CERTIFICATE=private?)" >&2
    exit 1
fi
redirect="$(curl -s -o /dev/null -w '%{redirect_url}' "http://localhost:$HTTP_PORT/api/ping")"
[[ "$redirect" == "$BASE/api/ping" ]] || { echo "FAIL plain HTTP redirected to '$redirect', not $BASE/api/ping" >&2; exit 1; }
echo "Caddy answers without SNI and sends plain HTTP to $BASE"

echo "Walking the counter through the proxy, then restoring with a restart"
python3 "$ROOT/scripts/counter_e2e.py" "$BASE" --insecure --restart

restarts="$(docker inspect --format '{{.RestartCount}}' "$(compose ps -q ticketman)")"
[[ "$restarts" -ge 1 ]] || { echo "FAIL Docker didn't restart the server (restart count $restarts)" >&2; exit 1; }
echo "Docker started it again after the restart ($restarts restart)"

echo "Stopping the server the way docker stop does"
compose stop ticketman
code="$(docker inspect --format '{{.State.ExitCode}}' "$(compose ps -aq ticketman)")"
# The last line, the stop's; the restart before it closed H2 too. Read whole first: grep -q quitting early would
# fail the pipe under pipefail
last="$(compose logs --no-log-prefix ticketman | tail -n 1)"
[[ "$last" == *"Database closed"* ]] || { echo "FAIL H2 wasn't closed on stop (last line: $last)" >&2; exit 1; }
# 143 is SIGTERM handled by the JVM; 137 would mean Docker had to kill it
[[ "$code" == "143" ]] || { echo "FAIL stopped with exit code $code" >&2; exit 1; }
echo "Stopped cleanly (exit $code, database closed)"

$KEEP && compose start ticketman >/dev/null
echo
echo "Docker smoke test passed"
