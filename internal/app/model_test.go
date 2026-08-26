package app

import (
	"testing"

	"github.com/user/antigravity-cli-app/internal/engine"
)

func TestNewModel(t *testing.T) {
	eng := engine.NewSimulatedEngine()
	m := NewModel(eng)

	if m.State != StateReady {
		t.Errorf("expected initial state to be StateReady, got %v", m.State)
	}

	if len(m.Messages) != 0 {
		t.Errorf("expected initial messages to be empty, got %d", len(m.Messages))
	}
}
