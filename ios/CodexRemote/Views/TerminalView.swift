import SwiftUI

struct TerminalView: View {
    @EnvironmentObject private var state: AppState
    @State private var commandInput: String = ""
    @State private var outputLogs: String = "$ Antigravity Remote Terminal (PTY) Ready.\nKetik perintah bash di bawah:\n\n"
    @State private var isRunning: Bool = false
    @State private var currentCwd: String = "~"

    private var palette: Palette { state.palette }

    private let presets = [
        "git status", "git diff", "git log -n 5", "git branch -a", "ls -la", "pwd", "npm test", "docker ps", "free -h", "uptime"
    ]

    var body: some View {
        VStack(spacing: 0) {
            // CWD & Host bar
            HStack(spacing: 6) {
                Image(systemName: "laptopcomputer")
                    .foregroundColor(palette.green)
                Text(currentCwd)
                    .font(.system(size: 11.5, design: .monospaced))
                    .foregroundColor(palette.accent)
                    .lineLimit(1)
                Spacer()
                Button("Clear") {
                    outputLogs = "$ Terminal cleared.\n"
                }
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(palette.textMuted)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(palette.surfaceMuted)

            // Presets row
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(presets, id: \.self) { cmd in
                        Button {
                            commandInput = cmd
                            runCommand(cmd)
                        } label: {
                            Text(cmd)
                                .font(.system(size: 11.5, weight: .semibold, design: .monospaced))
                                .foregroundColor(palette.accent)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(palette.surface)
                                .clipShape(Capsule())
                                .overlay(Capsule().stroke(palette.border, lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
            }

            // Output Console
            ScrollViewReader { proxy in
                ScrollView {
                    Text(outputLogs)
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(Color(red: 0.79, green: 0.82, blue: 0.85))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .id("bottomID")
                }
                .background(Color(red: 0.05, green: 0.07, blue: 0.09))
                .onChange(of: outputLogs) { _ in
                    proxy.scrollTo("bottomID", anchor: .bottom)
                }
            }

            // Command input bar
            HStack(spacing: 8) {
                TextField("", text: $commandInput, prompt: Text("Ketik perintah bash...").foregroundColor(palette.textLight))
                    .font(.system(size: 13, design: .monospaced))
                    .foregroundColor(palette.textMain)
                    .padding(10)
                    .background(palette.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(palette.border, lineWidth: 1))
                    .onSubmit {
                        let cmd = commandInput.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !cmd.isEmpty {
                            runCommand(cmd)
                        }
                    }

                Button {
                    let cmd = commandInput.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !cmd.isEmpty {
                        runCommand(cmd)
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "play.fill")
                            .font(.system(size: 11))
                        Text("Run")
                            .font(.system(size: 13, weight: .bold))
                    }
                    .foregroundColor(palette.onAccent)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(palette.accent)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
                .disabled(isRunning)
            }
            .padding(12)
            .background(palette.surfaceMuted)
        }
        .navigationTitle("Terminal PTY")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func runCommand(_ cmd: String) {
        commandInput = ""
        outputLogs += "\n$ \(cmd)\n[Menjalankan...]\n"
        isRunning = true

        Task {
            do {
                guard let client = state.client else {
                    await MainActor.run {
                        outputLogs += "Error: Bridge client not connected.\n"
                        isRunning = false
                    }
                    return
                }
                let payload: [String: Any] = ["command": cmd]
                let res = try await client.postJSON(path: "/api/terminal/exec", body: payload)
                await MainActor.run {
                    if let out = res["output"] as? String, !out.isEmpty {
                        outputLogs += out + (out.hasSuffix("\n") ? "" : "\n")
                    } else if let err = res["error"] as? String, !err.isEmpty {
                        outputLogs += "Error: \(err)\n"
                    } else {
                        outputLogs += "(Perintah selesai)\n"
                    }
                    if let cwd = res["cwd"] as? String, !cwd.isEmpty {
                        currentCwd = cwd
                    }
                    isRunning = false
                }
            } catch {
                await MainActor.run {
                    outputLogs += "Gagal terhubung ke bridge: \(error.localizedDescription)\n"
                    isRunning = false
                }
            }
        }
    }
}
