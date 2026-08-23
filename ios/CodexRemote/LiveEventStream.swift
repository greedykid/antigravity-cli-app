import Foundation

/// Server-Sent Events client for `/api/events`.
///
/// The bridge streams every CLI step as it happens, so the app follows a run
/// live instead of polling. Events carry the job they belong to; anything from
/// another device or a terminal-started task is filtered out by the caller.
struct LiveEvent {
    let name: String
    let data: [String: Any]

    var jobId: String? { data["jobId"] as? String }
    var conversationId: String? { data["conversationId"] as? String }
    var isOk: Bool { (data["ok"] as? Bool) ?? true }
    var errorText: String? { data["error"] as? String }
}

actor LiveEventStream {
    private var task: Task<Void, Never>?

    func start(client: BridgeClient, onEvent: @escaping (LiveEvent) -> Void) {
        stop()
        task = Task {
            var backoff: UInt64 = 2
            while !Task.isCancelled {
                guard client.isPaired,
                      var req = client.request("/api/events", timeout: 3600) else {
                    try? await Task.sleep(nanoseconds: 5_000_000_000)
                    continue
                }
                req.setValue("text/event-stream", forHTTPHeaderField: "Accept")

                do {
                    let (bytes, response) = try await URLSession.shared.bytes(for: req)
                    guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
                        throw URLError(.badServerResponse)
                    }
                    backoff = 2

                    var eventName: String?
                    for try await line in bytes.lines {
                        if Task.isCancelled { break }
                        if line.hasPrefix(":") { continue }          // heartbeat
                        if line.hasPrefix("event:") {
                            eventName = String(line.dropFirst(6)).trimmingCharacters(in: .whitespaces)
                        } else if line.hasPrefix("data:") {
                            let raw = String(line.dropFirst(5)).trimmingCharacters(in: .whitespaces)
                            guard let name = eventName,
                                  let payload = raw.data(using: .utf8),
                                  let object = try? JSONSerialization.jsonObject(with: payload) as? [String: Any]
                            else { continue }
                            onEvent(LiveEvent(name: name, data: object))
                        } else if line.isEmpty {
                            eventName = nil
                        }
                    }
                } catch {
                    // Tunnels drop idle connections routinely; reconnect quietly.
                }

                if Task.isCancelled { break }
                try? await Task.sleep(nanoseconds: backoff * 1_000_000_000)
                backoff = min(backoff * 2, 30)
            }
        }
    }

    func stop() {
        task?.cancel()
        task = nil
    }
}
