// Command smoke is the DevX client showcase for private-identity uploads:
// client holder key → blinded issuer signature → PoP presentation →
// origin-bound pseudonym (no email) → WebP upload.
package main

import (
	"bytes"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"time"

	"github.com/maceip/tamayo/tokenprofile"
)

func main() {
	base := env("GATEWAY_PUBLIC_BASE", "http://127.0.0.1:8790")
	if err := run(base); err != nil {
		fmt.Fprintln(os.Stderr, "smoke:", err)
		os.Exit(1)
	}
}

func run(base string) error {
	fmt.Println("== health ==")
	health, err := getJSON(base + "/healthz")
	if err != nil {
		return err
	}
	fmt.Printf("token_family=%v email=%v\n", health["token_family"], health["email"])

	fmt.Println("== session ==")
	sess, err := postJSON(base+"/v1/sessions", map[string]any{})
	if err != nil {
		return err
	}
	sid := sess["session_id"].(string)
	origin := sess["origin"].(string)
	nonceB64 := sess["presentation_nonce_b64"].(string)
	fmt.Printf("origin=%s (no email)\n", origin)

	fmt.Println("== holder keygen (client-side; private key never leaves this process) ==")
	holderPub, holderPriv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return err
	}

	fmt.Println("== assisted-mint (blind sig over client holder key) ==")
	mint, err := postJSON(base+"/v1/sessions/"+sid+"/assisted-mint", map[string]any{
		"holder_pub_b64": base64.RawURLEncoding.EncodeToString(holderPub),
	})
	if err != nil {
		return err
	}
	fmt.Printf("token_family=%v email=%v\n", mint["token_family"], mint["email"])
	fmt.Printf("holder_alg=%v pseudonym=%v\n", mint["holder_alg"], mint["pseudonym_hex"])
	fmt.Printf("note: %v\n", mint["note"])
	if _, leaked := mint["holder_seed_b64"]; leaked {
		return fmt.Errorf("gateway returned a holder seed; refusing to continue")
	}

	tokenBytes, err := base64.RawURLEncoding.DecodeString(mint["token_b64"].(string))
	if err != nil {
		return err
	}
	token, err := tokenprofile.ParsePrivateIdentityToken(tokenBytes)
	if err != nil {
		return err
	}
	nonceRaw, err := base64.RawURLEncoding.DecodeString(nonceB64)
	if err != nil || len(nonceRaw) != 32 {
		return fmt.Errorf("bad presentation nonce")
	}
	var nonce [32]byte
	copy(nonce[:], nonceRaw)
	issuedAt := time.Now().Unix()
	msg := tokenprofile.PrivateIdentityPresentationMessage(origin, nonce, token.Digest(), issuedAt)
	sig := ed25519.Sign(holderPriv, msg)

	payload := append([]byte("RIFF????WEBP"), bytes.Repeat([]byte{0xab}, 1024)...)
	sum := sha256.Sum256(payload)

	fmt.Println("== uploads (holder PoP → pseudonym, still no email) ==")
	up, err := postJSON(base+"/v1/uploads", map[string]any{
		"session_id":         sid,
		"token_b64":          mint["token_b64"],
		"signature_b64":      base64.RawURLEncoding.EncodeToString(sig),
		"issued_at":          issuedAt,
		"content_sha256_hex": hex.EncodeToString(sum[:]),
		"content_length":     len(payload),
		"content_type":       "image/webp",
	})
	if err != nil {
		return err
	}
	fmt.Printf("pseudonym=%v email=%v\n", up["pseudonym_hex"], up["email"])
	uploadURL := up["upload_url"].(string)
	publicURL := up["public_url"].(string)

	fmt.Println("== PUT ==")
	req, err := http.NewRequest(http.MethodPut, uploadURL, bytes.NewReader(payload))
	if err != nil {
		return err
	}
	req.Header.Set("content-type", "image/webp")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	resp.Body.Close()
	if resp.StatusCode >= 300 {
		return fmt.Errorf("put status %d", resp.StatusCode)
	}

	fmt.Println("== GET ==")
	got, err := http.Get(publicURL)
	if err != nil {
		return err
	}
	defer got.Body.Close()
	body, _ := io.ReadAll(got.Body)
	if !bytes.Equal(body, payload) {
		return fmt.Errorf("payload mismatch")
	}
	fmt.Printf("RESULT: PASS  public_url=%s\n", publicURL)
	return nil
}

func getJSON(url string) (map[string]any, error) {
	resp, err := http.Get(url)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	return decode(resp)
}

func postJSON(url string, body any) (map[string]any, error) {
	b, _ := json.Marshal(body)
	resp, err := http.Post(url, "application/json", bytes.NewReader(b))
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	return decode(resp)
}

func decode(resp *http.Response) (map[string]any, error) {
	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode >= 300 {
		return nil, fmt.Errorf("status %d body %s", resp.StatusCode, raw)
	}
	var out map[string]any
	if err := json.Unmarshal(raw, &out); err != nil {
		return nil, err
	}
	return out, nil
}

func env(k, def string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return def
}
