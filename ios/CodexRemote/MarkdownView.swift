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

        case .table(let headers, let alignments, let rows):
            MarkdownTableView(headers: headers,
                              alignments: alignments,
                              rows: rows,
                              palette: palette)
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
    case table(headers: [String], alignments: [MarkdownTableAlignment], rows: [[String]])

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
        var tableBuffer: [String] = []

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
        func flushTable() {
            if let block = parseTable(tableBuffer) {
                blocks.append(block)
            } else {
                // Not a real table — emit the lines as a paragraph so we
                // do not silently swallow the user's content.
                for line in tableBuffer { paragraph.append(line) }
            }
            tableBuffer = []
        }

        let lines = text.components(separatedBy: "\n")
        var idx = 0
        while idx < lines.count {
            let line = lines[idx]
            let trimmed = line.trimmingCharacters(in: .whitespaces)

            if trimmed.range(of: "^(-{3,}|\\*{3,}|_{3,})$", options: .regularExpression) != nil {
                flushParagraph(); flushQuote(); flushList(); flushTable()
                blocks.append(.rule)
                idx += 1
                continue
            }
            if trimmed.hasPrefix("#") {
                let hashes = trimmed.prefix(while: { $0 == "#" }).count
                let body = trimmed.dropFirst(hashes).trimmingCharacters(in: .whitespaces)
                if hashes <= 6 && !body.isEmpty {
                    flushParagraph(); flushQuote(); flushList(); flushTable()
                    blocks.append(.heading(hashes, body))
                    idx += 1
                    continue
                }
            }
            if trimmed.hasPrefix(">") {
                flushParagraph(); flushList(); flushTable()
                quote.append(String(trimmed.dropFirst()).trimmingCharacters(in: .whitespaces))
                idx += 1
                continue
            }
            if let item = listItem(from: line) {
                flushParagraph(); flushQuote(); flushTable()
                items.append(item)
                idx += 1
                continue
            }

            // GFM table detection: a header line that starts with `|` (or
            // whose first cell starts with `|`), followed by a separator
            // row of `| --- | --- |`. We accumulate contiguous candidate
            // lines and validate at flush time.
            if isTableCandidate(trimmed) {
                tableBuffer.append(line)
                idx += 1
                continue
            }

            if trimmed.isEmpty {
                flushParagraph(); flushQuote(); flushList(); flushTable()
                idx += 1
                continue
            }

            flushQuote(); flushList(); flushTable()
            paragraph.append(line)
            idx += 1
        }

        flushParagraph(); flushQuote(); flushList(); flushTable()
        return blocks
    }

    private static func isTableCandidate(_ line: String) -> Bool {
        // Cheap guard before we commit to buffering. The first cell can be
        // an inline-code span or contain a backtick; we accept any line
        // whose outer shape looks like a pipe-delimited row.
        let t = line.trimmingCharacters(in: .whitespaces)
        guard t.count >= 3 else { return false }
        if !t.hasPrefix("|") && !t.contains(" |") { return false }
        // Reject obvious non-tables: lines that are not consistently
        // pipe-delimited (more than one segment without a `|` boundary).
        let pipeCount = t.filter { $0 == "|" }.count
        return pipeCount >= 2
    }

    private static func parseTable(_ lines: [String]) -> MarkdownBlock? {
        guard lines.count >= 2 else { return nil }

        func split(_ raw: String) -> [String] {
            var t = raw.trimmingCharacters(in: .whitespaces)
            if t.hasPrefix("|") { t.removeFirst() }
            if t.hasSuffix("|") { t.removeLast() }
            return t.components(separatedBy: "|").map { $0.trimmingCharacters(in: .whitespaces) }
        }

        func isSeparator(_ raw: String) -> Bool {
            let stripped = raw.replacingOccurrences(of: "|", with: "")
                .replacingOccurrences(of: "-", with: "")
                .replacingOccurrences(of: ":", with: "")
                .replacingOccurrences(of: " ", with: "")
                .replacingOccurrences(of: "\t", with: "")
            return !stripped.isEmpty ? false : raw.contains("-")
        }

        // The first line is the header, the second must be a separator.
        guard isSeparator(lines[1]) else { return nil }

        let headers = split(lines[0])
        let colCount = headers.count
        if colCount == 0 { return nil }

        // Per-column alignment from the separator.
        var alignments: [MarkdownTableAlignment] = []
        alignments.reserveCapacity(colCount)
        for cell in split(lines[1]) {
            let left = cell.hasPrefix(":")
            let right = cell.hasSuffix(":")
            if left && right {
                alignments.append(.center)
            } else if right {
                alignments.append(.trailing)
            } else {
                alignments.append(.leading)
            }
        }
        // Pad/trim the alignment array to match header length.
        while alignments.count < colCount { alignments.append(.leading) }
        if alignments.count > colCount { alignments = Array(alignments.prefix(colCount)) }

        // Remaining lines are data rows; ignore any further separators.
        var rows: [[String]] = []
        for raw in lines.dropFirst(2) {
            if isSeparator(raw) { continue }
            let cells = split(raw)
            var padded = cells
            while padded.count < colCount { padded.append("") }
            if padded.count > colCount { padded = Array(padded.prefix(colCount)) }
            rows.append(padded)
        }

        return .table(headers: headers, alignments: alignments, rows: rows)
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

enum MarkdownTableAlignment {
    case leading, center, trailing
}

// MARK: - table view

/// Renders a GFM table with per-column widths measured in a first pass so
/// the columns line up under the header. Falls back to a horizontal scroll
/// view when the table is wider than the parent chat bubble.
struct MarkdownTableView: View {
    let headers: [String]
    let alignments: [MarkdownTableAlignment]
    let rows: [[String]]
    let palette: Palette

    // Hard caps so a single very long cell (path, URL, JSON blob) does not
    // blow out the row width. The scroll view handles anything beyond.
    private let columnMaxWidth: CGFloat = 240
    private let columnMinWidth: CGFloat = 56

    private struct Measurement {
        let width: CGFloat
    }

    var body: some View {
        let columnWidths = computeColumnWidths()
        let totalWidth = columnWidths.reduce(0, +)
        let needsScroll = totalWidth > availableWidth

        let grid = tableGrid(columnWidths: columnWidths)

        return Group {
            if needsScroll {
                ScrollView(.horizontal, showsIndicators: false) {
                    grid
                }
            } else {
                grid
            }
        }
        .background(palette.surface)
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(palette.border, lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }

    @State private var containerWidth: CGFloat = 320

    private var availableWidth: CGFloat { max(containerWidth, 160) }

    private func tableGrid(columnWidths: [CGFloat]) -> some View {
        let headerCells = headers.enumerated().map { idx, text -> AnyView in
            let width = columnWidths[idx]
            return AnyView(
                Text(text.uppercased())
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(palette.textMuted)
                    .multilineTextAlignment(textAlignment(for: idx))
                    .frame(width: width, alignment: textAlignment(for: idx))
                    .padding(.horizontal, 10).padding(.vertical, 10)
            )
        }

        let rowViews = rows.enumerated().map { rowIdx, row -> AnyView in
            let isAlt = rowIdx % 2 == 1
            let background = isAlt ? palette.surface.opacity(0.5) : Color.clear
            return AnyView(
                VStack(spacing: 0) {
                    HStack(alignment: .center, spacing: 0) {
                        ForEach(Array(row.enumerated()), id: \.offset) { colIdx, cell in
                            cellView(cell, alignment: textAlignment(for: colIdx),
                                     width: columnWidths[colIdx], isHeader: false)
                        }
                    }
                    .background(background)
                    if rowIdx < rows.count - 1 {
                        Rectangle().fill(palette.border).frame(height: 1)
                    }
                }
            )
        }

        return VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: 0) {
                ForEach(Array(headerCells.enumerated()), id: \.offset) { _, cell in cell }
            }
            .background(palette.surfaceMuted)
            Rectangle().fill(palette.borderStrong).frame(height: 1)
            ForEach(Array(rowViews.enumerated()), id: \.offset) { _, row in row }
        }
        .background(
            GeometryReader { proxy in
                Color.clear.onAppear { containerWidth = proxy.size.width }
                    .onChange(of: proxy.size.width) { _, newValue in containerWidth = newValue }
            }
        )
    }

    private func cellView(_ raw: String, alignment: TextAlignment, width: CGFloat, isHeader: Bool) -> some View {
        Group {
            if let attributed = try? AttributedString(
                markdown: raw,
                options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)) {
                Text(attributed)
            } else {
                Text(raw)
            }
        }
        .font(.system(size: 13))
        .foregroundColor(palette.textMain)
        .multilineTextAlignment(alignment)
        .frame(width: width, alignment: textFrameAlignment(for: alignment))
        .padding(.horizontal, 10).padding(.vertical, 10)
    }

    private func textAlignment(for column: Int) -> TextAlignment {
        switch alignments[safe: column] ?? .leading {
        case .leading:  return .leading
        case .center:   return .center
        case .trailing: return .trailing
        }
    }

    private func textFrameAlignment(for alignment: TextAlignment) -> Alignment {
        switch alignment {
        case .leading:  return .leading
        case .center:   return .center
        case .trailing: return .trailing
        }
    }

    /// Measures each header and body cell with a hidden Text view and picks
    /// the widest value per column. Capped at columnMinWidth / columnMaxWidth.
    private func computeColumnWidths() -> [CGFloat] {
        let colCount = headers.count
        guard colCount > 0 else { return [] }
        var widths = Array(repeating: columnMinWidth, count: colCount)

        for idx in 0..<colCount {
            let headerWidth = measureText(headers[idx],
                                          font: .system(size: 11, weight: .bold),
                                          horizontalPadding: 20)
            widths[idx] = max(widths[idx], headerWidth + 20)
        }
        for row in rows {
            for idx in 0..<colCount {
                let raw = idx < row.count ? row[idx] : ""
                let w = measureText(raw, font: .system(size: 13), horizontalPadding: 20)
                widths[idx] = max(widths[idx], w + 20)
            }
        }
        return widths.map { min(columnMaxWidth, max(columnMinWidth, $0)) }
    }

    private func measureText(_ text: String, font: Font, horizontalPadding: CGFloat) -> CGFloat {
        let attributed: AttributedString
        if let parsed = try? AttributedString(
            markdown: text,
            options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)) {
            attributed = parsed
        } else {
            attributed = AttributedString(text)
        }
        let uiFont: UIFont
        switch font {
        case .system(size: 11, weight: .bold):
            uiFont = .systemFont(ofSize: 11, weight: .bold)
        case .system(size: 13):
            uiFont = .systemFont(ofSize: 13)
        default:
            uiFont = .systemFont(ofSize: 13)
        }
        let bounding = (text as NSString).boundingRect(
            with: CGSize(width: .greatestFiniteMagnitude, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: [.font: uiFont],
            context: nil
        )
        _ = attributed // keep compiler from eliding the variable
        return ceil(bounding.width) + horizontalPadding
    }
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
