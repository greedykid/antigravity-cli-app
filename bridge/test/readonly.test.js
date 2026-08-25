const assert = require("node:assert/strict");
const { execFileSync, spawn } = require("node:child_process");
const fs = require("node:fs");
const http = require("node:http");
const os = require("node:os");
const path = require("node:path");
const test = require("node:test");

function request(port, pathname, method, token, body, deviceId) {
  return new Promise((resolve, reject) => {
    const req = http.request({
      host: "127.0.0.1", port, path: pathname, method,
      headers: Object.assign(
        { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
        deviceId ? { "X-Codex-Device-Id": deviceId } : {}
      )
    }, res => {
      let raw = "";
      res.setEncoding("utf8");
      res.on("data", chunk => raw += chunk);
      res.on("end", () => resolve({ status: res.statusCode, body: JSON.parse(raw) }));
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
      const result = await request(port, "/health", "GET", "");
      if (result.body && result.body.features && result.body.features.includes("device_management")) return;
    } catch { /* retry */ }
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  throw new Error("bridge did not start");
}

test("readonly mode blocks chat execution", async () => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), "codex-readonly-home-"));
  const workdir = fs.mkdtempSync(path.join(os.tmpdir(), "codex-readonly-work-"));
  const port = 18790;
  const child = startBridge(home, workdir, port);
  child.on("exit", (code, signal) => {
    if (signal !== "SIGTERM") assert.fail(`bridge exited early with code ${code}`);
  });
  try {
    await waitUntilReady(port);
    await request(port, "/api/settings", "POST", "test-token", {
      sandboxMode: "readonly", notifyOnFinish: false
    });
    const result = await request(port, "/api/chat", "POST", "test-token", { prompt: "hello" });
    assert.equal(result.status, 403);
    assert.match(result.body.error, /Hanya Baca/);
  } finally {
    child.kill("SIGTERM");
    fs.rmSync(home, { recursive: true, force: true });
    fs.rmSync(workdir, { recursive: true, force: true });
  }
});

test("devices can be registered and revoked", async (t) => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), "codex-devices-home-"));
  const workdir = fs.mkdtempSync(path.join(os.tmpdir(), "codex-devices-work-"));
  const port = 18792;
  const child = startBridge(home, workdir, port);
  t.after(() => {
    child.kill("SIGTERM");
    fs.rmSync(home, { recursive: true, force: true });
    fs.rmSync(workdir, { recursive: true, force: true });
  });

  await waitUntilReady(port);
  await request(port, "/api/jobs", "GET", "test-token", null, "phone-one");
  await request(port, "/api/jobs", "GET", "test-token", null, "phone-two");

  const list = await request(port, "/api/devices", "GET", "test-token", null, "admin");
  assert.equal(list.status, 200);
  assert.deepEqual(
    list.body.devices.map(d => d.id).filter(id => id.startsWith("phone-")).sort(),
    ["phone-one", "phone-two"]
  );

  const revoked = await request(port, "/api/devices/revoke", "POST", "test-token",
    { id: "phone-two", revoked: true }, "admin");
  assert.equal(revoked.status, 200);
  assert.equal(revoked.body.device.revoked, true);

  const denied = await request(port, "/api/jobs", "GET", "test-token", null, "phone-two");
  assert.equal(denied.status, 403);
  assert.equal(denied.body.code, "DEVICE_REVOKED");
});

test("git push requires and consumes a one-time approval", async (t) => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), "codex-approval-home-"));
  const repo = path.join(home, "repo");
  fs.mkdirSync(repo);
  execFileSync("git", ["init", "--initial-branch=main"], { cwd: repo });
  execFileSync("git", ["config", "user.email", "test@example.com"], { cwd: repo });
  execFileSync("git", ["config", "user.name", "Test"], { cwd: repo });
  fs.writeFileSync(path.join(repo, "README.md"), "# test\n");
  execFileSync("git", ["add", "."], { cwd: repo });
  execFileSync("git", ["commit", "-m", "init"], { cwd: repo });
  const bare = path.join(home, "remote.git");
  execFileSync("git", ["init", "--bare", bare]);
  execFileSync("git", ["remote", "add", "origin", bare], { cwd: repo });

  const port = 18791;
  const child = startBridge(home, repo, port);
  t.after(() => {
    child.kill("SIGTERM");
    fs.rmSync(home, { recursive: true, force: true });
  });

  await waitUntilReady(port);
  const first = await request(port, "/api/git/push", "POST", "test-token", {});
  assert.equal(first.status, 403);
  assert.equal(first.body.code, "APPROVAL_REQUIRED");
  assert.ok(first.body.approvalToken);

  const second = await request(port, "/api/git/push", "POST", "test-token", {
    approvalToken: first.body.approvalToken
  });
  if (second.status !== 200) {
    assert.match(second.body.error, /no upstream branch/);
  }

  const replay = await request(port, "/api/git/push", "POST", "test-token", {
    approvalToken: first.body.approvalToken
  });
  assert.equal(replay.status, 403);
});
