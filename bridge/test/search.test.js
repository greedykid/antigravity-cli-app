const { test, beforeEach } = require("node:test");
const assert = require("node:assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

process.env.HOME = fs.mkdtempSync(path.join(os.tmpdir(), "search-home-"));
const search = require("../search");

const scratch = fs.mkdtempSync(path.join(os.tmpdir(), "search-src-"));

function sourceFile(name, contents) {
  const file = path.join(scratch, name);
  fs.writeFileSync(file, contents);
  return file;
}

beforeEach(() => search.reset());

test("finds a match inside a transcript and returns a snippet", () => {
  const src = sourceFile("a.jsonl", "x");
  search.sync(
    { conversationId: "s1", title: "Sesi satu", engine: "codex", timestamp: 2 },
    [src],
    () => [{ role: "user", content: "tolong perbaiki bug pada porcelain parser" }]
  );
  const results = search.query("porcelain", 10);
  assert.equal(results.length, 1);
  assert.equal(results[0].conversationId, "s1");
  assert.equal(results[0].matchIn, "message");
  assert.match(results[0].snippet, /porcelain/);
});

test("matches the title without needing a body hit", () => {
  search.sync({ conversationId: "s2", title: "Deploy ke VPS", engine: "codex", timestamp: 1 },
    [sourceFile("b.jsonl", "x")], () => []);
  const results = search.query("deploy", 10);
  assert.equal(results[0].matchIn, "title");
});

test("search is case-insensitive", () => {
  search.sync({ conversationId: "s3", title: "T", engine: "codex", timestamp: 1 },
    [sourceFile("c.jsonl", "x")], () => [{ role: "user", content: "MEMBACA Token Rahasia" }]);
  assert.equal(search.query("token rahasia", 10).length, 1);
});

test("an empty query returns nothing rather than everything", () => {
  search.sync({ conversationId: "s4", title: "T", engine: "codex", timestamp: 1 },
    [sourceFile("d.jsonl", "x")], () => [{ role: "user", content: "isi" }]);
  assert.deepEqual(search.query("   ", 10), []);
});

test("the limit is respected", () => {
  for (let i = 0; i < 8; i++) {
    search.sync({ conversationId: "m" + i, title: "T" + i, engine: "codex", timestamp: i },
      [sourceFile("m" + i + ".jsonl", "x")], () => [{ role: "user", content: "kata kunci bersama" }]);
  }
  assert.equal(search.query("kata kunci", 3).length, 3);
});

test("newest sessions come first", () => {
  search.sync({ conversationId: "old", title: "A", engine: "codex", timestamp: 100 },
    [sourceFile("old.jsonl", "x")], () => [{ role: "user", content: "shared word" }]);
  search.sync({ conversationId: "new", title: "B", engine: "codex", timestamp: 900 },
    [sourceFile("new.jsonl", "x")], () => [{ role: "user", content: "shared word" }]);
  assert.equal(search.query("shared word", 10)[0].conversationId, "new");
});

// The whole point of the index: unchanged transcripts must not be re-read.
test("an unchanged transcript is not parsed again", () => {
  const src = sourceFile("cache.jsonl", "v1");
  const session = { conversationId: "cached", title: "T", engine: "codex", timestamp: 1 };
  let reads = 0;
  const loader = () => { reads++; return [{ role: "user", content: "hasil" }]; };

  search.sync(session, [src], loader);
  search.sync(session, [src], loader);
  search.sync(session, [src], loader);
  assert.equal(reads, 1, "loader should only run on a cache miss");
});

test("a changed transcript is re-indexed", () => {
  const src = sourceFile("changed.jsonl", "v1");
  const session = { conversationId: "changed", title: "T", engine: "codex", timestamp: 1 };
  search.sync(session, [src], () => [{ role: "user", content: "versi lama" }]);
  assert.equal(search.query("versi baru", 10).length, 0);

  const later = Date.now() / 1000 + 60;
  fs.writeFileSync(src, "v2");
  fs.utimesSync(src, later, later);

  search.sync(session, [src], () => [{ role: "user", content: "versi baru" }]);
  assert.equal(search.query("versi baru", 10).length, 1);
});

test("a renamed session is re-indexed even when the file is untouched", () => {
  const src = sourceFile("rename.jsonl", "v1");
  search.sync({ conversationId: "r", title: "Judul lama", engine: "codex", timestamp: 1 },
    [src], () => [{ role: "user", content: "isi" }]);
  search.sync({ conversationId: "r", title: "Judul baru", engine: "codex", timestamp: 1 },
    [src], () => [{ role: "user", content: "isi" }]);
  assert.equal(search.query("judul baru", 10).length, 1);
});

test("dropMissing forgets sessions that no longer exist", () => {
  const src = sourceFile("gone.jsonl", "x");
  search.sync({ conversationId: "keep", title: "K", engine: "codex", timestamp: 1 }, [src], () => []);
  search.sync({ conversationId: "gone", title: "G", engine: "codex", timestamp: 1 }, [src], () => []);
  assert.equal(search.stats().sessions, 2);
  search.dropMissing(["keep"]);
  assert.equal(search.stats().sessions, 1);
  assert.equal(search.query("G", 10).length, 0);
});

test("a session without an id is skipped", () => {
  assert.equal(search.sync({ title: "no id" }, [], () => []), null);
});

test("a loader that throws does not take the index down", () => {
  const src = sourceFile("throw.jsonl", "x");
  const entry = search.sync({ conversationId: "t", title: "Judul aman", engine: "codex", timestamp: 1 },
    [src], () => { throw new Error("parse failed"); });
  assert.ok(entry);
  assert.equal(search.query("judul aman", 10).length, 1);
});
