# Signature Image Hosting Gateway

- **Issue**: n/a (signature editor follow-up)
- **RFC**: n/a
- **ADR**: n/a
- **Status**: proposed
- **Summary**: Host signature images as WebP ≤256 KiB on S3, gated by a
  Lambda-style product gateway that mints and spends [tamayo](https://github.com/maceip/tamayo)
  burn tokens so anonymous bulk upload is not a free firehose.

## Current State

Signature images are base64 `data:` URIs embedded in the identity signature
HTML string (SQLite preferences). There is no remote hosting. The HTML
sanitizer strips `https://` image sources. Large data URIs previously froze
identity/composition screens.

## Proposed Design

### Client (Android)

1. Accept PNG / JPEG / GIF (first frame) / WebP input.
2. Decode → aggressively re-encode as **lossy WebP**, shrinking dimensions and
   quality until the payload is **≤ 256 KiB** (or reject if impossible).
3. Request an upload session from the gateway; complete it by presenting a
   **tamayo burn token** bound to the upload challenge.
4. `PUT` the WebP bytes to the returned S3 presigned URL.
5. Store `https://<cdn-host>/<object-key>` in the signature HTML (not a data URI).

### Gateway (this service — product runtime)

Tamayo owns crypto + token profiles. **This gateway owns HTTP, durable spend
storage, S3, budgets, and abuse policy** (per tamayo’s implementation inventory).

```text
App                     Gateway                         Tamayo packages / issuer
 |                         |                                    |
 |-- POST /v1/sessions --->|                                    |
 |<-- challenge + mint hint|                                    |
 |                         |                                    |
 |  (blind mint; DevX may use assisted mint — see below)        |
 |                         |                                    |
 |-- POST /v1/uploads ---->|-- AuthorizeMint / BlindSign ------>|
 |   (burn token + meta)   |-- VerifyBurn + spend-once -------->|
 |                         |-- Presign S3 PUT (256KiB, webp) -->|
 |<-- upload_url + public_url                                   |
 |                         |                                    |
 |-- PUT webp -----------> S3                                   |
```

#### HTTP surface (v1)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/healthz` | Liveness |
| `POST` | `/v1/sessions` | Create upload session; returns `session_id`, `challenge_b64`, issuer info |
| `POST` | `/v1/sessions/{id}/assisted-mint` | **DevX only**: server-side blind mint → burn token (software-witness). Not for production. |
| `POST` | `/v1/uploads` | Present burn token + content SHA-256 + byte length; spend token; return S3 PUT URL + public URL |
| `POST` | `/v1/tokens/verify` | Optional debug: verify burn without issuing upload (no S3) |

#### Upload constraints (enforced at presign + documented for client)

- `Content-Type: image/webp` only
- `Content-Length` ≤ **262144** (256 KiB)
- Object key: `sig/{yyyy}/{mm}/{sha256-prefix}/{object-id}.webp`
- Public URL host allow-listed in the Android sanitizer

#### Abuse gate (why tamayo)

- **Burn token**: one successful `/v1/uploads` spends the token (409 on replay).
- **Budget**: tokenauth budget group (e.g. 16 burns / hour / eligibility bucket).
- **Eligibility**: production should use mailbox / account-bound gates; DevX uses
  `tee` + `software-witness` measurement allow-list (tamayo example policy).
- **No anonymous free PUT**: S3 bucket blocks public write; only gateway-minted
  short-lived presigned URLs can write.

### S3 / CDN

- Private bucket; public read via CloudFront (or bucket policy on `sig/*` GET only).
- Gateway IAM: `s3:PutObject` on `sig/*` only, via `PresignPutObject`.
- Optional: S3 Object Lambda / event to reject non-WebP magic bytes after upload
  (defense in depth; not required for DevX).

### DevX vs production mint

| Mode | Who runs PoMFRIT blind loop | Notes |
| --- | --- | --- |
| **assisted-mint (DevX)** | Gateway | Surfaces that Android cannot yet run PoMFRIT client-side; good friction log for tamayo/Android |
| **client blind mint (prod goal)** | App | App blinds locally, calls issuer `/v1/blind-sign` (or gateway proxy), finalizes token |

Assisted mint is explicitly labeled and disabled unless `GATEWAY_MODE=dev`.

## Migration and Rollout

1. Land WebP ≤256 KiB encoder in `library/signature-editor` (local-only still works).
2. Land gateway scaffold + local/SAM-style runbook; point at local tamayo or embed.
3. Allow-list CDN host in `SignatureHtmlSanitizer`.
4. Wire Android upload client behind a feature flag / build config endpoint.
5. Production: mailbox-gated eligibility, durable spend journal, real AWS account,
   remove assisted-mint.

## Testing and Verification

- Gateway unit tests: session create, assisted mint, upload happy path, double-spend 409,
  oversize reject, wrong content-type reject.
- Tamayo: `go test` token packages; `tamayo demo`; `tamayo serve` smoke.
- Android: Robolectric tests for WebP budget and GIF/PNG/JPEG inputs.
- Manual DevX: encode → assisted-mint → upload → fetch public URL.

## Open Technical Questions

1. **Email client WebP support** — Outlook/Gmail historically weak for WebP in
   HTML mail. May need JPEG/PNG at the CDN edge, or dual upload. Track separately.
2. **Android PoMFRIT client** — no Kotlin port; assisted-mint is the DevX bridge.
3. **Account binding** — bind burns to Thunderbird account UUID / mailbox bucket
   without letting the issuer learn the address (tamayo `mailbox` package).
4. **Retention / deletion** — when user removes signature image, delete S3 object?
5. **Where the gateway repo lives long-term** — scaffolded under
   `services/signature-image-gateway` in this tree as the **reference product
   runtime** (portable core + HTTP/Lambda adapters). May split later.

## Reference implementation notes

Built to mirror tamayo’s own boundary:

- **Crypto / tokens:** import `tokenprofile` + `tokenauth` + `tokenservice`
  (same packages `cmd/tamayo serve` wires).
- **Product HTTP + storage + S3:** owned here, not upstreamed into tamayo.
- **Portable core:** `Gateway.Handle` has no edge SDK types so the same binary
  logic can sit behind local HTTP, Lambda, or a future Workers adapter.
