// Tests for the scoped process kill helper. The bridge exports the helper
// only through /api/session/control, so we drive it via that route and stub
// the child handle by mocking the runningChildren map through a child we
// spawn just for this test.

const assert = require("node:assert/strict");
const http = require("node:http");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawn } = require("node:child_process");
const test = require("node:test");

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

function startBridge(home, workdir, port) {
  return spawn(process.execPath, ["server.js"], {
    cwd: path.join(__dirname, ".."),
    env: { ...process.env, HOME: home, WORKDIR: workdir, PORT: String(port), TOKEN: "test-token" },
    stdio: ["ignore", "inherit", "inherit"]
  });
}

async function waitUntilReady(port) {
  for (let attempt = 0; attempt < 50; attempt++) {
    try {
      const r = await request(port, "/health", "GET", "");
      if (r.body && r.body.features) return;
    } catch { /* retry */ }
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  throw new Error("bridge did not start");
}

// Killing requires approval; the bridge issues an approval token in the 403
// response which we pass straight back in.
async function postWithApproval(port, pathname, body) {
  const first = await request(port, pathname, "POST", "test-token", body);
  if (first.status === 200) return first;
  if (first.status === 403 && first.body && first.body.approvalToken) {
    return request(port, pathname, "POST", "test-token",
      Object.assign({}, body, { approvalToken: first.body.approvalToken }));
  }
  return first;
}

test("scoped kill refuses blanket pkill without confirm=all", async (t) => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), "codex-kill-home-"));
  const workdir = fs.mkdtempSync(path.join(os.tmpdir(), "codex-kill-work-"));
  const port = 18797;
  const child = startBridge(home, workdir, port);
  t.after(() => {
    child.kill("SIGTERM");
    fs.rmSync(home, { recursive: true, force: true });
    fs.rmSync(workdir, { recursive: true, force: true });
  });

  await waitUntilReady(port);

  const r = await postWithApproval(port, "/api/session/control", {
    action: "stop",
    engine: "codex"
    // no jobId / no conversationId / no confirm=all
  });
  assert.equal(r.status, 200);
  assert.equal(r.body.method, "noop");
  assert.equal(r.body.ok, false);
  assert.equal(r.body.tried.blanket, false);
});

test("scoped kill with confirm=all performs the blanket pkill", async (t) => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), "codex-kill2-home-"));
  const workdir = fs.mkdtempSync(path.join(os.tmpdir(), "codex-kill2-work-"));
  const port = 18798;
  const child = startBridge(home, workdir, port);
  t.after(() => {
    child.kill("SIGTERM");
    fs.rmSync(home, { recursive: true, force: true });
    fs.rmSync(workdir, { recursive: true, force: true });
  });

  await waitUntilReady(port);

  const r = await postWithApproval(port, "/api/session/control", {
    action: "stop",
    engine: "codex",
    confirm: "all"
  });
  assert.equal(r.status, 200);
  assert.equal(r.body.method, "blanket");
  assert.equal(r.body.tried.blanket, true);
});