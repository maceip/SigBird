// Package core is the portable signature-image upload gateway.
//
// It has no dependency on net/http servers, AWS Lambda, or any other edge
// runtime. Adapters in ../adapters map platform events onto [Request] /
// [Response] and call [Gateway.Handle].
//
// Token mint/verify uses github.com/maceip/tamayo (burn tokens). S3 writes
// go through a [Presigner] seam so local DevX can stub uploads.
package core

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/maceip/tamayo/tokenauth"
	"github.com/maceip/tamayo/tokenprofile"
	"github.com/maceip/tamayo/tokenservice"
)

const (
	MaxUploadBytes     = 256 * 1024
	RequiredContentType = "image/webp"
	SessionTTL          = 10 * time.Minute
)

// Request is a platform-neutral HTTP-ish request.
type Request struct {
	Method  string
	Path    string
	Headers map[string]string
	Body    []byte
}

// Response is a platform-neutral HTTP-ish response.
type Response struct {
	Status  int
	Headers map[string]string
	Body    []byte
}

// Presigner issues short-lived write credentials for one object.
type Presigner interface {
	PresignPut(ctx context.Context, key string, contentType string, maxBytes int64, ttl time.Duration) (uploadURL, publicURL string, err error)
}

// Clock abstracts time for tests.
type Clock interface {
	Now() time.Time
}

type realClock struct{}

func (realClock) Now() time.Time { return time.Now() }

// Config is runtime configuration shared across edge adapters.
type Config struct {
	// Mode is "dev" or "prod". Assisted mint is only available in "dev".
	Mode string
	// PublicBase is the CDN/public origin used in returned public URLs when
	// the Presigner does not override them (informational; Presigner wins).
	PublicBase string
	// OriginChallengePrefix is mixed into session challenges so burns are
	// bound to this product ("sigbird-signature-upload").
	OriginChallengePrefix string
}

// Gateway is the reference product runtime for signature-image uploads.
type Gateway struct {
	cfg       Config
	issuer    *tokenprofile.Issuer
	svc       *tokenservice.Issuer
	policy    *tokenauth.Policy
	budgets   tokenauth.BudgetStore
	spent     tokenservice.SpentStore
	presigner Presigner
	clock     Clock

	mu       sync.Mutex
	sessions map[string]*session
}

type session struct {
	ID        string
	Challenge []byte
	Created   time.Time
	Expires   time.Time
}

// New builds a gateway around an already-loaded tamayo issuer + policy.
func New(
	cfg Config,
	issuer *tokenprofile.Issuer,
	policy *tokenauth.Policy,
	presigner Presigner,
	spent tokenservice.SpentStore,
	budgets tokenauth.BudgetStore,
) (*Gateway, error) {
	if issuer == nil {
		return nil, errors.New("issuer required")
	}
	if policy == nil {
		return nil, errors.New("policy required")
	}
	if presigner == nil {
		return nil, errors.New("presigner required")
	}
	if cfg.Mode == "" {
		cfg.Mode = "dev"
	}
	if cfg.OriginChallengePrefix == "" {
		cfg.OriginChallengePrefix = "sigbird-signature-upload"
	}
	if spent == nil {
		spent = tokenservice.NewMemorySpentStore()
	}
	if budgets == nil {
		budgets = tokenauth.NewMemoryBudgetStore()
	}
	svc, err := tokenservice.NewIssuer(issuer, nil)
	if err != nil {
		return nil, err
	}
	return &Gateway{
		cfg:       cfg,
		issuer:    issuer,
		svc:       svc,
		policy:    policy,
		budgets:   budgets,
		spent:     spent,
		presigner: presigner,
		clock:     realClock{},
		sessions:  make(map[string]*session),
	}, nil
}

