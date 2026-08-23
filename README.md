# 🌌 Antigravity & Codex Remote for Android

> **Mobile Command Center & Live Companion HUD for Google Antigravity CLI (`agy`) and OpenAI Codex CLI.**

Antigravity Remote adalah aplikasi Android native yang memungkinkan Anda mengontrol, memantau, dan berinteraksi secara real-time dengan **Google Antigravity CLI** maupun **OpenAI Codex CLI** yang berjalan di VPS, server, atau komputer lokal Anda langsung dari genggaman tangan.

---

## 📱 Fitur Unggulan

* **🎨 Claude Code & Material Design Aesthetics:** Tampilan warm-ivory (`#FBFBF9`), aksen terracotta orange (`#D96B43`), tipografi serif elegan, dan dark code blocks.
* **⚡ Instant In-Session Live Streaming:** Transkrip percakapan, proses pemikiran (*Thinking*), dan eksekusi perintah (*Tool Calls*) ter-update secara real-time detik demi detik di layar tanpa perlu keluar-masuk sesi.
* **📂 Interactive Accordion Bottom Sheet:** Rincian eksekusi tool dan thinking dapat dibuka dalam modal swipeable yang dapat digeser naik-turun atau dimaksimalkan (*fullscreen*), lengkap dengan status *live spinner*.
* **📷 Instant QR Code Pairing:** Cukup scan QR code di terminal PC/VPS Anda dengan kamera HP untuk terhubung secara instan tanpa perlu mengetik URL panjang.
* **🖼️ Pairing dari Galeri:** Punya screenshot QR dari sesi SSH atau foto lama? Pilih gambarnya langsung — tidak perlu kamera menyorot terminal.
* **📋 1-Tap Clipboard Pairing:** Salin tautan pairing (`agy://connect?...`) atau JSON token di HP, lalu ketuk *Paste from Clipboard*.
* **🔄 Two-Way Live Terminal Synchronization:** Pantau sesi terminal PC yang sedang berjalan secara langsung di HP (*Live Companion HUD*), atau lanjutkan instruksi dari HP secara bergantian.
* **🔀 Dual Engine Gateway:** Beralih antara engine **Google Antigravity CLI** dan **OpenAI Codex CLI** dengan 1 ketukan.
* **📊 Rich Markdown & Native Tables:** Dukungan format Markdown lengkap dengan tabel horizontal scrollable dan tombol salin kode cepat.
* **🎙️ Voice Dictation & File Upload:** Dukungan input suara (Speech-to-Text) dan upload file / gambar langsung ke server.
* **🔔 Notifikasi Task Selesai:** Kirim prompt panjang, kunci layar — HP memberi tahu saat task selesai atau gagal lewat koneksi SSE latar belakang.
* **📁 File Browser Workspace:** Telusuri dan baca file di workdir server langsung dari HP, lengkap dengan syntax highlight.
* **🔀 Panel Git:** Lihat branch, file berubah, diff berwarna, lalu commit dan push tanpa membuka terminal.
* **🔎 Pencarian Sesi:** Cari kata kunci di judul maupun isi transkrip semua sesi.
* **🖥️ Multi-Server:** Simpan beberapa VPS dan beralih dengan satu ketukan.
* **🛡️ Mode Eksekusi:** Pilih sandbox CLI di server — akses penuh, tulis-di-workspace, atau hanya-baca.
* **⏱️ Task Berjalan di Latar:** Prompt panjang dijalankan sebagai job di server, jadi koneksi putus atau HP terkunci tidak membatalkannya. Timeout bisa diatur sampai 4 jam.
* **✏️ Edit File dari HP:** Perbaiki hasil AI langsung dari file browser tanpa buka laptop.
* **📂 Multi-Proyek:** Simpan beberapa folder proyek dan alihkan target panel Git & File.
* **📤 Ekspor Transkrip:** Simpan atau bagikan sesi sebagai Markdown.
* **🧾 Audit Log & Rate Limit:** Semua eksekusi perintah tercatat; endpoint dibatasi 20 permintaan per menit.

---

## 🔐 Keamanan

Bridge server menjalankan perintah CLI sebagai user Anda, jadi aksesnya diperlakukan sebagai kredensial penuh ke server.

