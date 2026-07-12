package core_test

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/maceip/tamayo/tokenauth"
	"github.com/maceip/tamayo/tokenprofile"

	"github.com/maceip/SigBird/services/signature-image-gateway/internal/core"
)

// holder is a client-side keypair: the private key never goes to the gateway.
type holder struct {
	pub  ed25519.PublicKey
	priv ed25519.PrivateKey
}

func newHolder(t *testing.T) holder {
	t.Helper()
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	return holder{pub: pub, priv: priv}
}

func (h holder) pubB64() string {
	return base64.RawURLEncoding.EncodeToString(h.pub)
}

func (h holder) present(t *testing.T, origin, nonceB64, tokenB64 string, issuedAt int64) string {
	t.Helper()
	tokenBytes, err := base64.RawURLEncoding.DecodeString(tokenB64)
	if err != nil {
		t.Fatal(err)
	}
	token, err := tokenprofile.ParsePrivateIdentityToken(tokenBytes)
	if err != nil {
		t.Fatal(err)
	}
	nonceRaw, err := base64.RawURLEncoding.DecodeString(nonceB64)
	if err != nil || len(nonceRaw) != 32 {
		t.Fatalf("bad nonce: %v", err)
	}
	var nonce [32]byte
	copy(nonce[:], nonceRaw)
	msg := tokenprofile.PrivateIdentityPresentationMessage(origin, nonce, token.Digest(), issuedAt)
	return base64.RawURLEncoding.EncodeToString(ed25519.Sign(h.priv, msg))
}

func TestPrivateIdentityUploadShowcase(t *testing.T) {
	gw, mem := testGateway(t)
	h := newHolder(t)

	sess := postJSON(t, gw, "POST", "/v1/sessions", map[string]any{})
	if sess["token_family"] != "private_identity" {
		t.Fatalf("token_family=%v", sess["token_family"])
	}
	if sess["email"] != nil {
		t.Fatalf("email should be null, got %v", sess["email"])
	}
	sid := sess["session_id"].(string)
	origin := sess["origin"].(string)
	nonceB64 := sess["presentation_nonce_b64"].(string)

	mint := postJSON(t, gw, "POST", "/v1/sessions/"+sid+"/assisted-mint", map[string]any{
		"holder_pub_b64": h.pubB64(),
	})
	if mint["email"] != nil {
		t.Fatalf("mint email should be null")
	}
	if mint["pseudonym_hex"] == nil || mint["pseudonym_hex"] == "" {
		t.Fatal("expected pseudonym_hex in mint response")
	}
	if _, leaked := mint["holder_seed_b64"]; leaked {
		t.Fatal("mint must never return a holder seed")
	}
	if mint["holder_pub_b64"] != h.pubB64() {
		t.Fatalf("mint must echo the client holder key, got %v", mint["holder_pub_b64"])
	}
	tokenB64 := mint["token_b64"].(string)

	payload := bytes.Repeat([]byte("w"), 2048)
	sum := sha256.Sum256(payload)
	issuedAt := time.Now().Unix()

	up := postJSON(t, gw, "POST", "/v1/uploads", map[string]any{
		"session_id":         sid,
		"token_b64":          tokenB64,
		"signature_b64":      h.present(t, origin, nonceB64, tokenB64, issuedAt),
		"issued_at":          issuedAt,
		"content_sha256_hex": hex.EncodeToString(sum[:]),
		"content_length":     len(payload),
		"content_type":       "image/webp",
	})
	if up["pseudonym_hex"] != mint["pseudonym_hex"] {
		t.Fatalf("pseudonym mismatch mint=%v upload=%v", mint["pseudonym_hex"], up["pseudonym_hex"])
	}
	if up["email"] != nil {
		t.Fatal("upload must not expose email")
	}
	key := up["object_key"].(string)
	if err := mem.Put(key, payload, "image/webp"); err != nil {
		t.Fatal(err)
	}

	// Replay same presentation nonce must be rejected.
	sess2 := postJSON(t, gw, "POST", "/v1/sessions", map[string]any{})
	resp := raw(t, gw, "POST", "/v1/uploads", map[string]any{
		"session_id":         sess2["session_id"],
		"token_b64":          tokenB64,
		"signature_b64":      h.present(t, origin, nonceB64, tokenB64, issuedAt),
		"issued_at":          issuedAt,
		"content_sha256_hex": hex.EncodeToString(sum[:]),
		"content_length":     len(payload),
		"content_type":       "image/webp",
	})
	// Different session has different nonce — signature won't match that nonce,
	// so expect 401 (bad PoP) rather than 200.
	if resp.Status == 200 {
		t.Fatalf("expected reject, got 200 body=%s", resp.Body)
	}
}

