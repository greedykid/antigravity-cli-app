package engine

import "time"

// Role defines the message author role (user, assistant, tool, system)
type Role string

const (
	RoleUser      Role = "user"
	RoleAssistant Role = "assistant"
	RoleTool      Role = "tool"
	RoleSystem    Role = "system"
)

// ToolStatus indicates the state of a tool call
type ToolStatus string

const (
	ToolStatusRunning ToolStatus = "running"
	ToolStatusSuccess ToolStatus = "success"
	ToolStatusError   ToolStatus = "error"
)

// ToolExecution represents an executed tool call step
type ToolExecution struct {
	Name      string
	Input     string
	Output    string
	Status    ToolStatus
	Duration  time.Duration
	Timestamp time.Time
}

// ChatMessage represents a single message in the chat history
type ChatMessage struct {
	ID        string
	Role      Role
	Content   string
	Tools     []ToolExecution
	Timestamp time.Time
}

// StreamChunk represents a piece of content streamed from the AI
type StreamChunk struct {
	Content      string
	IsToolCall   bool
	ToolName     string
	ToolInput    string
	IsDone       bool
	ErrorMessage string
}
