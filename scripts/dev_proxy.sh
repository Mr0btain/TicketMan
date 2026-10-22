#!/usr/bin/env bash
# A development server behind Caddy, the way a shop runs it behind a reverse proxy, without Docker.
# Needs Caddy installed (https://caddyserver.com/docs/install), or CADDY=/path/to/caddy.
#
#   scripts/dev_proxy.sh               # starts the server too, with the proxy trusted
#   scripts/dev_proxy.sh --no-server   # only Caddy, in front of a server already on SERVER_PORT
#
# Then point a client at https://localhost:8444. Caddy's local CA isn't trusted by anything, so the desktop client
# asks to trust it (the private certificate case). For the public certificate case, run the client with
#   ./gradlew :composeApp:run -DticketmanDebug=true -PdevTrustStore=build/dev-truststore.p12
# which this script writes from Caddy's CA once Caddy has made it.
#
# Settings, all optional: SERVER_PORT (8080), PROXY_PORT (8444), DATA_PATH (build/dev-proxy-data),
# ADMIN_PASSWORD (admin_pass), CADDY (caddy). See docs/getting-started.md#behind-a-proxy
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CADDY="${CADDY:-caddy}"
export SERVER_PORT="${SERVER_PORT:-8080}"
export PROXY_PORT="${PROXY_PORT:-8444}"
START_SERVER=true
[[ "${1:-}" == "--no-server" ]] && START_SERVER=false

if ! command -v "$CADDY" >/dev/null 2>&1; then
    echo "Caddy not found. Install it (https://caddyserver.com/docs/install) or set CADDY=/path/to/caddy" >&2
    exit 1
fi

# Caddy keeps its local CA and its saved config here rather than in your own Caddy folders
export XDG_DATA_HOME="$ROOT/build/dev-caddy"
export XDG_CONFIG_HOME="$ROOT/build/dev-caddy/config"
export CA_DIR="$XDG_DATA_HOME/caddy/pki/authorities/local"
CA_ROOT="$CA_DIR/root.crt"
TRUST_STORE="$ROOT/build/dev-truststore.p12"
mkdir -p "$XDG_DATA_HOME"

pids=()
cleanup() {
    for pid in "${pids[@]}"; do kill "$pid" 2>/dev/null || true; done
    wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# -k: the proxy's certificate is from Caddy's local CA, which curl doesn't trust either
ping() { curl -sk --max-time 2 "$1/api/ping" 2>/dev/null | grep -q TicketMan; }

if $START_SERVER; then
    echo "Starting the server on http://localhost:$SERVER_PORT behind the proxy"
    (cd "$ROOT" && PORT="$SERVER_PORT" TRUSTED_PROXIES=127.0.0.1,::1 \
        DATA_PATH="${DATA_PATH:-$ROOT/build/dev-proxy-data}" ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin_pass}" \
        exec ./gradlew --console=plain :server:run -Pdevelopment) &
    pids+=($!)
fi

echo "Waiting for the server on http://localhost:$SERVER_PORT"
until ping "http://localhost:$SERVER_PORT"; do
    if $START_SERVER && ! kill -0 "${pids[0]}" 2>/dev/null; then echo "The server stopped" >&2; exit 1; fi
    sleep 1
done

"$CADDY" run --config "$ROOT/scripts/dev/Caddyfile" --adapter caddyfile &
pids+=($!)

# Caddy makes its CA on the first start; export it for -PdevTrustStore each time, in case it was made again
for _ in $(seq 30); do [[ -f "$CA_ROOT" ]] && break; sleep 1; done
if [[ -f "$CA_ROOT" ]] && command -v keytool >/dev/null 2>&1; then
    rm -f "$TRUST_STORE"
    keytool -importcert -noprompt -alias caddy-dev -file "$CA_ROOT" \
        -keystore "$TRUST_STORE" -storetype PKCS12 -storepass changeit >/dev/null
    echo "Caddy's CA is in build/dev-truststore.p12 (for -PdevTrustStore)"
else
    echo "Couldn't write build/dev-truststore.p12: no Caddy CA yet, or no keytool on the PATH" >&2
fi

for _ in $(seq 30); do ping "https://localhost:$PROXY_PORT" && break; sleep 1; done
echo
echo "Proxy ready on https://localhost:$PROXY_PORT (Ctrl+C stops it$($START_SERVER && echo " and the server"))"
echo "  End to end:  python3 scripts/counter_e2e.py https://localhost:$PROXY_PORT --insecure"
echo "  A 413:       anything under https://localhost:$PROXY_PORT/tiny/ has a 1 KB body limit"
wait