func TestAssistedMintRequiresClientHolderKey(t *testing.T) {
	gw, _ := testGateway(t)
	sess := postJSON(t, gw, "POST", "/v1/sessions", map[string]any{})
	sid := sess["session_id"].(string)

	resp := raw(t, gw, "POST", "/v1/sessions/"+sid+"/assisted-mint", map[string]any{})
	if resp.Status != 400 {
		t.Fatalf("status=%d want 400 body=%s", resp.Status, resp.Body)
	}

	resp = raw(t, gw, "POST", "/v1/sessions/"+sid+"/assisted-mint", map[string]any{
		"holder_pub_b64": base64.RawURLEncoding.EncodeToString([]byte("short")),
	})
	if resp.Status != 400 {
		t.Fatalf("status=%d want 400 body=%s", resp.Status, resp.Body)
	}
}

func TestAssistedMintDisabledInProd(t *testing.T) {
	issuer := newIssuer(t)
	policy := compilePolicy(t)
	mem := core.NewMemoryPresigner("http://example.test")
	gw, err := core.New(core.Config{Mode: "prod"}, issuer, policy, mem, nil)
	if err != nil {
		t.Fatal(err)
	}
	sess := postJSON(t, gw, "POST", "/v1/sessions", map[string]any{})
	sid := sess["session_id"].(string)
	if sess["assisted_mint_available"] != false {
		t.Fatalf("assisted mint should be unavailable in prod")
	}
	resp := raw(t, gw, "POST", "/v1/sessions/"+sid+"/assisted-mint", map[string]any{
		"holder_pub_b64": newHolder(t).pubB64(),
	})
	if resp.Status != 403 {
		t.Fatalf("status=%d want 403 body=%s", resp.Status, resp.Body)
	}
}

func TestRejectOversize(t *testing.T) {
	gw, _ := testGateway(t)
	h := newHolder(t)
	sess := postJSON(t, gw, "POST", "/v1/sessions", map[string]any{})
	sid := sess["session_id"].(string)
	mint := postJSON(t, gw, "POST", "/v1/sessions/"+sid+"/assisted-mint", map[string]any{
		"holder_pub_b64": h.pubB64(),
	})
	tokenB64 := mint["token_b64"].(string)
	issuedAt := time.Now().Unix()
	sum := sha256.Sum256([]byte("x"))
	resp := raw(t, gw, "POST", "/v1/uploads", map[string]any{
		"session_id":         sid,
		"token_b64":          tokenB64,
		"signature_b64":      h.present(t, sess["origin"].(string), sess["presentation_nonce_b64"].(string), tokenB64, issuedAt),
		"issued_at":          issuedAt,
		"content_sha256_hex": hex.EncodeToString(sum[:]),
		"content_length":     core.MaxUploadBytes + 1,
		"content_type":       "image/webp",
	})
	if resp.Status != 400 {
		t.Fatalf("status=%d want 400 body=%s", resp.Status, resp.Body)
	}
}

func TestRejectMissingIssuedAt(t *testing.T) {
	gw, _ := testGateway(t)
	h := newHolder(t)
	sess := postJSON(t, gw, "POST", "/v1/sessions", map[string]any{})
	sid := sess["session_id"].(string)
	mint := postJSON(t, gw, "POST", "/v1/sessions/"+sid+"/assisted-mint", map[string]any{
		"holder_pub_b64": h.pubB64(),
	})
	tokenB64 := mint["token_b64"].(string)
	sum := sha256.Sum256([]byte("x"))
	resp := raw(t, gw, "POST", "/v1/uploads", map[string]any{
		"session_id":         sid,
		"token_b64":          tokenB64,
		"signature_b64":      h.present(t, sess["origin"].(string), sess["presentation_nonce_b64"].(string), tokenB64, time.Now().Unix()),
		"content_sha256_hex": hex.EncodeToString(sum[:]),
		"content_length":     16,
		"content_type":       "image/webp",
	})
	if resp.Status != 400 {
		t.Fatalf("status=%d want 400 body=%s", resp.Status, resp.Body)
	}
}

func TestMemoryPresignerEnforcesGrant(t *testing.T) {
	mem := core.NewMemoryPresigner("http://example.test")
	payload := []byte("RIFFxxxxWEBPdata")
	sum := sha256.Sum256(payload)
	_, _, err := mem.PresignPut(context.Background(), core.PresignPutInput{
		Key:           "sig/2026/07/aa/k.webp",
		ContentType:   "image/webp",
		ContentLength: int64(len(payload)),
		ContentSHA256: sum,
		TTL:           time.Minute,
	})
	if err != nil {
		t.Fatal(err)
	}

	if err := mem.Put("sig/2026/07/aa/other.webp", payload, "image/webp"); err == nil {
		t.Fatal("put without a grant must fail")
	}
	if err := mem.Put("sig/2026/07/aa/k.webp", payload, "text/html"); err == nil {
		t.Fatal("content-type mismatch must fail")
	}
	if err := mem.Put("sig/2026/07/aa/k.webp", append(payload, 'x'), "image/webp"); err == nil {
		t.Fatal("length mismatch must fail")
	}
	tampered := append([]byte(nil), payload...)
	tampered[0] ^= 0xff
	if err := mem.Put("sig/2026/07/aa/k.webp", tampered, "image/webp"); err == nil {
		t.Fatal("checksum mismatch must fail")
	}
	if err := mem.Put("sig/2026/07/aa/k.webp", payload, "image/webp"); err != nil {
		t.Fatalf("matching put must succeed: %v", err)
	}
	if err := mem.Put("sig/2026/07/aa/k.webp", payload, "image/webp"); err == nil {
		t.Fatal("grant must be single-use")
	}
}

