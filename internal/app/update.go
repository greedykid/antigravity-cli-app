package app

import (
	"fmt"
	"strings"
	"time"

	"github.com/charmbracelet/bubbles/spinner"
	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/user/antigravity-cli-app/internal/engine"
)

// Update handles all state transitions and events
func (m Model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	var (
		cmds []tea.Cmd
		cmd  tea.Cmd
	)

	switch msg := msg.(type) {
	case tea.KeyMsg:
		switch msg.Type {
		case tea.KeyCtrlC, tea.KeyCtrlD:
			if m.CancelFunc != nil {
				m.CancelFunc()
			}
			return m, tea.Quit

		case tea.KeyEsc:
			if m.State != StateReady {
				if m.CancelFunc != nil {
					m.CancelFunc()
				}
				m.finishCurrentTurn()
				m.updateViewportContent()
				return m, nil
			}

		case tea.KeyCtrlL:
			m.Messages = nil
			m.CurrentStreamingText = ""
			m.ActiveTools = nil
			m.updateViewportContent()
			return m, nil

		case tea.KeyEnter:
			// Submit prompt when Enter is pressed without Shift
			if m.State == StateReady && !msg.Alt {
				prompt := strings.TrimSpace(m.Textarea.Value())
				if prompt == "" {
					return m, nil
				}

				// 1. Commit user message to history
				m.Messages = append(m.Messages, engine.ChatMessage{
					ID:        fmt.Sprintf("msg-%d", time.Now().UnixNano()),
					Role:      engine.RoleUser,
					Content:   prompt,
					Timestamp: time.Now(),
				})

				// 2. Reset and lock textarea
				m.Textarea.Reset()
				m.Textarea.Blur()

				// 3. Transition to Thinking state
				m.State = StateThinking
				m.ThinkingStartTime = time.Now()
				m.ThinkingDuration = 0
				m.CurrentStreamingText = ""
				m.ActiveTools = nil

				// 4. Start processing command
				startCmd, cancel := StartProcessingCmd(m.Engine, m.Messages, prompt)
				m.CancelFunc = cancel

				m.updateViewportContent()
				return m, tea.Batch(
					startCmd,
					m.Spinner.Tick,
					TickEvery(100*time.Millisecond),
				)
			}
		}

	case tea.WindowSizeMsg:
		m.Width = msg.Width
		m.Height = msg.Height

		headerHeight := 3
		inputHeight := 5
		footerHeight := 2
		viewportHeight := msg.Height - headerHeight - inputHeight - footerHeight
		if viewportHeight < 5 {
			viewportHeight = 5
		}

		if !m.Ready {
			m.Viewport = viewport.New(msg.Width, viewportHeight)
			m.Viewport.YPosition = headerHeight
			m.Ready = true
		} else {
			m.Viewport.Width = msg.Width
			m.Viewport.Height = viewportHeight
		}

		m.Textarea.SetWidth(msg.Width - 4)
		m.updateViewportContent()

	case StreamChunkMsg:
		if msg.Chunk.IsToolCall {
			m.State = StateExecutingTool
			m.ActiveTools = append(m.ActiveTools, engine.ToolExecution{
				Name:      msg.Chunk.ToolName,
				Input:     msg.Chunk.ToolInput,
				Status:    engine.ToolStatusRunning,
				Timestamp: time.Now(),
			})
		} else if msg.Chunk.Content != "" {
			m.State = StateStreaming
			m.CurrentStreamingText += msg.Chunk.Content
		}

		if msg.Chunk.IsDone {
			m.finishCurrentTurn()
			m.updateViewportContent()
			return m, nil
		}

		m.updateViewportContent()
		return m, WaitForNextChunkCmd(msg.Stream)

	case StreamDoneMsg:
		m.finishCurrentTurn()
		m.updateViewportContent()
		return m, nil

	case StreamErrorMsg:
		m.Err = msg.Err
		m.finishCurrentTurn()
		m.updateViewportContent()
		return m, nil

	case TimerTickMsg:
		if m.State != StateReady && m.State != StateDone {
			m.ThinkingDuration = time.Since(m.ThinkingStartTime)
			return m, TickEvery(100 * time.Millisecond)
		}

	case spinner.TickMsg:
		if m.State == StateThinking || m.State == StateExecutingTool {
			m.Spinner, cmd = m.Spinner.Update(msg)
			cmds = append(cmds, cmd)
			m.updateViewportContent()
		}
	}

	// Update components
	if m.State == StateReady {
		m.Textarea, cmd = m.Textarea.Update(msg)
		cmds = append(cmds, cmd)
	}

	m.Viewport, cmd = m.Viewport.Update(msg)
	cmds = append(cmds, cmd)

	return m, tea.Batch(cmds...)
}

// finishCurrentTurn finalizes the current streaming response into permanent message history
func (m *Model) finishCurrentTurn() {
	if m.CurrentStreamingText != "" || len(m.ActiveTools) > 0 {
		m.Messages = append(m.Messages, engine.ChatMessage{
			ID:        fmt.Sprintf("msg-%d", time.Now().UnixNano()),
			Role:      engine.RoleAssistant,
			Content:   m.CurrentStreamingText,
			Tools:     m.ActiveTools,
			Timestamp: time.Now(),
		})
	}

	m.State = StateReady
	m.CurrentStreamingText = ""
	m.ActiveTools = nil
	m.CancelFunc = nil
	m.Textarea.Focus()
}
