package app

import (
	"context"
	"time"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/user/antigravity-cli-app/internal/engine"
)

// Message types for Bubble Tea update loop
type (
	// StreamChunkMsg carries a chunk and the channel reference to continue reading
	StreamChunkMsg struct {
		Chunk  engine.StreamChunk
		Stream <-chan engine.StreamChunk
	}

	// StreamDoneMsg is sent when the stream finishes
	StreamDoneMsg struct{}

	// StreamErrorMsg is sent when an error occurs during streaming
	StreamErrorMsg struct {
		Err error
	}

	// TimerTickMsg updates elapsed thinking/execution time
	TimerTickMsg time.Time
)

// TickEvery returns a command that sends a TimerTickMsg periodically
func TickEvery(d time.Duration) tea.Cmd {
	return tea.Tick(d, func(t time.Time) tea.Msg {
		return TimerTickMsg(t)
	})
}

// StartProcessingCmd starts the AI processing in background and streams back chunks
func StartProcessingCmd(eng engine.Engine, history []engine.ChatMessage, prompt string) (tea.Cmd, context.CancelFunc) {
	ctx, cancel := context.WithCancel(context.Background())

	cmd := func() tea.Msg {
		stream, err := eng.ProcessPrompt(ctx, history, prompt)
		if err != nil {
			return StreamErrorMsg{Err: err}
		}

		// Read the first chunk from the channel
		chunk, ok := <-stream
		if !ok {
			return StreamDoneMsg{}
		}

		return StreamChunkMsg{
			Chunk:  chunk,
			Stream: stream,
		}
	}

	return cmd, cancel
}

// WaitForNextChunkCmd continues reading from an active stream channel
func WaitForNextChunkCmd(stream <-chan engine.StreamChunk) tea.Cmd {
	return func() tea.Msg {
		chunk, ok := <-stream
		if !ok {
			return StreamDoneMsg{}
		}
		return StreamChunkMsg{
			Chunk:  chunk,
			Stream: stream,
		}
	}
}
