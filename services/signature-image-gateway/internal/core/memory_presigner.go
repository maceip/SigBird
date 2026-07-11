package core

import (
	"context"
	"fmt"
	"sync"
	"time"
)

// MemoryPresigner is the DevX / offline Presigner: no AWS required.
// UploadURL points at the local gateway's /v1/dev-put/{key} path when
// PublicBase is set to the gateway's own origin; otherwise it returns a
// synthetic URL that tests can assert on.
type MemoryPresigner struct {
	PublicBase string

	mu    sync.Mutex
	blobs map[string][]byte
}

func NewMemoryPresigner(publicBase string) *MemoryPresigner {
	return &MemoryPresigner{
		PublicBase: stringsTrimRightSlash(publicBase),
		blobs:      make(map[string][]byte),
	}
}

func (m *MemoryPresigner) PresignPut(
	_ context.Context,
	key string,
	contentType string,
	maxBytes int64,
	ttl time.Duration,
) (uploadURL, publicURL string, err error) {
	_ = contentType
	_ = maxBytes
	_ = ttl
	if m.PublicBase == "" {
		return "", "", fmt.Errorf("MemoryPresigner: PublicBase required")
	}
	uploadURL = m.PublicBase + "/v1/dev-put/" + key
	publicURL = m.PublicBase + "/v1/dev-get/" + key
	return uploadURL, publicURL, nil
}

// Put stores bytes for a previously "presigned" key (DevX only).
func (m *MemoryPresigner) Put(key string, body []byte) error {
	if len(body) == 0 || len(body) > MaxUploadBytes {
		return fmt.Errorf("body length must be 1..%d", MaxUploadBytes)
	}
	m.mu.Lock()
	defer m.mu.Unlock()
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

func stringsTrimRightSlash(s string) string {
	for len(s) > 0 && s[len(s)-1] == '/' {
		s = s[:len(s)-1]
	}
	return s
}
