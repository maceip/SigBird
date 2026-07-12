# Signature Image Hosting Gateway

- **Issue**: n/a (signature editor follow-up)
- **RFC**: n/a
- **ADR**: n/a
- **Status**: proposed
- **Summary**: Host signature images as WebP ≤256 KiB on S3, gated by a
  Lambda-style product gateway that mints and spends [tamayo](https://github.com/maceip/tamayo)
  private-identity tokens so anonymous bulk upload is not a free firehose.

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
3. Hold a **client holder key** (Ed25519 or PQ). Request an upload session;
   obtain a **private-identity** token whose blinded issuer signature covers
   that holder public key (**no email** in the token).
4. Present with a holder proof-of-possession; the gateway learns only an
   **origin-bound pseudonym**, then returns an S3 presigned PUT.
5. `PUT` the WebP bytes; store `https://<cdn-host>/<object-key>` in the
   signature HTML (not a data URI).

### Gateway (this service — product runtime)

Tamayo owns crypto + token profiles. **This gateway owns HTTP, durable spend
storage, S3, budgets, and abuse policy** (per tamayo’s implementation inventory).

```text
App                     Gateway                         Tamayo packages / issuer
 |                         |                                    |
 |-- POST /v1/sessions --->|                                    |
 |<-- nonce + origin       |                                    |
 |                         |                                    |
 |  (holder keypair is generated on the client; only the        |
 |   public key is sent — blind mint via assisted mint in DevX) |
 |                         |                                    |
 |-- POST assisted-mint -->|-- AuthorizeMint / BlindSign ------>|
 |   (holder_pub only)     |                                    |
 |<-- private-identity token (no seed, no email)                |
 |                         |                                    |
 |-- POST /v1/uploads ---->|-- VerifyPresentation + spend-once  |
 |   (holder PoP + sha256) |-- Presign S3 PUT (sha/len/type) -->|
 |<-- upload_url + public_url                                   |
 |                         |                                    |
 |-- PUT webp -----------> S3 (rejects bytes that differ)       |
```

#### HTTP surface (v1)

| Method |               Path                |                                                    Purpose                                                     |
|--------|-----------------------------------|----------------------------------------------------------------------------------------------------------------|
| `GET`  | `/healthz`                        | Liveness                                                                                                       |
| `GET`  | `/v1/issuer`                      | Public issuer info (algorithm, key version, public keys, origin)                                               |
| `POST` | `/v1/sessions`                    | Create upload session; returns `session_id`, `presentation_nonce_b64`, origin                                  |
| `POST` | `/v1/sessions/{id}/assisted-mint` | **DevX only**: server-side blind loop over a **client-supplied holder public key**. Never returns a seed.      |
| `POST` | `/v1/uploads`                     | Present holder PoP + content SHA-256 + byte length; spend the session nonce; return S3 PUT URL + public URL    |

#### Upload constraints (enforced at presign + PUT)

- `Content-Type: image/webp` only
- `Content-Length` ≤ **262144** (256 KiB)
- Content **SHA-256, length, and type are pinned at presign time** and the
  object store rejects a PUT whose bytes differ (S3: signed checksum headers;
  DevX memory store: same checks, single-use grant)
- Object key: `sig/{yyyy}/{mm}/{sha256-prefix}/{object-id}.webp`
- Public URL host allow-listed in the Android sanitizer

#### Abuse gate (why tamayo private-identity)

- **Private-identity token**: blinded issuer signature over the **client holder
  key**; presentation requires holder PoP. Verifier learns an **origin-bound
  pseudonym only** — never an email address.
- **Presentation nonce**: one successful `/v1/uploads` consumes the session
  nonce (409 on replay).
- **Budget**: tokenauth budget group for `private_identity`, keyed by
  `gate:bucket:group`. **The bucket is never client-named** — a client that
  could pick its own `bucket_id` would mint a fresh budget per request.
- **Sybil boundary (dev)**: the bucket is a salted hash of the request source
  IP, so each source spends only its own mints-per-window budget — one greedy
  client cannot starve other clients, and one client cannot fabricate new
  budgets. Known limits: shared NATs share a budget; a botnet brings many
  sources. That is acceptable for the DevX deployment because each mint is
  still one bounded WebP within a per-source window.
- **Sybil boundary (prod)**: the bucket must come from verified evidence the
  client cannot mint for free — an attestation `bucket_claim` (device-bound),
  or the `mailbox` gate's keyed HMAC bucket (account-bound; natural for a
  mail client, and the issuer still never learns the address). One abusive
  account exhausts only its own budget.
- **Session caps**: the unauthenticated sessions table is bounded globally
  (memory) and per source (fairness), so a session flood cannot lock out
  other clients.
- **No anonymous free PUT**: S3 bucket blocks public write; only gateway-minted
  short-lived presigned URLs can write, pinned to one sha/len/type.

### S3 / CDN

- Private bucket; public read via CloudFront (or bucket policy on `sig/*` GET only).
- Gateway IAM: `s3:PutObject` on `sig/*` only, via `PresignPutObject`.
- Optional: S3 Object Lambda / event to reject non-WebP magic bytes after upload
  (defense in depth; not required for DevX).

### DevX vs production mint

|               Mode                | Who runs PoMFRIT blind loop |                                             Notes                                              |
|-----------------------------------|-----------------------------|------------------------------------------------------------------------------------------------|
| **assisted-mint (DevX)**          | Gateway                     | Surfaces that Android cannot yet run PoMFRIT client-side; good friction log for tamayo/Android |
| **client blind mint (prod goal)** | App                         | App blinds locally, calls issuer `/v1/blind-sign` (or gateway proxy), finalizes token          |

Assisted mint is explicitly labeled and disabled unless `GATEWAY_MODE=dev`.

**Holder-key custody is client-side in both modes.** The app generates the
Ed25519 holder keypair on-device and sends only the public key; the gateway
refuses assisted-mint requests without `holder_pub_b64` and never returns a
seed. What assisted mint still concedes (until a Kotlin PoMFRIT port exists)
is that the gateway sees the token input it blinds — so mint-time
issuer/verifier unlinkability only holds in client-blind-mint mode. Holder
proof-of-possession, however, is real in both modes: only the device can sign
presentations.

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

