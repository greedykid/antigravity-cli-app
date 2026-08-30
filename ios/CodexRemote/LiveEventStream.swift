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

    /// Gap threshold before we treat an idle SSE as "tunnel flapping" and
    /// probe the bridge health endpoint instead of tearing the socket down.
    /// 45s = 3x the server's 15s heartbeat.
    private let gapProbeSeconds: TimeInterval = 45
    private let maxBackoff: UInt64 = 30

    /// Mutable state shared between the SSE line reader and the gap probe.
    /// Reference-typed so concurrent tasks see the same memory.
    private final class GapState {
        var lastEventAt: Date = Date()
        var consecutiveIdleProbes: Int = 0
    }

    func start(client: BridgeClient,
               onEvent: @escaping (LiveEvent) -> Void,
               replayJobId: String? = nil) {
        stop()
        let path = LiveEventStream.ssePath(replayJobId: replayJobId)
        task = Task {
            var backoff: UInt64 = 2
            while !Task.isCancelled {
                guard client.isPaired,
                      var req = client.request(path, timeout: 3600) else {
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
                    let state = GapState()

                    // Race the SSE line iterator against a periodic health
                    // probe. If the iterator yields nothing for 45s we
                    // ping /api/health instead of tearing the socket down
                    // (which would drop buffered cli.output chunks).
                    let probeTask = Task { [gapProbeSeconds] in
                        while !Task.isCancelled {
                            try? await Task.sleep(nanoseconds: 5_000_000_000)
                            if Task.isCancelled { break }
                            let idle = Date().timeIntervalSince(state.lastEventAt)
                            if idle > gapProbeSeconds {
                                state.consecutiveIdleProbes += 1
                                _ = await client.checkConnection()
                                state.lastEventAt = Date()
                                if state.consecutiveIdleProbes >= 3 {
                                    throw URLError(.networkConnectionLost)
                                }
                            }
                        }
                    }

                    for try await line in bytes.lines {
                        if Task.isCancelled { break }
                        if line.isEmpty { continue }
                        state.lastEventAt = Date()
                        state.consecutiveIdleProbes = 0
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
                        }
                    }
                    probeTask.cancel()
                } catch {
                    // Tunnels drop idle connections routinely; reconnect quietly.
                }

                if Task.isCancelled { break }
                try? await Task.sleep(nanoseconds: backoff * 1_000_000_000)
                backoff = min(backoff * 2, maxBackoff)
            }
        }
    }

    func stop() {
        task?.cancel()
        task = nil
    }

    private static func ssePath(replayJobId: String?) -> String {
        guard let id = replayJobId, !id.isEmpty else { return "/api/events" }
        let encoded = id.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? id
        return "/api/events?since=\(encoded)"
    }
}
