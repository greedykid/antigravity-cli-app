import SwiftUI
import UIKit
import PhotosUI

struct ChatView: View {
    @EnvironmentObject private var state: AppState
    @State private var draft = ""
    @FocusState private var inputFocused: Bool
    @State private var photoItem: PhotosPickerItem?
    @State private var attachedImage: UIImage?
    @State private var attachedData: Data?

    private var palette: Palette { state.palette }

    var body: some View {
        VStack(spacing: 0) {
            transcript
            composer
        }
        .background(palette.background.ignoresSafeArea())
        .onChange(of: photoItem) { _ in loadAttachedPhoto() }
    }

    private func loadAttachedPhoto() {
        guard let photoItem else { return }
        Task {
            if let data = try? await photoItem.loadTransferable(type: Data.self),
               let uiImage = UIImage(data: data) {
                await MainActor.run {
                    attachedData = data
                    attachedImage = uiImage
                }
            }
        }
    }

    private var transcript: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 14) {
                    if state.turns.isEmpty && state.pendingPrompt == nil && !state.isRunning {
                        emptyState.padding(.top, 60)
                    }

                    ForEach(state.turns) { turn in
                        row(for: turn)
                    }

                    // The optimistic bubble stays until the run ends, so a
                    // partial transcript cannot make the sent message vanish.
                    if let pending = state.pendingPrompt,
                       !state.turns.contains(where: { $0.isUser && $0.text == pending }) {
                        userBubble(pending)
                    }

                    if state.isRunning && !state.streamingResponse.isEmpty {
                        MarkdownView(markdown: state.streamingResponse + " ▊", palette: palette)
                    }

                    if let failed = state.failedPrompt {
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(palette.amber)
                            Text("Gagal terkirim: \(failed)")
                                .font(.system(size: 13))
                                .foregroundColor(palette.textMain)
                                .lineLimit(1)
                            Spacer()
                            Button("Kirim Ulang 🔄") {
                                let p = failed
                                state.failedPrompt = nil
                                Task { await state.send(prompt: p) }
                            }
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(palette.accent)
                        }
                        .padding(12)
                        .background(palette.surface)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(palette.red, lineWidth: 1))
                    }

                    if state.isRunning && state.streamingResponse.isEmpty {
                        runningIndicator.id("running")
                    }
                }
                .padding(16)
            }
            .onChange(of: state.turns.count) { _ in
                withAnimation { proxy.scrollTo("running", anchor: .bottom) }
            }
        }
    }

    @ViewBuilder
    private func row(for turn: Turn) -> some View {
        if turn.isUser {
            userBubble(turn.text)
        } else if turn.isStep {
            stepPill(turn)
        } else if !turn.text.isEmpty {
            MarkdownView(markdown: turn.text, palette: palette)
        }
    }

    private func userBubble(_ text: String) -> some View {
        HStack {
            Spacer(minLength: 40)
            Text(text)
                .font(.system(size: 15))
                .foregroundColor(palette.textMain)
                .padding(12)
                .background(palette.surfaceMuted)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .textSelection(.enabled)
        }
    }

    private func stepPill(_ turn: Turn) -> some View {
        DisclosureGroup {
            ScrollView(.horizontal, showsIndicators: false) {
                Text(turn.text)
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundColor(palette.textMuted)
                    .textSelection(.enabled)
                    .padding(.top, 6)
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: turn.role == "thinking" ? "brain" : "terminal")
                    .foregroundColor(palette.accent)
                Text(turn.stepLabel)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(palette.textMuted)
                    .lineLimit(1)
            }
        }
        .padding(12)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(palette.border, lineWidth: 1))
        .tint(palette.textMuted)
    }

    private var runningIndicator: some View {
        HStack(spacing: 10) {
            ProgressView().tint(palette.accent)
            Text("(state.engine.label) sedang bekerja...")
                .font(.system(size: 13))
                .foregroundColor(palette.textMuted)
            Spacer()
            Button("Hentikan") { Task { await state.interrupt() } }
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(palette.red)
        }
        .padding(12)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var emptyState: some View {
        VStack(spacing: 14) {
            // The product logo already spells out the app name, so the separate
            // title line underneath it is gone.
            Image("BrandLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 150)
            Text(state.engine.tagline)
                .font(.system(size: 13.5))
                .foregroundColor(palette.textMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private var quickToolbar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                // Prompts
                quickChip(icon: "wrench.and.screwdriver.fill", title: "Perbaiki Error", insert: "Tolong perbaiki error berikut: ")
                quickChip(icon: "doc.text.magnifyingglass", title: "Review Kode", insert: "Tolong review dan periksa kode ini untuk potensi bug atau peningkatan: ")
                quickChip(icon: "play.circle.fill", title: "Jalankan Test", insert: "Jalankan test suite dan laporkan hasilnya.")
                quickChip(icon: "text.book.closed.fill", title: "Jelaskan Alur", insert: "Jelaskan alur kerja kode ini secara ringkas.")
                quickChip(icon: "shippingbox.fill", title: "Git Status", insert: "Cek git status dan rangkum perubahan.")
                quickChip(icon: "arrow.triangle.branch", title: "Git Diff", insert: "Tampilkan git diff dari perubahan terbaru.")
                quickChip(icon: "square.and.pencil", title: "Buat Commit", insert: "Buat commit git dengan pesan yang jelas untuk perubahan saat ini.")

                // Symbols
                symbolChip("```", snippet: "```\n\n```")
                symbolChip("{ }", snippet: "{  }")
                symbolChip("[ ]", snippet: "[  ]")
                symbolChip("/* */", snippet: "/*  */")
                symbolChip("->", snippet: "-> ")
                symbolChip("$", snippet: "$ ")
                symbolChip("/", snippet: "/")
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 4)
        }
    }

    private func quickChip(icon: String, title: String, insert: String) -> some View {
        Button {
            if draft.isEmpty {
                draft = insert
            } else {
                draft += "\n" + insert
            }
            inputFocused = true
        } label: {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(palette.accent)
                Text(title)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(palette.textMain)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(palette.surface)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(palette.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private func symbolChip(_ title: String, snippet: String) -> some View {
        Button {
            draft += snippet
            inputFocused = true
        } label: {
            Text(title)
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundColor(palette.accent)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(palette.surfaceMuted)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(palette.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var slashCommandsView: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                slashChip("/diff", title: "Git Diff", insert: "Tampilkan git diff dari perubahan terbaru di repository ini.")
                slashChip("/test", title: "Run Tests", insert: "Jalankan semua test suite di project dan laporkan hasilnya.")
                slashChip("/commit", title: "Git Commit", insert: "Buat commit git dengan deskripsi ringkas dan rapi untuk perubahan saat ini.")
                slashChip("/review", title: "Code Review", insert: "Tolong review kode terbaru di workspace ini, periksa potensi bug, performa, dan keamanan.")
                slashChip("/explain", title: "Jelaskan Alur", insert: "Jelaskan arsitektur dan alur kerja utama dari codebase project ini secara ringkas.")
                slashChip("/status", title: "Git Status", insert: "Periksa git status dan rangkum file apa saja yang diubah atau belum di-stage.")
                slashChip("/fix", title: "Perbaiki Error", insert: "Tolong perbaiki bug atau error berikut pada project ini: ")
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 4)
        }
    }

    private func slashChip(_ cmd: String, title: String, insert: String) -> some View {
        Button {
            draft = insert
            inputFocused = true
        } label: {
            HStack(spacing: 4) {
                Text(cmd)
                    .font(.system(size: 12, weight: .bold, design: .monospaced))
                    .foregroundColor(palette.accent)
                Text("• \(title)")
                    .font(.system(size: 11.5))
                    .foregroundColor(palette.textMuted)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(palette.surface)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(palette.accent, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var attachmentTray: some View {
        Group {
            if let image = attachedImage {
                HStack(spacing: 8) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 44, height: 44)
                        .clipShape(RoundedRectangle(cornerRadius: 8))

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Gambar Terlampir")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(palette.textMain)
                        Text("Siap dikirim ke AI")
                            .font(.system(size: 11))
                            .foregroundColor(palette.textMuted)
                    }
                    Spacer()
                    Button {
                        attachedImage = nil
                        attachedData = nil
                        photoItem = nil
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 18))
                            .foregroundColor(palette.textMuted)
                    }
                }
                .padding(8)
                .background(palette.surface)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(RoundedRectangle(cornerRadius: 12).stroke(palette.border, lineWidth: 1))
                .padding(.horizontal, 12)
            }
        }
    }

    private var composer: some View {
        VStack(spacing: 6) {
            if let error = state.errorMessage {
                Text(error)
                    .font(.system(size: 12.5))
                    .foregroundColor(palette.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
            }

            attachmentTray
            if draft.hasPrefix("/") {
                slashCommandsView
            }
            quickToolbar

            HStack(alignment: .bottom, spacing: 8) {
                PhotosPicker(selection: $photoItem, matching: .images) {
                    Image(systemName: "paperclip")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(attachedImage != nil ? palette.accent : palette.textMuted)
                        .frame(width: 40, height: 40)
                        .background(palette.surface)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(palette.border, lineWidth: 1))
                }

                TextField("", text: $draft, prompt: Text("Ketik perintah...").foregroundColor(palette.textLight), axis: .vertical)
                    .lineLimit(1...5)
                    .focused($inputFocused)
                    .foregroundColor(palette.textMain)
                    .padding(12)
                    .background(palette.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(palette.border, lineWidth: 1))

                Button {
                    let text = draft
                    let dataToSend = attachedData
                    draft = ""
                    attachedImage = nil
                    attachedData = nil
                    photoItem = nil
                    inputFocused = false
                    Task {
                        var promptToSend = text
                        if let data = dataToSend,
                           let uploadedPath = await state.uploadImage(data: data, filename: "ios_upload_\(Int(Date().timeIntervalSince1970)).jpg") {
                            promptToSend = "[Attached File: \(uploadedPath)]\n" + text
                        }
                        await state.send(prompt: promptToSend)
                    }
                } label: {
                    Image(systemName: "arrow.up")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(palette.onAccent)
                        .frame(width: 44, height: 44)
                        .background(palette.accent)
                        .clipShape(Circle())
                }
                .disabled(state.isRunning || (draft.trimmingCharacters(in: .whitespaces).isEmpty && attachedData == nil))
                .opacity(state.isRunning ? 0.5 : 1)
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 8)
        }
        .background(palette.background)
    }
}

/// The floating droid from the Android empty state, drawn with shapes so it
/// picks up whichever engine palette is active.
struct MascotView: View {
    let palette: Palette
    @State private var floating = false

    var body: some View {
        ZStack {
            Circle().fill(palette.accentSoft).opacity(0.55)

            VStack(spacing: 6) {
                Circle().fill(palette.accent).frame(width: 10, height: 10)
                Rectangle().fill(palette.accent).frame(width: 3, height: 10)

                ZStack {
                    RoundedRectangle(cornerRadius: 22)
                        .fill(palette.accentSoft)
                        .overlay(RoundedRectangle(cornerRadius: 22).stroke(palette.accent, lineWidth: 2.5))
                        .frame(width: 92, height: 60)

                    RoundedRectangle(cornerRadius: 14)
                        .fill(palette.codeBackground)
                        .frame(width: 66, height: 34)

                    HStack(spacing: 14) {
                        Circle().fill(palette.accent.opacity(0.85)).frame(width: 9, height: 9)
                        Circle().fill(palette.accent.opacity(0.85)).frame(width: 9, height: 9)
                    }
                }

                ZStack {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(palette.surfaceMuted)
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(palette.accent, lineWidth: 2.5))
                        .frame(width: 72, height: 40)
                    Circle().fill(palette.accent).frame(width: 16, height: 16)
                }
            }
            .offset(y: floating ? -5 : 5)
            .animation(.easeInOut(duration: 1.7).repeatForever(autoreverses: true), value: floating)
        }
        .onAppear { floating = true }
    }
}
