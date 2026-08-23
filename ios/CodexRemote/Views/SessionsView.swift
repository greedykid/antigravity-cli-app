import SwiftUI

struct SessionsView: View {
    @EnvironmentObject private var state: AppState
    var onOpen: () -> Void

    private var palette: Palette { state.palette }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                if state.loadingSessions && state.sessions.isEmpty {
                    ProgressView().tint(palette.accent).padding(.top, 40)
                } else if state.sessions.isEmpty {
                    emptyState.padding(.top, 40)
                }

                ForEach(state.sessions) { session in
                    Button {
                        Task {
                            await state.open(session: session)
                            onOpen()
                        }
                    } label: {
                        card(session)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(16)
        }
        .background(palette.background.ignoresSafeArea())
        .refreshable { await state.loadSessions() }
    }

    private func card(_ session: SessionSummary) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "chevron.left.forwardslash.chevron.right")
                .foregroundColor(palette.textMuted)
                .frame(width: 42, height: 42)
                .background(palette.surfaceMuted)
                .clipShape(RoundedRectangle(cornerRadius: 12))

            VStack(alignment: .leading, spacing: 3) {
                Text(session.title)
                    .font(.system(size: 14.5, weight: .semibold))
                    .foregroundColor(palette.textMain)
                    .lineLimit(1)
                Text(session.engineValue.label)
                    .font(.system(size: 12))
                    .foregroundColor(palette.green)
            }
            Spacer()
            Text(session.date, format: .dateTime.day().month())
                .font(.system(size: 12))
                .foregroundColor(palette.textMuted)
        }
        .padding(12)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(palette.border, lineWidth: 1))
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Image(systemName: "tray")
                .font(.system(size: 28))
                .foregroundColor(palette.textLight)
            // Naming the engine keeps an empty list from reading as lost history.
            Text("Belum ada sesi \(state.engine.label)")
                .font(.system(size: 14.5, weight: .semibold))
                .foregroundColor(palette.textMain)
            Text("Mulai percakapan baru dari tab Obrolan.")
                .font(.system(size: 13))
                .foregroundColor(palette.textMuted)
        }
    }
}
