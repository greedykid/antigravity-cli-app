#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const qrcode = require('qrcode-terminal');

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
const token = 'codex-remote-token-2026';

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
