package main

import (
	"fmt"
	"os"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/user/antigravity-cli-app/internal/app"
	"github.com/user/antigravity-cli-app/internal/engine"
)

func main() {
	// Initialize the AI engine
	eng := engine.NewSimulatedEngine()

	// Initialize the Bubble Tea model
	m := app.NewModel(eng)

	// Create Bubble Tea program with AltScreen to prevent terminal flickering
	p := tea.NewProgram(
		m,
		tea.WithAltScreen(),
		tea.WithMouseCellMotion(),
	)

	if _, err := p.Run(); err != nil {
		fmt.Fprintf(os.Stderr, "Error running Antigravity CLI: %v\n", err)
		os.Exit(1)
	}
}
