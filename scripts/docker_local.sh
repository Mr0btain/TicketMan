#!/usr/bin/env bash
# docker-compose.yml on this machine, to try the shop setup by hand: the server behind Caddy on its local CA at
# https://localhost:8443, with the desktop client and a browser on the same machine. Runs as its own compose project
# (ticketman-local) on its own ports and network, so it leaves a real install alone, and keeps its data between runs.
#
#   scripts/docker_local.sh              # builds the server (:server:installDist -PwithWeb) and the image, starts it
#   scripts/docker_local.sh --no-build   # uses the server already in server/build/install/server
#   scripts/docker_local.sh --down       # stops it and deletes its data
#
# Settings, all optional: HTTPS_PORT (8443), HTTP_PORT (8082), SUBNET (172.30.82.0/24). See docs/testing.md
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD=true
for arg in "$@"; do
    case "$arg" in
        --no-build) BUILD=false ;;
        --down) DOWN=true ;;
        *) echo "Unknown option $arg" >&2; exit 2 ;;
    esac
done

export DOMAIN=localhost TZ="${TZ:-America/New_York}" ADMIN_PASSWORD=admin_pass
export HTTPS_PORT="${HTTPS_PORT:-8443}" HTTP_PORT="${HTTP_PORT:-8082}" SUBNET="${SUBNET:-172.30.82.0/24}"
BASE="https://localhost:$HTTPS_PORT"
TRUST_STORE="$ROOT/build/docker-truststore.p12"
compose() { docker compose -p ticketman-local -f "$ROOT/docker-compose.yml" "$@"; }

if [[ "${DOWN:-false}" == true ]]; then
    compose down -v --remove-orphans
    rm -f "$TRUST_STORE"
    exit 0
fi

if $BUILD; then
    echo "Building the server with the web client"
    (cd "$ROOT" && ./gradlew --console=plain -q :server:installDist -PwithWeb)
fi

echo "Starting the server and Caddy"
compose up -d --build

echo -n "Waiting for $BASE"
for _ in $(seq 1 90); do
    curl -sk --max-time 2 "$BASE/api/ping" 2>/dev/null | grep -q TicketMan && break
    echo -n "."
    sleep 2
done
echo
curl -sk --max-time 2 "$BASE/api/ping" | grep -q TicketMan || { compose logs | tail -40; echo "FAIL it never answered" >&2; exit 1; }

# The same file the apps fetch when they ask to trust the server
CA="$(mktemp)"
trap 'rm -f "$CA"' EXIT
curl -sk "$BASE/.well-known/ticketman/ca.pem" -o "$CA"
FINGERPRINT="$(openssl x509 -in "$CA" -noout -fingerprint -sha256 | cut -d= -f2)"

# For trying the public certificate case: a client run with this trust store trusts the CA without asking
if command -v keytool >/dev/null 2>&1; then
    mkdir -p "$(dirname "$TRUST_STORE")"
    rm -f "$TRUST_STORE"
    keytool -importcert -noprompt -alias caddy-docker -file "$CA" \
        -keystore "$TRUST_STORE" -storetype PKCS12 -storepass changeit >/dev/null 2>&1
fi

cat <<EOF

Running at $BASE (admin / admin_pass). Caddy's local CA fingerprint, which the apps ask you to check:

    $FINGERPRINT

Desktop client, asking to trust the CA the way a shop with a private certificate sees it:
    ./gradlew :composeApp:run -DticketmanDebug=true          then connect to $BASE

Desktop client trusting it already, the way a shop with a public certificate sees it:
    ./gradlew :composeApp:run -DticketmanDebug=true -PdevTrustStore=build/docker-truststore.p12

Browser: open $BASE and accept the warning, or import the CA to skip it
    (docker compose -p ticketman-local exec caddy cat /data/caddy/pki/authorities/local/root.crt > caddy-root.crt)

Plain HTTP on http://localhost:$HTTP_PORT only redirects to HTTPS.
Logs: docker compose -p ticketman-local logs -f      Stop and delete: scripts/docker_local.sh --down
EOF
