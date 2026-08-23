const { test } = require("node:test");
const assert = require("node:assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

process.env.HOME = fs.mkdtempSync(path.join(os.tmpdir(), "settings-home-"));
const settings = require("../settings");

test("defaults to full access with notifications on", () => {
  const loaded = settings.load();
  assert.equal(loaded.sandboxMode, "full");
  assert.equal(loaded.notifyOnFinish, true);
});

test("saves and reloads a sandbox mode", () => {
  settings.save({ sandboxMode: "readonly" });
  assert.equal(settings.load().sandboxMode, "readonly");
});

test("an unknown sandbox mode falls back instead of being stored", () => {
  settings.save({ sandboxMode: "obviously-not-a-mode" });
  assert.equal(settings.load().sandboxMode, "full");
});

test("each mode maps to the flags codex actually understands", () => {
  settings.save({ sandboxMode: "readonly" });
  assert.deepEqual(settings.codexSandboxArgs(), ["--sandbox", "read-only"]);

  settings.save({ sandboxMode: "workspace" });
  assert.deepEqual(settings.codexSandboxArgs(), ["--sandbox", "workspace-write"]);

  settings.save({ sandboxMode: "full" });
  assert.deepEqual(settings.codexSandboxArgs(), ["--dangerously-bypass-approvals-and-sandbox"]);
});

test("callers cannot mutate the flag list held by the module", () => {
  settings.save({ sandboxMode: "readonly" });
  const args = settings.codexSandboxArgs();
  args.push("--dangerously-bypass-approvals-and-sandbox");
  assert.deepEqual(settings.codexSandboxArgs(), ["--sandbox", "read-only"]);
});

test("notifyOnFinish is coerced to a boolean", () => {
  settings.save({ notifyOnFinish: "yes" });
  assert.strictEqual(settings.load().notifyOnFinish, true);
  settings.save({ notifyOnFinish: 0 });
  assert.strictEqual(settings.load().notifyOnFinish, false);
});

test("a corrupt settings file falls back to defaults", () => {
  fs.writeFileSync(settings.SETTINGS_FILE, "{ this is not json");
  assert.equal(settings.load().sandboxMode, "full");
});
