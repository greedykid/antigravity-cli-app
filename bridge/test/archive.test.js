const { test, beforeEach } = require("node:test");
const assert = require("node:assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

process.env.HOME = fs.mkdtempSync(path.join(os.tmpdir(), "archive-home-"));
const archive = require("../archive");

const SESSIONS = [
  { conversationId: "a", title: "Satu", engine: "codex" },
  { conversationId: "b", title: "Dua", engine: "codex" },
  { conversationId: "c", title: "Tiga", engine: "antigravity" }
];

beforeEach(() => archive.reset());

test("nothing is archived to begin with", () => {
  assert.equal(archive.count(), 0);
  assert.equal(archive.apply(SESSIONS, false).length, 3);
});

test("archiving hides a session from the default list", () => {
  archive.setArchived("b", true);
  const visible = archive.apply(SESSIONS, false);
  assert.deepEqual(visible.map(s => s.conversationId), ["a", "c"]);
});

test("archived sessions are still returned when asked for, and tagged", () => {
  archive.setArchived("b", true);
  const all = archive.apply(SESSIONS, true);
  assert.equal(all.length, 3);
  assert.equal(all.find(s => s.conversationId === "b").archived, true);
  assert.equal(all.find(s => s.conversationId === "a").archived, false);
});

test("unarchiving brings it back", () => {
  archive.setArchived("b", true);
  archive.setArchived("b", false);
  assert.equal(archive.count(), 0);
  assert.equal(archive.apply(SESSIONS, false).length, 3);
});

test("archiving is idempotent", () => {
  archive.setArchived("b", true);
  archive.setArchived("b", true);
  assert.equal(archive.count(), 1);
});

test("unarchiving something that was never archived is harmless", () => {
  const res = archive.setArchived("zzz", false);
  assert.equal(res.ok, true);
  assert.equal(archive.count(), 0);
});

test("an empty id is refused rather than stored", () => {
  assert.equal(archive.setArchived("", true).ok, false);
  assert.equal(archive.setArchived(null, true).ok, false);
  assert.equal(archive.count(), 0);
});

test("the record survives a reload", () => {
  archive.setArchived("a", true);
  delete require.cache[require.resolve("../archive")];
  const reloaded = require("../archive");
  assert.equal(reloaded.isArchived("a"), true);
  assert.equal(reloaded.isArchived("b"), false);
});

test("a corrupt archive file falls back to empty instead of throwing", () => {
  fs.mkdirSync(path.dirname(archive.ARCHIVE_FILE), { recursive: true });
  fs.writeFileSync(archive.ARCHIVE_FILE, "{ not json");
  assert.equal(archive.count(), 0);
  assert.equal(archive.apply(SESSIONS, false).length, 3);
});

// The whole point: transcripts stay where the CLIs put them.
test("archiving records only an id and a timestamp", () => {
  archive.setArchived("a", true);
  const stored = archive.load();
  assert.deepEqual(Object.keys(stored.entries), ["a"]);
  assert.ok(typeof stored.entries.a.at === "number");
});

test("apply tolerates a missing or empty session list", () => {
  assert.deepEqual(archive.apply(null, false), []);
  assert.deepEqual(archive.apply([], true), []);
});
