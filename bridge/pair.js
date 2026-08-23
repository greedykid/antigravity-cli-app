const qrcode = require('qrcode-terminal');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execSync } = require('child_process');
const config = require('./config');

// The token must come from the same place the server reads it, otherwise the
// QR pairs the phone with a secret the server rejects.
const token = config.loadToken();

function detectTunnelUrl() {
  const candidates = [
    path.join(os.homedir(), 'tunnel.log'),
    path.join(config.CONFIG_DIR, 'tunnel.log')
  ];
  for (const file of candidates) {
    try {
      if (!fs.existsSync(file)) continue;
      const found = fs.readFileSync(file, 'utf8').match(/https:\/\/[a-zA-Z0-9.-]+\.trycloudflare\.com/g);
      if (found && found.length) return found[found.length - 1];
    } catch (e) {}
  }
  try {
    const journal = execSync('journalctl -u codex-tunnel -n 200 --no-pager 2>/dev/null', { encoding: 'utf8' });
    const found = journal.match(/https:\/\/[a-zA-Z0-9.-]+\.trycloudflare\.com/g);
    if (found && found.length) return found[found.length - 1];
  } catch (e) {}
  return null;
}

const detected = detectTunnelUrl();
const url = process.env.BRIDGE_URL
  || (detected ? detected + '/api/chat' : `http://127.0.0.1:${config.port()}/api/chat`);
const engine = process.env.ENGINE || process.env.DEFAULT_ENGINE || 'antigravity';

const payload = JSON.stringify({ agy: 'v1', url, token, engine, name: `Remote Server (${os.hostname()})` });

console.log('\n======================================================');
console.log('       📱 ANTIGRAVITY REMOTE - QR CODE PAIRING        ');
console.log('======================================================\n');
console.log('Scan the QR code below using the Android App:\n');

qrcode.generate(payload, { small: true });

console.log('\n------------------------------------------------------');
console.log(`🔗 Endpoint URL : ${url}`);
console.log(`🔑 Secret Token : ${token}`);
console.log('------------------------------------------------------');
console.log('Or paste this link into the Android App:');
console.log(`agy://connect?url=${encodeURIComponent(url)}&token=${encodeURIComponent(token)}&engine=${encodeURIComponent(engine)}`);
console.log('------------------------------------------------------\n');