func TestUploadAllowsRetryAfterPresignFailure(t *testing.T) {
	// Arrange
	issuer := newIssuer(t)
	policy := compilePolicy(t)
	presigner := &flakyPresigner{}
	gw, err := core.New(core.Config{Mode: "dev"}, issuer, policy, presigner, nil)
	if err != nil {
		t.Fatal(err)
	}
	h := newHolder(t)

	sess := postJSON(t, gw, "POST", "/v1/sessions", map[string]any{})
	sid := sess["session_id"].(string)
	mint := postJSON(t, gw, "POST", "/v1/sessions/"+sid+"/assisted-mint", map[string]any{
		"holder_pub_b64": h.pubB64(),
	})
	tokenB64 := mint["token_b64"].(string)
	issuedAt := time.Now().Unix()
	sum := sha256.Sum256([]byte("retry"))
	body := map[string]any{
		"session_id":         sid,
		"token_b64":          tokenB64,
		"signature_b64":      h.present(t, sess["origin"].(string), sess["presentation_nonce_b64"].(string), tokenB64, issuedAt),
		"issued_at":          issuedAt,
		"content_sha256_hex": hex.EncodeToString(sum[:]),
		"content_length":     len(sum),
		"content_type":       "image/webp",
	}

	// Act
	firstResponse := raw(t, gw, "POST", "/v1/uploads", body)
	secondResponse := raw(t, gw, "POST", "/v1/uploads", body)

	// Assert
	if firstResponse.Status != 500 {
		t.Fatalf("first status=%d want 500 body=%s", firstResponse.Status, firstResponse.Body)
	}
	if secondResponse.Status != 200 {
		t.Fatalf("second status=%d want 200 body=%s", secondResponse.Status, secondResponse.Body)
	}
	if presigner.attempts != 2 {
		t.Fatalf("attempts=%d want 2", presigner.attempts)
	}
}

func testGateway(t *testing.T) (*core.Gateway, *core.MemoryPresigner) {
	t.Helper()
	issuer := newIssuer(t)
	policy := compilePolicy(t)
	mem := core.NewMemoryPresigner("http://example.test")
	gw, err := core.New(core.Config{Mode: "dev"}, issuer, policy, mem, nil)
	if err != nil {
		t.Fatal(err)
	}
	return gw, mem
}

func newIssuer(t *testing.T) *tokenprofile.Issuer {
	t.Helper()
	issuer, err := tokenprofile.NewIssuer(1, nil)
	if err != nil {
		t.Fatalf("NewIssuer: %v", err)
	}
	return issuer
}

func compilePolicy(t *testing.T) *tokenauth.Policy {
	t.Helper()
	raw, err := os.ReadFile(filepath.Join("..", "..", "policy.dev.json"))
	if err != nil {
		raw, err = os.ReadFile("policy.dev.json")
	}
	if err != nil {
		t.Fatal(err)
	}
	policy, err := tokenauth.CompileJSON(raw)
	if err != nil {
		t.Fatal(err)
	}
	return policy
}

func postJSON(t *testing.T, gw *core.Gateway, method, path string, body any) map[string]any {
	t.Helper()
	resp := raw(t, gw, method, path, body)
	if resp.Status != 200 {
		t.Fatalf("%s %s status=%d body=%s", method, path, resp.Status, resp.Body)
	}
	var out map[string]any
	if err := json.Unmarshal(resp.Body, &out); err != nil {
		t.Fatal(err)
	}
	return out
}

func raw(t *testing.T, gw *core.Gateway, method, path string, body any) core.Response {
	t.Helper()
	b, err := json.Marshal(body)
	if err != nil {
		t.Fatal(err)
	}
	return gw.Handle(context.Background(), core.Request{
		Method: method,
		Path:   path,
		Body:   b,
	})
}

type flakyPresigner struct {
	attempts int
}

func (p *flakyPresigner) PresignPut(
	ctx context.Context,
	in core.PresignPutInput,
) (uploadURL, publicURL string, err error) {
	p.attempts++
	if p.attempts == 1 {
		return "", "", errors.New("temporary presign failure")
	}
	return "http://upload.example.test", "http://public.example.test/object.webp", nil
}
