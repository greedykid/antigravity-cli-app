package app

import (
	"fmt"
	"strings"

	"github.com/charmbracelet/lipgloss"
	"github.com/user/antigravity-cli-app/internal/engine"
)

// View renders the overall UI layout
func (m Model) View() string {
	if !m.Ready {
		return "\n  Memuat Antigravity CLI..."
	}

	var sections []string

	// 1. Header Bar
	sections = append(sections, m.renderHeader())

	// 2. Viewport (Chat History & Active Stream)
	sections = append(sections, m.Viewport.View())

	// 3. Input Box
	sections = append(sections, m.renderInput())

	// 4. Footer Shortcuts
	sections = append(sections, m.renderFooter())

	return lipgloss.JoinVertical(lipgloss.Left, sections...)
}

// renderHeader renders the top status bar
func (m Model) renderHeader() string {
	title := HeaderTitleStyle.Render("✦ ANTIGRAVITY AI CLI")

	var stateColor lipgloss.Color
	switch m.State {
	case StateReady:
		stateColor = ColorSecondary
	case StateThinking, StateExecutingTool:
		stateColor = ColorWarning
	case StateStreaming:
		stateColor = ColorPrimary
	default:
		stateColor = ColorSubtle
	}

	stateBadge := lipgloss.NewStyle().
		Bold(true).
		Foreground(ColorWhite).
		Background(stateColor).
		Padding(0, 1).
		Render(fmt.Sprintf("[%s]", m.State.String()))

	var durationText string
	if m.ThinkingDuration > 0 && m.State != StateReady {
		durationText = fmt.Sprintf(" • %.1fs", m.ThinkingDuration.Seconds())
	}

	statusText := HeaderStatusStyle.Render(fmt.Sprintf("Messages: %d%s", len(m.Messages), durationText))

	barContent := lipgloss.JoinHorizontal(
		lipgloss.Center,
		title,
		" ",
		stateBadge,
		statusText,
	)

	return HeaderBarStyle.Width(m.Width).Render(barContent)
}

// renderInput renders the interactive prompt input box
func (m Model) renderInput() string {
	if m.State == StateReady {
		return InputActiveStyle.Width(m.Width - 2).Render(m.Textarea.View())
	}

	// Locked state view
	lockedNotice := lipgloss.NewStyle().
		Foreground(ColorMuted).
		Italic(true).
		Render("⏳ AI sedang memproses... Tekan [Esc] untuk membatalkan.")

	return InputLockedStyle.Width(m.Width - 2).Render(lockedNotice)
}

// renderFooter renders keyboard shortcuts
func (m Model) renderFooter() string {
	helpItems := []string{
		fmt.Sprintf("%s Kirim", KeyHelpStyle.Render("Enter:")),
		fmt.Sprintf("%s Batal", KeyHelpStyle.Render("Esc:")),
		fmt.Sprintf("%s Hapus Chat", KeyHelpStyle.Render("Ctrl+L:")),
		fmt.Sprintf("%s Keluar", KeyHelpStyle.Render("Ctrl+C:")),
	}

	return FooterHelpStyle.Render("  " + strings.Join(helpItems, "   •   "))
}

// updateViewportContent updates the chat history content in the viewport and scrolls down
func (m *Model) updateViewportContent() {
	var body strings.Builder

	if len(m.Messages) == 0 && m.CurrentStreamingText == "" && len(m.ActiveTools) == 0 && m.State == StateReady {
		welcome := lipgloss.NewStyle().
			Foreground(ColorSubtle).
			Padding(1, 2).
			Render("Selamat datang di Antigravity CLI!\nKetik instruksi atau prompt Anda di bawah untuk memulai sesi.")
		body.WriteString(welcome + "\n")
	}

	// Render historical messages
	for _, msg := range m.Messages {
		if msg.Role == engine.RoleUser {
			badge := UserBadgeStyle.Render("YOU")
			content := lipgloss.NewStyle().Foreground(ColorWhite).Render(msg.Content)
			userBlock := fmt.Sprintf("%s\n%s", badge, content)
			body.WriteString(UserMsgBoxStyle.Width(m.Width - 4).Render(userBlock))
			body.WriteString("\n")
		} else if msg.Role == engine.RoleAssistant {
			// Render tool steps if any
			for _, tool := range msg.Tools {
				toolBadge := ToolSuccessBadgeStyle.Render("✓ TOOL: " + tool.Name)
				toolInfo := ToolStepTextStyle.Render(tool.Input)
				body.WriteString(fmt.Sprintf("%s %s\n", toolBadge, toolInfo))
			}

			badge := AssistantBadgeStyle.Render("AGENT")
			content := lipgloss.NewStyle().Foreground(ColorText).Render(msg.Content)
			agentBlock := fmt.Sprintf("%s\n%s", badge, content)
			body.WriteString(AssistantMsgBoxStyle.Width(m.Width - 4).Render(agentBlock))
			body.WriteString("\n")
		}
	}

	// Render in-progress thinking / active tools / streaming
	if m.State == StateThinking {
		spinnerText := fmt.Sprintf("%s %s (%.1fs)",
			m.Spinner.View(),
			ThinkingTextStyle.Render("Memproses prompt & merencanakan respon..."),
			m.ThinkingDuration.Seconds(),
		)
		body.WriteString(ThinkingContainerStyle.Width(m.Width - 4).Render(spinnerText))
		body.WriteString("\n")
	}

	if m.State == StateExecutingTool {
		for _, tool := range m.ActiveTools {
			spinnerText := fmt.Sprintf("%s %s %s (%.1fs)",
				m.Spinner.View(),
				ToolRunningBadgeStyle.Render("⚙ RUNNING TOOL: "+tool.Name),
				ToolStepTextStyle.Render(tool.Input),
				m.ThinkingDuration.Seconds(),
			)
			body.WriteString(ThinkingContainerStyle.Width(m.Width - 4).Render(spinnerText))
			body.WriteString("\n")
		}
	}

	if m.State == StateStreaming && m.CurrentStreamingText != "" {
		badge := AssistantBadgeStyle.Render("AGENT (STREAMING)")
		content := lipgloss.NewStyle().Foreground(ColorText).Render(m.CurrentStreamingText + " ▌")
		agentBlock := fmt.Sprintf("%s\n%s", badge, content)
		body.WriteString(AssistantMsgBoxStyle.Width(m.Width - 4).Render(agentBlock))
		body.WriteString("\n")
	}

	m.Viewport.SetContent(body.String())
	m.Viewport.GotoBottom()
}
