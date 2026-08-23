#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const os = require('os');
const { execSync, spawn } = require('child_process');

let config;
for (const candidate of ['../bridge/config.js', './config.js', '../config.js']) {
  try { config = require(path.join(__dirname, candidate)); break; } catch (e) {}
}

let qrcode;
try {
  qrcode = require('qrcode-terminal');
} catch (e) {
  try {
    qrcode = require(path.join(__dirname, '../bridge/node_modules/qrcode-terminal'));
  } catch (err) {}
}

const action = process.argv[2] || 'pair';

function getTunnelUrl() {
  const home = os.homedir();
  const logPaths = [
    path.join(home, 'tunnel.log'),
    path.join(home, '.codexcli-remote-app/tunnel.log'),
    '/tmp/codex-tunnel.log'
  ];

  for (const p of logPaths) {
    if (fs.existsSync(p)) {
      try {
        const content = fs.readFileSync(p, 'utf8');
        const matches = content.match(/https:\/\/[a-zA-Z0-9.-]+\.trycloudflare\.com/g);
        if (matches && matches.length > 0) {
          return matches[matches.length - 1];
        }
      } catch (e) {}
    }
  }

  try {
    const journal = execSync('journalctl -u codex-tunnel -n 100 --no-pager 2>/dev/null', { encoding: 'utf8' });
    const matches = journal.match(/https:\/\/[a-zA-Z0-9.-]+\.trycloudflare\.com/g);
    if (matches && matches.length > 0) {
      return matches[matches.length - 1];
    }
  } catch (e) {}

  // Fallback to local IP if no tunnel
  try {
    const ifaces = os.networkInterfaces();
    for (const name of Object.keys(ifaces)) {
      for (const iface of ifaces[name]) {
        if (!iface.internal && iface.family === 'IPv4') {
          return `http://${iface.address}:8787`;
        }
      }
    }
  } catch (e) {}

  return 'http://127.0.0.1:8787';
}

function showPairing() {
  const tunnelBase = getTunnelUrl();
  const chatEndpoint = `${tunnelBase}/api/chat`;
  const token = config ? config.loadToken() : (process.env.TOKEN || process.env.REMOTE_TOKEN || '');
  if (!token) {
    console.log('\n✘ Tidak menemukan token. Jalankan server bridge sekali agar token dibuat.\n');
    return;
  }

  const payload = {
    agy: 'v1',
    url: chatEndpoint,
    token: token,
    engine: 'antigravity',
    name: `Remote Server (${os.hostname()})`
  };

  const payloadString = JSON.stringify(payload);

  console.log('\n======================================================');
  console.log('       📱 ANTIGRAVITY & CODEX REMOTE PAIRING          ');
  console.log('======================================================\n');
  console.log('1. Buka aplikasi AI CLI Remote di HP Android Anda');
  console.log('2. Tekan tombol [ 📷 Scan QR ] dan arahkan ke kode ini:\n');

  if (qrcode) {
    qrcode.generate(payloadString, { small: true });
  } else {
    console.log(`[DATA PAIRING]:\n${payloadString}\n`);
  }

  console.log('------------------------------------------------------');
  console.log(`🔗 Endpoint URL : ${chatEndpoint}`);
  console.log(`🔑 Secret Token : ${token}`);
  console.log('------------------------------------------------------');
  console.log('💡 Perintah Bantuan:');
  console.log('   codex-remote         -> Tampilkan QR Code Pairing ini lagi');
  console.log('   codex-remote status  -> Cek status aktif server');
  console.log('   codex-remote logs    -> Lihat live logs server & aktivitas');
  console.log('   codex-remote restart -> Restart server & cloudflare tunnel');
  console.log('   codex-remote rotate  -> Ganti token dengan yang baru (perlu pairing ulang)\n');
}

function showStatus() {
  console.log('\n🔍 Memeriksa Status Server Remote...\n');
  try {
    execSync('systemctl status codex-bridge --no-pager', { stdio: 'inherit' });
    console.log('\n------------------------------------------------------\n');
    execSync('systemctl status codex-tunnel --no-pager', { stdio: 'inherit' });
  } catch (e) {
    console.log('Periksa proses lokal:');
    try {
      execSync('ps aux | grep -E "node.*server.js|cloudflared" | grep -v grep', { stdio: 'inherit' });
    } catch (err) {}
  }
  console.log(`\n🔗 Public URL: ${getTunnelUrl()}/api/chat\n`);
}

function showLogs() {
  console.log('\n📋 Menampilkan Live Logs (Ctrl+C untuk keluar)...\n');
  try {
    spawn('journalctl', ['-u', 'codex-bridge', '-u', 'codex-tunnel', '-f'], { stdio: 'inherit' });
  } catch (e) {
    console.log('Gagal membuka journalctl.');
  }
}

function restartServices() {
  console.log('\n🔄 Me-restart Server Bridge & Tunnel...\n');
  try {
    execSync('sudo systemctl restart codex-bridge codex-tunnel', { stdio: 'inherit' });
    console.log('✓ Server berhasil direstart!');
    setTimeout(showPairing, 2000);
  } catch (e) {
    console.log('Gagal me-restart systemd service (pastikan memiliki izin sudo).');
  }
}

function rotateToken() {
  if (!config) {
    console.log('\n✘ Modul config tidak ditemukan.\n');
    return;
  }
  const fresh = config.rotateToken();
  console.log(`\n✔ Token baru dibuat: ${fresh}`);
  console.log('  Restart server lalu pairing ulang dari HP.\n');
  try {
    execSync('sudo systemctl restart codex-bridge', { stdio: 'inherit' });
    setTimeout(showPairing, 1500);
  } catch (e) {
    console.log('  Restart manual: sudo systemctl restart codex-bridge');
  }
}

switch (action) {
  case 'rotate':
  case 'rotate-token':
    rotateToken();
    break;
  case 'status':
    showStatus();
    break;
  case 'logs':
  case 'log':
    showLogs();
    break;
  case 'restart':
    restartServices();
    break;
  case 'pair':
  case 'qr':
  default:
    showPairing();
    break;
}
