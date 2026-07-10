#!/usr/bin/env bash
# DevX: keygen (via tamayo or embedded), run gateway, exercise upload loop.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export GOTOOLCHAIN="${GOTOOLCHAIN:-go1.26.4}"
export GATEWAY_MODE=dev
export GATEWAY_POLICY="$ROOT/policy.dev.json"
export GATEWAY_ISSUER="${GATEWAY_ISSUER:-$ROOT/issuer.json}"
export GATEWAY_ADDR="${GATEWAY_ADDR:-127.0.0.1:8790}"
export GATEWAY_PUBLIC_BASE="http://${GATEWAY_ADDR}"

if [[ ! -f "$GATEWAY_ISSUER" ]]; then
  echo "generating issuer at $GATEWAY_ISSUER"
  if command -v tamayo >/dev/null 2>&1; then
    tamayo keygen -out "$GATEWAY_ISSUER"
  else
    go run github.com/maceip/tamayo/cmd/tamayo@latest keygen -out "$GATEWAY_ISSUER"
  fi
fi

echo "starting gateway on $GATEWAY_PUBLIC_BASE"
exec go run ./cmd/gateway -addr "$GATEWAY_ADDR"
