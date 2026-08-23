import SwiftUI

/// Palette and identity for the active engine.
///
/// Mirrors the Android `Theme`: Antigravity keeps the warm terracotta ground,
/// Codex gets a cooler slate one with a teal accent, so the screen says which
/// CLI will run the next prompt without reading a label.
enum Engine: String, CaseIterable, Codable {
    case antigravity
    case codex

    var label: String { self == .codex ? "Codex CLI" : "Antigravity CLI" }
    var short: String { self == .codex ? "Codex" : "Agy" }
    var wordmark: String { self == .codex ? "Codex" : "Antigravity" }
    var brandTitle: String { self == .codex ? "Codex Remote" : "Antigravity Code" }
    var repo: String { self == .codex ? "openai/codex-cli" : "google/antigravity-cli" }

    var tagline: String {
        self == .codex
            ? "Siap membantu. Ketik perintah untuk memulai sesi Codex."
            : "Siap membantu. Ketik perintah untuk memulai sesi Antigravity."
    }

    var defaultModel: String { self == .codex ? "gpt-5.6-luna" : "auto" }

    var models: [String] {
        self == .codex
            ? ["gpt-5.6-luna", "gpt-5.6-sol", "default"]
            : ["auto", "gemini-3.7-flash-high", "gemini-3.7-flash-medium",
               "gemini-3.1-pro-high", "claude-sonnet-4-6", "gpt-oss-120b-medium"]
    }
}

struct Palette {
    let background: Color
    let surface: Color
    let surfaceMuted: Color
    let border: Color
    let borderStrong: Color
    let codeBackground: Color
    let textMain: Color
    let textMuted: Color
    let textLight: Color
    let accent: Color
    let accentSoft: Color
    let onAccent: Color
    let green: Color
    let amber: Color
    let red: Color
    let blue: Color
    let headingFont: Font.Design

    static func rgb(_ r: Double, _ g: Double, _ b: Double) -> Color {
        Color(red: r / 255, green: g / 255, blue: b / 255)
    }

    static let antigravity = Palette(
        background: rgb(24, 24, 23),
        surface: rgb(33, 32, 30),
        surfaceMuted: rgb(42, 41, 38),
        border: rgb(48, 46, 43),
        borderStrong: rgb(62, 60, 56),
        codeBackground: rgb(18, 18, 18),
        textMain: rgb(237, 236, 232),
        textMuted: rgb(158, 157, 153),
        textLight: rgb(112, 111, 108),
        accent: rgb(217, 107, 67),
        accentSoft: rgb(56, 36, 29),
        onAccent: .white,
        green: rgb(76, 175, 80),
        amber: rgb(245, 158, 11),
        red: rgb(239, 68, 68),
        blue: rgb(59, 130, 246),
        headingFont: .serif
    )

    static let codex = Palette(
        background: rgb(19, 20, 23),
        surface: rgb(27, 29, 33),
        surfaceMuted: rgb(36, 39, 44),
        border: rgb(46, 50, 58),
        borderStrong: rgb(59, 64, 73),
        codeBackground: rgb(14, 16, 19),
        textMain: rgb(231, 234, 238),
        textMuted: rgb(152, 160, 172),
        textLight: rgb(107, 114, 128),
        accent: rgb(16, 163, 127),
        accentSoft: rgb(14, 42, 36),
        // White reads at only 3.2:1 on this green; a near-black label is 5.95:1.
        onAccent: rgb(8, 18, 15),
        green: rgb(52, 199, 137),
        amber: rgb(233, 165, 61),
        red: rgb(238, 92, 92),
        blue: rgb(90, 156, 248),
        headingFont: .default
    )

    static func of(_ engine: Engine) -> Palette {
        engine == .codex ? .codex : .antigravity
    }
}
