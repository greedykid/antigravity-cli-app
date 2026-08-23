import SwiftUI
import UIKit

struct ChatView: View {
    @EnvironmentObject private var state: AppState
    @State private var draft = ""
    @FocusState private var inputFocused: Bool

    private var palette: Palette { state.palette }

    var body: some View {
        VStack(spacing: 0) {
            transcript
            composer
        }
        .background(palette.background.ignoresSafeArea())
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

                    if state.isRunning {
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
            Text("\(state.engine.label) sedang bekerja...")
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
            MascotView(palette: palette).frame(width: 140, height: 140)
            Text(state.engine.brandTitle)
                .font(.system(size: 19, weight: .bold, design: palette.headingFont))
                .foregroundColor(palette.textMain)
            Text(state.engine.tagline)
                .font(.system(size: 13.5))
                .foregroundColor(palette.textMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    private var composer: some View {
        VStack(spacing: 8) {
            if let error = state.errorMessage {
                Text(error)
                    .font(.system(size: 12.5))
                    .foregroundColor(palette.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            HStack(spacing: 10) {
                Text(state.engine.short)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(palette.accent)
                    .padding(.horizontal, 10).padding(.vertical, 4)
                    .background(palette.accentSoft)
                    .clipShape(Capsule())

                Text(state.model)
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundColor(palette.textMuted)
                    .lineLimit(1)
                Spacer()
            }

            HStack(alignment: .bottom, spacing: 10) {
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
                    draft = ""
                    inputFocused = false
                    Task { await state.send(prompt: text) }
                } label: {
                    Image(systemName: "arrow.up")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(palette.onAccent)
                        .frame(width: 44, height: 44)
                        .background(palette.accent)
                        .clipShape(Circle())
                }
                .disabled(state.isRunning || draft.trimmingCharacters(in: .whitespaces).isEmpty)
                .opacity(state.isRunning ? 0.5 : 1)
            }
        }
        .padding(12)
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
