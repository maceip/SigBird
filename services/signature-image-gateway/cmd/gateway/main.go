package main

import (
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"

	"github.com/maceip/SigBird/services/signature-image-gateway/internal/adapters/httpadapter"
	"github.com/maceip/SigBird/services/signature-image-gateway/internal/bootstrap"
)

func main() {
	addr := flag.String("addr", getenv("GATEWAY_ADDR", "127.0.0.1:8790"), "listen address")
	flag.Parse()

	opts := bootstrap.FromEnv()
	opts.PublicBase = getenv("GATEWAY_PUBLIC_BASE", "http://"+*addr)

	res, err := bootstrap.Build(opts)
	if err != nil {
		log.Fatalf("bootstrap: %v", err)
	}

	h := httpadapter.Handler{
		Gateway:   res.Gateway,
		Presigner: res.Presigner,
	}

	fmt.Fprintf(os.Stderr, "signature-image-gateway listening on http://%s (mode=%s)\n", *addr, opts.Mode)
	fmt.Fprintf(os.Stderr, "  GET  /healthz\n")
	fmt.Fprintf(os.Stderr, "  GET  /v1/issuer\n")
	fmt.Fprintf(os.Stderr, "  POST /v1/sessions\n")
	fmt.Fprintf(os.Stderr, "  POST /v1/sessions/{id}/assisted-mint   (dev only)\n")
	fmt.Fprintf(os.Stderr, "  POST /v1/uploads\n")
	log.Fatal(http.ListenAndServe(*addr, h))
}

func getenv(k, def string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return def
}
