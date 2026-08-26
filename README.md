# Antigravity CLI App (Go + Bubble Tea)

A smooth, modern AI Terminal User Interface (TUI) built in **Go** using the [Bubble Tea](https://github.com/charmbracelet/bubbletea) framework and [Lipgloss](https://github.com/charmbracelet/lipgloss).

---

## ✨ Fitur & Keunggulan Flow

1. **Sesi Baru & Input Handling**:
   - Inisialisasi instan dengan *Alternate Screen Buffer* (`tea.WithAltScreen()`).
   - Area prompt interaktif dengan multi-line support (`bubbles/textarea`).
2. **State Transition & Thinking Indicator**:
   - Prompt dikunci seketika (*optimistic lock*).
   - Status beralih ke `THINKING` dengan animated braille spinner dan duration timer.
3. **Tool Execution Steps**:
   - Menampilkan status tool yang sedang berjalan secara terisolasi tanpa merusak layout terminal.
4. **Smooth Token Streaming**:
   - Streaming respon AI per-token/chunk melalui goroutine channel (`tea.Cmd`).
   - Scrolling otomatis pada `bubbles/viewport` tanpa loncatan kasar.
5. **Kembali ke Ready State**:
   - Respon diformat rapi, input dibuka kembali, dan fokus kursor dikembalikan secara otomatis.

---

## 📁 Struktur Direktori

```
antigravity-cli-app/
├── .github/
│   └── workflows/
│       ├── build.yml          # CI: Multi-OS Build & Test matrix
│       ├── release.yml        # Tagged Release workflow
│       └── latest-release.yml # Automated Latest Release (APK, DEB, Binaries)
├── cmd/
│   └── agy/
│       └── main.go            # CLI entrypoint
├── internal/
│   ├── app/
│   │   ├── commands.go        # Bubble Tea async commands & streaming channels
│   │   ├── model.go           # State machine (Ready, Thinking, ExecutingTool, Streaming, Done)
│   │   ├── styles.go          # Lipgloss themes, colors, badges, and layout styles
│   │   ├── update.go          # Event handlers, key bindings, and message dispatchers
│   │   └── view.go            # Layout renderers (header, viewport, input box, footer)
│   └── engine/
│       ├── engine.go          # AI streaming engine implementation
│       └── types.go           # Data structures for messages, tools, and stream chunks
├── nfpm.yaml                  # NFPM Packaging config for .apk and .deb
├── .gitignore
├── go.mod
├── Makefile
└── README.md
```

---

## 🤖 Otomasi GitHub Actions & Output Release "Latest"

Workflow [`.github/workflows/latest-release.yml`](.github/workflows/latest-release.yml) disiapkan untuk berjalan secara **otomatis setiap kali ada push ke branch `main`**.

### Daftar Aset Output yang Dihasilkan pada Rilis `latest`:
1. 📱 **APK Package**:
   - `antigravity-cli-app-latest.apk` (Alpine package installer)
   - `agy-android-arm64.tar.gz` (Android CLI binary untuk Termux/Android shell)
2. 🐧 **Debian / Ubuntu**: `*.deb`
3. 💻 **Linux**: `agy-linux-amd64.tar.gz` & `agy-linux-arm64.tar.gz`
4. 🍎 **macOS**: `agy-darwin-arm64.tar.gz` & `agy-darwin-amd64.tar.gz`
5. 🪟 **Windows**: `agy-windows-amd64.zip`

---

## 🚀 Cara Menjalankan Secara Lokal

```bash
# 1. Unduh modul Go
go mod tidy

# 2. Jalankan aplikasi
make run
# atau
go run ./cmd/agy
```
