#!/usr/bin/env bash
# End-to-end DevX smoke: session → assisted-mint → upload → PUT → GET
set -euo pipefail
BASE="${GATEWAY_PUBLIC_BASE:-http://127.0.0.1:8790}"

echo "== health =="
curl -fsS "$BASE/healthz"
echo

echo "== session =="
SESSION_JSON=$(curl -fsS -X POST "$BASE/v1/sessions" -H 'content-type: application/json' -d '{}')
echo "$SESSION_JSON"
SID=$(echo "$SESSION_JSON" | python3 -c 'import sys,json; print(json.load(sys.stdin)["session_id"])')

echo "== assisted-mint =="
MINT_JSON=$(curl -fsS -X POST "$BASE/v1/sessions/$SID/assisted-mint" -H 'content-type: application/json' -d '{}')
echo "$MINT_JSON" | python3 -c 'import sys,json; d=json.load(sys.stdin); print("token_bytes", len(d["token_b64"]))'
TOKEN=$(echo "$MINT_JSON" | python3 -c 'import sys,json; print(json.load(sys.stdin)["token_b64"])')

# Tiny fake "webp" payload (gateway does not sniff magic in DevX memory mode)
PAYLOAD=$(python3 - <<'PY'
import hashlib, os
data = b"RIFF" + b"\x00"*12 + b"WEBP" + os.urandom(1024)
open("/tmp/sig-smoke.webp","wb").write(data)
print(hashlib.sha256(data).hexdigest())
print(len(data))
PY
)
SHA=$(echo "$PAYLOAD" | head -1)
LEN=$(echo "$PAYLOAD" | tail -1)

echo "== uploads =="
UP_JSON=$(curl -fsS -X POST "$BASE/v1/uploads" -H 'content-type: application/json' -d "$(python3 - <<PY
import json
print(json.dumps({
  "session_id": "$SID",
  "token_b64": "$TOKEN",
  "content_sha256_hex": "$SHA",
  "content_length": int("$LEN"),
  "content_type": "image/webp",
}))
PY
)")
echo "$UP_JSON"
UPLOAD_URL=$(echo "$UP_JSON" | python3 -c 'import sys,json; print(json.load(sys.stdin)["upload_url"])')
PUBLIC_URL=$(echo "$UP_JSON" | python3 -c 'import sys,json; print(json.load(sys.stdin)["public_url"])')

echo "== PUT =="
curl -fsS -X PUT "$UPLOAD_URL" -H 'content-type: image/webp' --data-binary @/tmp/sig-smoke.webp
echo "put ok"

echo "== GET =="
curl -fsS -o /tmp/sig-smoke-out.webp "$PUBLIC_URL"
cmp /tmp/sig-smoke.webp /tmp/sig-smoke-out.webp
echo "RESULT: PASS  public_url=$PUBLIC_URL"
