package engine

import (
	"context"
	"testing"
	"time"
)

func TestSimulatedEngine_ProcessPrompt(t *testing.T) {
	eng := NewSimulatedEngine()
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	history := []ChatMessage{}
	stream, err := eng.ProcessPrompt(ctx, history, "halo")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	var chunks []StreamChunk
	for chunk := range stream {
		chunks = append(chunks, chunk)
	}

	if len(chunks) == 0 {
		t.Fatalf("expected stream chunks, got 0")
	}

	lastChunk := chunks[len(chunks)-1]
	if !lastChunk.IsDone {
		t.Errorf("expected last chunk to have IsDone=true")
	}
}
