#!/usr/bin/env bash
# End-to-end DevX smoke (private-identity showcase).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
export GOTOOLCHAIN="${GOTOOLCHAIN:-go1.26.4}"
export GATEWAY_PUBLIC_BASE="${GATEWAY_PUBLIC_BASE:-http://127.0.0.1:8790}"
exec go run ./cmd/smoke