// Handle routes a portable request. Paths are matched without a host prefix.
func (g *Gateway) Handle(ctx context.Context, req Request) Response {
	path := strings.TrimSuffix(req.Path, "/")
	if path == "" {
		path = "/"
	}
	switch {
	case req.Method == "GET" && (path == "/healthz" || path == "/v1/healthz"):
		return jsonOK(map[string]any{"ok": true, "mode": g.cfg.Mode})
	case req.Method == "GET" && path == "/v1/issuer":
		return g.handleIssuerInfo()
	case req.Method == "POST" && path == "/v1/sessions":
		return g.handleCreateSession(req)
	case req.Method == "POST" && strings.HasPrefix(path, "/v1/sessions/") && strings.HasSuffix(path, "/assisted-mint"):
		id := strings.TrimSuffix(strings.TrimPrefix(path, "/v1/sessions/"), "/assisted-mint")
		return g.handleAssistedMint(ctx, id, req)
	case req.Method == "POST" && path == "/v1/uploads":
		return g.handleUpload(ctx, req)
	case req.Method == "POST" && path == "/v1/tokens/verify":
		return g.handleVerifyOnly(req)
	default:
		return jsonErr(404, "not found")
	}
}

func (g *Gateway) handleIssuerInfo() Response {
	id := g.issuer.TokenKeyID()
	return jsonOK(map[string]any{
		"algorithm":               tokenprofile.Algorithm,
		"key_version":             g.issuer.KeyVersion(),
		"token_key_id_hex":        hex.EncodeToString(id[:]),
		"expanded_public_key_b64": b64(g.issuer.ExpandedPublicKey()),
		"compact_public_key_b64":  b64(g.issuer.CompactPublicKey()),
		"mode":                    g.cfg.Mode,
	})
}

type createSessionResponse struct {
	SessionID      string `json:"session_id"`
	ChallengeB64   string `json:"challenge_b64"`
	ExpiresAtUnix  int64  `json:"expires_at_unix"`
	MaxUploadBytes int    `json:"max_upload_bytes"`
	ContentType    string `json:"content_type"`
	AssistedMint   bool   `json:"assisted_mint_available"`
}

func (g *Gateway) handleCreateSession(_ Request) Response {
	g.gcSessions()
	var raw [32]byte
	if _, err := rand.Read(raw[:]); err != nil {
		return jsonErr(500, "entropy: "+err.Error())
	}
	challenge := []byte(fmt.Sprintf("%s:%s", g.cfg.OriginChallengePrefix, hex.EncodeToString(raw[:])))
	id := hex.EncodeToString(raw[:16])
	now := g.clock.Now()
	sess := &session{
		ID:        id,
		Challenge: challenge,
		Created:   now,
		Expires:   now.Add(SessionTTL),
	}
	g.mu.Lock()
	g.sessions[id] = sess
	g.mu.Unlock()
	return jsonOK(createSessionResponse{
		SessionID:      id,
		ChallengeB64:   b64(challenge),
		ExpiresAtUnix:  sess.Expires.Unix(),
		MaxUploadBytes: MaxUploadBytes,
		ContentType:    RequiredContentType,
		AssistedMint:   g.cfg.Mode == "dev",
	})
}

type assistedMintRequest struct {
	// Optional overrides for DevX; defaults match tamayo example-policy.
	SubjectValueX string `json:"subject_value_x,omitempty"`
	BucketID      string `json:"bucket_id,omitempty"`
}

type assistedMintResponse struct {
	TokenB64     string `json:"token_b64"`
	ChallengeB64 string `json:"challenge_b64"`
	Note         string `json:"note"`
}

