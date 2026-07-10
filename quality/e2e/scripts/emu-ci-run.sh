#!/usr/bin/env bash
# Submit an APK to emu.devkeys.net, poll until done, download logs + screen.
#
# Usage:
#   export EMU_API_TOKEN=...   # or EMU_TOKEN
#   ./quality/e2e/scripts/emu-ci-run.sh <apk-path> [activity] [run_seconds]
#
# Examples:
#   ./quality/e2e/scripts/emu-ci-run.sh \
#     app-thunderbird/build/outputs/apk/full/debug/app-thunderbird-full-debug.apk \
#     net.thunderbird.android.debug/net.thunderbird.app.common.MainActivity \
#     90
#
# Notes:
# - This API installs + launches + captures logcat/screen. It does NOT run Maestro.
# - Maestro needs `adb connect` via SSH local-forward (IP-allowlisted), which this
#   cloud agent cannot use unless SSH is opened to the agent egress IP.

set -euo pipefail

APK="${1:?apk path required}"
ACTIVITY="${2:-}"
RUN_SECONDS="${3:-60}"
BASE_URL="${EMU_BASE_URL:-https://emu.devkeys.net}"
TOKEN="${EMU_API_TOKEN:-${EMU_TOKEN:-}}"
# Some secret stores append a trailing '%'; strip it so Bearer auth works.
TOKEN="${TOKEN%"%"}"
OUT_DIR="${EMU_OUT_DIR:-artifacts/emu-run}"

if [[ -z "$TOKEN" ]]; then
  echo "error: set EMU_API_TOKEN (or EMU_TOKEN)" >&2
  exit 2
fi
if [[ ! -f "$APK" ]]; then
  echo "error: apk not found: $APK" >&2
  exit 2
fi
if (( RUN_SECONDS < 5 || RUN_SECONDS > 180 )); then
  echo "error: run_seconds must be 5..180 (got $RUN_SECONDS)" >&2
  exit 2
fi

auth=(-H "Authorization: Bearer ${TOKEN}")
mkdir -p "$OUT_DIR"

echo "==> healthz"
HEALTH=$(curl -fsS "${BASE_URL}/healthz")
echo "$HEALTH"
python3 - <<'PY' "$HEALTH"
import json,sys
h=json.loads(sys.argv[1])
if not h.get("ok"):
    raise SystemExit("healthz not ok")
if not h.get("emulator_ready"):
    raise SystemExit("emulator not ready")
PY

echo "==> submit $(basename "$APK") (run_seconds=${RUN_SECONDS})"
FORM=(-F "apk=@${APK}" -F "run_seconds=${RUN_SECONDS}")
if [[ -n "$ACTIVITY" ]]; then
  FORM+=(-F "activity=${ACTIVITY}")
fi
RESP=$(curl -fsS "${auth[@]}" "${FORM[@]}" "${BASE_URL}/run")
echo "$RESP"
JOB=$(python3 - <<'PY' "$RESP"
import json,sys
print(json.loads(sys.argv[1])["job_id"])
PY
)
JOB_DIR="${OUT_DIR}/job-${JOB}"
mkdir -p "$JOB_DIR"
echo "$RESP" > "${JOB_DIR}/submit.json"
echo "JOB=${JOB}"

echo "==> poll"
STATUS=""
for _ in $(seq 1 90); do
  META=$(curl -fsS "${auth[@]}" "${BASE_URL}/jobs/${JOB}")
  STATUS=$(python3 - <<'PY' "$META"
import json,sys
print(json.loads(sys.argv[1]).get("status",""))
PY
)
  echo "status=${STATUS}"
  case "$STATUS" in
    completed|failed)
      echo "$META" > "${JOB_DIR}/job.json"
      break
      ;;
  esac
  sleep 3
done

if [[ "$STATUS" != "completed" && "$STATUS" != "failed" ]]; then
  echo "error: timed out waiting for job ${JOB} (last status=${STATUS})" >&2
  exit 1
fi

echo "==> download logs"
curl -fsS "${auth[@]}" "${BASE_URL}/jobs/${JOB}/logs" -o "${JOB_DIR}/logs.txt" || true
echo "==> download screen"
HTTP=$(curl -sS -o "${JOB_DIR}/screen.mp4" -w "%{http_code}" "${auth[@]}" "${BASE_URL}/jobs/${JOB}/screen.mp4" || true)
echo "screen_http=${HTTP}"
[[ "$HTTP" == "200" ]] || rm -f "${JOB_DIR}/screen.mp4"

python3 - <<'PY' "$JOB_DIR"
import pathlib,re,sys
job_dir=pathlib.Path(sys.argv[1])
meta=job_dir.joinpath("job.json").read_text(encoding="utf-8", errors="replace")
logs=job_dir.joinpath("logs.txt").read_text(encoding="utf-8", errors="replace") if job_dir.joinpath("logs.txt").exists() else ""
print("--- job ---")
print(meta)
patterns=[r"FATAL EXCEPTION", r"AndroidRuntime", r"ANR in", r"Process: .* crashed", r"AssertionError"]
hits=[]
for p in patterns:
    for m in re.finditer(p, logs):
        line=logs[max(0,m.start()-80):m.end()+160].replace("\n"," ")
        hits.append(line)
        if len(hits)>=20: break
    if len(hits)>=20: break
if hits:
    print("--- crash-like log hits ---")
    for h in hits: print(h)
status='"status": "failed"' in meta or '"status":"failed"' in meta
if status or hits:
    raise SystemExit(1)
print("emu run ok")
PY
