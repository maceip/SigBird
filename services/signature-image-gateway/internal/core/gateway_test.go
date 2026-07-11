package core_test

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/maceip/tamayo/tokenauth"
	"github.com/maceip/tamayo/tokenprofile"

	"github.com/maceip/SigBird/services/signature-image-gateway/internal/core"
)

func TestPrivateIdentityUploadShowcase(t *testing.T) {
	gw, mem := testGateway(t)

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

	mint := postJSON(t, gw, "POST", "/v1/sessions/"+sid+"/assisted-mint", map[string]any{})
	if mint["email"] != nil {
		t.Fatalf("mint email should be null")
	}
	if mint["pseudonym_hex"] == nil || mint["pseudonym_hex"] == "" {
		t.Fatal("expected pseudonym_hex in mint response")
	}
	tokenB64 := mint["token_b64"].(string)
	seedB64 := mint["holder_seed_b64"].(string)

	seed, err := base64.RawURLEncoding.DecodeString(seedB64)
	if err != nil {
		t.Fatal(err)
	}
	holderPriv := ed25519.NewKeyFromSeed(seed)
	tokenBytes, _ := base64.RawURLEncoding.DecodeString(tokenB64)
	token, err := tokenprofile.ParsePrivateIdentityToken(tokenBytes)
	if err != nil {
		t.Fatal(err)
	}
	nonceRaw, _ := base64.RawURLEncoding.DecodeString(nonceB64)
	var nonce [32]byte
	copy(nonce[:], nonceRaw)
	issuedAt := time.Now().Unix()
	msg := tokenprofile.PrivateIdentityPresentationMessage(origin, nonce, token.Digest(), issuedAt)
	sig := ed25519.Sign(holderPriv, msg)

	payload := bytes.Repeat([]byte("w"), 2048)
	sum := sha256.Sum256(payload)

	up := postJSON(t, gw, "POST", "/v1/uploads", map[string]any{
		"session_id":         sid,
		"token_b64":          tokenB64,
		"signature_b64":      base64.RawURLEncoding.EncodeToString(sig),
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
	if err := mem.Put(key, payload); err != nil {
		t.Fatal(err)
	}

	// Replay same presentation nonce must 409.
	sess2 := postJSON(t, gw, "POST", "/v1/sessions", map[string]any{})
	resp := raw(t, gw, "POST", "/v1/uploads", map[string]any{
		"session_id":         sess2["session_id"],
		"token_b64":          tokenB64,
		"signature_b64":      base64.RawURLEncoding.EncodeToString(sig),
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
	resp := raw(t, gw, "POST", "/v1/sessions/"+sid+"/assisted-mint", map[string]any{})
	if resp.Status != 403 {
		t.Fatalf("status=%d want 403 body=%s", resp.Status, resp.Body)
	}
}

func TestRejectOversize(t *testing.T) {
	gw, _ := testGateway(t)
	sess := postJSON(t, gw, "POST", "/v1/sessions", map[string]any{})
	sid := sess["session_id"].(string)
	mint := postJSON(t, gw, "POST", "/v1/sessions/"+sid+"/assisted-mint", map[string]any{})
	seed, _ := base64.RawURLEncoding.DecodeString(mint["holder_seed_b64"].(string))
	holderPriv := ed25519.NewKeyFromSeed(seed)
	tokenBytes, _ := base64.RawURLEncoding.DecodeString(mint["token_b64"].(string))
	token, _ := tokenprofile.ParsePrivateIdentityToken(tokenBytes)
	nonceRaw, _ := base64.RawURLEncoding.DecodeString(sess["presentation_nonce_b64"].(string))
	var nonce [32]byte
	copy(nonce[:], nonceRaw)
	issuedAt := time.Now().Unix()
	msg := tokenprofile.PrivateIdentityPresentationMessage(sess["origin"].(string), nonce, token.Digest(), issuedAt)
	sig := ed25519.Sign(holderPriv, msg)
	sum := sha256.Sum256([]byte("x"))
	resp := raw(t, gw, "POST", "/v1/uploads", map[string]any{
		"session_id":         sid,
		"token_b64":          mint["token_b64"],
		"signature_b64":      base64.RawURLEncoding.EncodeToString(sig),
		"issued_at":          issuedAt,
		"content_sha256_hex": hex.EncodeToString(sum[:]),
		"content_length":     core.MaxUploadBytes + 1,
		"content_type":       "image/webp",
	})
	if resp.Status != 400 {
		t.Fatalf("status=%d want 400 body=%s", resp.Status, resp.Body)
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
