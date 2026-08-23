import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var state: AppState
    @State private var showPairing = false
    @State private var sandboxMode = "full"
    @State private var savingSandbox = false

    private var palette: Palette { state.palette }

    private let sandboxModes: [(key: String, label: String, note: String)] = [
        ("full", "Akses penuh", "Tanpa sandbox. Paling cepat, paling berisiko."),
        ("workspace", "Tulis di workspace", "Boleh menulis di workdir saja."),
        ("readonly", "Hanya baca", "Tidak boleh mengubah file apa pun.")
    ]

    var body: some View {
        List {
            Section("Engine") {
                Picker("Engine aktif", selection: Binding(
                    get: { state.engine },
                    set: { state.switchEngine(to: $0) })) {
                    ForEach(Engine.allCases, id: \.self) { engine in
                        Text(engine.label).tag(engine)
                    }
                }
                .pickerStyle(.segmented)

                Picker("Model", selection: Binding(
                    get: { state.model },
                    set: { state.model = $0 })) {
                    ForEach(state.engine.models, id: \.self) { Text($0).tag($0) }
                }
                row("Repository", state.engine.repo)
            }

            Section("Mode Eksekusi") {
                ForEach(sandboxModes, id: \.key) { mode in
                    Button {
                        Task { await applySandbox(mode.key) }
                    } label: {
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(mode.label)
                                    .foregroundColor(sandboxMode == mode.key ? palette.accent : palette.textMain)
                                Text(mode.note)
                                    .font(.system(size: 12))
                                    .foregroundColor(palette.textMuted)
                            }
                            Spacer()
                            if sandboxMode == mode.key {
                                Image(systemName: "checkmark").foregroundColor(palette.accent)
                            }
                        }
                    }
                    .disabled(savingSandbox)
                }
            }

            Section("Server") {
                row("Status", state.status)
                row("Endpoint", state.endpoint.isEmpty ? "-" : state.endpoint)

                Button("Pairing ulang") { showPairing = true }
                    .foregroundColor(palette.accent)
                Button("Test koneksi") { Task { await state.refreshStatus() } }
                    .foregroundColor(palette.textMain)
                Button("Putuskan & hapus token", role: .destructive) { state.unpair() }
            }

            Section {
                NavigationLink("Penggunaan & Kuota") { UsageView() }
            }
        }
        .scrollContentBackground(.hidden)
        .background(palette.background.ignoresSafeArea())
        .navigationTitle("Pengaturan")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showPairing) { PairingView().environmentObject(state) }
        .task { await loadSandbox() }
    }

    private func row(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).foregroundColor(palette.textMuted)
            Spacer()
            Text(value)
                .foregroundColor(palette.textMain)
                .lineLimit(1)
                .truncationMode(.middle)
        }
    }

    private func loadSandbox() async {
        guard state.isPaired else { return }
        if let response = try? await state.client.get("/api/settings", as: SettingsResponse.self),
           let mode = response.settings?.sandboxMode {
            sandboxMode = mode
        }
    }

    private func applySandbox(_ mode: String) async {
        savingSandbox = true
        defer { savingSandbox = false }
        // The server owns this setting; mirror what it confirms rather than
        // assuming the change stuck.
        if let response = try? await state.client.post("/api/settings",
                                                       body: ["sandboxMode": mode],
                                                       as: SettingsResponse.self),
           let saved = response.settings?.sandboxMode {
            sandboxMode = saved
        }
    }
}
