const assert = require("node:assert/strict");
const http = require("node:http");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawn } = require("node:child_process");
const test = require("node:test");

// These tests boot the bridge with PATH stripped of any real CLI binary
// (codex/opencode/command-code/agy), so the engine-missing branches fire.
function request(port, pathname, method, token, body) {
  return new Promise((resolve, reject) => {
    const req = http.request({
      host: "127.0.0.1", port, path: pathname, method,
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json"
      }
    }, res => {
      let raw = "";
      res.setEncoding("utf8");
      res.on("data", chunk => raw += chunk);
      res.on("end", () => {
        try { resolve({ status: res.statusCode, body: JSON.parse(raw) }); }
        catch { resolve({ status: res.statusCode, body: raw }); }
      });
    });
    req.on("error", reject);
    req.end(body ? JSON.stringify(body) : undefined);
  });
}

function openSse(port, token) {
  return new Promise((resolve, reject) => {
    const req = http.request({
      host: "127.0.0.1", port, path: "/api/events", method: "GET",
      headers: { Authorization: `Bearer ${token}`, Accept: "text/event-stream" }
    }, res => {
      const events = [];
      res.setEncoding("utf8");
      res.on("data", chunk => events.push(chunk));
      resolve({ req, res, events });
    });
    req.on("error", reject);
    req.end();
  });
}

function waitForEvent(res, name, predicate, timeoutMs) {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + (timeoutMs || 8000);
    let buffer = "";
    const onData = chunk => {
      buffer += chunk;
      const blocks = buffer.split("\n\n");
      buffer = blocks.pop();
      for (const block of blocks) {
        const lines = block.split("\n");
        const evName = (lines.find(l => l.startsWith("event:")) || "").slice(6).trim();
        const dataLine = lines.find(l => l.startsWith("data:"));
        if (!dataLine) continue;
        if (evName === name) {
          try {
            const parsed = JSON.parse(dataLine.slice(5).trim());
            if (!predicate || predicate(parsed)) {
              cleanup();
              resolve(parsed);
              return;
            }
          } catch {}
        }
      }
      if (Date.now() > deadline) {
        cleanup();
        reject(new Error("timeout waiting for " + name));
      }
    };
    function cleanup() {
      res.removeListener("data", onData);
    }
    res.on("data", onData);
  });
}

function startBridgeWithEmptyPath(home, workdir, port) {
  return spawn(process.execPath, ["server.js"], {
    cwd: path.join(__dirname, ".."),
    env: {
      ...process.env,
      HOME: home,
      WORKDIR: workdir,
      PORT: String(port),
      TOKEN: "test-token",
      // Force every CLI probe to fail.
      PATH: "/var/empty/does-not-exist",
      CODEX_BIN: "/var/empty/no-codex",
      AGY_BIN: "/var/empty/no-agy",
      COMMAND_CODE_BIN: "/var/empty/no-command-code",
      OPENCODE_BIN: "/var/empty/no-opencode"
    },
    stdio: ["ignore", "inherit", "inherit"]
  });
}

async function waitUntilReady(port) {
  for (let attempt = 0; attempt < 50; attempt++) {
    try {
      const result = await request(port, "/health", "GET", "");
      if (result.body && result.body.features) return;
    } catch { /* retry */ }
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  throw new Error("bridge did not start");
}

test("engine-missing failure surfaces as task.finished ok:false", async (t) => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), "codex-fail-home-"));
  const workdir = fs.mkdtempSync(path.join(os.tmpdir(), "codex-fail-work-"));
  const port = 18795;
  const child = startBridgeWithEmptyPath(home, workdir, port);
  t.after(() => {
    child.kill("SIGTERM");
    fs.rmSync(home, { recursive: true, force: true });
    fs.rmSync(workdir, { recursive: true, force: true });
  });

  await waitUntilReady(port);

  // Subscribe to SSE so we observe the failure broadcast.
  const stream = await openSse(port, "test-token");
  t.after(() => {
    try { stream.req.destroy(); } catch {}
  });

  const accepted = await request(port, "/api/chat", "POST", "test-token",
    { prompt: "hello", engine: "opencode", async: true });
  assert.equal(accepted.status, 202);
  assert.ok(accepted.body.jobId);

  const finished = await waitForEvent(stream.res, "task.finished",
    ev => ev.jobId === accepted.body.jobId && ev.ok === false,
    8000);
  assert.match(finished.error, /OpenCode CLI Tidak Ditemukan/);
});

test("commandcode-missing failure surfaces as task.finished ok:false", async (t) => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), "codex-fail-cc-home-"));
  const workdir = fs.mkdtempSync(path.join(os.tmpdir(), "codex-fail-cc-work-"));
  const port = 18796;
  const child = startBridgeWithEmptyPath(home, workdir, port);
  t.after(() => {
    child.kill("SIGTERM");
    fs.rmSync(home, { recursive: true, force: true });
    fs.rmSync(workdir, { recursive: true, force: true });
  });

  await waitUntilReady(port);

  const stream = await openSse(port, "test-token");
  t.after(() => { try { stream.req.destroy(); } catch {} });

  const accepted = await request(port, "/api/chat", "POST", "test-token",
    { prompt: "hello", engine: "commandcode", async: true });
  assert.equal(accepted.status, 202);

  const finished = await waitForEvent(stream.res, "task.finished",
    ev => ev.jobId === accepted.body.jobId && ev.ok === false,
    8000);
  assert.match(finished.error, /Command Code CLI Tidak Ditemukan/);
});