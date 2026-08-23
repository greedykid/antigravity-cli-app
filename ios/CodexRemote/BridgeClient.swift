import Foundation

/// HTTP layer for the bridge server.
///
/// The paired endpoint is stored as the `/api/chat` URL, so every other route
/// is derived from it in one place rather than string-replaced at each call.
struct BridgeClient {
    var endpoint: String
    var token: String

    var isPaired: Bool { !endpoint.isEmpty }

    func url(_ apiPath: String) -> URL? {
        guard !endpoint.isEmpty else { return nil }
        var root = endpoint
        if let range = root.range(of: "/api/") {
            root = String(root[root.startIndex..<range.lowerBound])
        }
        while root.hasSuffix("/") { root.removeLast() }
        let path = apiPath.hasPrefix("/") ? apiPath : "/" + apiPath
        return URL(string: root + path)
    }

    static func encode(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? ""
    }

    func request(_ apiPath: String, method: String = "GET", timeout: TimeInterval = 20) -> URLRequest? {
        guard let target = url(apiPath) else { return nil }
        var req = URLRequest(url: target)
        req.httpMethod = method
        req.timeoutInterval = timeout
        if !token.isEmpty {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        return req
    }

    enum BridgeError: LocalizedError {
        case notPaired
        case unauthorized
        case http(Int)
        case badResponse

        var errorDescription: String? {
            switch self {
            case .notPaired: return "Belum terhubung ke server"
            case .unauthorized: return "Token ditolak server. Scan ulang QR pairing."
            case .http(let code): return "Server membalas HTTP \(code)"
            case .badResponse: return "Balasan server tidak bisa dibaca"
            }
        }
    }

    private func send(_ req: URLRequest) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: req)
        guard let http = response as? HTTPURLResponse else { throw BridgeError.badResponse }
        if http.statusCode == 401 || http.statusCode == 403 { throw BridgeError.unauthorized }
        guard (200..<300).contains(http.statusCode) else { throw BridgeError.http(http.statusCode) }
        return data
    }

    func get<T: Decodable>(_ apiPath: String, as type: T.Type, timeout: TimeInterval = 20) async throws -> T {
        guard let req = request(apiPath, timeout: timeout) else { throw BridgeError.notPaired }
        let data = try await send(req)
        return try JSONDecoder().decode(T.self, from: data)
    }

    func post<T: Decodable>(_ apiPath: String, body: [String: Any],
                            as type: T.Type, timeout: TimeInterval = 60) async throws -> T {
        guard var req = request(apiPath, method: "POST", timeout: timeout) else { throw BridgeError.notPaired }
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let data = try await send(req)
        return try JSONDecoder().decode(T.self, from: data)
    }

    /// Health needs no token, so it says nothing about whether pairing is valid.
    /// Probing an authenticated route as well is what distinguishes "server up"
    /// from "server up but this phone's token was replaced".
    func checkConnection() async -> String {
        guard isPaired else { return "Belum dipasangkan" }
        do {
            _ = try await get("/health", as: HealthResponse.self, timeout: 10)
        } catch {
            return "Gateway offline"
        }
        do {
            _ = try await get("/api/sessions", as: SessionsResponse.self, timeout: 12)
            return "Gateway online"
        } catch BridgeError.unauthorized {
            return "Token ditolak"
        } catch {
            return "Gateway online"
        }
    }
}

struct HealthResponse: Codable {
    let ok: Bool?
    let hostname: String?
    let features: [String]?
}
