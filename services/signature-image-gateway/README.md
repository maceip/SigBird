# Signature Image Gateway (reference)

Portable **product** gateway for hosting signature images (WebP ≤ 256 KiB).

## Token showcase: private identity (no email)

Auth uses tamayo **private-identity** tokens:

1. Client **generates** an Ed25519 (or PQ) **holder key on-device** and sends
   only the public key — the gateway never generates keys for a client and
   never returns a seed
2. Issuer places a **blinded PoMFRIT signature** over that holder public key
3. Client **presents** with a proof-of-possession of the holder key
4. Verifier learns only an **origin-bound pseudonym** — never an email address

```text
holder key (client) ──blind──► issuer signature ──present+PoP──► pseudonym@origin
                                      ▲
                               no email in token
```

Burn tokens remain available in tamayo itself; this gateway’s happy path is
the private-identity showcase above.

Tamayo owns crypto + token profiles. **This service** owns HTTP, sessions,
presentation-nonce replay, S3 (or DevX stand-in), and abuse policy.

### Anti-spam shape

Tokens are cheap by design; scarcity lives in the mint gate. The tokenauth
budget is keyed per **bucket**, and the bucket is server-owned: in dev it is
a salted hash of the request source, so every source has its own
mints-per-window budget — a greedy client exhausts only itself, and no
client can name its own bucket to escape the limit. Sessions are capped
globally and per source. In production the bucket comes from verified
evidence (attestation `bucket_claim`, or the tamayo `mailbox` HMAC bucket —
account-bound without revealing the address).

## Design goals

1. **One core, many edges** — `internal/core.Gateway.Handle(Request) Response`
2. **Straightforward DevX** — `scripts/dev-serve.sh` + `scripts/smoke.sh`
3. **Honest about production** — `assisted-mint` is **dev-only**

## Quick start

```bash
cd services/signature-image-gateway
export GOTOOLCHAIN=go1.26.4
go run github.com/maceip/tamayo/cmd/tamayo@latest keygen -out issuer.json

./scripts/dev-serve.sh          # terminal 1
./scripts/smoke.sh              # terminal 2 — RESULT: PASS
```

Smoke prints `token_family=private_identity`, `email=<nil>`, and the
`pseudonym` after the holder PoP.

## API (v1)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/healthz` | Liveness (`token_family`, `email: null`) |
| `GET` | `/v1/issuer` | Public issuer info + origin |
| `POST` | `/v1/sessions` | Session + presentation nonce |
| `POST` | `/v1/sessions/{id}/assisted-mint` | **Dev** — blind-mint over the client's `holder_pub_b64` (required) |
| `POST` | `/v1/uploads` | Holder PoP → spend nonce → presigned PUT |
| `PUT` | `/v1/dev-put/{key}` | DevX memory store (enforces presigned sha/len/type) |
| `GET` | `/v1/dev-get/{key}` | DevX fetch |

Upload constraints: `Content-Type: image/webp`, body ≤ **262144** bytes, and
the PUT body must match the SHA-256 + length presented at `/v1/uploads` —
the presigned grant is single-use and expires after 5 minutes.

## Layout

```
cmd/gateway/          local HTTP server
cmd/lambda/           AWS Lambda entry
cmd/smoke/            private-identity showcase client
internal/core/        portable Gateway
internal/bootstrap/   env + issuer/policy loading
internal/adapters/    http + lambda
policy.dev.json       tokenauth policy (private_identity)
scripts/              serve + smoke
```

## Env

| Variable | Default | Meaning |
| --- | --- | --- |
| `GATEWAY_MODE` | `dev` | `dev` enables assisted-mint |
| `GATEWAY_ISSUER` | `issuer.json` | tamayo issuer key-epoch |
| `GATEWAY_POLICY` | `policy.dev.json` | tokenauth policy |
| `GATEWAY_PUBLIC_BASE` | `http://127.0.0.1:8790` | public origin for URLs |
| `GATEWAY_CHALLENGE_PREFIX` | `sigbird-signature-upload` | RP **origin** for pseudonyms |
| `GATEWAY_PRESIGNER` | `memory` | DevX object store |
| `GATEWAY_ADDR` | `127.0.0.1:8790` | listen address |

## Tests

```bash
export GOTOOLCHAIN=go1.26.4
go test ./...
```
