# 🌌 Antigravity & Codex Remote for Android

> **Mobile Command Center & Live Companion HUD for Google Antigravity CLI (`agy`) and OpenAI Codex CLI.**

Antigravity Remote adalah aplikasi Android native yang memungkinkan Anda mengontrol, memantau, dan berinteraksi secara real-time dengan **Google Antigravity CLI** maupun **OpenAI Codex CLI** yang berjalan di VPS, server, atau komputer lokal Anda langsung dari genggaman tangan.

---

## 📱 Fitur Unggulan

* **🎨 Claude Code & Material Design Aesthetics:** Tampilan warm-ivory (`#FBFBF9`), aksen terracotta orange (`#D96B43`), tipografi serif elegan, dan dark code blocks.
* **⚡ Instant In-Session Live Streaming:** Transkrip percakapan, proses pemikiran (*Thinking*), dan eksekusi perintah (*Tool Calls*) ter-update secara real-time detik demi detik di layar tanpa perlu keluar-masuk sesi.
* **📂 Interactive Accordion Bottom Sheet:** Rincian eksekusi tool dan thinking dapat dibuka dalam modal swipeable yang dapat digeser naik-turun atau dimaksimalkan (*fullscreen*), lengkap dengan status *live spinner*.
* **📷 Instant QR Code Pairing:** Cukup scan QR code di terminal PC/VPS Anda dengan kamera HP untuk terhubung secara instan tanpa perlu mengetik URL panjang.
* **📋 1-Tap Clipboard Pairing:** Salin tautan pairing (`agy://connect?...`) atau JSON token di HP, lalu ketuk *Paste from Clipboard*.
* **🔄 Two-Way Live Terminal Synchronization:** Pantau sesi terminal PC yang sedang berjalan secara langsung di HP (*Live Companion HUD*), atau lanjutkan instruksi dari HP secara bergantian.
* **🔀 Dual Engine Gateway:** Beralih antara engine **Google Antigravity CLI** dan **OpenAI Codex CLI** dengan 1 ketukan.
* **📊 Rich Markdown & Native Tables:** Dukungan format Markdown lengkap dengan tabel horizontal scrollable dan tombol salin kode cepat.
* **🎙️ Voice Dictation & File Upload:** Dukungan input suara (Speech-to-Text) dan upload file / gambar langsung ke server.

---

## ⚡ Setup Server 1-Perintah (Paling Cepat & Otomatis)

Jalankan perintah berikut di terminal VPS / server Linux Anda:

```bash
curl -fsSL https://raw.githubusercontent.com/greedykid/codexcli-remote-app/main/install.sh | bash
```

Script ini akan secara otomatis:
1. Menginstall Node.js dan Cloudflared (jika belum ada).
2. Menyiapkan service server background (`codex-bridge` & `codex-tunnel`) agar selalu aktif otomatis saat booting/restart.
3. Menghubungkan Cloudflare Tunnel publik secara gratis dan aman.
4. Menampilkan **QR Code Pairing** di terminal untuk langsung di-scan dari HP Android Anda!

### 🛠️ Perintah Bantuan Terminal (Setelah Install)
Ketik perintah ini di terminal Anda kapan saja:
* `codex-remote` atau `agy-remote` : Menampilkan kembali QR Code Pairing & URL.
* `codex-remote status` : Memeriksa status aktif server & tunnel.
* `codex-remote logs` : Membuka live monitor logs server.
* `codex-remote restart` : Merestart server bridge dan tunnel.

---

## 🚀 Panduan Setup Manual (Alternatif)

Jika Anda ingin menjalankan secara manual tanpa background service:

---

### 3. Membuat Secure Tunnel (Cloudflare Tunnel / Tailscale)

Agar HP Android dapat mengakses Bridge Server di VPS secara aman:

```bash
# Menggunakan Cloudflare Tunnel gratis tanpa buka port router:
cloudflared tunnel --url http://127.0.0.1:8787
```
*Catat URL publik yang dihasilkan (misal: `https://your-tunnel-name.trycloudflare.com`).*

---

