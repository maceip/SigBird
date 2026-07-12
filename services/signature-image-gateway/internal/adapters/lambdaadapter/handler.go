// Package lambdaadapter maps AWS Lambda Function URL / API Gateway HTTP
// API events onto the portable core.Gateway.
//
// The handler is intentionally tiny: decode event → core.Request →
// core.Response → encode. Swap this file for Cloudflare Workers / Fastly
// Compute by writing another adapter that calls the same Gateway.Handle.
package lambdaadapter

import (
	"context"
	"encoding/base64"
	"strings"

	"github.com/maceip/SigBird/services/signature-image-gateway/internal/core"
)

// APIGatewayV2Request is the subset of the AWS HTTP API / Function URL
// event we need. Defined locally so the module does not require the AWS
// Lambda SDK just to compile the portable core + local server.
type APIGatewayV2Request struct {
	RawPath         string            `json:"rawPath"`
	Body            string            `json:"body"`
	IsBase64Encoded bool              `json:"isBase64Encoded"`
	Headers         map[string]string `json:"headers"`
	RequestContext  struct {
		HTTP struct {
			Method string `json:"method"`
		} `json:"http"`
	} `json:"requestContext"`
}

// APIGatewayV2Response is the matching response shape.
type APIGatewayV2Response struct {
	StatusCode      int               `json:"statusCode"`
	Headers         map[string]string `json:"headers"`
	Body            string            `json:"body"`
	IsBase64Encoded bool              `json:"isBase64Encoded"`
}

// Handler wraps Gateway for Lambda.
type Handler struct {
	Gateway *core.Gateway
}

func (h Handler) Handle(ctx context.Context, event APIGatewayV2Request) (APIGatewayV2Response, error) {
	body := []byte(event.Body)
	if event.IsBase64Encoded {
		decoded, err := base64.StdEncoding.DecodeString(event.Body)
		if err != nil {
			return APIGatewayV2Response{StatusCode: 400, Body: `{"error":"bad base64 body"}`}, nil
		}
		body = decoded
	}
	headers := map[string]string{}
	for k, v := range event.Headers {
		headers[strings.ToLower(k)] = v
	}
	path := event.RawPath
	if path == "" {
		path = "/"
	}
	resp := h.Gateway.Handle(ctx, core.Request{
		Method:  event.RequestContext.HTTP.Method,
		Path:    path,
		Headers: headers,
		Body:    body,
	})
	return APIGatewayV2Response{
		StatusCode: resp.Status,
		Headers:    resp.Headers,
		Body:       string(resp.Body),
	}, nil
}
