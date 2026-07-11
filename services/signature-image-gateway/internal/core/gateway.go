// Package core is the portable signature-image upload gateway.
//
// It has no dependency on net/http servers, AWS Lambda, or any other edge
// runtime. Adapters in ../adapters map platform events onto [Request] /
// [Response] and call [Gateway.Handle].
//
// Auth showcase: tamayo private-identity tokens — a blinded issuer signature
// over the client's holder public key. No email address is ever learned;
// the verifier only sees an origin-bound pseudonym after the holder proves
// possession of the client key.
//
// S3 writes go through a [Presigner] seam so local DevX can stub uploads.
package core

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
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
	MaxUploadBytes      = 256 * 1024
	RequiredContentType = "image/webp"
	SessionTTL          = 10 * time.Minute
	DefaultMaxSkew      = 2 * time.Minute
	DefaultOrigin       = "sigbird-signature-upload"
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
	// Origin is the relying-party origin for private-identity presentations
	// (pseudonym is bound to this string; never an email).
	Origin string
	// MaxSkew is the allowed presentation timestamp skew.
	MaxSkew time.Duration
}

// Gateway is the reference product runtime for signature-image uploads.
type Gateway struct {
	cfg       Config
	issuer    *tokenprofile.Issuer
	svc       *tokenservice.Issuer
	policy    *tokenauth.Policy
	budgets   tokenauth.BudgetStore
	presigner Presigner
	clock     Clock

	mu       sync.Mutex
	sessions map[string]*session
	seenPvt  map[string]bool // origin\x00nonce replay set
}

type session struct {
	ID                string
	Origin            string
	PresentationNonce [32]byte
	Created           time.Time
	Expires           time.Time
}

// New builds a gateway around an already-loaded tamayo issuer + policy.
func New(
	cfg Config,
	issuer *tokenprofile.Issuer,
	policy *tokenauth.Policy,
	presigner Presigner,
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
	if cfg.Origin == "" {
		cfg.Origin = DefaultOrigin
	}
	if cfg.MaxSkew == 0 {
		cfg.MaxSkew = DefaultMaxSkew
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
		presigner: presigner,
		clock:     realClock{},
		sessions:  make(map[string]*session),
		seenPvt:   make(map[string]bool),
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
		return jsonOK(map[string]any{
			"ok":           true,
			"mode":         g.cfg.Mode,
			"token_family": "private_identity",
			"email":        nil,
		})
	case req.Method == "GET" && path == "/v1/issuer":
		return g.handleIssuerInfo()
	case req.Method == "POST" && path == "/v1/sessions":
		return g.handleCreateSession()
	case req.Method == "POST" && strings.HasPrefix(path, "/v1/sessions/") && strings.HasSuffix(path, "/assisted-mint"):
		id := strings.TrimSuffix(strings.TrimPrefix(path, "/v1/sessions/"), "/assisted-mint")
		return g.handleAssistedMint(id, req)
	case req.Method == "POST" && path == "/v1/uploads":
		return g.handleUpload(ctx, req)
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
		"token_family":            "private_identity",
		"origin":                  g.cfg.Origin,
		"email":                   nil,
	})
}

type createSessionResponse struct {
	SessionID            string `json:"session_id"`
	Origin               string `json:"origin"`
	PresentationNonceB64 string `json:"presentation_nonce_b64"`
	ExpiresAtUnix        int64  `json:"expires_at_unix"`
	MaxUploadBytes       int    `json:"max_upload_bytes"`
	ContentType          string `json:"content_type"`
	TokenFamily          string `json:"token_family"`
	Email                any    `json:"email"`
	AssistedMint         bool   `json:"assisted_mint_available"`
}