* **Token unik per instalasi.** Dibuat otomatis saat server pertama kali jalan dan disimpan di `~/.codex-remote/token` (mode `0600`). Tidak ada lagi token bawaan di repo. Server **menolak start** kalau token masih nilai default lama.
* **Hanya loopback.** Server bind ke `127.0.0.1` secara bawaan; satu-satunya pintu masuk adalah Cloudflare Tunnel. Ubah lewat `BRIDGE_HOST` kalau memang perlu.
* **Ganti token kapan saja:** `codex-remote rotate` — server direstart dan QR pairing baru ditampilkan.
* **Batasi kemampuan CLI** lewat menu *Mode Eksekusi* di aplikasi (`full` / `workspace` / `readonly`).

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
* `codex-remote rotate` : Membuat token baru (perlu pairing ulang dari HP).

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


---

## 🌐 API Bridge

Semua endpoint (kecuali `/health`) butuh header `Authorization: Bearer <token>`.

| Method | Endpoint | Kegunaan |
|---|---|---|
| GET | `/health` | Status server, daftar engine dan fitur |
| GET | `/api/events` | Stream Server-Sent Events (`task.started`, `cli.event`, `task.finished`) |
| POST | `/api/chat` | Kirim prompt; `conversationId` melanjutkan sesi lama |
| GET | `/api/sessions` | Daftar sesi Antigravity + Codex |
| GET | `/api/session/transcript?id=` | Transkrip satu sesi |
| GET | `/api/search?q=` | Cari di judul dan isi transkrip |
| GET | `/api/files?path=` | Daftar isi folder (dikunci di dalam workdir) |
| GET | `/api/files/read?path=` | Baca file (maks 512 KB, deteksi biner) |
| GET | `/api/git/status`, `/api/git/diff` | Status dan diff repo |
| POST | `/api/git/commit`, `/api/git/push` | Commit dan push |
| GET/POST | `/api/settings` | Mode sandbox dan preferensi notifikasi |
| POST | `/api/session/control` | Interrupt task yang sedang jalan |
| POST | `/api/upload` | Upload file / gambar |
| POST | `/api/files/write` | Tulis file (dikunci di workdir, maks 2 MB) |
| GET | `/api/jobs`, `/api/jobs/:id` | Status task yang berjalan di latar |
| GET | `/api/session/export?id=` | Transkrip sesi dalam Markdown |
| GET/POST | `/api/projects` | Daftar folder proyek |
| GET | `/api/uploads` | Isi & ukuran folder upload |
| POST | `/api/uploads/cleanup` | Hapus upload lama |
| GET | `/api/audit` | Catatan aktivitas |

`POST /api/chat` dengan `"async": true` membalas `202 {jobId}` seketika; progres lewat `/api/events`, hasil lewat `/api/jobs/:id`.

### Tunnel dengan URL tetap

URL `trycloudflare.com` berubah tiap restart. Untuk URL permanen, simpan token Cloudflare Named Tunnel di `~/.codex-remote/tunnel-token` — `bin/tunnel-run.sh` otomatis memakainya dan pairing tidak perlu diulang lagi.


---

## 🧪 Pengembangan

```bash
npm test --prefix bridge     # 76 tes, tanpa dependensi tambahan
```

CI menjalankan tes ini sebelum membangun APK, jadi build gagal kalau ada regresi.

Struktur kode:

| File | Isi |
|---|---|
| `bridge/server.js` | Routing HTTP dan eksekusi CLI |
| `bridge/config.js` | Token, workdir, host bind |
| `bridge/jobs.js` | Registry task latar belakang |
| `bridge/events.js` | Hub Server-Sent Events |
| `bridge/search.js` | Indeks pencarian transkrip |
| `bridge/files.js` | File browser & penulisan (path-jailed) |
| `bridge/git.js` | Operasi git (argv, bukan shell) |
| `bridge/audit.js` | Log aktivitas & rate limit |
| `bridge/settings.js` | Sandbox, timeout, retensi, proyek |
| `app/.../MainActivity.java` | Chat, hub, pengaturan, sidebar |
| `app/.../WorkspacePanels.java` | File, Git, Search, Proyek, Pemeliharaan |
| `app/.../MarkdownRenderer.java` | Renderer markdown |
| `app/.../BridgeClient.java` | Klien HTTP |
| `app/.../LiveEventService.java` | SSE latar belakang + notifikasi |
| `app/.../Theme.java` | Palet & pembuat view |
