package app

import (
	"context"
	"time"

	"github.com/charmbracelet/bubbles/spinner"
	"github.com/charmbracelet/bubbles/textarea"
	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/user/antigravity-cli-app/internal/engine"
)

// AppState represents the current state of the application lifecycle
type AppState int

const (
	StateReady AppState = iota
	StateThinking
	StateExecutingTool
	StateStreaming
	StateDone
)

func (s AppState) String() string {
	switch s {
	case StateReady:
		return "READY"
	case StateThinking:
		return "THINKING"
	case StateExecutingTool:
		return "RUNNING TOOL"
	case StateStreaming:
		return "GENERATING"
	case StateDone:
		return "COMPLETED"
	default:
		return "UNKNOWN"
	}
}

// Model is the main Bubble Tea model
type Model struct {
	State        AppState
	Engine       engine.Engine
	Messages     []engine.ChatMessage
	CurrentInput string

	// Components
	Viewport viewport.Model
	Textarea textarea.Model
	Spinner  spinner.Model

	// Active generation state
	CurrentStreamingText string
	ActiveTools          []engine.ToolExecution
	ThinkingStartTime    time.Time
	ThinkingDuration     time.Duration
	CancelFunc           context.CancelFunc

	// Window dimensions
	Width  int
	Height int
	Ready  bool
	Err    error
}

// NewModel initializes the Bubble Tea model
func NewModel(eng engine.Engine) Model {
	ta := textarea.New()
	ta.Placeholder = "Ketik pesan prompt di sini... (Enter untuk kirim, Shift+Enter untuk baris baru)"
	ta.Focus()
	ta.Prompt = "❯ "
	ta.CharLimit = 2000
	ta.SetWidth(80)
	ta.SetHeight(3)
	ta.ShowLineNumbers = false

	s := spinner.New()
	s.Spinner = spinner.Dot
	s.Style = ThinkingTextStyle

	return Model{
		State:    StateReady,
		Engine:   eng,
		Messages: make([]engine.ChatMessage, 0),
		Textarea: ta,
		Spinner:  s,
		Ready:    false,
	}
}

// Init initializes Bubble Tea commands
func (m Model) Init() tea.Cmd {
	return tea.Batch(
		textarea.Blink,
		m.Spinner.Tick,
	)
}