func (g *Gateway) handleCreateSession() Response {
	g.gcSessions()
	var idBytes, nonce [32]byte
	if _, err := rand.Read(idBytes[:]); err != nil {
		return jsonErr(500, "entropy: "+err.Error())
	}
	if _, err := rand.Read(nonce[:]); err != nil {
		return jsonErr(500, "entropy: "+err.Error())
	}
	id := hex.EncodeToString(idBytes[:16])
	now := g.clock.Now()
	sess := &session{
		ID:                id,
		Origin:            g.cfg.Origin,
		PresentationNonce: nonce,
		Created:           now,
		Expires:           now.Add(SessionTTL),
	}
	g.mu.Lock()
	g.sessions[id] = sess
	g.mu.Unlock()
	return jsonOK(createSessionResponse{
		SessionID:            id,
		Origin:               sess.Origin,
		PresentationNonceB64: b64(nonce[:]),
		ExpiresAtUnix:        sess.Expires.Unix(),
		MaxUploadBytes:       MaxUploadBytes,
		ContentType:          RequiredContentType,
		TokenFamily:          "private_identity",
		Email:                nil,
		AssistedMint:         g.cfg.Mode == "dev",
	})
}

type assistedMintRequest struct {
	SubjectValueX string `json:"subject_value_x,omitempty"`
	BucketID      string `json:"bucket_id,omitempty"`
	// HolderPubB64: optional client-supplied Ed25519 public key (32 bytes).
	// If omitted in DevX, the gateway generates a holder keypair and returns
	// the seed so the smoke client can sign presentations.
	HolderPubB64 string `json:"holder_pub_b64,omitempty"`
}

type assistedMintResponse struct {
	TokenFamily          string `json:"token_family"`
	Email                any    `json:"email"`
	TokenB64             string `json:"token_b64"`
	HolderAlg            string `json:"holder_alg"`
	HolderPubB64         string `json:"holder_pub_b64"`
	HolderSeedB64        string `json:"holder_seed_b64,omitempty"` // DevX only
	Origin               string `json:"origin"`
	PresentationNonceB64 string `json:"presentation_nonce_b64"`
	PseudonymHex         string `json:"pseudonym_hex"`
	Note                 string `json:"note"`
}

