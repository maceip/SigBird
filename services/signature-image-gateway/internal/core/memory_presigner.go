package core

import (
	"context"
	"crypto/sha256"
	"fmt"
	"strings"
	"sync"
	"time"
)

// MemoryPresigner is the DevX / offline Presigner: no AWS required.
//
// It honours the same contract a real S3 presigner provides: every PUT is
// checked against the exact content type, length, and SHA-256 that were
// pinned at presign time, and the grant expires after its TTL. UploadURL
// points at the local gateway's /v1/dev-put/{key} path when PublicBase is
// the gateway's own origin; otherwise it returns a synthetic URL that tests
// can assert on.
type MemoryPresigner struct {
	PublicBase string
	Clock      Clock // defaults to wall clock

	mu     sync.Mutex
	grants map[string]presignGrant
	blobs  map[string][]byte
}

type presignGrant struct {
	contentType   string
	contentLength int64
	contentSHA256 [32]byte
	expires       time.Time
}

func NewMemoryPresigner(publicBase string) *MemoryPresigner {
	return &MemoryPresigner{
		PublicBase: strings.TrimRight(publicBase, "/"),
		Clock:      realClock{},
		grants:     make(map[string]presignGrant),
		blobs:      make(map[string][]byte),
	}
}

func (m *MemoryPresigner) PresignPut(
	_ context.Context,
	in PresignPutInput,
) (uploadURL, publicURL string, err error) {
	if m.PublicBase == "" {
		return "", "", fmt.Errorf("MemoryPresigner: PublicBase required")
	}
	if in.ContentLength <= 0 || in.ContentLength > MaxUploadBytes {
		return "", "", fmt.Errorf("MemoryPresigner: content length must be 1..%d", MaxUploadBytes)
	}
	m.mu.Lock()
	m.grants[in.Key] = presignGrant{
		contentType:   in.ContentType,
		contentLength: in.ContentLength,
		contentSHA256: in.ContentSHA256,
		expires:       m.now().Add(in.TTL),
	}
	m.mu.Unlock()
	uploadURL = m.PublicBase + "/v1/dev-put/" + in.Key
	publicURL = m.PublicBase + "/v1/dev-get/" + in.Key
	return uploadURL, publicURL, nil
}

// Put stores bytes for a previously presigned key, enforcing the pinned
// content type, length, SHA-256, and TTL — mirroring what S3 enforces when
// those values are part of the signed request.
func (m *MemoryPresigner) Put(key string, body []byte, contentType string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	grant, ok := m.grants[key]
	if !ok {
		return fmt.Errorf("no presigned grant for key %q", key)
	}
	if m.now().After(grant.expires) {
		delete(m.grants, key)
		return fmt.Errorf("presigned grant for key %q expired", key)
	}
	if contentType != "" && !strings.EqualFold(contentType, grant.contentType) {
		return fmt.Errorf("content type %q does not match presigned %q", contentType, grant.contentType)
	}
	if int64(len(body)) != grant.contentLength {
		return fmt.Errorf("body is %d bytes, presigned for %d", len(body), grant.contentLength)
	}
	if sha256.Sum256(body) != grant.contentSHA256 {
		return fmt.Errorf("body SHA-256 does not match presigned checksum")
	}
	delete(m.grants, key)
	cp := make([]byte, len(body))
	copy(cp, body)
	m.blobs[key] = cp
	return nil
}

// Get returns stored bytes.
func (m *MemoryPresigner) Get(key string) ([]byte, bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	b, ok := m.blobs[key]
	if !ok {
		return nil, false
	}
	cp := make([]byte, len(b))
	copy(cp, b)
	return cp, true
}

func (m *MemoryPresigner) now() time.Time {
	if m.Clock != nil {
		return m.Clock.Now()
	}
	return time.Now()
}
