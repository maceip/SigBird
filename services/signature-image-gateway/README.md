# Signature Image Gateway (reference)

Portable **product** gateway for hosting signature images (WebP ≤ 256 KiB) behind
[tamayo](https://github.com/maceip/tamayo) burn tokens.

Tamayo owns crypto + token profiles. **This service** owns HTTP, sessions,
spend storage, S3 (or a DevX stand-in), and abuse policy — the same boundary
documented in tamayo’s `cmd/tamayo serve` and `docs/implementation-inventory.md`.

## Design goals

1. **One core, many edges** — business logic lives in `internal/core` as
   `Gateway.Handle(ctx, Request) Response`. No `net/http` or Lambda types in
   core. Adapters are thin:
   - `internal/adapters/httpadapter` → local / Cloud Run / any HTTP host
   - `internal/adapters/lambdaadapter` → AWS Lambda Function URL / HTTP API
2. **Straightforward DevX** — `scripts/dev-serve.sh` + `scripts/smoke.sh`.
   No AWS account required for the happy path (`GATEWAY_PRESIGNER=memory`).
3. **Honest about production** — `assisted-mint` is **dev-only** (server runs
   the PoMFRIT blind loop). Production clients must blind locally.

## Quick start

```bash
cd services/signature-image-gateway
export GOTOOLCHAIN=go1.26.4

# one-time: issuer key (or: go run github.com/maceip/tamayo/cmd/tamayo@latest keygen -out issuer.json)
go run github.com/maceip/tamayo/cmd/tamayo@latest keygen -out issuer.json

./scripts/dev-serve.sh          # terminal 1 — http://127.0.0.1:8790
./scripts/smoke.sh              # terminal 2 — RESULT: PASS
```

## API (v1)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/healthz` | Liveness |
| `GET` | `/v1/issuer` | Public issuer info |
| `POST` | `/v1/sessions` | Create upload session + challenge |
| `POST` | `/v1/sessions/{id}/assisted-mint` | **Dev only** — mint burn token |
| `POST` | `/v1/uploads` | Spend burn token → presigned PUT + public URL |
| `PUT` | `/v1/dev-put/{key}` | DevX memory store (when `GATEWAY_PRESIGNER=memory`) |
| `GET` | `/v1/dev-get/{key}` | DevX fetch |

Upload constraints: `Content-Type: image/webp`, body ≤ **262144** bytes.

## Layout

```
cmd/gateway/          local HTTP server (start here)
cmd/lambda/           AWS Lambda entry (same core)
internal/core/        portable Gateway + MemoryPresigner
internal/bootstrap/   env + issuer/policy loading
internal/adapters/    http + lambda
policy.dev.json       tamayo tokenauth policy for DevX
scripts/              serve + smoke
```

## Edge portability

To run on another edge (Cloudflare Workers, Fastly, etc.):

1. Keep using `core.Gateway`.
2. Add `internal/adapters/<platform>` that maps the platform event →
   `core.Request` / `core.Response` (copy `lambdaadapter` as a template).
3. Do **not** fork mint/verify logic into the adapter.

S3: implement `core.Presigner` and select it with `GATEWAY_PRESIGNER=…`
(currently `memory` only; real S3 is the next seam to fill).

## Env

| Variable | Default | Meaning |
| --- | --- | --- |
| `GATEWAY_MODE` | `dev` | `dev` enables assisted-mint |
| `GATEWAY_ISSUER` | `issuer.json` | tamayo issuer key-epoch file |
| `GATEWAY_POLICY` | `policy.dev.json` | tokenauth policy JSON |
| `GATEWAY_PUBLIC_BASE` | `http://127.0.0.1:8790` | public origin for URLs |
| `GATEWAY_PRESIGNER` | `memory` | `memory` (DevX) |
| `GATEWAY_ADDR` | `127.0.0.1:8790` | listen address for `cmd/gateway` |

## Tests

```bash
export GOTOOLCHAIN=go1.26.4
go test ./...
```