### 4. Menampilkan QR Code Pairing di Terminal

Jalankan perintah berikut di terminal server/VPS Anda:

```bash
cd /path/to/codexcli-remote-app/bridge
BRIDGE_URL="https://your-tunnel-name.trycloudflare.com/api/chat" REMOTE_TOKEN="codex-remote-token-2026" npm run pair
```
Terminal Anda akan menampilkan **QR Code Pairing** dan tautan cepat `agy://connect?...`.

---

### 5. Memasang & Menghubungkan Aplikasi Android

1. **Unduh APK:**
   * Buka menu **Actions** di GitHub repository ini -> Pilih build terbaru -> Unduh artifact **`codex-remote-debug-apk`**.
   * Ekstrak file `.zip` dan instal `app-debug.apk` di HP Android Anda.

2. **Hubungkan Aplikasi (Pilih salah satu metode):**

   * **Metode A: Scan QR Code (Paling Cepat & Direkomendasikan) 📸**
     1. Buka aplikasi di HP.
     2. Ketuk ikon **QR Code** di pojok kanan atas layar atau di Menu Sidebar.
     3. Berikan izin kamera dan arahkan kamera HP ke QR Code di terminal Anda.
     4. Aplikasi seketika terhubung dan siap digunakan!

   * **Metode B: 1-Tap Paste dari Clipboard 📋**
     1. Salin link pairing terminal (`agy://connect?...`) atau teks JSON.
     2. Di aplikasi Android, buka menu sidebar lalu pilih **"Paste Pairing from Clipboard"**.

   * **Metode C: Pengaturan Manual ⚙️**
     1. Buka sidebar -> pilih **"Connection Settings"**.
     2. Masukkan **Bridge Endpoint URL** (contoh: `https://your-tunnel.trycloudflare.com/api/chat`).
     3. Masukkan **Bearer Token** (contoh: `codex-remote-token-2026`).
     4. Ketuk **Save & Connect**.

---

## 💡 Cara Penggunaan

1. **Memulai Sesi Chat Baru:**
   * Ketik instruksi coding Anda di kotak input bawah (*"Code anything..."*), lalu ketuk tombol kirim berwarna terracotta.
   * Amati proses pemikiran (*Thinking*) dan eksekusi aksi (*Tool calls*) yang ter-update secara real-time.
   * Ketuk pill status ringkasan untuk membuka **Bottom Sheet Modal** guna melihat detail eksekusi.
2. **Melihat Sesi Aktif di Terminal (Live HUD Mirror):**
   * Ketuk ikon **Menu** di pojok kiri atas -> pilih **"All Sessions (Code Hub)"**.
   * Di bagian **"Live & Active"**, pilih sesi yang sedang berjalan di terminal PC/VPS Anda.
   * Layar Android Anda seketika menjadi monitor live (*companion screen*) dari terminal PC Anda.
3. **Mengunggah File / Gambar:**
   * Ketuk tombol **`+`** di samping composer chat untuk memilih file/gambar dari HP. File akan otomatis diunggah ke server dan dilampirkan ke prompt AI.
4. **Menghentikan Proses (Interrupt / Stop):**
   * Ketuk titik tiga di pojok kanan atas -> pilih **"Interrupt / Stop Task"** untuk menghentikan proses CLI yang sedang berjalan.

---

## 🛠️ Build APK Mandiri dari Source Code

Aplikasi menggunakan pipeline otomatis **GitHub Actions**:
1. Lakukan `git push` ke branch `main`.
2. Buka tab **Actions** di GitHub repository untuk memantau proses kompilasi.
3. Setelah workflow selesai (`✓ Success`), unduh artifact `codex-remote-debug-apk`.

---

## 🔒 Keamanan & Privasi

* Seluruh komunikasi diamankan menggunakan **Bearer Token Authentication** dan koneksi **HTTPS/TLS**.
* Bridge Server berjalan di mesin pribadi Anda tanpa server perantara pihak ketiga.
* Token rahasia disimpan secara lokal dan aman di `SharedPreferences` perangkat Android Anda.
