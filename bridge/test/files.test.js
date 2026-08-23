const { test } = require("node:test");
const assert = require("node:assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

const files = require("../files");

function makeRoot() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "files-test-"));
  fs.mkdirSync(path.join(root, "src"));
  fs.writeFileSync(path.join(root, "src", "main.js"), "console.log('hi')\n");
  fs.writeFileSync(path.join(root, "readme.md"), "# Title\n");
  fs.writeFileSync(path.join(root, ".hidden"), "secret\n");
  fs.mkdirSync(path.join(root, "node_modules"));
  fs.writeFileSync(path.join(root, "binary.bin"), Buffer.from([0x00, 0x01, 0x02, 0x00]));
  return root;
}

test("lists directories first, then files alphabetically", () => {
  const root = makeRoot();
  const result = files.list(root, ".");
  assert.equal(result.ok, true);
  const names = result.entries.map(e => e.name);
  assert.equal(names[0], "src", "directory should sort before files");
  assert.ok(names.includes("readme.md"));
});

test("hides dotfiles and heavy tool directories", () => {
  const root = makeRoot();
  const names = files.list(root, ".").entries.map(e => e.name);
  assert.ok(!names.includes(".hidden"));
  assert.ok(!names.includes("node_modules"));
});

test("refuses to escape the root with ..", () => {
  const root = makeRoot();
  for (const attempt of ["../..", "../../etc", "src/../../..", "/etc"]) {
    const result = files.list(root, attempt);
    assert.ok(result.error, `"${attempt}" must be rejected, got ${JSON.stringify(result)}`);
  }
});

test("safeResolve returns null outside the root and a path inside it", () => {
  const root = makeRoot();
  assert.equal(files.safeResolve(root, "../.."), null);
  assert.ok(files.safeResolve(root, "src").endsWith("src"));
});

test("a path whose name merely starts with the root is still rejected", () => {
  const root = makeRoot();
  assert.equal(files.safeResolve(root, "../" + path.basename(root) + "-evil"), null);
});

test("reads text files and reports the language", () => {
  const root = makeRoot();
  const result = files.read(root, "src/main.js");
  assert.equal(result.ok, true);
  assert.equal(result.binary, false);
  assert.equal(result.language, "js");
  assert.match(result.content, /console\.log/);
});

test("flags binary files instead of returning garbage", () => {
  const root = makeRoot();
  const result = files.read(root, "binary.bin");
  assert.equal(result.binary, true);
  assert.equal(result.content, "");
});

test("reading a missing file reports an error, not a crash", () => {
  const root = makeRoot();
  assert.ok(files.read(root, "nope.txt").error);
});

test("writes a new file inside the root", () => {
  const root = makeRoot();
  const result = files.write(root, "docs/notes.md", "# Catatan\n");
  assert.equal(result.ok, true);
  assert.equal(fs.readFileSync(path.join(root, "docs", "notes.md"), "utf8"), "# Catatan\n");
});

test("overwrites an existing file", () => {
  const root = makeRoot();
  files.write(root, "readme.md", "baru\n");
  assert.equal(fs.readFileSync(path.join(root, "readme.md"), "utf8"), "baru\n");
});

test("refuses to write outside the root", () => {
  const root = makeRoot();
  assert.ok(files.write(root, "../escape.txt", "x").error);
  assert.ok(files.write(root, "/etc/escape.txt", "x").error);
});

test("refuses to write over a directory", () => {
  const root = makeRoot();
  assert.ok(files.write(root, "src", "x").error);
});

test("rejects non-string content instead of writing junk", () => {
  const root = makeRoot();
  assert.ok(files.write(root, "bad.txt", { not: "a string" }).error);
  assert.ok(files.write(root, "bad.txt", null).error);
});

test("rejects content past the write cap", () => {
  const root = makeRoot();
  const tooBig = "x".repeat(files.MAX_WRITE_BYTES + 1);
  assert.ok(files.write(root, "huge.txt", tooBig).error);
  assert.equal(fs.existsSync(path.join(root, "huge.txt")), false);
});

test("a failed write leaves no temporary file behind", () => {
  const root = makeRoot();
  files.write(root, "src", "x");
  assert.equal(fs.readdirSync(root).some(n => n.includes(".codexremote.tmp")), false);
});

test("prune removes files older than the window and keeps the rest", () => {
  const root = makeRoot();
  const dir = path.join(root, "uploads");
  fs.mkdirSync(dir);
  fs.writeFileSync(path.join(dir, "old.png"), "old");
  fs.writeFileSync(path.join(dir, "fresh.png"), "fresh");

  const longAgo = Date.now() / 1000 - 40 * 24 * 60 * 60;
  fs.utimesSync(path.join(dir, "old.png"), longAgo, longAgo);

  const result = files.pruneOlderThan(dir, 14);
  assert.deepEqual(result.removed, ["old.png"]);
  assert.equal(result.kept, 1);
  assert.ok(result.freedBytes > 0);
  assert.equal(fs.existsSync(path.join(dir, "fresh.png")), true);
});

test("a retention of zero disables pruning entirely", () => {
  const root = makeRoot();
  const dir = path.join(root, "uploads");
  fs.mkdirSync(dir);
  fs.writeFileSync(path.join(dir, "old.png"), "old");
  const longAgo = Date.now() / 1000 - 400 * 24 * 60 * 60;
  fs.utimesSync(path.join(dir, "old.png"), longAgo, longAgo);

  const result = files.pruneOlderThan(dir, 0);
  assert.equal(result.skipped, true);
  assert.equal(fs.existsSync(path.join(dir, "old.png")), true);
});

test("pruning a missing directory is harmless", () => {
  const root = makeRoot();
  assert.equal(files.pruneOlderThan(path.join(root, "nope"), 14).ok, true);
});
