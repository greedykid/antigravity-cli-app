import SwiftUI

struct UsageView: View {
    @EnvironmentObject private var state: AppState
    @State private var usage: UsageResponse?
    @State private var loading = false

    private var palette: Palette { state.palette }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if let usage {
                    activity(usage)
                    quota(usage)
                    details(usage)
                } else if loading {
                    ProgressView().tint(palette.accent).frame(maxWidth: .infinity).padding(.top, 40)
                } else {
                    Text("Tidak ada data penggunaan.")
                        .foregroundColor(palette.textMuted)
                }
            }
            .padding(16)
        }
        .background(palette.background.ignoresSafeArea())
        .navigationTitle("Penggunaan")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { Task { await load(force: true) } } label: {
                    Image(systemName: "arrow.clockwise").foregroundColor(palette.accent)
                }
            }
        }
        .task { await load(force: false) }
    }

    private func load(force: Bool) async {
        loading = true
        defer { loading = false }
        let suffix = force ? "&refresh=1" : ""
        usage = try? await state.client.get("/api/usage?engine=\(state.engine.rawValue)\(suffix)",
                                            as: UsageResponse.self,
                                            timeout: force ? 70 : 20)
    }

    private func card<Content: View>(_ title: String, subtitle: String?,
                                     @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(palette.textMain)
            if let subtitle {
                Text(subtitle).font(.system(size: 12)).foregroundColor(palette.textMuted)
            }
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(palette.border, lineWidth: 1))
    }

    private func activity(_ usage: UsageResponse) -> some View {
        card("AKTIVITAS", subtitle: "Dihitung dari riwayat lokal di server") {
            VStack(spacing: 8) {
                statRow("5 jam terakhir", "\(usage.promptsLast5h ?? 0) prompt")
                statRow("24 jam terakhir", "\(usage.promptsLast24h ?? 0) prompt")
                statRow("7 hari terakhir", "\(usage.promptsLast7d ?? 0) prompt")
            }
        }
    }

    @ViewBuilder
    private func quota(_ usage: UsageResponse) -> some View {
        let groups = usage.quotaGroups ?? []
        if usage.quotaKnown == true && !groups.isEmpty {
            card("KUOTA PROVIDER",
                 subtitle: usage.quotaStale == true
                    ? "Angka terakhir yang berhasil dibaca"
                    : "Sisa kuota akun Antigravity") {
                VStack(alignment: .leading, spacing: 18) {
                    ForEach(groups) { group in
                        VStack(alignment: .leading, spacing: 12) {
                            Text(group.group ?? "Model")
                                .font(.system(size: 12.5, weight: .semibold))
                                .foregroundColor(palette.textMain)
                            ForEach(Array((group.limits ?? []).enumerated()), id: \.offset) { _, limit in
                                quotaBar(limit)
                            }
                        }
                    }
                }
            }
        } else {
            card("KUOTA PROVIDER", subtitle: "Tidak bisa dibaca saat ini") {
                Text(usage.quotaStatus ?? "CLI tidak mengembalikan sisa kuota.")
                    .font(.system(size: 12.5))
                    .foregroundColor(palette.textMuted)
            }
        }
    }

    private func quotaBar(_ limit: QuotaLimit) -> some View {
        let percent = max(0, min(100, limit.percent ?? 0))
        // Remaining quota: green with room, amber when thin, red when nearly out.
        let color = percent <= 10 ? palette.red : (percent <= 30 ? palette.amber : palette.green)

        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(limit.label ?? "Limit")
                    .font(.system(size: 13))
                    .foregroundColor(palette.textMuted)
                Spacer()
                Text("\(percent)%")
                    .font(.system(size: 13.5, weight: .bold))
                    .foregroundColor(color)
            }
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Capsule().fill(palette.surfaceMuted)
                    Capsule().fill(color)
                        .frame(width: max(percent > 0 ? 4 : 0, geometry.size.width * CGFloat(percent) / 100))
                }
            }
            .frame(height: 7)

            if let reset = Self.resetText(limit.resetAt) {
                Text(reset).font(.system(size: 11.5)).foregroundColor(palette.textLight)
            }
        }
    }

    /// A countdown is more useful here than the raw timestamp.
    static func resetText(_ iso: String?) -> String? {
        guard let iso, !iso.isEmpty else { return nil }
        let formatter = ISO8601DateFormatter()
        guard let date = formatter.date(from: iso) else { return nil }
        let remaining = date.timeIntervalSinceNow
        if remaining <= 0 { return "Sudah direset" }

        let minutes = Int(remaining / 60)
        let days = minutes / (60 * 24)
        let hours = (minutes % (60 * 24)) / 60
        let mins = minutes % 60
        if days > 0 { return "Reset dalam \(days) hari \(hours) jam" }
        if hours > 0 { return "Reset dalam \(hours) jam \(mins) mnt" }
        return "Reset dalam \(mins) mnt"
    }

    private func details(_ usage: UsageResponse) -> some View {
        card("RINCIAN", subtitle: nil) {
            VStack(spacing: 8) {
                statRow("Engine aktif", state.engine.label)
                statRow("Model aktif", state.model)
                statRow("Total prompt", "\(usage.totalPrompts ?? 0)")
                statRow("Total sesi", "\(usage.totalSessions ?? 0)")
                statRow("Langkah dieksekusi", "\(usage.totalSteps ?? 0)")
                statRow("Pemanggilan tool", "\(usage.totalTools ?? 0)")
                statRow("Perkiraan token", "\(Int(usage.estimatedTokens ?? 0))")
                statRow("Host", usage.hostname ?? "-")
                statRow("Uptime", usage.uptime ?? "-")
            }
        }
    }

    private func statRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.system(size: 13)).foregroundColor(palette.textMuted)
            Spacer()
            Text(value).font(.system(size: 13, weight: .semibold)).foregroundColor(palette.textMain)
        }
    }
}
