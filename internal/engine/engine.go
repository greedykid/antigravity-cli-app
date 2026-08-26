package engine

import (
	"context"
	"fmt"
	"strings"
	"time"
)

// Engine defines the interface for AI processing and streaming
type Engine interface {
	ProcessPrompt(ctx context.Context, history []ChatMessage, prompt string) (<-chan StreamChunk, error)
}

// SimulatedEngine simulates an intelligent agent with realistic thinking, tool calls, and streaming
type SimulatedEngine struct{}

// NewSimulatedEngine creates a new simulated engine
func NewSimulatedEngine() *SimulatedEngine {
	return &SimulatedEngine{}
}

// ProcessPrompt generates a simulated realistic agent stream with thinking, tool execution, and token streaming
func (e *SimulatedEngine) ProcessPrompt(ctx context.Context, history []ChatMessage, prompt string) (<-chan StreamChunk, error) {
	out := make(chan StreamChunk, 32)

	go func() {
		defer close(out)

		// 1. Thinking phase delay (simulating API latency + initial token generation)
		select {
		case <-ctx.Done():
			return
		case <-time.After(450 * time.Millisecond):
		}

		lowerPrompt := strings.ToLower(prompt)

		// 2. Check if a tool execution should be simulated
		if strings.Contains(lowerPrompt, "cek") || strings.Contains(lowerPrompt, "file") || strings.Contains(lowerPrompt, "list") || strings.Contains(lowerPrompt, "cari") {
			// Emit tool call
			out <- StreamChunk{
				IsToolCall: true,
				ToolName:   "search_files",
				ToolInput:  `{"query": "` + prompt + `", "max_depth": 3}`,
			}

			// Simulating tool execution time
			select {
			case <-ctx.Done():
				return
			case <-time.After(600 * time.Millisecond):
			}
		}

		// 3. Generate response text
		var fullResponse string
		if strings.Contains(lowerPrompt, "halo") || strings.Contains(lowerPrompt, "hi") {
			fullResponse = "Halo! Sesi siap digunakan. Silakan masukkan perintah, pertanyaan, atau instruksi coding untuk mulai."
		} else if strings.Contains(lowerPrompt, "cek") || strings.Contains(lowerPrompt, "file") {
			fullResponse = fmt.Sprintf("Saya telah memeriksa direktori proyek untuk query `%s`.\n\nBerikut ringkasannya:\n- **Struktur Proyek**: Modular dengan Go & Bubble Tea\n- **Status Flow**: Transisi state aktif (*Idle* → *Thinking* → *Tool Exec* → *Streaming*)\n- **Render Engine**: Lipgloss + Glamour Markdown\n\nSemua komponen berjalan dengan normal dan siap dikompilasi.", prompt)
		} else {
			fullResponse = fmt.Sprintf("Menerima instruksi: **\"%s\"**.\n\nBerikut langkah yang telah diproses:\n1. **Input validation**: Input terverifikasi.\n2. **Execution context**: Menyiapkan buffer stream.\n3. **Hasil**: State transition berjalan mulus tanpa flickering.\n\n```go\n// Output render snapshot\nfmt.Println(\"Status: OK\")\n```", prompt)
		}

		// 4. Stream response word by word / chunk by chunk with smooth timing (20-35ms)
		words := strings.Split(fullResponse, " ")
		for i, word := range words {
			select {
			case <-ctx.Done():
				return
			case <-time.After(28 * time.Millisecond):
				separator := " "
				if i == len(words)-1 {
					separator = ""
				}
				out <- StreamChunk{
					Content: word + separator,
				}
			}
		}

		// 5. Done chunk
		out <- StreamChunk{
			IsDone: true,
		}
	}()

	return out, nil
}
