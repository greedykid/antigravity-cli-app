const http = require('http');
const { spawn } = require('child_process');

const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.BRIDGE_HOST || '127.0.0.1';
const TOKEN = process.env.REMOTE_TOKEN || '';
const CODEX_BIN = process.env.CODEX_BIN || 'codex';
const AGY_BIN = process.env.AGY_BIN || 'agy';
const WORKDIR = process.env.CODEX_WORKDIR || process.cwd();

function send(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(data),
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS'
  });
  res.end(data);
}

function authorized(req) {
  return !TOKEN || req.headers.authorization === `Bearer ${TOKEN}`;
}

function runCodex(prompt) {
  return new Promise((resolve, reject) => {
    const child = spawn(CODEX_BIN, ['exec', '--skip-git-repo-check', prompt], {
      cwd: WORKDIR,
      env: process.env
    });
    let output = '';
    let error = '';
    child.stdout.on('data', chunk => { output += chunk.toString(); });
    child.stderr.on('data', chunk => { error += chunk.toString(); });
    const timer = setTimeout(() => {
      child.kill('SIGTERM');
      reject(new Error('Codex timed out after 5 minutes'));
    }, 300000);
    child.on('error', err => { clearTimeout(timer); reject(err); });
    child.on('close', code => {
      clearTimeout(timer);
      if (code === 0) resolve(output.trim());
      else reject(new Error((error || output || `Codex exited with code ${code}`).trim()));
    });
  });
}

function runAgy(prompt) {
  return new Promise((resolve, reject) => {
    const extraPath = ':/home/ubuntu/.local/bin:/usr/local/bin';
    const env = Object.assign({}, process.env, {
      PATH: (process.env.PATH || '') + extraPath
    });
    const child = spawn(AGY_BIN, ['-p', prompt, '--dangerously-skip-permissions'], {
      cwd: WORKDIR,
      env
    });
    let output = '';
    let error = '';
    child.stdout.on('data', chunk => { output += chunk.toString(); });
    child.stderr.on('data', chunk => { error += chunk.toString(); });
    const timer = setTimeout(() => {
      child.kill('SIGTERM');
      reject(new Error('Antigravity CLI timed out after 5 minutes'));
    }, 300000);
    child.on('error', err => { clearTimeout(timer); reject(err); });
    child.on('close', code => {
      clearTimeout(timer);
      if (code === 0) resolve(output.trim());
      else reject(new Error((error || output || `Antigravity CLI exited with code ${code}`).trim()));
    });
  });
}

const server = http.createServer((req, res) => {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Authorization, Content-Type',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS'
    });
    return res.end();
  }

  if (req.method === 'GET' && (req.url === '/health' || req.url === '/api/health')) {
    return send(res, 200, { ok: true, engines: ['antigravity', 'codex'] });
  }

  if (req.method !== 'POST' || req.url !== '/api/chat') {
    return send(res, 404, { error: 'Not found' });
  }

  if (!authorized(req)) {
    return send(res, 401, { error: 'Unauthorized' });
  }

  let raw = '';
  req.on('data', chunk => {
    raw += chunk;
    if (raw.length > 200000) req.destroy();
  });

  req.on('end', async () => {
    try {
      const payload = JSON.parse(raw);
      const prompt = payload.prompt;
      const engine = (payload.engine || payload.cli || 'antigravity').toLowerCase();

      if (typeof prompt !== 'string' || !prompt.trim()) {
        return send(res, 400, { error: 'prompt is required' });
      }

      let response;
      if (engine === 'codex') {
        response = await runCodex(prompt.trim());
      } else {
        response = await runAgy(prompt.trim());
      }

      send(res, 200, { response, engine: engine === 'codex' ? 'codex' : 'antigravity' });
    } catch (error) {
      send(res, 500, { error: error.message || 'Internal server error' });
    }
  });
});

server.listen(PORT, HOST, () => {
  console.log(`AI CLI bridge listening on ${HOST}:${PORT} (engines: Antigravity, Codex)`);
});
