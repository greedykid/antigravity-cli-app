# Binary output name
BINARY_NAME=agy

.PHONY: all build test run clean deps lint build-all package apk

all: build

deps:
	go mod tidy
	go mod download

build:
	@mkdir -p bin
	go build -ldflags="-s -w" -o bin/$(BINARY_NAME) ./cmd/agy

run: build
	./bin/$(BINARY_NAME)

test:
	go test -v -race ./...

clean:
	rm -rf bin dist

# Cross-compilation targets
build-all:
	@mkdir -p dist
	GOOS=linux GOARCH=amd64 go build -ldflags="-s -w" -o dist/$(BINARY_NAME)-linux-amd64 ./cmd/agy
	GOOS=linux GOARCH=arm64 go build -ldflags="-s -w" -o dist/$(BINARY_NAME)-linux-arm64 ./cmd/agy
	GOOS=darwin GOARCH=amd64 go build -ldflags="-s -w" -o dist/$(BINARY_NAME)-darwin-amd64 ./cmd/agy
	GOOS=darwin GOARCH=arm64 go build -ldflags="-s -w" -o dist/$(BINARY_NAME)-darwin-arm64 ./cmd/agy
	GOOS=windows GOARCH=amd64 go build -ldflags="-s -w" -o dist/$(BINARY_NAME)-windows-amd64.exe ./cmd/agy
	GOOS=android GOARCH=arm64 go build -ldflags="-s -w" -o dist/$(BINARY_NAME)-android-arm64 ./cmd/agy

# Package Alpine APK & Debian DEB via nfpm
package: build-all
	nfpm package --config nfpm.yaml --packager apk --target dist/
	nfpm package --config nfpm.yaml --packager deb --target dist/

apk: build-all
	nfpm package --config nfpm.yaml --packager apk --target dist/