func (g *Gateway) handleAssistedMint(sessionID string, req Request) Response {
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

	var (
		holderPub  ed25519.PublicKey
		holderPriv ed25519.PrivateKey
		holderSeed []byte
		err        error
	)
	if body.HolderPubB64 != "" {
		raw, err := decodeB64(body.HolderPubB64)
		if err != nil || len(raw) != ed25519.PublicKeySize {
			return jsonErr(400, "holder_pub_b64 must be 32 raw bytes (base64url)")
		}
		holderPub = ed25519.PublicKey(raw)
		// Client keeps the private key; we cannot return a seed.
	} else {
		holderPub, holderPriv, err = ed25519.GenerateKey(rand.Reader)
		if err != nil {
			return jsonErr(500, "holder keygen: "+err.Error())
		}
		holderSeed = holderPriv.Seed()
	}

	var additionalR [32]byte
	if _, err := rand.Read(additionalR[:]); err != nil {
		return jsonErr(500, "entropy: "+err.Error())
	}
	input := tokenprofile.NewPrivateIdentityInput(
		g.issuer.KeyVersion(),
		g.issuer.TokenKeyID(),
		tokenprofile.HolderAlgEd25519,
		holderPub,
	)
	target, state := tokenprofile.PrepareBlind(input.Bytes(), additionalR)

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
		TokenFamily: tokenauth.TokenPrivateIdentity,
		Count:       1,
		KeyVersion:  g.issuer.KeyVersion(),
		Origin:      sess.Origin,
		Binding:     bindingOf([][]byte{target}),
	}, g.budgets, now)
	if !decision.Allow {
		return jsonStatus(403, map[string]any{"error": "mint denied", "decision": decision})
	}
	sigs, err := g.svc.SignAuthorizedBlind(tokenservice.BlindMintRequest{
		Decision: decision,
		Family:   tokenauth.TokenPrivateIdentity,
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
	pvt := tokenprofile.PrivateIdentityToken{Input: input, Authenticator: authenticator}
	pseudonym := pvt.PseudonymForOrigin(sess.Origin)

	out := assistedMintResponse{
		TokenFamily:          "private_identity",
		Email:                nil,
		TokenB64:             b64(pvt.Bytes()),
		HolderAlg:            "ed25519",
		HolderPubB64:         b64(holderPub),
		Origin:               sess.Origin,
		PresentationNonceB64: b64(sess.PresentationNonce[:]),
		PseudonymHex:         hex.EncodeToString(pseudonym[:]),
		Note: "Showcase: blinded PoMFRIT signature over the client holder key. " +
			"No email is in the token. Present with a holder PoP to reveal only an origin-bound pseudonym. " +
			"DevX may return holder_seed_b64; production clients generate and keep the key locally.",
	}
	if holderSeed != nil {
		out.HolderSeedB64 = b64(holderSeed)
	}
	return jsonOK(out)
}

type uploadRequest struct {
	SessionID     string `json:"session_id"`
	TokenB64      string `json:"token_b64"`
	SignatureB64  string `json:"signature_b64"`
	IssuedAt      int64  `json:"issued_at"`
	ContentSHA256 string `json:"content_sha256_hex"`
	ContentLength int64  `json:"content_length"`
	ContentType   string `json:"content_type"`
}

type uploadResponse struct {
	UploadURL    string `json:"upload_url"`
	PublicURL    string `json:"public_url"`
	ObjectKey    string `json:"object_key"`
	ExpiresInS   int    `json:"expires_in_seconds"`
	PseudonymHex string `json:"pseudonym_hex"`
	TokenFamily  string `json:"token_family"`
	Email        any    `json:"email"`
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
	tokenBytes, err := decodeB64(body.TokenB64)
	if err != nil {
		return jsonErr(400, "token_b64: "+err.Error())
	}
	token, err := tokenprofile.ParsePrivateIdentityToken(tokenBytes)
	if err != nil {
		return jsonErr(400, "token: "+err.Error())
	}
	sig, err := decodeB64(body.SignatureB64)
	if err != nil {
		return jsonErr(400, "signature_b64: "+err.Error())
	}
	if body.IssuedAt == 0 {
		body.IssuedAt = g.clock.Now().Unix()
	}

	replayKey := sess.Origin + "\x00" + string(sess.PresentationNonce[:])
	g.mu.Lock()
	if g.seenPvt[replayKey] {
		g.mu.Unlock()
		return jsonStatus(409, map[string]string{"error": "presentation nonce already used for this origin"})
	}
	g.seenPvt[replayKey] = true
	g.mu.Unlock()

	pseudonym, err := g.issuer.VerifyPrivateIdentityPresentation(tokenprofile.PrivateIdentityPresentation{
		Token:     token,
		Origin:    sess.Origin,
		Nonce:     sess.PresentationNonce,
		IssuedAt:  body.IssuedAt,
		Signature: sig,
	}, g.clock.Now(), g.cfg.MaxSkew)
	if err != nil {
		// allow retry with a fresh session if verify failed after marking —
		// unmark so a bad signature does not burn the nonce forever in DevX.
		g.mu.Lock()
		delete(g.seenPvt, replayKey)
		g.mu.Unlock()
		return jsonErr(401, "private-identity present: "+err.Error())
	}

	now := g.clock.Now().UTC()
	key := fmt.Sprintf("sig/%04d/%02d/%s/%s.webp",
		now.Year(), int(now.Month()), hex.EncodeToString(shaBytes[:4]), body.SessionID)
	const ttl = 5 * time.Minute
	uploadURL, publicURL, err := g.presigner.PresignPut(ctx, key, RequiredContentType, body.ContentLength, ttl)
	if err != nil {
		g.mu.Lock()
		delete(g.seenPvt, replayKey)
		g.mu.Unlock()
		return jsonErr(500, "presign: "+err.Error())
	}
	g.mu.Lock()
	delete(g.sessions, body.SessionID)
	g.mu.Unlock()
	return jsonOK(uploadResponse{
		UploadURL:    uploadURL,
		PublicURL:    publicURL,
		ObjectKey:    key,
		ExpiresInS:   int(ttl.Seconds()),
		PseudonymHex: hex.EncodeToString(pseudonym[:]),
		TokenFamily:  "private_identity",
		Email:        nil,
	})
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
