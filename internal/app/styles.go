package app

import "github.com/charmbracelet/lipgloss"

// Palette defines the modern theme colors
var (
	ColorPrimary   = lipgloss.Color("#7D56F4") // Purple/Indigo
	ColorSecondary = lipgloss.Color("#04B575") // Emerald Green
	ColorAccent    = lipgloss.Color("#FF5F87") // Coral/Pink
	ColorWarning   = lipgloss.Color("#FFAF00") // Amber
	ColorMuted     = lipgloss.Color("#626262") // Slate Gray
	ColorSubtle    = lipgloss.Color("#8A8A8A") // Subtle Gray
	ColorBg        = lipgloss.Color("#1A1B26") // Dark Canvas
	ColorBgLight   = lipgloss.Color("#24283B") // Surface
	ColorText      = lipgloss.Color("#C0CAF5") // Off-white/Light blue
	ColorWhite     = lipgloss.Color("#FFFFFF")
)

// UI Styles
var (
	// Header Styles
	HeaderTitleStyle = lipgloss.NewStyle().
				Bold(true).
				Foreground(ColorWhite).
				Background(ColorPrimary).
				Padding(0, 1)

	HeaderStatusStyle = lipgloss.NewStyle().
				Foreground(ColorSubtle).
				Padding(0, 1)

	HeaderBarStyle = lipgloss.NewStyle().
			Border(lipgloss.NormalBorder(), false, false, true, false).
			BorderForeground(lipgloss.Color("#3B4261")).
			MarginBottom(1)

	// User Message Styles
	UserBadgeStyle = lipgloss.NewStyle().
			Bold(true).
			Foreground(ColorWhite).
			Background(ColorPrimary).
			Padding(0, 1).
			MarginRight(1)

	UserMsgBoxStyle = lipgloss.NewStyle().
			Border(lipgloss.RoundedBorder()).
			BorderForeground(ColorPrimary).
			Padding(0, 1).
			MarginBottom(1)

	// Assistant Message Styles
	AssistantBadgeStyle = lipgloss.NewStyle().
				Bold(true).
				Foreground(ColorWhite).
				Background(ColorSecondary).
				Padding(0, 1).
				MarginRight(1)

	AssistantMsgBoxStyle = lipgloss.NewStyle().
				Border(lipgloss.RoundedBorder()).
				BorderForeground(ColorSecondary).
				Padding(0, 1).
				MarginBottom(1)

	// Thinking & Tool Execution Styles
	ThinkingContainerStyle = lipgloss.NewStyle().
				Border(lipgloss.RoundedBorder()).
				BorderForeground(ColorWarning).
				Padding(0, 1).
				MarginBottom(1)

	ThinkingTextStyle = lipgloss.NewStyle().
				Foreground(ColorWarning).
				Italic(true)

	ToolRunningBadgeStyle = lipgloss.NewStyle().
				Bold(true).
				Foreground(ColorWhite).
				Background(ColorWarning).
				Padding(0, 1)

	ToolSuccessBadgeStyle = lipgloss.NewStyle().
				Bold(true).
				Foreground(ColorWhite).
				Background(ColorSecondary).
				Padding(0, 1)

	ToolStepTextStyle = lipgloss.NewStyle().
				Foreground(ColorText).
				MarginLeft(1)

	// Input Box Styles
	InputActiveStyle = lipgloss.NewStyle().
				Border(lipgloss.RoundedBorder()).
				BorderForeground(ColorPrimary).
				Padding(0, 1)

	InputLockedStyle = lipgloss.NewStyle().
				Border(lipgloss.RoundedBorder()).
				BorderForeground(ColorMuted).
				Padding(0, 1)

	// Footer Help Style
	FooterHelpStyle = lipgloss.NewStyle().
			Foreground(ColorMuted).
			MarginTop(1)

	KeyHelpStyle = lipgloss.NewStyle().
			Foreground(ColorSubtle).
			Bold(true)
)
