import Foundation

struct SessionSummary: Identifiable, Codable, Hashable {
    let conversationId: String
    let title: String
    let timestamp: Double?
    let engine: String?

    var id: String { conversationId }
    var date: Date { Date(timeIntervalSince1970: (timestamp ?? 0) / 1000) }
    var engineValue: Engine { engine == "codex" ? .codex : .antigravity }
}

struct SessionsResponse: Codable {
    let ok: Bool?
    let hostname: String?
    let sessions: [SessionSummary]?
}

/// One entry of a transcript. Tool and thinking steps arrive as their own turns.
struct Turn: Codable, Identifiable, Hashable {
    let role: String?
    let content: String?
    let time: String?
    let title: String?
    let toolTitle: String?
    let command: String?

    var id: String { (role ?? "?") + "|" + (time ?? "") + "|" + String((content ?? "").prefix(48)) }

    var text: String { (content ?? "").trimmingCharacters(in: .whitespacesAndNewlines) }
    var isUser: Bool { role == "user" }
    var isAssistant: Bool { role == "assistant" }
    var isStep: Bool { role == "tool" || role == "thinking" }
    var stepLabel: String { title ?? toolTitle ?? (role == "thinking" ? "Thinking" : "Tool") }
}

struct TranscriptResponse: Codable {
    let ok: Bool?
    let conversationId: String?
    let turns: [Turn]?
    let session: SessionSummary?
}

struct ChatAccepted: Codable {
    let ok: Bool?
    let jobId: String?
    let conversationId: String?
    let response: String?
    let error: String?
}

struct JobEnvelope: Codable {
    let ok: Bool?
    let job: Job?
}

struct Job: Codable {
    let id: String?
    let state: String?
    let conversationId: String?
    let response: String?
    let error: String?

    var isRunning: Bool { state == "running" }
}

struct QuotaLimit: Codable, Hashable {
    let label: String?
    let percent: Int?
    let resetAt: String?
}

struct QuotaGroup: Codable, Identifiable, Hashable {
    let group: String?
    let limits: [QuotaLimit]?
    var id: String { group ?? UUID().uuidString }
}

struct UsageResponse: Codable {
    let account: String?
    let totalPrompts: Int?
    let totalSessions: Int?
    let totalSteps: Int?
    let totalTools: Int?
    let estimatedTokens: Double?
    let promptsLast5h: Int?
    let promptsLast24h: Int?
    let promptsLast7d: Int?
    let quotaKnown: Bool?
    let quotaGroups: [QuotaGroup]?
    let quotaStale: Bool?
    let quotaStatus: String?
    let memoryUsage: String?
    let hostname: String?
    let uptime: String?
}

struct SettingsPayload: Codable {
    let sandboxMode: String?
    let notifyOnFinish: Bool?
    let taskTimeoutMinutes: Int?
}

struct SettingsResponse: Codable {
    let ok: Bool?
    let settings: SettingsPayload?
}

/// Payload encoded in the pairing QR / `agy://connect` link.
struct PairingPayload: Codable {
    let url: String
    let token: String
    let engine: String?
    let name: String?
}
