package bootstrap

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"strings"

	"github.com/maceip/tamayo/tokenauth"
	"github.com/maceip/tamayo/tokenprofile"

	"github.com/maceip/SigBird/services/signature-image-gateway/internal/core"
)

// Options wires a Gateway from files + env — shared by every edge adapter.
type Options struct {
	Mode            string // dev | prod
	IssuerPath      string
	PolicyPath      string
	PublicBase      string // e.g. http://127.0.0.1:8790
	ChallengePrefix string
}

// FromEnv reads GATEWAY_* variables with sensible DevX defaults.
func FromEnv() Options {
	mode := getenv("GATEWAY_MODE", "dev")
	return Options{
		Mode:            mode,
		IssuerPath:      getenv("GATEWAY_ISSUER", "issuer.json"),
		PolicyPath:      getenv("GATEWAY_POLICY", "policy.dev.json"),
		PublicBase:      getenv("GATEWAY_PUBLIC_BASE", "http://127.0.0.1:8790"),
		ChallengePrefix: getenv("GATEWAY_CHALLENGE_PREFIX", "sigbird-signature-upload"),
	}
}

// Result is the constructed runtime graph.
type Result struct {
	Gateway   *core.Gateway
	Presigner *core.MemoryPresigner // non-nil in DevX memory mode
}

// Build constructs the portable Gateway. DevX uses MemoryPresigner; set
// GATEWAY_PRESIGNER=s3 later to swap in a real S3 implementation.
func Build(opts Options) (*Result, error) {
	issuer, err := loadIssuer(opts.IssuerPath)
	if err != nil {
		return nil, err
	}
	rawPolicy, err := os.ReadFile(opts.PolicyPath)
	if err != nil {
		return nil, fmt.Errorf("policy: %w", err)
	}
	policy, err := tokenauth.CompileJSON(rawPolicy)
	if err != nil {
		return nil, fmt.Errorf("policy compile: %w", err)
	}

	presignerName := getenv("GATEWAY_PRESIGNER", "memory")
	var (
		presigner core.Presigner
		mem       *core.MemoryPresigner
	)
	switch strings.ToLower(presignerName) {
	case "memory", "dev", "":
		mem = core.NewMemoryPresigner(opts.PublicBase)
		presigner = mem
	default:
		return nil, fmt.Errorf("unknown GATEWAY_PRESIGNER %q (supported: memory)", presignerName)
	}

	gw, err := core.New(
		core.Config{
			Mode:       opts.Mode,
			PublicBase: opts.PublicBase,
			Origin:     opts.ChallengePrefix,
		},
		issuer,
		policy,
		presigner,
		tokenauth.NewMemoryBudgetStore(),
	)
	if err != nil {
		return nil, err
	}
	return &Result{Gateway: gw, Presigner: mem}, nil
}

type issuerFile struct {
	KeyVersion   uint32 `json:"key_version"`
	Mayo1SeedB64 string `json:"mayo1_sk_seed_b64"`
}

func loadIssuer(path string) (*tokenprofile.Issuer, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("issuer: %w", err)
	}
	var f issuerFile
	dec := json.NewDecoder(strings.NewReader(string(raw)))
	dec.DisallowUnknownFields()
	if err := dec.Decode(&f); err != nil {
		return nil, fmt.Errorf("issuer file: %w", err)
	}
	seed, err := base64.RawURLEncoding.DecodeString(f.Mayo1SeedB64)
	if err != nil {
		return nil, fmt.Errorf("issuer seed: %w", err)
	}
	defer tokenprofile.Wipe(seed)
	return tokenprofile.NewIssuer(f.KeyVersion, seed)
}

func getenv(k, def string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return def
}
