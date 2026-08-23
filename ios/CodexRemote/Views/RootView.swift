import SwiftUI

struct RootView: View {
    @EnvironmentObject private var state: AppState
    @State private var tab = 0
    @State private var showPairing = false

    private var palette: Palette { state.palette }

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack {
                ChatView()
                    .navigationTitle(state.activeSessionTitle)
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar { chatToolbar }
            }
            .tabItem { Label("Obrolan", systemImage: "bubble.left.and.bubble.right") }
            .tag(0)

            NavigationStack {
                SessionsView { tab = 0 }
                    .navigationTitle("Kode · \(state.engine.short)")
                    .navigationBarTitleDisplayMode(.inline)
            }
            .tabItem { Label("Kode", systemImage: "chevron.left.forwardslash.chevron.right") }
            .tag(1)

            NavigationStack { SettingsView() }
                .tabItem { Label("Pengaturan", systemImage: "gearshape") }
                .tag(2)
        }
        .tint(palette.accent)
        .preferredColorScheme(.dark)
        .sheet(isPresented: $showPairing) { PairingView().environmentObject(state) }
        .task {
            if state.isPaired {
                await state.refreshStatus()
                await state.loadSessions()
                await state.restartLiveEvents()
            } else {
                showPairing = true
            }
        }
        .onChange(of: tab) { newValue in
            if newValue == 1 { Task { await state.loadSessions() } }
        }
    }

    @ToolbarContentBuilder
    private var chatToolbar: some ToolbarContent {
        ToolbarItem(placement: .navigationBarLeading) {
            Button {
                state.startNewSession()
            } label: {
                Image(systemName: "square.and.pencil").foregroundColor(palette.accent)
            }
        }
        ToolbarItem(placement: .navigationBarTrailing) {
            Menu {
                Button("Sesi terbaru \(state.engine.short)") {
                    Task { await state.openLatestConversation() }
                }
                Button("Muat ulang transkrip") {
                    Task { await state.refreshActiveTranscript() }
                }
                Divider()
                // Switching engines starts a fresh session: the two CLIs keep
                // separate histories and cannot resume each other's.
                ForEach(Engine.allCases, id: \.self) { engine in
                    if engine != state.engine {
                        Button("Beralih ke \(engine.label)") { state.switchEngine(to: engine) }
                    }
                }
                Divider()
                Button("Scan QR pairing") { showPairing = true }
            } label: {
                Image(systemName: "ellipsis.circle").foregroundColor(palette.accent)
            }
        }
    }
}
