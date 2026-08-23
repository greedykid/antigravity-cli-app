const qrcode = require('qrcode-terminal');

const token = process.env.REMOTE_TOKEN || 'codex-remote-token-2026';
const url = process.env.BRIDGE_URL || 'https://globe-extreme-meetup-accent.trycloudflare.com/api/chat';
const engine = process.env.ENGINE || 'antigravity';

const payload = JSON.stringify({
  url,
  token,
  engine
});

console.log('\n======================================================');
console.log('       📱 ANTIGRAVITY REMOTE - QR CODE PAIRING        ');
console.log('======================================================\n');
console.log('Scan the QR code below using the Android App:\n');

qrcode.generate(payload, { small: true });

console.log('\n------------------------------------------------------');
console.log('🔗 Or copy & paste this link into the Android App:');
console.log(`agy://connect?url=${encodeURIComponent(url)}&token=${encodeURIComponent(token)}&engine=${encodeURIComponent(engine)}`);
console.log('------------------------------------------------------\n');
