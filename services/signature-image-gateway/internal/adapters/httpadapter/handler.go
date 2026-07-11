// Package httpadapter maps net/http onto the portable core.Gateway.
// Use this for local DevX and any host that speaks standard HTTP
// (Cloud Run, Fly, plain VMs, reverse proxies).
package httpadapter

import (
	"io"
	"net/http"
	"strings"

	"github.com/maceip/SigBird/services/signature-image-gateway/internal/core"
)

// Handler serves Gateway plus optional DevX memory PUT/GET routes.
type Handler struct {
	Gateway   *core.Gateway
	Presigner *core.MemoryPresigner // optional; enables /v1/dev-put and /v1/dev-get
}

func (h Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	path := r.URL.Path

	// DevX object store — only when MemoryPresigner is wired.
	if h.Presigner != nil {
		if r.Method == http.MethodPut && strings.HasPrefix(path, "/v1/dev-put/") {
			key := strings.TrimPrefix(path, "/v1/dev-put/")
			body, err := io.ReadAll(io.LimitReader(r.Body, int64(core.MaxUploadBytes)+1))
			if err != nil {
				http.Error(w, err.Error(), 400)
				return
			}
			if err := h.Presigner.Put(key, body); err != nil {
				http.Error(w, err.Error(), 400)
				return
			}
			w.WriteHeader(http.StatusNoContent)
			return
		}
		if r.Method == http.MethodGet && strings.HasPrefix(path, "/v1/dev-get/") {
			key := strings.TrimPrefix(path, "/v1/dev-get/")
			body, ok := h.Presigner.Get(key)
			if !ok {
				http.NotFound(w, r)
				return
			}
			w.Header().Set("content-type", core.RequiredContentType)
			_, _ = w.Write(body)
			return
		}
	}

	body, _ := io.ReadAll(io.LimitReader(r.Body, 1<<20))
	headers := map[string]string{}
	for k, vals := range r.Header {
		if len(vals) > 0 {
			headers[strings.ToLower(k)] = vals[0]
		}
	}
	resp := h.Gateway.Handle(r.Context(), core.Request{
		Method:  r.Method,
		Path:    path,
		Headers: headers,
		Body:    body,
	})
	for k, v := range resp.Headers {
		w.Header().Set(k, v)
	}
	w.WriteHeader(resp.Status)
	_, _ = w.Write(resp.Body)
}
