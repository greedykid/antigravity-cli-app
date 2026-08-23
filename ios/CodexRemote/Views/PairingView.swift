import PhotosUI
import SwiftUI
import UIKit

struct PairingView: View {
    @EnvironmentObject private var state: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var showScanner = false
    @State private var photoItem: PhotosPickerItem?
    @State private var manualUrl = ""
    @State private var manualToken = ""
    @State private var message: String?

    private var palette: Palette { state.palette }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Jalankan `codex-remote` di terminal server, lalu pindai QR yang muncul.")
                        .font(.system(size: 13))
                        .foregroundColor(palette.textMuted)

                    if showScanner {
                        QRScannerView { payload in
                            showScanner = false
                            apply(payload)
                        }
                        .frame(height: 280)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(palette.accent, lineWidth: 2))
                    } else {
                        primaryButton("Scan QR Code", icon: "qrcode.viewfinder") {
                            showScanner = true
                        }
                    }

                    PhotosPicker(selection: $photoItem, matching: .images) {
                        actionLabel("Ambil QR dari Galeri", icon: "photo.on.rectangle")
                    }

                    Button {
                        if let text = UIPasteboard.general.string { apply(text) }
                        else { message = "Clipboard kosong" }
                    } label: {
                        actionLabel("Tempel dari Clipboard", icon: "doc.on.clipboard")
                    }

                    Divider().background(palette.border).padding(.vertical, 4)

                    Text("Atau masukkan manual")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(palette.textMuted)

                    field("URL server", text: $manualUrl)
                    field("Token", text: $manualToken)

                    primaryButton("Hubungkan", icon: "link") {
                        guard !manualUrl.isEmpty, !manualToken.isEmpty else {
                            message = "URL dan token harus diisi"
                            return
                        }
                        state.applyPairing(PairingPayload(url: manualUrl, token: manualToken,
                                                          engine: nil, name: nil))
                        dismiss()
                    }

                    if let message {
                        Text(message)
                            .font(.system(size: 13))
                            .foregroundColor(palette.amber)
                    }
                }
                .padding(20)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("Pairing")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Tutup") { dismiss() }.foregroundColor(palette.textMuted)
                }
            }
        }
        .onChange(of: photoItem) { _ in loadPickedImage() }
    }

    private func loadPickedImage() {
        guard let photoItem else { return }
        Task {
            guard let data = try? await photoItem.loadTransferable(type: Data.self),
                  let image = UIImage(data: data) else {
                message = "Gambar tidak bisa dibaca"
                return
            }
            guard let payload = QRImageDecoder.decode(image) else {
                message = "Tidak menemukan QR pada gambar itu. Potong gambar sampai QR memenuhi bingkai."
                return
            }
            apply(payload)
        }
    }

    private func apply(_ raw: String) {
        guard let payload = AppState.parsePairingText(raw) else {
            message = "Data pairing tidak dikenali"
            return
        }
        state.applyPairing(payload)
        dismiss()
    }

    private func field(_ placeholder: String, text: Binding<String>) -> some View {
        TextField("", text: text, prompt: Text(placeholder).foregroundColor(palette.textLight))
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .foregroundColor(palette.textMain)
            .padding(12)
            .background(palette.surface)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(palette.border, lineWidth: 1))
    }

    private func actionLabel(_ title: String, icon: String) -> some View {
        HStack {
            Image(systemName: icon)
            Text(title).font(.system(size: 14, weight: .semibold))
            Spacer()
        }
        .foregroundColor(palette.textMain)
        .padding(14)
        .frame(maxWidth: .infinity)
        .background(palette.surfaceMuted)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(palette.border, lineWidth: 1))
    }

    private func primaryButton(_ title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Image(systemName: icon)
                Text(title).font(.system(size: 15, weight: .bold))
            }
            .foregroundColor(palette.onAccent)
            .frame(maxWidth: .infinity)
            .padding(14)
            .background(palette.accent)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
    }
}
