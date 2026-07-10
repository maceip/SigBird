package core_test

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"

	"github.com/maceip/tamayo/tokenauth"
	"github.com/maceip/tamayo/tokenprofile"

	"github.com/maceip/SigBird/services/signature-image-gateway/internal/core"
)

func TestUploadHappyPathAndDoubleSpend(t *testing.T) {
	gw, mem := testGateway(t)

	sess := postJSON(t, gw, "POST", "/v1/sessions", map[string]any{})
	sid := sess["session_id"].(string)

	mint := postJSON(t, gw, "POST", "/v1/sessions/"+sid+"/assisted-mint", map[string]any{})
	token := mint["token_b64"].(string)

	payload := bytes.Repeat([]byte("w"), 2048)
	sum := sha256.Sum256(payload)

	up := postJSON(t, gw, "POST", "/v1/uploads", map[string]any{
		"session_id":          sid,
		"token_b64":           token,
		"content_sha256_hex":  hex.EncodeToString(sum[:]),
		"content_length":      len(payload),
		"content_type":        "image/webp",
	})
	if up["upload_url"] == nil || up["public_url"] == nil {
		t.Fatalf("missing urls: %#v", up)
	}
	key := up["object_key"].(string)
	if err := mem.Put(key, payload); err != nil {
		t.Fatal(err)
	}
	got, ok := mem.Get(key)
	if !ok || !bytes.Equal(got, payload) {
		t.Fatalf("stored payload mismatch")
	}

	// Replaying the same burn must fail (token already spent) even with a new session.
	sess2 := postJSON(t, gw, "POST", "/v1/sessions", map[string]any{})
	sid2 := sess2["session_id"].(string)
	// Token was bound to the first session challenge — verify fails or spend conflicts.
	resp := raw(t, gw, "POST", "/v1/uploads", map[string]any{
		"session_id":         sid2,
		"token_b64":          token,
		"content_sha256_hex": hex.EncodeToString(sum[:]),
		"content_length":     len(payload),
		"content_type":       "image/webp",
	})
	if resp.Status == 200 {
		t.Fatalf("expected reject on reused burn, got 200")
	}
}

func TestAssistedMintDisabledInProd(t *testing.T) {
	issuer := newIssuer(t)
	policy := compilePolicy(t)
	mem := core.NewMemoryPresigner("http://example.test")
	gw, err := core.New(core.Config{Mode: "prod"}, issuer, policy, mem, nil, nil)
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
	sum := sha256.Sum256([]byte("x"))
	resp := raw(t, gw, "POST", "/v1/uploads", map[string]any{
		"session_id":         sid,
		"token_b64":          mint["token_b64"],
		"content_sha256_hex": hex.EncodeToString(sum[:]),
		"content_length":     core.MaxUploadBytes + 1,
		"content_type":       "image/webp",
	})
	if resp.Status != 400 {
		t.Fatalf("status=%d want 400", resp.Status)
	}
}

func testGateway(t *testing.T) (*core.Gateway, *core.MemoryPresigner) {
	t.Helper()
	issuer := newIssuer(t)
	policy := compilePolicy(t)
	mem := core.NewMemoryPresigner("http://example.test")
	gw, err := core.New(core.Config{Mode: "dev"}, issuer, policy, mem, nil, nil)
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
		// when tests run from module root
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