func (g *Gateway) handleAssistedMint(_ context.Context, sessionID string, req Request) Response {
	if g.cfg.Mode != "dev" {
		return jsonErr(403, "assisted-mint is disabled outside GATEWAY_MODE=dev")
	}
	sess, ok := g.getSession(sessionID)
	if !ok {
		return jsonErr(404, "unknown or expired session")
	}
	var body assistedMintRequest
	if len(req.Body) > 0 {
		if err := json.Unmarshal(req.Body, &body); err != nil {
			return jsonErr(400, "json: "+err.Error())
		}
	}
	if body.SubjectValueX == "" {
		body.SubjectValueX = "dev-measurement"
	}
	if body.BucketID == "" {
		body.BucketID = "runtime-1"
	}

	var nonce, additionalR [32]byte
	if _, err := rand.Read(nonce[:]); err != nil {
		return jsonErr(500, "entropy: "+err.Error())
	}
	if _, err := rand.Read(additionalR[:]); err != nil {
		return jsonErr(500, "entropy: "+err.Error())
	}
	challengeDigest := sha256.Sum256(sess.Challenge)
	input := tokenprofile.BurnInput(nonce, challengeDigest, g.issuer.TokenKeyID())
	target, state := tokenprofile.PrepareBlind(input, additionalR)

	now := g.clock.Now()
	decision := g.policy.AuthorizeMint(tokenauth.MintRequest{
		Subject: tokenauth.Subject{
			ValueX:   body.SubjectValueX,
			Platform: "software-witness",
		},
		Eligibility: []tokenauth.Eligibility{{
			GateKind:  tokenauth.GateTEE,
			BucketID:  body.BucketID,
			Assurance: tokenauth.AssuranceVerified,
		}},
		TokenFamily: tokenauth.TokenBurn,
		Count:       1,
		KeyVersion:  g.issuer.KeyVersion(),
		Binding:     bindingOf([][]byte{target}),
	}, g.budgets, now)
	if !decision.Allow {
		return jsonStatus(403, map[string]any{"error": "mint denied", "decision": decision})
	}
	sigs, err := g.svc.SignAuthorizedBlind(tokenservice.BlindMintRequest{
		Decision: decision,
		Family:   tokenauth.TokenBurn,
		Blinded:  [][]byte{target},
		Now:      now,
	})
	if err != nil {
		return jsonErr(403, "blind-sign: "+err.Error())
	}
	authenticator, err := tokenprofile.FinalizeBlind(g.issuer.ExpandedPublicKey(), sigs[0], state)
	if err != nil {
		return jsonErr(500, "finalize: "+err.Error())
	}
	token := tokenprofile.BurnToken{
		TokenType:       tokenprofile.BurnTokenType,
		Nonce:           nonce,
		ChallengeDigest: challengeDigest,
		TokenKeyID:      g.issuer.TokenKeyID(),
		Authenticator:   authenticator,
	}
	return jsonOK(assistedMintResponse{
		TokenB64:     b64(token.Bytes()),
		ChallengeB64: b64(sess.Challenge),
		Note:         "DevX only: server ran the PoMFRIT blind loop. Production clients must blind locally.",
	})
}

type uploadRequest struct {
	SessionID      string `json:"session_id"`
	TokenB64       string `json:"token_b64"`
	ContentSHA256  string `json:"content_sha256_hex"`
	ContentLength  int64  `json:"content_length"`
	ContentType    string `json:"content_type"`
}

type uploadResponse struct {
	UploadURL  string `json:"upload_url"`
	PublicURL  string `json:"public_url"`
	ObjectKey  string `json:"object_key"`
	ExpiresInS int    `json:"expires_in_seconds"`
}

