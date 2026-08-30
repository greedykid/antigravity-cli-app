import Foundation
import SwiftUI
import UserNotifications

@MainActor
final class AppState: ObservableObject {

    // MARK: stored connection
    @AppStorage("bridge_url") var endpoint: String = ""
    @AppStorage("bridge_token") var token: String = ""
    @AppStorage("engine") private var engineRaw: String = Engine.antigravity.rawValue
    @AppStorage("model_antigravity") var modelAntigravity: String = Engine.antigravity.defaultModel
    @AppStorage("model_codex") var modelCodex: String = Engine.codex.defaultModel

    // MARK: session state
    @Published var sessions: [SessionSummary] = []
    @Published var turns: [Turn] = []
    @Published var activeConversationId: String?
    @Published var activeSessionTitle: String = "Sesi baru"
    @Published var isRunning = false
    @Published var status: String = "Belum dipasangkan"
    @Published var errorMessage: String?
    @Published var loadingSessions = false
    @Published var streamingResponse: String = ""
    @Published var failedPrompt: String?

    /// Rendered immediately so the message never waits on the round trip, and
    /// kept until the run ends — a partial transcript must not erase it.
    @Published var pendingPrompt: String?

    private var activeJobId: String?
    /// Bumped whenever the open conversation changes, so a reply that lands
    /// after the user moved on is discarded instead of painting the wrong chat.
    private var sessionEpoch = 0
    private let stream = LiveEventStream()

    var engine: Engine {
        get { Engine(rawValue: engineRaw) ?? .antigravity }
        set { engineRaw = newValue.rawValue }
    }

    var palette: Palette { Palette.of(engine) }

    var model: String {
        get { engine == .codex ? modelCodex : modelAntigravity }
        set {
            if engine == .codex { modelCodex = newValue } else { modelAntigravity = newValue }
        }
    }

    var client: BridgeClient { BridgeClient(endpoint: endpoint, token: token) }
    var isPaired: Bool { client.isPaired }

    // MARK: - pairing

    func applyPairing(_ payload: PairingPayload) {
        endpoint = Self.normalizeEndpoint(payload.url)
        token = payload.token.trimmingCharacters(in: .whitespacesAndNewlines)
        if let raw = payload.engine, let parsed = Engine(rawValue: raw) { engine = parsed }
        startNewSession()
        Task {
            await refreshStatus()
            await loadSessions()
            await restartLiveEvents()
        }
    }

    /// Accepts a bare host, an `agy://connect?...` link, or raw JSON.
    static func parsePairingText(_ raw: String) -> PairingPayload? {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }

        if text.hasPrefix("{"), let data = text.data(using: .utf8),
           let payload = try? JSONDecoder().decode(PairingPayload.self, from: data) {
            return payload
        }

