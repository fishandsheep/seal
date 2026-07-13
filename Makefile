.PHONY: generate test build clean

generate:
	go generate ./...

test:
	go test ./...

build: generate test
	CGO_ENABLED=0 go build -trimpath -ldflags "-s -w" -o seal ./cmd/seal

clean:
	rm -f seal coverage.out
