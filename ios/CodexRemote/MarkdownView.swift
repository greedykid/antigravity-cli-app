import SwiftUI
import UIKit

/// Block-aware Markdown renderer.
///
/// SwiftUI's `AttributedString(markdown:)` handles inline styling but flattens
/// everything else, so the text is split into blocks first — fenced code,
/// headings, quotes, lists, rules — and each becomes its own view, the same
/// approach the Android renderer takes.
struct MarkdownView: View {
    let markdown: String
    let palette: Palette

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(Array(MarkdownBlock.parse(markdown).enumerated()), id: \.offset) { _, block in
                view(for: block)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder
    private func view(for block: MarkdownBlock) -> some View {
        switch block {
        case .paragraph(let text):
            inline(text)
                .font(.system(size: 15))
                .foregroundColor(palette.textMain)
                .textSelection(.enabled)

        case .heading(let level, let text):
            inline(text)
                .font(.system(size: level <= 1 ? 21 : (level == 2 ? 18 : 16),
                              weight: .bold,
                              design: level <= 2 ? palette.headingFont : .default))
                .foregroundColor(level >= 4 ? palette.textMuted : palette.textMain)
                .padding(.top, 6)

        case .rule:
            Rectangle()
                .fill(palette.border)
                .frame(height: 1)
                .padding(.vertical, 4)

        case .quote(let text):
            HStack(alignment: .top, spacing: 0) {
                Rectangle().fill(palette.accent).frame(width: 3)
                inline(text)
                    .font(.system(size: 14.5, design: palette.headingFont))
                    .italic()
                    .foregroundColor(palette.textMuted)
                    .padding(12)
            }
            .background(palette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 10))

        case .list(let items):
            VStack(alignment: .leading, spacing: 6) {
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    HStack(alignment: .top, spacing: 10) {
                        Text(item.marker)
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(item.checked == true ? palette.green : palette.accent)
                            .frame(minWidth: 18, alignment: .leading)
                        inline(item.text)
                            .font(.system(size: 15))
                            .foregroundColor(item.checked == true ? palette.textMuted : palette.textMain)
                    }
                    .padding(.leading, CGFloat(item.depth) * 16)
                }
            }

        case .code(let language, let body):
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(language.isEmpty ? "CODE" : language.uppercased())
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(palette.accent)
                        .padding(.horizontal, 7).padding(.vertical, 3)
                        .background(palette.accentSoft)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                    Spacer()
                    Button {
                        UIPasteboard.general.string = body
                    } label: {
                        Label("Salin", systemImage: "doc.on.doc")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(palette.textMuted)
                    }
                }
                .padding(.horizontal, 12).padding(.vertical, 8)

                Divider().background(palette.border)

                // Long lines scroll sideways instead of wrapping into soup.
                ScrollView(.horizontal, showsIndicators: false) {
                    Text(body)
                        .font(.system(size: 12.5, design: .monospaced))
                        .foregroundColor(Color(red: 0.94, green: 0.94, blue: 0.96))
                        .textSelection(.enabled)
                        .padding(12)
                }
            }
            .background(palette.codeBackground)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(palette.border, lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }

    private func inline(_ text: String) -> Text {
        if let attributed = try? AttributedString(
            markdown: text,
            options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)) {
            return Text(attributed)
        }
        return Text(text)
    }
}

// MARK: - block parser

struct MarkdownListItem {
    let marker: String
    let text: String
    let depth: Int
    let checked: Bool?
}

enum MarkdownBlock {
    case paragraph(String)
    case heading(Int, String)
    case quote(String)
    case list([MarkdownListItem])
    case code(String, String)
    case rule

    static func parse(_ markdown: String) -> [MarkdownBlock] {
        var blocks: [MarkdownBlock] = []
        let sections = markdown.components(separatedBy: "```")

        for (index, section) in sections.enumerated() {
            if index % 2 == 1 {
                var language = ""
                var body = section
                if let newline = section.firstIndex(of: "\n") {
                    let head = String(section[section.startIndex..<newline]).trimmingCharacters(in: .whitespaces)
                    if !head.contains(" ") && head.count < 20 {
                        language = head
                        body = String(section[section.index(after: newline)...])
                    }
                }
                blocks.append(.code(language, body.trimmingCharacters(in: .newlines)))
            } else {
                blocks.append(contentsOf: parseText(section))
            }
        }
        return blocks
    }

    private static func parseText(_ text: String) -> [MarkdownBlock] {
        var blocks: [MarkdownBlock] = []
        var paragraph: [String] = []
        var quote: [String] = []
        var items: [MarkdownListItem] = []

        func flushParagraph() {
            let joined = paragraph.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
            if !joined.isEmpty { blocks.append(.paragraph(joined)) }
            paragraph = []
        }
        func flushQuote() {
            let joined = quote.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
            if !joined.isEmpty { blocks.append(.quote(joined)) }
            quote = []
        }
        func flushList() {
            if !items.isEmpty { blocks.append(.list(items)); items = [] }
        }

        for rawLine in text.components(separatedBy: "\n") {
            let line = rawLine
            let trimmed = line.trimmingCharacters(in: .whitespaces)

            if trimmed.range(of: "^(-{3,}|\\*{3,}|_{3,})$", options: .regularExpression) != nil {
                flushParagraph(); flushQuote(); flushList()
                blocks.append(.rule)
                continue
            }
            if trimmed.hasPrefix("#") {
                let hashes = trimmed.prefix(while: { $0 == "#" }).count
                let body = trimmed.dropFirst(hashes).trimmingCharacters(in: .whitespaces)
                if hashes <= 6 && !body.isEmpty {
                    flushParagraph(); flushQuote(); flushList()
                    blocks.append(.heading(hashes, body))
                    continue
                }
            }
            if trimmed.hasPrefix(">") {
                flushParagraph(); flushList()
                quote.append(String(trimmed.dropFirst()).trimmingCharacters(in: .whitespaces))
                continue
            }
            if let item = listItem(from: line) {
                flushParagraph(); flushQuote()
                items.append(item)
                continue
            }
            if trimmed.isEmpty {
                flushParagraph(); flushQuote(); flushList()
                continue
            }
            flushQuote(); flushList()
            paragraph.append(line)
        }

        flushParagraph(); flushQuote(); flushList()
        return blocks
    }

    private static func listItem(from line: String) -> MarkdownListItem? {
        let indent = line.prefix(while: { $0 == " " || $0 == "\t" }).count
        let trimmed = line.trimmingCharacters(in: .whitespaces)
        let depth = min(3, indent / 2)

        var content: String?
        var marker = "•"

        if let match = trimmed.range(of: "^([-*+])\\s+", options: .regularExpression) {
            content = String(trimmed[match.upperBound...])
            marker = depth == 0 ? "•" : "◦"
        } else if let match = trimmed.range(of: "^(\\d{1,3})[.)]\\s+", options: .regularExpression) {
            let number = trimmed[trimmed.startIndex..<match.upperBound]
                .trimmingCharacters(in: CharacterSet(charactersIn: " .)"))
            content = String(trimmed[match.upperBound...])
            marker = number + "."
        }

        guard var body = content else { return nil }

        var checked: Bool?
        if body.hasPrefix("[x] ") || body.hasPrefix("[X] ") {
            checked = true; marker = "☑"; body = String(body.dropFirst(4))
        } else if body.hasPrefix("[ ] ") {
            checked = false; marker = "☐"; body = String(body.dropFirst(4))
        }

        return MarkdownListItem(marker: marker, text: body, depth: depth, checked: checked)
    }
}
