// Command lambda is the AWS Lambda (Function URL / HTTP API) entrypoint.
//
//	GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -o bootstrap ./cmd/lambda
//
// Same core.Gateway as cmd/gateway — only the adapter differs.
package main

import (
	"log"

	"github.com/aws/aws-lambda-go/lambda"

	"github.com/maceip/SigBird/services/signature-image-gateway/internal/adapters/lambdaadapter"
	"github.com/maceip/SigBird/services/signature-image-gateway/internal/bootstrap"
)

func main() {
	res, err := bootstrap.Build(bootstrap.FromEnv())
	if err != nil {
		log.Fatalf("bootstrap: %v", err)
	}
	lambda.Start(lambdaadapter.Handler{Gateway: res.Gateway}.Handle)
}
