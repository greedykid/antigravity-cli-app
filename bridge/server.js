const http = require('http');
const { spawn } = require('child_process');

const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.BRIDGE_HOST || '127.0.0.1';
const TOKEN = process.env.REMOTE_TOKEN || '';
const CODEX_BIN = process.env.CODEX_BIN || 'codex';
const WORKDIR = process.env.CODEX_WORKDIR || process.cwd();

function send(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) });
  res.end(data);
}

function authorized(req) {
  return !TOKEN || req.headers.authorization === `Bearer ${TOKEN}`;
}

function runCodex(prompt) {
  return new Promise((resolve, reject) => {
    const child = spawn(CODEX_BIN, ['exec', '--skip-git-repo-check', prompt], { cwd: WORKDIR, env: process.env });
    let output = ''; let error = '';
    child.stdout.on('data', chunk => { output += chunk.toString(); });
    child.stderr.on('data', chunk => { error += chunk.toString(); });
    const timer = setTimeout(() => { child.kill('SIGTERM'); reject(new Error('Codex timed out after 5 minutes')); }, 300000);
    child.on('error', err => { clearTimeout(timer); reject(err); });
    child.on('close', code => { clearTimeout(timer); if (code === 0) resolve(output.trim()); else reject(new Error((error || output || `Codex exited with code ${code}`).trim())); });
  });
}

const server = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') return send(res, 200, { ok: true });
  if (req.method !== 'POST' || req.url !== '/api/chat') return send(res, 404, { error: 'Not found' });
  if (!authorized(req)) return send(res, 401, { error: 'Unauthorized' });
  let raw = ''; req.on('data', chunk => { raw += chunk; if (raw.length > 100000) req.destroy(); });
  req.on('end', async () => {
    try {
      const prompt = JSON.parse(raw).prompt;
      if (typeof prompt !== 'string' || !prompt.trim()) return send(res, 400, { error: 'prompt is required' });
      const response = await runCodex(prompt.trim()); send(res, 200, { response });
    } catch (error) { send(res, 500, { error: error.message }); }
  });
});
server.listen(PORT, HOST, () => console.log(`Codex bridge listening on ${HOST}:${PORT}`));