        if let components = URLComponents(string: text), components.scheme == "agy" {
            let items = components.queryItems ?? []
            let url = items.first(where: { $0.name == "url" })?.value ?? ""
            let token = items.first(where: { $0.name == "token" })?.value ?? ""
            if !url.isEmpty && !token.isEmpty {
                return PairingPayload(url: url, token: token,
                                      engine: items.first(where: { $0.name == "engine" })?.value,
                                      name: items.first(where: { $0.name == "name" })?.value)
            }
        }
        return nil
    }

    static func normalizeEndpoint(_ raw: String) -> String {
        var url = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if url.isEmpty { return url }
        if !url.hasPrefix("http://") && !url.hasPrefix("https://") { url = "https://" + url }
        while url.hasSuffix("/") { url.removeLast() }
        if url.hasSuffix("/api/chat") { return url }
        if url.hasSuffix("/api") { return url + "/chat" }
        return url + "/api/chat"
    }

    func unpair() {
        endpoint = ""
        token = ""
        sessions = []
        turns = []
        activeConversationId = nil
        status = "Belum dipasangkan"
        Task { await stream.stop() }
    }

    // MARK: - engine

    func switchEngine(to target: Engine) {
        guard target != engine else { return }
        let previous = engine
        let previousJobId = activeJobId
        let previousConvId = activeConversationId
        engine = target
        // Kill any in-flight job from the previous engine before we forget
        // its id, otherwise the orphan subprocess keeps streaming events
        // for a chat the user can no longer see.
        Task { await stopPreviousEngineIfAny(jobId: previousJobId,
                                              conversationId: previousConvId,
                                              engine: previous) }
        startNewSession()
        Task { await loadSessions() }
    }

    /// Fire-and-forget kill of an in-flight job tied to the engine we just
    /// left. The bridge now scopes pkill by jobId + conversationId so this
    /// cannot take out a different device's run.
    private func stopPreviousEngineIfAny(jobId: String?,
                                         conversationId: String?,
                                         engine: Engine) async {
        guard jobId != nil || conversationId != nil else { return }
        var body: [String: Any] = ["action": "stop"]
        if let jobId { body["jobId"] = jobId }
        if let conversationId { body["conversationId"] = conversationId }
        body["engine"] = engine.rawValue
        _ = try? await client.post("/api/session/control", body: body,
                                   as: ChatAccepted.self, timeout: 8)
    }

    // MARK: - sessions

    func startNewSession() {
        sessionEpoch += 1
        activeJobId = nil
        activeConversationId = nil
        activeSessionTitle = "Sesi baru"
        turns = []
        pendingPrompt = nil
        isRunning = false
    }

    func loadSessions() async {
        guard isPaired else { return }
        loadingSessions = true
        defer { loadingSessions = false }
        do {
            let response = try await client.get(
                "/api/sessions?engine=\(engine.rawValue)", as: SessionsResponse.self)
            // Filter again on arrival: a stale or unfiltered reply must not leak
            // the other engine's history into the list.
            sessions = (response.sessions ?? []).filter { $0.engineValue == engine }
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func open(session: SessionSummary) async {
        sessionEpoch += 1
        let epoch = sessionEpoch
        activeJobId = nil
        activeConversationId = session.conversationId
        activeSessionTitle = session.title
        turns = []
        await loadTranscript(for: session.conversationId, epoch: epoch)
    }

    /// Opens the newest chat of the active engine — what "Obrolan" implies.
    func openLatestConversation() async {
        if activeConversationId != nil { return }
        await loadSessions()
        if let newest = sessions.first {
            await open(session: newest)
        } else {
            startNewSession()
        }
    }

    private func loadTranscript(for conversationId: String, epoch: Int) async {
        do {
            let response = try await client.get(
                "/api/session/transcript?id=\(BridgeClient.encode(conversationId))",
                as: TranscriptResponse.self, timeout: 30)
            guard epoch == sessionEpoch else { return }

            let incoming = response.turns ?? []
            // Never repaint a running chat from an empty transcript: the Codex
            // rollout is parsed while the CLI is still writing it, so a poll can
            // momentarily come back with nothing.
            if incoming.isEmpty && isRunning && !turns.isEmpty { return }

            turns = incoming
            if let title = response.session?.title, !title.isEmpty { activeSessionTitle = title }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func refreshActiveTranscript() async {
        guard let conversationId = activeConversationId else { return }
        await loadTranscript(for: conversationId, epoch: sessionEpoch)
    }

    // MARK: - sending

    func send(prompt: String) async {
        let text = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, isPaired else { return }

        let epoch = sessionEpoch
        pendingPrompt = text
        streamingResponse = ""
        failedPrompt = nil
        isRunning = true
        errorMessage = nil

        var body: [String: Any] = [
            "prompt": text,
            "engine": engine.rawValue,
            "model": model,
            "async": true
        ]
        if let conversationId = activeConversationId {
            body["conversationId"] = conversationId
            body["resume"] = true
        }

        do {
            let accepted = try await client.post("/api/chat", body: body, as: ChatAccepted.self)
            guard epoch == sessionEpoch else { return }

            if let jobId = accepted.jobId {
                activeJobId = jobId
                // Ask the SSE stream to replay any cli.event/cli.output that
                // landed while our POST was still in flight. Without this the
                // first chunks of the run would be dropped because the server
                // didn't know to filter by our jobId.
                await restartLiveEvents(replayJobId: jobId)
                await waitForJob(jobId, epoch: epoch)
            } else {
                // Server predates jobs: the reply already carries the result.
                if let conversationId = accepted.conversationId { adopt(conversationId) }
                finishRun(epoch: epoch)
                await refreshActiveTranscript()
            }
        } catch {
            errorMessage = error.localizedDescription
            failedPrompt = text
            finishRun(epoch: epoch)
        }
    }

    private func waitForJob(_ jobId: String, epoch: Int) async {
        var delay: UInt64 = 1_500_000_000
        let deadline = Date().addingTimeInterval(4 * 60 * 60)

        while Date() < deadline {
            try? await Task.sleep(nanoseconds: delay)
            delay = min(delay + 500_000_000, 8_000_000_000)
            guard epoch == sessionEpoch else { return }

            do {
                let envelope = try await client.get("/api/jobs/\(BridgeClient.encode(jobId))",
                                                    as: JobEnvelope.self, timeout: 15)
                guard let job = envelope.job else { continue }
                if let conversationId = job.conversationId { adopt(conversationId) }
                if job.isRunning {
                    await refreshActiveTranscript()
                    continue
                }
                if let failure = job.error, !failure.isEmpty { errorMessage = failure }
                finishRun(epoch: epoch)
                await refreshActiveTranscript()
                return
            } catch {
                // Network blip: the job keeps running on the server.
            }
        }
        finishRun(epoch: epoch)
    }

    private func adopt(_ conversationId: String) {
        guard activeConversationId == nil, !conversationId.isEmpty else { return }
        activeConversationId = conversationId
    }

    private func finishRun(epoch: Int) {
        guard epoch == sessionEpoch else { return }
        isRunning = false
        activeJobId = nil
        pendingPrompt = nil
    }

    func interrupt() async {
        _ = try? await client.post("/api/session/control", body: ["action": "stop"],
                                   as: ChatAccepted.self, timeout: 20)
        isRunning = false
        pendingPrompt = nil
    }

    // MARK: - live events

    func restartLiveEvents(replayJobId: String? = nil) async {
        let snapshot = client
        await stream.start(client: snapshot,
                           onEvent: { [weak self] event in
                               Task { @MainActor in self?.handle(event) }
                           },
                           replayJobId: replayJobId)
    }

    private func handle(_ event: LiveEvent) {
        // Only follow the job this screen started; a task from the terminal or
        // another device must not swap the transcript being read.
        guard let jobId = event.jobId, jobId == activeJobId else { return }

        switch event.name {
        case "task.started":
            isRunning = true
        case "task.finished":
            if !event.isOk, let failure = event.errorText { errorMessage = failure }
            isRunning = false
            streamingResponse = ""
            pendingPrompt = nil
            postNotification(title: event.isOk ? "✅ Tugas Selesai: \(activeSessionTitle)" : "⚠️ Tugas Gagal: \(activeSessionTitle)",
                             body: event.isOk ? "AI telah selesai mengerjakan tugas coding Anda." : (event.errorText ?? "Terjadi kesalahan."))
            // The server inserts a 250ms settle delay before broadcasting
            // task.finished, but the first /api/session/transcript poll can
            // still race the rollout flush on slow disks. Re-poll once after
            // 500ms if we still have no turns.
            Task {
                await refreshActiveTranscript()
                if turns.isEmpty {
                    try? await Task.sleep(nanoseconds: 500_000_000)
                    await refreshActiveTranscript()
                }
            }
        case "cli.event", "cli.output":
            if let conversationId = event.conversationId { adopt(conversationId) }
            if event.name == "cli.output", let chunk = event.data["chunk"] as? String {
                streamingResponse += chunk
            }
            Task { await refreshActiveTranscript() }
        default:
            break
        }
    }

    // MARK: - notifications & upload

    func requestNotificationPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    func postNotification(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        let req = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(req)
    }

    struct UploadResult: Codable {
        let ok: Bool?
        let filePath: String?
        let filename: String?
    }

    func uploadImage(data: Data, filename: String = "photo.jpg") async -> String? {
        guard isPaired else { return nil }
        let base64 = data.base64EncodedString()
        let body: [String: Any] = [
            "filename": filename,
            "data": base64
        ]
        if let res = try? await client.post("/api/upload", body: body, as: UploadResult.self) {
            return res.filePath
        }
        return nil
    }

    // MARK: - status

    func refreshStatus() async {
        status = await client.checkConnection()
    }
}
