const assert = require("node:assert/strict");
const { spawn } = require("node:child_process");
const fs = require("node:fs");
const http = require("node:http");
const os = require("node:os");
const path = require("node:path");
const test = require("node:test");

function request(port, pathname, method, token) {
  return new Promise((resolve, reject) => {
    const req = http.request({
      host: "127.0.0.1", port, path: pathname, method,
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }
    }, res => {
      let raw = "";
      res.setEncoding("utf8");
      res.on("data", chunk => raw += chunk);
      res.on("end", () => {
        try {
          resolve({ status: res.statusCode, body: JSON.parse(raw) });
        } catch (e) {
          resolve({ status: res.statusCode, raw });
        }
      });
    });
    req.on("error", reject);
    req.end();
  });
}

test("/api/health/operations returns operational health stats", async () => {
  const home = fs.mkdtempSync(path.join(os.tmpdir(), "codex-ops-home-"));
  const work = fs.mkdtempSync(path.join(os.tmpdir(), "codex-ops-work-"));
  const port = 18795;
  const bridge = spawn(process.execPath, ["server.js"], {
    cwd: path.join(__dirname, ".."),
    env: { ...process.env, HOME: home, WORKDIR: work, PORT: String(port), TOKEN: "test-token" },
    stdio: ["ignore", "inherit", "inherit"]
  });

  try {
    for (let attempt = 0; attempt < 50; attempt++) {
      try {
        const result = await request(port, "/health", "GET", "");
        if (result.body && result.body.ok) break;
      } catch {}
      await new Promise(r => setTimeout(r, 100));
    }

    const ops = await request(port, "/api/health/operations", "GET", "test-token");
    assert.equal(ops.status, 200);
    assert.equal(ops.body.ok, true);
    assert.ok(ops.body.health, "health should exist");
    assert.ok(ops.body.health.engines, "engines should exist");
    assert.ok(ops.body.health.filesystem, "filesystem should exist");
    assert.ok(ops.body.health.server, "server stats should exist");
    assert.ok(typeof ops.body.health.runningJobs === "number");
  } finally {
    bridge.kill("SIGTERM");
  }
});