func (g *Gateway) handleUpload(ctx context.Context, req Request) Response {
	var body uploadRequest
	if err := json.Unmarshal(req.Body, &body); err != nil {
		return jsonErr(400, "json: "+err.Error())
	}
	if body.ContentType == "" {
		body.ContentType = RequiredContentType
	}
	if !strings.EqualFold(body.ContentType, RequiredContentType) {
		return jsonErr(400, "content_type must be image/webp")
	}
	if body.ContentLength <= 0 || body.ContentLength > MaxUploadBytes {
		return jsonErr(400, fmt.Sprintf("content_length must be 1..%d", MaxUploadBytes))
	}
	if len(body.ContentSHA256) != 64 {
		return jsonErr(400, "content_sha256_hex must be 64 hex chars")
	}
	shaBytes, err := hex.DecodeString(body.ContentSHA256)
	if err != nil {
		return jsonErr(400, "content_sha256_hex: "+err.Error())
	}
	sess, ok := g.getSession(body.SessionID)
	if !ok {
		return jsonErr(404, "unknown or expired session")
	}
	tokenBytes, err := base64.RawURLEncoding.DecodeString(body.TokenB64)
	if err != nil {
		// also accept std encoding
		tokenBytes, err = base64.StdEncoding.DecodeString(body.TokenB64)
		if err != nil {
			return jsonErr(400, "token_b64: "+err.Error())
		}
	}
	token, err := tokenprofile.ParseBurnToken(tokenBytes)
	if err != nil {
		return jsonErr(400, "token: "+err.Error())
	}
	if err := g.issuer.VerifyBurnToken(token, sha256.Sum256(sess.Challenge)); err != nil {
		return jsonErr(401, "burn verify: "+err.Error())
	}
	if err := g.spent.CheckAndMark(g.issuer.KeyVersion(), token.Nonce); err != nil {
		return jsonStatus(409, map[string]string{"error": "burn token already spent"})
	}

	now := g.clock.Now().UTC()
	key := fmt.Sprintf("sig/%04d/%02d/%s/%s.webp",
		now.Year(), int(now.Month()), hex.EncodeToString(shaBytes[:4]), body.SessionID)
	const ttl = 5 * time.Minute
	uploadURL, publicURL, err := g.presigner.PresignPut(ctx, key, RequiredContentType, body.ContentLength, ttl)
	if err != nil {
		return jsonErr(500, "presign: "+err.Error())
	}
	// One-shot session.
	g.mu.Lock()
	delete(g.sessions, body.SessionID)
	g.mu.Unlock()
	return jsonOK(uploadResponse{
		UploadURL:  uploadURL,
		PublicURL:  publicURL,
		ObjectKey:  key,
		ExpiresInS: int(ttl.Seconds()),
	})
}

type verifyRequest struct {
	TokenB64     string `json:"token_b64"`
	ChallengeB64 string `json:"challenge_b64"`
}

func (g *Gateway) handleVerifyOnly(req Request) Response {
	var body verifyRequest
	if err := json.Unmarshal(req.Body, &body); err != nil {
		return jsonErr(400, "json: "+err.Error())
	}
	tokenBytes, err := decodeB64(body.TokenB64)
	if err != nil {
		return jsonErr(400, "token_b64: "+err.Error())
	}
	challenge, err := decodeB64(body.ChallengeB64)
	if err != nil {
		return jsonErr(400, "challenge_b64: "+err.Error())
	}
	token, err := tokenprofile.ParseBurnToken(tokenBytes)
	if err != nil {
		return jsonErr(400, "token: "+err.Error())
	}
	if err := g.issuer.VerifyBurnToken(token, sha256.Sum256(challenge)); err != nil {
		return jsonErr(401, err.Error())
	}
	return jsonOK(map[string]any{"ok": true, "spent": false, "note": "verify-only; did not mark spent"})
}

func (g *Gateway) getSession(id string) (*session, bool) {
	g.mu.Lock()
	defer g.mu.Unlock()
	sess, ok := g.sessions[id]
	if !ok {
		return nil, false
	}
	if g.clock.Now().After(sess.Expires) {
		delete(g.sessions, id)
		return nil, false
	}
	return sess, true
}

func (g *Gateway) gcSessions() {
	now := g.clock.Now()
	g.mu.Lock()
	defer g.mu.Unlock()
	for id, sess := range g.sessions {
		if now.After(sess.Expires) {
			delete(g.sessions, id)
		}
	}
}

func bindingOf(blinded [][]byte) []byte {
	b := tokenprofile.BindingOf(blinded)
	return b[:]
}

func b64(b []byte) string {
	return base64.RawURLEncoding.EncodeToString(b)
}

func decodeB64(s string) ([]byte, error) {
	if s == "" {
		return nil, errors.New("empty")
	}
	if b, err := base64.RawURLEncoding.DecodeString(s); err == nil {
		return b, nil
	}
	return base64.StdEncoding.DecodeString(s)
}

func jsonOK(v any) Response {
	return jsonStatus(200, v)
}

func jsonErr(status int, msg string) Response {
	return jsonStatus(status, map[string]string{"error": msg})
}

func jsonStatus(status int, v any) Response {
	body, err := json.Marshal(v)
	if err != nil {
		body = []byte(`{"error":"marshal failed"}`)
		status = 500
	}
	return Response{
		Status: status,
		Headers: map[string]string{
			"content-type": "application/json",
		},
		Body: body,
	}
}
