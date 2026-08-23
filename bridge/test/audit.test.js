const { test, beforeEach } = require("node:test");
const assert = require("node:assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

process.env.HOME = fs.mkdtempSync(path.join(os.tmpdir(), "audit-home-"));
const audit = require("../audit");

beforeEach(() => {
  audit.resetLimits();
  try { fs.unlinkSync(audit.LOG_FILE); } catch (e) {}
});

test("logs an event with a timestamp and reads it back", () => {
  audit.log("chat.started", { jobId: "abc", engine: "codex" });
  const entries = audit.read();
  assert.equal(entries.length, 1);
  assert.equal(entries[0].event, "chat.started");
  assert.equal(entries[0].jobId, "abc");
  assert.ok(!Number.isNaN(Date.parse(entries[0].at)));
});

test("returns newest first and honours the limit", () => {
  for (let i = 0; i < 10; i++) audit.log("e" + i, { i });
  const entries = audit.read(3);
  assert.equal(entries.length, 3);
  assert.equal(entries[0].event, "e9");
});

test("the log is owner-readable only", () => {
  audit.log("secret.thing", {});
  assert.equal(fs.statSync(audit.LOG_FILE).mode & 0o777, 0o600);
});

test("reading with no log yet returns empty, not an error", () => {
  assert.deepEqual(audit.read(), []);
});

test("a corrupt line is surfaced rather than dropping the whole log", () => {
  audit.log("good.event", {});
  fs.appendFileSync(audit.LOG_FILE, "{not json\n");
  const entries = audit.read();
  assert.equal(entries.length, 2);
  assert.ok(entries.some(e => e.raw));
  assert.ok(entries.some(e => e.event === "good.event"));
});

test("rate limit allows up to the cap then refuses", () => {
  for (let i = 0; i < 3; i++) {
    assert.equal(audit.rateLimit("test", 3, 60000).allowed, true, `call ${i} should pass`);
  }
  const blocked = audit.rateLimit("test", 3, 60000);
  assert.equal(blocked.allowed, false);
  assert.ok(blocked.retryAfterMs > 0);
});

test("separate buckets do not share a budget", () => {
  audit.rateLimit("a", 1, 60000);
  assert.equal(audit.rateLimit("a", 1, 60000).allowed, false);
  assert.equal(audit.rateLimit("b", 1, 60000).allowed, true);
});

test("the window reopens once it has elapsed", async () => {
  assert.equal(audit.rateLimit("short", 1, 30).allowed, true);
  assert.equal(audit.rateLimit("short", 1, 30).allowed, false);
  await new Promise(r => setTimeout(r, 45));
  assert.equal(audit.rateLimit("short", 1, 30).allowed, true);
});
