#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const qrcode = require('qrcode-terminal');

let config;
for (const candidate of ['../bridge/config.js', './config.js']) {
  try { config = require(path.join(__dirname, candidate)); break; } catch (e) {}
}

function getTunnelUrl() {
  try {
    const journal = execSync('journalctl -u codex-tunnel -n 150 --no-pager', { encoding: 'utf8' });
    const matches = journal.match(/https:\/\/[a-zA-Z0-9.-]+\.trycloudflare\.com/g);
    if (matches && matches.length > 0) {
      return matches[matches.length - 1];
    }
  } catch (e) {}
  return 'https://globe-extreme-meetup-accent.trycloudflare.com';
}

const tunnelBase = getTunnelUrl();
const chatEndpoint = `${tunnelBase}/api/chat`;
const token = config ? config.loadToken() : (process.env.TOKEN || process.env.REMOTE_TOKEN || '');
if (!token) {
  console.log('\n✘ Token belum ada. Jalankan server bridge sekali agar token dibuat.\n');
  process.exit(1);
}

const payload = {
  agy: 'v1',
  url: chatEndpoint,
  token: token,
  engine: 'antigravity',
  name: 'Antigravity Remote VPS'
};

const payloadString = JSON.stringify(payload);

console.log('\n======================================================');
console.log('       📱 ANTIGRAVITY & CODEX REMOTE PAIRING          ');
console.log('======================================================\n');
console.log('Buka aplikasi di HP Android dan tap tombol: [ 📷 Scan QR ]\n');

qrcode.generate(payloadString, { small: true });

console.log('\n------------------------------------------------------');
console.log(`🔗 Endpoint URL : ${chatEndpoint}`);
console.log(`🔑 Secret Token : ${token}`);
console.log('------------------------------------------------------\n');
